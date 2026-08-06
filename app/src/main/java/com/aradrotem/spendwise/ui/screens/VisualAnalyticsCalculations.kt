package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.domain.AnalyticsTimeRange
import com.aradrotem.spendwise.domain.BudgetActualCalculator
import com.aradrotem.spendwise.domain.CategoryDistributionCalculator
import com.aradrotem.spendwise.domain.CategoryTrendCalculator
import com.aradrotem.spendwise.domain.FinancialInsightGenerator
import com.aradrotem.spendwise.domain.MonthlyIncomeExpenseCalculator
import com.aradrotem.spendwise.domain.MonthlySpendingTrendCalculator
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatMonthYear
import com.aradrotem.spendwise.util.formatCategoryDisplayName
import com.aradrotem.spendwise.util.monthRange
import java.time.YearMonth
import java.time.ZoneId

// Pure period-analytics calculation logic, unchanged from Step 15's original implementation -
// only the ViewModel class that used to wrap this (VisualAnalyticsViewModel) was retired when
// Monthly Report and Visual Analytics were merged into ReportsAnalyticsScreen. Kept as a top-level
// function (not a ViewModel member) so it's directly unit-testable without a Main dispatcher - see
// VisualAnalyticsLogicTest, which needed no changes since this function's package/signature are
// untouched.
fun buildVisualAnalyticsUiState(
    selectedMonth: YearMonth,
    timeRange: AnalyticsTimeRange,
    months: List<YearMonth>,
    transactions: List<TransactionEntity>,
    expenseCategories: List<CategoryEntity>,
    budgets: List<BudgetEntity>,
    selectedTrendCategoryInput: String?,
    zoneId: ZoneId = ZoneId.systemDefault()
): VisualAnalyticsUiState {
    val periodIncomeCents = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountInCents }
    val periodExpenseCents = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountInCents }
    val periodExpenseTransactionCount = transactions.count { it.type == TransactionType.EXPENSE }
    val averageMonthlyExpenseCents = if (months.isNotEmpty()) periodExpenseCents / months.size else 0L

    val categoryDistribution = CategoryDistributionCalculator.calculate(transactions)
    val monthlyIncomeExpense = MonthlyIncomeExpenseCalculator.calculate(months, transactions, zoneId)
    val monthlySpendingTrend = MonthlySpendingTrendCalculator.calculate(monthlyIncomeExpense)

    // Prefer a category that actually appears in this period (most useful default), falling back
    // to the first available expense category, and finally to whatever the user explicitly picked
    // even if it has no data this period (so re-selecting the same category across range changes
    // doesn't silently reset).
    val availableCategoryNames = expenseCategories.map { it.name }
    val categoriesInPeriod = categoryDistribution.map { it.categoryName }
    val resolvedTrendCategory = selectedTrendCategoryInput?.takeIf { it in availableCategoryNames }
        ?: categoriesInPeriod.firstOrNull { it in availableCategoryNames }
        ?: availableCategoryNames.firstOrNull()

    val categoryTrend = resolvedTrendCategory
        ?.let { category -> CategoryTrendCalculator.calculate(category, months, transactions, zoneId) }
        .orEmpty()

    // Budgets are always evaluated against the selected calendar month specifically (a budget is a
    // monthly limit, not a period total), regardless of which multi-month time range is selected.
    val selectedRange = monthRange(selectedMonth, zoneId)
    val selectedMonthExpenseTotals = transactions
        .filter { it.type == TransactionType.EXPENSE && it.timestamp >= selectedRange.startMillis && it.timestamp < selectedRange.endExclusiveMillis }
        .groupBy { it.category }
        .map { (category, categoryTransactions) -> CategoryMonthlyTotal(category, categoryTransactions.sumOf { it.amountInCents }) }
    // Reuses BudgetsViewModel.computeBudgetProgress rather than recomputing spend-per-category.
    val budgetActuals = computeBudgetProgress(budgets, selectedMonthExpenseTotals)
        .map { progress -> BudgetActualCalculator.calculate(progress.categoryName, progress.limitCents, progress.spentCents) }

    val insights = FinancialInsightGenerator.generate(
        categoryPoints = categoryDistribution,
        monthlySpendingPoints = monthlySpendingTrend,
        monthlyIncomeExpensePoints = monthlyIncomeExpense,
        budgetActualPoints = budgetActuals,
        categoryTrendPoints = categoryTrend,
        selectedCategoryName = resolvedTrendCategory
    )

    return VisualAnalyticsUiState(
        isLoading = false,
        selectedMonth = selectedMonth,
        timeRange = timeRange,
        months = months,
        totalIncomeCents = periodIncomeCents,
        totalExpenseCents = periodExpenseCents,
        averageMonthlyExpenseCents = averageMonthlyExpenseCents,
        expenseTransactionCount = periodExpenseTransactionCount,
        highestSpendingCategory = categoryDistribution.firstOrNull { it.amountCents > 0L },
        categoryDistribution = categoryDistribution,
        monthlyIncomeExpense = monthlyIncomeExpense,
        monthlySpendingTrend = monthlySpendingTrend,
        highestSpendingMonth = MonthlySpendingTrendCalculator.highestSpendingMonth(monthlySpendingTrend),
        lowestSpendingMonth = MonthlySpendingTrendCalculator.lowestSpendingMonth(monthlySpendingTrend),
        availableExpenseCategories = availableCategoryNames,
        selectedTrendCategory = resolvedTrendCategory,
        categoryTrend = categoryTrend,
        budgetActuals = budgetActuals,
        insights = insights
    )
}

// Multi-month counterpart to buildMonthlyReportShareText (MonthlyReportCalculations.kt) - same
// ACTION_SEND/text-plain sharing mechanism, same "no stored report" rule, just summarizing a
// period instead of a single month. Nothing here is persisted.
fun buildPeriodShareText(state: VisualAnalyticsUiState): String {
    val startLabel = formatMonthYear(state.months.firstOrNull() ?: state.selectedMonth)
    val endLabel = formatMonthYear(state.selectedMonth)

    val lines = mutableListOf<String>()
    lines += "SpendWise Report — $startLabel to $endLabel"
    lines += ""
    lines += "Total income: ${formatAmountInCents(state.totalIncomeCents)}"
    lines += "Total expenses: ${formatAmountInCents(state.totalExpenseCents)}"
    lines += "Balance: ${signedAbsolute(state.netBalanceCents)}"
    lines += "Average monthly expenses: ${formatAmountInCents(state.averageMonthlyExpenseCents)}"

    state.highestSpendingCategory?.let {
        lines += "Highest spending category: ${formatCategoryDisplayName(it.categoryName)} (${formatAmountInCents(it.amountCents)})"
    }
    state.highestSpendingMonth?.let {
        lines += "Highest spending month: ${formatMonthYear(it.yearMonth)} (${formatAmountInCents(it.expenseCents)})"
    }

    val positiveMonths = state.monthlyIncomeExpense.count { it.incomeCents > it.expenseCents }
    lines += "Income exceeded expenses in $positiveMonths of ${state.monthlyIncomeExpense.size} months"

    if (state.insights.isNotEmpty()) {
        lines += ""
        lines += "Insights:"
        state.insights.forEach { lines += "- ${it.text}" }
    }

    return lines.joinToString(separator = "\n")
}

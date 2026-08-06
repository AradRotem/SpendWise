package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatMonthYear
import com.aradrotem.spendwise.ui.format.formatPercent
import com.aradrotem.spendwise.ui.format.signedAbsolute
import com.aradrotem.spendwise.ui.format.signedDelta
import com.aradrotem.spendwise.util.formatCategoryDisplayName
import java.time.YearMonth
import kotlin.math.abs

// Pure Monthly Report calculation/share-text logic, unchanged from Step 13 - only the ViewModel
// class that used to wrap these (MonthlyReportViewModel) was retired when Step 15's final
// refinement merged Monthly Report and Visual Analytics into ReportsAnalyticsScreen. Kept as
// top-level functions (not ViewModel members) so they stay directly unit-testable without a Main
// dispatcher - see MonthlyReportLogicTest/MonthlyReportIntegrationTest, which needed no changes
// since these functions' package and signatures are untouched.
fun buildMonthlyReportUiState(
    selectedMonth: YearMonth,
    incomeCents: Long,
    expenseCents: Long,
    previousMonthIncomeCents: Long,
    previousMonthExpenseCents: Long,
    categoryTotals: List<CategoryMonthlyTotal>,
    budgets: List<BudgetEntity>,
    transactionsInMonth: List<TransactionEntity>
): MonthlyReportUiState {
    val categoryBreakdown = categoryTotals.map { entry ->
        CategorySpending(
            categoryName = entry.category,
            totalCents = entry.totalCents,
            percentOfExpenses = if (expenseCents > 0L) entry.totalCents.toFloat() / expenseCents.toFloat() else 0f
        )
    }

    return MonthlyReportUiState(
        isLoading = false,
        selectedMonth = selectedMonth,
        incomeCents = incomeCents,
        expenseCents = expenseCents,
        previousMonthIncomeCents = previousMonthIncomeCents,
        previousMonthExpenseCents = previousMonthExpenseCents,
        categoryBreakdown = categoryBreakdown,
        budgets = computeBudgetProgress(budgets, categoryTotals),
        incomeTransactionCount = transactionsInMonth.count { it.type == TransactionType.INCOME },
        expenseTransactionCount = transactionsInMonth.count { it.type == TransactionType.EXPENSE }
    )
}

// Pure text builder sharing the exact same values the report screen displays - there is no
// separate calculation path for the shared text. SpendWise only tracks transactions locally, so
// this never references transaction/plan ids or bank/card provider language.
fun buildMonthlyReportShareText(state: MonthlyReportUiState): String {
    val monthLabel = formatMonthYear(state.selectedMonth)
    val previousMonthLabel = formatMonthYear(state.selectedMonth.minusMonths(1))

    val lines = mutableListOf<String>()
    lines += "SpendWise Monthly Report — $monthLabel"
    lines += ""
    lines += "Income: ${formatAmountInCents(state.incomeCents)}"
    lines += "Expenses: ${formatAmountInCents(state.expenseCents)}"
    lines += "Balance: ${signedAbsolute(state.balanceCents)}"
    lines += ""
    lines += "Compared with $previousMonthLabel:"
    lines += "Income: ${signedDelta(state.incomeChangeCents)}"
    lines += "Expenses: ${signedDelta(state.expenseChangeCents)}"
    lines += "Balance: ${signedDelta(state.balanceChangeCents)}"

    state.topCategory?.let { top ->
        lines += ""
        lines += "Top spending category:"
        lines += "${formatCategoryDisplayName(top.categoryName)} — ${formatAmountInCents(top.totalCents)}"
    }

    val spentCategories = state.categoryBreakdown.filter { it.totalCents > 0L }
    lines += ""
    if (spentCategories.isNotEmpty()) {
        lines += "Category breakdown:"
        spentCategories.forEach { entry ->
            lines += "${formatCategoryDisplayName(entry.categoryName)}: ${formatAmountInCents(entry.totalCents)}"
        }
    } else {
        lines += "No expenses recorded this month."
    }

    if (state.budgets.isNotEmpty()) {
        lines += ""
        lines += "Budgets:"
        state.budgets.forEach { budget ->
            val base = "${formatCategoryDisplayName(budget.categoryName)}: ${formatAmountInCents(budget.spentCents)} of " +
                formatAmountInCents(budget.limitCents)
            lines += if (budget.isExceeded) "$base — over budget" else base
        }
    }

    lines += ""
    lines += "Transactions:"
    lines += "Income: ${state.incomeTransactionCount}"
    lines += "Expenses: ${state.expenseTransactionCount}"

    return lines.joinToString(separator = "\n")
}

// Clear, non-numeric-only wording for a month-over-month expense/income/balance change, replacing
// a bare "-3187.66 vs last month" style line with an explicit increased/decreased sentence. Mirrors
// the "no previous spending" wording already used by Visual Analytics' monthly trend.
fun monthOverMonthText(label: String, currentCents: Long, previousCents: Long): String {
    val change = currentCents - previousCents
    return when {
        previousCents == 0L && currentCents == 0L -> "No previous $label for comparison"
        previousCents == 0L -> "$label started this month"
        change == 0L -> "No change in $label compared with last month"
        else -> {
            val percent = (abs(change).toFloat() / abs(previousCents).toFloat()) * 100f
            val verb = if (change > 0L) "increased" else "decreased"
            "$label $verb by ${formatAmountInCents(abs(change))} (${formatPercent(percent)}%) compared with last month"
        }
    }
}

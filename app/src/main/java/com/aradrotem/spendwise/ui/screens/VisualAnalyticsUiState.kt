package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.domain.AnalyticsTimeRange
import com.aradrotem.spendwise.domain.BudgetActualPoint
import com.aradrotem.spendwise.domain.CategorySpendingPoint
import com.aradrotem.spendwise.domain.CategoryTrendPoint
import com.aradrotem.spendwise.domain.CumulativeSpendingPoint
import com.aradrotem.spendwise.domain.FinancialInsight
import com.aradrotem.spendwise.domain.MonthEndProjection
import com.aradrotem.spendwise.domain.MonthlyIncomeExpensePoint
import com.aradrotem.spendwise.domain.MonthlySpendingPoint
import com.aradrotem.spendwise.domain.TopPayeePoint
import com.aradrotem.spendwise.domain.WeekdaySpendingPoint
import java.time.YearMonth

data class VisualAnalyticsUiState(
    val isLoading: Boolean = true,
    val selectedMonth: YearMonth = YearMonth.now(),
    val timeRange: AnalyticsTimeRange = AnalyticsTimeRange.SELECTED_MONTH,
    // Months covered by the current selection, chronological (oldest first) - see
    // monthsForAnalyticsRange for the exact selected-month/last-N-months rule.
    val months: List<YearMonth> = emptyList(),

    // Summary cards - totals for the whole selected period (months.size months).
    val totalIncomeCents: Long = 0L,
    val totalExpenseCents: Long = 0L,
    val averageMonthlyExpenseCents: Long = 0L,
    val expenseTransactionCount: Int = 0,
    val highestSpendingCategory: CategorySpendingPoint? = null,

    // Expense distribution by category (section 2).
    val categoryDistribution: List<CategorySpendingPoint> = emptyList(),

    // Income vs expenses per month (section 3).
    val monthlyIncomeExpense: List<MonthlyIncomeExpensePoint> = emptyList(),

    // Monthly spending trend (section 4).
    val monthlySpendingTrend: List<MonthlySpendingPoint> = emptyList(),
    val highestSpendingMonth: MonthlySpendingPoint? = null,
    val lowestSpendingMonth: MonthlySpendingPoint? = null,

    // Category trend comparison (section 5).
    val availableExpenseCategories: List<String> = emptyList(),
    val selectedTrendCategory: String? = null,
    val categoryTrend: List<CategoryTrendPoint> = emptyList(),

    // Budget vs actual, always for the selected calendar month specifically (section 6).
    val budgetActuals: List<BudgetActualPoint> = emptyList(),

    // Textual insights (section 7).
    val insights: List<FinancialInsight> = emptyList(),

    // Spending by day of week, across the whole selected period (section 8, Step 19).
    val weekdaySpending: List<WeekdaySpendingPoint> = emptyList(),

    // Top payees/merchants by total spend, approximated from the transaction note field since
    // there is no dedicated merchant field (section 9, Step 19).
    val topPayees: List<TopPayeePoint> = emptyList(),

    // Cumulative spending through the selected calendar month, day by day (section 10, Step 19).
    val cumulativeSpending: List<CumulativeSpendingPoint> = emptyList(),

    // Projected month-end spending, only populated when the selected month is the current
    // calendar month - a projection for a past or future month is meaningless (section 11, Step 19).
    val monthEndProjection: MonthEndProjection? = null
) {
    val netBalanceCents: Long get() = totalIncomeCents - totalExpenseCents
    val isSingleMonth: Boolean get() = timeRange == AnalyticsTimeRange.SELECTED_MONTH
    val hasAnyTransactions: Boolean get() = totalIncomeCents != 0L || totalExpenseCents != 0L || expenseTransactionCount > 0
}

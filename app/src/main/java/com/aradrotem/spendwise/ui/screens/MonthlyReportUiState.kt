package com.aradrotem.spendwise.ui.screens

import java.time.YearMonth

// Reuses CategorySpending (HomeUiState.kt) and BudgetProgress (BudgetsUiState.kt) rather than
// defining near-duplicate types - both already carry exactly the fields the report needs.
data class MonthlyReportUiState(
    val isLoading: Boolean = true,
    val selectedMonth: YearMonth = YearMonth.now(),
    val incomeCents: Long = 0L,
    val expenseCents: Long = 0L,
    val previousMonthIncomeCents: Long = 0L,
    val previousMonthExpenseCents: Long = 0L,
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    val budgets: List<BudgetProgress> = emptyList(),
    val incomeTransactionCount: Int = 0,
    val expenseTransactionCount: Int = 0
) {
    val balanceCents: Long get() = incomeCents - expenseCents
    val previousMonthBalanceCents: Long get() = previousMonthIncomeCents - previousMonthExpenseCents

    val incomeChangeCents: Long get() = incomeCents - previousMonthIncomeCents
    val expenseChangeCents: Long get() = expenseCents - previousMonthExpenseCents
    val balanceChangeCents: Long get() = balanceCents - previousMonthBalanceCents

    // categoryBreakdown is already sorted highest-to-lowest (see
    // TransactionDao.observeExpenseTotalsByCategory), so the first entry with any spend is top.
    val topCategory: CategorySpending? get() = categoryBreakdown.firstOrNull { it.totalCents > 0L }
}

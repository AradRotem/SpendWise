package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.TransactionEntity

data class HomeUiState(
    val isLoading: Boolean = true,
    val monthlyIncomeCents: Long = 0L,
    val monthlyExpenseCents: Long = 0L,
    val budgetOverview: BudgetOverview = BudgetOverview(),
    val topCategories: List<CategorySpending> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList()
) {
    val monthlyBalanceCents: Long get() = monthlyIncomeCents - monthlyExpenseCents
}

data class BudgetOverview(
    val activeBudgetCount: Int = 0,
    val totalLimitCents: Long = 0L,
    val totalSpentCents: Long = 0L
) {
    val hasBudgets: Boolean get() = activeBudgetCount > 0
    val isOverBudget: Boolean get() = totalSpentCents > totalLimitCents

    // Real (possibly >100%) utilization, for display text.
    val utilizationFraction: Float
        get() = if (totalLimitCents <= 0L) 0f else totalSpentCents.toFloat() / totalLimitCents.toFloat()

    // Clamped for progress-indicator UI, which cannot render past 100%.
    val clampedUtilizationFraction: Float
        get() = utilizationFraction.coerceIn(0f, 1f)
}

data class CategorySpending(
    val categoryName: String,
    val totalCents: Long,
    val percentOfExpenses: Float
)

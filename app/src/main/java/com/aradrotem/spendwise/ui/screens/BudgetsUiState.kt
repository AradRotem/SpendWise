package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.CategoryEntity

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val budgets: List<BudgetProgress> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val dialog: BudgetDialogUiState? = null,
    val budgetPendingDelete: BudgetEntity? = null
) {
    val budgetedCategoryNames: Set<String>
        get() = budgets.map { it.categoryName }.toSet()
}

data class BudgetProgress(
    val budget: BudgetEntity,
    val spentCents: Long
) {
    val categoryName: String get() = budget.categoryName
    val limitCents: Long get() = budget.monthlyLimitCents
    val remainingCents: Long get() = limitCents - spentCents
    val isExceeded: Boolean get() = spentCents > limitCents

    // Safely clamped for progress indicators; raw spentCents/limitCents remain accurate for display.
    val clampedProgressFraction: Float
        get() {
            if (limitCents <= 0L) return 0f
            return (spentCents.toFloat() / limitCents.toFloat()).coerceIn(0f, 1f)
        }
}

data class BudgetDialogUiState(
    val isEdit: Boolean,
    val budgetId: Long? = null,
    val categoryName: String? = null,
    val amountText: String = "",
    val categoryError: String? = null,
    val amountError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null
)

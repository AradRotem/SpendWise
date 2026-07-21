package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

// Pure-logic tests for buildHomeUiState, the Home dashboard's transform from raw repository
// values to HomeUiState. Kept as a top-level function (not a HomeViewModel member) specifically
// so it's testable without a real ViewModel/Main dispatcher.
class HomeViewModelLogicTest {

    private fun recentTransaction() = TransactionEntity(
        amountInCents = 1_000L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1_000L
    )

    @Test
    fun balance_isIncomeMinusExpense() {
        val state = buildHomeUiState(
            incomeCents = 10_000L, expenseCents = 4_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = emptyList(), budgets = emptyList(), recentTransactions = emptyList()
        )

        assertEquals(6_000L, state.monthlyBalanceCents)
    }

    @Test
    fun monthOverMonth_reflectsIncreaseAndDecrease() {
        val state = buildHomeUiState(
            incomeCents = 12_000L, expenseCents = 3_000L,
            previousMonthIncomeCents = 10_000L, previousMonthExpenseCents = 5_000L,
            categoryTotals = emptyList(), budgets = emptyList(), recentTransactions = emptyList()
        )

        assertEquals(5_000L, state.previousMonthBalanceCents) // 10000 - 5000
        assertEquals(2_000L, state.incomeChangeCents) // income up $20
        assertEquals(-2_000L, state.expenseChangeCents) // expenses down $20
        assertEquals(4_000L, state.balanceChangeCents) // balance 5000 -> 9000
    }

    @Test
    fun monthOverMonth_isZeroWhenBothMonthsMatch() {
        val state = buildHomeUiState(
            incomeCents = 5_000L, expenseCents = 2_000L,
            previousMonthIncomeCents = 5_000L, previousMonthExpenseCents = 2_000L,
            categoryTotals = emptyList(), budgets = emptyList(), recentTransactions = emptyList()
        )

        assertEquals(0L, state.incomeChangeCents)
        assertEquals(0L, state.expenseChangeCents)
        assertEquals(0L, state.balanceChangeCents)
    }

    @Test
    fun topCategories_excludesZeroTotalsAndCapsAtThree() {
        val categoryTotals = listOf(
            CategoryMonthlyTotal("HOUSING", 5_000L),
            CategoryMonthlyTotal("FOOD", 3_000L),
            CategoryMonthlyTotal("TRANSPORT", 2_000L),
            CategoryMonthlyTotal("SHOPPING", 1_000L),
            CategoryMonthlyTotal("OTHER", 0L)
        )

        val state = buildHomeUiState(
            incomeCents = 0L, expenseCents = 11_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = categoryTotals, budgets = emptyList(), recentTransactions = emptyList()
        )

        assertEquals(listOf("HOUSING", "FOOD", "TRANSPORT"), state.topCategories.map { it.categoryName })
        assertEquals(5_000f / 11_000f, state.topCategories.first().percentOfExpenses, 0.0001f)
    }

    @Test
    fun budgetOverview_sumsOnlyBudgetedCategoriesSpend() {
        val budgets = listOf(
            BudgetEntity(id = 1, categoryName = "HOUSING", monthlyLimitCents = 6_000L),
            BudgetEntity(id = 2, categoryName = "FOOD", monthlyLimitCents = 2_000L)
        )
        val categoryTotals = listOf(
            CategoryMonthlyTotal("HOUSING", 5_000L),
            CategoryMonthlyTotal("FOOD", 3_000L),
            CategoryMonthlyTotal("TRANSPORT", 9_000L) // not budgeted - must not affect totals
        )

        val state = buildHomeUiState(
            incomeCents = 0L, expenseCents = 17_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = categoryTotals, budgets = budgets, recentTransactions = emptyList()
        )

        assertEquals(2, state.budgetOverview.activeBudgetCount)
        assertEquals(8_000L, state.budgetOverview.totalLimitCents)
        assertEquals(8_000L, state.budgetOverview.totalSpentCents) // 5000 + 3000, TRANSPORT excluded
        assertEquals(false, state.budgetOverview.isOverBudget) // spent == limit is not over
    }

    @Test
    fun budgetOverview_flagsOverBudgetOnlyWhenSpendExceedsLimit() {
        val budgets = listOf(BudgetEntity(id = 1, categoryName = "HOUSING", monthlyLimitCents = 4_000L))
        val categoryTotals = listOf(CategoryMonthlyTotal("HOUSING", 5_000L))

        val state = buildHomeUiState(
            incomeCents = 0L, expenseCents = 5_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = categoryTotals, budgets = budgets, recentTransactions = emptyList()
        )

        assertEquals(true, state.budgetOverview.isOverBudget)
        assertEquals(1.25f, state.budgetOverview.utilizationFraction, 0.0001f)
        assertEquals(1f, state.budgetOverview.clampedUtilizationFraction, 0.0001f)
    }

    @Test
    fun recentTransactionsAndLoadingFlag_passThroughUnchanged() {
        val recent = listOf(recentTransaction())

        val state = buildHomeUiState(
            incomeCents = 0L, expenseCents = 0L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = emptyList(), budgets = emptyList(), recentTransactions = recent
        )

        assertEquals(false, state.isLoading)
        assertEquals(recent, state.recentTransactions)
    }
}

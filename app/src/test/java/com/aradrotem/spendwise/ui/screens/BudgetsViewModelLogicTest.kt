package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import org.junit.Assert.assertEquals
import org.junit.Test

// Pure-logic tests for computeBudgetProgress, the Budgets screen's transform pairing each budget
// with its current-month spend. Kept as a top-level function (not a BudgetsViewModel member)
// specifically so it's testable without a real ViewModel/Main dispatcher.
class BudgetsViewModelLogicTest {

    @Test
    fun budgetWithMatchingSpend_computesRemainingAndExceeded() {
        val budgets = listOf(BudgetEntity(id = 1, categoryName = "HOUSING", monthlyLimitCents = 10_000L))
        val totals = listOf(CategoryMonthlyTotal("HOUSING", 6_000L))

        val progress = computeBudgetProgress(budgets, totals).single()

        assertEquals(6_000L, progress.spentCents)
        assertEquals(4_000L, progress.remainingCents)
        assertEquals(false, progress.isExceeded)
        assertEquals(0.6f, progress.clampedProgressFraction, 0.0001f)
    }

    @Test
    fun budgetWithNoMatchingTransactions_hasZeroSpend() {
        val budgets = listOf(BudgetEntity(id = 1, categoryName = "HOUSING", monthlyLimitCents = 10_000L))

        val progress = computeBudgetProgress(budgets, emptyList()).single()

        assertEquals(0L, progress.spentCents)
        assertEquals(10_000L, progress.remainingCents)
        assertEquals(false, progress.isExceeded)
    }

    @Test
    fun spendExceedingLimit_marksExceededAndClampsProgress() {
        val budgets = listOf(BudgetEntity(id = 1, categoryName = "HOUSING", monthlyLimitCents = 5_000L))
        val totals = listOf(CategoryMonthlyTotal("HOUSING", 7_500L))

        val progress = computeBudgetProgress(budgets, totals).single()

        assertEquals(7_500L, progress.spentCents)
        assertEquals(-2_500L, progress.remainingCents)
        assertEquals(true, progress.isExceeded)
        assertEquals(1f, progress.clampedProgressFraction, 0.0001f)
    }

    @Test
    fun unbudgetedCategoryTotals_areIgnored() {
        val budgets = listOf(BudgetEntity(id = 1, categoryName = "HOUSING", monthlyLimitCents = 10_000L))
        val totals = listOf(
            CategoryMonthlyTotal("HOUSING", 3_000L),
            CategoryMonthlyTotal("TRANSPORT", 9_000L) // no budget exists for this category
        )

        val progress = computeBudgetProgress(budgets, totals)

        assertEquals(1, progress.size)
        assertEquals(3_000L, progress.single().spentCents)
    }

    @Test
    fun results_areSortedByCategoryNameCaseInsensitive() {
        val budgets = listOf(
            BudgetEntity(id = 1, categoryName = "transport", monthlyLimitCents = 1_000L),
            BudgetEntity(id = 2, categoryName = "Housing", monthlyLimitCents = 1_000L),
            BudgetEntity(id = 3, categoryName = "food", monthlyLimitCents = 1_000L)
        )

        val progress = computeBudgetProgress(budgets, emptyList())

        assertEquals(listOf("food", "Housing", "transport"), progress.map { it.categoryName })
    }
}

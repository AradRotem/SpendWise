package com.aradrotem.spendwise.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetActualCalculatorTest {

    @Test
    fun underBudget_statusUnder() {
        val point = BudgetActualCalculator.calculate("FOOD", 10_000L, 5_000L)
        assertEquals(BudgetStatus.UNDER_BUDGET, point.status)
        assertEquals(5_000L, point.remainingCents)
    }

    @Test
    fun nearBudgetThreshold_statusNear() {
        val point = BudgetActualCalculator.calculate("FOOD", 10_000L, 9_000L) // exactly 90%
        assertEquals(BudgetStatus.NEAR_BUDGET, point.status)
    }

    @Test
    fun justBelowNearThreshold_statusUnder() {
        val point = BudgetActualCalculator.calculate("FOOD", 10_000L, 8_999L)
        assertEquals(BudgetStatus.UNDER_BUDGET, point.status)
    }

    @Test
    fun exactlyAtBudget_statusNearNotOver() {
        val point = BudgetActualCalculator.calculate("FOOD", 10_000L, 10_000L)
        assertEquals(BudgetStatus.NEAR_BUDGET, point.status)
        assertEquals(0L, point.remainingCents)
    }

    @Test
    fun overBudget_statusOverWithNegativeRemaining() {
        val point = BudgetActualCalculator.calculate("FOOD", 10_000L, 12_000L)
        assertEquals(BudgetStatus.OVER_BUDGET, point.status)
        assertEquals(-2_000L, point.remainingCents)
    }

    @Test
    fun noSpending_statusUnderWithFullRemaining() {
        val point = BudgetActualCalculator.calculate("FOOD", 10_000L, 0L)
        assertEquals(BudgetStatus.UNDER_BUDGET, point.status)
        assertEquals(10_000L, point.remainingCents)
        assertEquals(0f, point.usageFraction, 0.001f)
    }

    @Test
    fun zeroBudget_doesNotDivideByZero() {
        val point = BudgetActualCalculator.calculate("FOOD", 0L, 500L)
        assertEquals(0f, point.usageFraction, 0.001f)
        assertEquals(BudgetStatus.OVER_BUDGET, point.status) // spent > budget(0) is still over
    }

    @Test
    fun multipleCategories_eachClassifiedIndependently() {
        val under = BudgetActualCalculator.calculate("FOOD", 10_000L, 1_000L)
        val over = BudgetActualCalculator.calculate("TRANSPORT", 5_000L, 6_000L)
        assertEquals(BudgetStatus.UNDER_BUDGET, under.status)
        assertEquals(BudgetStatus.OVER_BUDGET, over.status)
    }

    @Test
    fun remainingAndOverAmounts_areExact() {
        val over = BudgetActualCalculator.calculate("FOOD", 10_000L, 13_579L)
        assertEquals(-3_579L, over.remainingCents)
    }

    @Test
    fun usageFraction_isFiniteAndNonNegativeAcrossRange() {
        listOf(0L, 1L, 5_000L, 10_000L, 20_000L).forEach { spent ->
            val point = BudgetActualCalculator.calculate("FOOD", 10_000L, spent)
            assertEquals(true, point.usageFraction.isFinite())
            assertEquals(true, point.usageFraction >= 0f)
        }
    }
}

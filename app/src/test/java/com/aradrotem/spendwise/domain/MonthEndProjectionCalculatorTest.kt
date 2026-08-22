package com.aradrotem.spendwise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthEndProjectionCalculatorTest {

    @Test
    fun projectsLinearlyFromCurrentPace() {
        // 3,000 spent over 10 days in a 30-day month -> 300/day pace -> 9,000 projected.
        val projection = MonthEndProjectionCalculator.calculate(
            elapsedDays = 10, totalDaysInMonth = 30, spentSoFarCents = 3_000L, budgetCents = null
        )
        assertEquals(9_000L, projection.projectedTotalCents)
        assertTrue(projection.isReliable)
    }

    @Test
    fun earlyMonth_lowSample_flaggedUnreliable() {
        val projection = MonthEndProjectionCalculator.calculate(
            elapsedDays = 1, totalDaysInMonth = 30, spentSoFarCents = 500L, budgetCents = null
        )
        assertFalse(projection.isReliable)
    }

    @Test
    fun budgetExceeded_overageComputed() {
        val projection = MonthEndProjectionCalculator.calculate(
            elapsedDays = 10, totalDaysInMonth = 30, spentSoFarCents = 3_000L, budgetCents = 8_000L
        )
        assertTrue(projection.projectedToExceedBudget)
        assertEquals(1_000L, projection.projectedOverageCents)
    }

    @Test
    fun budgetNotExceeded_noOverage() {
        val projection = MonthEndProjectionCalculator.calculate(
            elapsedDays = 10, totalDaysInMonth = 30, spentSoFarCents = 3_000L, budgetCents = 20_000L
        )
        assertFalse(projection.projectedToExceedBudget)
        assertEquals(0L, projection.projectedOverageCents)
    }

    @Test
    fun noBudget_neverFlaggedAsExceeded() {
        val projection = MonthEndProjectionCalculator.calculate(
            elapsedDays = 10, totalDaysInMonth = 30, spentSoFarCents = 100_000L, budgetCents = null
        )
        assertFalse(projection.projectedToExceedBudget)
    }

    @Test
    fun elapsedDaysClamped_toMonthLength() {
        val projection = MonthEndProjectionCalculator.calculate(
            elapsedDays = 45, totalDaysInMonth = 30, spentSoFarCents = 9_000L, budgetCents = null
        )
        assertEquals(30, projection.elapsedDays)
        assertEquals(9_000L, projection.projectedTotalCents)
    }
}

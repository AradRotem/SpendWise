package com.aradrotem.spendwise.domain

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlySpendingTrendCalculatorTest {

    private fun point(month: Int, expenseCents: Long) =
        MonthlyIncomeExpensePoint(YearMonth.of(2026, month), incomeCents = 0L, expenseCents = expenseCents)

    @Test
    fun expenseIncrease_positiveChangeAndPercent() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(point(1, 10_000L), point(2, 15_000L)))
        assertEquals(5_000L, trend[1].changeCents)
        assertEquals(50f, trend[1].changePercent!!, 0.01f)
    }

    @Test
    fun expenseDecrease_negativeChangeAndPercent() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(point(1, 10_000L), point(2, 6_000L)))
        assertEquals(-4_000L, trend[1].changeCents)
        assertEquals(-40f, trend[1].changePercent!!, 0.01f)
    }

    @Test
    fun noChange_zeroChangeAndPercent() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(point(1, 10_000L), point(2, 10_000L)))
        assertEquals(0L, trend[1].changeCents)
        assertEquals(0f, trend[1].changePercent!!, 0.01f)
    }

    @Test
    fun previousValueZero_percentIsNullNotInfinite() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(point(1, 0L), point(2, 5_000L)))
        assertEquals(5_000L, trend[1].changeCents)
        assertNull(trend[1].changePercent)
    }

    @Test
    fun currentValueZero_changeIsNegativePreviousAmount() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(point(1, 5_000L), point(2, 0L)))
        assertEquals(-5_000L, trend[1].changeCents)
        assertEquals(-100f, trend[1].changePercent!!, 0.01f)
    }

    @Test
    fun highestAndLowestSpendingMonths_identifiedCorrectly() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(point(1, 3_000L), point(2, 9_000L), point(3, 1_000L)))
        assertEquals(YearMonth.of(2026, 2), MonthlySpendingTrendCalculator.highestSpendingMonth(trend)!!.yearMonth)
        assertEquals(YearMonth.of(2026, 3), MonthlySpendingTrendCalculator.lowestSpendingMonth(trend)!!.yearMonth)
    }

    @Test
    fun lowestSpendingMonth_ignoresZeroSpendMonths() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(point(1, 0L), point(2, 5_000L), point(3, 2_000L)))
        assertEquals(YearMonth.of(2026, 3), MonthlySpendingTrendCalculator.lowestSpendingMonth(trend)!!.yearMonth)
    }

    @Test
    fun singleMonthRange_hasNoPreviousData() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(point(1, 5_000L)))
        assertEquals(1, trend.size)
        assertFalse(trend.single().hasPreviousData)
        assertNull(trend.single().changeCents)
        assertNull(trend.single().changePercent)
    }

    @Test
    fun deterministicOutput_sameInputProducesSameResult() {
        val input = listOf(point(1, 3_000L), point(2, 9_000L), point(3, 1_000L))
        val first = MonthlySpendingTrendCalculator.calculate(input)
        val second = MonthlySpendingTrendCalculator.calculate(input)
        assertEquals(first, second)
    }

    @Test
    fun noSpendingAtAll_lowestSpendingMonthIsNull() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(point(1, 0L), point(2, 0L)))
        assertNull(MonthlySpendingTrendCalculator.lowestSpendingMonth(trend))
        assertTrue(MonthlySpendingTrendCalculator.highestSpendingMonth(trend) != null)
    }
}

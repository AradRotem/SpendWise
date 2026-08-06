package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.util.monthRange
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyAggregationTest {

    private val zoneId = ZoneId.of("UTC")

    private fun millisIn(year: Int, month: Int, day: Int): Long =
        YearMonth.of(year, month).atDay(day).atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun tx(amountCents: Long, type: TransactionType, category: String, year: Int, month: Int, day: Int = 15) =
        TransactionEntity(amountInCents = amountCents, type = type, category = category, timestamp = millisIn(year, month, day))

    @Test
    fun oneMonth_selectedMonthRange_returnsExactlyThatMonth() {
        val months = monthsForAnalyticsRange(YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH)
        assertEquals(listOf(YearMonth.of(2026, 3)), months)
    }

    @Test
    fun threeMonths_includesSelectedAndTwoPreceding() {
        val months = monthsForAnalyticsRange(YearMonth.of(2026, 3), AnalyticsTimeRange.LAST_3_MONTHS)
        assertEquals(listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)), months)
    }

    @Test
    fun sixMonths_includesSelectedAndFivePreceding() {
        val months = monthsForAnalyticsRange(YearMonth.of(2026, 6), AnalyticsTimeRange.LAST_6_MONTHS)
        assertEquals(
            listOf(
                YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3),
                YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6)
            ),
            months
        )
    }

    @Test
    fun twelveMonths_spansAcrossYearBoundary() {
        val months = monthsForAnalyticsRange(YearMonth.of(2026, 3), AnalyticsTimeRange.LAST_12_MONTHS)
        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2025, 4), months.first())
        assertEquals(YearMonth.of(2026, 3), months.last())
    }

    @Test
    fun missingMonths_representedAsZero() {
        val months = monthsForAnalyticsRange(YearMonth.of(2026, 3), AnalyticsTimeRange.LAST_3_MONTHS)
        val points = MonthlyIncomeExpenseCalculator.calculate(months, emptyList(), zoneId)
        assertEquals(3, points.size)
        assertEquals(listOf(0L, 0L, 0L), points.map { it.expenseCents })
        assertEquals(listOf(0L, 0L, 0L), points.map { it.incomeCents })
    }

    @Test
    fun chronologicalOrdering_isPreserved() {
        val months = monthsForAnalyticsRange(YearMonth.of(2026, 3), AnalyticsTimeRange.LAST_3_MONTHS)
        val points = MonthlyIncomeExpenseCalculator.calculate(months, emptyList(), zoneId)
        assertEquals(months, points.map { it.yearMonth })
    }

    @Test
    fun incomeAndExpense_separatedCorrectly() {
        val months = listOf(YearMonth.of(2026, 3))
        val transactions = listOf(
            tx(5_000L, TransactionType.INCOME, "SALARY", 2026, 3),
            tx(2_000L, TransactionType.EXPENSE, "FOOD", 2026, 3)
        )
        val points = MonthlyIncomeExpenseCalculator.calculate(months, transactions, zoneId)
        assertEquals(5_000L, points.single().incomeCents)
        assertEquals(2_000L, points.single().expenseCents)
    }

    @Test
    fun boundaryDates_noMidnightDoubleCounting() {
        val months = listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 4))
        // Exactly at the boundary: last instant of March belongs to March, first instant of April to April.
        val marchRange = monthRange(YearMonth.of(2026, 3), zoneId)
        val transactions = listOf(
            TransactionEntity(amountInCents = 100L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = marchRange.endExclusiveMillis - 1),
            TransactionEntity(amountInCents = 200L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = marchRange.endExclusiveMillis)
        )
        val points = MonthlyIncomeExpenseCalculator.calculate(months, transactions, zoneId)
        assertEquals(100L, points[0].expenseCents)
        assertEquals(200L, points[1].expenseCents)
    }

    @Test
    fun futureMonth_producesZeroWithoutError() {
        val futureMonth = YearMonth.now(zoneId).plusMonths(6)
        val months = monthsForAnalyticsRange(futureMonth, AnalyticsTimeRange.SELECTED_MONTH)
        val points = MonthlyIncomeExpenseCalculator.calculate(months, emptyList(), zoneId)
        assertEquals(0L, points.single().incomeCents)
        assertEquals(0L, points.single().expenseCents)
    }
}

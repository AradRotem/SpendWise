package com.aradrotem.spendwise.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

// Pure tests for monthRange() - the general building block currentMonthRange/previousMonthRange
// and the Monthly Report's arbitrary month selection are all built on.
class MonthRangeTest {

    private val zoneId = ZoneOffset.UTC

    private fun millisFor(date: LocalDate) = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    @Test
    fun monthRange_isHalfOpenFromFirstDayToStartOfNextMonth() {
        val range = monthRange(YearMonth.of(2026, 7), zoneId)

        assertEquals(millisFor(LocalDate.of(2026, 7, 1)), range.startMillis)
        assertEquals(millisFor(LocalDate.of(2026, 8, 1)), range.endExclusiveMillis)
    }

    @Test
    fun monthRange_handlesYearBoundaryCorrectly() {
        val range = monthRange(YearMonth.of(2025, 12), zoneId)

        assertEquals(millisFor(LocalDate.of(2025, 12, 1)), range.startMillis)
        assertEquals(millisFor(LocalDate.of(2026, 1, 1)), range.endExclusiveMillis)
    }

    @Test
    fun monthRange_handlesLeapFebruaryCorrectly() {
        val range = monthRange(YearMonth.of(2028, 2), zoneId)

        assertEquals(millisFor(LocalDate.of(2028, 2, 1)), range.startMillis)
        assertEquals(millisFor(LocalDate.of(2028, 3, 1)), range.endExclusiveMillis)
    }

    @Test
    fun currentMonthRange_matchesMonthRangeForTodaysYearMonth() {
        val today = YearMonth.now(zoneId)

        assertEquals(monthRange(today, zoneId), currentMonthRange(zoneId))
    }

    @Test
    fun previousMonthRange_matchesMonthRangeForLastMonth() {
        val lastMonth = YearMonth.now(zoneId).minusMonths(1)

        assertEquals(monthRange(lastMonth, zoneId), previousMonthRange(zoneId))
    }
}

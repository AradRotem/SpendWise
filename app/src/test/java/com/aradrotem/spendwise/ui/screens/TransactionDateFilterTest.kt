package com.aradrotem.spendwise.ui.screens

import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionDateFilterTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun all_hasNoRange() {
        assertNull(TransactionDateFilter.All.toRange(zoneId))
    }

    @Test
    fun month_resolvesToThatCalendarMonth() {
        val range = TransactionDateFilter.Month(YearMonth.of(2026, 3)).toRange(zoneId)!!
        val expectedStart = YearMonth.of(2026, 3).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val expectedEnd = YearMonth.of(2026, 4).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        assertEquals(expectedStart, range.startMillis)
        assertEquals(expectedEnd, range.endExclusiveMillis)
    }

    @Test
    fun customRange_passedThroughUnchanged() {
        val range = TransactionDateFilter.CustomRange(1_000L, 5_000L).toRange(zoneId)!!
        assertEquals(1_000L, range.startMillis)
        assertEquals(5_000L, range.endExclusiveMillis)
    }

    @Test
    fun label_all() {
        assertEquals("All transactions", transactionDateFilterLabel(TransactionDateFilter.All))
    }

    @Test
    fun label_month() {
        assertEquals("March 2026", transactionDateFilterLabel(TransactionDateFilter.Month(YearMonth.of(2026, 3))))
    }
}

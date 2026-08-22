package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.util.MonthRange
import com.aradrotem.spendwise.util.monthRange
import java.time.YearMonth
import java.time.ZoneId

// How the Transactions list is currently narrowed by date. Kept separate from any future
// category/search/sort filter state - none of that exists yet (Step 19 only adds date filtering).
sealed class TransactionDateFilter {
    object All : TransactionDateFilter()
    data class Month(val yearMonth: YearMonth) : TransactionDateFilter()
    // Half-open [startMillis, endExclusiveMillis), consistent with the rest of the app's date-range
    // convention (see MonthRange) - endExclusiveMillis should be the instant just after the last
    // included day (typically the start of the day after the picked end date).
    data class CustomRange(val startMillis: Long, val endExclusiveMillis: Long) : TransactionDateFilter()

    // The half-open range to query for, or null for "All" (no date restriction at all).
    fun toRange(zoneId: ZoneId = ZoneId.systemDefault()): MonthRange? = when (this) {
        is All -> null
        is Month -> monthRange(yearMonth, zoneId)
        is CustomRange -> MonthRange(startMillis, endExclusiveMillis)
    }
}

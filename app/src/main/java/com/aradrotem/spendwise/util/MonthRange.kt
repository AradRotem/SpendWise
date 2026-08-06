package com.aradrotem.spendwise.util

import java.time.YearMonth
import java.time.ZoneId

// Half-open range [startMillis, endExclusiveMillis) for the device-local calendar month.
// Avoids manually computing the final millisecond of the month.
data class MonthRange(val startMillis: Long, val endExclusiveMillis: Long)

// General building block for any calendar month, not just "now" - used by currentMonthRange/
// previousMonthRange below and by the Monthly Report's arbitrary month selection.
fun monthRange(yearMonth: YearMonth, zoneId: ZoneId = ZoneId.systemDefault()): MonthRange {
    val start = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val endExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    return MonthRange(start, endExclusive)
}

fun currentMonthRange(zoneId: ZoneId = ZoneId.systemDefault()): MonthRange =
    monthRange(YearMonth.now(zoneId), zoneId)

fun previousMonthRange(zoneId: ZoneId = ZoneId.systemDefault()): MonthRange =
    monthRange(YearMonth.now(zoneId).minusMonths(1), zoneId)

// Spans the earliest start to the latest end-exclusive across several month ranges - used by
// Visual Analytics to fetch a whole multi-month period with a single observeInRange query instead
// of one subscription per month.
fun combinedRange(monthRanges: List<MonthRange>): MonthRange {
    require(monthRanges.isNotEmpty()) { "monthRanges must not be empty" }
    return MonthRange(
        startMillis = monthRanges.minOf { it.startMillis },
        endExclusiveMillis = monthRanges.maxOf { it.endExclusiveMillis }
    )
}

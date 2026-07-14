package com.aradrotem.spendwise.util

import java.time.LocalDate
import java.time.ZoneId

// Half-open range [startMillis, endExclusiveMillis) for the device-local calendar month.
// Avoids manually computing the final millisecond of the month.
data class MonthRange(val startMillis: Long, val endExclusiveMillis: Long)

fun currentMonthRange(zoneId: ZoneId = ZoneId.systemDefault()): MonthRange {
    val firstOfMonth = LocalDate.now(zoneId).withDayOfMonth(1)
    val start = firstOfMonth.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val endExclusive = firstOfMonth.plusMonths(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    return MonthRange(start, endExclusive)
}

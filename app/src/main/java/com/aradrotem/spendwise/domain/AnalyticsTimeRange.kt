package com.aradrotem.spendwise.domain

import java.time.YearMonth

enum class AnalyticsTimeRange(val monthCount: Int, val label: String) {
    SELECTED_MONTH(1, "Month"),
    LAST_3_MONTHS(3, "3 months"),
    LAST_6_MONTHS(6, "6 months"),
    LAST_12_MONTHS(12, "12 months")
}

// Rule (documented once, applied everywhere - see VisualAnalyticsViewModel):
// - SELECTED_MONTH: exactly the selected calendar month.
// - LAST_N_MONTHS: the selected month plus the (N-1) calendar months immediately preceding it -
//   i.e. N consecutive months ending at, and including, the selected month.
// Both cases fall out of the same formula, returned oldest-first (chronological order).
fun monthsForAnalyticsRange(selectedMonth: YearMonth, timeRange: AnalyticsTimeRange): List<YearMonth> {
    val count = timeRange.monthCount
    return (count - 1 downTo 0).map { offset -> selectedMonth.minusMonths(offset.toLong()) }
}

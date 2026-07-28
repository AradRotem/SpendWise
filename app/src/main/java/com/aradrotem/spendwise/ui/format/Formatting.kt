package com.aradrotem.spendwise.ui.format

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

fun formatDate(timestampMillis: Long): String =
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(dateFormatter)

// e.g. "July 2026" - used by the Monthly Report's header and shared text.
fun formatMonthYear(yearMonth: YearMonth): String = yearMonth.format(monthYearFormatter)

// Formats cents as a decimal string without going through Double/Float.
fun formatAmountInCents(amountInCents: Long): String {
    val whole = amountInCents / 100
    val fraction = amountInCents % 100
    return "%d.%02d".format(whole, fraction)
}

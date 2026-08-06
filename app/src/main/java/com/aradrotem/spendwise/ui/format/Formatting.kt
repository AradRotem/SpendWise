package com.aradrotem.spendwise.ui.format

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val monthShortFormatter = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH)
private val monthNameOnlyFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)

fun formatDate(timestampMillis: Long): String =
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(dateFormatter)

// e.g. "July 2026" - used by the Monthly Report's header and shared text.
fun formatMonthYear(yearMonth: YearMonth): String = yearMonth.format(monthYearFormatter)

// e.g. "Jul 26" - compact axis label for Visual Analytics' multi-month charts, where a full
// "July 2026" per month would overlap or wrap across a row of up to 12 labels.
fun formatMonthShort(yearMonth: YearMonth): String = yearMonth.format(monthShortFormatter)

// e.g. "May–August 2026" for a same-year multi-month period (the first month's year is implied by
// the second), or "December 2025 – March 2026" when the period spans a year boundary. A single
// month collapses to the plain formatMonthYear form. Used by the category drill-down's period
// label ("Expenses in X" / "Y across N transactions" / period line).
fun formatPeriodLabel(months: List<YearMonth>): String {
    val first = months.firstOrNull() ?: return formatMonthYear(YearMonth.now())
    val last = months.last()
    return when {
        months.size == 1 -> formatMonthYear(first)
        first.year == last.year -> "${first.format(monthNameOnlyFormatter)}–${formatMonthYear(last)}"
        else -> "${formatMonthYear(first)} – ${formatMonthYear(last)}"
    }
}

// Formats cents as a decimal string without going through Double/Float.
fun formatAmountInCents(amountInCents: Long): String {
    val whole = amountInCents / 100
    val fraction = amountInCents % 100
    return "%d.%02d".format(whole, fraction)
}

// Shared building block for "<sign><magnitude>" displays (e.g. "+12.50", "-3.00", "12.50");
// callers decide how the sign string itself is chosen (by numeric sign, by transaction type, etc.).
fun formatAmountWithSign(sign: String, amountInCents: Long): String = "$sign${formatAmountInCents(abs(amountInCents))}"

// "-" only when negative; positive/zero show no sign. For values where an explicit "+" would be
// noise (e.g. a balance, which is only notable when it goes negative).
fun signedAbsolute(cents: Long): String = formatAmountWithSign(if (cents < 0L) "-" else "", cents)

// Explicit "+"/"-" so a change is never ambiguous; zero shows no sign. For deltas/comparisons
// (e.g. month-over-month change) where the direction itself is the point.
fun signedDelta(cents: Long): String {
    val sign = when {
        cents > 0L -> "+"
        cents < 0L -> "-"
        else -> ""
    }
    return formatAmountWithSign(sign, cents)
}

// One decimal place, matching the spec's own examples ("12.5%"), without ever showing a
// misleading sign - callers already choose "increased"/"decreased" wording, so this only formats
// a non-negative magnitude.
fun formatPercent(percent: Float): String {
    val hundredths = (abs(percent) * 10).roundToInt()
    return "${hundredths / 10}.${hundredths % 10}"
}

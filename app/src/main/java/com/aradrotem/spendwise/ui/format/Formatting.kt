package com.aradrotem.spendwise.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun formatDate(timestampMillis: Long): String =
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(dateFormatter)

// Formats cents as a decimal string without going through Double/Float.
fun formatAmountInCents(amountInCents: Long): String {
    val whole = amountInCents / 100
    val fraction = amountInCents % 100
    return "%d.%02d".format(whole, fraction)
}

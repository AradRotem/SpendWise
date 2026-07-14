package com.aradrotem.spendwise.ui.format

// Parses decimal text directly into cents to avoid Double/Float rounding on money.
// Returns null for blank/malformed text or for values that would overflow Long cents.
fun parseAmountToCents(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    val normalized = trimmed.replace(',', '.')
    val parts = normalized.split(".")
    if (parts.size > 2) return null

    val wholeDigits = parts[0].ifEmpty { "0" }
    val fractionDigits = if (parts.size == 2) parts[1] else ""

    if (!wholeDigits.all { it.isDigit() }) return null
    if (fractionDigits.length > 2 || !fractionDigits.all { it.isDigit() }) return null

    val whole = wholeDigits.toLongOrNull() ?: return null
    val fraction = fractionDigits.padEnd(2, '0').toLongOrNull() ?: return null

    return try {
        Math.addExact(Math.multiplyExact(whole, 100L), fraction)
    } catch (e: ArithmeticException) {
        null
    }
}

// Lets callers give a precise validation message for this specific case, instead of the
// generic "invalid amount" message parseAmountToCents' null return would otherwise imply.
fun hasTooManyDecimalPlaces(text: String): Boolean {
    val normalized = text.trim().replace(',', '.')
    val parts = normalized.split(".")
    return parts.size == 2 && parts[1].length > 2
}

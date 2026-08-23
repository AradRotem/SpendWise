package com.aradrotem.spendwise.ui.format

// Strips a leading currency symbol (and surrounding whitespace) so text produced by
// formatAmountInCents - e.g. when an existing transaction's amount is loaded back into an
// editable field - can round-trip back through parseAmountToCents/hasTooManyDecimalPlaces.
private fun stripCurrencySymbol(text: String): String = text.trim().removePrefix("₪").trim()

// Parses decimal text directly into cents to avoid Double/Float rounding on money.
// Returns null for blank/malformed text or for values that would overflow Long cents.
fun parseAmountToCents(text: String): Long? {
    val trimmed = stripCurrencySymbol(text)
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
    val normalized = stripCurrencySymbol(text).replace(',', '.')
    val parts = normalized.split(".")
    return parts.size == 2 && parts[1].length > 2
}

// Shared by every screen that collects a required, positive amount (AddTransactionViewModel,
// BudgetsViewModel, AddRecurringPlanViewModel's monthly-amount and installment-total fields) so
// they can't drift apart on validation rules or wording. Callers already need amountInCents
// themselves (to build the entity being saved), so it's passed in rather than re-parsed here.
// fieldLabel only varies the blank/zero messages ("Amount" vs "Total amount"); the malformed-input
// messages are identical everywhere.
fun validateAmount(text: String, amountInCents: Long?, fieldLabel: String = "Amount"): String? = when {
    text.isBlank() -> "$fieldLabel is required"
    hasTooManyDecimalPlaces(text) -> "Enter an amount with up to 2 decimal places."
    amountInCents == null -> "Enter a valid amount"
    amountInCents <= 0L -> "$fieldLabel must be greater than zero"
    else -> null
}

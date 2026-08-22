package com.aradrotem.spendwise.domain

// Pure conversion math, kept free of network/Room dependencies so it's directly unit-testable.
// Deliberately integer-cents in, integer-cents out - the rate itself is the only floating-point
// value involved, and only for this one multiplication, consistent with the rest of the app never
// storing money as Double.
object CurrencyConversion {
    fun convertToBaseCents(originalAmountCents: Long, rate: Double): Long =
        Math.round(originalAmountCents * rate)

    // Currencies Frankfurter (the exchange-rate API SpendWise uses) supports, as of the ECB
    // reference rates it's built on. Used both to populate the currency picker and to reject an
    // unsupported code before ever making a network call.
    val SUPPORTED_CURRENCIES = listOf(
        "USD", "EUR", "GBP", "ILS", "JPY", "CHF", "CAD", "AUD", "CNY", "INR", "SEK", "NOK", "DKK", "PLN", "TRY", "MXN", "ZAR", "SGD", "HKD", "NZD"
    )
}

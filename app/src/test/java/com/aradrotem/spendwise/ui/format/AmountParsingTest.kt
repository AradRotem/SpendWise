package com.aradrotem.spendwise.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountParsingTest {

    @Test
    fun parseAmountToCents_plainDecimal() {
        assertEquals(10_000L, parseAmountToCents("100.00"))
    }

    @Test
    fun parseAmountToCents_roundTripsFormatAmountInCentsOutput() {
        // formatAmountInCents prefixes ₪ (used to pre-fill the amount field when editing an
        // existing transaction) - parseAmountToCents must accept that same text back unchanged,
        // or every edit screen would show "Enter a valid amount" until the user retyped it.
        val formatted = formatAmountInCents(10_000L)
        assertEquals("₪100.00", formatted)
        assertEquals(10_000L, parseAmountToCents(formatted))
    }

    @Test
    fun parseAmountToCents_blank_returnsNull() {
        assertNull(parseAmountToCents(""))
        assertNull(parseAmountToCents("₪"))
    }

    @Test
    fun parseAmountToCents_malformed_returnsNull() {
        assertNull(parseAmountToCents("abc"))
        assertNull(parseAmountToCents("1.2.3"))
    }

    @Test
    fun hasTooManyDecimalPlaces_acceptsCurrencyPrefixedText() {
        assertTrue(hasTooManyDecimalPlaces("₪1.234"))
        assertEquals(false, hasTooManyDecimalPlaces("₪1.23"))
    }

    @Test
    fun formatAmountInCents_includesShekelSymbol() {
        assertEquals("₪0.00", formatAmountInCents(0L))
        assertEquals("₪12.50", formatAmountInCents(1_250L))
    }

    @Test
    fun validateAmount_blank_returnsRequiredMessage() {
        assertEquals("Amount is required", validateAmount("", parseAmountToCents("")))
    }

    @Test
    fun validateAmount_blank_usesCustomFieldLabel() {
        assertEquals("Total amount is required", validateAmount("", parseAmountToCents(""), fieldLabel = "Total amount"))
    }

    @Test
    fun validateAmount_tooManyDecimalPlaces_returnsSpecificMessage() {
        val text = "1.234"
        assertEquals("Enter an amount with up to 2 decimal places.", validateAmount(text, parseAmountToCents(text)))
    }

    @Test
    fun validateAmount_malformed_returnsGenericInvalidMessage() {
        val text = "abc"
        assertEquals("Enter a valid amount", validateAmount(text, parseAmountToCents(text)))
    }

    @Test
    fun validateAmount_zeroOrNegative_returnsGreaterThanZeroMessage() {
        assertEquals("Amount must be greater than zero", validateAmount("0.00", parseAmountToCents("0.00")))
    }

    @Test
    fun validateAmount_zeroOrNegative_usesCustomFieldLabel() {
        assertEquals(
            "Total amount must be greater than zero",
            validateAmount("0.00", parseAmountToCents("0.00"), fieldLabel = "Total amount")
        )
    }

    @Test
    fun validateAmount_valid_returnsNull() {
        val text = "42.50"
        assertNull(validateAmount(text, parseAmountToCents(text)))
    }

    @Test
    fun validateAmount_acceptsCurrencyPrefixedRoundTrip() {
        val formatted = formatAmountInCents(4_250L)
        assertNull(validateAmount(formatted, parseAmountToCents(formatted)))
    }
}

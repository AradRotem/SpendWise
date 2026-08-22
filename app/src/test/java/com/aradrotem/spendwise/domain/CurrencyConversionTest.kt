package com.aradrotem.spendwise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyConversionTest {

    @Test
    fun convertToBaseCents_appliesRate() {
        // 100.00 USD at 3.70 -> 370.00 base.
        assertEquals(37_000L, CurrencyConversion.convertToBaseCents(10_000L, 3.70))
    }

    @Test
    fun convertToBaseCents_roundsToNearestCent() {
        // 33.33 at 1.005 = 33.49965 -> rounds to 33.50 (3350 cents).
        assertEquals(3_350L, CurrencyConversion.convertToBaseCents(3_333L, 1.005))
    }

    @Test
    fun convertToBaseCents_identityRateUnchanged() {
        assertEquals(5_000L, CurrencyConversion.convertToBaseCents(5_000L, 1.0))
    }

    @Test
    fun supportedCurrencies_includesCommonCodes() {
        assertTrue("USD" in CurrencyConversion.SUPPORTED_CURRENCIES)
        assertTrue("ILS" in CurrencyConversion.SUPPORTED_CURRENCIES)
        assertTrue("EUR" in CurrencyConversion.SUPPORTED_CURRENCIES)
    }
}

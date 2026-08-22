package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.network.ExchangeRateApi
import com.aradrotem.spendwise.data.network.FrankfurterRateResponse
import java.io.IOException

// In-memory ExchangeRateApi used to unit-test RetrofitExchangeRateRepository without a real
// network call - mirrors the FakeTransactionDao/FakeBudgetDao style used elsewhere in the test
// tree (an in-memory implementation of the real interface, not a mocking framework).
class FakeExchangeRateApi : ExchangeRateApi {
    var rateToReturn: Double? = null
    var shouldThrow: Boolean = false
    // Simulates a malformed/unexpected API response: success, but the requested "to" currency is
    // absent from the returned rates map.
    var returnEmptyRates: Boolean = false

    override suspend fun getLatestRate(from: String, to: String): FrankfurterRateResponse {
        if (shouldThrow) throw IOException("network unavailable")
        if (returnEmptyRates) return FrankfurterRateResponse(amount = 1.0, base = from, date = "2026-08-16", rates = emptyMap())
        val rate = rateToReturn ?: throw IOException("no rate configured")
        return FrankfurterRateResponse(amount = 1.0, base = from, date = "2026-08-16", rates = mapOf(to to rate))
    }
}

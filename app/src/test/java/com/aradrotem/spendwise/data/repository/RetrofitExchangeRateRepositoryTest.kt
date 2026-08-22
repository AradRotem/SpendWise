package com.aradrotem.spendwise.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrofitExchangeRateRepositoryTest {

    @Test
    fun apiSuccess_returnsRateAndCachesIt() = runTest {
        val api = FakeExchangeRateApi().apply { rateToReturn = 3.7 }
        val cacheDao = FakeCachedExchangeRateDao()
        val repository = RetrofitExchangeRateRepository(api, cacheDao)

        val result = repository.getRate("USD", "ILS") as ExchangeRateResult.Success
        assertEquals(3.7, result.rate, 0.0001)
        assertTrue(!result.isFromCache)
        assertEquals(3.7, cacheDao.get("USD", "ILS")?.rate)
    }

    @Test
    fun apiFailure_noCachedRate_reportsUnavailable() = runTest {
        val api = FakeExchangeRateApi().apply { shouldThrow = true }
        val cacheDao = FakeCachedExchangeRateDao()
        val repository = RetrofitExchangeRateRepository(api, cacheDao)

        val result = repository.getRate("USD", "ILS")
        assertEquals(ExchangeRateResult.Unavailable, result)
    }

    @Test
    fun apiFailure_cachedRateExists_fallsBackToCache() = runTest {
        val api = FakeExchangeRateApi().apply { shouldThrow = true }
        val cacheDao = FakeCachedExchangeRateDao().apply {
            upsert(com.aradrotem.spendwise.data.local.CachedExchangeRateEntity(fromCurrency = "USD", toCurrency = "ILS", rate = 3.65, fetchedAtEpochMillis = 1_000L))
        }
        val repository = RetrofitExchangeRateRepository(api, cacheDao)

        val result = repository.getRate("USD", "ILS") as ExchangeRateResult.Success
        assertEquals(3.65, result.rate, 0.0001)
        assertTrue(result.isFromCache)
    }

    @Test
    fun unsupportedCurrency_rejectedWithoutNetworkCall() = runTest {
        val api = FakeExchangeRateApi()
        val cacheDao = FakeCachedExchangeRateDao()
        val repository = RetrofitExchangeRateRepository(api, cacheDao)

        val result = repository.getRate("XXX", "ILS")
        assertEquals(ExchangeRateResult.UnsupportedCurrency, result)
    }

    @Test
    fun sameCurrency_identityRateWithoutNetworkCall() = runTest {
        val api = FakeExchangeRateApi()
        val cacheDao = FakeCachedExchangeRateDao()
        val repository = RetrofitExchangeRateRepository(api, cacheDao)

        val result = repository.getRate("USD", "USD") as ExchangeRateResult.Success
        assertEquals(1.0, result.rate, 0.0001)
    }

    @Test
    fun apiMissingRequestedRateInResponse_fallsBackGracefully() = runTest {
        val api = FakeExchangeRateApi().apply { returnEmptyRates = true }
        val cacheDao = FakeCachedExchangeRateDao()
        val repository = RetrofitExchangeRateRepository(api, cacheDao)

        val result = repository.getRate("USD", "ILS")
        assertEquals(ExchangeRateResult.Unavailable, result)
    }
}

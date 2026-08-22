package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.CachedExchangeRateDao
import com.aradrotem.spendwise.data.local.CachedExchangeRateEntity
import com.aradrotem.spendwise.data.network.ExchangeRateApi
import com.aradrotem.spendwise.domain.CurrencyConversion
import kotlinx.coroutines.CancellationException

sealed class ExchangeRateResult {
    data class Success(val rate: Double, val fetchedAtEpochMillis: Long, val isFromCache: Boolean) : ExchangeRateResult()
    object Unavailable : ExchangeRateResult()
    object UnsupportedCurrency : ExchangeRateResult()
}

interface ExchangeRateRepository {
    suspend fun getRate(fromCurrency: String, toCurrency: String): ExchangeRateResult
}

// Never fabricates a rate: a network/API failure falls back to the most recent cached rate for
// the same pair if one exists, and only reports Unavailable when there is truly nothing to offer.
// A successful fetch always refreshes the cache, so the fallback stays as fresh as possible.
class RetrofitExchangeRateRepository(
    private val api: ExchangeRateApi,
    private val cacheDao: CachedExchangeRateDao
) : ExchangeRateRepository {

    override suspend fun getRate(fromCurrency: String, toCurrency: String): ExchangeRateResult {
        if (fromCurrency !in CurrencyConversion.SUPPORTED_CURRENCIES || toCurrency !in CurrencyConversion.SUPPORTED_CURRENCIES) {
            return ExchangeRateResult.UnsupportedCurrency
        }
        if (fromCurrency == toCurrency) {
            return ExchangeRateResult.Success(rate = 1.0, fetchedAtEpochMillis = System.currentTimeMillis(), isFromCache = false)
        }

        return try {
            val response = api.getLatestRate(fromCurrency, toCurrency)
            val rate = response.rates[toCurrency] ?: return fallbackToCache(fromCurrency, toCurrency)
            val now = System.currentTimeMillis()
            cacheDao.upsert(CachedExchangeRateEntity(fromCurrency = fromCurrency, toCurrency = toCurrency, rate = rate, fetchedAtEpochMillis = now))
            ExchangeRateResult.Success(rate, now, isFromCache = false)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            // Deliberately broad: any network/parsing failure (offline, DNS, timeout, malformed
            // response) degrades to the cache rather than crashing or propagating a raw exception
            // up to the UI - see Step 19's "never show a raw exception" requirement.
            fallbackToCache(fromCurrency, toCurrency)
        }
    }

    private suspend fun fallbackToCache(from: String, to: String): ExchangeRateResult {
        val cached = cacheDao.get(from, to) ?: return ExchangeRateResult.Unavailable
        return ExchangeRateResult.Success(cached.rate, cached.fetchedAtEpochMillis, isFromCache = true)
    }
}

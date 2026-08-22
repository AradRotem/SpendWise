package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.CachedExchangeRateDao
import com.aradrotem.spendwise.data.local.CachedExchangeRateEntity

class FakeCachedExchangeRateDao : CachedExchangeRateDao {
    private val rows = mutableMapOf<Pair<String, String>, CachedExchangeRateEntity>()

    override suspend fun upsert(entity: CachedExchangeRateEntity) {
        rows[entity.fromCurrency to entity.toCurrency] = entity
    }

    override suspend fun get(from: String, to: String): CachedExchangeRateEntity? = rows[from to to]
}

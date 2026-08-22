package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedExchangeRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedExchangeRateEntity)

    @Query("SELECT * FROM cached_exchange_rates WHERE fromCurrency = :from AND toCurrency = :to LIMIT 1")
    suspend fun get(from: String, to: String): CachedExchangeRateEntity?
}

package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Most recent successfully fetched rate for one currency pair, used as a fallback when a fresh
// rate can't be fetched (offline, API unavailable) - see ExchangeRateRepository. One row per pair;
// a new successful fetch overwrites the previous row rather than accumulating history, since only
// "the most recent known rate" is ever needed.
@Entity(
    tableName = "cached_exchange_rates",
    indices = [Index(value = ["fromCurrency", "toCurrency"], unique = true)]
)
data class CachedExchangeRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromCurrency: String,
    val toCurrency: String,
    val rate: Double,
    val fetchedAtEpochMillis: Long
)

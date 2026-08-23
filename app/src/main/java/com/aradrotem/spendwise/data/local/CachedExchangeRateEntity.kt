package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Most recent successfully fetched rate for one currency pair. One row per pair; a new successful
// fetch overwrites the previous row rather than accumulating history.
//
// SpendWise v1 is ILS-only and no longer performs currency conversion (the active conversion
// feature and its repository were removed) - this table and its DAO are kept, unused, purely for
// Room schema/backward-compatibility reasons: removing a registered @Database entity would change
// the schema identity and require a migration, which is unnecessary risk for a table that is
// otherwise empty and harmless to leave in place.
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

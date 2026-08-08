package com.aradrotem.spendwise.data.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Durable, per-record bookkeeping for the one-time legacy-database import (see
// LegacyImportManager). Each row means "the legacy row (entityType, legacyLocalId) has already
// been copied into this per-account database as newLocalId". Because this is a real table rather
// than an in-memory/flag-only marker, an import interrupted mid-way (process death, crash) can
// resume safely: every already-copied legacy row is skipped on retry, and its newLocalId is still
// available for remapping foreign keys in later stages.
@Entity(
    tableName = "legacy_import_map",
    indices = [Index(value = ["entityType", "legacyLocalId"], unique = true)]
)
data class LegacyImportRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val legacyLocalId: Long,
    val newLocalId: Long
)

package com.aradrotem.spendwise.data.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// One row per syncable local entity instance. Deliberately generic (one table for all 9 entity
// types) instead of adding sync columns to every entity table - see Step 17 plan for rationale.
// syncId is the stable cross-device identity (Firestore document id); localId/entityType is how
// this row maps back to a specific row in a specific Room table on this device only.
@Entity(
    tableName = "sync_metadata",
    indices = [
        Index(value = ["entityType", "localId"], unique = true),
        Index(value = ["syncId"], unique = true),
        Index(value = ["pendingSync"])
    ]
)
data class SyncMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val localId: Long,
    val syncId: String,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val pendingSync: Boolean = true
)

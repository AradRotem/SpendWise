package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Durable record of a Firebase Storage receipt object that still needs to be deleted - created
// when a receipt is removed/replaced or its owning transaction is deleted, in case the actual
// Storage delete call fails or the device is offline at that moment. Retried at the same trigger
// points as Firestore sync (see ReceiptRepository.retryPendingDeletions) until it succeeds, then
// the row is removed.
@Entity(tableName = "receipt_pending_deletions")
data class ReceiptPendingDeletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Durable record of a shared-group expense that was deleted locally and still needs its cloud
// counterpart deleted too - created at the moment of local deletion (see
// GroupExpenseRepository.deleteExpense), before the local row itself is gone, since the cloudId
// wouldn't be recoverable afterward. Retried at the same trigger points as SharedGroupSyncEngine's
// other work until it succeeds, then removed - exactly mirrors ReceiptPendingDeletionEntity's
// existing role for Firebase Storage deletes.
@Entity(tableName = "group_expense_pending_deletions")
data class GroupExpensePendingDeletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupSyncId: String,
    val cloudId: String,
    val createdAt: Long = System.currentTimeMillis()
)

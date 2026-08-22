package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_groups")
data class ExpenseGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),

    // Step 19: null for a purely local group (pre-Step-19 behavior, unchanged). Once a group is
    // upgraded to real multi-user sharing (see GroupCloudRepository.shareExistingGroup), this is
    // set to the canonical Firestore groups/{groupId} document id and never changes again - it is
    // the stable cross-device identity this local row's expenses/members reconcile against.
    val groupSyncId: String? = null,
    // The authenticated uid that owns/created the shared group. Null for a local-only group.
    val ownerUid: String? = null
) {
    val isSharedGroup: Boolean get() = groupSyncId != null
}

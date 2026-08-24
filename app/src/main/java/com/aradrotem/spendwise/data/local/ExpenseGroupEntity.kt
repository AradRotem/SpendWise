package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// The groupSyncId index is UNIQUE (added in MIGRATION_13_14) - the stable cloud identity a shared
// group's local row is keyed by, so at most one local row may ever mirror one Firestore
// groups/{groupId} doc. See GroupExpenseRepository.getOrCreateLocalGroupForSync for why this must
// be enforced at the database level, not just checked in application code: two overlapping Sync
// passes both racing to look up "does a local row for this groupSyncId already exist?" before
// either has inserted one is a real, previously-unguarded race that produced duplicate groups.
@Entity(tableName = "expense_groups", indices = [Index(value = ["groupSyncId"], unique = true)])
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

package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupSplitMethod

// One groups/{groupId}/expenses/{cloudId} Firestore document, already deserialized. Members are
// referenced by uid (not local Room id) since a shared expense's payer/participants are real
// authenticated accounts, not local-only representations.
data class RemoteGroupExpense(
    val cloudId: String,
    val title: String,
    val amountCents: Long,
    val dateEpochDay: Long,
    val paidByUid: String,
    val splitMethod: GroupSplitMethod,
    val note: String,
    val createdAtEpochMillis: Long,
    val createdByUid: String,
    // uid -> share amount in cents.
    val shares: Map<String, Long>
)

data class ReconciledExpense(
    val cloudId: String,
    // Null means "insert a new local row"; non-null means "update this existing local row in place".
    val existingLocalId: Long?,
    val entity: GroupExpenseEntity,
    // local Room memberId -> share amount in cents.
    val shares: Map<Long, Long>
)

// Pure reconciliation logic for pulling a shared group's expenses from Firestore into local Room -
// kept free of Room/Firestore dependencies so it's directly unit-testable (see
// GroupExpenseReconcilerTest). A remote expense whose payer/a participant hasn't accepted their
// invitation yet (no local member row for their uid) is skipped entirely rather than partially
// applied, since a share that silently drops a participant would misrepresent the split.
object GroupExpenseReconciler {

    fun reconcile(
        groupId: Long,
        remoteDocs: List<RemoteGroupExpense>,
        localExpensesByCloudId: Map<String, GroupExpenseEntity>,
        memberUidToLocalId: Map<String, Long>
    ): List<ReconciledExpense> = remoteDocs.mapNotNull { remote ->
        val paidByLocalId = memberUidToLocalId[remote.paidByUid] ?: return@mapNotNull null
        val shares = mutableMapOf<Long, Long>()
        for ((uid, cents) in remote.shares) {
            val localId = memberUidToLocalId[uid] ?: return@mapNotNull null
            shares[localId] = cents
        }

        val existing = localExpensesByCloudId[remote.cloudId]
        ReconciledExpense(
            cloudId = remote.cloudId,
            existingLocalId = existing?.id,
            entity = GroupExpenseEntity(
                id = existing?.id ?: 0L,
                groupId = groupId,
                title = remote.title,
                amountCents = remote.amountCents,
                dateEpochDay = remote.dateEpochDay,
                paidByMemberId = paidByLocalId,
                splitMethod = remote.splitMethod,
                note = remote.note,
                createdAtEpochMillis = remote.createdAtEpochMillis,
                cloudId = remote.cloudId,
                createdByUid = remote.createdByUid
            ),
            shares = shares
        )
    }

    // Previously-synced local expenses (tagged with a cloudId) that no longer appear in the
    // current full remote snapshot - their absence from an authoritative full pull is itself the
    // deletion signal, so no separate tombstone bookkeeping is needed.
    fun localIdsToDelete(localExpensesByCloudId: Map<String, GroupExpenseEntity>, remoteCloudIds: Set<String>): List<Long> =
        localExpensesByCloudId.filterKeys { it !in remoteCloudIds }.values.map { it.id }
}

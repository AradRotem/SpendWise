package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.GroupExpenseDao
import com.aradrotem.spendwise.data.local.GroupExpenseShareEntity
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncIdResolver

class GroupExpenseShareSyncAdapter(
    private val dao: GroupExpenseDao,
    private val resolver: SyncIdResolver,
    uid: String
) : EntitySyncAdapter<GroupExpenseShareEntity> {

    override val entityType = SyncEntityType.GROUP_EXPENSE_SHARE
    override val collectionPath = "users/$uid/groupExpenseShares"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val share = dao.getShareById(localId) ?: return null
        return mapOf(
            "groupExpenseSyncId" to resolver.syncIdFor(SyncEntityType.GROUP_EXPENSE, share.expenseId),
            "memberSyncId" to resolver.syncIdFor(SyncEntityType.GROUP_MEMBER, share.memberId),
            "shareAmountCents" to share.shareAmountCents
        )
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long? {
        val expenseId = resolver.localIdFor(SyncEntityType.GROUP_EXPENSE, data["groupExpenseSyncId"] as? String)
        val memberId = resolver.localIdFor(SyncEntityType.GROUP_MEMBER, data["memberSyncId"] as? String)
        // Defer rather than throw if the parent expense or member isn't locally available yet -
        // see EntitySyncAdapter.applyRemoteUpsert / RecurringExceptionSyncAdapter. Also defer (do
        // not crash) if the resolver claims the expense exists but the local row is actually
        // gone - a real but non-fatal inconsistency, safest handled the same way as "not yet
        // available" rather than throwing.
        if (expenseId == null || memberId == null) return null
        if (existingLocalId != null) return existingLocalId // shares are replaced wholesale by the expense adapter, not individually updated
        val existingShares = dao.getSharesForExpense(expenseId)
        val expense = dao.getById(expenseId) ?: return null
        val shareAmountCents = (data["shareAmountCents"] as Number).toLong()
        val updatedShares = existingShares.filterNot { it.memberId == memberId } +
            GroupExpenseShareEntity(expenseId = expenseId, memberId = memberId, shareAmountCents = shareAmountCents)
        dao.updateExpenseWithShares(expense, updatedShares)
        return dao.getSharesForExpense(expenseId).first { it.memberId == memberId }.id
    }

    override suspend fun applyRemoteDelete(localId: Long) {
        val share = dao.getShareById(localId) ?: return
        val expense = dao.getById(share.expenseId) ?: return
        val remainingShares = dao.getSharesForExpense(share.expenseId).filterNot { it.id == localId }
        dao.updateExpenseWithShares(expense, remainingShares)
    }
}

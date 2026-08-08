package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.GroupExpenseDao
import com.aradrotem.spendwise.data.local.GroupExpenseShareEntity
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncIdResolver
import kotlinx.coroutines.flow.first

class GroupExpenseShareSyncAdapter(
    private val dao: GroupExpenseDao,
    private val resolver: SyncIdResolver,
    uid: String
) : EntitySyncAdapter<GroupExpenseShareEntity> {

    override val entityType = SyncEntityType.GROUP_EXPENSE_SHARE
    override val collectionPath = "users/$uid/groupExpenseShares"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val share = findById(localId) ?: return null
        return mapOf(
            "groupExpenseSyncId" to resolver.syncIdFor(SyncEntityType.GROUP_EXPENSE, share.expenseId),
            "memberSyncId" to resolver.syncIdFor(SyncEntityType.GROUP_MEMBER, share.memberId),
            "shareAmountCents" to share.shareAmountCents
        )
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long {
        val expenseId = resolver.localIdFor(SyncEntityType.GROUP_EXPENSE, data["groupExpenseSyncId"] as? String)
        val memberId = resolver.localIdFor(SyncEntityType.GROUP_MEMBER, data["memberSyncId"] as? String)
        checkNotNull(expenseId) { "Expense for share $syncId not yet available locally" }
        checkNotNull(memberId) { "Member for share $syncId not yet available locally" }
        if (existingLocalId != null) return existingLocalId // shares are replaced wholesale by the expense adapter, not individually updated
        val existingShares = dao.getSharesForExpense(expenseId)
        val expense = dao.getById(expenseId) ?: error("Expense $expenseId missing for share $syncId")
        val shareAmountCents = (data["shareAmountCents"] as Number).toLong()
        val updatedShares = existingShares.filterNot { it.memberId == memberId } +
            GroupExpenseShareEntity(expenseId = expenseId, memberId = memberId, shareAmountCents = shareAmountCents)
        dao.updateExpenseWithShares(expense, updatedShares)
        return dao.getSharesForExpense(expenseId).first { it.memberId == memberId }.id
    }

    override suspend fun applyRemoteDelete(localId: Long) {
        val share = findById(localId) ?: return
        val expense = dao.getById(share.expenseId) ?: return
        val remainingShares = dao.getSharesForExpense(share.expenseId).filterNot { it.id == localId }
        dao.updateExpenseWithShares(expense, remainingShares)
    }

    private suspend fun findById(localId: Long): GroupExpenseShareEntity? =
        dao.observeAllShares().first().firstOrNull { it.id == localId }
}

package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.GroupExpenseDao
import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncIdResolver

// Pushes/pulls only the expense row itself - its shares are a separate entity type/collection
// (GroupExpenseShareSyncAdapter), synced independently, same as they're separate Room tables.
class GroupExpenseSyncAdapter(
    private val dao: GroupExpenseDao,
    private val resolver: SyncIdResolver,
    uid: String
) : EntitySyncAdapter<GroupExpenseEntity> {

    override val entityType = SyncEntityType.GROUP_EXPENSE
    override val collectionPath = "users/$uid/groupExpenses"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val expense = dao.getById(localId) ?: return null
        return mapOf(
            "groupSyncId" to resolver.syncIdFor(SyncEntityType.GROUP, expense.groupId),
            "title" to expense.title,
            "amountCents" to expense.amountCents,
            "dateEpochDay" to expense.dateEpochDay,
            "paidByMemberSyncId" to resolver.syncIdFor(SyncEntityType.GROUP_MEMBER, expense.paidByMemberId),
            "splitMethod" to expense.splitMethod.name,
            "note" to expense.note,
            "createdAtEpochMillis" to expense.createdAtEpochMillis
        )
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long? {
        val groupId = resolver.localIdFor(SyncEntityType.GROUP, data["groupSyncId"] as? String)
        val paidByMemberId = resolver.localIdFor(SyncEntityType.GROUP_MEMBER, data["paidByMemberSyncId"] as? String)
        // Defer rather than throw if the parent group or payer member isn't locally available
        // yet - see EntitySyncAdapter.applyRemoteUpsert / RecurringExceptionSyncAdapter.
        if (groupId == null || paidByMemberId == null) return null
        val entity = GroupExpenseEntity(
            id = existingLocalId ?: 0,
            groupId = groupId,
            title = data["title"] as String,
            amountCents = (data["amountCents"] as Number).toLong(),
            dateEpochDay = (data["dateEpochDay"] as Number).toLong(),
            paidByMemberId = paidByMemberId,
            splitMethod = GroupSplitMethod.valueOf(data["splitMethod"] as String),
            note = data["note"] as? String ?: "",
            createdAtEpochMillis = (data["createdAtEpochMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
        return if (existingLocalId != null) {
            // updateExpenseWithShares also rewrites shares; shares are pulled/applied
            // independently via GroupExpenseShareSyncAdapter, so pass through the existing ones
            // unchanged here to avoid clobbering them mid-pull.
            dao.updateExpenseWithShares(entity, dao.getSharesForExpense(existingLocalId))
            existingLocalId
        } else {
            dao.insertExpenseWithShares(entity, emptyList())
        }
    }

    override suspend fun applyRemoteDelete(localId: Long) {
        dao.getById(localId)?.let { dao.deleteExpense(it) }
    }
}

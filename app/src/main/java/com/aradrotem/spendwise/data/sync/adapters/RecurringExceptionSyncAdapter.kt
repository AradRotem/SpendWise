package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.OccurrenceExceptionType
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionDao
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionEntity
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncIdResolver

class RecurringExceptionSyncAdapter(
    private val dao: RecurringOccurrenceExceptionDao,
    private val resolver: SyncIdResolver,
    uid: String
) : EntitySyncAdapter<RecurringOccurrenceExceptionEntity> {

    override val entityType = SyncEntityType.RECURRING_EXCEPTION
    override val collectionPath = "users/$uid/recurringOccurrenceExceptions"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val exception = dao.getById(localId) ?: return null
        return mapOf(
            "recurringPlanSyncId" to resolver.syncIdFor(SyncEntityType.RECURRING_PLAN, exception.recurringPlanId),
            "scheduledYearMonth" to exception.scheduledYearMonth,
            "exceptionType" to exception.exceptionType.name,
            "createdAt" to exception.createdAt
        )
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long? {
        val recurringPlanSyncId = data["recurringPlanSyncId"] as? String
        val recurringPlanId = resolver.localIdFor(SyncEntityType.RECURRING_PLAN, recurringPlanSyncId)
        // The parent plan hasn't been pulled onto this device yet (a stale/out-of-step watermark,
        // a same-batch pull ordering hiccup, or - if it never arrives - a genuinely orphaned
        // remote exception). Deferring here (see EntitySyncAdapter.applyRemoteUpsert) instead of
        // throwing is what lets the rest of this sync pass - including unrelated entity types and
        // Step 19's shared-group sync - complete normally rather than the whole sync aborting on
        // one out-of-order or orphaned row.
        if (recurringPlanId == null) return null
        if (existingLocalId != null) return existingLocalId // immutable once created; nothing to update
        return dao.insert(
            RecurringOccurrenceExceptionEntity(
                recurringPlanId = recurringPlanId,
                scheduledYearMonth = data["scheduledYearMonth"] as String,
                exceptionType = OccurrenceExceptionType.valueOf(data["exceptionType"] as String),
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        )
    }

    override suspend fun applyRemoteDelete(localId: Long) = Unit // exceptions are never individually deleted
}

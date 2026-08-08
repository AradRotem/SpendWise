package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.OccurrenceExceptionType
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionDao
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionEntity
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncIdResolver
import kotlinx.coroutines.flow.first

class RecurringExceptionSyncAdapter(
    private val dao: RecurringOccurrenceExceptionDao,
    private val resolver: SyncIdResolver,
    uid: String
) : EntitySyncAdapter<RecurringOccurrenceExceptionEntity> {

    override val entityType = SyncEntityType.RECURRING_EXCEPTION
    override val collectionPath = "users/$uid/recurringOccurrenceExceptions"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val exception = findById(localId) ?: return null
        return mapOf(
            "recurringPlanSyncId" to resolver.syncIdFor(SyncEntityType.RECURRING_PLAN, exception.recurringPlanId),
            "scheduledYearMonth" to exception.scheduledYearMonth,
            "exceptionType" to exception.exceptionType.name,
            "createdAt" to exception.createdAt
        )
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long {
        val recurringPlanSyncId = data["recurringPlanSyncId"] as? String
        val recurringPlanId = resolver.localIdFor(SyncEntityType.RECURRING_PLAN, recurringPlanSyncId)
        checkNotNull(recurringPlanId) { "Recurring plan for exception $syncId not yet available locally" }
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

    private suspend fun findById(localId: Long): RecurringOccurrenceExceptionEntity? =
        dao.observeAll().first().firstOrNull { it.id == localId }
}

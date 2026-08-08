package com.aradrotem.spendwise.data.sync.adapters

import com.aradrotem.spendwise.data.local.TransactionDao
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.sync.EntitySyncAdapter
import com.aradrotem.spendwise.data.sync.SyncEntityType
import com.aradrotem.spendwise.data.sync.SyncIdResolver

class TransactionSyncAdapter(
    private val dao: TransactionDao,
    private val resolver: SyncIdResolver,
    uid: String
) : EntitySyncAdapter<TransactionEntity> {

    override val entityType = SyncEntityType.TRANSACTION
    override val collectionPath = "users/$uid/transactions"

    override suspend fun loadForPush(localId: Long): Map<String, Any?>? {
        val tx = dao.getById(localId) ?: return null
        return mapOf(
            "amountInCents" to tx.amountInCents,
            "type" to tx.type.name,
            "category" to tx.category,
            "timestamp" to tx.timestamp,
            "note" to tx.note,
            "recurringPlanSyncId" to resolver.syncIdFor(SyncEntityType.RECURRING_PLAN, tx.recurringPlanId),
            "installmentNumber" to tx.installmentNumber,
            "totalInstallments" to tx.totalInstallments,
            "isAutomaticallyGenerated" to tx.isAutomaticallyGenerated,
            "scheduledYearMonth" to tx.scheduledYearMonth,
            "sourceTitle" to tx.sourceTitle,
            "isOccurrenceModified" to tx.isOccurrenceModified
        )
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long {
        val recurringPlanSyncId = data["recurringPlanSyncId"] as? String
        val recurringPlanId = resolver.localIdFor(SyncEntityType.RECURRING_PLAN, recurringPlanSyncId)
        check(recurringPlanSyncId == null || recurringPlanId != null) {
            "Recurring plan for transaction $syncId not yet available locally"
        }
        val entity = TransactionEntity(
            id = existingLocalId ?: 0,
            amountInCents = (data["amountInCents"] as Number).toLong(),
            type = TransactionType.valueOf(data["type"] as String),
            category = data["category"] as String,
            timestamp = (data["timestamp"] as Number).toLong(),
            note = data["note"] as? String ?: "",
            recurringPlanId = recurringPlanId,
            installmentNumber = (data["installmentNumber"] as? Number)?.toInt(),
            totalInstallments = (data["totalInstallments"] as? Number)?.toInt(),
            isAutomaticallyGenerated = data["isAutomaticallyGenerated"] as? Boolean ?: false,
            scheduledYearMonth = data["scheduledYearMonth"] as? String,
            sourceTitle = data["sourceTitle"] as? String,
            isOccurrenceModified = data["isOccurrenceModified"] as? Boolean ?: false
        )
        return if (existingLocalId != null) {
            dao.update(entity)
            existingLocalId
        } else {
            dao.insert(entity)
        }
    }

    override suspend fun applyRemoteDelete(localId: Long) {
        dao.getById(localId)?.let { dao.delete(it) }
    }
}

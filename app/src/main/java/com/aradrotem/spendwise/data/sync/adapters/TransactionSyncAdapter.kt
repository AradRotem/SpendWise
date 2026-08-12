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
            "isOccurrenceModified" to tx.isOccurrenceModified,
            // Receipt: metadata/reference only, never the image bytes themselves (those live in
            // Firebase Storage - see ReceiptStorageRepository). receiptLocalUri and
            // receiptUploadPending are deliberately device-local and never synced: a storage path
            // is only pushed once the upload actually succeeded, so a second device only ever
            // sees a receiptStoragePath that's genuinely resolvable via Storage.
            "receiptId" to tx.receiptId,
            "receiptStoragePath" to tx.receiptStoragePath,
            "receiptMimeType" to tx.receiptMimeType,
            "receiptUpdatedAt" to tx.receiptUpdatedAt
        )
    }

    override suspend fun applyRemoteUpsert(syncId: String, data: Map<String, Any?>, existingLocalId: Long?): Long {
        val recurringPlanSyncId = data["recurringPlanSyncId"] as? String
        val recurringPlanId = resolver.localIdFor(SyncEntityType.RECURRING_PLAN, recurringPlanSyncId)
        check(recurringPlanSyncId == null || recurringPlanId != null) {
            "Recurring plan for transaction $syncId not yet available locally"
        }
        val remoteReceiptId = data["receiptId"] as? String
        val existing = existingLocalId?.let { dao.getById(it) }
        // A pull frequently reflects this same device's own just-pushed write (e.g. after this
        // device uploaded a receipt) - in that case receiptId is unchanged, so the local file
        // cache/pending-upload state must be preserved rather than wiped. Only a genuinely
        // different remote receiptId (a real change from elsewhere) clears the local cache, since
        // it would otherwise point at the wrong image.
        val sameReceiptAsBefore = existing != null && existing.receiptId == remoteReceiptId

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
            isOccurrenceModified = data["isOccurrenceModified"] as? Boolean ?: false,
            receiptId = remoteReceiptId,
            receiptStoragePath = data["receiptStoragePath"] as? String,
            receiptLocalUri = if (sameReceiptAsBefore) existing?.receiptLocalUri else null,
            receiptMimeType = data["receiptMimeType"] as? String,
            receiptUpdatedAt = (data["receiptUpdatedAt"] as? Number)?.toLong(),
            receiptUploadPending = if (sameReceiptAsBefore) existing?.receiptUploadPending ?: false else false
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

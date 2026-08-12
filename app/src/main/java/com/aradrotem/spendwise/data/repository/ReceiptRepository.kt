package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.ReceiptPendingDeletionDao
import com.aradrotem.spendwise.data.local.ReceiptPendingDeletionEntity
import com.aradrotem.spendwise.data.receipt.ReceiptStorageRepository
import java.io.File
import java.util.UUID

// Orchestrates attaching/replacing/removing a transaction's receipt across Room (source of truth,
// always updated first so the transaction stays fully usable offline) and Firebase Storage (best
// effort, retried later on failure - see retryPendingUploads/retryPendingDeletions). Keeps
// Firebase Storage calls out of ViewModels/Composables, mirroring AuthRepository/SyncEngine.
class ReceiptRepository(
    private val transactionRepository: TransactionRepository,
    private val receiptStorageRepository: ReceiptStorageRepository,
    private val receiptPendingDeletionDao: ReceiptPendingDeletionDao,
    private val uid: String
) {
    // Attaches a brand-new receipt to a transaction that has none yet. The local update happens
    // unconditionally first (offline-first: the transaction is immediately usable/viewable with
    // its receipt regardless of network), then upload is attempted best-effort.
    suspend fun attachReceipt(transactionId: Long, file: File, mimeType: String): Result<Unit> {
        val transaction = transactionRepository.getById(transactionId)
            ?: return Result.failure(IllegalStateException("Transaction not found"))
        val receiptId = UUID.randomUUID().toString()
        transactionRepository.update(
            transaction.copy(
                receiptId = receiptId,
                receiptLocalUri = file.absolutePath,
                receiptMimeType = mimeType,
                receiptStoragePath = null,
                receiptUpdatedAt = System.currentTimeMillis(),
                receiptUploadPending = true
            )
        )
        return tryUpload(transactionId, receiptId, file, mimeType)
    }

    // Preferred sequence (see Step 18 spec): acquire+upload the new image first, only remove the
    // old cloud object once the new reference is durable - never delete-then-upload, so a failed
    // upload can never leave the transaction with no receipt at all.
    suspend fun replaceReceipt(transactionId: Long, file: File, mimeType: String): Result<Unit> {
        val transaction = transactionRepository.getById(transactionId)
            ?: return Result.failure(IllegalStateException("Transaction not found"))
        val oldStoragePath = transaction.receiptStoragePath
        val newReceiptId = UUID.randomUUID().toString()

        transactionRepository.update(
            transaction.copy(
                receiptId = newReceiptId,
                receiptLocalUri = file.absolutePath,
                receiptMimeType = mimeType,
                receiptStoragePath = null,
                receiptUpdatedAt = System.currentTimeMillis(),
                receiptUploadPending = true
            )
        )

        val uploadResult = tryUpload(transactionId, newReceiptId, file, mimeType)
        if (uploadResult.isSuccess && oldStoragePath != null) {
            deleteOrEnqueueRetry(oldStoragePath)
        }
        return uploadResult
    }

    suspend fun removeReceipt(transactionId: Long): Result<Unit> {
        val transaction = transactionRepository.getById(transactionId)
            ?: return Result.failure(IllegalStateException("Transaction not found"))
        val storagePath = transaction.receiptStoragePath

        transactionRepository.update(
            transaction.copy(
                receiptId = null,
                receiptStoragePath = null,
                receiptLocalUri = null,
                receiptMimeType = null,
                receiptUpdatedAt = System.currentTimeMillis(),
                receiptUploadPending = false
            )
        )

        if (storagePath != null) {
            deleteOrEnqueueRetry(storagePath)
        }
        return Result.success(Unit)
    }

    // Called from the same trigger points as SyncEngine.sync() (app resume, connectivity
    // regained, manual "Sync now") - see SpendWiseApplication.triggerSync.
    suspend fun retryPendingUploads() {
        for (transaction in transactionRepository.getPendingReceiptUploads()) {
            val receiptId = transaction.receiptId ?: continue
            val localUri = transaction.receiptLocalUri ?: continue
            val mimeType = transaction.receiptMimeType ?: DEFAULT_MIME_TYPE
            val file = File(localUri)
            if (!file.exists()) continue
            tryUpload(transaction.id, receiptId, file, mimeType)
        }
    }

    suspend fun retryPendingDeletions() {
        for (entry in receiptPendingDeletionDao.getAll()) {
            receiptStorageRepository.deleteReceipt(entry.storagePath)
                .onSuccess { receiptPendingDeletionDao.deleteById(entry.id) }
        }
    }

    // Called when a transaction with a receipt is deleted - see TransactionRepository.delete.
    suspend fun enqueueReceiptCleanup(storagePath: String) {
        deleteOrEnqueueRetry(storagePath)
    }

    // Resolves a display URL for a receipt this device has no local file cache for (e.g. just
    // pulled from another device). Passthrough so callers never need ReceiptStorageRepository
    // directly - mirrors why UI/ViewModels only ever see ReceiptRepository.
    suspend fun getDownloadUrl(storagePath: String): Result<String> =
        receiptStorageRepository.getDownloadUrl(storagePath)

    private suspend fun tryUpload(transactionId: Long, receiptId: String, file: File, mimeType: String): Result<Unit> {
        val uploadResult = receiptStorageRepository.uploadReceipt(uid, receiptId, file, mimeType)
        return uploadResult.fold(
            onSuccess = { storagePath ->
                // Re-fetch in case the receipt was replaced/removed again while this upload was
                // in flight - only apply the result if it's still the same receipt.
                val current = transactionRepository.getById(transactionId)
                if (current != null && current.receiptId == receiptId) {
                    transactionRepository.update(
                        current.copy(
                            receiptStoragePath = storagePath,
                            receiptUploadPending = false,
                            receiptUpdatedAt = System.currentTimeMillis()
                        )
                    )
                }
                Result.success(Unit)
            },
            onFailure = { error -> Result.failure(error) } // leaves receiptUploadPending = true for a later retry
        )
    }

    private suspend fun deleteOrEnqueueRetry(storagePath: String) {
        val result = receiptStorageRepository.deleteReceipt(storagePath)
        if (result.isFailure) {
            receiptPendingDeletionDao.insert(ReceiptPendingDeletionEntity(storagePath = storagePath))
        }
    }

    companion object {
        private const val DEFAULT_MIME_TYPE = "image/jpeg"
    }
}

package com.aradrotem.spendwise.data.receipt

import java.io.File

// Used in place of FirebaseReceiptStorageRepository when this build has no google-services.json
// (see FirebaseAvailability) - every operation fails with a clear "Storage isn't set up" error
// instead of crashing or silently pretending to succeed.
class FirebaseUnavailableReceiptStorageRepository : ReceiptStorageRepository {
    override suspend fun uploadReceipt(uid: String, receiptId: String, file: File, mimeType: String): Result<String> =
        Result.failure(ReceiptError.StorageUnavailable)

    override suspend fun getDownloadUrl(storagePath: String): Result<String> =
        Result.failure(ReceiptError.StorageUnavailable)

    override suspend fun deleteReceipt(storagePath: String): Result<Unit> =
        Result.failure(ReceiptError.StorageUnavailable)
}

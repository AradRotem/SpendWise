package com.aradrotem.spendwise.data.receipt

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.File

// The only class in the app that touches FirebaseStorage directly.
class FirebaseReceiptStorageRepository(private val storage: FirebaseStorage) : ReceiptStorageRepository {

    override suspend fun uploadReceipt(uid: String, receiptId: String, file: File, mimeType: String): Result<String> =
        runStorageCatching {
            val storagePath = "users/$uid/receipts/$receiptId/receipt.${extensionForMimeType(mimeType)}"
            val metadata = StorageMetadata.Builder().setContentType(mimeType).build()
            storage.reference.child(storagePath).putFile(Uri.fromFile(file), metadata).await()
            storagePath
        }

    override suspend fun getDownloadUrl(storagePath: String): Result<String> =
        runStorageCatching {
            storage.reference.child(storagePath).downloadUrl.await().toString()
        }

    override suspend fun deleteReceipt(storagePath: String): Result<Unit> =
        try {
            storage.reference.child(storagePath).delete().await()
            Result.success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: StorageException) {
            // Already gone is a success from the caller's perspective - nothing left to clean up.
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) Result.success(Unit) else Result.failure(mapStorageError(e))
        } catch (e: Exception) {
            Result.failure(mapStorageError(e))
        }

    private inline fun <T> runStorageCatching(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Result.failure(mapStorageError(e))
        }
    }
}

internal fun mapStorageError(e: Exception): ReceiptError = when {
    e is StorageException && e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND -> ReceiptError.NotFound
    e is StorageException && e.errorCode == StorageException.ERROR_NOT_AUTHENTICATED -> ReceiptError.PermissionDenied
    e is StorageException && e.errorCode == StorageException.ERROR_NOT_AUTHORIZED -> ReceiptError.PermissionDenied
    e is StorageException && e.errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> ReceiptError.NetworkError
    e is StorageException && e.errorCode == StorageException.ERROR_QUOTA_EXCEEDED -> ReceiptError.NetworkError
    else -> ReceiptError.Unknown(e.message ?: "Something went wrong. Please try again.")
}

internal fun extensionForMimeType(mimeType: String): String = when (mimeType) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    else -> "jpg"
}

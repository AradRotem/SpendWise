package com.aradrotem.spendwise.data.receipt

import java.io.File

// The sole abstraction over Firebase Storage for receipts. No Composable, ViewModel, or
// repository outside this package touches FirebaseStorage/StorageReference directly - mirrors
// how data.auth.AuthRepository hides FirebaseAuth.
interface ReceiptStorageRepository {

    // Uploads [file] as the receipt identified by [receiptId] for [uid], returning the durable
    // Storage object path on success (e.g. "users/{uid}/receipts/{receiptId}/receipt.jpg") - not
    // a download URL, which can expire/rotate and isn't a stable identity.
    suspend fun uploadReceipt(uid: String, receiptId: String, file: File, mimeType: String): Result<String>

    // Resolves a durable storage path to a (possibly short-lived) display URL.
    suspend fun getDownloadUrl(storagePath: String): Result<String>

    suspend fun deleteReceipt(storagePath: String): Result<Unit>
}

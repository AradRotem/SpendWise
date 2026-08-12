package com.aradrotem.spendwise.receipt

import com.aradrotem.spendwise.data.receipt.ReceiptError
import com.aradrotem.spendwise.data.receipt.ReceiptStorageRepository
import java.io.File

// In-memory ReceiptStorageRepository test double - no real Firebase Storage involved.
class FakeReceiptStorageRepository : ReceiptStorageRepository {

    private val uploaded = mutableSetOf<String>()

    var nextUploadResult: Result<String>? = null // null = auto-succeed with a derived path
    var nextDeleteResult: Result<Unit> = Result.success(Unit)
    var uploadCallCount = 0
        private set
    var deleteCallCount = 0
        private set
    val deletedPaths = mutableListOf<String>()

    override suspend fun uploadReceipt(uid: String, receiptId: String, file: File, mimeType: String): Result<String> {
        uploadCallCount++
        val scripted = nextUploadResult
        if (scripted != null) return scripted
        val storagePath = "users/$uid/receipts/$receiptId/receipt.jpg"
        uploaded += storagePath
        return Result.success(storagePath)
    }

    override suspend fun getDownloadUrl(storagePath: String): Result<String> =
        if (storagePath in uploaded) Result.success("https://example.com/$storagePath") else Result.failure(ReceiptError.NotFound)

    override suspend fun deleteReceipt(storagePath: String): Result<Unit> {
        deleteCallCount++
        deletedPaths += storagePath
        uploaded -= storagePath
        return nextDeleteResult
    }
}

package com.aradrotem.spendwise.receipt

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.sync.SyncIdResolver
import com.aradrotem.spendwise.data.sync.adapters.TransactionSyncAdapter
import com.aradrotem.spendwise.domain.FakeTransactionDao
import com.aradrotem.spendwise.sync.FakeSyncMetadataDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSyncAdapterReceiptTest {

    @Test
    fun loadForPush_includesReceiptMetadata_andNeverRawImageBytes() = runBlocking {
        val dao = FakeTransactionDao()
        val adapter = TransactionSyncAdapter(dao, SyncIdResolver(FakeSyncMetadataDao()), uid = "uid-1")
        val id = dao.insert(
            TransactionEntity(
                amountInCents = 500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1L,
                receiptId = "receipt-1", receiptStoragePath = "users/uid-1/receipts/receipt-1/receipt.jpg",
                receiptLocalUri = "/data/local/cache/receipt-1.jpg", receiptMimeType = "image/jpeg", receiptUpdatedAt = 42L
            )
        )

        val pushed = adapter.loadForPush(id)

        assertEquals("receipt-1", pushed?.get("receiptId"))
        assertEquals("users/uid-1/receipts/receipt-1/receipt.jpg", pushed?.get("receiptStoragePath"))
        assertEquals("image/jpeg", pushed?.get("receiptMimeType"))
        assertEquals(42L, pushed?.get("receiptUpdatedAt"))
        // Local-only fields never leave the device.
        assertFalse(pushed!!.containsKey("receiptLocalUri"))
        assertFalse(pushed.containsKey("receiptUploadPending"))
        // No key holds anything resembling raw image bytes (a ByteArray/Bitmap) - every value is
        // a primitive/string, confirming only metadata/reference fields are ever pushed.
        assertTrue(pushed.values.all { it == null || it is String || it is Number || it is Boolean })
    }

    @Test
    fun applyRemoteUpsert_newRemoteTransaction_resolvesReceiptReferenceWithoutLocalCache() = runBlocking {
        val dao = FakeTransactionDao()
        val adapter = TransactionSyncAdapter(dao, SyncIdResolver(FakeSyncMetadataDao()), uid = "uid-1")

        val localId = adapter.applyRemoteUpsert(
            syncId = "sync-1",
            data = mapOf(
                "amountInCents" to 500L, "type" to "EXPENSE", "category" to "FOOD", "timestamp" to 1L,
                "receiptId" to "receipt-1", "receiptStoragePath" to "users/uid-1/receipts/receipt-1/receipt.jpg",
                "receiptMimeType" to "image/jpeg", "receiptUpdatedAt" to 42L
            ),
            existingLocalId = null
        )

        val applied = dao.getById(localId)
        assertEquals("receipt-1", applied?.receiptId)
        assertEquals("users/uid-1/receipts/receipt-1/receipt.jpg", applied?.receiptStoragePath)
        // A second device has no local file cache for a receipt it never uploaded itself - it
        // must resolve display via Storage (receiptStoragePath), not a local path.
        assertNull(applied?.receiptLocalUri)
        assertFalse(applied!!.receiptUploadPending)
    }

    @Test
    fun applyRemoteUpsert_pullOfOwnPreviousWrite_preservesLocalReceiptCache() = runBlocking {
        val dao = FakeTransactionDao()
        val adapter = TransactionSyncAdapter(dao, SyncIdResolver(FakeSyncMetadataDao()), uid = "uid-1")
        val localId = dao.insert(
            TransactionEntity(
                amountInCents = 500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1L,
                receiptId = "receipt-1", receiptStoragePath = "users/uid-1/receipts/receipt-1/receipt.jpg",
                receiptLocalUri = "/data/local/cache/receipt-1.jpg", receiptMimeType = "image/jpeg", receiptUpdatedAt = 42L
            )
        )

        // Simulates this same device pulling back the row it just pushed - same receiptId.
        adapter.applyRemoteUpsert(
            syncId = "sync-1",
            data = mapOf(
                "amountInCents" to 500L, "type" to "EXPENSE", "category" to "FOOD", "timestamp" to 1L,
                "receiptId" to "receipt-1", "receiptStoragePath" to "users/uid-1/receipts/receipt-1/receipt.jpg",
                "receiptMimeType" to "image/jpeg", "receiptUpdatedAt" to 42L
            ),
            existingLocalId = localId
        )

        val applied = dao.getById(localId)
        assertEquals("/data/local/cache/receipt-1.jpg", applied?.receiptLocalUri)
    }

    @Test
    fun applyRemoteUpsert_receiptChangedRemotely_clearsStaleLocalCache() = runBlocking {
        val dao = FakeTransactionDao()
        val adapter = TransactionSyncAdapter(dao, SyncIdResolver(FakeSyncMetadataDao()), uid = "uid-1")
        val localId = dao.insert(
            TransactionEntity(
                amountInCents = 500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1L,
                receiptId = "receipt-OLD", receiptStoragePath = "users/uid-1/receipts/receipt-OLD/receipt.jpg",
                receiptLocalUri = "/data/local/cache/receipt-OLD.jpg", receiptMimeType = "image/jpeg", receiptUpdatedAt = 10L
            )
        )

        // The receipt was replaced on another device - remote now has a different receiptId.
        adapter.applyRemoteUpsert(
            syncId = "sync-1",
            data = mapOf(
                "amountInCents" to 500L, "type" to "EXPENSE", "category" to "FOOD", "timestamp" to 1L,
                "receiptId" to "receipt-NEW", "receiptStoragePath" to "users/uid-1/receipts/receipt-NEW/receipt.jpg",
                "receiptMimeType" to "image/jpeg", "receiptUpdatedAt" to 99L
            ),
            existingLocalId = localId
        )

        val applied = dao.getById(localId)
        assertEquals("receipt-NEW", applied?.receiptId)
        assertNull(applied?.receiptLocalUri) // the old cached file is for the wrong receipt now
    }

    @Test
    fun legacyTransaction_withNoReceipt_pushesNullReceiptFields() = runBlocking {
        val dao = FakeTransactionDao()
        val adapter = TransactionSyncAdapter(dao, SyncIdResolver(FakeSyncMetadataDao()), uid = "uid-1")
        val id = dao.insert(TransactionEntity(amountInCents = 500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1L))

        val pushed = adapter.loadForPush(id)

        assertNull(pushed?.get("receiptId"))
        assertNull(pushed?.get("receiptStoragePath"))
    }
}

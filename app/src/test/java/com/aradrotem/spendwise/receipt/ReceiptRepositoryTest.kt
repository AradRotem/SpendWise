package com.aradrotem.spendwise.receipt

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.receipt.ReceiptError
import com.aradrotem.spendwise.data.repository.ReceiptRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.FakeTransactionDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReceiptRepositoryTest {

    private fun newFile(name: String = "receipt.jpg") = File.createTempFile(name, null).apply { deleteOnExit() }

    private fun setUp(): Triple<TransactionRepository, FakeReceiptStorageRepository, ReceiptRepository> {
        val transactionRepository = TransactionRepository(FakeTransactionDao())
        val storage = FakeReceiptStorageRepository()
        val receiptRepository = ReceiptRepository(transactionRepository, storage, FakeReceiptPendingDeletionDao(), uid = "uid-1")
        return Triple(transactionRepository, storage, receiptRepository)
    }

    private suspend fun insertTransaction(transactionRepository: TransactionRepository): Long =
        transactionRepository.insert(
            TransactionEntity(amountInCents = 1_000L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1L)
        )

    @Test
    fun attachReceipt_noPriorReceipt_savesLocallyThenUploads() = runBlocking {
        val (transactionRepository, storage, receiptRepository) = setUp()
        val id = insertTransaction(transactionRepository)

        val result = receiptRepository.attachReceipt(id, newFile(), "image/jpeg")

        assertTrue(result.isSuccess)
        val updated = transactionRepository.getById(id)
        assertNotNull(updated?.receiptId)
        assertNotNull(updated?.receiptStoragePath)
        assertFalse(updated!!.receiptUploadPending)
        assertEquals(1, storage.uploadCallCount)
    }

    @Test
    fun attachReceipt_uploadFails_transactionStillUsableWithPendingFlagSet() = runBlocking {
        val (transactionRepository, storage, receiptRepository) = setUp()
        val id = insertTransaction(transactionRepository)
        storage.nextUploadResult = Result.failure(ReceiptError.NetworkError)

        val result = receiptRepository.attachReceipt(id, newFile(), "image/jpeg")

        assertTrue(result.isFailure)
        val updated = transactionRepository.getById(id)
        assertNotNull(updated) // transaction itself is untouched/still usable
        assertNotNull(updated?.receiptLocalUri) // local preview survives an upload failure
        assertTrue(updated!!.receiptUploadPending)
        assertNull(updated.receiptStoragePath)
    }

    @Test
    fun retryPendingUploads_succeedsOnRetry_clearsPendingFlag() = runBlocking {
        val (transactionRepository, storage, receiptRepository) = setUp()
        val id = insertTransaction(transactionRepository)
        storage.nextUploadResult = Result.failure(ReceiptError.NetworkError)
        receiptRepository.attachReceipt(id, newFile(), "image/jpeg")
        storage.nextUploadResult = null // now succeeds

        receiptRepository.retryPendingUploads()

        val updated = transactionRepository.getById(id)
        assertFalse(updated!!.receiptUploadPending)
        assertNotNull(updated.receiptStoragePath)
    }

    @Test
    fun replaceReceipt_success_deletesOldStorageObjectOnlyAfterNewOneUploaded() = runBlocking {
        val (transactionRepository, storage, receiptRepository) = setUp()
        val id = insertTransaction(transactionRepository)
        receiptRepository.attachReceipt(id, newFile(), "image/jpeg")
        val oldStoragePath = transactionRepository.getById(id)!!.receiptStoragePath!!

        val result = receiptRepository.replaceReceipt(id, newFile(), "image/jpeg")

        assertTrue(result.isSuccess)
        val updated = transactionRepository.getById(id)
        assertNotNull(updated?.receiptStoragePath)
        assertTrue(updated!!.receiptStoragePath != oldStoragePath)
        assertTrue(storage.deletedPaths.contains(oldStoragePath))
    }

    @Test
    fun replaceReceipt_uploadFails_preservesOldReceiptAndNeverDeletesIt() = runBlocking {
        val (transactionRepository, storage, receiptRepository) = setUp()
        val id = insertTransaction(transactionRepository)
        receiptRepository.attachReceipt(id, newFile(), "image/jpeg")
        val oldStoragePath = transactionRepository.getById(id)!!.receiptStoragePath!!

        storage.nextUploadResult = Result.failure(ReceiptError.NetworkError)
        val result = receiptRepository.replaceReceipt(id, newFile(), "image/jpeg")

        assertTrue(result.isFailure)
        // Old cloud object must never be deleted before the replacement is durable.
        assertFalse(storage.deletedPaths.contains(oldStoragePath))
    }

    @Test
    fun removeReceipt_clearsMetadataAndDeletesStorageObject() = runBlocking {
        val (transactionRepository, storage, receiptRepository) = setUp()
        val id = insertTransaction(transactionRepository)
        receiptRepository.attachReceipt(id, newFile(), "image/jpeg")
        val storagePath = transactionRepository.getById(id)!!.receiptStoragePath!!

        val result = receiptRepository.removeReceipt(id)

        assertTrue(result.isSuccess)
        val updated = transactionRepository.getById(id)
        assertNull(updated?.receiptId)
        assertNull(updated?.receiptStoragePath)
        assertNull(updated?.receiptLocalUri)
        assertTrue(storage.deletedPaths.contains(storagePath))
        // The transaction itself remains intact.
        assertNotNull(updated)
    }

    @Test
    fun removeReceipt_deleteFails_enqueuesPendingDeletionForRetry() = runBlocking {
        val transactionRepository = TransactionRepository(FakeTransactionDao())
        val storage = FakeReceiptStorageRepository()
        val pendingDeletionDao = FakeReceiptPendingDeletionDao()
        val receiptRepository = ReceiptRepository(transactionRepository, storage, pendingDeletionDao, uid = "uid-1")
        val id = insertTransaction(transactionRepository)
        receiptRepository.attachReceipt(id, newFile(), "image/jpeg")

        storage.nextDeleteResult = Result.failure(ReceiptError.NetworkError)
        receiptRepository.removeReceipt(id)

        assertEquals(1, pendingDeletionDao.getAll().size)

        // Retry succeeds once the network issue clears.
        storage.nextDeleteResult = Result.success(Unit)
        receiptRepository.retryPendingDeletions()
        assertTrue(pendingDeletionDao.getAll().isEmpty())
    }

    @Test
    fun deletingTransactionWithReceipt_enqueuesStorageCleanup() = runBlocking {
        val dao = FakeTransactionDao()
        val pendingDeletionDao = FakeReceiptPendingDeletionDao()
        val transactionRepository = TransactionRepository(dao, receiptPendingDeletionDao = pendingDeletionDao)
        val storage = FakeReceiptStorageRepository()
        val receiptRepository = ReceiptRepository(transactionRepository, storage, pendingDeletionDao, uid = "uid-1")
        val id = insertTransaction(transactionRepository)
        receiptRepository.attachReceipt(id, newFile(), "image/jpeg")
        val transactionWithReceipt = transactionRepository.getById(id)!!

        transactionRepository.delete(transactionWithReceipt)

        // The delete itself never depends on network - the cleanup is queued for retry rather
        // than attempted synchronously (see TransactionRepository.delete).
        assertEquals(1, pendingDeletionDao.getAll().size)
        assertNull(transactionRepository.getById(id))
    }

    @Test
    fun deletingTransactionWithoutReceipt_enqueuesNothing() = runBlocking {
        val pendingDeletionDao = FakeReceiptPendingDeletionDao()
        val transactionRepository = TransactionRepository(FakeTransactionDao(), receiptPendingDeletionDao = pendingDeletionDao)
        val id = insertTransaction(transactionRepository)
        val transaction = transactionRepository.getById(id)!!

        transactionRepository.delete(transaction)

        assertTrue(pendingDeletionDao.getAll().isEmpty())
    }

    @Test
    fun differentAccounts_produceDifferentReceiptStoragePaths_forSameLocalTransaction() = runBlocking {
        val storage = FakeReceiptStorageRepository()
        val transactionRepositoryA = TransactionRepository(FakeTransactionDao())
        val transactionRepositoryB = TransactionRepository(FakeTransactionDao())
        val receiptRepositoryA = ReceiptRepository(transactionRepositoryA, storage, FakeReceiptPendingDeletionDao(), uid = "uid-A")
        val receiptRepositoryB = ReceiptRepository(transactionRepositoryB, storage, FakeReceiptPendingDeletionDao(), uid = "uid-B")
        val idA = insertTransaction(transactionRepositoryA)
        val idB = insertTransaction(transactionRepositoryB)

        receiptRepositoryA.attachReceipt(idA, newFile(), "image/jpeg")
        receiptRepositoryB.attachReceipt(idB, newFile(), "image/jpeg")

        val pathA = transactionRepositoryA.getById(idA)!!.receiptStoragePath!!
        val pathB = transactionRepositoryB.getById(idB)!!.receiptStoragePath!!
        assertTrue(pathA.startsWith("users/uid-A/"))
        assertTrue(pathB.startsWith("users/uid-B/"))
        assertTrue(pathA != pathB)
    }

    @Test
    fun transactionWithNoReceipt_hasNullReceiptFields() = runBlocking {
        val (transactionRepository, _, _) = setUp()
        val id = insertTransaction(transactionRepository)

        val transaction = transactionRepository.getById(id)

        assertNull(transaction?.receiptId)
        assertNull(transaction?.receiptStoragePath)
        assertNull(transaction?.receiptLocalUri)
        assertFalse(transaction!!.receiptUploadPending)
    }
}

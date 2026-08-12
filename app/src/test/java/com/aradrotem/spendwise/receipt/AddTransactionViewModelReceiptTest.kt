package com.aradrotem.spendwise.receipt

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.receipt.ProcessedReceiptImage
import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.ReceiptRepository
import com.aradrotem.spendwise.data.repository.RecurringOccurrenceExceptionRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.FakeCategoryDao
import com.aradrotem.spendwise.domain.FakeRecurringOccurrenceExceptionDao
import com.aradrotem.spendwise.domain.FakeRecurringPaymentPlanDao
import com.aradrotem.spendwise.domain.FakeTransactionDao
import com.aradrotem.spendwise.domain.RecurringOccurrenceManager
import com.aradrotem.spendwise.ui.screens.AddTransactionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelReceiptTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var receiptStorage: FakeReceiptStorageRepository
    private lateinit var receiptRepository: ReceiptRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        transactionRepository = TransactionRepository(FakeTransactionDao())
        receiptStorage = FakeReceiptStorageRepository()
        receiptRepository = ReceiptRepository(transactionRepository, receiptStorage, FakeReceiptPendingDeletionDao(), uid = "uid-1")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(transactionId: Long?) = AddTransactionViewModel(
        transactionRepository = transactionRepository,
        categoryRepository = CategoryRepository(FakeCategoryDao()),
        recurringOccurrenceManager = RecurringOccurrenceManager(
            transactionRepository,
            RecurringPaymentRepository(FakeRecurringPaymentPlanDao()),
            RecurringOccurrenceExceptionRepository(FakeRecurringOccurrenceExceptionDao())
        ),
        transactionId = transactionId,
        receiptRepository = receiptRepository
    )

    private fun tempFile() = File.createTempFile("receipt", ".jpg").apply { deleteOnExit() }

    @Test
    fun newTransaction_noReceiptSelected_hasNoReceiptInUiState() {
        val viewModel = newViewModel(transactionId = null)
        assertFalse(viewModel.uiState.value.hasReceipt)
    }

    @Test
    fun editMode_receiptImageProcessed_attachesImmediatelyAndUpdatesUiState() = kotlinx.coroutines.runBlocking {
        val id = transactionRepository.insert(
            TransactionEntity(amountInCents = 500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1L)
        )
        val viewModel = newViewModel(transactionId = id)

        viewModel.onReceiptImageProcessed(ProcessedReceiptImage(tempFile(), "image/jpeg"))

        assertTrue(viewModel.uiState.value.hasReceipt)
        assertNotNull(viewModel.uiState.value.receiptStoragePath)
        assertFalse(viewModel.uiState.value.isReceiptBusy)
        assertNull(viewModel.uiState.value.receiptError)
    }

    @Test
    fun editMode_removeReceipt_clearsUiState() = kotlinx.coroutines.runBlocking {
        val id = transactionRepository.insert(
            TransactionEntity(amountInCents = 500L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1L)
        )
        val viewModel = newViewModel(transactionId = id)
        viewModel.onReceiptImageProcessed(ProcessedReceiptImage(tempFile(), "image/jpeg"))

        viewModel.onRemoveReceipt()

        assertFalse(viewModel.uiState.value.hasReceipt)
        assertNull(viewModel.uiState.value.receiptStoragePath)
        assertNull(viewModel.uiState.value.receiptLocalPath)
    }

    @Test
    fun createMode_receiptQueuedBeforeSave_attachedAfterSaveSucceeds() = kotlinx.coroutines.runBlocking {
        val viewModel = newViewModel(transactionId = null)
        viewModel.onReceiptImageProcessed(ProcessedReceiptImage(tempFile(), "image/jpeg"))
        // Not attached to any repository row yet - just previewed locally, transaction doesn't exist.
        assertTrue(viewModel.uiState.value.hasReceipt)

        viewModel.onCategoryChange("FOOD")
        viewModel.onAmountChange("12.50")
        viewModel.save()

        // FakeTransactionDao's first inserted row is always id 1 - the only transaction this
        // test creates - and the queued receipt must now be attached to it.
        val saved = transactionRepository.getById(1L)
        assertNotNull(saved)
        assertNotNull(saved?.receiptStoragePath)
    }
}

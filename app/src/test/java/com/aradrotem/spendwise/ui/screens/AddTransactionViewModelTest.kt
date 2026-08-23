package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.RecurringOccurrenceExceptionRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.FakeCategoryDao
import com.aradrotem.spendwise.domain.FakeRecurringOccurrenceExceptionDao
import com.aradrotem.spendwise.domain.FakeRecurringPaymentPlanDao
import com.aradrotem.spendwise.domain.FakeTransactionDao
import com.aradrotem.spendwise.domain.RecurringOccurrenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// SpendWise v1 is ILS-only: no currency selection, no exchange-rate fetch, no conversion. These
// tests replace the removed AddTransactionViewModelCurrencyTest and confirm the amount entered by
// the user is saved unchanged (in agorot) with the legacy currency-metadata columns left null.
@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var transactionDao: FakeTransactionDao

    // Passed into runTest(testDispatcher) below so the test body and every ViewModel's
    // viewModelScope (dispatched via Dispatchers.Main -> this same instance) share one
    // TestCoroutineScheduler, keeping load-then-assert deterministic.
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        transactionDao = FakeTransactionDao()
        transactionRepository = TransactionRepository(transactionDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(transactionId: Long? = null) = AddTransactionViewModel(
        transactionRepository = transactionRepository,
        categoryRepository = CategoryRepository(FakeCategoryDao()),
        recurringOccurrenceManager = RecurringOccurrenceManager(
            transactionRepository,
            RecurringPaymentRepository(FakeRecurringPaymentPlanDao()),
            RecurringOccurrenceExceptionRepository(FakeRecurringOccurrenceExceptionDao())
        ),
        transactionId = transactionId,
        receiptRepository = null
    )

    @Test
    fun save_storesEnteredAmountUnchangedAsAgorot() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        viewModel.onAmountChange("42.50")
        viewModel.onCategoryChange("FOOD")

        viewModel.save()

        val saved = transactionDao.allRows.single()
        assertEquals(4_250L, saved.amountInCents)
    }

    @Test
    fun save_leavesLegacyCurrencyMetadataNull() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        viewModel.onAmountChange("20.00")
        viewModel.onCategoryChange("FOOD")

        viewModel.save()

        val saved = transactionDao.allRows.single()
        assertNull(saved.originalAmountCents)
        assertNull(saved.originalCurrencyCode)
        assertNull(saved.conversionRate)
        assertNull(saved.rateTimestampEpochMillis)
    }

    @Test
    fun editingTransaction_preservesAmountUnchanged() = runTest(testDispatcher) {
        val createViewModel = newViewModel()
        createViewModel.onAmountChange("100.00")
        createViewModel.onCategoryChange("FOOD")
        createViewModel.save()
        val savedId = transactionDao.allRows.single().id

        val editViewModel = newViewModel(transactionId = savedId)
        // The amount field is pre-filled from the stored value via formatAmountInCents (which
        // includes the ₪ symbol) - confirms that round-trips back through parseAmountToCents
        // (via save() below) without the user having to touch it.
        assertEquals("₪100.00", editViewModel.uiState.value.amountText)
        editViewModel.onNoteChange("updated note")
        editViewModel.save()

        val updated = transactionDao.allRows.single()
        assertEquals(10_000L, updated.amountInCents)
        assertEquals("updated note", updated.note)
    }
}

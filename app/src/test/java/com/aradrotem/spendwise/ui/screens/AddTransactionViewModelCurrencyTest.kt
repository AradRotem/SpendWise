package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.ExchangeRateRepository
import com.aradrotem.spendwise.data.repository.ExchangeRateResult
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class FixedExchangeRateRepository(private val result: ExchangeRateResult) : ExchangeRateRepository {
    var lastFromCurrency: String? = null
    override suspend fun getRate(fromCurrency: String, toCurrency: String): ExchangeRateResult {
        lastFromCurrency = fromCurrency
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelCurrencyTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var transactionDao: FakeTransactionDao

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        transactionDao = FakeTransactionDao()
        transactionRepository = TransactionRepository(transactionDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(exchangeRateRepository: ExchangeRateRepository?) = AddTransactionViewModel(
        transactionRepository = transactionRepository,
        categoryRepository = CategoryRepository(FakeCategoryDao()),
        recurringOccurrenceManager = RecurringOccurrenceManager(
            transactionRepository,
            RecurringPaymentRepository(FakeRecurringPaymentPlanDao()),
            RecurringOccurrenceExceptionRepository(FakeRecurringOccurrenceExceptionDao())
        ),
        transactionId = null,
        receiptRepository = null,
        exchangeRateRepository = exchangeRateRepository,
        baseCurrencyCode = "ILS"
    )

    @Test
    fun disabledByDefault_amountFieldBehavesLikeBeforeStep19() {
        val viewModel = newViewModel(FixedExchangeRateRepository(ExchangeRateResult.Success(3.7, 1_000L, false)))
        assertEquals(false, viewModel.uiState.value.useForeignCurrency)
    }

    @Test
    fun enablingForeignCurrency_fetchesRateAgainstBaseCurrency() = runTest {
        val repo = FixedExchangeRateRepository(ExchangeRateResult.Success(3.7, 1_000L, false))
        val viewModel = newViewModel(repo)

        viewModel.onForeignCurrencyToggle(true)

        assertEquals("USD", repo.lastFromCurrency)
        assertEquals(3.7, viewModel.uiState.value.conversionRate)
    }

    @Test
    fun enteringForeignAmount_recomputesBaseAmountUsingFetchedRate() {
        val viewModel = newViewModel(FixedExchangeRateRepository(ExchangeRateResult.Success(3.7, 1_000L, false)))
        viewModel.onForeignCurrencyToggle(true)

        viewModel.onForeignAmountChange("100.00")

        // 100.00 USD at 3.70 -> 370.00 base.
        assertEquals("370.00", viewModel.uiState.value.amountText)
    }

    @Test
    fun rateUnavailable_noConversionRateSetAndErrorShown() {
        val viewModel = newViewModel(FixedExchangeRateRepository(ExchangeRateResult.Unavailable))
        viewModel.onForeignCurrencyToggle(true)

        assertNull(viewModel.uiState.value.conversionRate)
        assertNotNull(viewModel.uiState.value.rateError)
    }

    @Test
    fun cachedRate_flaggedAndSurfacedAsInformationalMessage() {
        val viewModel = newViewModel(FixedExchangeRateRepository(ExchangeRateResult.Success(3.65, 1_000L, isFromCache = true)))
        viewModel.onForeignCurrencyToggle(true)

        assertEquals(true, viewModel.uiState.value.rateIsFromCache)
        assertNotNull(viewModel.uiState.value.rateError)
    }

    @Test
    fun saveWithForeignCurrency_preservesOriginalAmountCurrencyRateAndTimestamp() = runTest {
        val viewModel = newViewModel(FixedExchangeRateRepository(ExchangeRateResult.Success(3.7, 5_000L, false)))
        viewModel.onForeignCurrencyToggle(true)
        viewModel.onForeignAmountChange("50.00")
        viewModel.onCategoryChange("FOOD")

        viewModel.save()

        val saved = transactionDao.allRows.single()
        assertEquals(5_000L, saved.originalAmountCents)
        assertEquals("USD", saved.originalCurrencyCode)
        assertEquals(3.7, saved.conversionRate)
        assertEquals(5_000L, saved.rateTimestampEpochMillis)
        assertEquals(18_500L, saved.amountInCents) // 50.00 * 3.70 = 185.00
    }

    @Test
    fun saveWithoutForeignCurrency_leavesOriginalFieldsNull() = runTest {
        val viewModel = newViewModel(exchangeRateRepository = null)
        viewModel.onAmountChange("20.00")
        viewModel.onCategoryChange("FOOD")

        viewModel.save()

        val saved = transactionDao.allRows.single()
        assertNull(saved.originalAmountCents)
        assertNull(saved.originalCurrencyCode)
        assertNull(saved.conversionRate)
    }

    @Test
    fun editingTransactionLater_doesNotRecalculateHistoricalRate() = runTest {
        // Saved once with a rate of 3.70 - a later fetch returning a different current rate must
        // never be applied retroactively to the already-saved row (see TransactionEntity's
        // conversionRate doc comment).
        val viewModel = newViewModel(FixedExchangeRateRepository(ExchangeRateResult.Success(3.7, 5_000L, false)))
        viewModel.onForeignCurrencyToggle(true)
        viewModel.onForeignAmountChange("50.00")
        viewModel.onCategoryChange("FOOD")
        viewModel.save()

        val saved = transactionDao.allRows.single()
        assertEquals(3.7, saved.conversionRate)

        // Nothing in the ViewModel/repository re-touches this row after save - confirmed by it
        // still being the only row with the original rate.
        assertEquals(1, transactionDao.allRows.size)
        assertEquals(3.7, transactionDao.allRows.single().conversionRate)
    }
}

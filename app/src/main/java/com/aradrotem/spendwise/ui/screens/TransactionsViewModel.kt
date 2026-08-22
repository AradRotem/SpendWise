package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.RecurringOccurrenceManager
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val recurringOccurrenceManager: RecurringOccurrenceManager
) : ViewModel() {

    private val dateFilter = MutableStateFlow<TransactionDateFilter>(TransactionDateFilter.All)

    // Re-queries Room only when the filter itself changes, reusing the same half-open
    // observeInRange the rest of the app already uses for date-scoped queries (see MonthRange) -
    // never loads the whole table into Compose just to filter it there.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TransactionsUiState> = dateFilter
        .flatMapLatest { filter ->
            val range = filter.toRange()
            val transactionsFlow = if (range == null) {
                transactionRepository.observeAll()
            } else {
                transactionRepository.observeInRange(range.startMillis, range.endExclusiveMillis)
            }
            transactionsFlow.map { transactions -> TransactionsUiState(transactions = transactions, isLoading = false, dateFilter = filter) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionsUiState(isLoading = true)
        )

    fun setMonthFilter(yearMonth: YearMonth) {
        dateFilter.value = TransactionDateFilter.Month(yearMonth)
    }

    fun setCustomRangeFilter(startMillis: Long, endExclusiveMillis: Long) {
        dateFilter.value = TransactionDateFilter.CustomRange(startMillis, endExclusiveMillis)
    }

    fun clearDateFilter() {
        dateFilter.value = TransactionDateFilter.All
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.delete(transaction)
        }
    }

    // Resolved before the generated-transaction action menu is shown, mirroring how
    // CategoriesScreen pre-fetches delete-impact info before opening its confirmation dialog.
    // The actual decision is a pure function (see resolveGeneratedTransactionActionInfo) so it's
    // directly unit-testable without a real ViewModel/repository.
    suspend fun loadActionMenuInfo(transaction: TransactionEntity): GeneratedTransactionActionInfo {
        val plan = transaction.recurringPlanId?.let { recurringPaymentRepository.getById(it) }
        return resolveGeneratedTransactionActionInfo(transaction, plan)
    }

    suspend fun countLaterGeneratedTransactions(transaction: TransactionEntity): Int =
        recurringOccurrenceManager.countLaterGeneratedTransactions(transaction)

    fun deleteOccurrenceOnly(transaction: TransactionEntity) {
        viewModelScope.launch {
            recurringOccurrenceManager.deleteOccurrenceOnly(transaction)
        }
    }

    fun deleteThisAndFuture(transaction: TransactionEntity) {
        viewModelScope.launch {
            recurringOccurrenceManager.deleteThisAndFuture(transaction)
        }
    }

    companion object {
        fun factory(
            transactionRepository: TransactionRepository,
            recurringPaymentRepository: RecurringPaymentRepository,
            recurringOccurrenceManager: RecurringOccurrenceManager
        ) = viewModelFactory {
            initializer { TransactionsViewModel(transactionRepository, recurringPaymentRepository, recurringOccurrenceManager) }
        }
    }
}

// What the generated-transaction action menu should offer, given the transaction and its
// (possibly null, possibly inactive) source plan. Kept as a top-level pure function - not a
// ViewModel member - so it's directly unit-testable without a Main dispatcher.
fun resolveGeneratedTransactionActionInfo(
    transaction: TransactionEntity,
    plan: RecurringPaymentPlanEntity?
): GeneratedTransactionActionInfo {
    val isLastInstallment = transaction.installmentNumber != null && transaction.installmentNumber == transaction.totalInstallments
    val planIsLive = plan != null && plan.status != RecurringPlanStatus.STOPPED && plan.status != RecurringPlanStatus.COMPLETED
    return GeneratedTransactionActionInfo(
        planExists = plan != null,
        canActOnFuture = planIsLive && !isLastInstallment,
        isInstallmentOccurrence = transaction.installmentNumber != null
    )
}

// Manual, non-recurring transactions keep their normal direct Edit/Delete menu; only generated
// occurrences get the recurring-aware action menu (installment two-tier or standing-order/salary
// flexible menu - see TransactionsScreen). Kept as a top-level pure function so this routing
// decision is directly unit-testable.
fun shouldShowGeneratedActionMenu(transaction: TransactionEntity): Boolean = transaction.isAutomaticallyGenerated

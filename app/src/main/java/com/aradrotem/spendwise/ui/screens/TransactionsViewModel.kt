package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val uiState: StateFlow<TransactionsUiState> = transactionRepository.observeAll()
        .map { transactions -> TransactionsUiState(transactions = transactions, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionsUiState(isLoading = true)
        )

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.delete(transaction)
        }
    }

    companion object {
        fun factory(transactionRepository: TransactionRepository) = viewModelFactory {
            initializer { TransactionsViewModel(transactionRepository) }
        }
    }
}

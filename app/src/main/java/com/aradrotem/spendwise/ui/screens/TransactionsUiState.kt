package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.TransactionEntity

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = true
)

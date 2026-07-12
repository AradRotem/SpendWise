package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.TransactionCategory
import com.aradrotem.spendwise.data.local.TransactionType

data class AddTransactionUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val category: TransactionCategory? = null,
    val dateMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val amountError: String? = null,
    val categoryError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: String? = null
)

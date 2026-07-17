package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.TransactionType

data class AddTransactionUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    // Only shown/used when editing a single generated occurrence (see isGeneratedOccurrence);
    // manual transactions have no title of their own, they're identified by category alone.
    val title: String = "",
    val amountText: String = "",
    val category: String? = null,
    val dateMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val titleError: String? = null,
    val amountError: String? = null,
    val categoryError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    // True once the loaded transaction is confirmed to be an automatically generated occurrence.
    // Drives the "edit this transaction" restrictions: type is fixed, the date must stay within
    // scheduledYearMonth, and save routes through RecurringOccurrenceManager instead of a plain
    // transaction update so the recurring-link fields are preserved.
    val isGeneratedOccurrence: Boolean = false,
    val scheduledYearMonth: String? = null
)

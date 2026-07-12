package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.TransactionCategory
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val transactionId: Long? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState(isLoading = transactionId != null))
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        val id = transactionId
        if (id != null) {
            viewModelScope.launch {
                val existing = transactionRepository.getById(id)
                if (existing != null) {
                    _uiState.update {
                        it.copy(
                            type = existing.type,
                            amountText = formatAmountInCents(existing.amountInCents),
                            category = existing.category,
                            dateMillis = existing.timestamp,
                            note = existing.note,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, loadError = "Transaction not found") }
                }
            }
        }
    }

    fun onTypeChange(type: TransactionType) {
        _uiState.update { it.copy(type = type) }
    }

    fun onAmountChange(amountText: String) {
        _uiState.update { it.copy(amountText = amountText, amountError = null, saveError = null) }
    }

    fun onCategoryChange(category: TransactionCategory) {
        _uiState.update { it.copy(category = category, categoryError = null, saveError = null) }
    }

    fun onDateChange(dateMillis: Long) {
        _uiState.update { it.copy(dateMillis = dateMillis) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val amountInCents = parseAmountToCents(state.amountText)
        val amountError = when {
            state.amountText.isBlank() -> "Amount is required"
            amountInCents == null -> "Enter a valid amount"
            amountInCents <= 0L -> "Amount must be greater than zero"
            else -> null
        }
        val categoryError = if (state.category == null) "Category is required" else null

        if (amountError != null || categoryError != null) {
            _uiState.update { it.copy(amountError = amountError, categoryError = categoryError) }
            return
        }

        val validAmountInCents = amountInCents ?: return
        val validCategory = state.category ?: return

        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            try {
                val id = transactionId
                if (id != null) {
                    transactionRepository.update(
                        TransactionEntity(
                            id = id,
                            amountInCents = validAmountInCents,
                            type = state.type,
                            category = validCategory,
                            timestamp = state.dateMillis,
                            note = state.note.trim()
                        )
                    )
                } else {
                    transactionRepository.insert(
                        TransactionEntity(
                            amountInCents = validAmountInCents,
                            type = state.type,
                            category = validCategory,
                            timestamp = state.dateMillis,
                            note = state.note.trim()
                        )
                    )
                }
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, saveError = "Could not save the transaction. Please try again.")
                }
            }
        }
    }

    companion object {
        fun factory(transactionRepository: TransactionRepository, transactionId: Long? = null) = viewModelFactory {
            initializer { AddTransactionViewModel(transactionRepository, transactionId) }
        }
    }
}

// Parses decimal text directly into cents to avoid Double/Float rounding on money.
private fun parseAmountToCents(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    val normalized = trimmed.replace(',', '.')
    val parts = normalized.split(".")
    if (parts.size > 2) return null

    val wholeDigits = parts[0].ifEmpty { "0" }
    val fractionDigits = if (parts.size == 2) parts[1] else ""

    if (!wholeDigits.all { it.isDigit() }) return null
    if (fractionDigits.length > 2 || !fractionDigits.all { it.isDigit() }) return null

    val whole = wholeDigits.toLongOrNull() ?: return null
    val fraction = fractionDigits.padEnd(2, '0').toLongOrNull() ?: return null

    return whole * 100 + fraction
}

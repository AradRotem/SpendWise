package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.RecurringOccurrenceManager
import com.aradrotem.spendwise.ui.components.transactionPrimaryText
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.hasTooManyDecimalPlaces
import com.aradrotem.spendwise.ui.format.parseAmountToCents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringOccurrenceManager: RecurringOccurrenceManager,
    private val transactionId: Long? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState(isLoading = transactionId != null))
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val availableCategories: StateFlow<List<CategoryEntity>> = _uiState
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> categoryRepository.observeByType(type) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = transactionId
        if (id != null) {
            viewModelScope.launch {
                val existing = transactionRepository.getById(id)
                if (existing != null) {
                    _uiState.update {
                        it.copy(
                            type = existing.type,
                            title = transactionPrimaryText(existing),
                            amountText = formatAmountInCents(existing.amountInCents),
                            category = existing.category,
                            dateMillis = existing.timestamp,
                            note = existing.note,
                            isLoading = false,
                            isGeneratedOccurrence = existing.isAutomaticallyGenerated,
                            scheduledYearMonth = existing.scheduledYearMonth
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, loadError = "Transaction not found") }
                }
            }
        }
    }

    fun onTypeChange(type: TransactionType) {
        // A generated occurrence's type is fixed; the screen hides the selector in that mode,
        // this is just a defensive backstop.
        if (_uiState.value.isGeneratedOccurrence) return
        viewModelScope.launch {
            val validCategoryNames = categoryRepository.observeByType(type).first().map { it.name }
            _uiState.update { state ->
                val validCategory = state.category?.takeIf { it in validCategoryNames }
                state.copy(type = type, category = validCategory, categoryError = null)
            }
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, titleError = null, saveError = null) }
    }

    fun onAmountChange(amountText: String) {
        _uiState.update { it.copy(amountText = amountText, amountError = null, saveError = null) }
    }

    fun onCategoryChange(category: String) {
        _uiState.update { it.copy(category = category, categoryError = null, saveError = null) }
    }

    fun onDateChange(dateMillis: Long) {
        _uiState.update { it.copy(dateMillis = dateMillis, saveError = null) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val amountInCents = parseAmountToCents(state.amountText)
        val titleError = if (state.isGeneratedOccurrence && state.title.isBlank()) "Title is required" else null
        val amountError = when {
            state.amountText.isBlank() -> "Amount is required"
            hasTooManyDecimalPlaces(state.amountText) -> "Enter an amount with up to 2 decimal places."
            amountInCents == null -> "Enter a valid amount"
            amountInCents <= 0L -> "Amount must be greater than zero"
            else -> null
        }
        val categoryError = if (state.category == null) "Category is required" else null

        if (titleError != null || amountError != null || categoryError != null) {
            _uiState.update { it.copy(titleError = titleError, amountError = amountError, categoryError = categoryError) }
            return
        }

        val validAmountInCents = amountInCents ?: return
        val validCategory = state.category ?: return

        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            val id = transactionId
            if (id != null && state.isGeneratedOccurrence) {
                recurringOccurrenceManager.editOccurrenceOnly(
                    transactionId = id,
                    title = state.title.trim(),
                    amountInCents = validAmountInCents,
                    category = validCategory,
                    note = state.note.trim(),
                    timestamp = state.dateMillis
                )
                    .onSuccess { _uiState.update { it.copy(isSaving = false, isSaved = true) } }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(isSaving = false, saveError = error.message ?: "Could not save the transaction. Please try again.")
                        }
                    }
                return@launch
            }

            try {
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
        fun factory(
            transactionRepository: TransactionRepository,
            categoryRepository: CategoryRepository,
            recurringOccurrenceManager: RecurringOccurrenceManager,
            transactionId: Long? = null
        ) = viewModelFactory {
            initializer { AddTransactionViewModel(transactionRepository, categoryRepository, recurringOccurrenceManager, transactionId) }
        }
    }
}

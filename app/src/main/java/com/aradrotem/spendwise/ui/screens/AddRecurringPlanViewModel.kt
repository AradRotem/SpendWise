package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.RecurringOccurrenceManager
import com.aradrotem.spendwise.domain.RecurringPaymentGenerator
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.hasTooManyDecimalPlaces
import com.aradrotem.spendwise.ui.format.parseAmountToCents
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

class AddRecurringPlanViewModel(
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val recurringPaymentGenerator: RecurringPaymentGenerator,
    private val recurringOccurrenceManager: RecurringOccurrenceManager,
    private val planId: Long? = null,
    private val occurrenceTransactionId: Long? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddRecurringPlanUiState(
            planId = planId,
            isLoading = planId != null,
            occurrenceTransactionId = occurrenceTransactionId
        )
    )
    val uiState: StateFlow<AddRecurringPlanUiState> = _uiState.asStateFlow()

    // Income for Monthly salary, Expense for the other two plan types.
    @OptIn(ExperimentalCoroutinesApi::class)
    val availableCategories: StateFlow<List<CategoryEntity>> = _uiState
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> categoryRepository.observeByType(categoryTypeForPlanType(type)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = planId
        if (id != null) {
            viewModelScope.launch {
                val plan = recurringPaymentRepository.getById(id)
                if (plan != null) {
                    val generatedCount = transactionRepository.countGeneratedForPlan(id)
                    val occurrenceMonth = occurrenceTransactionId?.let { transactionRepository.getById(it)?.scheduledYearMonth }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            type = plan.type,
                            title = plan.title,
                            category = plan.categoryName,
                            note = plan.note,
                            amountText = plan.amountInCents?.let(::formatAmountInCents) ?: "",
                            startDateMillis = plan.firstPaymentDateMillis,
                            preferredDayOfMonthText = plan.preferredDayOfMonth.toString(),
                            hasEndDate = plan.endDateMillis != null,
                            endDateMillis = plan.endDateMillis,
                            totalAmountText = plan.totalAmountInCents?.let(::formatAmountInCents) ?: "",
                            installmentCountText = plan.totalInstallments?.toString() ?: "",
                            firstPaymentDateMillis = plan.firstPaymentDateMillis,
                            generatedInstallmentCount = generatedCount,
                            occurrenceScheduledYearMonth = occurrenceMonth
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, loadError = "Plan not found") }
                }
            }
        }
    }

    // The type selector is only shown when creating a new plan (see AddRecurringPlanScreen), but
    // this still resets an incompatible category, mirroring AddTransactionViewModel.onTypeChange.
    fun onTypeChange(type: RecurringPlanType) {
        viewModelScope.launch {
            val validCategoryNames = categoryRepository.observeByType(categoryTypeForPlanType(type)).first().map { it.name }
            _uiState.update { state ->
                state.copy(
                    type = type,
                    category = resetCategoryIfInvalid(state.category, validCategoryNames),
                    categoryError = null,
                    saveError = null
                )
            }
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, titleError = null, saveError = null) }
    }

    fun onCategoryChange(category: String) {
        _uiState.update { it.copy(category = category, categoryError = null, saveError = null) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onAmountChange(amountText: String) {
        _uiState.update { it.copy(amountText = amountText, amountError = null, saveError = null) }
    }

    fun onStartDateChange(startDateMillis: Long) {
        _uiState.update { it.copy(startDateMillis = startDateMillis) }
    }

    fun onPreferredDayChange(preferredDayText: String) {
        _uiState.update { it.copy(preferredDayOfMonthText = preferredDayText, preferredDayError = null, saveError = null) }
    }

    fun onHasEndDateChange(hasEndDate: Boolean) {
        _uiState.update {
            it.copy(
                hasEndDate = hasEndDate,
                endDateMillis = if (hasEndDate) it.endDateMillis ?: it.startDateMillis else null,
                endDateError = null
            )
        }
    }

    fun onEndDateChange(endDateMillis: Long) {
        _uiState.update { it.copy(endDateMillis = endDateMillis, endDateError = null, saveError = null) }
    }

    fun onTotalAmountChange(totalAmountText: String) {
        _uiState.update { it.copy(totalAmountText = totalAmountText, totalAmountError = null, saveError = null) }
    }

    fun onInstallmentCountChange(installmentCountText: String) {
        _uiState.update { it.copy(installmentCountText = installmentCountText, installmentCountError = null, saveError = null) }
    }

    fun onFirstPaymentDateChange(firstPaymentDateMillis: Long) {
        _uiState.update { it.copy(firstPaymentDateMillis = firstPaymentDateMillis) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        when (state.type) {
            RecurringPlanType.MONTHLY_RECURRING, RecurringPlanType.MONTHLY_SALARY -> saveMonthlyPlan(state)
            RecurringPlanType.INSTALLMENT -> saveInstallment(state)
        }
    }

    private fun saveMonthlyPlan(state: AddRecurringPlanUiState) {
        val amountInCents = parseAmountToCents(state.amountText)
        val preferredDay = state.preferredDayOfMonthText.toIntOrNull()
        val endDateMillis = if (state.hasEndDate) state.endDateMillis else null

        val titleError = if (state.title.isBlank()) "Title is required" else null
        val amountError = when {
            state.amountText.isBlank() -> "Amount is required"
            hasTooManyDecimalPlaces(state.amountText) -> "Enter an amount with up to 2 decimal places."
            amountInCents == null -> "Enter a valid amount"
            amountInCents <= 0L -> "Amount must be greater than zero"
            else -> null
        }
        val categoryError = if (state.category == null) "Category is required" else null
        val preferredDayError = when {
            preferredDay == null -> "Enter a day between 1 and 31"
            preferredDay !in 1..31 -> "Day must be between 1 and 31"
            else -> null
        }
        val endDateError = if (state.hasEndDate && endDateMillis != null && endDateMillis < state.startDateMillis) {
            "End date must be on or after the start date"
        } else null

        if (titleError != null || amountError != null || categoryError != null || preferredDayError != null || endDateError != null) {
            _uiState.update {
                it.copy(
                    titleError = titleError,
                    amountError = amountError,
                    categoryError = categoryError,
                    preferredDayError = preferredDayError,
                    endDateError = endDateError
                )
            }
            return
        }

        val validAmount = amountInCents ?: return
        val validDay = preferredDay ?: return
        val validCategory = state.category ?: return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val result = if (state.isEditMode) {
                recurringPaymentRepository.updateMonthlyPlan(
                    id = state.planId!!,
                    title = state.title.trim(),
                    categoryName = validCategory,
                    note = state.note.trim(),
                    amountInCents = validAmount,
                    preferredDayOfMonth = validDay,
                    endDateMillis = endDateMillis
                )
            } else {
                recurringPaymentRepository.createMonthlyPlan(
                    type = state.type,
                    title = state.title.trim(),
                    categoryName = validCategory,
                    note = state.note.trim(),
                    amountInCents = validAmount,
                    startDateMillis = state.startDateMillis,
                    preferredDayOfMonth = validDay,
                    endDateMillis = endDateMillis
                )
            }
            onSaveResult(result, state.occurrenceTransactionId, state.title.trim(), validAmount, validCategory, state.note.trim())
        }
    }

    private fun saveInstallment(state: AddRecurringPlanUiState) {
        val totalAmountInCents = parseAmountToCents(state.totalAmountText)
        val installmentCount = state.installmentCountText.toIntOrNull()

        val titleError = if (state.title.isBlank()) "Title is required" else null
        val totalAmountError = when {
            state.totalAmountText.isBlank() -> "Total amount is required"
            hasTooManyDecimalPlaces(state.totalAmountText) -> "Enter an amount with up to 2 decimal places."
            totalAmountInCents == null -> "Enter a valid amount"
            totalAmountInCents <= 0L -> "Total amount must be greater than zero"
            else -> null
        }
        val categoryError = if (state.category == null) "Category is required" else null
        val installmentCountError = when {
            state.installmentCountText.isBlank() -> "Number of installments is required"
            installmentCount == null -> "Enter a valid whole number"
            installmentCount <= 0 -> "Number of installments must be greater than zero"
            installmentCount > MAX_INSTALLMENTS -> "Number of installments cannot exceed $MAX_INSTALLMENTS"
            state.isEditMode && installmentCount < state.generatedInstallmentCount ->
                "Cannot be fewer than the ${state.generatedInstallmentCount} installments already generated"
            else -> null
        }

        if (titleError != null || totalAmountError != null || categoryError != null || installmentCountError != null) {
            _uiState.update {
                it.copy(
                    titleError = titleError,
                    totalAmountError = totalAmountError,
                    categoryError = categoryError,
                    installmentCountError = installmentCountError
                )
            }
            return
        }

        val validTotal = totalAmountInCents ?: return
        val validCount = installmentCount ?: return
        val validCategory = state.category ?: return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val result = if (state.isEditMode) {
                recurringPaymentRepository.updateInstallmentPlan(
                    id = state.planId!!,
                    title = state.title.trim(),
                    categoryName = validCategory,
                    note = state.note.trim(),
                    totalAmountInCents = validTotal,
                    totalInstallments = validCount,
                    generatedInstallmentCount = state.generatedInstallmentCount
                )
            } else {
                recurringPaymentRepository.createInstallmentPlan(
                    title = state.title.trim(),
                    categoryName = validCategory,
                    note = state.note.trim(),
                    totalAmountInCents = validTotal,
                    totalInstallments = validCount,
                    firstPaymentDateMillis = state.firstPaymentDateMillis
                )
            }
            // Installment amounts are never edited retroactively (see RecurringOccurrenceManager),
            // so amountInCents is always null here regardless of what's on screen.
            onSaveResult(result, state.occurrenceTransactionId, state.title.trim(), null, validCategory, state.note.trim())
        }
    }

    private suspend fun onSaveResult(
        result: Result<Unit>,
        occurrenceTransactionId: Long?,
        title: String,
        amountInCentsForOccurrence: Long?,
        category: String,
        note: String
    ) {
        result
            .onSuccess {
                // Immediately materialize any already-due payment (e.g. a start/first-payment
                // date of today or earlier) instead of waiting for the next app start/resume.
                recurringPaymentGenerator.generateDuePayments()
                // "Edit this and future": also propagate the new values to the occurrence the
                // user started from, plus any later already-generated, non-overridden rows.
                if (occurrenceTransactionId != null) {
                    recurringOccurrenceManager.applyEditToOccurrenceAndFuture(
                        occurrenceTransactionId = occurrenceTransactionId,
                        title = title,
                        amountInCents = amountInCentsForOccurrence,
                        category = category,
                        note = note
                    )
                }
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            }
            .onFailure { error ->
                _uiState.update { it.copy(isSaving = false, saveError = error.message) }
            }
    }

    companion object {
        const val MAX_INSTALLMENTS = 120

        fun factory(
            recurringPaymentRepository: RecurringPaymentRepository,
            categoryRepository: CategoryRepository,
            transactionRepository: TransactionRepository,
            recurringPaymentGenerator: RecurringPaymentGenerator,
            recurringOccurrenceManager: RecurringOccurrenceManager,
            planId: Long? = null,
            occurrenceTransactionId: Long? = null
        ) = viewModelFactory {
            initializer {
                AddRecurringPlanViewModel(
                    recurringPaymentRepository,
                    categoryRepository,
                    transactionRepository,
                    recurringPaymentGenerator,
                    recurringOccurrenceManager,
                    planId,
                    occurrenceTransactionId
                )
            }
        }
    }
}

// Income for Monthly salary, Expense for the other two plan types. Kept as a top-level pure
// function (not a ViewModel member) so it's directly unit-testable without instantiating a real
// ViewModel, which needs a Main dispatcher unavailable in plain JUnit tests.
fun categoryTypeForPlanType(type: RecurringPlanType): TransactionType = when (type) {
    RecurringPlanType.MONTHLY_SALARY -> TransactionType.INCOME
    RecurringPlanType.MONTHLY_RECURRING, RecurringPlanType.INSTALLMENT -> TransactionType.EXPENSE
}

// Keeps the current category selection only if it's still valid for the (possibly new) plan
// type; otherwise resets it to null, forcing the user to pick a compatible one.
fun resetCategoryIfInvalid(currentCategory: String?, validCategoryNames: List<String>): String? =
    currentCategory?.takeIf { it in validCategoryNames }

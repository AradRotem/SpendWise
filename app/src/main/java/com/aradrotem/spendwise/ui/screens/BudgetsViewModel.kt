package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.BudgetRepository
import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.hasTooManyDecimalPlaces
import com.aradrotem.spendwise.ui.format.parseAmountToCents
import com.aradrotem.spendwise.util.compareCategoryNames
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetsViewModel(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _dialogState = MutableStateFlow<BudgetDialogUiState?>(null)
    private val _budgetPendingDelete = MutableStateFlow<BudgetEntity?>(null)

    val uiState: StateFlow<BudgetsUiState> = combine(
        budgetProgressFlow(),
        categoryRepository.observeByType(TransactionType.EXPENSE),
        _dialogState,
        _budgetPendingDelete
    ) { budgets, expenseCategories, dialog, pendingDelete ->
        BudgetsUiState(
            isLoading = false,
            budgets = budgets,
            expenseCategories = expenseCategories,
            dialog = dialog,
            budgetPendingDelete = pendingDelete
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetsUiState(isLoading = true)
    )

    private fun budgetProgressFlow() = run {
        val (startMillis, endMillis) = currentMonthMillisRange()
        combine(
            budgetRepository.observeAll(),
            transactionRepository.observeExpenseTotalsByCategory(startMillis, endMillis)
        ) { budgets, totals ->
            val totalsByCategory = totals.associateBy({ it.category }, { it.totalCents })
            budgets
                .map { budget -> BudgetProgress(budget, totalsByCategory[budget.categoryName] ?: 0L) }
                .sortedWith { a, b -> compareCategoryNames(a.categoryName, b.categoryName) }
        }
    }

    fun onAddBudgetClick() {
        _dialogState.value = BudgetDialogUiState(isEdit = false)
    }

    fun onEditBudgetClick(budget: BudgetEntity) {
        _dialogState.value = BudgetDialogUiState(
            isEdit = true,
            budgetId = budget.id,
            categoryName = budget.categoryName,
            amountText = formatAmountInCents(budget.monthlyLimitCents)
        )
    }

    fun onDialogCategoryChange(categoryName: String) {
        _dialogState.value = _dialogState.value?.copy(categoryName = categoryName, categoryError = null, saveError = null)
    }

    fun onDialogAmountChange(amountText: String) {
        _dialogState.value = _dialogState.value?.copy(amountText = amountText, amountError = null, saveError = null)
    }

    fun onDialogDismiss() {
        _dialogState.value = null
    }

    fun onDialogSave() {
        val dialog = _dialogState.value ?: return
        if (dialog.isSaving) return

        val amountInCents = parseAmountToCents(dialog.amountText)
        val amountError = when {
            dialog.amountText.isBlank() -> "Amount is required"
            hasTooManyDecimalPlaces(dialog.amountText) -> "Enter an amount with up to 2 decimal places."
            amountInCents == null -> "Enter a valid amount"
            amountInCents <= 0L -> "Amount must be greater than zero"
            else -> null
        }
        val categoryError = if (dialog.categoryName == null) "Category is required" else null

        if (amountError != null || categoryError != null) {
            _dialogState.value = dialog.copy(amountError = amountError, categoryError = categoryError)
            return
        }

        val validAmount = amountInCents ?: return
        val validCategory = dialog.categoryName ?: return

        _dialogState.value = dialog.copy(isSaving = true, saveError = null)

        viewModelScope.launch {
            val result = if (dialog.isEdit) {
                budgetRepository.updateBudget(dialog.budgetId!!, validCategory, validAmount)
            } else {
                budgetRepository.addBudget(validCategory, validAmount)
            }
            result
                .onSuccess { _dialogState.value = null }
                .onFailure { error ->
                    _dialogState.value = _dialogState.value?.copy(isSaving = false, saveError = error.message)
                }
        }
    }

    fun onDeleteClick(budget: BudgetEntity) {
        _budgetPendingDelete.value = budget
    }

    fun onDeleteDismiss() {
        _budgetPendingDelete.value = null
    }

    fun onDeleteConfirm() {
        val budget = _budgetPendingDelete.value ?: return
        viewModelScope.launch {
            budgetRepository.deleteBudget(budget)
            _budgetPendingDelete.value = null
        }
    }

    companion object {
        fun factory(
            budgetRepository: BudgetRepository,
            categoryRepository: CategoryRepository,
            transactionRepository: TransactionRepository
        ) = viewModelFactory {
            initializer { BudgetsViewModel(budgetRepository, categoryRepository, transactionRepository) }
        }
    }
}

// Device-local calendar month boundaries, consistent with how transaction timestamps are stored.
private fun currentMonthMillisRange(zoneId: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val today = LocalDate.now(zoneId)
    val startMillis = today.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val endMillis = today.withDayOfMonth(today.lengthOfMonth())
        .atTime(LocalTime.MAX)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
    return startMillis to endMillis
}

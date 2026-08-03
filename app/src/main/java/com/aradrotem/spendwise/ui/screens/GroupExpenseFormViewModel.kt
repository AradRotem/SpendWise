package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import com.aradrotem.spendwise.data.repository.GroupExpenseRepository
import com.aradrotem.spendwise.domain.GroupExpenseSplit
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.hasTooManyDecimalPlaces
import com.aradrotem.spendwise.ui.format.parseAmountToCents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupExpenseFormViewModel(
    private val repository: GroupExpenseRepository,
    private val groupId: Long,
    private val expenseId: Long? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GroupExpenseFormUiState(groupId = groupId, expenseId = expenseId, isLoading = expenseId != null)
    )

    val uiState: StateFlow<GroupExpenseFormUiState> = combine(
        _uiState,
        repository.observeMembers(groupId)
    ) { state, members -> state.copy(members = members) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupExpenseFormUiState(groupId = groupId, expenseId = expenseId, isLoading = expenseId != null)
    )

    init {
        val id = expenseId
        if (id != null) {
            viewModelScope.launch {
                val expense = repository.getExpense(id)
                if (expense != null) {
                    val shares = repository.getShares(id)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = expense.title,
                            amountText = formatAmountInCents(expense.amountCents),
                            dateEpochDay = expense.dateEpochDay,
                            note = expense.note,
                            payerMemberId = expense.paidByMemberId,
                            participantMemberIds = shares.map { share -> share.memberId }.toSet(),
                            splitMethod = expense.splitMethod,
                            customShareTexts = shares.associate { share -> share.memberId to formatAmountInCents(share.shareAmountCents) }
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, loadError = "Expense not found") }
                }
            }
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, titleError = null, saveError = null) }
    }

    fun onAmountChange(amountText: String) {
        _uiState.update { it.copy(amountText = amountText, amountError = null, saveError = null) }
    }

    fun onDateChange(dateEpochDay: Long) {
        _uiState.update { it.copy(dateEpochDay = dateEpochDay) }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onPayerChange(memberId: Long) {
        _uiState.update { it.copy(payerMemberId = memberId, payerError = null, saveError = null) }
    }

    fun onToggleParticipant(memberId: Long) {
        _uiState.update { state ->
            val updated = if (memberId in state.participantMemberIds) {
                state.participantMemberIds - memberId
            } else {
                state.participantMemberIds + memberId
            }
            state.copy(participantMemberIds = updated, participantsError = null, saveError = null)
        }
    }

    fun onSplitMethodChange(splitMethod: GroupSplitMethod) {
        _uiState.update { it.copy(splitMethod = splitMethod, customShareErrors = emptyMap(), saveError = null) }
    }

    fun onCustomShareTextChange(memberId: Long, text: String) {
        _uiState.update {
            it.copy(
                customShareTexts = it.customShareTexts + (memberId to text),
                customShareErrors = it.customShareErrors - memberId,
                saveError = null
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val amountCents = parseAmountToCents(state.amountText)
        val titleError = if (state.title.isBlank()) "Title is required" else null
        val amountError = when {
            state.amountText.isBlank() -> "Amount is required"
            hasTooManyDecimalPlaces(state.amountText) -> "Enter an amount with up to 2 decimal places."
            amountCents == null -> "Enter a valid amount"
            amountCents <= 0L -> "Amount must be greater than zero"
            else -> null
        }
        val payerError = if (state.payerMemberId == null) "Select who paid" else null
        val participantsError = if (state.participantMemberIds.isEmpty()) "At least one participant is required" else null

        if (titleError != null || amountError != null || payerError != null || participantsError != null) {
            _uiState.update {
                it.copy(
                    titleError = titleError,
                    amountError = amountError,
                    payerError = payerError,
                    participantsError = participantsError
                )
            }
            return
        }

        val validAmount = amountCents ?: return
        val validPayer = state.payerMemberId ?: return
        val participantIds = state.participantMemberIds.toList()

        val shares: Map<Long, Long> = when (state.splitMethod) {
            GroupSplitMethod.EQUAL -> GroupExpenseSplit.equalShares(validAmount, participantIds)
            GroupSplitMethod.CUSTOM -> {
                val shareErrors = mutableMapOf<Long, String>()
                val parsedShares = mutableMapOf<Long, Long>()
                participantIds.forEach { memberId ->
                    val text = state.customShareTexts[memberId].orEmpty()
                    val result = validateCustomShareText(text)
                    result.fold(
                        onSuccess = { cents -> parsedShares[memberId] = cents },
                        onFailure = { error -> shareErrors[memberId] = error.message.orEmpty() }
                    )
                }
                if (shareErrors.isNotEmpty()) {
                    _uiState.update { it.copy(customShareErrors = shareErrors) }
                    return
                }
                val difference = GroupExpenseSplit.customSplitDifferenceCents(validAmount, parsedShares.values)
                if (difference != 0L) {
                    _uiState.update { it.copy(saveError = "Shares must add up to the total amount") }
                    return
                }
                parsedShares
            }
        }

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val result = if (state.isEditMode) {
                repository.updateExpense(
                    id = state.expenseId!!,
                    groupId = state.groupId,
                    title = state.title.trim(),
                    amountCents = validAmount,
                    dateEpochDay = state.dateEpochDay,
                    paidByMemberId = validPayer,
                    splitMethod = state.splitMethod,
                    note = state.note.trim(),
                    shares = shares
                )
            } else {
                repository.createExpense(
                    groupId = state.groupId,
                    title = state.title.trim(),
                    amountCents = validAmount,
                    dateEpochDay = state.dateEpochDay,
                    paidByMemberId = validPayer,
                    splitMethod = state.splitMethod,
                    note = state.note.trim(),
                    shares = shares
                ).map { }
            }
            result
                .onSuccess { _uiState.update { it.copy(isSaving = false, isSaved = true) } }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, saveError = error.message) } }
        }
    }

    companion object {
        fun factory(repository: GroupExpenseRepository, groupId: Long, expenseId: Long? = null) = viewModelFactory {
            initializer { GroupExpenseFormViewModel(repository, groupId, expenseId) }
        }
    }
}

// Parses and validates one participant's custom-split text entry. Kept as a top-level pure
// function (not inline in save()) so it's directly unit-testable without a Main dispatcher,
// mirroring AddRecurringPlanViewModel's categoryTypeForPlanType/resetCategoryIfInvalid.
fun validateCustomShareText(text: String): Result<Long> {
    val cents = parseAmountToCents(text)
    return when {
        text.isBlank() -> Result.failure(IllegalArgumentException("Required"))
        hasTooManyDecimalPlaces(text) -> Result.failure(IllegalArgumentException("Up to 2 decimal places"))
        cents == null -> Result.failure(IllegalArgumentException("Invalid amount"))
        cents < 0L -> Result.failure(IllegalArgumentException("Cannot be negative"))
        else -> Result.success(cents)
    }
}

package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupExpenseShareEntity
import com.aradrotem.spendwise.data.local.GroupMemberEntity
import com.aradrotem.spendwise.data.repository.GroupExpenseRepository
import com.aradrotem.spendwise.domain.GroupBalanceCalculator
import com.aradrotem.spendwise.domain.GroupExpenseForBalance
import com.aradrotem.spendwise.domain.GroupExpensePayerShares
import com.aradrotem.spendwise.domain.GroupPairwiseDebtCalculator
import com.aradrotem.spendwise.domain.GroupSettlementCalculator
import com.aradrotem.spendwise.domain.GroupSettlementExplanation
import com.aradrotem.spendwise.domain.GroupShareForBalance
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupDetailsViewModel(
    private val repository: GroupExpenseRepository,
    private val groupId: Long
) : ViewModel() {

    val uiState: StateFlow<GroupDetailsUiState> = combine(
        repository.observeGroups(),
        repository.observeMembers(groupId),
        repository.observeExpenses(groupId),
        repository.observeShares(groupId)
    ) { groups, members, expenses, shares ->
        buildGroupDetailsUiState(groupId, groups, members, expenses, shares)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupDetailsUiState(isLoading = true)
    )

    fun onDeleteExpense(expense: GroupExpenseEntity) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }

    companion object {
        fun factory(repository: GroupExpenseRepository, groupId: Long) = viewModelFactory {
            initializer { GroupDetailsViewModel(repository, groupId) }
        }
    }
}

// Pure transform from raw repository data to GroupDetailsUiState - kept top-level so it's
// directly unit-testable without a Main dispatcher.
fun buildGroupDetailsUiState(
    groupId: Long,
    groups: List<ExpenseGroupEntity>,
    members: List<GroupMemberEntity>,
    expenses: List<GroupExpenseEntity>,
    shares: List<GroupExpenseShareEntity>
): GroupDetailsUiState {
    val memberNameById = members.associateBy({ it.id }, { it.name })
    val sharesByExpense = shares.groupBy { it.expenseId }

    val balances = GroupBalanceCalculator.computeBalances(
        memberIds = members.map { it.id },
        expenses = expenses.map { GroupExpenseForBalance(it.paidByMemberId, it.amountCents) },
        shares = shares.map { GroupShareForBalance(it.memberId, it.shareAmountCents) }
    )

    val expenseItems = expenses.map { expense ->
        val participantIds = sharesByExpense[expense.id].orEmpty().map { it.memberId }
        GroupExpenseListItem(
            expense = expense,
            payerName = memberNameById[expense.paidByMemberId] ?: "Unknown",
            participantNames = participantIds.map { memberNameById[it] ?: "Unknown" }
        )
    }

    val originalDebts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(
        expenses.map { expense ->
            GroupExpensePayerShares(
                payerMemberId = expense.paidByMemberId,
                shares = sharesByExpense[expense.id].orEmpty().map { GroupShareForBalance(it.memberId, it.shareAmountCents) }
            )
        }
    )
    val settlements = GroupSettlementCalculator.simplify(balances)

    return GroupDetailsUiState(
        isLoading = false,
        group = groups.firstOrNull { it.id == groupId },
        members = members,
        expenses = expenseItems,
        balances = balances,
        originalDebts = originalDebts,
        settlements = settlements,
        settlementExplanation = GroupSettlementExplanation.explain(originalDebts, settlements, memberNameById, ::formatAmountInCents),
        totalSpentCents = expenses.sumOf { it.amountCents }
    )
}

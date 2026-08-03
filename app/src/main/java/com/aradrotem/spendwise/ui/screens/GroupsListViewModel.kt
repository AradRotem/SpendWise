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
import com.aradrotem.spendwise.domain.GroupShareForBalance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsListViewModel(
    private val repository: GroupExpenseRepository
) : ViewModel() {

    // All members/expenses/shares are loaded globally (not per-group) so every group's summary
    // card can be computed in one combine(), mirroring HomeViewModel's dashboard-aggregation
    // pattern - the local dataset is small enough that this is simpler than a per-group
    // subscription per card.
    val uiState: StateFlow<GroupsListUiState> = combine(
        repository.observeGroups(),
        repository.observeAllMembers(),
        repository.observeAllExpenses(),
        repository.observeAllShares()
    ) { groups, members, expenses, shares ->
        buildGroupsListUiState(groups, members, expenses, shares)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupsListUiState(isLoading = true)
    )

    fun onDeleteGroup(group: ExpenseGroupEntity) {
        viewModelScope.launch { repository.deleteGroup(group) }
    }

    companion object {
        fun factory(repository: GroupExpenseRepository) = viewModelFactory {
            initializer { GroupsListViewModel(repository) }
        }
    }
}

// Pure transform from raw repository data to GroupsListUiState - kept top-level (not a ViewModel
// member) so it's directly unit-testable without a Main dispatcher, mirroring buildHomeUiState.
fun buildGroupsListUiState(
    groups: List<ExpenseGroupEntity>,
    members: List<GroupMemberEntity>,
    expenses: List<GroupExpenseEntity>,
    shares: List<GroupExpenseShareEntity>
): GroupsListUiState {
    val membersByGroup = members.groupBy { it.groupId }
    val expensesByGroup = expenses.groupBy { it.groupId }

    val items = groups.map { group ->
        val groupMembers = membersByGroup[group.id].orEmpty()
        val groupExpenses = expensesByGroup[group.id].orEmpty()
        val groupExpenseIds = groupExpenses.map { it.id }.toSet()
        val groupShares = shares.filter { it.expenseId in groupExpenseIds }

        val balances = GroupBalanceCalculator.computeBalances(
            memberIds = groupMembers.map { it.id },
            expenses = groupExpenses.map { GroupExpenseForBalance(it.paidByMemberId, it.amountCents) },
            shares = groupShares.map { GroupShareForBalance(it.memberId, it.shareAmountCents) }
        )

        GroupSummary(
            group = group,
            memberCount = groupMembers.size,
            expenseCount = groupExpenses.size,
            totalSpentCents = groupExpenses.sumOf { it.amountCents },
            isSettled = balances.all { it.netBalanceCents == 0L }
        )
    }
    return GroupsListUiState(isLoading = false, items = items)
}

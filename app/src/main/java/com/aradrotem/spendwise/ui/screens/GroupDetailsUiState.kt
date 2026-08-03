package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupMemberEntity
import com.aradrotem.spendwise.domain.GroupMemberBalance
import com.aradrotem.spendwise.domain.GroupPairwiseDebt
import com.aradrotem.spendwise.domain.GroupSettlement

data class GroupExpenseListItem(
    val expense: GroupExpenseEntity,
    val payerName: String,
    val participantNames: List<String>
)

data class GroupDetailsUiState(
    val isLoading: Boolean = true,
    val group: ExpenseGroupEntity? = null,
    val members: List<GroupMemberEntity> = emptyList(),
    val expenses: List<GroupExpenseListItem> = emptyList(),
    val balances: List<GroupMemberBalance> = emptyList(),
    // Original, expense-level pairwise debts (see GroupPairwiseDebtCalculator) - distinct from
    // settlements below, which may redirect payments through other members.
    val originalDebts: List<GroupPairwiseDebt> = emptyList(),
    val settlements: List<GroupSettlement> = emptyList(),
    // Null when there's nothing to explain (no settlements, or the settlements are identical to
    // the original debts) - see GroupSettlementExplanation.
    val settlementExplanation: String? = null,
    val totalSpentCents: Long = 0L
) {
    val canAddExpense: Boolean get() = members.size >= 2

    val memberNameById: Map<Long, String> get() = members.associateBy({ it.id }, { it.name })
}

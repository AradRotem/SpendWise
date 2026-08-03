package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.ExpenseGroupEntity

data class GroupSummary(
    val group: ExpenseGroupEntity,
    val memberCount: Int,
    val expenseCount: Int,
    val totalSpentCents: Long,
    val isSettled: Boolean
)

data class GroupsListUiState(
    val isLoading: Boolean = true,
    val items: List<GroupSummary> = emptyList()
)

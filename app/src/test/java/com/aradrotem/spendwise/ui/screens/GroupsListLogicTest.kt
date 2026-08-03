package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupExpenseShareEntity
import com.aradrotem.spendwise.data.local.GroupMemberEntity
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupsListLogicTest {

    @Test
    fun buildGroupsListUiState_groupWithNoExpenses_isSettledWithZeroTotal() {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        val members = listOf(GroupMemberEntity(id = 1L, groupId = 1L, name = "Ann"), GroupMemberEntity(id = 2L, groupId = 1L, name = "Ben"))

        val state = buildGroupsListUiState(listOf(group), members, emptyList(), emptyList())

        val summary = state.items.single()
        assertEquals(0L, summary.totalSpentCents)
        assertTrue(summary.isSettled)
        assertEquals(2, summary.memberCount)
        assertEquals(0, summary.expenseCount)
    }

    @Test
    fun buildGroupsListUiState_unsettledGroup_reportsUnsettled() {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        val members = listOf(GroupMemberEntity(id = 1L, groupId = 1L, name = "Ann"), GroupMemberEntity(id = 2L, groupId = 1L, name = "Ben"))
        val expense = GroupExpenseEntity(
            id = 10L, groupId = 1L, title = "Dinner", amountCents = 4_000L, dateEpochDay = 0L,
            paidByMemberId = 1L, splitMethod = GroupSplitMethod.EQUAL
        )
        val shares = listOf(GroupExpenseShareEntity(expenseId = 10L, memberId = 1L, shareAmountCents = 2_000L), GroupExpenseShareEntity(expenseId = 10L, memberId = 2L, shareAmountCents = 2_000L))

        val state = buildGroupsListUiState(listOf(group), members, listOf(expense), shares)

        val summary = state.items.single()
        assertEquals(4_000L, summary.totalSpentCents)
        assertFalse(summary.isSettled)
        assertEquals(1, summary.expenseCount)
    }

    @Test
    fun buildGroupsListUiState_multipleGroups_dataStaysSeparate() {
        val groupA = ExpenseGroupEntity(id = 1L, name = "Trip")
        val groupB = ExpenseGroupEntity(id = 2L, name = "Roommates")
        val members = listOf(
            GroupMemberEntity(id = 1L, groupId = 1L, name = "Ann"),
            GroupMemberEntity(id = 2L, groupId = 2L, name = "Ben")
        )
        val expenseA = GroupExpenseEntity(
            id = 10L, groupId = 1L, title = "Dinner", amountCents = 1_000L, dateEpochDay = 0L,
            paidByMemberId = 1L, splitMethod = GroupSplitMethod.EQUAL
        )

        val state = buildGroupsListUiState(listOf(groupA, groupB), members, listOf(expenseA), emptyList())

        val byId = state.items.associateBy { it.group.id }
        assertEquals(1, byId[1L]!!.expenseCount)
        assertEquals(0, byId[2L]!!.expenseCount)
        assertEquals(1_000L, byId[1L]!!.totalSpentCents)
        assertEquals(0L, byId[2L]!!.totalSpentCents)
    }
}

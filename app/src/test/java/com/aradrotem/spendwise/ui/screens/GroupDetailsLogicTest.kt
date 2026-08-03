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

class GroupDetailsLogicTest {

    private val group = ExpenseGroupEntity(id = 1L, name = "Trip")

    @Test
    fun canAddExpense_isFalse_whenFewerThanTwoMembers() {
        val members = listOf(GroupMemberEntity(id = 1L, groupId = 1L, name = "Ann"))

        val state = buildGroupDetailsUiState(1L, listOf(group), members, emptyList(), emptyList())

        assertFalse(state.canAddExpense)
    }

    @Test
    fun canAddExpense_isTrue_withTwoOrMoreMembers() {
        val members = listOf(
            GroupMemberEntity(id = 1L, groupId = 1L, name = "Ann"),
            GroupMemberEntity(id = 2L, groupId = 1L, name = "Ben"),
            GroupMemberEntity(id = 3L, groupId = 1L, name = "Cal")
        )

        val state = buildGroupDetailsUiState(1L, listOf(group), members, emptyList(), emptyList())

        assertTrue(state.canAddExpense)
    }

    @Test
    fun noExpenses_producesEmptyExpenseListAndZeroTotal() {
        val members = listOf(
            GroupMemberEntity(id = 1L, groupId = 1L, name = "Ann"),
            GroupMemberEntity(id = 2L, groupId = 1L, name = "Ben")
        )

        val state = buildGroupDetailsUiState(1L, listOf(group), members, emptyList(), emptyList())

        assertTrue(state.expenses.isEmpty())
        assertEquals(0L, state.totalSpentCents)
        assertTrue(state.balances.all { it.netBalanceCents == 0L })
    }

    @Test
    fun addingAnExpense_updatesTotalsAndBalancesImmediately() {
        val members = listOf(
            GroupMemberEntity(id = 1L, groupId = 1L, name = "Ann"),
            GroupMemberEntity(id = 2L, groupId = 1L, name = "Ben")
        )

        val before = buildGroupDetailsUiState(1L, listOf(group), members, emptyList(), emptyList())
        assertEquals(0L, before.totalSpentCents)

        val expense = GroupExpenseEntity(
            id = 10L, groupId = 1L, title = "Dinner", amountCents = 4_000L, dateEpochDay = 0L,
            paidByMemberId = 1L, splitMethod = GroupSplitMethod.EQUAL
        )
        val shares = listOf(
            GroupExpenseShareEntity(expenseId = 10L, memberId = 1L, shareAmountCents = 2_000L),
            GroupExpenseShareEntity(expenseId = 10L, memberId = 2L, shareAmountCents = 2_000L)
        )

        val after = buildGroupDetailsUiState(1L, listOf(group), members, listOf(expense), shares)

        assertEquals(4_000L, after.totalSpentCents)
        assertEquals(1, after.expenses.size)
        val annBalance = after.balances.first { it.memberId == 1L }
        val benBalance = after.balances.first { it.memberId == 2L }
        assertEquals(2_000L, annBalance.netBalanceCents)
        assertEquals(-2_000L, benBalance.netBalanceCents)
        assertEquals(1, after.settlements.size)
        assertEquals(2_000L, after.settlements.single().amountCents)
    }

    @Test
    fun expenseListItem_showsPayerAndParticipantNames() {
        val members = listOf(
            GroupMemberEntity(id = 1L, groupId = 1L, name = "Ann"),
            GroupMemberEntity(id = 2L, groupId = 1L, name = "Ben")
        )
        val expense = GroupExpenseEntity(
            id = 10L, groupId = 1L, title = "Dinner", amountCents = 2_000L, dateEpochDay = 0L,
            paidByMemberId = 1L, splitMethod = GroupSplitMethod.EQUAL
        )
        val shares = listOf(
            GroupExpenseShareEntity(expenseId = 10L, memberId = 1L, shareAmountCents = 1_000L),
            GroupExpenseShareEntity(expenseId = 10L, memberId = 2L, shareAmountCents = 1_000L)
        )

        val state = buildGroupDetailsUiState(1L, listOf(group), members, listOf(expense), shares)

        val item = state.expenses.single()
        assertEquals("Ann", item.payerName)
        assertEquals(setOf("Ann", "Ben"), item.participantNames.toSet())
    }

    @Test
    fun physicalDeviceExample_originalDebtsAndSettlementsAreComputedTogether() {
        val members = listOf(
            GroupMemberEntity(id = 1L, groupId = 1L, name = "Bar"),
            GroupMemberEntity(id = 2L, groupId = 1L, name = "Shai"),
            GroupMemberEntity(id = 3L, groupId = 1L, name = "Dor")
        )
        val expenses = listOf(
            GroupExpenseEntity(id = 10L, groupId = 1L, title = "Car rental", amountCents = 60_000L, dateEpochDay = 0L, paidByMemberId = 1L, splitMethod = GroupSplitMethod.EQUAL),
            GroupExpenseEntity(id = 11L, groupId = 1L, title = "Groceries", amountCents = 10_000L, dateEpochDay = 0L, paidByMemberId = 2L, splitMethod = GroupSplitMethod.EQUAL),
            GroupExpenseEntity(id = 12L, groupId = 1L, title = "Flights", amountCents = 300_000L, dateEpochDay = 0L, paidByMemberId = 3L, splitMethod = GroupSplitMethod.EQUAL)
        )
        val shares = listOf(
            GroupExpenseShareEntity(expenseId = 10L, memberId = 1L, shareAmountCents = 20_000L),
            GroupExpenseShareEntity(expenseId = 10L, memberId = 2L, shareAmountCents = 20_000L),
            GroupExpenseShareEntity(expenseId = 10L, memberId = 3L, shareAmountCents = 20_000L),
            GroupExpenseShareEntity(expenseId = 11L, memberId = 2L, shareAmountCents = 5_000L),
            GroupExpenseShareEntity(expenseId = 11L, memberId = 3L, shareAmountCents = 5_000L),
            GroupExpenseShareEntity(expenseId = 12L, memberId = 3L, shareAmountCents = 100_000L),
            GroupExpenseShareEntity(expenseId = 12L, memberId = 2L, shareAmountCents = 100_000L),
            GroupExpenseShareEntity(expenseId = 12L, memberId = 1L, shareAmountCents = 100_000L)
        )

        val state = buildGroupDetailsUiState(1L, listOf(group), members, expenses, shares)

        assertEquals(
            setOf(
                com.aradrotem.spendwise.domain.GroupPairwiseDebt(2L, 1L, 20_000L),
                com.aradrotem.spendwise.domain.GroupPairwiseDebt(2L, 3L, 95_000L),
                com.aradrotem.spendwise.domain.GroupPairwiseDebt(1L, 3L, 80_000L)
            ),
            state.originalDebts.toSet()
        )
        assertEquals(
            setOf(
                com.aradrotem.spendwise.domain.GroupSettlement(2L, 3L, 115_000L),
                com.aradrotem.spendwise.domain.GroupSettlement(1L, 3L, 60_000L)
            ),
            state.settlements.toSet()
        )
        requireNotNull(state.settlementExplanation)
    }

    @Test
    fun editingAnExpense_updatesOriginalDebtsAndSettlementsImmediately() {
        val members = listOf(
            GroupMemberEntity(id = 1L, groupId = 1L, name = "Ann"),
            GroupMemberEntity(id = 2L, groupId = 1L, name = "Ben")
        )
        val originalExpense = GroupExpenseEntity(
            id = 10L, groupId = 1L, title = "Dinner", amountCents = 4_000L, dateEpochDay = 0L,
            paidByMemberId = 1L, splitMethod = GroupSplitMethod.EQUAL
        )
        val originalShares = listOf(
            GroupExpenseShareEntity(expenseId = 10L, memberId = 1L, shareAmountCents = 2_000L),
            GroupExpenseShareEntity(expenseId = 10L, memberId = 2L, shareAmountCents = 2_000L)
        )
        val before = buildGroupDetailsUiState(1L, listOf(group), members, listOf(originalExpense), originalShares)
        assertEquals(1, before.originalDebts.size)
        assertEquals(2_000L, before.originalDebts.single().amountCents)

        // Simulate the same expense edited: payer switched to Ben, amount changed, CUSTOM shares.
        val editedExpense = originalExpense.copy(amountCents = 10_000L, paidByMemberId = 2L, splitMethod = GroupSplitMethod.CUSTOM)
        val editedShares = listOf(
            GroupExpenseShareEntity(expenseId = 10L, memberId = 1L, shareAmountCents = 6_000L),
            GroupExpenseShareEntity(expenseId = 10L, memberId = 2L, shareAmountCents = 4_000L)
        )

        val after = buildGroupDetailsUiState(1L, listOf(group), members, listOf(editedExpense), editedShares)

        assertEquals(10_000L, after.totalSpentCents)
        assertEquals(1, after.originalDebts.size)
        val debt = after.originalDebts.single()
        assertEquals(1L, debt.debtorMemberId) // Ann now owes Ben (Ben is the new payer)
        assertEquals(2L, debt.creditorMemberId)
        assertEquals(6_000L, debt.amountCents)
        assertEquals(1, after.settlements.size)
    }

    @Test
    fun fullySettledGroup_hasNoOriginalDebtsAndNoExplanation() {
        val members = listOf(
            GroupMemberEntity(id = 1L, groupId = 1L, name = "Ann"),
            GroupMemberEntity(id = 2L, groupId = 1L, name = "Ben")
        )

        val state = buildGroupDetailsUiState(1L, listOf(group), members, emptyList(), emptyList())

        assertTrue(state.originalDebts.isEmpty())
        assertTrue(state.settlements.isEmpty())
        assertEquals(null, state.settlementExplanation)
    }
}

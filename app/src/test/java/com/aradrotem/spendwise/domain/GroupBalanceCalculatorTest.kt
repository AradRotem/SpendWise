package com.aradrotem.spendwise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupBalanceCalculatorTest {

    @Test
    fun onePayerMultipleParticipants_paidAndOwedComputedCorrectly() {
        // Member 1 pays 9000 for members 1,2,3 (equal split: 3000 each).
        val balances = GroupBalanceCalculator.computeBalances(
            memberIds = listOf(1L, 2L, 3L),
            expenses = listOf(GroupExpenseForBalance(paidByMemberId = 1L, amountCents = 9_000L)),
            shares = listOf(
                GroupShareForBalance(1L, 3_000L),
                GroupShareForBalance(2L, 3_000L),
                GroupShareForBalance(3L, 3_000L)
            )
        )

        val byId = balances.associateBy { it.memberId }
        assertEquals(9_000L, byId[1L]!!.paidCents)
        assertEquals(3_000L, byId[1L]!!.owedCents)
        assertEquals(6_000L, byId[1L]!!.netBalanceCents)
        assertEquals(-3_000L, byId[2L]!!.netBalanceCents)
        assertEquals(-3_000L, byId[3L]!!.netBalanceCents)
    }

    @Test
    fun payerIncludedAsParticipant_ownShareReducesNetBalance() {
        val balances = GroupBalanceCalculator.computeBalances(
            memberIds = listOf(1L, 2L),
            expenses = listOf(GroupExpenseForBalance(paidByMemberId = 1L, amountCents = 4_000L)),
            shares = listOf(GroupShareForBalance(1L, 2_000L), GroupShareForBalance(2L, 2_000L))
        )
        val byId = balances.associateBy { it.memberId }
        assertEquals(2_000L, byId[1L]!!.netBalanceCents)
        assertEquals(-2_000L, byId[2L]!!.netBalanceCents)
    }

    @Test
    fun payerExcludedFromParticipants_paysButOwesNothing() {
        // Member 1 pays for members 2 and 3 only.
        val balances = GroupBalanceCalculator.computeBalances(
            memberIds = listOf(1L, 2L, 3L),
            expenses = listOf(GroupExpenseForBalance(paidByMemberId = 1L, amountCents = 2_000L)),
            shares = listOf(GroupShareForBalance(2L, 1_000L), GroupShareForBalance(3L, 1_000L))
        )
        val byId = balances.associateBy { it.memberId }
        assertEquals(0L, byId[1L]!!.owedCents)
        assertEquals(2_000L, byId[1L]!!.netBalanceCents)
    }

    @Test
    fun multipleExpensesWithDifferentPayers_accumulateCorrectly() {
        val balances = GroupBalanceCalculator.computeBalances(
            memberIds = listOf(1L, 2L),
            expenses = listOf(
                GroupExpenseForBalance(paidByMemberId = 1L, amountCents = 2_000L),
                GroupExpenseForBalance(paidByMemberId = 2L, amountCents = 4_000L)
            ),
            shares = listOf(
                GroupShareForBalance(1L, 1_000L), GroupShareForBalance(2L, 1_000L),
                GroupShareForBalance(1L, 2_000L), GroupShareForBalance(2L, 2_000L)
            )
        )
        val byId = balances.associateBy { it.memberId }
        assertEquals(2_000L, byId[1L]!!.paidCents)
        assertEquals(3_000L, byId[1L]!!.owedCents)
        assertEquals(-1_000L, byId[1L]!!.netBalanceCents)
        assertEquals(4_000L, byId[2L]!!.paidCents)
        assertEquals(3_000L, byId[2L]!!.owedCents)
        assertEquals(1_000L, byId[2L]!!.netBalanceCents)
    }

    @Test
    fun fullySettledGroup_allBalancesZero() {
        val balances = GroupBalanceCalculator.computeBalances(
            memberIds = listOf(1L, 2L),
            expenses = emptyList(),
            shares = emptyList()
        )
        assertTrue(balances.all { it.netBalanceCents == 0L })
    }

    @Test
    fun sumOfNetBalances_alwaysEqualsZero() {
        val balances = GroupBalanceCalculator.computeBalances(
            memberIds = listOf(1L, 2L, 3L, 4L),
            expenses = listOf(
                GroupExpenseForBalance(paidByMemberId = 1L, amountCents = 10_000L),
                GroupExpenseForBalance(paidByMemberId = 3L, amountCents = 3_333L)
            ),
            shares = listOf(
                GroupShareForBalance(1L, 2_500L), GroupShareForBalance(2L, 2_500L),
                GroupShareForBalance(3L, 2_500L), GroupShareForBalance(4L, 2_500L),
                GroupShareForBalance(2L, 1_111L), GroupShareForBalance(3L, 1_111L), GroupShareForBalance(4L, 1_111L)
            )
        )
        assertEquals(0L, balances.sumOf { it.netBalanceCents })
    }
}

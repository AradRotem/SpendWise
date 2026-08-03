package com.aradrotem.spendwise.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupExpenseSplitTest {

    @Test
    fun equalShares_twoParticipants_exactDivision() {
        val shares = GroupExpenseSplit.equalShares(10_000L, listOf(1L, 2L))

        assertEquals(5_000L, shares[1L])
        assertEquals(5_000L, shares[2L])
        assertEquals(10_000L, shares.values.sum())
    }

    @Test
    fun equalShares_threeParticipants_remainderGoesToFirstMembersInOrder() {
        val shares = GroupExpenseSplit.equalShares(10_000L, listOf(1L, 2L, 3L))

        // 10000 / 3 = 3333 remainder 1 -> first participant gets the extra cent.
        assertEquals(3_334L, shares[1L])
        assertEquals(3_333L, shares[2L])
        assertEquals(3_333L, shares[3L])
        assertEquals(10_000L, shares.values.sum())
    }

    @Test
    fun equalShares_totalSmallerThanParticipantCount_deterministicZeroShares() {
        val shares = GroupExpenseSplit.equalShares(2L, listOf(1L, 2L, 3L))

        assertEquals(1L, shares[1L])
        assertEquals(1L, shares[2L])
        assertEquals(0L, shares[3L])
        assertEquals(2L, shares.values.sum())
    }

    @Test
    fun equalShares_remainderAssignment_isDeterministicAcrossCalls() {
        val first = GroupExpenseSplit.equalShares(1_001L, listOf(10L, 20L, 30L))
        val second = GroupExpenseSplit.equalShares(1_001L, listOf(10L, 20L, 30L))

        assertEquals(first, second)
    }

    @Test
    fun equalShares_alwaysSumsToOriginalAmount() {
        val amounts = listOf(1L, 99L, 100L, 12_345L, 999_999L)
        val participantCounts = listOf(1, 2, 3, 5, 7)

        for (amount in amounts) {
            for (count in participantCounts) {
                val ids = (1..count).map { it.toLong() }
                val shares = GroupExpenseSplit.equalShares(amount, ids)
                assertEquals("amount=$amount count=$count", amount, shares.values.sum())
            }
        }
    }

    @Test
    fun customSplitDifferenceCents_exactMatch_isZero() {
        assertEquals(0L, GroupExpenseSplit.customSplitDifferenceCents(10_000L, listOf(4_000L, 6_000L)))
    }

    @Test
    fun customSplitDifferenceCents_underAllocated_isPositive() {
        assertEquals(1_000L, GroupExpenseSplit.customSplitDifferenceCents(10_000L, listOf(4_000L, 5_000L)))
    }

    @Test
    fun customSplitDifferenceCents_overAllocated_isNegative() {
        assertEquals(-500L, GroupExpenseSplit.customSplitDifferenceCents(10_000L, listOf(4_000L, 6_500L)))
    }
}

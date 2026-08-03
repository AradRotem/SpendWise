package com.aradrotem.spendwise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSettlementCalculatorTest {

    private fun balance(memberId: Long, paid: Long, owed: Long) = GroupMemberBalance(memberId, paid, owed)

    @Test
    fun oneDebtorOneCreditor_singleTransfer() {
        val balances = listOf(balance(1L, 8_000L, 0L), balance(2L, 0L, 8_000L))

        val settlements = GroupSettlementCalculator.simplify(balances)

        assertEquals(listOf(GroupSettlement(2L, 1L, 8_000L)), settlements)
    }

    @Test
    fun multipleDebtorsOneCreditor() {
        // Member 1: +8000, member 2: -3000, member 3: -5000
        val balances = listOf(balance(1L, 8_000L, 0L), balance(2L, 0L, 3_000L), balance(3L, 0L, 5_000L))

        val settlements = GroupSettlementCalculator.simplify(balances)

        assertEquals(2, settlements.size)
        assertEquals(8_000L, settlements.sumOf { it.amountCents })
        assertTrue(settlements.all { it.toMemberId == 1L })
    }

    @Test
    fun oneDebtorMultipleCreditors() {
        // Member 1: -8000, member 2: +3000, member 3: +5000
        val balances = listOf(balance(1L, 0L, 8_000L), balance(2L, 3_000L, 0L), balance(3L, 5_000L, 0L))

        val settlements = GroupSettlementCalculator.simplify(balances)

        assertEquals(2, settlements.size)
        assertEquals(8_000L, settlements.sumOf { it.amountCents })
        assertTrue(settlements.all { it.fromMemberId == 1L })
    }

    @Test
    fun multipleDebtorsAndCreditors_allSettleAfterTransfers() {
        val balances = listOf(
            balance(1L, 10_000L, 0L),
            balance(2L, 0L, 4_000L),
            balance(3L, 0L, 3_000L),
            balance(4L, 0L, 3_000L)
        )

        val settlements = GroupSettlementCalculator.simplify(balances)

        val net = mutableMapOf(1L to 10_000L, 2L to -4_000L, 3L to -3_000L, 4L to -3_000L)
        settlements.forEach { s ->
            net[s.fromMemberId] = net.getValue(s.fromMemberId) + s.amountCents
            net[s.toMemberId] = net.getValue(s.toMemberId) - s.amountCents
        }
        assertTrue(net.values.all { it == 0L })
    }

    @Test
    fun alreadySettledGroup_producesNoSettlements() {
        val balances = listOf(balance(1L, 5_000L, 5_000L), balance(2L, 0L, 0L))

        assertTrue(GroupSettlementCalculator.simplify(balances).isEmpty())
    }

    @Test
    fun neverProducesZeroValueTransfers() {
        val balances = listOf(balance(1L, 100L, 0L), balance(2L, 0L, 100L))

        val settlements = GroupSettlementCalculator.simplify(balances)

        assertTrue(settlements.all { it.amountCents > 0L })
    }

    @Test
    fun totalMoneySent_equalsTotalDebt() {
        val balances = listOf(
            balance(1L, 12_345L, 0L),
            balance(2L, 0L, 6_000L),
            balance(3L, 0L, 6_345L)
        )

        val settlements = GroupSettlementCalculator.simplify(balances)

        assertEquals(12_345L, settlements.sumOf { it.amountCents })
    }

    @Test
    fun deterministicOutput_sameInputProducesSameSettlements() {
        val balances = listOf(
            balance(1L, 7_000L, 0L),
            balance(2L, 3_000L, 0L),
            balance(3L, 0L, 5_000L),
            balance(4L, 0L, 5_000L)
        )

        val first = GroupSettlementCalculator.simplify(balances)
        val second = GroupSettlementCalculator.simplify(balances)

        assertEquals(first, second)
    }
}

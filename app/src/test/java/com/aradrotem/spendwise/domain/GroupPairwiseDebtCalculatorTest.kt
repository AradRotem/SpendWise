package com.aradrotem.spendwise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPairwiseDebtCalculatorTest {

    @Test
    fun onePayerOneParticipant_producesSingleDebt() {
        // Payer=1 pays for participant 2 only (payer excluded from shares).
        val expenses = listOf(GroupExpensePayerShares(payerMemberId = 1L, shares = listOf(GroupShareForBalance(2L, 1_000L))))

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertEquals(listOf(GroupPairwiseDebt(2L, 1L, 1_000L)), debts)
    }

    @Test
    fun payerAlsoParticipant_noSelfDebt() {
        val expenses = listOf(
            GroupExpensePayerShares(
                payerMemberId = 1L,
                shares = listOf(GroupShareForBalance(1L, 500L), GroupShareForBalance(2L, 500L))
            )
        )

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertEquals(listOf(GroupPairwiseDebt(2L, 1L, 500L)), debts)
    }

    @Test
    fun threeWayEqualSplit_producesTwoDebtsToPayer() {
        val expenses = listOf(
            GroupExpensePayerShares(
                payerMemberId = 1L,
                shares = listOf(GroupShareForBalance(1L, 100L), GroupShareForBalance(2L, 100L), GroupShareForBalance(3L, 100L))
            )
        )

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertEquals(setOf(GroupPairwiseDebt(2L, 1L, 100L), GroupPairwiseDebt(3L, 1L, 100L)), debts.toSet())
    }

    @Test
    fun customSplit_usesStoredShareAmountsExactly() {
        val expenses = listOf(
            GroupExpensePayerShares(
                payerMemberId = 1L,
                shares = listOf(GroupShareForBalance(1L, 15_000L), GroupShareForBalance(2L, 25_000L), GroupShareForBalance(3L, 10_000L))
            )
        )

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertEquals(setOf(GroupPairwiseDebt(2L, 1L, 25_000L), GroupPairwiseDebt(3L, 1L, 10_000L)), debts.toSet())
    }

    @Test
    fun multipleExpensesWithDifferentPayers_accumulate() {
        val expenses = listOf(
            GroupExpensePayerShares(payerMemberId = 1L, shares = listOf(GroupShareForBalance(2L, 1_000L))),
            GroupExpensePayerShares(payerMemberId = 1L, shares = listOf(GroupShareForBalance(2L, 500L)))
        )

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertEquals(listOf(GroupPairwiseDebt(2L, 1L, 1_500L)), debts)
    }

    @Test
    fun opposingDebtsBetweenSamePair_areOffset() {
        val expenses = listOf(
            GroupExpensePayerShares(payerMemberId = 1L, shares = listOf(GroupShareForBalance(2L, 1_000L))),
            GroupExpensePayerShares(payerMemberId = 2L, shares = listOf(GroupShareForBalance(1L, 300L)))
        )

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertEquals(listOf(GroupPairwiseDebt(2L, 1L, 700L)), debts)
    }

    @Test
    fun opposingDebtsThatCancelExactly_produceNoRow() {
        val expenses = listOf(
            GroupExpensePayerShares(payerMemberId = 1L, shares = listOf(GroupShareForBalance(2L, 1_000L))),
            GroupExpensePayerShares(payerMemberId = 2L, shares = listOf(GroupShareForBalance(1L, 1_000L)))
        )

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertTrue(debts.isEmpty())
    }

    @Test
    fun noSelfDebts_evenWhenOnlyParticipantIsPayer() {
        val expenses = listOf(GroupExpensePayerShares(payerMemberId = 1L, shares = listOf(GroupShareForBalance(1L, 1_000L))))

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertTrue(debts.isEmpty())
    }

    @Test
    fun noZeroValueRows() {
        val expenses = listOf(GroupExpensePayerShares(payerMemberId = 1L, shares = listOf(GroupShareForBalance(2L, 0L))))

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertTrue(debts.isEmpty())
    }

    @Test
    fun deterministicOrdering_sortedByDebtorThenCreditorId() {
        val expenses = listOf(
            GroupExpensePayerShares(payerMemberId = 1L, shares = listOf(GroupShareForBalance(3L, 100L), GroupShareForBalance(2L, 200L)))
        )

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertEquals(listOf(GroupPairwiseDebt(2L, 1L, 200L), GroupPairwiseDebt(3L, 1L, 100L)), debts)
    }

    @Test
    fun largeCentValues_doNotOverflowOrLosePrecision() {
        val large = 999_999_999_999L
        val expenses = listOf(GroupExpensePayerShares(payerMemberId = 1L, shares = listOf(GroupShareForBalance(2L, large))))

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses)

        assertEquals(large, debts.single().amountCents)
    }

    @Test
    fun physicalDeviceExample_producesExpectedThreeDebts() {
        // Bar(1) pays 60000, split equally between Bar,Shai,Dor -> 20000 each.
        // Shai(2) pays 10000, split equally between Shai,Dor -> 5000 each.
        // Dor(3) pays 300000, split equally between Dor,Shai,Bar -> 100000 each.
        val expenses = listOf(
            GroupExpensePayerShares(
                payerMemberId = 1L,
                shares = listOf(GroupShareForBalance(1L, 20_000L), GroupShareForBalance(2L, 20_000L), GroupShareForBalance(3L, 20_000L))
            ),
            GroupExpensePayerShares(
                payerMemberId = 2L,
                shares = listOf(GroupShareForBalance(2L, 5_000L), GroupShareForBalance(3L, 5_000L))
            ),
            GroupExpensePayerShares(
                payerMemberId = 3L,
                shares = listOf(GroupShareForBalance(3L, 100_000L), GroupShareForBalance(2L, 100_000L), GroupShareForBalance(1L, 100_000L))
            )
        )

        val debts = GroupPairwiseDebtCalculator.calculatePairwiseDebts(expenses).toSet()

        assertEquals(
            setOf(
                GroupPairwiseDebt(2L, 1L, 20_000L), // Shai -> Bar
                GroupPairwiseDebt(2L, 3L, 95_000L), // Shai -> Dor
                GroupPairwiseDebt(1L, 3L, 80_000L)  // Bar -> Dor
            ),
            debts
        )
    }
}

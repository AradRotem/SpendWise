package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDistributionCalculatorTest {

    private fun tx(amountCents: Long, type: TransactionType, category: String) =
        TransactionEntity(amountInCents = amountCents, type = type, category = category, timestamp = 1_000L)

    @Test
    fun multipleTransactionsInOneCategory_areSummed() {
        val points = CategoryDistributionCalculator.calculate(
            listOf(tx(1_000L, TransactionType.EXPENSE, "FOOD"), tx(2_000L, TransactionType.EXPENSE, "FOOD"))
        )
        assertEquals(1, points.size)
        assertEquals(3_000L, points.single().amountCents)
        assertEquals(2, points.single().transactionCount)
    }

    @Test
    fun multipleCategories_eachAggregatedSeparately() {
        val points = CategoryDistributionCalculator.calculate(
            listOf(tx(1_000L, TransactionType.EXPENSE, "FOOD"), tx(2_000L, TransactionType.EXPENSE, "TRANSPORT"))
        )
        assertEquals(2, points.size)
        assertEquals(setOf("FOOD", "TRANSPORT"), points.map { it.categoryName }.toSet())
    }

    @Test
    fun customCategory_isAggregatedLikeAnyOther() {
        val points = CategoryDistributionCalculator.calculate(listOf(tx(500L, TransactionType.EXPENSE, "Custom Hobby")))
        assertEquals("Custom Hobby", points.single().categoryName)
        assertEquals(500L, points.single().amountCents)
    }

    @Test
    fun otherCategory_isAggregatedLikeAnyOther() {
        val points = CategoryDistributionCalculator.calculate(listOf(tx(500L, TransactionType.EXPENSE, "OTHER")))
        assertEquals("OTHER", points.single().categoryName)
    }

    @Test
    fun incomeTransactions_excludedFromDistribution() {
        val points = CategoryDistributionCalculator.calculate(
            listOf(tx(5_000L, TransactionType.INCOME, "SALARY"), tx(1_000L, TransactionType.EXPENSE, "FOOD"))
        )
        assertEquals(1, points.size)
        assertEquals("FOOD", points.single().categoryName)
    }

    @Test
    fun zeroExpenses_producesEmptyList() {
        assertTrue(CategoryDistributionCalculator.calculate(emptyList()).isEmpty())
        assertTrue(CategoryDistributionCalculator.calculate(listOf(tx(5_000L, TransactionType.INCOME, "SALARY"))).isEmpty())
    }

    @Test
    fun categoryTotals_sumExactlyToTotalExpense() {
        val transactions = listOf(
            tx(1_234L, TransactionType.EXPENSE, "FOOD"),
            tx(5_678L, TransactionType.EXPENSE, "TRANSPORT"),
            tx(999L, TransactionType.EXPENSE, "OTHER")
        )
        val points = CategoryDistributionCalculator.calculate(transactions)
        assertEquals(transactions.sumOf { it.amountInCents }, points.sumOf { it.amountCents })
    }

    @Test
    fun deterministicOrdering_highestAmountFirstTiesBrokenByName() {
        val points = CategoryDistributionCalculator.calculate(
            listOf(
                tx(1_000L, TransactionType.EXPENSE, "B_CAT"),
                tx(1_000L, TransactionType.EXPENSE, "A_CAT"),
                tx(2_000L, TransactionType.EXPENSE, "TOP")
            )
        )
        assertEquals(listOf("TOP", "A_CAT", "B_CAT"), points.map { it.categoryName })
    }

    @Test
    fun percentageCalculation_isCorrectAndSafe() {
        val points = CategoryDistributionCalculator.calculate(
            listOf(tx(2_500L, TransactionType.EXPENSE, "FOOD"), tx(7_500L, TransactionType.EXPENSE, "RENT"))
        )
        val food = points.first { it.categoryName == "FOOD" }
        assertEquals(25f, food.percentage, 0.01f)
    }

    @Test
    fun largeValues_handledWithoutOverflowOrPrecisionLoss() {
        val large = 999_999_999_999L
        val points = CategoryDistributionCalculator.calculate(listOf(tx(large, TransactionType.EXPENSE, "FOOD")))
        assertEquals(large, points.single().amountCents)
        assertEquals(100f, points.single().percentage, 0.01f)
    }
}

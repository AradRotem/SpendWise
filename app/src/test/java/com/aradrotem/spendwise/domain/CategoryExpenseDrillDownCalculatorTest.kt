package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryExpenseDrillDownCalculatorTest {

    private fun expenseTx(id: Long, amountCents: Long, timestamp: Long, category: String = "FOOD", note: String = "", sourceTitle: String? = null) =
        TransactionEntity(id = id, amountInCents = amountCents, type = TransactionType.EXPENSE, category = category, timestamp = timestamp, note = note, sourceTitle = sourceTitle)

    private fun incomeTx(id: Long, amountCents: Long) =
        TransactionEntity(id = id, amountInCents = amountCents, type = TransactionType.INCOME, category = "SALARY", timestamp = 1_000L)

    @Test
    fun oneMatchingTransaction_isDisplayed() {
        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", listOf(expenseTx(1, 1_000L, 100L)))

        assertEquals(1, details.displayedTransactions.size)
        assertEquals(1_000L, details.totalAmountCents)
        assertEquals(1, details.transactionCount)
        assertFalse(details.isLimited)
    }

    @Test
    fun exactlyFiveMatching_showsAllFive() {
        val transactions = (1..5).map { expenseTx(it.toLong(), it * 1_000L, it * 100L) }

        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)

        assertEquals(5, details.displayedTransactions.size)
        assertFalse(details.isLimited)
        assertEquals(0, details.hiddenTransactionCount)
    }

    @Test
    fun moreThanFiveMatching_showsOnlyFive() {
        val transactions = (1..12).map { expenseTx(it.toLong(), it * 1_000L, it * 100L) }

        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)

        assertEquals(5, details.displayedTransactions.size)
        assertTrue(details.isLimited)
        assertEquals(7, details.hiddenTransactionCount)
        assertEquals(12, details.transactionCount)
    }

    @Test
    fun theFiveLargest_areSelected_notTheNewestFive() {
        // Newest 5 by timestamp would be ids 8-12 (amounts 100-500), but the 5 largest by amount
        // are ids 12,11,10,9,8 only if amounts correlate with recency - here we deliberately
        // reverse the correlation: the single largest transaction is also the OLDEST.
        val transactions = listOf(
            expenseTx(1, 50_000L, timestamp = 1L), // largest amount, oldest
            expenseTx(2, 1_000L, timestamp = 10L),
            expenseTx(3, 1_000L, timestamp = 9L),
            expenseTx(4, 1_000L, timestamp = 8L),
            expenseTx(5, 1_000L, timestamp = 7L),
            expenseTx(6, 1_000L, timestamp = 6L),
            expenseTx(7, 100L, timestamp = 20L) // newest, but smallest amount
        )

        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)

        assertEquals(5, details.displayedTransactions.size)
        assertTrue(details.displayedTransactions.any { it.transactionId == 1L })
        assertFalse(details.displayedTransactions.any { it.transactionId == 7L }) // smallest, excluded despite being newest
    }

    @Test
    fun sortOrder_amountDescending() {
        val transactions = listOf(expenseTx(1, 1_000L, 100L), expenseTx(2, 5_000L, 200L), expenseTx(3, 3_000L, 300L))

        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)

        assertEquals(listOf(5_000L, 3_000L, 1_000L), details.displayedTransactions.map { it.amountCents })
    }

    @Test
    fun dateTieBreaker_appliedWhenAmountsEqual() {
        val transactions = listOf(
            expenseTx(1, 1_000L, timestamp = 100L),
            expenseTx(2, 1_000L, timestamp = 300L),
            expenseTx(3, 1_000L, timestamp = 200L)
        )

        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)

        assertEquals(listOf(2L, 3L, 1L), details.displayedTransactions.map { it.transactionId })
    }

    @Test
    fun idTieBreaker_appliedWhenAmountAndDateEqual() {
        val transactions = listOf(
            expenseTx(1, 1_000L, timestamp = 100L),
            expenseTx(5, 1_000L, timestamp = 100L),
            expenseTx(3, 1_000L, timestamp = 100L)
        )

        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)

        assertEquals(listOf(5L, 3L, 1L), details.displayedTransactions.map { it.transactionId })
    }

    @Test
    fun totalRemainsCorrect_whenOnlyFiveDisplayed() {
        val transactions = (1..10).map { expenseTx(it.toLong(), 1_000L, it * 100L) }

        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)

        assertEquals(10_000L, details.totalAmountCents)
    }

    @Test
    fun transactionCount_reflectsAllMatchingNotJustDisplayed() {
        val transactions = (1..8).map { expenseTx(it.toLong(), 1_000L, it * 100L) }

        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)

        assertEquals(8, details.transactionCount)
        assertEquals(5, details.displayedTransactions.size)
    }

    @Test
    fun incomeTransactions_excluded() {
        val transactions = listOf(expenseTx(1, 1_000L, 100L, category = "SALARY"), incomeTx(2, 5_000L))

        val details = CategoryExpenseDrillDownCalculator.calculate("SALARY", "Salary", "July 2026", transactions)

        assertEquals(1, details.transactionCount)
        assertEquals(1_000L, details.totalAmountCents)
    }

    @Test
    fun otherCategories_excluded() {
        val transactions = listOf(expenseTx(1, 1_000L, 100L, category = "FOOD"), expenseTx(2, 2_000L, 200L, category = "TRANSPORT"))

        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)

        assertEquals(1, details.transactionCount)
    }

    @Test
    fun customCategory_supported() {
        val transactions = listOf(expenseTx(1, 1_000L, 100L, category = "Book Club"))

        val details = CategoryExpenseDrillDownCalculator.calculate("Book Club", "Book Club", "July 2026", transactions)

        assertEquals(1, details.transactionCount)
        assertEquals(1_000L, details.totalAmountCents)
    }

    @Test
    fun otherCategory_supported() {
        val transactions = listOf(expenseTx(1, 1_000L, 100L, category = "OTHER"))

        val details = CategoryExpenseDrillDownCalculator.calculate("OTHER", "Other", "July 2026", transactions)

        assertEquals(1, details.transactionCount)
    }

    @Test
    fun emptyResult_whenNoMatchingTransactions() {
        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", emptyList())

        assertEquals(0, details.transactionCount)
        assertEquals(0L, details.totalAmountCents)
        assertTrue(details.displayedTransactions.isEmpty())
        assertFalse(details.isLimited)
    }

    @Test
    fun largeCentValues_handledWithoutOverflow() {
        val large = 999_999_999_999L
        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", listOf(expenseTx(1, large, 100L)))

        assertEquals(large, details.totalAmountCents)
        assertEquals(large, details.displayedTransactions.single().amountCents)
    }

    @Test
    fun deterministicOutput_sameInputProducesSameResult() {
        val transactions = (1..10).map { expenseTx(it.toLong(), it * 137L, it * 913L) }

        val first = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)
        val second = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", transactions)

        assertEquals(first, second)
    }

    @Test
    fun titleFallback_usesSourceTitleThenNoteThenUntitled() {
        val withSourceTitle = expenseTx(1, 1_000L, 100L, sourceTitle = "Rent")
        val withNoteOnly = expenseTx(2, 1_000L, 200L, note = "Groceries")
        val withNeither = expenseTx(3, 1_000L, 300L)

        val details = CategoryExpenseDrillDownCalculator.calculate("FOOD", "Food", "July 2026", listOf(withSourceTitle, withNoteOnly, withNeither))

        val byId = details.displayedTransactions.associateBy { it.transactionId }
        assertEquals("Rent", byId[1L]!!.title)
        assertEquals("Groceries", byId[2L]!!.title)
        assertEquals("Untitled expense", byId[3L]!!.title)
    }
}

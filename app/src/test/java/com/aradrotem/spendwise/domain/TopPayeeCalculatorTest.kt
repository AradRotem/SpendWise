package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopPayeeCalculatorTest {

    private fun tx(amountCents: Long, note: String, type: TransactionType = TransactionType.EXPENSE) =
        TransactionEntity(amountInCents = amountCents, type = type, category = "FOOD", timestamp = 0L, note = note)

    @Test
    fun groupsByNote_caseAndWhitespaceInsensitive() {
        val transactions = listOf(tx(500L, "Netflix"), tx(300L, "netflix "), tx(200L, " NETFLIX"))
        val result = TopPayeeCalculator.calculate(transactions)
        assertEquals(1, result.size)
        assertEquals(1_000L, result.single().totalCents)
        assertEquals(3, result.single().transactionCount)
        assertEquals("Netflix", result.single().name)
    }

    @Test
    fun blankNotes_excluded() {
        val transactions = listOf(tx(500L, ""), tx(300L, "   "), tx(200L, "Wolt"))
        val result = TopPayeeCalculator.calculate(transactions)
        assertEquals(1, result.size)
        assertEquals("Wolt", result.single().name)
    }

    @Test
    fun rankedDescendingByTotal_andLimited() {
        val transactions = listOf(tx(100L, "A"), tx(500L, "B"), tx(300L, "C"), tx(900L, "D"), tx(200L, "E"), tx(50L, "F"))
        val result = TopPayeeCalculator.calculate(transactions, limit = 3)
        assertEquals(listOf("D", "B", "C"), result.map { it.name })
    }

    @Test
    fun incomeTransactions_excluded() {
        val transactions = listOf(tx(5_000L, "Employer", type = TransactionType.INCOME))
        assertTrue(TopPayeeCalculator.calculate(transactions).isEmpty())
    }

    @Test
    fun emptyTransactions_emptyResult() {
        assertTrue(TopPayeeCalculator.calculate(emptyList()).isEmpty())
    }
}

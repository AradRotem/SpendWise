package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CumulativeSpendingCalculatorTest {

    private val zoneId = ZoneId.of("UTC")

    private fun tx(amountCents: Long, day: Int, type: TransactionType = TransactionType.EXPENSE) =
        TransactionEntity(
            amountInCents = amountCents, type = type, category = "FOOD",
            timestamp = YearMonth.of(2026, 1).atDay(day).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )

    @Test
    fun runningTotal_accumulatesAcrossDays() {
        val transactions = listOf(tx(1_000L, 1), tx(2_000L, 5), tx(500L, 5), tx(1_500L, 10))
        val points = CumulativeSpendingCalculator.calculate(YearMonth.of(2026, 1), transactions, zoneId, dayLimit = 10)
        assertEquals(1_000L, points[0].cumulativeExpenseCents)
        assertEquals(3_500L, points[4].cumulativeExpenseCents)
        assertEquals(5_000L, points[9].cumulativeExpenseCents)
    }

    @Test
    fun dayWithNoSpending_carriesPreviousTotalForward() {
        val transactions = listOf(tx(1_000L, 1))
        val points = CumulativeSpendingCalculator.calculate(YearMonth.of(2026, 1), transactions, zoneId, dayLimit = 5)
        assertEquals(0L, points[2].dayExpenseCents)
        assertEquals(1_000L, points[2].cumulativeExpenseCents)
    }

    @Test
    fun incomeTransactions_excludedFromCumulativeSpending() {
        val transactions = listOf(tx(5_000L, 1, type = TransactionType.INCOME), tx(1_000L, 1))
        val points = CumulativeSpendingCalculator.calculate(YearMonth.of(2026, 1), transactions, zoneId, dayLimit = 5)
        assertEquals(1_000L, points[0].cumulativeExpenseCents)
    }

    @Test
    fun transactionsOutsideMonth_ignored() {
        val outsideMonth = TransactionEntity(
            amountInCents = 9_000L, type = TransactionType.EXPENSE, category = "FOOD",
            timestamp = YearMonth.of(2026, 2).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
        val points = CumulativeSpendingCalculator.calculate(YearMonth.of(2026, 1), listOf(outsideMonth), zoneId, dayLimit = 5)
        assertEquals(0L, points.last().cumulativeExpenseCents)
    }

    @Test
    fun emptyTransactions_allZero() {
        val points = CumulativeSpendingCalculator.calculate(YearMonth.of(2026, 1), emptyList(), zoneId, dayLimit = 3)
        assertEquals(listOf(0L, 0L, 0L), points.map { it.cumulativeExpenseCents })
    }
}

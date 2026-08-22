package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekdaySpendingCalculatorTest {

    private val zoneId = ZoneId.of("UTC")

    // 2026-01-04 is a Sunday.
    private fun tx(amountCents: Long, date: LocalDate, type: TransactionType = TransactionType.EXPENSE) =
        TransactionEntity(
            amountInCents = amountCents, type = type, category = "FOOD",
            timestamp = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        )

    @Test
    fun allSevenDaysReturned_sundayFirst() {
        val points = WeekdaySpendingCalculator.calculate(emptyList(), zoneId)
        assertEquals(
            listOf(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
            ),
            points.map { it.dayOfWeek }
        )
    }

    @Test
    fun expensesGroupedByWeekday() {
        val transactions = listOf(
            tx(1_000L, LocalDate.of(2026, 1, 4)), // Sunday
            tx(500L, LocalDate.of(2026, 1, 11)),  // Sunday
            tx(2_000L, LocalDate.of(2026, 1, 6))  // Tuesday
        )
        val points = WeekdaySpendingCalculator.calculate(transactions, zoneId)
        val byDay = points.associateBy { it.dayOfWeek }
        assertEquals(1_500L, byDay[DayOfWeek.SUNDAY]!!.expenseCents)
        assertEquals(2_000L, byDay[DayOfWeek.TUESDAY]!!.expenseCents)
        assertEquals(0L, byDay[DayOfWeek.MONDAY]!!.expenseCents)
    }

    @Test
    fun incomeTransactions_excluded() {
        val transactions = listOf(tx(5_000L, LocalDate.of(2026, 1, 4), type = TransactionType.INCOME))
        val points = WeekdaySpendingCalculator.calculate(transactions, zoneId)
        assertEquals(0L, points.sumOf { it.expenseCents })
    }

    @Test
    fun highestSpendingDay_identifiedCorrectly() {
        val transactions = listOf(tx(1_000L, LocalDate.of(2026, 1, 4)), tx(3_000L, LocalDate.of(2026, 1, 6)))
        val points = WeekdaySpendingCalculator.calculate(transactions, zoneId)
        assertEquals(DayOfWeek.TUESDAY, WeekdaySpendingCalculator.highestSpendingDay(points)!!.dayOfWeek)
    }

    @Test
    fun highestSpendingDay_nullWhenNoSpending() {
        assertNull(WeekdaySpendingCalculator.highestSpendingDay(WeekdaySpendingCalculator.calculate(emptyList(), zoneId)))
    }
}

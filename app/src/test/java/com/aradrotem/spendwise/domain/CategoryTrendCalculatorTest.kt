package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryTrendCalculatorTest {

    private val zoneId = ZoneId.of("UTC")

    private fun tx(amountCents: Long, category: String, year: Int, month: Int, type: TransactionType = TransactionType.EXPENSE) =
        TransactionEntity(
            amountInCents = amountCents, type = type, category = category,
            timestamp = YearMonth.of(year, month).atDay(10).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )

    @Test
    fun selectedCategory_acrossSeveralMonths_oneValuePerMonth() {
        val months = listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3))
        val transactions = listOf(tx(1_000L, "FOOD", 2026, 1), tx(2_000L, "FOOD", 2026, 2), tx(3_000L, "FOOD", 2026, 3))
        val trend = CategoryTrendCalculator.calculate("FOOD", months, transactions, zoneId)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), trend.map { it.amountCents })
    }

    @Test
    fun monthsWithZeroCategorySpending_showZero() {
        val months = listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2))
        val transactions = listOf(tx(1_000L, "FOOD", 2026, 1))
        val trend = CategoryTrendCalculator.calculate("FOOD", months, transactions, zoneId)
        assertEquals(listOf(1_000L, 0L), trend.map { it.amountCents })
    }

    @Test
    fun categoryAbsentFromPeriod_allZero() {
        val months = listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2))
        val transactions = listOf(tx(1_000L, "TRANSPORT", 2026, 1))
        val trend = CategoryTrendCalculator.calculate("FOOD", months, transactions, zoneId)
        assertEquals(listOf(0L, 0L), trend.map { it.amountCents })
    }

    @Test
    fun customCategoryName_behavesLikeAnyOtherCategory() {
        val months = listOf(YearMonth.of(2026, 1))
        val transactions = listOf(tx(500L, "Custom Hobby", 2026, 1))
        val trend = CategoryTrendCalculator.calculate("Custom Hobby", months, transactions, zoneId)
        assertEquals(500L, trend.single().amountCents)
    }

    @Test
    fun incomeCategory_excludedEvenIfNameMatches() {
        val months = listOf(YearMonth.of(2026, 1))
        val transactions = listOf(tx(5_000L, "SALARY", 2026, 1, type = TransactionType.INCOME))
        val trend = CategoryTrendCalculator.calculate("SALARY", months, transactions, zoneId)
        assertEquals(0L, trend.single().amountCents)
    }

    @Test
    fun totalAndAverage_computedCorrectly() {
        val months = listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2))
        val transactions = listOf(tx(1_000L, "FOOD", 2026, 1), tx(3_000L, "FOOD", 2026, 2))
        val trend = CategoryTrendCalculator.calculate("FOOD", months, transactions, zoneId)
        assertEquals(4_000L, CategoryTrendCalculator.total(trend))
        assertEquals(2_000L, CategoryTrendCalculator.average(trend))
    }

    @Test
    fun highest_identifiedCorrectly() {
        val months = listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2))
        val transactions = listOf(tx(1_000L, "FOOD", 2026, 1), tx(3_000L, "FOOD", 2026, 2))
        val trend = CategoryTrendCalculator.calculate("FOOD", months, transactions, zoneId)
        assertEquals(YearMonth.of(2026, 2), CategoryTrendCalculator.highest(trend)!!.yearMonth)
    }

    @Test
    fun changeFromPrevious_nullWithFewerThanTwoPoints() {
        val months = listOf(YearMonth.of(2026, 1))
        val trend = CategoryTrendCalculator.calculate("FOOD", months, emptyList())
        assertNull(CategoryTrendCalculator.changeFromPrevious(trend))
    }

    @Test
    fun changeFromPrevious_computedFromLastTwoPoints() {
        val months = listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2))
        val transactions = listOf(tx(1_000L, "FOOD", 2026, 1), tx(3_000L, "FOOD", 2026, 2))
        val trend = CategoryTrendCalculator.calculate("FOOD", months, transactions, zoneId)
        assertEquals(2_000L, CategoryTrendCalculator.changeFromPrevious(trend))
    }
}

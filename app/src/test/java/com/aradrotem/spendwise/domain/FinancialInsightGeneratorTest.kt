package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatPercent
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialInsightGeneratorTest {

    private fun monthlyPoint(month: Int, income: Long, expense: Long) =
        MonthlyIncomeExpensePoint(YearMonth.of(2026, month), income, expense)

    private fun generate(
        categoryPoints: List<CategorySpendingPoint> = emptyList(),
        monthlySpendingPoints: List<MonthlySpendingPoint> = emptyList(),
        monthlyIncomeExpensePoints: List<MonthlyIncomeExpensePoint> = emptyList(),
        budgetActualPoints: List<BudgetActualPoint> = emptyList(),
        categoryTrendPoints: List<CategoryTrendPoint> = emptyList(),
        selectedCategoryName: String? = null
    ) = FinancialInsightGenerator.generate(
        categoryPoints,
        monthlySpendingPoints,
        monthlyIncomeExpensePoints,
        budgetActualPoints,
        categoryTrendPoints,
        selectedCategoryName,
        formatAmountCents = ::formatAmountInCents,
        formatPercent = ::formatPercent
    )

    @Test
    fun highestCategoryInsight_namesTheTopCategory() {
        val categories = listOf(
            CategorySpendingPoint("FOOD", 5_000L, 3, 50f),
            CategorySpendingPoint("TRANSPORT", 2_000L, 1, 20f)
        )
        val insights = generate(categories, emptyList(), emptyList(), emptyList(), emptyList(), null)
        assertTrue(insights.any { it.text.contains("Food") && it.text.contains("highest spending category") })
    }

    @Test
    fun monthOverMonthIncrease_reportsPositivePercent() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(monthlyPoint(1, 0, 10_000L), monthlyPoint(2, 0, 12_500L)))
        val insights = generate(emptyList(), trend, emptyList(), emptyList(), emptyList(), null)
        assertTrue(insights.any { it.text.contains("increased") && it.text.contains("25.0%") })
    }

    @Test
    fun monthOverMonthDecrease_reportsNegativeAsDecrease() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(monthlyPoint(1, 0, 10_000L), monthlyPoint(2, 0, 7_500L)))
        val insights = generate(emptyList(), trend, emptyList(), emptyList(), emptyList(), null)
        assertTrue(insights.any { it.text.contains("decreased") && it.text.contains("25.0%") })
        assertFalse(insights.any { it.text.contains("-25.0%") })
    }

    @Test
    fun budgetExceeded_reportsOverAmount() {
        val budgets = listOf(BudgetActualCalculator.calculate("SHOPPING", 10_000L, 22_000L))
        val insights = generate(emptyList(), emptyList(), emptyList(), budgets, emptyList(), null)
        assertTrue(insights.any { it.text.contains("Shopping") && it.text.contains("exceeded") && it.text.contains("120.00") })
    }

    @Test
    fun incomeGreaterThanExpenses_acrossMultipleMonths() {
        val months = listOf(
            monthlyPoint(1, 10_000L, 5_000L),
            monthlyPoint(2, 10_000L, 12_000L),
            monthlyPoint(3, 10_000L, 5_000L),
            monthlyPoint(4, 10_000L, 4_000L)
        )
        val insights = generate(emptyList(), emptyList(), months, emptyList(), emptyList(), null)
        assertTrue(insights.any { it.text.contains("higher") && it.text.contains("3 of the last 4 months") })
    }

    @Test
    fun insufficientData_showsAddMoreTransactionsMessage() {
        val insights = generate(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null)
        assertEquals(1, insights.size)
        assertEquals("Add more transactions over time to receive meaningful insights.", insights.single().text)
    }

    @Test
    fun maximumInsightCount_neverExceedsFive() {
        val categories = listOf(CategorySpendingPoint("FOOD", 5_000L, 3, 100f))
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(monthlyPoint(1, 0, 10_000L), monthlyPoint(2, 0, 12_500L)))
        val budgets = listOf(
            BudgetActualCalculator.calculate("FOOD", 1_000L, 2_000L),
            BudgetActualCalculator.calculate("TRANSPORT", 1_000L, 2_000L)
        )
        val categoryTrend = listOf(CategoryTrendPoint(YearMonth.of(2026, 1), 3_000L), CategoryTrendPoint(YearMonth.of(2026, 2), 2_000L), CategoryTrendPoint(YearMonth.of(2026, 3), 1_000L))
        val months = listOf(monthlyPoint(1, 10_000L, 5_000L), monthlyPoint(2, 10_000L, 5_000L))

        val insights = generate(categories, trend, months, budgets, categoryTrend, "FOOD")

        assertTrue(insights.size <= FinancialInsightGenerator.MAX_INSIGHTS)
    }

    @Test
    fun stablePriorityOrdering_budgetExceededComesFirst() {
        val categories = listOf(CategorySpendingPoint("FOOD", 5_000L, 3, 100f))
        val budgets = listOf(BudgetActualCalculator.calculate("FOOD", 1_000L, 2_000L))
        val insights = generate(categories, emptyList(), emptyList(), budgets, emptyList(), null)
        assertTrue(insights.first().text.contains("exceeded"))
    }

    @Test
    fun noContradictoryInsights_neverBothIncreaseAndDecrease() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(monthlyPoint(1, 0, 10_000L), monthlyPoint(2, 0, 12_500L)))
        val insights = generate(emptyList(), trend, emptyList(), emptyList(), emptyList(), null)
        val hasIncrease = insights.any { it.text.contains("increased") }
        val hasDecrease = insights.any { it.text.contains("decreased") }
        assertFalse(hasIncrease && hasDecrease)
    }

    @Test
    fun noUnsupportedCausalLanguage_insightsStateFactsOnly() {
        val categories = listOf(CategorySpendingPoint("FOOD", 5_000L, 3, 100f))
        val insights = generate(categories, emptyList(), emptyList(), emptyList(), emptyList(), null)
        insights.forEach { insight ->
            assertFalse(insight.text.contains("because"))
            assertFalse(insight.text.contains("should"))
            assertFalse(insight.text.contains("recommend"))
        }
    }

    @Test
    fun previousMonthZero_showsSpendingStartedNotPercentage() {
        val trend = MonthlySpendingTrendCalculator.calculate(listOf(monthlyPoint(1, 0, 0L), monthlyPoint(2, 0, 5_000L)))
        val insights = generate(emptyList(), trend, emptyList(), emptyList(), emptyList(), null)
        assertTrue(insights.any { it.text == "Spending started this month." })
        assertFalse(insights.any { it.text.contains("%") })
    }
}

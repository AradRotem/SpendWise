package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.domain.AnalyticsTimeRange
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Unit tests for the unified monthly-mode builder (buildMonthlyModeUiState) and the multi-month
// share-text builder (buildPeriodShareText) added when Monthly Report and Visual Analytics were
// merged into ReportsAnalyticsScreen. buildMonthlyReportUiState/buildVisualAnalyticsUiState
// themselves are unchanged and already covered by MonthlyReportLogicTest/VisualAnalyticsLogicTest.
class ReportsAnalyticsLogicTest {

    private val month = YearMonth.of(2026, 7)
    private val previousMonth = YearMonth.of(2026, 6)

    private fun expenseTx(amountInCents: Long, category: String) =
        TransactionEntity(amountInCents = amountInCents, type = TransactionType.EXPENSE, category = category, timestamp = 1_000L)

    @Test
    fun monthlyMode_summaryMatchesUnderlyingMonthlyReportValues() {
        val amounts = ReportMonthlyAmounts(incomeCents = 600_000L, expenseCents = 325_000L, previousIncomeCents = 500_000L, previousExpenseCents = 300_000L)
        val details = MonthlyDetails(
            categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 325_000L)),
            budgets = emptyList(),
            transactionsInMonth = listOf(expenseTx(325_000L, "FOOD"))
        )

        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, amounts, details, selectedDrillDownCategory = null)

        assertEquals(month, state.selectedMonth)
        assertTrue(state.isSingleMonth)
        assertEquals(600_000L, state.monthly!!.incomeCents)
        assertEquals(325_000L, state.monthly!!.expenseCents)
        assertEquals(500_000L, state.monthly!!.previousMonthIncomeCents)
        assertNull(state.period)
    }

    @Test
    fun monthlyMode_topCategoryMatchesCategoryBreakdown() {
        val amounts = ReportMonthlyAmounts(0L, 8_000L, 0L, 0L)
        val details = MonthlyDetails(
            categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 5_000L), CategoryMonthlyTotal("TRANSPORT", 3_000L)),
            budgets = emptyList(),
            transactionsInMonth = listOf(expenseTx(5_000L, "FOOD"), expenseTx(3_000L, "TRANSPORT"))
        )

        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, amounts, details, selectedDrillDownCategory = null)

        assertEquals("FOOD", state.monthly!!.topCategory?.categoryName)
    }

    @Test
    fun monthlyMode_budgetComparisonIsVisible() {
        val amounts = ReportMonthlyAmounts(0L, 12_000L, 0L, 0L)
        val details = MonthlyDetails(
            categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 12_000L)),
            budgets = listOf(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 10_000L)),
            transactionsInMonth = listOf(expenseTx(12_000L, "FOOD"))
        )

        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, amounts, details, selectedDrillDownCategory = null)

        assertEquals(1, state.monthly!!.budgets.size)
        assertTrue(state.monthly!!.budgets.single().isExceeded)
    }

    @Test
    fun monthlyMode_transactionCountsAreCorrect() {
        val amounts = ReportMonthlyAmounts(0L, 0L, 0L, 0L)
        val details = MonthlyDetails(
            categoryTotals = emptyList(),
            budgets = emptyList(),
            transactionsInMonth = listOf(expenseTx(1_000L, "FOOD"), expenseTx(2_000L, "FOOD"))
        )

        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, amounts, details, selectedDrillDownCategory = null)

        assertEquals(2, state.monthly!!.expenseTransactionCount)
        assertEquals(0, state.monthly!!.incomeTransactionCount)
    }

    @Test
    fun monthlyMode_shareTextContainsMonthlyData() {
        val amounts = ReportMonthlyAmounts(600_000L, 325_000L, 500_000L, 300_000L)
        val details = MonthlyDetails(
            categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 325_000L)),
            budgets = emptyList(),
            transactionsInMonth = listOf(expenseTx(325_000L, "FOOD"))
        )
        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, amounts, details, selectedDrillDownCategory = null)

        val shareText = buildMonthlyReportShareText(state.monthly!!)

        assertTrue(shareText.contains("July 2026"))
        assertTrue(shareText.contains("6000.00"))
        assertTrue(shareText.contains("Food"))
    }

    @Test
    fun monthlyMode_producesInsightsFromTwoPointHistory() {
        val amounts = ReportMonthlyAmounts(incomeCents = 0L, expenseCents = 15_000L, previousIncomeCents = 0L, previousExpenseCents = 10_000L)
        val details = MonthlyDetails(
            categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 15_000L)),
            budgets = emptyList(),
            transactionsInMonth = listOf(expenseTx(15_000L, "FOOD"))
        )

        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, amounts, details, selectedDrillDownCategory = null)

        assertTrue(state.monthlyInsights.any { it.text.contains("increased") && it.text.contains("50.0%") })
    }

    @Test
    fun periodShareText_containsPeriodSummaryData() {
        val period = buildVisualAnalyticsUiState(
            selectedMonth = month,
            timeRange = AnalyticsTimeRange.LAST_3_MONTHS,
            months = listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), month),
            transactions = listOf(expenseTx(9_000L, "FOOD")).map { it.copy(timestamp = 1_000L) },
            expenseCategories = emptyList(),
            budgets = emptyList(),
            selectedTrendCategoryInput = null
        )

        val shareText = buildPeriodShareText(period)

        assertTrue(shareText.contains("July 2026"))
        assertTrue(shareText.contains("90.00"))
    }
}

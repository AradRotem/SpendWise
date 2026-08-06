package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.domain.AnalyticsTimeRange
import com.aradrotem.spendwise.domain.monthsForAnalyticsRange
import com.aradrotem.spendwise.util.combinedRange
import com.aradrotem.spendwise.util.monthRange
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualAnalyticsLogicTest {

    private val zoneId = ZoneId.of("UTC")

    private fun tx(amountCents: Long, type: TransactionType, category: String, year: Int, month: Int, day: Int = 10) =
        TransactionEntity(
            amountInCents = amountCents, type = type, category = category,
            timestamp = YearMonth.of(year, month).atDay(day).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )

    private fun buildState(
        selectedMonth: YearMonth,
        timeRange: AnalyticsTimeRange,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity> = listOf(CategoryEntity(name = "FOOD", normalizedName = "food", type = TransactionType.EXPENSE)),
        budgets: List<BudgetEntity> = emptyList(),
        selectedCategory: String? = null
    ): VisualAnalyticsUiState {
        val months = monthsForAnalyticsRange(selectedMonth, timeRange)
        // Mirrors VisualAnalyticsViewModel: only transactions within the combined range are ever
        // fetched (via observeInRange), so the pure builder assumes its input is already scoped
        // to that range - this test helper applies the same filtering before calling it.
        val combined = combinedRange(months.map { monthRange(it, zoneId) })
        val scopedTransactions = transactions.filter { it.timestamp >= combined.startMillis && it.timestamp < combined.endExclusiveMillis }
        return buildVisualAnalyticsUiState(selectedMonth, timeRange, months, scopedTransactions, categories, budgets, selectedCategory, zoneId)
    }

    @Test
    fun switchingSelectedMonth_recomputesTotalsForThatMonthOnly() {
        val transactions = listOf(tx(1_000L, TransactionType.EXPENSE, "FOOD", 2026, 1), tx(2_000L, TransactionType.EXPENSE, "FOOD", 2026, 2))
        val january = buildState(YearMonth.of(2026, 1), AnalyticsTimeRange.SELECTED_MONTH, transactions)
        val february = buildState(YearMonth.of(2026, 2), AnalyticsTimeRange.SELECTED_MONTH, transactions)
        assertEquals(1_000L, january.totalExpenseCents)
        assertEquals(2_000L, february.totalExpenseCents)
    }

    @Test
    fun switchingTimeRange_changesMonthsIncludedAndTotals() {
        val transactions = listOf(tx(1_000L, TransactionType.EXPENSE, "FOOD", 2026, 1), tx(2_000L, TransactionType.EXPENSE, "FOOD", 2026, 3))
        val singleMonth = buildState(YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH, transactions)
        val threeMonths = buildState(YearMonth.of(2026, 3), AnalyticsTimeRange.LAST_3_MONTHS, transactions)
        assertEquals(1, singleMonth.months.size)
        assertEquals(3, threeMonths.months.size)
        assertEquals(2_000L, singleMonth.totalExpenseCents)
        assertEquals(3_000L, threeMonths.totalExpenseCents)
    }

    @Test
    fun emptyState_noTransactionsProducesZeroedButValidState() {
        val state = buildState(YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH, emptyList())
        assertFalse(state.hasAnyTransactions)
        assertEquals(0L, state.totalIncomeCents)
        assertEquals(0L, state.totalExpenseCents)
        assertTrue(state.categoryDistribution.isEmpty())
        assertNull(state.highestSpendingCategory)
    }

    @Test
    fun reactiveTransactionUpdate_reflectedWhenRebuiltWithNewList() {
        val before = buildState(YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH, emptyList())
        val after = buildState(YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH, listOf(tx(1_000L, TransactionType.EXPENSE, "FOOD", 2026, 3)))
        assertEquals(0L, before.totalExpenseCents)
        assertEquals(1_000L, after.totalExpenseCents)
    }

    @Test
    fun reactiveBudgetUpdate_reflectedWhenRebuiltWithNewBudgets() {
        val transactions = listOf(tx(5_000L, TransactionType.EXPENSE, "FOOD", 2026, 3))
        val withoutBudget = buildState(YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH, transactions)
        val withBudget = buildState(
            YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH, transactions,
            budgets = listOf(BudgetEntity(categoryName = "FOOD", monthlyLimitCents = 10_000L))
        )
        assertTrue(withoutBudget.budgetActuals.isEmpty())
        assertEquals(1, withBudget.budgetActuals.size)
        assertEquals(5_000L, withBudget.budgetActuals.single().actualCents)
    }

    @Test
    fun categorySelection_prefersExplicitlySelectedCategoryWhenAvailable() {
        val categories = listOf(
            CategoryEntity(name = "FOOD", normalizedName = "food", type = TransactionType.EXPENSE),
            CategoryEntity(name = "TRANSPORT", normalizedName = "transport", type = TransactionType.EXPENSE)
        )
        val transactions = listOf(tx(1_000L, TransactionType.EXPENSE, "FOOD", 2026, 3), tx(500L, TransactionType.EXPENSE, "TRANSPORT", 2026, 3))
        val state = buildState(YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH, transactions, categories, selectedCategory = "TRANSPORT")
        assertEquals("TRANSPORT", state.selectedTrendCategory)
    }

    @Test
    fun categorySelection_fallsBackToFirstAvailableWhenNoneSelected() {
        val categories = listOf(CategoryEntity(name = "TRANSPORT", normalizedName = "transport", type = TransactionType.EXPENSE))
        val state = buildState(YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH, emptyList(), categories, selectedCategory = null)
        assertEquals("TRANSPORT", state.selectedTrendCategory)
    }

    @Test
    fun correctChartDataPassedToUiState_categoryDistributionSumsToTotal() {
        val transactions = listOf(
            tx(1_000L, TransactionType.EXPENSE, "FOOD", 2026, 3),
            tx(2_000L, TransactionType.EXPENSE, "TRANSPORT", 2026, 3)
        )
        val state = buildState(YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH, transactions)
        assertEquals(state.totalExpenseCents, state.categoryDistribution.sumOf { it.amountCents })
    }

    @Test
    fun futureMonth_producesValidEmptyStateWithoutError() {
        val futureMonth = YearMonth.now(zoneId).plusYears(1)
        val state = buildState(futureMonth, AnalyticsTimeRange.SELECTED_MONTH, emptyList())
        assertEquals(0L, state.totalExpenseCents)
        assertFalse(state.hasAnyTransactions)
    }

    @Test
    fun averageMonthlyExpense_forSingleMonth_equalsThatMonthsTotal() {
        val transactions = listOf(tx(4_000L, TransactionType.EXPENSE, "FOOD", 2026, 3))
        val state = buildState(YearMonth.of(2026, 3), AnalyticsTimeRange.SELECTED_MONTH, transactions)
        assertEquals(state.totalExpenseCents, state.averageMonthlyExpenseCents)
    }
}

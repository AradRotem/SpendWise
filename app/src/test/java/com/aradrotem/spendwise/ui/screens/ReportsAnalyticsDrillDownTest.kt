package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.domain.AnalyticsTimeRange
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Unified state tests for the category drill-down (Step 15's final "spending by category" tap
// feature), covering both monthly mode (buildMonthlyModeUiState) and period mode
// (buildPeriodModeUiState) - both call the same shared resolveDrillDown internally.
class ReportsAnalyticsDrillDownTest {

    private val month = YearMonth.of(2026, 7)
    private val previousMonth = YearMonth.of(2026, 6)

    private fun expenseTx(id: Long, amountCents: Long, category: String, timestamp: Long = 100L) =
        TransactionEntity(id = id, amountInCents = amountCents, type = TransactionType.EXPENSE, category = category, timestamp = timestamp)

    // --- Monthly mode ------------------------------------------------------------------------

    @Test
    fun monthlyMode_selectingCategory_producesDetails() {
        val amounts = ReportMonthlyAmounts(0L, 3_000L, 0L, 0L)
        val details = MonthlyDetails(
            categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 3_000L)),
            budgets = emptyList(),
            transactionsInMonth = listOf(expenseTx(1, 3_000L, "FOOD"))
        )

        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, amounts, details, selectedDrillDownCategory = "FOOD")

        assertEquals("FOOD", state.selectedDrillDownCategory)
        assertEquals(3_000L, state.drillDownDetails?.totalAmountCents)
        assertEquals(1, state.drillDownDetails?.transactionCount)
    }

    @Test
    fun monthlyMode_detailsUsesSameMonthAsVisibleAnalytics() {
        val amounts = ReportMonthlyAmounts(0L, 1_000L, 0L, 0L)
        val details = MonthlyDetails(
            categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 1_000L)),
            budgets = emptyList(),
            transactionsInMonth = listOf(expenseTx(1, 1_000L, "FOOD"))
        )

        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, amounts, details, selectedDrillDownCategory = "FOOD")

        assertTrue(state.drillDownDetails!!.periodLabel.contains("2026"))
        assertEquals(month, state.selectedMonth)
    }

    @Test
    fun monthlyMode_addingTransaction_updatesDetails() {
        val amounts = ReportMonthlyAmounts(0L, 1_000L, 0L, 0L)
        val before = MonthlyDetails(listOf(CategoryMonthlyTotal("FOOD", 1_000L)), emptyList(), listOf(expenseTx(1, 1_000L, "FOOD")))
        val stateBefore = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, amounts, before, "FOOD")
        assertEquals(1, stateBefore.drillDownDetails!!.transactionCount)

        val after = MonthlyDetails(
            listOf(CategoryMonthlyTotal("FOOD", 3_500L)), emptyList(),
            listOf(expenseTx(1, 1_000L, "FOOD"), expenseTx(2, 2_500L, "FOOD"))
        )
        val amountsAfter = ReportMonthlyAmounts(0L, 3_500L, 0L, 0L)
        val stateAfter = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, amountsAfter, after, "FOOD")

        assertEquals(2, stateAfter.drillDownDetails!!.transactionCount)
        assertEquals(3_500L, stateAfter.drillDownDetails!!.totalAmountCents)
    }

    @Test
    fun monthlyMode_editingAmount_updatesSortingAndTotal() {
        val details = MonthlyDetails(
            listOf(CategoryMonthlyTotal("FOOD", 6_000L)), emptyList(),
            listOf(expenseTx(1, 1_000L, "FOOD"), expenseTx(2, 5_000L, "FOOD"))
        )
        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, ReportMonthlyAmounts(0L, 6_000L, 0L, 0L), details, "FOOD")

        assertEquals(listOf(2L, 1L), state.drillDownDetails!!.displayedTransactions.map { it.transactionId })

        // Edit transaction 1's amount up to 9000 - it should now sort first, and total updates.
        val editedDetails = MonthlyDetails(
            listOf(CategoryMonthlyTotal("FOOD", 14_000L)), emptyList(),
            listOf(expenseTx(1, 9_000L, "FOOD"), expenseTx(2, 5_000L, "FOOD"))
        )
        val editedState = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, ReportMonthlyAmounts(0L, 14_000L, 0L, 0L), editedDetails, "FOOD")

        assertEquals(listOf(1L, 2L), editedState.drillDownDetails!!.displayedTransactions.map { it.transactionId })
        assertEquals(14_000L, editedState.drillDownDetails!!.totalAmountCents)
    }

    @Test
    fun monthlyMode_deletingTransaction_updatesListAndCount() {
        val details = MonthlyDetails(
            listOf(CategoryMonthlyTotal("FOOD", 3_000L)), emptyList(),
            listOf(expenseTx(1, 1_000L, "FOOD"), expenseTx(2, 2_000L, "FOOD"))
        )
        val stateBefore = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, ReportMonthlyAmounts(0L, 3_000L, 0L, 0L), details, "FOOD")
        assertEquals(2, stateBefore.drillDownDetails!!.transactionCount)

        val afterDelete = MonthlyDetails(listOf(CategoryMonthlyTotal("FOOD", 2_000L)), emptyList(), listOf(expenseTx(2, 2_000L, "FOOD")))
        val stateAfter = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, ReportMonthlyAmounts(0L, 2_000L, 0L, 0L), afterDelete, "FOOD")

        assertEquals(1, stateAfter.drillDownDetails!!.transactionCount)
        assertEquals(2_000L, stateAfter.drillDownDetails!!.totalAmountCents)
    }

    @Test
    fun monthlyMode_movingTransactionToAnotherCategory_updatesBothCategories() {
        val details = MonthlyDetails(
            listOf(CategoryMonthlyTotal("FOOD", 1_000L), CategoryMonthlyTotal("TRANSPORT", 0L)).filter { it.totalCents > 0 },
            emptyList(),
            listOf(expenseTx(1, 1_000L, "FOOD"))
        )
        val foodState = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, ReportMonthlyAmounts(0L, 1_000L, 0L, 0L), details, "FOOD")
        assertEquals(1, foodState.drillDownDetails!!.transactionCount)

        // Same transaction id recategorized to TRANSPORT.
        val movedDetails = MonthlyDetails(
            listOf(CategoryMonthlyTotal("TRANSPORT", 1_000L)), emptyList(), listOf(expenseTx(1, 1_000L, "TRANSPORT"))
        )
        val foodAfterMove = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, ReportMonthlyAmounts(0L, 1_000L, 0L, 0L), movedDetails, "FOOD")
        val transportAfterMove = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, ReportMonthlyAmounts(0L, 1_000L, 0L, 0L), movedDetails, "TRANSPORT")

        // FOOD is no longer in the distribution at all, so selection auto-clears.
        assertNull(foodAfterMove.selectedDrillDownCategory)
        assertEquals(1, transportAfterMove.drillDownDetails!!.transactionCount)
    }

    @Test
    fun monthlyMode_selectionClearsWhenCategoryDisappearsFromPeriod() {
        val details = MonthlyDetails(emptyList(), emptyList(), emptyList())

        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, ReportMonthlyAmounts(0L, 0L, 0L, 0L), details, "FOOD")

        assertNull(state.selectedDrillDownCategory)
        assertNull(state.drillDownDetails)
    }

    // --- Period mode -------------------------------------------------------------------------

    @Test
    fun periodMode_changingRange_recalculatesDetails() {
        val months3 = listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), month)
        val transactions = listOf(expenseTx(1, 1_000L, "FOOD"), expenseTx(2, 2_000L, "FOOD"))

        val state = buildPeriodModeUiState(
            month, AnalyticsTimeRange.LAST_3_MONTHS, months3, transactions,
            expenseCategories = listOf(CategoryEntity(name = "FOOD", normalizedName = "food", type = TransactionType.EXPENSE)),
            budgets = emptyList(), selectedTrendCategory = null, selectedDrillDownCategory = "FOOD"
        )

        assertEquals(3_000L, state.drillDownDetails!!.totalAmountCents)
        assertEquals(2, state.drillDownDetails!!.transactionCount)
    }

    @Test
    fun periodMode_changingSelectedMonth_recalculatesDetails() {
        val transactions = listOf(expenseTx(1, 1_000L, "FOOD"))
        val augState = buildPeriodModeUiState(
            YearMonth.of(2026, 8), AnalyticsTimeRange.SELECTED_MONTH, listOf(YearMonth.of(2026, 8)), transactions,
            expenseCategories = emptyList(), budgets = emptyList(), selectedTrendCategory = null, selectedDrillDownCategory = "FOOD"
        )
        assertTrue(augState.drillDownDetails!!.periodLabel.contains("August"))
    }

    @Test
    fun periodMode_detailsUseSamePeriodAsVisibleAnalytics() {
        val months = listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), month)
        val state = buildPeriodModeUiState(
            month, AnalyticsTimeRange.LAST_3_MONTHS, months, listOf(expenseTx(1, 1_000L, "FOOD")),
            expenseCategories = emptyList(), budgets = emptyList(), selectedTrendCategory = null, selectedDrillDownCategory = "FOOD"
        )
        assertEquals(state.period!!.months, months)
        assertTrue(state.drillDownDetails!!.periodLabel.contains("2026"))
    }

    @Test
    fun periodMode_selectionClearsWhenCategoryDisappearsFromPeriod() {
        val months = listOf(month)
        val state = buildPeriodModeUiState(
            month, AnalyticsTimeRange.SELECTED_MONTH, months, emptyList(),
            expenseCategories = emptyList(), budgets = emptyList(), selectedTrendCategory = null, selectedDrillDownCategory = "FOOD"
        )
        assertNull(state.selectedDrillDownCategory)
        assertNull(state.drillDownDetails)
    }

    @Test
    fun noSelection_producesNullDetails() {
        val details = MonthlyDetails(listOf(CategoryMonthlyTotal("FOOD", 1_000L)), emptyList(), listOf(expenseTx(1, 1_000L, "FOOD")))
        val state = buildMonthlyModeUiState(month, AnalyticsTimeRange.SELECTED_MONTH, previousMonth, ReportMonthlyAmounts(0L, 1_000L, 0L, 0L), details, selectedDrillDownCategory = null)

        assertNull(state.selectedDrillDownCategory)
        assertNull(state.drillDownDetails)
    }
}

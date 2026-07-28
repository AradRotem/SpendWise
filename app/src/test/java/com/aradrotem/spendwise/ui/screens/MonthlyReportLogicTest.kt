package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.BudgetEntity
import com.aradrotem.spendwise.data.local.CategoryMonthlyTotal
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure-logic tests for buildMonthlyReportUiState and buildMonthlyReportShareText - the Monthly
// Report's single source of truth for both the on-screen values and the shared text. Kept as
// top-level functions (not MonthlyReportViewModel members) specifically so they're testable
// without a real ViewModel/Main dispatcher, mirroring buildHomeUiState.
class MonthlyReportLogicTest {

    private val month = YearMonth.of(2026, 7)

    private fun incomeTransaction(amountInCents: Long = 100_000L) = TransactionEntity(
        amountInCents = amountInCents, type = TransactionType.INCOME, category = "SALARY", timestamp = 1_000L
    )

    private fun expenseTransaction(amountInCents: Long = 5_000L, category: String = "FOOD") = TransactionEntity(
        amountInCents = amountInCents, type = TransactionType.EXPENSE, category = category, timestamp = 1_000L
    )

    // --- Totals, balance, and month-over-month comparisons ----------------------------------------

    @Test
    fun incomeExpenseAndBalance_areComputedCorrectly() {
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 600_000L, expenseCents = 325_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = emptyList(), budgets = emptyList(), transactionsInMonth = emptyList()
        )

        assertEquals(month, state.selectedMonth)
        assertEquals(600_000L, state.incomeCents)
        assertEquals(325_000L, state.expenseCents)
        assertEquals(275_000L, state.balanceCents)
    }

    @Test
    fun monthOverMonthChanges_reflectIncreaseAndDecrease() {
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 600_000L, expenseCents = 325_000L,
            previousMonthIncomeCents = 550_000L, previousMonthExpenseCents = 345_000L,
            categoryTotals = emptyList(), budgets = emptyList(), transactionsInMonth = emptyList()
        )

        assertEquals(50_000L, state.incomeChangeCents) // income up
        assertEquals(-20_000L, state.expenseChangeCents) // expenses down
        assertEquals(205_000L, state.previousMonthBalanceCents) // 550000 - 345000
        assertEquals(70_000L, state.balanceChangeCents) // 275000 - 205000
    }

    // --- Category breakdown, ordering, percentages -------------------------------------------------

    @Test
    fun topSpendingCategory_isTheHighestNonZeroEntry() {
        val categoryTotals = listOf(
            CategoryMonthlyTotal("FOOD", 120_000L),
            CategoryMonthlyTotal("TRANSPORT", 80_000L)
        )
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 0L, expenseCents = 200_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = categoryTotals, budgets = emptyList(), transactionsInMonth = emptyList()
        )

        assertEquals("FOOD", state.topCategory?.categoryName)
        assertEquals(120_000L, state.topCategory?.totalCents)
    }

    @Test
    fun categoryBreakdown_preservesDescendingOrderFromTheQuery() {
        // observeExpenseTotalsByCategory already returns rows ORDER BY totalCents DESC; the
        // builder must not silently reorder them.
        val categoryTotals = listOf(
            CategoryMonthlyTotal("FOOD", 120_000L),
            CategoryMonthlyTotal("TRANSPORT", 80_000L),
            CategoryMonthlyTotal("BILLS", 75_000L)
        )
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 0L, expenseCents = 275_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = categoryTotals, budgets = emptyList(), transactionsInMonth = emptyList()
        )

        assertEquals(listOf("FOOD", "TRANSPORT", "BILLS"), state.categoryBreakdown.map { it.categoryName })
    }

    @Test
    fun categoryPercentages_areFractionOfTotalExpenses() {
        val categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 120_000L))
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 0L, expenseCents = 300_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = categoryTotals, budgets = emptyList(), transactionsInMonth = emptyList()
        )

        assertEquals(0.4f, state.categoryBreakdown.single().percentOfExpenses, 0.0001f)
    }

    @Test
    fun categoryPercentages_areSafeWhenExpensesAreZero() {
        // Zero total expenses but a (theoretically stray) category row must not divide by zero.
        val categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 0L))
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 100_000L, expenseCents = 0L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = categoryTotals, budgets = emptyList(), transactionsInMonth = emptyList()
        )

        assertEquals(0f, state.categoryBreakdown.single().percentOfExpenses, 0.0001f)
        assertNull(state.topCategory)
    }

    // --- Budget integration (reuses computeBudgetProgress - no duplicated calculation) -------------

    @Test
    fun budgetSummary_computesSpentRemainingAndOverBudget() {
        val budgets = listOf(BudgetEntity(id = 1, categoryName = "FOOD", monthlyLimitCents = 100_000L))
        val categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 120_000L))

        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 0L, expenseCents = 120_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = categoryTotals, budgets = budgets, transactionsInMonth = emptyList()
        )

        val progress = state.budgets.single()
        assertEquals(120_000L, progress.spentCents)
        assertEquals(-20_000L, progress.remainingCents)
        assertTrue(progress.isExceeded)
    }

    @Test
    fun noBudgets_producesEmptyBudgetList() {
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 0L, expenseCents = 0L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = emptyList(), budgets = emptyList(), transactionsInMonth = emptyList()
        )

        assertTrue(state.budgets.isEmpty())
    }

    // --- Transaction counts --------------------------------------------------------------------------

    @Test
    fun transactionCounts_countIncomeAndExpenseSeparately() {
        val transactions = listOf(
            incomeTransaction(), incomeTransaction(),
            expenseTransaction(), expenseTransaction(), expenseTransaction()
        )
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 200_000L, expenseCents = 15_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = emptyList(), budgets = emptyList(), transactionsInMonth = transactions
        )

        assertEquals(2, state.incomeTransactionCount)
        assertEquals(3, state.expenseTransactionCount)
    }

    // --- Empty and partial-data states -------------------------------------------------------------

    @Test
    fun emptyMonth_hasZeroTotalsAndNoCategoriesOrCounts() {
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 0L, expenseCents = 0L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = emptyList(), budgets = emptyList(), transactionsInMonth = emptyList()
        )

        assertEquals(0L, state.balanceCents)
        assertTrue(state.categoryBreakdown.isEmpty())
        assertNull(state.topCategory)
        assertEquals(0, state.incomeTransactionCount)
        assertEquals(0, state.expenseTransactionCount)
    }

    @Test
    fun incomeOnlyMonth_hasNoExpenseDataButValidIncome() {
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 500_000L, expenseCents = 0L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = emptyList(), budgets = emptyList(), transactionsInMonth = listOf(incomeTransaction())
        )

        assertEquals(500_000L, state.incomeCents)
        assertEquals(500_000L, state.balanceCents)
        assertTrue(state.categoryBreakdown.isEmpty())
        assertEquals(1, state.incomeTransactionCount)
        assertEquals(0, state.expenseTransactionCount)
    }

    @Test
    fun expenseOnlyMonth_hasNoIncomeButValidExpenseData() {
        val categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 30_000L))
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 0L, expenseCents = 30_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = categoryTotals, budgets = emptyList(), transactionsInMonth = listOf(expenseTransaction())
        )

        assertEquals(0L, state.incomeCents)
        assertEquals(-30_000L, state.balanceCents)
        assertEquals("FOOD", state.topCategory?.categoryName)
        assertEquals(0, state.incomeTransactionCount)
        assertEquals(1, state.expenseTransactionCount)
    }

    // --- Share text -----------------------------------------------------------------------------------

    @Test
    fun shareText_usesTheSelectedMonthAndMatchesUiStateTotals() {
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 600_000L, expenseCents = 325_000L,
            previousMonthIncomeCents = 550_000L, previousMonthExpenseCents = 345_000L,
            categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 120_000L)),
            budgets = listOf(BudgetEntity(id = 1, categoryName = "FOOD", monthlyLimitCents = 150_000L)),
            transactionsInMonth = listOf(incomeTransaction(), expenseTransaction())
        )

        val text = buildMonthlyReportShareText(state)

        assertTrue(text.contains("SpendWise Monthly Report — July 2026"))
        assertTrue(text.contains("Compared with June 2026:"))
        assertTrue(text.contains("Income: ${com.aradrotem.spendwise.ui.format.formatAmountInCents(state.incomeCents)}"))
        assertTrue(text.contains("Expenses: ${com.aradrotem.spendwise.ui.format.formatAmountInCents(state.expenseCents)}"))
        assertTrue(text.contains("Income: +${com.aradrotem.spendwise.ui.format.formatAmountInCents(state.incomeChangeCents)}"))
        assertTrue(text.contains("Expenses: -${com.aradrotem.spendwise.ui.format.formatAmountInCents(-state.expenseChangeCents)}"))
        assertTrue(text.contains("Food: ${com.aradrotem.spendwise.ui.format.formatAmountInCents(120_000L)}"))
        assertTrue(text.contains("Food: ${com.aradrotem.spendwise.ui.format.formatAmountInCents(120_000L)} of " +
            com.aradrotem.spendwise.ui.format.formatAmountInCents(150_000L)))
        assertTrue(text.contains("Income: 1"))
        assertTrue(text.contains("Expenses: 1"))
    }

    @Test
    fun shareText_omitsBudgetsSectionWhenNoBudgetsExist() {
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 100_000L, expenseCents = 0L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = emptyList(), budgets = emptyList(), transactionsInMonth = emptyList()
        )

        val text = buildMonthlyReportShareText(state)

        assertFalse(text.contains("Budgets:"))
    }

    @Test
    fun shareText_labelsMissingExpenseDataInsteadOfCrashingOrShowingGarbage() {
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 100_000L, expenseCents = 0L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = emptyList(), budgets = emptyList(), transactionsInMonth = emptyList()
        )

        val text = buildMonthlyReportShareText(state)

        assertTrue(text.contains("No expenses recorded this month."))
        assertFalse(text.contains("Top spending category:"))
    }

    @Test
    fun shareText_marksOverBudgetCategoriesClearly() {
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 0L, expenseCents = 120_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 120_000L)),
            budgets = listOf(BudgetEntity(id = 1, categoryName = "FOOD", monthlyLimitCents = 100_000L)),
            transactionsInMonth = emptyList()
        )

        val text = buildMonthlyReportShareText(state)

        assertTrue(text.contains("— over budget"))
    }

    @Test
    fun shareText_containsNoInternalIdsOrMisleadingCancellationWording() {
        val budgets = listOf(BudgetEntity(id = 42, categoryName = "FOOD", monthlyLimitCents = 100_000L))
        val transactions = listOf(
            TransactionEntity(
                id = 999L, amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1_000L,
                recurringPlanId = 7L, installmentNumber = 3, totalInstallments = 12, isAutomaticallyGenerated = true,
                scheduledYearMonth = "2026-07"
            )
        )
        val state = buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = 0L, expenseCents = 5_000L,
            previousMonthIncomeCents = 0L, previousMonthExpenseCents = 0L,
            categoryTotals = listOf(CategoryMonthlyTotal("FOOD", 5_000L)),
            budgets = budgets, transactionsInMonth = transactions
        )

        val text = buildMonthlyReportShareText(state)

        val forbidden = listOf("999", "42", "recurringPlanId", "installment 3", "cancel payment", "cancel installment", "bank", "card provider")
        forbidden.forEach { token ->
            assertFalse("share text must not contain \"$token\"", text.lowercase().contains(token.lowercase()))
        }
    }
}

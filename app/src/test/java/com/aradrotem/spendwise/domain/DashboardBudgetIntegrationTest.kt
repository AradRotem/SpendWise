package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.BudgetRepository
import com.aradrotem.spendwise.data.repository.RecurringOccurrenceExceptionRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.ui.screens.computeBudgetProgress
import com.aradrotem.spendwise.util.MonthRange
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Step 12: verifies that generated recurring transactions - and their Step 11 occurrence-level
// edits/deletes - flow correctly into the same monthly income/expense/category-total queries the
// Home dashboard and Budgets screen already use for manual transactions, with no double counting
// from the recurring plan table or the occurrence-exception table.
class DashboardBudgetIntegrationTest {

    private val zoneId = ZoneOffset.UTC
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var planDao: FakeRecurringPaymentPlanDao
    private lateinit var occurrenceExceptionDao: FakeRecurringOccurrenceExceptionDao
    private lateinit var budgetDao: FakeBudgetDao
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var recurringPaymentRepository: RecurringPaymentRepository
    private lateinit var occurrenceExceptionRepository: RecurringOccurrenceExceptionRepository
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var manager: RecurringOccurrenceManager
    private lateinit var generator: RecurringPaymentGenerator

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        planDao = FakeRecurringPaymentPlanDao()
        occurrenceExceptionDao = FakeRecurringOccurrenceExceptionDao()
        budgetDao = FakeBudgetDao()
        transactionRepository = TransactionRepository(transactionDao)
        recurringPaymentRepository = RecurringPaymentRepository(planDao)
        occurrenceExceptionRepository = RecurringOccurrenceExceptionRepository(occurrenceExceptionDao)
        budgetRepository = BudgetRepository(budgetDao)
        manager = RecurringOccurrenceManager(transactionRepository, recurringPaymentRepository, occurrenceExceptionRepository, zoneId)
        generator = RecurringPaymentGenerator(recurringPaymentRepository, transactionRepository, occurrenceExceptionRepository, zoneId)
    }

    private fun millisFor(date: LocalDate) = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun rangeFor(yearMonth: YearMonth): MonthRange {
        val start = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return MonthRange(start, endExclusive)
    }

    private suspend fun expenseTotal(yearMonth: YearMonth): Long {
        val range = rangeFor(yearMonth)
        return transactionRepository.observeTotalByType(TransactionType.EXPENSE, range.startMillis, range.endExclusiveMillis).first()
    }

    private suspend fun incomeTotal(yearMonth: YearMonth): Long {
        val range = rangeFor(yearMonth)
        return transactionRepository.observeTotalByType(TransactionType.INCOME, range.startMillis, range.endExclusiveMillis).first()
    }

    private suspend fun categoryTotals(yearMonth: YearMonth): Map<String, Long> {
        val range = rangeFor(yearMonth)
        return transactionRepository.observeExpenseTotalsByCategory(range.startMillis, range.endExclusiveMillis).first()
            .associateBy({ it.category }, { it.totalCents })
    }

    private suspend fun budgetProgressFor(yearMonth: YearMonth) = run {
        val range = rangeFor(yearMonth)
        val totals = transactionRepository.observeExpenseTotalsByCategory(range.startMillis, range.endExclusiveMillis).first()
        computeBudgetProgress(budgetDao.allRows, totals)
    }

    private suspend fun createMonthlyPlan(
        type: RecurringPlanType = RecurringPlanType.MONTHLY_RECURRING,
        title: String = "Rent",
        categoryName: String = "HOUSING",
        amountInCents: Long = 5_000L,
        startDate: LocalDate = LocalDate.of(2026, 1, 1)
    ): Long {
        recurringPaymentRepository.createMonthlyPlan(
            type = type, title = title, categoryName = categoryName, note = "", amountInCents = amountInCents,
            startDateMillis = millisFor(startDate), preferredDayOfMonth = startDate.dayOfMonth, endDateMillis = null
        )
        return planDao.allRows.single().id
    }

    // 1. A generated recurring expense is included in the correct month's expense total.
    @Test
    fun generatedRecurringExpense_includedInCorrectMonthExpenseTotal() = runBlocking {
        createMonthlyPlan(amountInCents = 5_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))

        assertEquals(5_000L, expenseTotal(YearMonth.of(2026, 1)))
    }

    // 2. A generated recurring income/salary is included in income but not budget spending.
    @Test
    fun generatedRecurringSalary_includedInIncomeButNotBudgetSpending() = runBlocking {
        createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY, title = "Salary", categoryName = "SALARY", amountInCents = 500_000L
        )
        budgetRepository.addBudget("SALARY", 100_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))

        assertEquals(500_000L, incomeTotal(YearMonth.of(2026, 1)))
        assertEquals(0L, expenseTotal(YearMonth.of(2026, 1)))
        assertEquals(0L, budgetProgressFor(YearMonth.of(2026, 1)).single().spentCents)
    }

    // 3. A generated recurring expense contributes to the matching category budget.
    @Test
    fun generatedRecurringExpense_contributesToMatchingCategoryBudget() = runBlocking {
        createMonthlyPlan(categoryName = "HOUSING", amountInCents = 5_000L)
        budgetRepository.addBudget("HOUSING", 8_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))

        val progress = budgetProgressFor(YearMonth.of(2026, 1)).single { it.categoryName == "HOUSING" }
        assertEquals(5_000L, progress.spentCents)
        assertEquals(3_000L, progress.remainingCents)
    }

    // 4. An occurrence in a different month is excluded from the current month.
    @Test
    fun occurrenceInDifferentMonth_excludedFromCurrentMonthTotal() = runBlocking {
        createMonthlyPlan(amountInCents = 5_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 2, 15)) // generates Jan + Feb

        assertEquals(5_000L, expenseTotal(YearMonth.of(2026, 1)))
        assertEquals(5_000L, expenseTotal(YearMonth.of(2026, 2)))
        assertEquals(0L, expenseTotal(YearMonth.of(2026, 3)))
    }

    // 5. Editing an occurrence amount updates the relevant totals.
    @Test
    fun editingOccurrenceAmount_updatesMonthlyTotal() = runBlocking {
        createMonthlyPlan(amountInCents = 5_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single()

        manager.editOccurrenceOnly(
            transactionId = occurrence.id, title = "Rent", amountInCents = 7_500L,
            category = occurrence.category, note = "", timestamp = occurrence.timestamp
        )

        assertEquals(7_500L, expenseTotal(YearMonth.of(2026, 1)))
    }

    // 6. Editing its category moves spending between category totals (and budgets).
    @Test
    fun editingOccurrenceCategory_movesSpendingBetweenCategories() = runBlocking {
        createMonthlyPlan(categoryName = "HOUSING", amountInCents = 5_000L)
        budgetRepository.addBudget("HOUSING", 10_000L)
        budgetRepository.addBudget("UTILITIES", 10_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single()

        manager.editOccurrenceOnly(
            transactionId = occurrence.id, title = "Rent", amountInCents = occurrence.amountInCents,
            category = "UTILITIES", note = "", timestamp = occurrence.timestamp
        )

        val totals = categoryTotals(YearMonth.of(2026, 1))
        assertNull(totals["HOUSING"])
        assertEquals(5_000L, totals["UTILITIES"])

        val progress = budgetProgressFor(YearMonth.of(2026, 1))
        assertEquals(0L, progress.single { it.categoryName == "HOUSING" }.spentCents)
        assertEquals(5_000L, progress.single { it.categoryName == "UTILITIES" }.spentCents)
    }

    // 7. Editing its date moves it between monthly totals. editOccurrenceOnly deliberately rejects
    // moving a *generated occurrence* outside its scheduled month (Step 11 rule, still covered by
    // RecurringOccurrenceManagerTest.editOccurrenceOnly_rejectsDateOutsideScheduledMonth), so this
    // is exercised on a plain manual transaction via the same transactionRepository.update() path
    // AddTransactionViewModel uses for any transaction edit.
    @Test
    fun editingTransactionDate_movesItBetweenMonthlyTotals() = runBlocking {
        val id = transactionRepository.insert(
            TransactionEntity(
                amountInCents = 3_000L, type = TransactionType.EXPENSE, category = "FOOD",
                timestamp = millisFor(LocalDate.of(2026, 1, 10))
            )
        )
        assertEquals(3_000L, expenseTotal(YearMonth.of(2026, 1)))
        assertEquals(0L, expenseTotal(YearMonth.of(2026, 2)))

        val existing = transactionRepository.getById(id)!!
        transactionRepository.update(existing.copy(timestamp = millisFor(LocalDate.of(2026, 2, 10))))

        assertEquals(0L, expenseTotal(YearMonth.of(2026, 1)))
        assertEquals(3_000L, expenseTotal(YearMonth.of(2026, 2)))
    }

    // 8. Deleting one occurrence removes it from dashboard and budget calculations.
    @Test
    fun deletingOneOccurrence_removesItFromDashboardAndBudgetTotals() = runBlocking {
        createMonthlyPlan(categoryName = "HOUSING", amountInCents = 5_000L)
        budgetRepository.addBudget("HOUSING", 10_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single()

        manager.deleteOccurrenceOnly(occurrence)

        assertEquals(0L, expenseTotal(YearMonth.of(2026, 1)))
        assertEquals(0L, budgetProgressFor(YearMonth.of(2026, 1)).single().spentCents)
    }

    // 9. A recurring plan definition is not counted without a generated transaction row.
    @Test
    fun recurringPlanDefinition_notCountedWithoutGeneratedRow() = runBlocking {
        createMonthlyPlan(amountInCents = 5_000L)
        // No generateDuePayments() call: the plan exists but has produced no transaction rows yet.

        assertEquals(0L, expenseTotal(YearMonth.of(2026, 1)))
        assertTrue(transactionDao.allRows.isEmpty())
    }

    // 10. There is no double counting when both a recurring plan and its generated rows exist.
    @Test
    fun noDoubleCounting_whenPlanAndGeneratedRowsBothExist() = runBlocking {
        createMonthlyPlan(amountInCents = 5_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        // Re-running generation must not create a second row for the same plan+month (idempotent
        // via the unique index simulated in FakeTransactionDao).
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 20))

        assertEquals(1, transactionDao.allRows.size)
        assertEquals(5_000L, expenseTotal(YearMonth.of(2026, 1)))
    }

    // 11. A skipped-occurrence exception is not counted as a transaction.
    @Test
    fun skippedOccurrenceException_notCountedAsTransaction() = runBlocking {
        createMonthlyPlan(amountInCents = 5_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single()
        manager.deleteOccurrenceOnly(occurrence) // records a SKIPPED exception for 2026-01

        assertEquals(1, occurrenceExceptionDao.allRows.size)
        assertEquals(0L, expenseTotal(YearMonth.of(2026, 1)))
        assertTrue(transactionDao.allRows.isEmpty())
    }

    // 12. Existing non-recurring transactions continue to behave unchanged alongside recurring ones.
    @Test
    fun manualTransactions_behaveUnchangedAlongsideRecurringOnes() = runBlocking {
        transactionRepository.insert(
            TransactionEntity(
                amountInCents = 1_200L, type = TransactionType.EXPENSE, category = "FOOD",
                timestamp = millisFor(LocalDate.of(2026, 1, 5))
            )
        )
        createMonthlyPlan(categoryName = "HOUSING", amountInCents = 5_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))

        assertEquals(6_200L, expenseTotal(YearMonth.of(2026, 1)))
        val totals = categoryTotals(YearMonth.of(2026, 1))
        assertEquals(1_200L, totals["FOOD"])
        assertEquals(5_000L, totals["HOUSING"])
    }
}

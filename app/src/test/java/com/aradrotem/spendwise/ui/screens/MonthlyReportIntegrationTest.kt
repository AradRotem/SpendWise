package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.BudgetRepository
import com.aradrotem.spendwise.data.repository.RecurringOccurrenceExceptionRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.FakeBudgetDao
import com.aradrotem.spendwise.domain.FakeRecurringOccurrenceExceptionDao
import com.aradrotem.spendwise.domain.FakeRecurringPaymentPlanDao
import com.aradrotem.spendwise.domain.FakeTransactionDao
import com.aradrotem.spendwise.domain.RecurringPaymentGenerator
import com.aradrotem.spendwise.util.MonthRange
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Exercises the Monthly Report's data path through the REAL production repositories and the
// recurring generator, the same way DashboardBudgetIntegrationTest verified Step 12's dashboard/
// budget integration - proving generated recurring transactions (expense, salary, installment)
// flow into the report exactly like manual ones, with correct cross-month exclusion and no crash
// when a source plan is later deleted (the report never looks up plans at all - see
// MonthlyReportViewModel, which only reads TransactionRepository/BudgetRepository).
class MonthlyReportIntegrationTest {

    private val zoneId = ZoneOffset.UTC
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var planDao: FakeRecurringPaymentPlanDao
    private lateinit var occurrenceExceptionDao: FakeRecurringOccurrenceExceptionDao
    private lateinit var budgetDao: FakeBudgetDao
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var recurringPaymentRepository: RecurringPaymentRepository
    private lateinit var occurrenceExceptionRepository: RecurringOccurrenceExceptionRepository
    private lateinit var budgetRepository: BudgetRepository
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
        generator = RecurringPaymentGenerator(recurringPaymentRepository, transactionRepository, occurrenceExceptionRepository, zoneId)
    }

    private fun millisFor(date: LocalDate) = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun rangeFor(yearMonth: YearMonth): MonthRange {
        val start = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return MonthRange(start, endExclusive)
    }

    // Mirrors MonthlyReportViewModel.observeReportFor's data-gathering, using .first() snapshots
    // instead of a live flatMapLatest subscription.
    private suspend fun reportFor(month: YearMonth): MonthlyReportUiState {
        val range = rangeFor(month)
        val previousRange = rangeFor(month.minusMonths(1))

        val income = transactionRepository.observeTotalByType(TransactionType.INCOME, range.startMillis, range.endExclusiveMillis).first()
        val expense = transactionRepository.observeTotalByType(TransactionType.EXPENSE, range.startMillis, range.endExclusiveMillis).first()
        val previousIncome = transactionRepository.observeTotalByType(
            TransactionType.INCOME, previousRange.startMillis, previousRange.endExclusiveMillis
        ).first()
        val previousExpense = transactionRepository.observeTotalByType(
            TransactionType.EXPENSE, previousRange.startMillis, previousRange.endExclusiveMillis
        ).first()
        val categoryTotals = transactionRepository.observeExpenseTotalsByCategory(range.startMillis, range.endExclusiveMillis).first()
        val budgets = budgetRepository.observeAll().first()
        val transactionsInMonth = transactionRepository.observeInRange(range.startMillis, range.endExclusiveMillis).first()

        return buildMonthlyReportUiState(
            selectedMonth = month, incomeCents = income, expenseCents = expense,
            previousMonthIncomeCents = previousIncome, previousMonthExpenseCents = previousExpense,
            categoryTotals = categoryTotals, budgets = budgets, transactionsInMonth = transactionsInMonth
        )
    }

    private suspend fun createMonthlyPlan(
        type: RecurringPlanType,
        title: String,
        categoryName: String,
        amountInCents: Long,
        startDate: LocalDate
    ): Long {
        recurringPaymentRepository.createMonthlyPlan(
            type = type, title = title, categoryName = categoryName, note = "", amountInCents = amountInCents,
            startDateMillis = millisFor(startDate), preferredDayOfMonth = startDate.dayOfMonth, endDateMillis = null
        )
        return planDao.allRows.single { it.title == title }.id
    }

    // --- Recurring expense flows into report totals and budget --------------------------------------

    @Test
    fun recurringExpense_flowsIntoReportTotalsAndBudget() = runBlocking {
        createMonthlyPlan(RecurringPlanType.MONTHLY_RECURRING, "Rent", "HOUSING", 50_000L, LocalDate.of(2026, 7, 1))
        budgetRepository.addBudget("HOUSING", 80_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 7, 15))

        val report = reportFor(YearMonth.of(2026, 7))

        assertEquals(50_000L, report.expenseCents)
        assertEquals("HOUSING", report.topCategory?.categoryName)
        val budget = report.budgets.single()
        assertEquals(50_000L, budget.spentCents)
        assertEquals(30_000L, budget.remainingCents)
    }

    // --- Recurring salary: income yes, budget spend no -----------------------------------------------

    @Test
    fun recurringSalary_countsAsIncomeButNeverAsBudgetSpend() = runBlocking {
        createMonthlyPlan(RecurringPlanType.MONTHLY_SALARY, "Salary", "SALARY", 600_000L, LocalDate.of(2026, 7, 1))
        budgetRepository.addBudget("SALARY", 100_000L) // mis-created budget on an income category
        generator.generateDuePayments(today = LocalDate.of(2026, 7, 15))

        val report = reportFor(YearMonth.of(2026, 7))

        assertEquals(600_000L, report.incomeCents)
        assertEquals(0L, report.expenseCents)
        assertEquals(0L, report.budgets.single().spentCents)
    }

    // --- Installment occurrence included in report ----------------------------------------------------

    @Test
    fun installmentOccurrence_isIncludedInReportExpensesAndCategoryBreakdown() = runBlocking {
        recurringPaymentRepository.createInstallmentPlan(
            title = "Laptop", categoryName = "SHOPPING", note = "", totalAmountInCents = 300_000L,
            totalInstallments = 3, firstPaymentDateMillis = millisFor(LocalDate.of(2026, 7, 1)), zoneId = zoneId
        )
        generator.generateDuePayments(today = LocalDate.of(2026, 7, 1))

        val report = reportFor(YearMonth.of(2026, 7))

        assertEquals(100_000L, report.expenseCents) // 300000 / 3
        assertEquals("SHOPPING", report.categoryBreakdown.single().categoryName)
        assertEquals(1, report.expenseTransactionCount)
    }

    // --- Cross-month exclusion --------------------------------------------------------------------------

    @Test
    fun occurrenceInAnotherMonth_isExcludedFromTheSelectedMonthsReport() = runBlocking {
        createMonthlyPlan(RecurringPlanType.MONTHLY_RECURRING, "Rent", "HOUSING", 50_000L, LocalDate.of(2026, 1, 1))
        generator.generateDuePayments(today = LocalDate.of(2026, 2, 15)) // generates Jan + Feb

        val julyReport = reportFor(YearMonth.of(2026, 7))

        assertEquals(0L, julyReport.expenseCents)
        assertTrue(julyReport.categoryBreakdown.isEmpty())
        assertEquals(0, julyReport.expenseTransactionCount)
    }

    // --- Deleted source plan does not affect the report -------------------------------------------------

    @Test
    fun deletedSourcePlan_doesNotAffectReportCalculationsOrCrash() = runBlocking {
        val planId = createMonthlyPlan(RecurringPlanType.MONTHLY_RECURRING, "Netflix", "ENTERTAINMENT", 20_000L, LocalDate.of(2026, 7, 1))
        generator.generateDuePayments(today = LocalDate.of(2026, 7, 15))
        val plan = planDao.allRows.single { it.id == planId }

        planDao.delete(plan)

        // The report never looks up RecurringPaymentRepository at all - it only reads already-
        // generated transaction rows - so deleting the plan must have zero effect on its output.
        val report = reportFor(YearMonth.of(2026, 7))

        assertEquals(20_000L, report.expenseCents)
        assertEquals("ENTERTAINMENT", report.topCategory?.categoryName)
        assertEquals(1, report.expenseTransactionCount)
    }

    // --- Month-over-month comparison across generated data ------------------------------------------------

    @Test
    fun monthOverMonthComparison_reflectsGeneratedTransactionsAcrossBothMonths() = runBlocking {
        createMonthlyPlan(RecurringPlanType.MONTHLY_RECURRING, "Rent", "HOUSING", 50_000L, LocalDate.of(2026, 6, 1))
        generator.generateDuePayments(today = LocalDate.of(2026, 7, 15)) // generates June + July

        val julyReport = reportFor(YearMonth.of(2026, 7))

        assertEquals(50_000L, julyReport.expenseCents)
        assertEquals(50_000L, julyReport.previousMonthExpenseCents)
        assertEquals(0L, julyReport.expenseChangeCents)
    }
}

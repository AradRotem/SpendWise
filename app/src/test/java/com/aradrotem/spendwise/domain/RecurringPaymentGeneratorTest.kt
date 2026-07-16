package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Exercises RecurringPaymentGenerator's orchestration against in-memory fake DAOs (no real
// database), so it runs as a fast, verifiable plain unit test. DB-level concerns (the real SQLite
// unique index, actual SQL correctness) are covered separately by instrumented DAO tests.
class RecurringPaymentGeneratorTest {

    private val zoneId = ZoneOffset.UTC
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var planDao: FakeRecurringPaymentPlanDao
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var recurringPaymentRepository: RecurringPaymentRepository
    private lateinit var generator: RecurringPaymentGenerator

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        planDao = FakeRecurringPaymentPlanDao()
        transactionRepository = TransactionRepository(transactionDao)
        recurringPaymentRepository = RecurringPaymentRepository(planDao)
        generator = RecurringPaymentGenerator(recurringPaymentRepository, transactionRepository, zoneId)
    }

    private fun millisFor(date: LocalDate) = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    @Test
    fun monthlyPlan_createsOneDueTransaction() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 3, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )

        val count = generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        assertEquals(1, count)
        assertEquals(1, transactionDao.allRows.size)
    }

    @Test
    fun runningGenerationTwice_doesNotDuplicate() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 3, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )

        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))
        val secondRunCount = generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        assertEquals(0, secondRunCount)
        assertEquals(1, transactionDao.allRows.size)
    }

    @Test
    fun pausedPlan_createsNoPayments() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val planId = planDao.allRows.single().id
        recurringPaymentRepository.pause(planId)

        val count = generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        assertEquals(0, count)
        assertTrue(transactionDao.allRows.isEmpty())
    }

    @Test
    fun endedPlan_createsNoPaymentsAfterEndDate() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1,
            endDateMillis = millisFor(LocalDate.of(2026, 1, 1))
        )

        generator.generateDuePayments(today = LocalDate.of(2026, 5, 1))

        assertEquals(1, transactionDao.allRows.size)
        assertEquals("2026-01", transactionDao.allRows.single().scheduledYearMonth)
    }

    @Test
    fun futurePayment_notCreatedBeforeDueDate() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 3, 1)), preferredDayOfMonth = 20, endDateMillis = null
        )

        val count = generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        assertEquals(0, count)
        assertTrue(transactionDao.allRows.isEmpty())
    }

    @Test
    fun missedMonths_generatedDuringCatchUp() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 5)), preferredDayOfMonth = 5, endDateMillis = null
        )

        val count = generator.generateDuePayments(today = LocalDate.of(2026, 4, 10))

        assertEquals(4, count)
        assertEquals(
            listOf("2026-01", "2026-02", "2026-03", "2026-04"),
            transactionDao.allRows.mapNotNull { it.scheduledYearMonth }.sorted()
        )
    }

    @Test
    fun installmentPlan_generatesCorrectNumberOfPaymentsAndSumsExactly() = runBlocking {
        recurringPaymentRepository.createInstallmentPlan(
            title = "Laptop", categoryName = "SHOPPING", note = "", totalAmountInCents = 100_000L,
            totalInstallments = 3, firstPaymentDateMillis = millisFor(LocalDate.of(2026, 1, 15)), zoneId = zoneId
        )

        val count = generator.generateDuePayments(today = LocalDate.of(2026, 12, 1))

        assertEquals(3, count)
        assertEquals(100_000L, transactionDao.allRows.sumOf { it.amountInCents })
        assertEquals(33_334L, transactionDao.allRows.maxBy { it.installmentNumber ?: 0 }.amountInCents)
    }

    @Test
    fun installmentGeneration_storesSourceTitleOnEveryInstallment() = runBlocking {
        recurringPaymentRepository.createInstallmentPlan(
            title = "Laptop", categoryName = "ELECTRONICS", note = "", totalAmountInCents = 90_000L,
            totalInstallments = 3, firstPaymentDateMillis = millisFor(LocalDate.of(2026, 1, 15)), zoneId = zoneId
        )

        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        assertEquals(3, transactionDao.allRows.size)
        assertTrue(transactionDao.allRows.all { it.sourceTitle == "Laptop" })
    }

    @Test
    fun installmentPlan_stopsAfterFinalInstallmentAndMarksCompleted() = runBlocking {
        recurringPaymentRepository.createInstallmentPlan(
            title = "Laptop", categoryName = "SHOPPING", note = "", totalAmountInCents = 30_000L,
            totalInstallments = 3, firstPaymentDateMillis = millisFor(LocalDate.of(2026, 1, 15)), zoneId = zoneId
        )

        generator.generateDuePayments(today = LocalDate.of(2030, 1, 1))
        val secondRunCount = generator.generateDuePayments(today = LocalDate.of(2031, 1, 1))

        assertEquals(3, transactionDao.allRows.size)
        assertEquals(0, secondRunCount)
        assertEquals(RecurringPlanStatus.COMPLETED, planDao.allRows.single().status)
    }

    @Test
    fun generatedTransactions_useExpenseTypeAndPreserveCategoryAndNoteAndLinkToPlan() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Netflix", categoryName = "ENTERTAINMENT", note = "Streaming subscription",
            amountInCents = 1_500L, startDateMillis = millisFor(LocalDate.of(2026, 3, 1)),
            preferredDayOfMonth = 1, endDateMillis = null
        )
        val planId = planDao.allRows.single().id

        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        val generated = transactionDao.allRows.single()
        assertEquals(TransactionType.EXPENSE, generated.type)
        assertEquals("ENTERTAINMENT", generated.category)
        assertEquals("Streaming subscription", generated.note)
        assertEquals(planId, generated.recurringPlanId)
        assertEquals("2026-03", generated.scheduledYearMonth)
        assertTrue(generated.isAutomaticallyGenerated)
    }

    // --- Monthly Salary ---------------------------------------------------------------------

    @Test
    fun monthlySalary_createsIncomeTransaction() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY,
            title = "Monthly salary", categoryName = "SALARY", note = "", amountInCents = 1_000_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 3, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )

        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        val generated = transactionDao.allRows.single()
        assertEquals(TransactionType.INCOME, generated.type)
        assertEquals(1_000_000L, generated.amountInCents)
    }

    @Test
    fun monthlySalary_preservesTitleCategoryAndNote() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY,
            title = "Monthly salary", categoryName = "SALARY", note = "Base pay", amountInCents = 1_000_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 3, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )

        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        val generated = transactionDao.allRows.single()
        assertEquals("Monthly salary", generated.sourceTitle)
        assertEquals("SALARY", generated.category)
        assertEquals("Base pay", generated.note)
    }

    @Test
    fun monthlySalary_generationIsIdempotent() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY,
            title = "Monthly salary", categoryName = "SALARY", note = "", amountInCents = 1_000_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 3, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )

        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))
        val secondRunCount = generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        assertEquals(0, secondRunCount)
        assertEquals(1, transactionDao.allRows.size)
    }

    @Test
    fun monthlySalary_catchUpGeneratesMissingDueMonths() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY,
            title = "Monthly salary", categoryName = "SALARY", note = "", amountInCents = 1_000_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )

        val count = generator.generateDuePayments(today = LocalDate.of(2026, 4, 10))

        assertEquals(4, count)
        assertTrue(transactionDao.allRows.all { it.type == TransactionType.INCOME })
    }

    @Test
    fun pausedSalary_createsNoTransactions() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY,
            title = "Monthly salary", categoryName = "SALARY", note = "", amountInCents = 1_000_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val planId = planDao.allRows.single().id
        recurringPaymentRepository.pause(planId)

        val count = generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        assertEquals(0, count)
        assertTrue(transactionDao.allRows.isEmpty())
    }

    @Test
    fun stoppedSalary_createsNoTransactions() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY,
            title = "Monthly salary", categoryName = "SALARY", note = "", amountInCents = 1_000_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val planId = planDao.allRows.single().id
        recurringPaymentRepository.stop(planId)

        val count = generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        assertEquals(0, count)
        assertTrue(transactionDao.allRows.isEmpty())
    }

    @Test
    fun futureSalary_doesNotGenerateEarly() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY,
            title = "Monthly salary", categoryName = "SALARY", note = "", amountInCents = 1_000_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 6, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )

        val count = generator.generateDuePayments(today = LocalDate.of(2026, 3, 1))

        assertEquals(0, count)
    }

    @Test
    fun monthlySalary_preservesIncomeCategory() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY,
            title = "Freelance income", categoryName = "OTHER", note = "", amountInCents = 500_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 3, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )

        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        assertEquals("OTHER", transactionDao.allRows.single().category)
    }

    @Test
    fun monthlySalary_neverCreatesExpenseTransaction() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY,
            title = "Monthly salary", categoryName = "SALARY", note = "", amountInCents = 1_000_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )

        generator.generateDuePayments(today = LocalDate.of(2026, 4, 10))

        assertFalse(transactionDao.allRows.any { it.type == TransactionType.EXPENSE })
    }

    // --- Title snapshot -----------------------------------------------------------------------

    @Test
    fun generatedTransaction_storesThePlanTitle() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Netflix", categoryName = "ENTERTAINMENT", note = "", amountInCents = 1_500L,
            startDateMillis = millisFor(LocalDate.of(2026, 3, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )

        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        assertEquals("Netflix", transactionDao.allRows.single().sourceTitle)
    }

    @Test
    fun editingPlanTitle_doesNotModifyExistingGeneratedTransactions() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Netflix", categoryName = "ENTERTAINMENT", note = "", amountInCents = 1_500L,
            startDateMillis = millisFor(LocalDate.of(2026, 3, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val planId = planDao.allRows.single().id
        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        recurringPaymentRepository.updateMonthlyPlan(
            id = planId, title = "Netflix Premium", categoryName = "ENTERTAINMENT", note = "",
            amountInCents = 1_500L, preferredDayOfMonth = 1, endDateMillis = null
        )

        assertEquals("Netflix", transactionDao.allRows.single().sourceTitle)
    }

    @Test
    fun futureGeneratedTransactions_useUpdatedTitle() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Netflix", categoryName = "ENTERTAINMENT", note = "", amountInCents = 1_500L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val planId = planDao.allRows.single().id
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))

        recurringPaymentRepository.updateMonthlyPlan(
            id = planId, title = "Netflix Premium", categoryName = "ENTERTAINMENT", note = "",
            amountInCents = 1_500L, preferredDayOfMonth = 1, endDateMillis = null
        )
        generator.generateDuePayments(today = LocalDate.of(2026, 2, 15))

        val titles = transactionDao.allRows.sortedBy { it.scheduledYearMonth }.map { it.sourceTitle }
        assertEquals(listOf("Netflix", "Netflix Premium"), titles)
    }

    @Test
    fun deletingPlan_doesNotRemoveGeneratedTransactionTitle() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Netflix", categoryName = "ENTERTAINMENT", note = "", amountInCents = 1_500L,
            startDateMillis = millisFor(LocalDate.of(2026, 3, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val plan = planDao.allRows.single()
        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        recurringPaymentRepository.deletePlan(plan)

        assertEquals("Netflix", transactionDao.allRows.single().sourceTitle)
        assertTrue(planDao.allRows.isEmpty())
    }

    // --- Restore and delete -------------------------------------------------------------------

    @Test
    fun stoppedPlan_canBeRestored() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val planId = planDao.allRows.single().id
        recurringPaymentRepository.stop(planId)

        recurringPaymentRepository.resume(planId)

        assertEquals(RecurringPlanStatus.ACTIVE, planDao.allRows.single().status)
    }

    @Test
    fun restoredPlan_generatesMissingDueTransactionsWithoutDuplicates() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val planId = planDao.allRows.single().id
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        recurringPaymentRepository.stop(planId)

        recurringPaymentRepository.resume(planId)
        val restoredRunCount = generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        // January already existed; February and March are missing due months caught up on restore.
        assertEquals(2, restoredRunCount)
        assertEquals(3, transactionDao.allRows.size)
        assertEquals(3, transactionDao.allRows.map { it.scheduledYearMonth }.distinct().size)
    }

    @Test
    fun deletingAPlan_removesThePlan() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val plan = planDao.allRows.single()

        recurringPaymentRepository.deletePlan(plan)

        assertTrue(planDao.allRows.isEmpty())
    }

    @Test
    fun deletingAPlan_preservesHistoricalGeneratedTransactions() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val plan = planDao.allRows.single()
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))

        recurringPaymentRepository.deletePlan(plan)

        assertEquals(1, transactionDao.allRows.size)
    }

    @Test
    fun completedInstallmentPlan_canBeDeletedSafely() = runBlocking {
        recurringPaymentRepository.createInstallmentPlan(
            title = "Laptop", categoryName = "SHOPPING", note = "", totalAmountInCents = 30_000L,
            totalInstallments = 3, firstPaymentDateMillis = millisFor(LocalDate.of(2026, 1, 15)), zoneId = zoneId
        )
        generator.generateDuePayments(today = LocalDate.of(2030, 1, 1))
        val plan = planDao.allRows.single()
        assertEquals(RecurringPlanStatus.COMPLETED, plan.status)

        recurringPaymentRepository.deletePlan(plan)

        assertTrue(planDao.allRows.isEmpty())
        assertEquals(3, transactionDao.allRows.size)
    }

    // --- Edit ------------------------------------------------------------------------------

    @Test
    fun editingMonthlyExpense_affectsOnlyFutureGeneration() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_RECURRING,
            title = "Rent", categoryName = "HOUSING", note = "", amountInCents = 5_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val planId = planDao.allRows.single().id
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))

        recurringPaymentRepository.updateMonthlyPlan(
            id = planId, title = "Rent", categoryName = "HOUSING", note = "",
            amountInCents = 6_000L, preferredDayOfMonth = 1, endDateMillis = null
        )
        generator.generateDuePayments(today = LocalDate.of(2026, 2, 15))

        val amounts = transactionDao.allRows.sortedBy { it.scheduledYearMonth }.map { it.amountInCents }
        assertEquals(listOf(5_000L, 6_000L), amounts)
    }

    @Test
    fun editingSalary_affectsOnlyFutureGeneration() = runBlocking {
        recurringPaymentRepository.createMonthlyPlan(
            type = RecurringPlanType.MONTHLY_SALARY,
            title = "Monthly salary", categoryName = "SALARY", note = "", amountInCents = 1_000_000L,
            startDateMillis = millisFor(LocalDate.of(2026, 1, 1)), preferredDayOfMonth = 1, endDateMillis = null
        )
        val planId = planDao.allRows.single().id
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))

        recurringPaymentRepository.updateMonthlyPlan(
            id = planId, title = "Monthly salary", categoryName = "SALARY", note = "",
            amountInCents = 1_200_000L, preferredDayOfMonth = 1, endDateMillis = null
        )
        generator.generateDuePayments(today = LocalDate.of(2026, 2, 15))

        val amounts = transactionDao.allRows.sortedBy { it.scheduledYearMonth }.map { it.amountInCents }
        assertEquals(listOf(1_000_000L, 1_200_000L), amounts)
        assertTrue(transactionDao.allRows.all { it.type == TransactionType.INCOME })
    }

    @Test
    fun installmentEdit_unsafeCountChangeBlockedAfterGenerationBegins() = runBlocking {
        recurringPaymentRepository.createInstallmentPlan(
            title = "Laptop", categoryName = "SHOPPING", note = "", totalAmountInCents = 30_000L,
            totalInstallments = 6, firstPaymentDateMillis = millisFor(LocalDate.of(2026, 1, 15)), zoneId = zoneId
        )
        val planId = planDao.allRows.single().id
        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))
        val generatedCount = transactionRepository.countGeneratedForPlan(planId)
        assertEquals(3, generatedCount)

        // The repository itself is a defensive last gate; the ViewModel is expected to disable
        // these fields once generatedInstallmentCount > 0, but shrinking below what's already
        // generated (here: down to 2, fewer than the 3 already generated) must never succeed
        // even if called directly.
        val result = recurringPaymentRepository.updateInstallmentPlan(
            id = planId, title = "Laptop", categoryName = "SHOPPING", note = "",
            totalAmountInCents = 20_000L, totalInstallments = 2, generatedInstallmentCount = generatedCount
        )

        assertTrue(result.isFailure)
        assertEquals(6, planDao.allRows.single().totalInstallments)
    }

    @Test
    fun installmentEdit_safeMetadataEditsArePreserved() = runBlocking {
        recurringPaymentRepository.createInstallmentPlan(
            title = "Laptop", categoryName = "SHOPPING", note = "", totalAmountInCents = 30_000L,
            totalInstallments = 3, firstPaymentDateMillis = millisFor(LocalDate.of(2026, 1, 15)), zoneId = zoneId
        )
        val planId = planDao.allRows.single().id
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))

        recurringPaymentRepository.updateInstallmentPlan(
            id = planId, title = "Gaming Laptop", categoryName = "SHOPPING", note = "From Best Buy",
            totalAmountInCents = 30_000L, totalInstallments = 3
        )
        generator.generateDuePayments(today = LocalDate.of(2026, 2, 15))

        val updated = planDao.allRows.single()
        assertEquals("Gaming Laptop", updated.title)
        assertEquals("From Best Buy", updated.note)
        // Existing (first) installment keeps its original snapshot; only future ones use the edit.
        val titles = transactionDao.allRows.sortedBy { it.installmentNumber }.map { it.sourceTitle }
        assertEquals(listOf("Laptop", "Gaming Laptop"), titles)
    }
}

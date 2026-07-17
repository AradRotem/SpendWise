package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.RecurringOccurrenceExceptionRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Exercises RecurringOccurrenceManager's orchestration (edit-one, edit-and-future, delete-one,
// delete-and-future) against in-memory fake DAOs, including round-trips through
// RecurringPaymentGenerator to verify the regeneration-prevention guarantees actually hold
// end-to-end (not just that the right repository calls are made).
class RecurringOccurrenceManagerTest {

    private val zoneId = ZoneOffset.UTC
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var planDao: FakeRecurringPaymentPlanDao
    private lateinit var occurrenceExceptionDao: FakeRecurringOccurrenceExceptionDao
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var recurringPaymentRepository: RecurringPaymentRepository
    private lateinit var occurrenceExceptionRepository: RecurringOccurrenceExceptionRepository
    private lateinit var manager: RecurringOccurrenceManager
    private lateinit var generator: RecurringPaymentGenerator

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        planDao = FakeRecurringPaymentPlanDao()
        occurrenceExceptionDao = FakeRecurringOccurrenceExceptionDao()
        transactionRepository = TransactionRepository(transactionDao)
        recurringPaymentRepository = RecurringPaymentRepository(planDao)
        occurrenceExceptionRepository = RecurringOccurrenceExceptionRepository(occurrenceExceptionDao)
        manager = RecurringOccurrenceManager(transactionRepository, recurringPaymentRepository, occurrenceExceptionRepository, zoneId)
        generator = RecurringPaymentGenerator(recurringPaymentRepository, transactionRepository, occurrenceExceptionRepository, zoneId)
    }

    private fun millisFor(date: LocalDate) = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

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

    private suspend fun createInstallmentPlan(
        title: String = "Laptop",
        categoryName: String = "SHOPPING",
        totalAmountInCents: Long = 90_000L,
        totalInstallments: Int = 3,
        startDate: LocalDate = LocalDate.of(2026, 1, 15)
    ): Long {
        recurringPaymentRepository.createInstallmentPlan(
            title = title, categoryName = categoryName, note = "", totalAmountInCents = totalAmountInCents,
            totalInstallments = totalInstallments, firstPaymentDateMillis = millisFor(startDate), zoneId = zoneId
        )
        return planDao.allRows.single().id
    }

    private suspend fun insertManualTransaction(): TransactionEntity {
        val id = transactionRepository.insert(
            TransactionEntity(
                amountInCents = 2_000L, type = TransactionType.EXPENSE, category = "FOOD",
                timestamp = millisFor(LocalDate.of(2026, 1, 1))
            )
        )
        return transactionRepository.getById(id)!!
    }

    // --- editOccurrenceOnly ---------------------------------------------------------------------

    @Test
    fun editOccurrenceOnly_updatesFieldsAndMarksModified() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single()

        val result = manager.editOccurrenceOnly(
            transactionId = occurrence.id, title = "Rent (adjusted)", amountInCents = 5_500L,
            category = "UTILITIES", note = "one-off increase", timestamp = millisFor(LocalDate.of(2026, 1, 20))
        )

        assertTrue(result.isSuccess)
        val updated = transactionDao.allRows.single()
        assertEquals(5_500L, updated.amountInCents)
        assertEquals("UTILITIES", updated.category)
        assertEquals("one-off increase", updated.note)
        assertEquals("Rent (adjusted)", updated.sourceTitle)
        assertTrue(updated.isOccurrenceModified)
    }

    @Test
    fun editOccurrenceOnly_preservesPlanLinkageAndGeneratedFlags() = runBlocking {
        val planId = createInstallmentPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single()

        manager.editOccurrenceOnly(
            transactionId = occurrence.id, title = "Laptop", amountInCents = 40_000L,
            category = "ELECTRONICS", note = "", timestamp = occurrence.timestamp
        )

        val updated = transactionDao.allRows.single()
        assertEquals(planId, updated.recurringPlanId)
        assertEquals(occurrence.scheduledYearMonth, updated.scheduledYearMonth)
        assertEquals(occurrence.installmentNumber, updated.installmentNumber)
        assertEquals(occurrence.totalInstallments, updated.totalInstallments)
        assertTrue(updated.isAutomaticallyGenerated)
        assertEquals(TransactionType.EXPENSE, updated.type)
    }

    @Test
    fun editOccurrenceOnly_doesNotAffectOtherOccurrencesOfSamePlan() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))
        val first = transactionDao.allRows.sortedBy { it.scheduledYearMonth }.first()

        manager.editOccurrenceOnly(
            transactionId = first.id, title = "Rent (Jan only)", amountInCents = 9_999L,
            category = "HOUSING", note = "", timestamp = first.timestamp
        )

        val untouched = transactionDao.allRows.filter { it.id != first.id }
        assertTrue(untouched.all { it.amountInCents == 5_000L })
        assertTrue(untouched.all { !it.isOccurrenceModified })
    }

    @Test
    fun editOccurrenceOnly_rejectsDateOutsideScheduledMonth() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single()

        val result = manager.editOccurrenceOnly(
            transactionId = occurrence.id, title = "Rent", amountInCents = 5_000L,
            category = "HOUSING", note = "", timestamp = millisFor(LocalDate.of(2026, 2, 1))
        )

        assertTrue(result.isFailure)
        assertEquals(5_000L, transactionDao.allRows.single().amountInCents)
        assertFalse(transactionDao.allRows.single().isOccurrenceModified)
    }

    @Test
    fun editOccurrenceOnly_failsWhenTransactionMissing() = runBlocking {
        val result = manager.editOccurrenceOnly(
            transactionId = 999L, title = "Ghost", amountInCents = 1_000L,
            category = "OTHER", note = "", timestamp = millisFor(LocalDate.of(2026, 1, 1))
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun editOccurrenceOnly_skipsMonthCheckForManualTransactions() = runBlocking {
        val manual = insertManualTransaction()

        val result = manager.editOccurrenceOnly(
            transactionId = manual.id, title = "Groceries", amountInCents = 2_500L,
            category = "FOOD", note = "", timestamp = millisFor(LocalDate.of(2026, 6, 1))
        )

        assertTrue(result.isSuccess)
        assertEquals(2_500L, transactionDao.allRows.single().amountInCents)
    }

    // --- applyEditToOccurrenceAndFuture ---------------------------------------------------------

    @Test
    fun applyEditToOccurrenceAndFuture_monthlyPlan_updatesAnchorAndLaterUnmodifiedRows() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))
        val january = transactionDao.allRows.single { it.scheduledYearMonth == "2026-01" }

        val result = manager.applyEditToOccurrenceAndFuture(
            occurrenceTransactionId = january.id, title = "Rent (raised)", amountInCents = 6_000L,
            category = "HOUSING", note = "rent increase"
        )

        assertTrue(result.isSuccess)
        val updated = transactionDao.allRows
        assertTrue(updated.all { it.amountInCents == 6_000L })
        assertTrue(updated.all { it.sourceTitle == "Rent (raised)" })
        assertTrue(transactionDao.allRows.single { it.scheduledYearMonth == "2026-01" }.isOccurrenceModified)
    }

    @Test
    fun applyEditToOccurrenceAndFuture_monthlyPlan_doesNotTouchEarlierMonths() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))
        val february = transactionDao.allRows.single { it.scheduledYearMonth == "2026-02" }

        manager.applyEditToOccurrenceAndFuture(
            occurrenceTransactionId = february.id, title = "Rent (raised)", amountInCents = 6_000L,
            category = "HOUSING", note = ""
        )

        val january = transactionDao.allRows.single { it.scheduledYearMonth == "2026-01" }
        assertEquals(5_000L, january.amountInCents)
        assertEquals("Rent", january.sourceTitle)
        assertFalse(january.isOccurrenceModified)
    }

    @Test
    fun applyEditToOccurrenceAndFuture_installmentPlan_leavesAmountUntouched() = runBlocking {
        createInstallmentPlan(totalInstallments = 3, totalAmountInCents = 90_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))
        val originalAmounts = transactionDao.allRows.associate { it.id to it.amountInCents }
        val firstInstallment = transactionDao.allRows.single { it.installmentNumber == 1 }

        val result = manager.applyEditToOccurrenceAndFuture(
            occurrenceTransactionId = firstInstallment.id, title = "Laptop (renamed)", amountInCents = null,
            category = "ELECTRONICS", note = "bought from a different store"
        )

        assertTrue(result.isSuccess)
        transactionDao.allRows.forEach { row -> assertEquals(originalAmounts.getValue(row.id), row.amountInCents) }
        assertTrue(transactionDao.allRows.all { it.sourceTitle == "Laptop (renamed)" })
        assertTrue(transactionDao.allRows.all { it.category == "ELECTRONICS" })
    }

    @Test
    fun applyEditToOccurrenceAndFuture_skipsIndividuallyOverriddenLaterRows() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))
        val january = transactionDao.allRows.single { it.scheduledYearMonth == "2026-01" }
        val february = transactionDao.allRows.single { it.scheduledYearMonth == "2026-02" }

        // February was already individually edited by the user before this plan-wide edit runs.
        manager.editOccurrenceOnly(
            transactionId = february.id, title = "Rent (Feb special)", amountInCents = 4_000L,
            category = "HOUSING", note = "", timestamp = february.timestamp
        )

        manager.applyEditToOccurrenceAndFuture(
            occurrenceTransactionId = january.id, title = "Rent (raised)", amountInCents = 6_000L,
            category = "HOUSING", note = ""
        )

        val updatedFebruary = transactionDao.allRows.single { it.scheduledYearMonth == "2026-02" }
        assertEquals(4_000L, updatedFebruary.amountInCents)
        assertEquals("Rent (Feb special)", updatedFebruary.sourceTitle)

        val updatedMarch = transactionDao.allRows.single { it.scheduledYearMonth == "2026-03" }
        assertEquals(6_000L, updatedMarch.amountInCents)
    }

    @Test
    fun applyEditToOccurrenceAndFuture_failsForManualTransaction() = runBlocking {
        val manual = insertManualTransaction()

        val result = manager.applyEditToOccurrenceAndFuture(
            occurrenceTransactionId = manual.id, title = "Groceries", amountInCents = 2_500L,
            category = "FOOD", note = ""
        )

        assertTrue(result.isFailure)
    }

    // --- deleteOccurrenceOnly --------------------------------------------------------------------

    @Test
    fun deleteOccurrenceOnly_removesTheRowAndRecordsException() = runBlocking {
        val planId = createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single()

        val result = manager.deleteOccurrenceOnly(occurrence)

        assertTrue(result.isSuccess)
        assertTrue(transactionDao.allRows.isEmpty())
        assertEquals(setOf("2026-01"), occurrenceExceptionRepository.getSkippedMonths(planId))
    }

    @Test
    fun deleteOccurrenceOnly_preventsRegenerationOnNextCatchUp() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single()
        manager.deleteOccurrenceOnly(occurrence)

        val regeneratedCount = generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))

        // February and March are new due months; January must stay skipped forever.
        assertEquals(2, regeneratedCount)
        assertEquals(listOf("2026-02", "2026-03"), transactionDao.allRows.mapNotNull { it.scheduledYearMonth }.sorted())
    }

    @Test
    fun deleteOccurrenceOnly_failsForManualTransaction() = runBlocking {
        val manual = insertManualTransaction()

        val result = manager.deleteOccurrenceOnly(manual)

        assertTrue(result.isFailure)
        assertEquals(1, transactionDao.allRows.size)
    }

    @Test
    fun deleteOccurrenceOnly_repeatedDeleteStaysIdempotent() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single()

        manager.deleteOccurrenceOnly(occurrence)
        val secondResult = manager.deleteOccurrenceOnly(occurrence)

        assertTrue(secondResult.isSuccess)
        assertEquals(1, occurrenceExceptionDao.allRows.size)
    }

    // --- countLaterGeneratedTransactions ----------------------------------------------------------

    @Test
    fun countLaterGeneratedTransactions_countsOnlyStrictlyLaterMonths() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 4, 15))
        val january = transactionDao.allRows.single { it.scheduledYearMonth == "2026-01" }

        val count = manager.countLaterGeneratedTransactions(january)

        assertEquals(3, count) // Feb, Mar, Apr
    }

    @Test
    fun countLaterGeneratedTransactions_isZeroForTheLastOccurrence() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 3, 15))
        val march = transactionDao.allRows.single { it.scheduledYearMonth == "2026-03" }

        assertEquals(0, manager.countLaterGeneratedTransactions(march))
    }

    @Test
    fun countLaterGeneratedTransactions_isZeroForManualTransaction() = runBlocking {
        val manual = insertManualTransaction()

        assertEquals(0, manager.countLaterGeneratedTransactions(manual))
    }

    // --- deleteThisAndFuture ----------------------------------------------------------------------

    @Test
    fun deleteThisAndFuture_monthlyPlan_removesSelectedAndLaterKeepsEarlier() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 4, 15))
        val february = transactionDao.allRows.single { it.scheduledYearMonth == "2026-02" }

        val result = manager.deleteThisAndFuture(february)

        assertTrue(result.isSuccess)
        assertEquals(listOf("2026-01"), transactionDao.allRows.map { it.scheduledYearMonth })
    }

    @Test
    fun deleteThisAndFuture_monthlyPlan_stopsPlanWithEndDateCappingBeforeSelectedMonth() = runBlocking {
        val planId = createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 4, 15))
        val february = transactionDao.allRows.single { it.scheduledYearMonth == "2026-02" }

        manager.deleteThisAndFuture(february)

        val plan = planDao.allRows.single { it.id == planId }
        assertEquals(RecurringPlanStatus.STOPPED, plan.status)
        val cappedDate = Instant.ofEpochMilli(plan.endDateMillis!!).atZone(zoneId).toLocalDate()
        assertEquals(LocalDate.of(2026, 1, 31), cappedDate)
    }

    @Test
    fun deleteThisAndFuture_monthlyPlan_survivesLaterRestoreWithoutRegeneratingDeletedMonths() = runBlocking {
        createMonthlyPlan()
        generator.generateDuePayments(today = LocalDate.of(2026, 4, 15))
        val february = transactionDao.allRows.single { it.scheduledYearMonth == "2026-02" }
        manager.deleteThisAndFuture(february)
        val planId = planDao.allRows.single().id

        // A later Restore/Resume must not let Feb/Mar/Apr come back.
        recurringPaymentRepository.resume(planId)
        val regeneratedCount = generator.generateDuePayments(today = LocalDate.of(2026, 6, 15))

        assertEquals(0, regeneratedCount)
        assertEquals(listOf("2026-01"), transactionDao.allRows.map { it.scheduledYearMonth })
    }

    @Test
    fun deleteThisAndFuture_installmentPlan_removesSelectedAndLaterCapsInstallmentCount() = runBlocking {
        val planId = createInstallmentPlan(totalInstallments = 5, totalAmountInCents = 100_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 12, 1))
        val secondInstallment = transactionDao.allRows.single { it.installmentNumber == 2 }

        val result = manager.deleteThisAndFuture(secondInstallment)

        assertTrue(result.isSuccess)
        assertEquals(listOf(1), transactionDao.allRows.map { it.installmentNumber })
        val plan = planDao.allRows.single { it.id == planId }
        assertEquals(1, plan.totalInstallments)
        assertEquals(RecurringPlanStatus.STOPPED, plan.status)
    }

    @Test
    fun deleteThisAndFuture_installmentPlan_survivesLaterRestoreWithoutRegeneratingRemovedInstallments() = runBlocking {
        createInstallmentPlan(totalInstallments = 5, totalAmountInCents = 100_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 12, 1))
        val secondInstallment = transactionDao.allRows.single { it.installmentNumber == 2 }
        manager.deleteThisAndFuture(secondInstallment)
        val planId = planDao.allRows.single().id

        recurringPaymentRepository.resume(planId)
        val regeneratedCount = generator.generateDuePayments(today = LocalDate.of(2027, 1, 1))

        assertEquals(0, regeneratedCount)
        assertEquals(listOf(1), transactionDao.allRows.map { it.installmentNumber })
    }

    @Test
    fun deleteThisAndFuture_deletingFirstInstallment_capsTotalInstallmentsAtZero() = runBlocking {
        val planId = createInstallmentPlan(totalInstallments = 3, totalAmountInCents = 90_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 12, 1))
        val firstInstallment = transactionDao.allRows.single { it.installmentNumber == 1 }

        manager.deleteThisAndFuture(firstInstallment)

        assertTrue(transactionDao.allRows.isEmpty())
        val plan = planDao.allRows.single { it.id == planId }
        assertEquals(0, plan.totalInstallments)
        assertEquals(RecurringPlanStatus.STOPPED, plan.status)
    }

    @Test
    fun deleteThisAndFuture_failsForManualTransaction() = runBlocking {
        val manual = insertManualTransaction()

        val result = manager.deleteThisAndFuture(manual)

        assertTrue(result.isFailure)
        assertEquals(1, transactionDao.allRows.size)
    }

    // --- Completed installment deleted-history display (final Step 11 clarification) -----------

    @Test
    fun completedInstallmentPlan_deletingOccurrence_staysCompletedAndIsNotRecreated() = runBlocking {
        val planId = createInstallmentPlan(totalInstallments = 3, totalAmountInCents = 90_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 12, 1))
        assertEquals(RecurringPlanStatus.COMPLETED, planDao.allRows.single { it.id == planId }.status)
        val secondInstallment = transactionDao.allRows.single { it.installmentNumber == 2 }

        val result = manager.deleteOccurrenceOnly(secondInstallment)

        assertTrue(result.isSuccess)
        val plan = planDao.allRows.single { it.id == planId }
        // Historical-only: status, and the installment count itself, are untouched by a plain
        // single-occurrence delete (unlike deleteThisAndFuture, which deliberately caps both).
        assertEquals(RecurringPlanStatus.COMPLETED, plan.status)
        assertEquals(3, plan.totalInstallments)

        // getActivePlans() excludes COMPLETED plans, so the deleted installment is never
        // reconsidered as an outstanding payment on any later generation run.
        val regeneratedCount = generator.generateDuePayments(today = LocalDate.of(2027, 6, 1))
        assertEquals(0, regeneratedCount)
        assertEquals(listOf(1, 3), transactionDao.allRows.mapNotNull { it.installmentNumber }.sorted())
        assertEquals(RecurringPlanStatus.COMPLETED, planDao.allRows.single { it.id == planId }.status)
    }

    @Test
    fun editingOccurrence_doesNotCreateExceptionRecord() = runBlocking {
        val planId = createInstallmentPlan(totalInstallments = 3, totalAmountInCents = 90_000L)
        generator.generateDuePayments(today = LocalDate.of(2026, 12, 1))
        val firstInstallment = transactionDao.allRows.single { it.installmentNumber == 1 }

        manager.editOccurrenceOnly(
            transactionId = firstInstallment.id, title = "Laptop (renamed)", amountInCents = 25_000L,
            category = "ELECTRONICS", note = "", timestamp = firstInstallment.timestamp
        )

        // An edited-but-not-deleted occurrence must never be mistaken for a deleted one: it still
        // exists as a transaction row and has no exception record.
        assertTrue(occurrenceExceptionDao.getForPlan(planId).isEmpty())
        assertEquals(3, transactionDao.allRows.size)
    }

    @Test
    fun deleteThisAndFuture_failsWhenPlanNoLongerExists() = runBlocking {
        // A generated-looking transaction whose plan is gone (mirrors deletingPlan_preserves
        // HistoricalGeneratedTransactions in RecurringPaymentGeneratorTest: deleting a plan leaves
        // its transactions in place with a now-dangling recurringPlanId).
        val id = transactionRepository.insert(
            TransactionEntity(
                amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "HOUSING",
                timestamp = millisFor(LocalDate.of(2026, 1, 1)), recurringPlanId = 999L,
                isAutomaticallyGenerated = true, scheduledYearMonth = "2026-01"
            )
        )
        val orphaned = transactionRepository.getById(id)!!

        val result = manager.deleteThisAndFuture(orphaned)

        assertTrue(result.isFailure)
        assertEquals(1, transactionDao.allRows.size)
    }
}

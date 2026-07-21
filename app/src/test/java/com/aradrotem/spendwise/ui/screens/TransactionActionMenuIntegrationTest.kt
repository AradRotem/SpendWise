package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.RecurringOccurrenceExceptionRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.FakeRecurringOccurrenceExceptionDao
import com.aradrotem.spendwise.domain.FakeRecurringPaymentPlanDao
import com.aradrotem.spendwise.domain.FakeTransactionDao
import com.aradrotem.spendwise.domain.RecurringPaymentGenerator
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Exercises "Manage installment plan" visibility through the REAL production call chain -
// RecurringPaymentRepository.createInstallmentPlan(), RecurringPaymentGenerator.generateDuePayments(),
// then the exact two lines TransactionsViewModel.loadActionMenuInfo() runs
// (transaction.recurringPlanId?.let { repo.getById(it) } followed by resolveGeneratedTransactionActionInfo)
// - rather than only the isolated pure-function scenarios in TransactionsLogicTest.
// TransactionsViewModel itself can't be instantiated in a plain JVM test (viewModelScope needs a
// Main dispatcher unavailable here), so loadActionMenuInfo's body is replicated verbatim against
// the same repository instances instead.
//
// A Logcat-diagnosed real report ("Manage installment plan is missing") turned out not to be a
// bug: the transaction's recurringPlanId genuinely pointed at a deleted plan, while a different,
// unrelated plan happened to share its title. planExists = false was the correct answer. The
// tests below cover both the normal case (plan still exists, in every status) and that specific
// orphaned-reference case, to lock in that a same-titled plan is never used as a fallback.
class TransactionActionMenuIntegrationTest {

    private val zoneId = ZoneOffset.UTC
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var planDao: FakeRecurringPaymentPlanDao
    private lateinit var occurrenceExceptionDao: FakeRecurringOccurrenceExceptionDao
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var recurringPaymentRepository: RecurringPaymentRepository
    private lateinit var occurrenceExceptionRepository: RecurringOccurrenceExceptionRepository
    private lateinit var generator: RecurringPaymentGenerator

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        planDao = FakeRecurringPaymentPlanDao()
        occurrenceExceptionDao = FakeRecurringOccurrenceExceptionDao()
        transactionRepository = TransactionRepository(transactionDao)
        recurringPaymentRepository = RecurringPaymentRepository(planDao)
        occurrenceExceptionRepository = RecurringOccurrenceExceptionRepository(occurrenceExceptionDao)
        generator = RecurringPaymentGenerator(recurringPaymentRepository, transactionRepository, occurrenceExceptionRepository, zoneId)
    }

    private fun millisFor(date: LocalDate) = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    private suspend fun createInstallmentPlan(
        title: String,
        totalInstallments: Int,
        totalAmountInCents: Long,
        startDate: LocalDate
    ): Long {
        recurringPaymentRepository.createInstallmentPlan(
            title = title, categoryName = "SHOPPING", note = "", totalAmountInCents = totalAmountInCents,
            totalInstallments = totalInstallments, firstPaymentDateMillis = millisFor(startDate), zoneId = zoneId
        )
        return planDao.allRows.single { it.title == title }.id
    }

    // Exactly TransactionsViewModel.loadActionMenuInfo()'s body, against the same repository.
    private suspend fun loadActionMenuInfo(transaction: TransactionEntity): GeneratedTransactionActionInfo {
        val plan = transaction.recurringPlanId?.let { recurringPaymentRepository.getById(it) }
        return resolveGeneratedTransactionActionInfo(transaction, plan)
    }

    // --- "School" scenario from the report: first installment of a fresh, active plan -----------

    @Test
    fun freshInstallmentPlan_firstInstallment_showsManagePlan() = runBlocking {
        val planId = createInstallmentPlan("School", totalInstallments = 6, totalAmountInCents = 60_000L, startDate = LocalDate.of(2026, 7, 2))
        generator.generateDuePayments(today = LocalDate.of(2026, 7, 2))
        val occurrence = transactionDao.allRows.single { it.installmentNumber == 1 }

        val info = loadActionMenuInfo(occurrence)

        assertEquals(planId, occurrence.recurringPlanId)
        assertTrue("planExists should be true right after generation", info.planExists)
        assertTrue(info.isInstallmentOccurrence)
    }

    // --- "ביטוח רכב" scenario from the report: 11 of 12 installments already generated -----------

    @Test
    fun installmentPlan_elevenOfTwelveGenerated_lastGeneratedOccurrenceStillShowsManagePlan() = runBlocking {
        val planId = createInstallmentPlan(
            "Car insurance", totalInstallments = 12, totalAmountInCents = 6_000_00L, startDate = LocalDate.of(2025, 9, 5)
        )
        // 11 monthly installments due by 2026-07-05 (Sep'25 through Jul'26); the 12th (Aug'26) is
        // not yet due, matching the screenshot's "Installment 11 of 12".
        generator.generateDuePayments(today = LocalDate.of(2026, 7, 20))
        val generated = transactionDao.allRows.filter { it.recurringPlanId == planId }
        assertEquals(11, generated.size)
        val eleventh = generated.single { it.installmentNumber == 11 }

        val info = loadActionMenuInfo(eleventh)

        assertEquals(planId, eleventh.recurringPlanId)
        val planStatus = planDao.allRows.single { it.id == planId }.status
        assertEquals("plan should still be ACTIVE with one installment left", RecurringPlanStatus.ACTIVE, planStatus)
        assertTrue("planExists should be true for the 11th of 12 installments", info.planExists)
        assertTrue(info.isInstallmentOccurrence)
    }

    // --- Every occurrence of a still-existing plan must resolve, across every plan status ---------

    @Test
    fun installmentOccurrence_throughFullPipeline_resolvesForEveryLiveOrTerminalPlanStatus() = runBlocking {
        val planId = createInstallmentPlan("Laptop", totalInstallments = 3, totalAmountInCents = 90_000L, startDate = LocalDate.of(2026, 1, 15))
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 15))
        val occurrence = transactionDao.allRows.single { it.recurringPlanId == planId }

        for (status in RecurringPlanStatus.entries) {
            planDao.updateStatus(planId, status)

            val info = loadActionMenuInfo(occurrence)

            assertTrue("planExists must be true when the plan row exists with status $status", info.planExists)
            assertTrue(info.isInstallmentOccurrence)
        }
    }

    // --- Fully completed plan (all installments generated) ------------------------------------------

    @Test
    fun installmentPlan_fullyCompleted_lastOccurrenceStillShowsManagePlan() = runBlocking {
        val planId = createInstallmentPlan("Phone", totalInstallments = 3, totalAmountInCents = 90_000L, startDate = LocalDate.of(2026, 1, 15))
        generator.generateDuePayments(today = LocalDate.of(2026, 12, 1)) // generates all 3, auto-completes the plan
        val plan = planDao.allRows.single { it.id == planId }
        assertEquals(RecurringPlanStatus.COMPLETED, plan.status)
        val lastInstallment = transactionDao.allRows.single { it.installmentNumber == 3 }

        val info = loadActionMenuInfo(lastInstallment)

        assertTrue("planExists must be true even though the plan auto-completed", info.planExists)
    }

    // --- Genuinely deleted plan: the only case allowed to hide the button --------------------------

    @Test
    fun installmentOccurrence_planRowActuallyDeleted_hidesManagePlan() = runBlocking {
        val planId = createInstallmentPlan("Furniture", totalInstallments = 2, totalAmountInCents = 40_000L, startDate = LocalDate.of(2026, 1, 15))
        generator.generateDuePayments(today = LocalDate.of(2026, 12, 1))
        val occurrence = transactionDao.allRows.first { it.recurringPlanId == planId }
        val plan = planDao.allRows.single { it.id == planId }

        planDao.delete(plan)

        val info = loadActionMenuInfo(occurrence)

        assertFalse("planExists must be false once the plan row is actually gone", info.planExists)
        assertTrue(info.isInstallmentOccurrence)
    }

    // --- Data-compatibility: an occurrence whose recurringPlanId doesn't match any stored plan ----
    // (e.g. legacy/malformed data, or a plan that was deleted and a new unrelated one created with
    // the same title) must be handled safely - never crash, and correctly report no manageable plan.

    @Test
    fun installmentOccurrence_withOrphanedRecurringPlanId_handledSafelyNoCrash() = runBlocking {
        val orphan = TransactionEntity(
            amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "SHOPPING",
            timestamp = millisFor(LocalDate.of(2026, 1, 1)), recurringPlanId = 999_999L,
            installmentNumber = 2, totalInstallments = 6, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-01"
        )
        transactionRepository.insert(orphan)
        val stored = transactionDao.allRows.single()

        val info = loadActionMenuInfo(stored)

        assertFalse("an id with no matching plan row must resolve to planExists = false, not crash", info.planExists)
        assertTrue(info.isInstallmentOccurrence)
    }

    // --- Confirmed real-world scenario (Logcat diagnosis): the original installment plan was
    // deleted, and a later, unrelated plan happens to share its title. That plan must never be
    // used as a fallback - matching must be strictly by persisted id, never by title. -------------

    @Test
    fun orphanedInstallmentOccurrence_neverFallsBackToADifferentPlanWithTheSameTitle() = runBlocking {
        val deletedPlanId = createInstallmentPlan(
            "Car insurance", totalInstallments = 12, totalAmountInCents = 6_000_00L, startDate = LocalDate.of(2025, 9, 5)
        )
        generator.generateDuePayments(today = LocalDate.of(2026, 7, 20)) // generates 11 of 12
        val occurrence = transactionDao.allRows.single { it.installmentNumber == 11 && it.recurringPlanId == deletedPlanId }
        val deletedPlan = planDao.allRows.single { it.id == deletedPlanId }

        // The original plan is deleted (its historical transactions deliberately survive - see
        // RecurringPaymentRepository.deletePlan), and a later, completely unrelated plan is
        // created with the exact same title but a different id and a different type - exactly
        // what the Logcat diagnosis found on the real device (deleted id=6 INSTALLMENT
        // "ביטוח רכב", replaced by a live id=8 MONTHLY_RECURRING plan of the same name).
        planDao.delete(deletedPlan)
        val unrelatedPlanId = createInstallmentPlan(
            "Car insurance", totalInstallments = 1, totalAmountInCents = 100L, startDate = LocalDate.of(2026, 8, 1)
        )
        assertTrue("the replacement plan must genuinely be a different id", unrelatedPlanId != deletedPlanId)

        val info = loadActionMenuInfo(occurrence)

        // Must resolve as if no plan exists at all - never silently adopt the same-titled plan.
        assertFalse("an orphaned occurrence must not fall back to a same-titled plan", info.planExists)
        assertTrue(info.isInstallmentOccurrence)
        // The occurrence's own persisted recurringPlanId is untouched - it still points at the
        // deleted plan's id, not the unrelated replacement.
        assertEquals(deletedPlanId, occurrence.recurringPlanId)
    }

    @Test
    fun orphanedInstallmentOccurrence_neverFallsBackEvenWhenReplacementIsSameTypeToo() = runBlocking {
        val deletedPlanId = createInstallmentPlan("Course", totalInstallments = 3, totalAmountInCents = 30_000L, startDate = LocalDate.of(2026, 1, 1))
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 1))
        val occurrence = transactionDao.allRows.single { it.recurringPlanId == deletedPlanId }
        planDao.delete(planDao.allRows.single { it.id == deletedPlanId })

        // Even a same-title, same-type replacement (the closest possible false-positive match)
        // must not be adopted - only an exact id match is ever acceptable.
        val replacementId = createInstallmentPlan("Course", totalInstallments = 3, totalAmountInCents = 30_000L, startDate = LocalDate.of(2026, 6, 1))
        assertTrue(replacementId != deletedPlanId)

        val info = loadActionMenuInfo(occurrence)

        assertFalse(info.planExists)
        assertEquals(deletedPlanId, occurrence.recurringPlanId)
    }

    // --- Data-compatibility: an old/malformed occurrence with no recurringPlanId at all ------------

    @Test
    fun installmentLikeOccurrence_withNullRecurringPlanId_handledSafelyNoCrash() = runBlocking {
        val malformed = TransactionEntity(
            amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "SHOPPING",
            timestamp = millisFor(LocalDate.of(2026, 1, 1)), recurringPlanId = null,
            installmentNumber = 2, totalInstallments = 6, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-01"
        )
        transactionRepository.insert(malformed)
        val stored = transactionDao.allRows.single()

        val info = loadActionMenuInfo(stored)

        assertFalse(info.planExists)
        // Still routed to the installment menu (installmentNumber is set) - "Manage installment
        // plan" is correctly hidden, "Advanced occurrence actions" remains available.
        assertTrue(info.isInstallmentOccurrence)
    }

    // --- Navigation correctness: the id passed to onOpenRecurringPlan must be the real plan id ----

    @Test
    fun managePlanNavigation_usesTheOccurrencesOwnRecurringPlanId_notAnyOtherPlan() = runBlocking {
        createInstallmentPlan("Other purchase", totalInstallments = 2, totalAmountInCents = 20_000L, startDate = LocalDate.of(2026, 1, 1))
        val targetPlanId = createInstallmentPlan("Target purchase", totalInstallments = 2, totalAmountInCents = 20_000L, startDate = LocalDate.of(2026, 1, 1))
        generator.generateDuePayments(today = LocalDate.of(2026, 1, 1))
        val occurrence = transactionDao.allRows.single { it.recurringPlanId == targetPlanId }

        val info = loadActionMenuInfo(occurrence)

        assertTrue(info.planExists)
        assertEquals(targetPlanId, occurrence.recurringPlanId)
        assertEquals(targetPlanId, planDao.allRows.single { it.id == occurrence.recurringPlanId }.id)
    }
}

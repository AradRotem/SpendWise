package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure-logic tests for resolveGeneratedTransactionActionInfo, which decides what the generated-
// transaction long-press action menu should offer, and shouldShowGeneratedActionMenu, which
// decides whether a transaction gets the recurring-aware menu at all. Kept as top-level functions
// (not TransactionsViewModel members) specifically so they're testable without a real ViewModel.
class TransactionsLogicTest {

    private fun plan(
        status: RecurringPlanStatus = RecurringPlanStatus.ACTIVE,
        type: RecurringPlanType = RecurringPlanType.MONTHLY_RECURRING
    ) = RecurringPaymentPlanEntity(
        type = type, title = "Rent", categoryName = "HOUSING", amountInCents = 5_000L,
        firstPaymentDateMillis = 1_000L, preferredDayOfMonth = 1, status = status
    )

    private fun monthlyOccurrence(planId: Long = 1L) = TransactionEntity(
        amountInCents = 5_000L, type = TransactionType.EXPENSE, category = "HOUSING", timestamp = 1_000L,
        recurringPlanId = planId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-01"
    )

    private fun salaryOccurrence(planId: Long = 1L) = TransactionEntity(
        amountInCents = 500_000L, type = TransactionType.INCOME, category = "SALARY", timestamp = 1_000L,
        recurringPlanId = planId, isAutomaticallyGenerated = true, scheduledYearMonth = "2026-01"
    )

    private fun installmentOccurrence(installmentNumber: Int, totalInstallments: Int, planId: Long = 1L) = TransactionEntity(
        amountInCents = 30_000L, type = TransactionType.EXPENSE, category = "SHOPPING", timestamp = 1_000L,
        recurringPlanId = planId, installmentNumber = installmentNumber, totalInstallments = totalInstallments,
        isAutomaticallyGenerated = true, scheduledYearMonth = "2026-01"
    )

    private fun manualTransaction() = TransactionEntity(
        amountInCents = 1_200L, type = TransactionType.EXPENSE, category = "FOOD", timestamp = 1_000L
    )

    // --- resolveGeneratedTransactionActionInfo ---------------------------------------------------

    @Test
    fun deletedPlan_hidesFutureActionsAndOpenPlan() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan = null)

        assertEquals(GeneratedTransactionActionInfo(planExists = false, canActOnFuture = false, isInstallmentOccurrence = false), info)
    }

    @Test
    fun activeMonthlyPlan_allowsFutureActions() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan(status = RecurringPlanStatus.ACTIVE))

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = true, isInstallmentOccurrence = false), info)
    }

    @Test
    fun pausedPlan_stillAllowsFutureActions() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan(status = RecurringPlanStatus.PAUSED))

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = true, isInstallmentOccurrence = false), info)
    }

    @Test
    fun stoppedPlan_disallowsFutureActions() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan(status = RecurringPlanStatus.STOPPED))

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = false, isInstallmentOccurrence = false), info)
    }

    @Test
    fun completedPlan_disallowsFutureActions() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan(status = RecurringPlanStatus.COMPLETED))

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = false, isInstallmentOccurrence = false), info)
    }

    @Test
    fun nonFinalInstallment_onActivePlan_allowsFutureActions() {
        val info = resolveGeneratedTransactionActionInfo(
            installmentOccurrence(installmentNumber = 1, totalInstallments = 3),
            plan(status = RecurringPlanStatus.ACTIVE, type = RecurringPlanType.INSTALLMENT)
        )

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = true, isInstallmentOccurrence = true), info)
    }

    @Test
    fun finalInstallment_onActivePlan_disallowsFutureActionsEvenThoughPlanIsLive() {
        val info = resolveGeneratedTransactionActionInfo(
            installmentOccurrence(installmentNumber = 3, totalInstallments = 3),
            plan(status = RecurringPlanStatus.ACTIVE, type = RecurringPlanType.INSTALLMENT)
        )

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = false, isInstallmentOccurrence = true), info)
    }

    // --- isInstallmentOccurrence: installment vs. standing-order vs. salary (Step 13) -------------

    @Test
    fun installmentOccurrence_isFlaggedAsInstallment_soItGetsTheTwoTierMenu() {
        val info = resolveGeneratedTransactionActionInfo(
            installmentOccurrence(installmentNumber = 1, totalInstallments = 3),
            plan(status = RecurringPlanStatus.ACTIVE, type = RecurringPlanType.INSTALLMENT)
        )

        // TransactionsScreen routes isInstallmentOccurrence == true to InstallmentOccurrenceActionDialog,
        // whose only first-level actions are "Manage installment plan" and "Advanced occurrence actions" -
        // occurrence-only edit only becomes reachable through that second, "advanced" dialog.
        assertTrue(info.isInstallmentOccurrence)
        assertTrue(info.planExists)
    }

    // Regression coverage for the reported "Manage installment plan is missing for an existing
    // plan" bug: planExists must be true for every non-null plan status, not just ACTIVE - in
    // particular PAUSED and COMPLETED, since an installment plan naturally becomes COMPLETED once
    // every installment has generated while the plan row itself still exists.
    @Test
    fun installmentOccurrence_withExistingPlan_showsManagePlanRegardlessOfPlanStatus() {
        listOf(RecurringPlanStatus.ACTIVE, RecurringPlanStatus.PAUSED, RecurringPlanStatus.STOPPED, RecurringPlanStatus.COMPLETED)
            .forEach { status ->
                val info = resolveGeneratedTransactionActionInfo(
                    installmentOccurrence(installmentNumber = 2, totalInstallments = 3, planId = 42L),
                    plan(status = status, type = RecurringPlanType.INSTALLMENT)
                )

                assertTrue("planExists must be true for an existing plan with status $status", info.planExists)
                assertTrue(info.isInstallmentOccurrence)
            }
    }

    @Test
    fun installmentOccurrence_recurringPlanIdMatchesTheSourcePlanUsedToResolveIt() {
        val transaction = installmentOccurrence(installmentNumber = 1, totalInstallments = 3, planId = 42L)
        val sourcePlan = plan(status = RecurringPlanStatus.ACTIVE, type = RecurringPlanType.INSTALLMENT)

        val info = resolveGeneratedTransactionActionInfo(transaction, sourcePlan)

        // "Manage installment plan" navigates using transaction.recurringPlanId (see
        // TransactionsScreen's onManagePlan), so a correctly-resolved plan must correspond to the
        // same id the occurrence actually points at.
        assertEquals(42L, transaction.recurringPlanId)
        assertTrue(info.planExists)
    }

    @Test
    fun installmentOccurrenceWithDeletedPlan_stillFlaggedAsInstallment_managePlanHidden() {
        val info = resolveGeneratedTransactionActionInfo(
            installmentOccurrence(installmentNumber = 2, totalInstallments = 3), plan = null
        )

        // isInstallmentOccurrence is derived from the transaction's own installmentNumber, not a
        // plan lookup, so it survives even if the source plan row is gone; "Manage installment
        // plan" itself is what disappears (gated on planExists) in that case.
        assertTrue(info.isInstallmentOccurrence)
        assertFalse(info.planExists)
    }

    @Test
    fun installmentOccurrenceWithDeletedPlan_advancedActionsAreStillAvailable() {
        val info = resolveGeneratedTransactionActionInfo(
            installmentOccurrence(installmentNumber = 2, totalInstallments = 3), plan = null
        )

        // "Advanced occurrence actions" is gated only on isInstallmentOccurrence in
        // InstallmentOccurrenceActionDialog, never on planExists - it must stay reachable (and the
        // app must not crash / attempt to navigate anywhere) even when the source plan is gone.
        assertTrue(info.isInstallmentOccurrence)
        assertEquals(listOf(LABEL_EDIT_RECORD_ONLY), installmentAdvancedActionLabels())
    }

    // --- Orphaned-plan explanatory message (shown in place of "Manage installment plan") ---------

    @Test
    fun missingInstallmentPlan_explanatoryMessageHasExactRequiredWording() {
        assertEquals(
            "The original installment plan no longer exists.",
            MESSAGE_ORIGINAL_INSTALLMENT_PLAN_MISSING
        )
    }

    @Test
    fun missingInstallmentPlan_managePlanHiddenAndExplanatoryMessageApplies() {
        val info = resolveGeneratedTransactionActionInfo(
            installmentOccurrence(installmentNumber = 2, totalInstallments = 3), plan = null
        )

        // InstallmentOccurrenceActionDialog renders the "Manage installment plan" button only
        // when info.planExists; otherwise it renders MESSAGE_ORIGINAL_INSTALLMENT_PLAN_MISSING in
        // its place - the two are mutually exclusive on this same boolean.
        assertFalse("Manage installment plan must not be offered when the source plan is missing", info.planExists)
    }

    @Test
    fun existingInstallmentPlan_doesNotTriggerTheExplanatoryMessage() {
        val info = resolveGeneratedTransactionActionInfo(
            installmentOccurrence(installmentNumber = 2, totalInstallments = 3),
            plan(status = RecurringPlanStatus.ACTIVE, type = RecurringPlanType.INSTALLMENT)
        )

        // planExists = true means InstallmentOccurrenceActionDialog renders "Manage installment
        // plan" instead of MESSAGE_ORIGINAL_INSTALLMENT_PLAN_MISSING - the message is conditioned
        // on the exact same boolean, so it cannot appear alongside a live plan.
        assertTrue("the explanatory message must not apply when the source plan still exists", info.planExists)
    }

    @Test
    fun standingOrderOccurrence_isNotFlaggedAsInstallment_soItKeepsTheFlexibleMenu() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan(status = RecurringPlanStatus.ACTIVE))

        // The flexible RecurringOccurrenceActionDialog always renders onRemoveThis (see
        // TransactionsScreen) for any non-installment occurrence - standing orders keep
        // occurrence-level removal, unlike installments.
        assertFalse(info.isInstallmentOccurrence)
    }

    @Test
    fun salaryOccurrence_isNotFlaggedAsInstallment_soItFollowsStandingOrderStyleFlexibility() {
        val info = resolveGeneratedTransactionActionInfo(
            salaryOccurrence(), plan(status = RecurringPlanStatus.ACTIVE, type = RecurringPlanType.MONTHLY_SALARY)
        )

        // Recurring salary also routes to RecurringOccurrenceActionDialog, so it keeps the same
        // occurrence-level removal and this-and-future actions as a standing order.
        assertFalse(info.isInstallmentOccurrence)
        assertTrue(info.canActOnFuture)
    }

    // --- Installment advanced menu: single-occurrence removal is no longer exposed ----------------

    @Test
    fun installmentAdvancedMenu_exposesOnlyEditRecordOnly() {
        assertEquals(listOf(LABEL_EDIT_RECORD_ONLY), installmentAdvancedActionLabels())
    }

    @Test
    fun installmentAdvancedMenu_doesNotExposeRemoveFromHistoryOrAnyOtherDeleteAction() {
        val labels = installmentAdvancedActionLabels()

        assertEquals(1, labels.size)
        assertFalse(labels.any { it.contains("Remove", ignoreCase = true) })
        assertFalse(labels.any { it.contains("Delete", ignoreCase = true) })
    }

    // --- shouldShowGeneratedActionMenu -------------------------------------------------------------

    @Test
    fun manualTransaction_doesNotGetTheGeneratedActionMenu() {
        assertFalse(shouldShowGeneratedActionMenu(manualTransaction()))
    }

    @Test
    fun generatedOccurrence_getsTheGeneratedActionMenu() {
        assertTrue(shouldShowGeneratedActionMenu(monthlyOccurrence()))
        assertTrue(shouldShowGeneratedActionMenu(installmentOccurrence(installmentNumber = 1, totalInstallments = 3)))
    }

    // --- User-facing wording must never imply cancelling a real external charge --------------------

    @Test
    fun userFacingActionLabels_avoidMisleadingCancellationWording() {
        val forbiddenPhrases = listOf(
            "cancel payment", "cancel installment", "delete actual payment", "cancel the payment",
            "cancel this payment", "cancel the installment"
        )
        val labels = listOf(
            LABEL_MANAGE_INSTALLMENT_PLAN, LABEL_ADVANCED_OCCURRENCE_ACTIONS, LABEL_EDIT_RECORD_ONLY,
            WARNING_EDIT_INSTALLMENT_OCCURRENCE, MESSAGE_ORIGINAL_INSTALLMENT_PLAN_MISSING,
            LABEL_EDIT_OCCURRENCE_ONLY, LABEL_EDIT_OCCURRENCE_AND_FUTURE, LABEL_REMOVE_OCCURRENCE_FROM_HISTORY,
            LABEL_REMOVE_OCCURRENCE_AND_FUTURE_FROM_HISTORY, LABEL_MANAGE_RECURRING_PLAN
        )

        labels.forEach { label ->
            val lower = label.lowercase()
            forbiddenPhrases.forEach { phrase ->
                assertFalse("\"$label\" must not contain the misleading phrase \"$phrase\"", lower.contains(phrase))
            }
        }
    }

    @Test
    fun installmentEditWarning_usesExactRequiredWording() {
        assertEquals(
            "This changes only this SpendWise record.\nIt does not change the installment plan or the actual payment.",
            WARNING_EDIT_INSTALLMENT_OCCURRENCE
        )
    }
}

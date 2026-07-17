package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

// Pure-logic tests for resolveGeneratedTransactionActionInfo, which decides what the generated-
// transaction long-press action menu should offer. Kept as a top-level function (not a
// TransactionsViewModel member) specifically so it's testable without a real ViewModel.
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

    private fun installmentOccurrence(installmentNumber: Int, totalInstallments: Int, planId: Long = 1L) = TransactionEntity(
        amountInCents = 30_000L, type = TransactionType.EXPENSE, category = "SHOPPING", timestamp = 1_000L,
        recurringPlanId = planId, installmentNumber = installmentNumber, totalInstallments = totalInstallments,
        isAutomaticallyGenerated = true, scheduledYearMonth = "2026-01"
    )

    @Test
    fun deletedPlan_hidesFutureActionsAndOpenPlan() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan = null)

        assertEquals(GeneratedTransactionActionInfo(planExists = false, canActOnFuture = false), info)
    }

    @Test
    fun activeMonthlyPlan_allowsFutureActions() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan(status = RecurringPlanStatus.ACTIVE))

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = true), info)
    }

    @Test
    fun pausedPlan_stillAllowsFutureActions() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan(status = RecurringPlanStatus.PAUSED))

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = true), info)
    }

    @Test
    fun stoppedPlan_disallowsFutureActions() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan(status = RecurringPlanStatus.STOPPED))

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = false), info)
    }

    @Test
    fun completedPlan_disallowsFutureActions() {
        val info = resolveGeneratedTransactionActionInfo(monthlyOccurrence(), plan(status = RecurringPlanStatus.COMPLETED))

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = false), info)
    }

    @Test
    fun nonFinalInstallment_onActivePlan_allowsFutureActions() {
        val info = resolveGeneratedTransactionActionInfo(
            installmentOccurrence(installmentNumber = 1, totalInstallments = 3),
            plan(status = RecurringPlanStatus.ACTIVE, type = RecurringPlanType.INSTALLMENT)
        )

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = true), info)
    }

    @Test
    fun finalInstallment_onActivePlan_disallowsFutureActionsEvenThoughPlanIsLive() {
        val info = resolveGeneratedTransactionActionInfo(
            installmentOccurrence(installmentNumber = 3, totalInstallments = 3),
            plan(status = RecurringPlanStatus.ACTIVE, type = RecurringPlanType.INSTALLMENT)
        )

        assertEquals(GeneratedTransactionActionInfo(planExists = true, canActOnFuture = false), info)
    }
}

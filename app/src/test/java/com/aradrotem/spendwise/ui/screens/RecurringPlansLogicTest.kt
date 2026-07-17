package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.OccurrenceExceptionType
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import org.junit.Assert.assertEquals
import org.junit.Test

// Pure-logic tests for the Recurring Transactions list screen's Completed-installment display
// clarification: installmentProgressText (RecurringPaymentsScreen.kt) and
// deletedInstallmentOccurrenceCount (RecurringPlansViewModel.kt). Both are kept as top-level
// functions specifically so they're testable without a real ViewModel/Room database.
class RecurringPlansLogicTest {

    private fun exception(planId: Long, month: String, type: OccurrenceExceptionType = OccurrenceExceptionType.SKIPPED) =
        RecurringOccurrenceExceptionEntity(recurringPlanId = planId, scheduledYearMonth = month, exceptionType = type)

    // --- installmentProgressText ----------------------------------------------------------------

    @Test
    fun completed_noDeletions_showsNormalSingleLineText() {
        val lines = installmentProgressText(
            status = RecurringPlanStatus.COMPLETED, totalInstallments = 6,
            remainingGeneratedInstallments = 6, deletedInstallmentOccurrences = 0
        )

        assertEquals(listOf("6 of 6 payments"), lines)
    }

    @Test
    fun completed_oneDeletion_showsTwoLinesWithSingularWording() {
        val lines = installmentProgressText(
            status = RecurringPlanStatus.COMPLETED, totalInstallments = 6,
            remainingGeneratedInstallments = 5, deletedInstallmentOccurrences = 1
        )

        assertEquals(
            listOf("5 of 6 payments remain in history", "1 payment was deleted manually"),
            lines
        )
    }

    @Test
    fun completed_multipleDeletions_showsTwoLinesWithPluralWording() {
        val lines = installmentProgressText(
            status = RecurringPlanStatus.COMPLETED, totalInstallments = 6,
            remainingGeneratedInstallments = 4, deletedInstallmentOccurrences = 2
        )

        assertEquals(
            listOf("4 of 6 payments remain in history", "2 payments were deleted manually"),
            lines
        )
    }

    @Test
    fun completed_deletions_lineNumbersReflectHistoryNotOriginalTotal() {
        val lines = installmentProgressText(
            status = RecurringPlanStatus.COMPLETED, totalInstallments = 12,
            remainingGeneratedInstallments = 9, deletedInstallmentOccurrences = 3
        )

        assertEquals("9 of 12 payments remain in history", lines[0])
        assertEquals("3 payments were deleted manually", lines[1])
    }

    @Test
    fun activePlan_ignoresDeletedCount_showsNormalSingleLineText() {
        val withDeletions = installmentProgressText(
            status = RecurringPlanStatus.ACTIVE, totalInstallments = 6,
            remainingGeneratedInstallments = 2, deletedInstallmentOccurrences = 1
        )

        assertEquals(listOf("2 of 6 payments"), withDeletions)
    }

    @Test
    fun pausedPlan_ignoresDeletedCount_showsNormalSingleLineText() {
        val lines = installmentProgressText(
            status = RecurringPlanStatus.PAUSED, totalInstallments = 6,
            remainingGeneratedInstallments = 3, deletedInstallmentOccurrences = 2
        )

        assertEquals(listOf("3 of 6 payments"), lines)
    }

    @Test
    fun stoppedPlan_ignoresDeletedCount_showsNormalSingleLineText() {
        val lines = installmentProgressText(
            status = RecurringPlanStatus.STOPPED, totalInstallments = 6,
            remainingGeneratedInstallments = 2, deletedInstallmentOccurrences = 1
        )

        assertEquals(listOf("2 of 6 payments"), lines)
    }

    // --- deletedInstallmentOccurrenceCount ------------------------------------------------------

    @Test
    fun deletedInstallmentOccurrenceCount_countsOnlyExceptionsForGivenPlan() {
        val exceptions = listOf(
            exception(planId = 1L, month = "2026-01"),
            exception(planId = 1L, month = "2026-02"),
            exception(planId = 2L, month = "2026-01")
        )

        assertEquals(2, deletedInstallmentOccurrenceCount(1L, exceptions))
    }

    @Test
    fun deletedInstallmentOccurrenceCount_excludesExceptionsFromOtherPlans() {
        val exceptions = listOf(exception(planId = 999L, month = "2026-01"))

        assertEquals(0, deletedInstallmentOccurrenceCount(1L, exceptions))
    }

    @Test
    fun deletedInstallmentOccurrenceCount_zeroWhenNoExceptionsExist() {
        assertEquals(0, deletedInstallmentOccurrenceCount(1L, emptyList()))
    }
}

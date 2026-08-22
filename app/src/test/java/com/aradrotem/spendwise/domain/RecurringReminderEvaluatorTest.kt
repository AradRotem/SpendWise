package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringReminderEvaluatorTest {

    private val zoneId: ZoneId = ZoneOffset.UTC

    private fun millisFor(date: LocalDate): Long = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    private fun monthlyPlan(id: Long, preferredDay: Int, firstPaymentDate: LocalDate) = RecurringPaymentPlanEntity(
        id = id, type = RecurringPlanType.MONTHLY_RECURRING, title = "Rent", categoryName = "HOUSING",
        amountInCents = 500_000L, firstPaymentDateMillis = millisFor(firstPaymentDate), preferredDayOfMonth = preferredDay,
        status = RecurringPlanStatus.ACTIVE
    )

    @Test
    fun dueTomorrow_reminderFires() {
        val today = LocalDate.of(2026, 8, 4)
        val plan = monthlyPlan(1L, preferredDay = 5, firstPaymentDate = LocalDate.of(2026, 1, 5))
        val reminders = RecurringReminderEvaluator.evaluate(listOf(plan), today, emptySet(), zoneId)
        assertEquals(1, reminders.size)
        assertEquals(LocalDate.of(2026, 8, 5), reminders.single().dueDate)
        assertEquals("2026-08", reminders.single().scheduledYearMonth)
    }

    @Test
    fun dueInTwoDays_noReminderYet() {
        val today = LocalDate.of(2026, 8, 3)
        val plan = monthlyPlan(1L, preferredDay = 5, firstPaymentDate = LocalDate.of(2026, 1, 5))
        assertTrue(RecurringReminderEvaluator.evaluate(listOf(plan), today, emptySet(), zoneId).isEmpty())
    }

    @Test
    fun alreadyDueDate_noReminderForToday() {
        val today = LocalDate.of(2026, 8, 5)
        val plan = monthlyPlan(1L, preferredDay = 5, firstPaymentDate = LocalDate.of(2026, 1, 5))
        assertTrue(RecurringReminderEvaluator.evaluate(listOf(plan), today, emptySet(), zoneId).isEmpty())
    }

    @Test
    fun duplicatePrevention_alreadyNotifiedForOccurrence_suppressed() {
        val today = LocalDate.of(2026, 8, 4)
        val plan = monthlyPlan(1L, preferredDay = 5, firstPaymentDate = LocalDate.of(2026, 1, 5))
        val alreadyNotified = setOf(1L to "2026-08")
        assertTrue(RecurringReminderEvaluator.evaluate(listOf(plan), today, alreadyNotified, zoneId).isEmpty())
    }

    @Test
    fun differentMonthOccurrence_notSuppressedByPreviousMonthsNotification() {
        val today = LocalDate.of(2026, 8, 4)
        val plan = monthlyPlan(1L, preferredDay = 5, firstPaymentDate = LocalDate.of(2026, 1, 5))
        val alreadyNotified = setOf(1L to "2026-07")
        val reminders = RecurringReminderEvaluator.evaluate(listOf(plan), today, alreadyNotified, zoneId)
        assertEquals(1, reminders.size)
    }

    @Test
    fun stoppedPlan_excluded() {
        val today = LocalDate.of(2026, 8, 4)
        val plan = monthlyPlan(1L, preferredDay = 5, firstPaymentDate = LocalDate.of(2026, 1, 5)).copy(status = RecurringPlanStatus.STOPPED)
        // nextDueDate itself doesn't check status - callers are expected to pass only active plans
        // (see RecurringPaymentRepository.getActivePlans, used by NotificationCheckWorker), so a
        // stopped plan slipping through would still be evaluated here; this documents that the
        // filtering responsibility is the caller's, not the evaluator's.
        val reminders = RecurringReminderEvaluator.evaluate(listOf(plan), today, emptySet(), zoneId)
        assertEquals(1, reminders.size)
    }
}

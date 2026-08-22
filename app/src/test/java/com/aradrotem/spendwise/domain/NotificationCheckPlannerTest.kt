package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCheckPlannerTest {

    private val zoneId = ZoneOffset.UTC

    private fun millisFor(date: LocalDate): Long = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    @Test
    fun budgetAlertsDisabled_noBudgetAlertsEvenWhenThresholdCrossed() {
        val overBudgetPoint = BudgetActualCalculator.calculate("FOOD", 10_000L, 12_000L)
        val result = NotificationCheckPlanner.plan(
            NotificationCheckInput(
                budgetAlertsEnabled = false,
                recurringRemindersEnabled = false,
                budgetActualPoints = listOf(overBudgetPoint),
                alreadyNotifiedBudgetKeys = emptySet(),
                activePlans = emptyList(),
                today = LocalDate.of(2026, 8, 4),
                alreadyNotifiedReminderKeys = emptySet(),
                zoneId = zoneId
            )
        )
        assertTrue(result.budgetAlerts.isEmpty())
    }

    @Test
    fun recurringRemindersDisabled_noRemindersEvenWhenDueTomorrow() {
        val plan = RecurringPaymentPlanEntity(
            id = 1L, type = RecurringPlanType.MONTHLY_RECURRING, title = "Rent", categoryName = "HOUSING",
            amountInCents = 500_000L, firstPaymentDateMillis = millisFor(LocalDate.of(2026, 1, 5)), preferredDayOfMonth = 5,
            status = RecurringPlanStatus.ACTIVE
        )
        val result = NotificationCheckPlanner.plan(
            NotificationCheckInput(
                budgetAlertsEnabled = false,
                recurringRemindersEnabled = false,
                budgetActualPoints = emptyList(),
                alreadyNotifiedBudgetKeys = emptySet(),
                activePlans = listOf(plan),
                today = LocalDate.of(2026, 8, 4),
                alreadyNotifiedReminderKeys = emptySet(),
                zoneId = zoneId
            )
        )
        assertTrue(result.reminders.isEmpty())
    }

    @Test
    fun bothEnabled_bothCategoriesReported() {
        val overBudgetPoint = BudgetActualCalculator.calculate("FOOD", 10_000L, 12_000L)
        val plan = RecurringPaymentPlanEntity(
            id = 1L, type = RecurringPlanType.MONTHLY_RECURRING, title = "Rent", categoryName = "HOUSING",
            amountInCents = 500_000L, firstPaymentDateMillis = millisFor(LocalDate.of(2026, 1, 5)), preferredDayOfMonth = 5,
            status = RecurringPlanStatus.ACTIVE
        )
        val result = NotificationCheckPlanner.plan(
            NotificationCheckInput(
                budgetAlertsEnabled = true,
                recurringRemindersEnabled = true,
                budgetActualPoints = listOf(overBudgetPoint),
                alreadyNotifiedBudgetKeys = emptySet(),
                activePlans = listOf(plan),
                today = LocalDate.of(2026, 8, 4),
                alreadyNotifiedReminderKeys = emptySet(),
                zoneId = zoneId
            )
        )
        assertTrue(result.budgetAlerts.size == 1)
        assertTrue(result.reminders.size == 1)
    }
}

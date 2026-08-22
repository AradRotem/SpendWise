package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import java.time.LocalDate
import java.time.ZoneId

data class NotificationCheckInput(
    val budgetAlertsEnabled: Boolean,
    val recurringRemindersEnabled: Boolean,
    val budgetActualPoints: List<BudgetActualPoint>,
    val alreadyNotifiedBudgetKeys: Set<Pair<String, BudgetThresholdType>>,
    val activePlans: List<RecurringPaymentPlanEntity>,
    val today: LocalDate,
    val alreadyNotifiedReminderKeys: Set<Pair<Long, String>>,
    val zoneId: ZoneId = ZoneId.systemDefault()
)

data class NotificationCheckResult(
    val budgetAlerts: List<BudgetAlertEvent>,
    val reminders: List<RecurringReminderEvent>
)

// Top-level orchestration for one notification-check pass (see NotificationCheckWorker), kept as a
// pure function so the "respect the user's per-category preference toggle" gate is directly
// unit-testable without WorkManager/Context - a disabled preference always yields an empty list
// for that category, regardless of what the underlying evaluator would otherwise report.
object NotificationCheckPlanner {
    fun plan(input: NotificationCheckInput): NotificationCheckResult {
        val budgetAlerts = if (input.budgetAlertsEnabled) {
            BudgetAlertEvaluator.evaluate(input.budgetActualPoints, input.alreadyNotifiedBudgetKeys)
        } else {
            emptyList()
        }
        val reminders = if (input.recurringRemindersEnabled) {
            RecurringReminderEvaluator.evaluate(input.activePlans, input.today, input.alreadyNotifiedReminderKeys, input.zoneId)
        } else {
            emptyList()
        }
        return NotificationCheckResult(budgetAlerts, reminders)
    }
}

package com.aradrotem.spendwise.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.domain.BudgetActualCalculator
import com.aradrotem.spendwise.domain.BudgetThresholdType
import com.aradrotem.spendwise.domain.NotificationCheckInput
import com.aradrotem.spendwise.domain.NotificationCheckPlanner
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

// Daily periodic check for both notification categories that need a background scan rather than
// a live UI trigger (budget thresholds crossed by a transaction added while the app is closed,
// recurring payments coming due). No custom WorkerFactory/Configuration.Provider is needed since
// this Worker only needs a Context - it reaches the current account's repositories through
// SpendWiseApplication.repositories directly (same manual-DI style the rest of the app uses),
// rather than having anything injected.
class NotificationCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationChannels.ensureCreated(applicationContext)

        val app = applicationContext as SpendWiseApplication
        val repositories = app.repositories
        val prefs = repositories.notificationPreferencesStore
        val sender = SystemNotificationSender(applicationContext)
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val currentYearMonth = YearMonth.from(today).toString()

        val budgets = repositories.budgetRepository.observeAll().first()
        val currentMonthRange = com.aradrotem.spendwise.util.monthRange(YearMonth.from(today), zoneId)
        val categoryTotals = repositories.transactionRepository
            .observeExpenseTotalsByCategory(currentMonthRange.startMillis, currentMonthRange.endExclusiveMillis)
            .first()
        val budgetActualPoints = budgets.map { budget ->
            val spent = categoryTotals.firstOrNull { it.category == budget.categoryName }?.totalCents ?: 0L
            BudgetActualCalculator.calculate(budget.categoryName, budget.monthlyLimitCents, spent)
        }
        val alreadyNotifiedBudgetKeys = repositories.notificationRepository.getNotifiedBudgetThresholdKeys(currentYearMonth)

        val activePlans = repositories.recurringPaymentRepository.getActivePlans()
        val alreadyNotifiedReminderKeys = repositories.notificationRepository.getNotifiedRecurringReminderKeys()

        val result = NotificationCheckPlanner.plan(
            NotificationCheckInput(
                budgetAlertsEnabled = prefs.budgetAlertsEnabled.value,
                recurringRemindersEnabled = prefs.recurringRemindersEnabled.value,
                budgetActualPoints = budgetActualPoints,
                alreadyNotifiedBudgetKeys = alreadyNotifiedBudgetKeys,
                activePlans = activePlans,
                today = today,
                alreadyNotifiedReminderKeys = alreadyNotifiedReminderKeys,
                zoneId = zoneId
            )
        )

        val now = System.currentTimeMillis()
        result.budgetAlerts.forEach { alert ->
            val title = if (alert.thresholdType == BudgetThresholdType.OVER_BUDGET) "Budget exceeded" else "Budget almost reached"
            val text = if (alert.thresholdType == BudgetThresholdType.OVER_BUDGET) {
                "${alert.categoryName}: ${formatCents(alert.actualCents)} spent, over the ${formatCents(alert.budgetCents)} budget."
            } else {
                "${alert.categoryName}: ${formatCents(alert.actualCents)} of ${formatCents(alert.budgetCents)} budget used."
            }
            sender.send(NotificationChannels.BUDGET_ALERTS, notificationIdForBudget(alert.categoryName, alert.thresholdType), title, text)
            repositories.notificationRepository.recordBudgetThresholdNotified(alert.categoryName, currentYearMonth, alert.thresholdType, now)
        }

        result.reminders.forEach { reminder ->
            sender.send(
                NotificationChannels.RECURRING_PAYMENTS,
                notificationIdForReminder(reminder.planId),
                "Upcoming payment",
                "${reminder.title} is due tomorrow."
            )
            repositories.notificationRepository.recordRecurringReminderNotified(reminder.planId, reminder.scheduledYearMonth, now)
        }

        return Result.success()
    }

    private fun formatCents(cents: Long): String = "₪%d.%02d".format(cents / 100, cents % 100)

    // Stable per-category/per-plan ids so a re-notification (a different threshold tier, or a
    // different month's reminder) creates a new notification rather than silently replacing one
    // the user hasn't seen yet, while still avoiding unbounded id growth.
    private fun notificationIdForBudget(categoryName: String, thresholdType: BudgetThresholdType): Int =
        ("budget_${categoryName}_$thresholdType").hashCode()

    private fun notificationIdForReminder(planId: Long): Int = ("reminder_$planId").hashCode()

    companion object {
        private const val UNIQUE_WORK_NAME = "spendwise_notification_check"

        // Safe to call on every app start/account switch - enqueueUniquePeriodicWork with KEEP
        // no-ops if the same-named periodic work is already scheduled, so this never creates
        // duplicate recurring checks.
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationCheckWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

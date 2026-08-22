package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class RecurringReminderEvent(
    val planId: Long,
    val title: String,
    val dueDate: LocalDate,
    val scheduledYearMonth: String
)

// Pure "is a reminder due today" detection, built on top of RecurringPaymentSchedule.nextDueDate
// (already unit-tested for the schedule math itself) rather than re-deriving due dates. Dedup is
// keyed by (planId, scheduledYearMonth) - the same pair the generator itself uses as its unique
// index - so a reminder for one occurrence never fires twice even if the worker runs more than
// once on the same day.
object RecurringReminderEvaluator {

    // One day before the due date: early enough to still act on it, late enough that "due
    // tomorrow" reads as genuinely imminent rather than noise weeks in advance.
    const val REMINDER_DAYS_BEFORE = 1L

    fun evaluate(
        activePlans: List<RecurringPaymentPlanEntity>,
        today: LocalDate,
        alreadyNotified: Set<Pair<Long, String>>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<RecurringReminderEvent> = activePlans.mapNotNull { plan ->
        val nextDue = RecurringPaymentSchedule.nextDueDate(plan, today, zoneId) ?: return@mapNotNull null
        if (nextDue != today.plusDays(REMINDER_DAYS_BEFORE)) return@mapNotNull null

        val scheduledYearMonth = YearMonth.from(nextDue).toString()
        if ((plan.id to scheduledYearMonth) in alreadyNotified) return@mapNotNull null

        RecurringReminderEvent(plan.id, plan.title, nextDue, scheduledYearMonth)
    }
}

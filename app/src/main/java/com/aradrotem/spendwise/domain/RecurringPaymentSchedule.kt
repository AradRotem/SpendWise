package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// One theoretical due payment for a plan: a date, its amount, and (for installment plans) its
// 1-based position. Pure data, no knowledge of what has already been generated in the database.
data class DuePayment(
    val dueDate: LocalDate,
    val amountCents: Long,
    val installmentNumber: Int?,
    val scheduledYearMonth: String
)

// Pure date/amount math for recurring plans, kept free of Room/repository dependencies so it can
// be unit-tested directly (see RecurringPaymentScheduleTest).
object RecurringPaymentSchedule {

    // Clamps to the month's last valid day instead of skipping or rolling into the next month
    // (e.g. day 31 in February safely becomes Feb 28, or 29 in a leap year).
    fun safeDayOfMonth(yearMonth: YearMonth, preferredDay: Int): LocalDate =
        yearMonth.atDay(preferredDay.coerceIn(1, yearMonth.lengthOfMonth()))

    // Splits a total into `count` parts that sum back to it exactly. Any remainder from the
    // integer division goes entirely to the final installment.
    fun splitInstallments(totalAmountInCents: Long, count: Int): List<Long> {
        require(count > 0) { "count must be positive" }
        val base = totalAmountInCents / count
        val remainder = totalAmountInCents - base * count
        return List(count) { index -> if (index == count - 1) base + remainder else base }
    }

    // All payments due for this plan on or before `today`, oldest first. Includes any missed
    // months since the plan's start date (catch-up), and excludes anything not yet due.
    fun duePayments(
        plan: RecurringPaymentPlanEntity,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<DuePayment> {
        val startDate = Instant.ofEpochMilli(plan.firstPaymentDateMillis).atZone(zoneId).toLocalDate()
        return when (plan.type) {
            // Monthly salary follows the exact same schedule as a monthly expense - only the
            // generated transaction's type differs (see RecurringPaymentGenerator).
            RecurringPlanType.MONTHLY_RECURRING, RecurringPlanType.MONTHLY_SALARY ->
                monthlyDuePayments(plan, startDate, today, zoneId)
            RecurringPlanType.INSTALLMENT -> installmentDuePayments(plan, startDate, today)
        }
    }

    // The next upcoming due date after `today`, or null if the plan has no more payments left
    // (e.g. an installment plan whose schedule is already exhausted). Reuses duePayments with a
    // far-future cutoff rather than duplicating the schedule-walking logic.
    fun nextDueDate(plan: RecurringPaymentPlanEntity, today: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
        duePayments(plan, today.plusYears(5), zoneId).firstOrNull { it.dueDate.isAfter(today) }?.dueDate

    // Epoch millis for the last instant a monthly plan may still generate into, used by "delete
    // this and future" (see RecurringOccurrenceManager) to cap a plan's endDateMillis so it can
    // never regenerate the deleted month or anything after it, even if later restored.
    fun lastDayOfPreviousMonth(scheduledYearMonth: String, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val month = YearMonth.parse(scheduledYearMonth)
        return month.minusMonths(1).atEndOfMonth().atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun monthlyDuePayments(
        plan: RecurringPaymentPlanEntity,
        startDate: LocalDate,
        today: LocalDate,
        zoneId: ZoneId
    ): List<DuePayment> {
        val amount = plan.amountInCents ?: return emptyList()
        val endDate = plan.endDateMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }

        val results = mutableListOf<DuePayment>()
        var month = YearMonth.from(startDate)
        while (true) {
            val dueDate = safeDayOfMonth(month, plan.preferredDayOfMonth)
            if (dueDate.isAfter(today)) break

            val withinPlanWindow = !dueDate.isBefore(startDate) && (endDate == null || !dueDate.isAfter(endDate))
            if (withinPlanWindow) {
                results += DuePayment(dueDate, amount, installmentNumber = null, scheduledYearMonth = YearMonth.from(dueDate).toString())
            }
            if (endDate != null && dueDate.isAfter(endDate)) break

            month = month.plusMonths(1)
        }
        return results
    }

    private fun installmentDuePayments(
        plan: RecurringPaymentPlanEntity,
        startDate: LocalDate,
        today: LocalDate
    ): List<DuePayment> {
        val total = plan.totalAmountInCents ?: return emptyList()
        val count = plan.totalInstallments ?: return emptyList()
        if (count <= 0) return emptyList()

        val amounts = splitInstallments(total, count)
        val startMonth = YearMonth.from(startDate)

        val results = mutableListOf<DuePayment>()
        for (index in 0 until count) {
            // The first installment uses the exact chosen date; later ones re-derive the safe
            // day from the original preferred day so a clamped short month doesn't drift it.
            val dueDate = if (index == 0) startDate else safeDayOfMonth(startMonth.plusMonths(index.toLong()), plan.preferredDayOfMonth)
            if (dueDate.isAfter(today)) break
            results += DuePayment(dueDate, amounts[index], installmentNumber = index + 1, scheduledYearMonth = YearMonth.from(dueDate).toString())
        }
        return results
    }
}

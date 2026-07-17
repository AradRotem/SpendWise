package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.domain.RecurringPaymentSchedule
import com.aradrotem.spendwise.ui.format.parseAmountToCents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AddRecurringPlanUiState(
    val type: RecurringPlanType = RecurringPlanType.MONTHLY_RECURRING,
    val title: String = "",
    val category: String? = null,
    val note: String = "",

    // Monthly recurring / monthly salary fields
    val amountText: String = "",
    val startDateMillis: Long = System.currentTimeMillis(),
    val preferredDayOfMonthText: String = LocalDate.now().dayOfMonth.toString(),
    val hasEndDate: Boolean = false,
    val endDateMillis: Long? = null,

    // Installment fields
    val totalAmountText: String = "",
    val installmentCountText: String = "",
    val firstPaymentDateMillis: Long = System.currentTimeMillis(),

    // Validation errors
    val titleError: String? = null,
    val categoryError: String? = null,
    val amountError: String? = null,
    val preferredDayError: String? = null,
    val endDateError: String? = null,
    val totalAmountError: String? = null,
    val installmentCountError: String? = null,

    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isSaved: Boolean = false,

    // Edit mode: set when this screen was opened for an existing plan.
    val planId: Long? = null,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    // Number of installments already generated for the loaded plan; installment plans lock
    // total amount/count editing once this is greater than zero.
    val generatedInstallmentCount: Int = 0,
    // Set when this screen was opened via "Edit this and future transactions" on a specific
    // generated transaction (see TransactionsScreen). When present, a successful plan save also
    // applies the new values to this occurrence and any later non-overridden generated ones.
    val occurrenceTransactionId: Long? = null,
    // The occurrence's scheduled month, loaded alongside it, purely so the screen can show which
    // month "and future" starts from.
    val occurrenceScheduledYearMonth: String? = null
) {
    val isEditMode: Boolean get() = planId != null

    val isInstallmentFieldsLocked: Boolean
        get() = isEditMode && type == RecurringPlanType.INSTALLMENT && generatedInstallmentCount > 0

    // Preview shown before saving (spec: "Show the calculated monthly installment amount before
    // saving"). Null while the total/count fields aren't yet valid numbers.
    val installmentPreviewCents: List<Long>?
        get() {
            val total = parseAmountToCents(totalAmountText) ?: return null
            val count = installmentCountText.toIntOrNull() ?: return null
            if (total <= 0L || count <= 0) return null
            return RecurringPaymentSchedule.splitInstallments(total, count)
        }

    // Non-blocking heads-up for Monthly expense/salary: days 29-31 don't exist in every month,
    // so a shorter month's payment safely clamps to that month's last valid day instead (see
    // RecurringPaymentSchedule.safeDayOfMonth). This never blocks saving - it only explains the
    // existing clamping behavior.
    val showPreferredDayWarning: Boolean
        get() = isRiskyDayOfMonth(preferredDayOfMonthText.toIntOrNull())

    // Same warning, but for an installment plan's first-payment day: later installments reuse
    // that same day-of-month (see RecurringPaymentSchedule), so it's just as affected.
    val showFirstPaymentDayWarning: Boolean
        get() = isRiskyDayOfMonth(dayOfMonthOf(firstPaymentDateMillis))

    companion object {
        const val RISKY_DAY_WARNING =
            "Note: Not every month contains this day. In shorter months, the transaction will " +
                "be scheduled for the last valid day of the month."
    }
}

private fun isRiskyDayOfMonth(day: Int?): Boolean = day != null && day in 29..31

private fun dayOfMonthOf(millis: Long): Int =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().dayOfMonth

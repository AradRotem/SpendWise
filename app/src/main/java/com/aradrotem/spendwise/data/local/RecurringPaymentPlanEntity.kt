package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RecurringPlanType {
    MONTHLY_RECURRING,
    INSTALLMENT,
    MONTHLY_SALARY
}

// STOPPED and COMPLETED are both terminal (no further generation), but kept distinct so the UI
// can tell "user stopped it" apart from "installment plan finished on its own".
enum class RecurringPlanStatus {
    ACTIVE,
    PAUSED,
    STOPPED,
    COMPLETED
}

// No foreign key to transactions: generated transactions are historical records that must
// survive a plan being paused/stopped (see Step 10 spec section 13), so the link is a plain
// nullable id on TransactionEntity rather than a constraint that could cascade-delete history.
@Entity(tableName = "recurring_payment_plans")
data class RecurringPaymentPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RecurringPlanType,
    val title: String,
    val categoryName: String,
    val note: String = "",
    // MONTHLY_RECURRING only: fixed amount charged every due month.
    val amountInCents: Long? = null,
    // INSTALLMENT only: total purchase amount; per-installment amounts are derived deterministically
    // (see RecurringPaymentSchedule.splitInstallments) rather than stored.
    val totalAmountInCents: Long? = null,
    // INSTALLMENT only: number of installments.
    val totalInstallments: Int? = null,
    val firstPaymentDateMillis: Long,
    // The day of month every future due date targets (safely clamped in short months). For
    // installment plans this is firstPaymentDateMillis's day-of-month, captured once so later
    // installments don't drift after a clamped short month (e.g. day 31 -> Feb 28 -> Mar 28).
    val preferredDayOfMonth: Int,
    // MONTHLY_RECURRING only, optional.
    val endDateMillis: Long? = null,
    val status: RecurringPlanStatus = RecurringPlanStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
)

package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import java.time.LocalDate

data class RecurringPlansUiState(
    val isLoading: Boolean = true,
    val items: List<RecurringPlanListItem> = emptyList()
)

data class RecurringPlanListItem(
    val plan: RecurringPaymentPlanEntity,
    // Count of transactions currently in history for this plan (already-generated minus any the
    // user deleted); used for installment progress ("3 of 12 payments").
    val generatedCount: Int,
    val nextDueDate: LocalDate?,
    // Count of persistent occurrence-exception rows for this plan (see
    // RecurringOccurrenceExceptionEntity) - i.e. installments the user intentionally deleted and
    // that must never regenerate. Only meaningful for Completed installment plans; see
    // installmentProgressText in RecurringPaymentsScreen.
    val deletedInstallmentOccurrences: Int
)

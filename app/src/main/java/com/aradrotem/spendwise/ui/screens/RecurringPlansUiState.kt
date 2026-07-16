package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import java.time.LocalDate

data class RecurringPlansUiState(
    val isLoading: Boolean = true,
    val items: List<RecurringPlanListItem> = emptyList()
)

data class RecurringPlanListItem(
    val plan: RecurringPaymentPlanEntity,
    // Count of transactions already generated for this plan; used for installment progress
    // ("3 of 12 payments").
    val generatedCount: Int,
    val nextDueDate: LocalDate?
)

package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.OccurrenceExceptionType
import com.aradrotem.spendwise.data.local.RecurringOccurrenceExceptionEntity
import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.repository.RecurringOccurrenceExceptionRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.RecurringPaymentGenerator
import com.aradrotem.spendwise.domain.RecurringPaymentSchedule
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecurringPlansViewModel(
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val transactionRepository: TransactionRepository,
    private val recurringOccurrenceExceptionRepository: RecurringOccurrenceExceptionRepository,
    private val recurringPaymentGenerator: RecurringPaymentGenerator
) : ViewModel() {

    // Reactively recomputed whenever plans, any transaction, or any occurrence exception changes
    // (including newly generated payments and manually deleted occurrences), so installment
    // progress and status stay live without a separate per-plan subscription.
    val uiState: StateFlow<RecurringPlansUiState> = combine(
        recurringPaymentRepository.observeAll(),
        transactionRepository.observeAll(),
        recurringOccurrenceExceptionRepository.observeAll()
    ) { plans, transactions, exceptions ->
        val today = LocalDate.now()
        val items = plans.map { plan ->
            RecurringPlanListItem(
                plan = plan,
                generatedCount = transactions.count { it.recurringPlanId == plan.id },
                nextDueDate = if (plan.status == RecurringPlanStatus.ACTIVE) {
                    RecurringPaymentSchedule.nextDueDate(plan, today)
                } else {
                    null
                },
                deletedInstallmentOccurrences = deletedInstallmentOccurrenceCount(plan.id, exceptions)
            )
        }
        RecurringPlansUiState(isLoading = false, items = items)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecurringPlansUiState(isLoading = true)
    )

    fun onPause(planId: Long) {
        viewModelScope.launch { recurringPaymentRepository.pause(planId) }
    }

    // Used for both "Resume" (from Paused) and "Restore" (from Stopped): both re-activate the
    // plan, then immediately run generation so any due catch-up payments appear right away
    // instead of waiting for the next app start/resume.
    fun onResume(planId: Long) {
        viewModelScope.launch {
            recurringPaymentRepository.resume(planId)
            recurringPaymentGenerator.generateDuePayments()
        }
    }

    fun onStop(planId: Long) {
        viewModelScope.launch { recurringPaymentRepository.stop(planId) }
    }

    fun onDelete(plan: RecurringPaymentPlanEntity) {
        viewModelScope.launch { recurringPaymentRepository.deletePlan(plan) }
    }

    companion object {
        fun factory(
            recurringPaymentRepository: RecurringPaymentRepository,
            transactionRepository: TransactionRepository,
            recurringOccurrenceExceptionRepository: RecurringOccurrenceExceptionRepository,
            recurringPaymentGenerator: RecurringPaymentGenerator
        ) = viewModelFactory {
            initializer {
                RecurringPlansViewModel(
                    recurringPaymentRepository, transactionRepository, recurringOccurrenceExceptionRepository, recurringPaymentGenerator
                )
            }
        }
    }
}

// How many occurrences of this plan the user intentionally deleted (and that must therefore never
// regenerate) - i.e. persistent SKIPPED exceptions belonging to this plan, per
// RecurringOccurrenceExceptionEntity. Kept as a top-level pure function - not inline in the
// combine block - so it's directly unit-testable without a Main dispatcher. Deliberately not
// derived as totalInstallments - generatedCount: that arithmetic happens to match today given the
// app's current deletion paths, but the exception table is the actual source of truth for "must
// never regenerate", and only it stays correct if a future change adds another way to remove a
// generated row.
fun deletedInstallmentOccurrenceCount(planId: Long, exceptions: List<RecurringOccurrenceExceptionEntity>): Int =
    exceptions.count { it.recurringPlanId == planId && it.exceptionType == OccurrenceExceptionType.SKIPPED }

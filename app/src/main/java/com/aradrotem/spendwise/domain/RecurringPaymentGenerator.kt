package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import java.time.LocalDate
import java.time.ZoneId

// Turns due recurring/installment payments into real Expense transactions. Intended to run at
// app-entry points (see SpendWiseApplication.onCreate) rather than on a background schedule.
//
// Idempotent by construction: duplicate candidates are rejected by the DB-level unique index on
// (recurringPlanId, scheduledYearMonth) via insertGeneratedIgnoringConflicts, so calling this
// repeatedly (app restarts, multiple entry points) never creates extra transactions.
class RecurringPaymentGenerator(
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val transactionRepository: TransactionRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    suspend fun generateDuePayments(today: LocalDate = LocalDate.now(zoneId)): Int {
        var generatedCount = 0
        for (plan in recurringPaymentRepository.getActivePlans()) {
            generatedCount += generateForPlan(plan, today)
        }
        return generatedCount
    }

    private suspend fun generateForPlan(plan: RecurringPaymentPlanEntity, today: LocalDate): Int {
        val candidates = RecurringPaymentSchedule.duePayments(plan, today, zoneId)

        var insertedCount = 0
        if (candidates.isNotEmpty()) {
            val transactionType = transactionTypeFor(plan.type)
            val entities = candidates.map { candidate ->
                TransactionEntity(
                    amountInCents = candidate.amountCents,
                    type = transactionType,
                    category = plan.categoryName,
                    timestamp = candidate.dueDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    note = plan.note,
                    recurringPlanId = plan.id,
                    installmentNumber = candidate.installmentNumber,
                    totalInstallments = plan.totalInstallments,
                    isAutomaticallyGenerated = true,
                    scheduledYearMonth = candidate.scheduledYearMonth,
                    // Snapshot, not a live reference: renaming or deleting the plan later must
                    // not change how already-generated transactions display (see TransactionEntity).
                    sourceTitle = plan.title
                )
            }
            val insertedIds = transactionRepository.insertGeneratedIgnoringConflicts(entities)
            insertedCount = insertedIds.count { it != -1L }
        }

        if (plan.type == RecurringPlanType.INSTALLMENT) {
            val totalInstallments = plan.totalInstallments ?: 0
            val generatedSoFar = transactionRepository.countGeneratedForPlan(plan.id)
            if (totalInstallments > 0 && generatedSoFar >= totalInstallments) {
                recurringPaymentRepository.markCompleted(plan.id)
            }
        }

        return insertedCount
    }

    private fun transactionTypeFor(planType: RecurringPlanType): TransactionType = when (planType) {
        RecurringPlanType.MONTHLY_RECURRING, RecurringPlanType.INSTALLMENT -> TransactionType.EXPENSE
        RecurringPlanType.MONTHLY_SALARY -> TransactionType.INCOME
    }
}

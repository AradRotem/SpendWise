package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.repository.RecurringOccurrenceExceptionRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

// Coordinates occurrence-level actions (edit/delete one generated transaction, or it-and-future)
// across the transaction, recurring-plan, and occurrence-exception repositories. Kept separate
// from RecurringPaymentGenerator: the generator only ever creates transactions going forward,
// this class only ever acts on a transaction the user is looking at right now.
class RecurringOccurrenceManager(
    private val transactionRepository: TransactionRepository,
    private val recurringPaymentRepository: RecurringPaymentRepository,
    private val occurrenceExceptionRepository: RecurringOccurrenceExceptionRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    // Edits exactly one occurrence, independent of its plan. Always reloads and `.copy()`s the
    // existing row rather than reconstructing one from scratch, so recurringPlanId,
    // scheduledYearMonth, installmentNumber, totalInstallments, isAutomaticallyGenerated, and
    // type are preserved no matter what - a generated transaction can never accidentally turn
    // into a plain manual one through this path.
    suspend fun editOccurrenceOnly(
        transactionId: Long,
        title: String,
        amountInCents: Long,
        category: String,
        note: String,
        timestamp: Long
    ): Result<Unit> {
        val existing = transactionRepository.getById(transactionId)
            ?: return Result.failure(IllegalStateException("Transaction not found"))

        val scheduledMonth = existing.scheduledYearMonth
        if (scheduledMonth != null) {
            val newMonth = YearMonth.from(Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()).toString()
            if (newMonth != scheduledMonth) {
                return Result.failure(IllegalArgumentException("Date must stay within the scheduled month ($scheduledMonth)"))
            }
        }

        transactionRepository.update(
            existing.copy(
                amountInCents = amountInCents,
                category = category,
                note = note,
                timestamp = timestamp,
                sourceTitle = title,
                isOccurrenceModified = true
            )
        )
        return Result.success(Unit)
    }

    // Applies a plan-level edit to the selected occurrence, plus any already-generated later
    // occurrences that the user hasn't individually overridden. Not-yet-generated future months
    // pick up the new plan values automatically once they're eventually created - see
    // AddRecurringPlanViewModel, which calls this only after successfully updating the plan
    // itself, so both stay consistent.
    suspend fun applyEditToOccurrenceAndFuture(
        occurrenceTransactionId: Long,
        title: String,
        amountInCents: Long?,
        category: String,
        note: String
    ): Result<Unit> {
        val occurrence = transactionRepository.getById(occurrenceTransactionId)
            ?: return Result.failure(IllegalStateException("Transaction not found"))
        val planId = occurrence.recurringPlanId
        val month = occurrence.scheduledYearMonth
        if (planId == null || month == null) {
            return Result.failure(IllegalStateException("Not a generated occurrence"))
        }

        transactionRepository.update(
            occurrence.copy(
                amountInCents = amountInCents ?: occurrence.amountInCents,
                category = category,
                note = note,
                sourceTitle = title,
                isOccurrenceModified = true
            )
        )

        if (amountInCents != null) {
            transactionRepository.updateFutureGeneratedTransactionsWithAmount(planId, month, amountInCents, category, note, title)
        } else {
            transactionRepository.updateFutureGeneratedTransactionsMetadataOnly(planId, month, category, note, title)
        }
        return Result.success(Unit)
    }

    // Deletes exactly one occurrence and records a persistent exception so catch-up generation
    // never recreates it - the transaction row's own existence is what normally blocks
    // regeneration, so deleting it alone would silently free that slot back up.
    suspend fun deleteOccurrenceOnly(transaction: TransactionEntity): Result<Unit> {
        val planId = transaction.recurringPlanId
        val month = transaction.scheduledYearMonth
        if (planId == null || month == null) {
            return Result.failure(IllegalStateException("Not a generated occurrence"))
        }
        transactionRepository.delete(transaction)
        occurrenceExceptionRepository.skipOccurrence(planId, month)
        return Result.success(Unit)
    }

    // For the delete-and-future confirmation dialog: how many additional already-generated
    // transactions (besides the selected one) would also be removed.
    suspend fun countLaterGeneratedTransactions(transaction: TransactionEntity): Int {
        val planId = transaction.recurringPlanId ?: return 0
        val month = transaction.scheduledYearMonth ?: return 0
        return transactionRepository.getGeneratedAfter(planId, month).size
    }

    // Removes the selected occurrence and any later generated ones, then caps the plan so it can
    // never generate that far again - even after a later Restore/Resume. Earlier transactions are
    // never touched.
    suspend fun deleteThisAndFuture(transaction: TransactionEntity): Result<Unit> {
        val planId = transaction.recurringPlanId
        val month = transaction.scheduledYearMonth
        if (planId == null || month == null) {
            return Result.failure(IllegalStateException("Not a generated occurrence"))
        }
        val plan = recurringPaymentRepository.getById(planId)
            ?: return Result.failure(IllegalStateException("Plan not found"))

        transactionRepository.deleteGeneratedFromMonth(planId, month)

        when (plan.type) {
            RecurringPlanType.INSTALLMENT -> {
                val newTotalInstallments = (transaction.installmentNumber ?: 1) - 1
                recurringPaymentRepository.stopFromInstallment(planId, newTotalInstallments)
            }
            RecurringPlanType.MONTHLY_RECURRING, RecurringPlanType.MONTHLY_SALARY -> {
                val endDateMillis = RecurringPaymentSchedule.lastDayOfPreviousMonth(month, zoneId)
                recurringPaymentRepository.stopWithEndDate(planId, endDateMillis)
            }
        }
        return Result.success(Unit)
    }
}

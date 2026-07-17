package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.RecurringPaymentPlanDao
import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

class RecurringPaymentRepository(private val planDao: RecurringPaymentPlanDao) {

    fun observeAll(): Flow<List<RecurringPaymentPlanEntity>> = planDao.observeAll()

    suspend fun getActivePlans(): List<RecurringPaymentPlanEntity> = planDao.getActivePlans()

    suspend fun getById(id: Long): RecurringPaymentPlanEntity? = planDao.getById(id)

    // Covers both MONTHLY_RECURRING (Expense) and MONTHLY_SALARY (Income) plans: they share the
    // same fields, and only differ in which transaction type the generator produces.
    // Defensive re-check of the amount, mirroring BudgetRepository.addBudget: the creation
    // ViewModel already validates every field with specific messages, this is the last gate.
    suspend fun createMonthlyPlan(
        type: RecurringPlanType,
        title: String,
        categoryName: String,
        note: String,
        amountInCents: Long,
        startDateMillis: Long,
        preferredDayOfMonth: Int,
        endDateMillis: Long?
    ): Result<Unit> {
        if (amountInCents <= 0L) {
            return Result.failure(IllegalArgumentException("Amount must be greater than zero"))
        }
        planDao.insert(
            RecurringPaymentPlanEntity(
                type = type,
                title = title,
                categoryName = categoryName,
                note = note,
                amountInCents = amountInCents,
                firstPaymentDateMillis = startDateMillis,
                preferredDayOfMonth = preferredDayOfMonth,
                endDateMillis = endDateMillis
            )
        )
        return Result.success(Unit)
    }

    suspend fun createInstallmentPlan(
        title: String,
        categoryName: String,
        note: String,
        totalAmountInCents: Long,
        totalInstallments: Int,
        firstPaymentDateMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<Unit> {
        if (totalAmountInCents <= 0L) {
            return Result.failure(IllegalArgumentException("Total amount must be greater than zero"))
        }
        if (totalInstallments <= 0) {
            return Result.failure(IllegalArgumentException("Installment count must be greater than zero"))
        }
        val preferredDayOfMonth = Instant.ofEpochMilli(firstPaymentDateMillis).atZone(zoneId).toLocalDate().dayOfMonth
        planDao.insert(
            RecurringPaymentPlanEntity(
                type = RecurringPlanType.INSTALLMENT,
                title = title,
                categoryName = categoryName,
                note = note,
                totalAmountInCents = totalAmountInCents,
                totalInstallments = totalInstallments,
                firstPaymentDateMillis = firstPaymentDateMillis,
                preferredDayOfMonth = preferredDayOfMonth
            )
        )
        return Result.success(Unit)
    }

    // Editing only ever changes future generation: this updates the plan row in place, and
    // never touches already-generated transactions (which snapshot their own title/amount/etc.
    // at generation time - see TransactionEntity.sourceTitle). Start date and plan type are
    // intentionally not editable, since they anchor already-generated history.
    suspend fun updateMonthlyPlan(
        id: Long,
        title: String,
        categoryName: String,
        note: String,
        amountInCents: Long,
        preferredDayOfMonth: Int,
        endDateMillis: Long?
    ): Result<Unit> {
        if (amountInCents <= 0L) {
            return Result.failure(IllegalArgumentException("Amount must be greater than zero"))
        }
        val existing = planDao.getById(id) ?: return Result.failure(IllegalStateException("Plan not found"))
        planDao.update(
            existing.copy(
                title = title,
                categoryName = categoryName,
                note = note,
                amountInCents = amountInCents,
                preferredDayOfMonth = preferredDayOfMonth,
                endDateMillis = endDateMillis
            )
        )
        return Result.success(Unit)
    }

    // Total amount/installment count are only safe to change freely before any installment has
    // been generated (see AddRecurringPlanViewModel, which disables those fields once
    // generatedInstallmentCount > 0). generatedInstallmentCount is a defensive, caller-supplied
    // floor: regardless of what the UI allows, the count can never drop below what has already
    // been generated. First payment date and the per-installment schedule day are not editable.
    suspend fun updateInstallmentPlan(
        id: Long,
        title: String,
        categoryName: String,
        note: String,
        totalAmountInCents: Long,
        totalInstallments: Int,
        generatedInstallmentCount: Int = 0
    ): Result<Unit> {
        if (totalAmountInCents <= 0L) {
            return Result.failure(IllegalArgumentException("Total amount must be greater than zero"))
        }
        if (totalInstallments <= 0) {
            return Result.failure(IllegalArgumentException("Installment count must be greater than zero"))
        }
        if (totalInstallments < generatedInstallmentCount) {
            return Result.failure(
                IllegalArgumentException("Cannot be fewer than the $generatedInstallmentCount installments already generated")
            )
        }
        val existing = planDao.getById(id) ?: return Result.failure(IllegalStateException("Plan not found"))
        planDao.update(
            existing.copy(
                title = title,
                categoryName = categoryName,
                note = note,
                totalAmountInCents = totalAmountInCents,
                totalInstallments = totalInstallments
            )
        )
        return Result.success(Unit)
    }

    suspend fun pause(id: Long) = planDao.updateStatus(id, RecurringPlanStatus.PAUSED)

    // Shared by both "Resume" (from PAUSED) and "Restore" (from STOPPED): both mean "make this
    // plan generate again"; the caller is expected to re-run generation afterward for catch-up.
    suspend fun resume(id: Long) = planDao.updateStatus(id, RecurringPlanStatus.ACTIVE)

    suspend fun stop(id: Long) = planDao.updateStatus(id, RecurringPlanStatus.STOPPED)

    suspend fun markCompleted(id: Long) = planDao.updateStatus(id, RecurringPlanStatus.COMPLETED)

    // "Delete this and future" for a monthly plan (see RecurringOccurrenceManager): stops the
    // plan AND caps its schedule with an end date. Both together, not just STOPPED status, so a
    // later Restore can never regenerate past this point - RecurringPaymentSchedule always
    // respects endDateMillis regardless of status.
    suspend fun stopWithEndDate(id: Long, endDateMillis: Long) {
        val existing = planDao.getById(id) ?: return
        planDao.update(existing.copy(endDateMillis = endDateMillis, status = RecurringPlanStatus.STOPPED))
    }

    // "Delete this and future" for an installment plan: caps totalInstallments at the count that
    // must remain, so even a later Restore can never generate beyond it (installment scheduling
    // is governed purely by totalInstallments, not an end date).
    suspend fun stopFromInstallment(id: Long, newTotalInstallments: Int) {
        val existing = planDao.getById(id) ?: return
        planDao.update(
            existing.copy(totalInstallments = newTotalInstallments.coerceAtLeast(0), status = RecurringPlanStatus.STOPPED)
        )
    }

    // Removes the plan itself only. Already-generated transactions are untouched (no FK/cascade
    // - see RecurringPaymentPlanEntity), so financial history is preserved.
    suspend fun deletePlan(plan: RecurringPaymentPlanEntity) = planDao.delete(plan)
}

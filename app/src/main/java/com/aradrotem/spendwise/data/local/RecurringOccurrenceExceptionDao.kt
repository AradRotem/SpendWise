package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringOccurrenceExceptionDao {

    // IGNORE, not error: re-deleting an already-skipped occurrence (e.g. a retried action) must
    // stay idempotent rather than crash.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exception: RecurringOccurrenceExceptionEntity): Long

    @Query("SELECT * FROM recurring_occurrence_exceptions WHERE recurringPlanId = :planId")
    suspend fun getForPlan(planId: Long): List<RecurringOccurrenceExceptionEntity>

    // Reactive, all-plans variant for the Recurring Transactions list screen: it needs a live
    // per-plan deleted-occurrence count alongside plans/transactions (see RecurringPlansViewModel)
    // so the Completed-installment display updates immediately after a delete, without a
    // per-plan subscription.
    @Query("SELECT * FROM recurring_occurrence_exceptions")
    fun observeAll(): Flow<List<RecurringOccurrenceExceptionEntity>>

    @Query("DELETE FROM recurring_occurrence_exceptions WHERE recurringPlanId = :planId")
    suspend fun deleteForPlan(planId: Long)
}

package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringPaymentPlanDao {

    @Insert
    suspend fun insert(plan: RecurringPaymentPlanEntity): Long

    @Update
    suspend fun update(plan: RecurringPaymentPlanEntity)

    // No foreign key from transactions to this table (see RecurringPaymentPlanEntity), so
    // deleting a plan never cascades into deleting the transactions it already generated.
    @Delete
    suspend fun delete(plan: RecurringPaymentPlanEntity)

    @Query("SELECT * FROM recurring_payment_plans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecurringPaymentPlanEntity>>

    @Query("SELECT * FROM recurring_payment_plans WHERE status = 'ACTIVE'")
    suspend fun getActivePlans(): List<RecurringPaymentPlanEntity>

    @Query("SELECT * FROM recurring_payment_plans WHERE id = :id")
    suspend fun getById(id: Long): RecurringPaymentPlanEntity?

    @Query("UPDATE recurring_payment_plans SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: RecurringPlanStatus)
}

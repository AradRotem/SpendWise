package com.aradrotem.spendwise.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationStateDao {

    // IGNORE rather than fail: a race between two worker runs hitting the same unique index is
    // harmless here (the "already notified" fact is idempotent), unlike normal domain data.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBudgetThresholdNotified(entity: NotifiedBudgetThresholdEntity)

    @Query("SELECT categoryName, thresholdType FROM notified_budget_thresholds WHERE yearMonth = :yearMonth")
    suspend fun getNotifiedBudgetThresholdKeys(yearMonth: String): List<NotifiedBudgetThresholdKey>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecurringReminderNotified(entity: NotifiedRecurringReminderEntity)

    @Query("SELECT planId, scheduledYearMonth FROM notified_recurring_reminders")
    suspend fun getNotifiedRecurringReminderKeys(): List<NotifiedRecurringReminderKey>
}

data class NotifiedBudgetThresholdKey(val categoryName: String, val thresholdType: String)
data class NotifiedRecurringReminderKey(val planId: Long, val scheduledYearMonth: String)

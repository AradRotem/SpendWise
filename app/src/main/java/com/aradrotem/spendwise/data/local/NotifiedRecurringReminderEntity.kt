package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// One row per recurring-plan occurrence a reminder was already sent for. Keyed the same way the
// generator itself dedups generated transactions (planId, scheduledYearMonth), so a reminder never
// fires twice for the same occurrence even if the notification worker runs more than once a day.
@Entity(
    tableName = "notified_recurring_reminders",
    indices = [Index(value = ["planId", "scheduledYearMonth"], unique = true)]
)
data class NotifiedRecurringReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val scheduledYearMonth: String,
    val notifiedAtEpochMillis: Long
)

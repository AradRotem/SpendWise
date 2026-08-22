package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// One row per (category, month, threshold) alert already sent - existence of a row is the dedup
// check itself. yearMonth is a plain "yyyy-MM" string (same convention as
// TransactionEntity.scheduledYearMonth) so a new calendar month naturally has no matching rows,
// which is what makes the monthly reset work without any explicit cleanup step.
@Entity(
    tableName = "notified_budget_thresholds",
    indices = [Index(value = ["categoryName", "yearMonth", "thresholdType"], unique = true)]
)
data class NotifiedBudgetThresholdEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String,
    val yearMonth: String,
    val thresholdType: String,
    val notifiedAtEpochMillis: Long
)

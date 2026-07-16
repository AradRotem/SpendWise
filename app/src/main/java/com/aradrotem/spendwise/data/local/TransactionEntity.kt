package com.aradrotem.spendwise.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// The (recurringPlanId, scheduledYearMonth) unique index is the DB-level duplicate guard for
// generated payments. It only constrains generated rows: manual transactions leave both columns
// null, and SQLite never treats NULL as equal to NULL in a unique index, so manual transactions
// never collide with each other or with generated ones.
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["recurringPlanId", "scheduledYearMonth"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountInCents: Long,
    val type: TransactionType,
    val category: String,
    val timestamp: Long,
    val note: String = "",
    // Nullable/defaulted so existing manually entered transactions remain valid unchanged.
    val recurringPlanId: Long? = null,
    // 1-based; null for monthly-recurring payments and manual transactions.
    val installmentNumber: Int? = null,
    // Snapshot of the plan's installment count at generation time, for display without a join.
    val totalInstallments: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val isAutomaticallyGenerated: Boolean = false,
    // "yyyy-MM" of the due month; always set for generated transactions, null for manual ones.
    val scheduledYearMonth: String? = null,
    // Snapshot of the source plan's title at generation time. Null for manual transactions.
    // Deliberately not re-read from the plan at display time: if the plan is later renamed or
    // deleted, historical transactions must keep showing the title that was active when they
    // were generated.
    val sourceTitle: String? = null
)

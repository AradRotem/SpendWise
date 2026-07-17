package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Only one exception type exists so far: an occurrence the user intentionally deleted (or, in a
// future step, pre-emptively skipped before it was ever generated). Both cases mean the same
// thing to the generator - "never create a transaction for this plan+month" - so a single value
// covers everything Step 11 needs. MODIFIED is intentionally not represented here: an edited
// occurrence still exists as a transaction row (see TransactionEntity.isOccurrenceModified), and
// that row's presence already blocks regeneration via the existing unique index - no exception
// record is needed for it.
enum class OccurrenceExceptionType {
    SKIPPED
}

// Persists the fact that a specific plan+month must never be (re)generated, independent of
// whether a transaction for it currently exists. This is necessary because deleting the
// transaction row alone is not enough: deletion frees up the (recurringPlanId, scheduledYearMonth)
// unique slot on `transactions`, so the next catch-up run would simply recreate it. This table is
// the durable record that survives the deletion.
@Entity(
    tableName = "recurring_occurrence_exceptions",
    indices = [Index(value = ["recurringPlanId", "scheduledYearMonth"], unique = true)]
)
data class RecurringOccurrenceExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recurringPlanId: Long,
    val scheduledYearMonth: String,
    val exceptionType: OccurrenceExceptionType = OccurrenceExceptionType.SKIPPED,
    val createdAt: Long = System.currentTimeMillis()
)

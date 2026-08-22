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
    val sourceTitle: String? = null,
    // True once the user has edited this specific occurrence independently of its plan (see
    // RecurringOccurrenceManager.editOccurrenceOnly). A bulk "edit this and future" update never
    // touches rows with this set, so a deliberate per-occurrence override always sticks.
    @ColumnInfo(defaultValue = "0")
    val isOccurrenceModified: Boolean = false,

    // Step 18: at most one receipt image per transaction. All nullable/defaulted so every
    // pre-Step-18 and legacy-imported transaction defaults to "no receipt" with no migration
    // needed beyond adding these columns - see ReceiptRepository for how they're populated.
    // Stable cross-device id for the receipt (also the Storage object's folder segment - see
    // FirebaseReceiptStorageRepository). Independent of this row's local Room id.
    val receiptId: String? = null,
    // Durable Firebase Storage object path, e.g. "users/{uid}/receipts/{receiptId}/{fileName}".
    // Null until the image has actually finished uploading - see receiptUploadPending.
    val receiptStoragePath: String? = null,
    // App-private cached file URI (content:// via FileProvider or a plain file path) so the
    // receipt remains viewable offline immediately after being attached, before/without a
    // successful upload.
    val receiptLocalUri: String? = null,
    val receiptMimeType: String? = null,
    val receiptUpdatedAt: Long? = null,
    // True from the moment a receipt is attached/replaced until its upload to Storage is
    // confirmed successful. Retried at the same trigger points as Firestore sync - see
    // ReceiptRepository.retryPendingUploads.
    @ColumnInfo(defaultValue = "0")
    val receiptUploadPending: Boolean = false,

    // Step 19: only set when this transaction was entered in a currency other than the account's
    // base currency. amountInCents above always holds the converted base-currency value (so every
    // existing query/report/budget calculation keeps working unchanged); these four fields are
    // purely a historical record of the original entry - never used to recompute amountInCents
    // later, so a transaction's displayed value never silently changes when today's rate moves.
    val originalAmountCents: Long? = null,
    // ISO 4217 currency code, e.g. "USD".
    val originalCurrencyCode: String? = null,
    // Base-currency units per 1 unit of originalCurrencyCode, at the moment this transaction was
    // saved.
    val conversionRate: Double? = null,
    val rateTimestampEpochMillis: Long? = null
)

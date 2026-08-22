package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.TransactionType

data class AddTransactionUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    // Only shown/used when editing a single generated occurrence (see isGeneratedOccurrence);
    // manual transactions have no title of their own, they're identified by category alone.
    val title: String = "",
    val amountText: String = "",
    val category: String? = null,
    val dateMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val titleError: String? = null,
    val amountError: String? = null,
    val categoryError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    // True once the loaded transaction is confirmed to be an automatically generated occurrence.
    // Drives the "edit this transaction" restrictions: type is fixed, the date must stay within
    // scheduledYearMonth, and save routes through RecurringOccurrenceManager instead of a plain
    // transaction update so the recurring-link fields are preserved.
    val isGeneratedOccurrence: Boolean = false,
    val scheduledYearMonth: String? = null,
    // Step 18: at most one receipt. receiptLocalPath is preferred for display when present
    // (instant, works offline); receiptRemoteUrl is only populated when this device has no local
    // cache of the receipt (e.g. pulled from another device) and a Storage download URL was
    // resolved instead - see AddTransactionViewModel.
    val receiptLocalPath: String? = null,
    val receiptStoragePath: String? = null,
    val receiptRemoteUrl: String? = null,
    val isReceiptBusy: Boolean = false,
    val receiptError: String? = null,

    // Step 19: optional foreign-currency entry. When disabled, amountText is the base-currency
    // amount exactly as before Step 19 - the streamlined default flow is unchanged. When enabled,
    // foreignAmountText is what the user types and amountText is kept in sync as the converted
    // base-currency preview (recomputed whenever the amount or the fetched rate changes).
    val useForeignCurrency: Boolean = false,
    val foreignCurrencyCode: String = "USD",
    val foreignAmountText: String = "",
    val isFetchingRate: Boolean = false,
    val conversionRate: Double? = null,
    val rateTimestampEpochMillis: Long? = null,
    val rateIsFromCache: Boolean = false,
    val rateError: String? = null
) {
    val hasReceipt: Boolean get() = receiptLocalPath != null || receiptStoragePath != null
}

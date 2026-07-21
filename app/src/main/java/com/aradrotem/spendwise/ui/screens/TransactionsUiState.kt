package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.TransactionEntity

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = true
)

// What the generated-transaction action menu should offer for a specific occurrence, resolved
// asynchronously (needs a plan lookup) before the menu is shown - see
// TransactionsViewModel.loadActionMenuInfo.
data class GeneratedTransactionActionInfo(
    // False if the source plan was deleted (or the transaction predates plan linking); when
    // false, "Manage recurring plan"/"Manage installment plan" and both "and future" actions are hidden.
    val planExists: Boolean,
    // False when the plan is Stopped/Completed, or this is the final installment - there is no
    // "future" left for either "and future" action to meaningfully affect.
    val canActOnFuture: Boolean,
    // Installment occurrences are managed primarily through the plan screen (see Step 13), so the
    // Transactions screen routes them through a restricted two-tier menu instead of the flexible
    // standing-order/salary menu. Derived from the transaction's own installmentNumber, which is
    // only ever set on installment-plan occurrences - this works even if the source plan was since
    // deleted, unlike a plan-type lookup which would require planExists to be true.
    val isInstallmentOccurrence: Boolean
)

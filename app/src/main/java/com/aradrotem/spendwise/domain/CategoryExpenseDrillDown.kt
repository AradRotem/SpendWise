package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType

// One transaction row shown in the category drill-down. `timestampMillis` (not an epoch day) is
// kept deliberately, since TransactionEntity itself stores dates as an epoch-millis timestamp and
// the existing `formatDate(timestampMillis)` formatter already operates on that same unit - adding
// an epoch-day conversion here would just be an unnecessary round trip for no benefit.
data class DisplayedCategoryTransaction(
    val transactionId: Long,
    val title: String,
    val timestampMillis: Long,
    val amountCents: Long
)

data class CategoryExpenseDetails(
    val category: String,
    val categoryDisplayName: String,
    val totalAmountCents: Long,
    val transactionCount: Int,
    val periodLabel: String,
    val displayedTransactions: List<DisplayedCategoryTransaction>,
    val isLimited: Boolean,
    val hiddenTransactionCount: Int
)

object CategoryExpenseDrillDownCalculator {

    private const val MAX_DISPLAYED = 5

    // Assumes `transactions` is already scoped to the selected analytics period (the same list
    // ReportsAnalyticsViewModel already fetched via observeInRange for the summary/chart
    // sections) - this performs no date-range filtering of its own, only type and category
    // filtering. totalAmountCents/transactionCount are computed from the FULL matching set before
    // the display list is limited to 5, so they always reflect every matching transaction even
    // when the row list is truncated.
    fun calculate(
        category: String,
        categoryDisplayName: String,
        periodLabel: String,
        transactions: List<TransactionEntity>
    ): CategoryExpenseDetails {
        val matching = transactions.filter { it.type == TransactionType.EXPENSE && it.category == category }
        val totalAmountCents = matching.sumOf { it.amountInCents }

        // Amount descending, then date descending, then id descending - all three deterministic,
        // so the same input always produces the same displayed rows and order.
        val sorted = matching.sortedWith(
            compareByDescending<TransactionEntity> { it.amountInCents }
                .thenByDescending { it.timestamp }
                .thenByDescending { it.id }
        )

        val displayed = sorted.take(MAX_DISPLAYED).map { transaction ->
            DisplayedCategoryTransaction(
                transactionId = transaction.id,
                title = displayTitle(transaction),
                timestampMillis = transaction.timestamp,
                amountCents = transaction.amountInCents
            )
        }

        return CategoryExpenseDetails(
            category = category,
            categoryDisplayName = categoryDisplayName,
            totalAmountCents = totalAmountCents,
            transactionCount = matching.size,
            periodLabel = periodLabel,
            displayedTransactions = displayed,
            isLimited = matching.size > MAX_DISPLAYED,
            hiddenTransactionCount = (matching.size - MAX_DISPLAYED).coerceAtLeast(0)
        )
    }
}

// Reuses the same "recurring-plan title snapshot first" idea as
// ui/components/TransactionRow.kt's transactionPrimaryText, but swaps its final fallback: showing
// the category name again here would be redundant (every row in this list already belongs to the
// one selected category), so the spec's "Untitled expense" wording is used instead, with the
// transaction's own note as a middle fallback.
private fun displayTitle(transaction: TransactionEntity): String =
    transaction.sourceTitle?.takeIf { it.isNotBlank() }
        ?: transaction.note.takeIf { it.isNotBlank() }
        ?: "Untitled expense"

package com.aradrotem.spendwise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.ui.format.formatAmountWithSign
import com.aradrotem.spendwise.ui.format.formatDate
import com.aradrotem.spendwise.util.formatCategoryDisplayName

@Composable
fun TransactionRow(transaction: TransactionEntity, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(text = transactionPrimaryText(transaction), style = MaterialTheme.typography.bodyLarge)
                if (hasReceipt(transaction)) {
                    Text(
                        text = " 📎",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
            transactionSecondaryText(transaction)?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            Text(text = formatDate(transaction.timestamp), style = MaterialTheme.typography.bodySmall)
            if (transaction.note.isNotBlank()) {
                Text(text = transaction.note, style = MaterialTheme.typography.bodySmall)
            }
        }

        val isExpense = transaction.type == TransactionType.EXPENSE
        val sign = if (isExpense) "-" else "+"
        val amountColor = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

        Text(
            text = formatAmountWithSign(sign, transaction.amountInCents),
            color = amountColor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// Primary title for a transaction row: the recurring-plan title snapshot when present and
// non-blank (see TransactionEntity.sourceTitle - never re-read from the live plan, so a later
// rename/delete never changes historical rows), otherwise the category. Extracted as a pure,
// Compose-free function so this fallback is directly unit-testable.
fun hasReceipt(transaction: TransactionEntity): Boolean =
    transaction.receiptLocalUri != null || transaction.receiptStoragePath != null

fun transactionPrimaryText(transaction: TransactionEntity): String =
    transaction.sourceTitle
        ?.takeIf { it.isNotBlank() }
        ?: formatCategoryDisplayName(transaction.category)

// Secondary line shown under the primary title for automatically generated transactions only:
// "<Category> · <Recurring expense/income, or Installment X of Y>". Null (no line) for manual
// transactions, which keep their original category-only appearance.
fun transactionSecondaryText(transaction: TransactionEntity): String? {
    if (!transaction.isAutomaticallyGenerated) return null
    return "${formatCategoryDisplayName(transaction.category)} · ${recurringLabel(transaction)}"
}

private fun recurringLabel(transaction: TransactionEntity): String {
    val installmentNumber = transaction.installmentNumber
    val totalInstallments = transaction.totalInstallments
    return when {
        installmentNumber != null && totalInstallments != null -> "Installment $installmentNumber of $totalInstallments"
        transaction.type == TransactionType.INCOME -> "Recurring income"
        else -> "Recurring expense"
    }
}

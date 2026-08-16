package com.aradrotem.spendwise.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.ui.format.formatAmountWithSign
import com.aradrotem.spendwise.ui.format.formatDate
import com.aradrotem.spendwise.util.formatCategoryDisplayName

// onReceiptClick is only ever invoked from the indicator's own click target (see
// ReceiptIndicator below), never from the row's own click/long-click handling passed in via
// [modifier] - the two are wired independently by the caller (TransactionsScreen), and Compose's
// clickable modifier already consumes the tap at the level it's attached, so a tap on the
// indicator never bubbles up to the parent row's gesture handling.
@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    modifier: Modifier = Modifier,
    onReceiptClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = transactionPrimaryText(transaction), style = MaterialTheme.typography.bodyLarge)
                if (hasReceipt(transaction)) {
                    ReceiptIndicator(onClick = onReceiptClick)
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

// A transaction "has a receipt" whenever either half of its Step 18 receipt reference is
// resolvable by the receipt viewer - a local cache (receiptLocalUri, from this device) or a
// durable Storage path (receiptStoragePath, e.g. pulled in from another device via Firestore
// sync, possibly with no local cache yet). Checking only the local URI would hide receipts synced
// in from other devices. Extracted as a plain, Compose-free function so this is directly
// unit-testable.
fun hasReceipt(transaction: TransactionEntity): Boolean =
    transaction.receiptLocalUri != null || transaction.receiptStoragePath != null

// Its own click target, independent of the row's click/long-click handling - a tap here is
// consumed by this Modifier.clickable and never reaches the parent row's gesture handling. Sized
// for a reasonable touch target without visually enlarging the glyph itself.
@Composable
private fun ReceiptIndicator(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = 2.dp)
            .size(40.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics { role = Role.Button; contentDescription = "View receipt" }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "📎", style = MaterialTheme.typography.bodyLarge)
    }
}

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

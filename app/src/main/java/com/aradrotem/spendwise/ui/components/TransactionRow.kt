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
import com.aradrotem.spendwise.ui.format.formatAmountInCents
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
            Text(text = formatCategoryDisplayName(transaction.category), style = MaterialTheme.typography.bodyLarge)
            Text(text = formatDate(transaction.timestamp), style = MaterialTheme.typography.bodySmall)
            if (transaction.note.isNotBlank()) {
                Text(text = transaction.note, style = MaterialTheme.typography.bodySmall)
            }
        }

        val isExpense = transaction.type == TransactionType.EXPENSE
        val sign = if (isExpense) "-" else "+"
        val amountColor = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

        Text(
            text = "$sign${formatAmountInCents(transaction.amountInCents)}",
            color = amountColor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatDate

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    onEditTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = viewModel(
        factory = TransactionsViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).transactionRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var actionTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionPendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            uiState.transactions.isEmpty() -> {
                Text(
                    text = "No transactions yet",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.transactions, key = { it.id }) { transaction ->
                        TransactionRow(
                            transaction = transaction,
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { actionTransaction = transaction }
                            )
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    actionTransaction?.let { transaction ->
        TransactionActionDialog(
            onEdit = {
                actionTransaction = null
                onEditTransaction(transaction.id)
            },
            onDelete = {
                actionTransaction = null
                transactionPendingDelete = transaction
            },
            onCancel = { actionTransaction = null }
        )
    }

    transactionPendingDelete?.let { transaction ->
        DeleteConfirmationDialog(
            onConfirm = {
                viewModel.deleteTransaction(transaction)
                transactionPendingDelete = null
            },
            onDismiss = { transactionPendingDelete = null }
        )
    }
}

@Composable
private fun TransactionRow(transaction: TransactionEntity, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = transaction.category.name, style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun TransactionActionDialog(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Transaction options",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete transaction?") },
        text = { Text("This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

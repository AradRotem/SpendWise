package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.ui.components.TransactionRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    onEditTransaction: (Long) -> Unit,
    onEditThisAndFuture: (planId: Long, transactionId: Long) -> Unit,
    onOpenRecurringPlan: (planId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = viewModel(
        factory = TransactionsViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).transactionRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).recurringPaymentRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).recurringOccurrenceManager
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var actionTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionPendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    var generatedActionTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var generatedActionInfo by remember { mutableStateOf<GeneratedTransactionActionInfo?>(null) }
    var occurrencePendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var occurrenceAndFuturePendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var laterGeneratedCount by remember { mutableStateOf(0) }

    fun openGeneratedActionMenu(transaction: TransactionEntity) {
        coroutineScope.launch {
            generatedActionInfo = viewModel.loadActionMenuInfo(transaction)
            generatedActionTransaction = transaction
        }
    }

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
                                onLongClick = {
                                    if (transaction.isAutomaticallyGenerated) {
                                        openGeneratedActionMenu(transaction)
                                    } else {
                                        actionTransaction = transaction
                                    }
                                }
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

    if (generatedActionTransaction != null && generatedActionInfo != null) {
        val transaction = generatedActionTransaction!!
        val info = generatedActionInfo!!
        GeneratedTransactionActionDialog(
            info = info,
            onEditThis = {
                generatedActionTransaction = null
                onEditTransaction(transaction.id)
            },
            onEditThisAndFuture = {
                generatedActionTransaction = null
                transaction.recurringPlanId?.let { onEditThisAndFuture(it, transaction.id) }
            },
            onDeleteThis = {
                generatedActionTransaction = null
                occurrencePendingDelete = transaction
            },
            onDeleteThisAndFuture = {
                generatedActionTransaction = null
                coroutineScope.launch {
                    laterGeneratedCount = viewModel.countLaterGeneratedTransactions(transaction)
                    occurrenceAndFuturePendingDelete = transaction
                }
            },
            onOpenPlan = {
                generatedActionTransaction = null
                transaction.recurringPlanId?.let(onOpenRecurringPlan)
            },
            onCancel = {
                generatedActionTransaction = null
                generatedActionInfo = null
            }
        )
    }

    occurrencePendingDelete?.let { transaction ->
        DeleteOccurrenceDialog(
            onConfirm = {
                viewModel.deleteOccurrenceOnly(transaction)
                occurrencePendingDelete = null
            },
            onDismiss = { occurrencePendingDelete = null }
        )
    }

    occurrenceAndFuturePendingDelete?.let { transaction ->
        DeleteOccurrenceAndFutureDialog(
            laterCount = laterGeneratedCount,
            onConfirm = {
                viewModel.deleteThisAndFuture(transaction)
                occurrenceAndFuturePendingDelete = null
            },
            onDismiss = { occurrenceAndFuturePendingDelete = null }
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

// Options for a single automatically generated transaction. Which scope each choice affects -
// one month only, this month and future months, or the whole plan - is spelled out in the label
// itself so the user never has to guess.
@Composable
private fun GeneratedTransactionActionDialog(
    info: GeneratedTransactionActionInfo,
    onEditThis: () -> Unit,
    onEditThisAndFuture: () -> Unit,
    onDeleteThis: () -> Unit,
    onDeleteThisAndFuture: () -> Unit,
    onOpenPlan: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Recurring transaction options",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onEditThis, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit this transaction")
                }
                if (info.canActOnFuture) {
                    TextButton(onClick = onEditThisAndFuture, modifier = Modifier.fillMaxWidth()) {
                        Text("Edit this and future transactions")
                    }
                }
                TextButton(onClick = onDeleteThis, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete this transaction", color = MaterialTheme.colorScheme.error)
                }
                if (info.canActOnFuture) {
                    TextButton(onClick = onDeleteThisAndFuture, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete this and future transactions", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (info.planExists) {
                    TextButton(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()) {
                        Text("Open recurring plan")
                    }
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
private fun DeleteOccurrenceDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this transaction?") },
        text = {
            Text(
                "This transaction will be removed and will not be regenerated. " +
                    "The recurring plan will continue for other months."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete this transaction", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteOccurrenceAndFutureDialog(laterCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this and future transactions?") },
        text = {
            val extra = if (laterCount > 0) {
                " and $laterCount later generated transaction${if (laterCount == 1) "" else "s"}"
            } else {
                ""
            }
            Text(
                "This transaction$extra will be removed. Earlier transactions will remain in " +
                    "your history. No future transactions will be generated from this plan."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete this and future", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

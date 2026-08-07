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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.R
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.ui.components.TransactionRow
import kotlinx.coroutines.launch

// A generated transaction and its resolved action-menu info, always set together (see
// openGeneratedActionMenu) so the two can never be observed independently/out of sync.
private data class GeneratedActionMenuState(val transaction: TransactionEntity, val info: GeneratedTransactionActionInfo)

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var actionTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionPendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    // Transaction and its resolved action info are held as ONE state object, set in a single
    // atomic assignment (see openGeneratedActionMenu below) rather than two separate
    // mutableStateOf fields. This is general Compose hygiene - two independently-mutable fields
    // for values that must always change together is a known footgun - but it is NOT what fixed
    // the "Manage installment plan is missing" report: Logcat diagnosis confirmed that bug was
    // actually correct behavior for a transaction whose recurringPlanId points at a genuinely
    // deleted plan (see resolveGeneratedTransactionActionInfo). Kept as a worthwhile hardening
    // change on its own merits, not as a fix for that specific report.
    var generatedActionMenu by remember { mutableStateOf<GeneratedActionMenuState?>(null) }
    var occurrencePendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var occurrenceAndFuturePendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var laterGeneratedCount by remember { mutableStateOf(0) }

    // Installment occurrences go through their own two-tier menu: each tier below owns its own
    // transaction reference so only one dialog is ever eligible to render at a time, instead of
    // layering boolean flags on top of the shared generatedActionMenu state.
    var installmentAdvancedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var installmentEditWarningTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    fun openGeneratedActionMenu(transaction: TransactionEntity) {
        coroutineScope.launch {
            val info = viewModel.loadActionMenuInfo(transaction)
            generatedActionMenu = GeneratedActionMenuState(transaction, info)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text("Transactions", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.transactions.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.empty_state_no_transactions),
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
                                        if (shouldShowGeneratedActionMenu(transaction)) {
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

    generatedActionMenu?.let { (transaction, info) ->
        if (info.isInstallmentOccurrence) {
            // Installment payments belong to one purchase, so day-to-day management routes to the
            // plan screen; only "Advanced occurrence actions" exposes a single-row edit.
            InstallmentOccurrenceActionDialog(
                info = info,
                onManagePlan = {
                    generatedActionMenu = null
                    transaction.recurringPlanId?.let(onOpenRecurringPlan)
                },
                onAdvancedActions = {
                    generatedActionMenu = null
                    installmentAdvancedTransaction = transaction
                },
                onCancel = { generatedActionMenu = null }
            )
        } else {
            // Standing orders and recurring salary keep the more flexible flat menu, since each
            // occurrence may legitimately differ, be skipped, or be changed on its own.
            RecurringOccurrenceActionDialog(
                info = info,
                onEditThis = {
                    generatedActionMenu = null
                    onEditTransaction(transaction.id)
                },
                onEditThisAndFuture = {
                    generatedActionMenu = null
                    transaction.recurringPlanId?.let { onEditThisAndFuture(it, transaction.id) }
                },
                onRemoveThis = {
                    generatedActionMenu = null
                    occurrencePendingDelete = transaction
                },
                onRemoveThisAndFuture = {
                    generatedActionMenu = null
                    coroutineScope.launch {
                        laterGeneratedCount = viewModel.countLaterGeneratedTransactions(transaction)
                        occurrenceAndFuturePendingDelete = transaction
                    }
                },
                onManagePlan = {
                    generatedActionMenu = null
                    transaction.recurringPlanId?.let(onOpenRecurringPlan)
                },
                onCancel = { generatedActionMenu = null }
            )
        }
    }

    installmentAdvancedTransaction?.let { transaction ->
        InstallmentAdvancedActionDialog(
            onEditRecordOnly = {
                installmentAdvancedTransaction = null
                installmentEditWarningTransaction = transaction
            },
            onCancel = { installmentAdvancedTransaction = null }
        )
    }

    installmentEditWarningTransaction?.let { transaction ->
        InstallmentEditWarningDialog(
            onConfirm = {
                installmentEditWarningTransaction = null
                onEditTransaction(transaction.id)
            },
            onDismiss = { installmentEditWarningTransaction = null }
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
                    Text(stringResource(R.string.action_edit))
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel))
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
        text = { Text(stringResource(R.string.dialog_action_cannot_be_undone)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// User-facing wording for recurring-occurrence actions, kept as named constants (not inline
// string literals) so a pure JUnit test (see TransactionsLogicTest) can assert against the exact
// same text the dialogs below show, without needing Compose UI testing. SpendWise only tracks
// transactions - it never talks to a bank, card provider, or merchant - so none of this wording
// may imply that editing or removing a record cancels a real external charge.
const val LABEL_MANAGE_INSTALLMENT_PLAN = "Manage installment plan"
const val LABEL_ADVANCED_OCCURRENCE_ACTIONS = "Advanced occurrence actions"
// Shown in place of "Manage installment plan" when the source plan can no longer be found (e.g.
// it was deleted) - see resolveGeneratedTransactionActionInfo.planExists. Explains the missing
// button instead of silently omitting it.
const val MESSAGE_ORIGINAL_INSTALLMENT_PLAN_MISSING = "The original installment plan no longer exists."
const val LABEL_EDIT_RECORD_ONLY = "Edit this record only"
const val WARNING_EDIT_INSTALLMENT_OCCURRENCE =
    "This changes only this SpendWise record.\nIt does not change the installment plan or the actual payment."

// Installments no longer expose single-occurrence removal at all (see this revision's spec): an
// installment series is one purchase, and removing one payment from history would create
// inconsistencies between the installment count, generated history, and plan completion state.
// Kept as a pure function (not inlined in the Composable) so its exact, minimal action set is
// directly assertable by a JUnit test without Compose UI testing.
fun installmentAdvancedActionLabels(): List<String> = listOf(LABEL_EDIT_RECORD_ONLY)

const val LABEL_EDIT_OCCURRENCE_ONLY = "Edit this occurrence only"
const val LABEL_EDIT_OCCURRENCE_AND_FUTURE = "Edit this and future occurrences"
const val LABEL_REMOVE_OCCURRENCE_FROM_HISTORY = "Remove this occurrence from history"
const val LABEL_REMOVE_OCCURRENCE_AND_FUTURE_FROM_HISTORY = "Remove this and future occurrences from history"
const val LABEL_MANAGE_RECURRING_PLAN = "Manage recurring plan"

// First-level menu for a generated installment occurrence. Deliberately narrow - installment
// payments belong to one purchase, so series-level changes are made on the plan screen, not here.
@Composable
private fun InstallmentOccurrenceActionDialog(
    info: GeneratedTransactionActionInfo,
    onManagePlan: () -> Unit,
    onAdvancedActions: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(text = "Installment options", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (info.planExists) {
                    TextButton(onClick = onManagePlan, modifier = Modifier.fillMaxWidth()) {
                        Text(LABEL_MANAGE_INSTALLMENT_PLAN)
                    }
                } else {
                    Text(
                        MESSAGE_ORIGINAL_INSTALLMENT_PLAN_MISSING,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(onClick = onAdvancedActions, modifier = Modifier.fillMaxWidth()) {
                    Text(LABEL_ADVANCED_OCCURRENCE_ACTIONS)
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
        confirmButton = {}
    )
}

// Second-level menu holding the occurrence-only actions for an installment - reached only through
// "Advanced occurrence actions". Deliberately contains only a single-record edit: installments no
// longer expose any occurrence-only remove/delete action (see installmentAdvancedActionLabels).
@Composable
private fun InstallmentAdvancedActionDialog(
    onEditRecordOnly: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(text = LABEL_ADVANCED_OCCURRENCE_ACTIONS, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEditRecordOnly, modifier = Modifier.fillMaxWidth()) {
                    Text(LABEL_EDIT_RECORD_ONLY)
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun InstallmentEditWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit this record only?") },
        text = { Text(WARNING_EDIT_INSTALLMENT_OCCURRENCE) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// Flat menu for a standing-order or recurring-salary occurrence. These stay more flexible than
// installments because each occurrence may legitimately differ, be skipped, or be changed on its
// own. Which scope each choice affects - one occurrence, this and future occurrences, or the
// whole plan - is spelled out in the label itself so the user never has to guess, and none of it
// implies SpendWise is cancelling a real external charge.
@Composable
private fun RecurringOccurrenceActionDialog(
    info: GeneratedTransactionActionInfo,
    onEditThis: () -> Unit,
    onEditThisAndFuture: () -> Unit,
    onRemoveThis: () -> Unit,
    onRemoveThisAndFuture: () -> Unit,
    onManagePlan: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Recurring occurrence options",
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
                    Text(LABEL_EDIT_OCCURRENCE_ONLY)
                }
                if (info.canActOnFuture) {
                    TextButton(onClick = onEditThisAndFuture, modifier = Modifier.fillMaxWidth()) {
                        Text(LABEL_EDIT_OCCURRENCE_AND_FUTURE)
                    }
                }
                TextButton(onClick = onRemoveThis, modifier = Modifier.fillMaxWidth()) {
                    Text(LABEL_REMOVE_OCCURRENCE_FROM_HISTORY, color = MaterialTheme.colorScheme.error)
                }
                if (info.canActOnFuture) {
                    TextButton(onClick = onRemoveThisAndFuture, modifier = Modifier.fillMaxWidth()) {
                        Text(LABEL_REMOVE_OCCURRENCE_AND_FUTURE_FROM_HISTORY, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (info.planExists) {
                    TextButton(onClick = onManagePlan, modifier = Modifier.fillMaxWidth()) {
                        Text(LABEL_MANAGE_RECURRING_PLAN)
                    }
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel))
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
        title = { Text("Remove this occurrence from history?") },
        text = {
            Text(
                "This removes only this record from SpendWise history and it will not be regenerated. " +
                    "It does not cancel the real payment. The recurring plan will continue for other occurrences."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(LABEL_REMOVE_OCCURRENCE_FROM_HISTORY, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DeleteOccurrenceAndFutureDialog(laterCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove this and future occurrences from history?") },
        text = {
            val extra = if (laterCount > 0) {
                " and $laterCount later generated occurrence${if (laterCount == 1) "" else "s"}"
            } else {
                ""
            }
            Text(
                "This occurrence$extra will be removed from SpendWise history. It does not cancel any real " +
                    "payments. Earlier occurrences will remain in your history. No future occurrences will be " +
                    "generated from this plan."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(LABEL_REMOVE_OCCURRENCE_AND_FUTURE_FROM_HISTORY, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

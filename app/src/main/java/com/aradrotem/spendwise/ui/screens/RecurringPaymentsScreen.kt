package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.aradrotem.spendwise.data.local.RecurringPaymentPlanEntity
import com.aradrotem.spendwise.data.local.RecurringPlanStatus
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatDate
import com.aradrotem.spendwise.util.formatCategoryDisplayName
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringPaymentsScreen(
    onAddPlan: () -> Unit,
    onEditPlan: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringPlansViewModel = viewModel(
        factory = RecurringPlansViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).recurringPaymentRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).transactionRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).recurringOccurrenceExceptionRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).recurringPaymentGenerator
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var planPendingDelete by remember { mutableStateOf<RecurringPaymentPlanEntity?>(null) }
    var planPendingStop by remember { mutableStateOf<RecurringPaymentPlanEntity?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Recurring Transactions") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onAddPlan) {
                    Text("+ Add plan")
                }
            }

            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.items.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyPlansState(onAddClick = onAddPlan)
                }
                else -> {
                    val grouped = uiState.items.groupBy { it.plan.status }
                    // Active/Paused first since they're what the user is most likely to act on;
                    // Stopped/Completed last as they're effectively archived.
                    val sections = listOf(
                        "Active" to grouped[RecurringPlanStatus.ACTIVE].orEmpty(),
                        "Paused" to grouped[RecurringPlanStatus.PAUSED].orEmpty(),
                        "Stopped" to grouped[RecurringPlanStatus.STOPPED].orEmpty(),
                        "Completed" to grouped[RecurringPlanStatus.COMPLETED].orEmpty()
                    ).filter { it.second.isNotEmpty() }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        sections.forEach { (label, plans) ->
                            item(key = "header-$label") {
                                Text(
                                    text = "$label (${plans.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            items(plans, key = { it.plan.id }) { item ->
                                RecurringPlanCard(
                                    item = item,
                                    onEdit = { onEditPlan(item.plan.id) },
                                    onPause = { viewModel.onPause(item.plan.id) },
                                    onResume = { viewModel.onResume(item.plan.id) },
                                    onRequestStop = { planPendingStop = item.plan },
                                    onRequestDelete = { planPendingDelete = item.plan }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    planPendingStop?.let { plan ->
        StopPlanDialog(
            onConfirm = {
                viewModel.onStop(plan.id)
                planPendingStop = null
            },
            onDismiss = { planPendingStop = null }
        )
    }

    planPendingDelete?.let { plan ->
        DeletePlanDialog(
            onConfirm = {
                viewModel.onDelete(plan)
                planPendingDelete = null
            },
            onDismiss = { planPendingDelete = null }
        )
    }
}

@Composable
private fun EmptyPlansState(onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("No recurring transactions yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Add a monthly expense, an installment purchase, or a monthly salary to have " +
                "SpendWise record its transactions automatically.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onAddClick) {
            Text("+ Add plan")
        }
    }
}

@Composable
private fun RecurringPlanCard(
    item: RecurringPlanListItem,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRequestStop: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = item.plan
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(plan.title, style = MaterialTheme.typography.titleMedium)
                Text(statusLabel(plan.status), color = statusColor(plan.status))
            }

            Text("${typeLabel(plan.type)} · ${formatCategoryDisplayName(plan.categoryName)}")
            Text(amountLabel(plan))

            if (plan.type == RecurringPlanType.INSTALLMENT) {
                installmentProgressText(
                    status = plan.status,
                    totalInstallments = plan.totalInstallments ?: 0,
                    remainingGeneratedInstallments = item.generatedCount,
                    deletedInstallmentOccurrences = item.deletedInstallmentOccurrences
                ).forEach { line -> Text(line) }
            }

            item.nextDueDate?.let { nextDueDate ->
                Text("Next due: ${formatDate(nextDueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())}")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (plan.status) {
                    RecurringPlanStatus.ACTIVE -> {
                        TextButton(onClick = onPause) { Text("Pause") }
                        TextButton(onClick = onRequestStop) { Text("Stop", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                    }
                    RecurringPlanStatus.PAUSED -> {
                        TextButton(onClick = onResume) { Text("Resume") }
                        TextButton(onClick = onRequestStop) { Text("Stop", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                    }
                    RecurringPlanStatus.STOPPED -> {
                        TextButton(onClick = onResume) { Text("Restore") }
                        TextButton(onClick = onRequestDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                    }
                    RecurringPlanStatus.COMPLETED -> {
                        // Finished on its own after the final installment; nothing left to
                        // restore, so only Delete is offered.
                        TextButton(onClick = onRequestDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopPlanDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop this recurring plan?") },
        text = {
            Text(
                "Existing transactions will remain in your transaction history. " +
                    "No future transactions will be generated until you restore this plan."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Stop", color = MaterialTheme.colorScheme.error)
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
private fun DeletePlanDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this recurring plan?") },
        text = {
            Text(
                "Existing transactions created by this plan will remain in your transaction history. " +
                    "No future transactions will be generated."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun typeLabel(type: RecurringPlanType): String = when (type) {
    RecurringPlanType.MONTHLY_RECURRING -> "Monthly expense"
    RecurringPlanType.INSTALLMENT -> "Installment purchase"
    RecurringPlanType.MONTHLY_SALARY -> "Monthly salary"
}

private fun statusLabel(status: RecurringPlanStatus): String = when (status) {
    RecurringPlanStatus.ACTIVE -> "Active"
    RecurringPlanStatus.PAUSED -> "Paused"
    RecurringPlanStatus.STOPPED -> "Stopped"
    RecurringPlanStatus.COMPLETED -> "Completed"
}

@Composable
private fun statusColor(status: RecurringPlanStatus) = when (status) {
    RecurringPlanStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    RecurringPlanStatus.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
    RecurringPlanStatus.STOPPED -> MaterialTheme.colorScheme.error
    RecurringPlanStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun amountLabel(plan: RecurringPaymentPlanEntity): String = when (plan.type) {
    RecurringPlanType.MONTHLY_RECURRING, RecurringPlanType.MONTHLY_SALARY -> "${formatAmountInCents(plan.amountInCents ?: 0L)} / month"
    RecurringPlanType.INSTALLMENT -> "${formatAmountInCents(plan.totalAmountInCents ?: 0L)} total"
}

// One or two lines of installment-progress text. Only a Completed plan with at least one
// intentionally deleted occurrence gets the two-line "remain in history" / "deleted manually"
// form - Active/Paused/Stopped plans, and a Completed plan with nothing deleted, keep the plain
// "X of Y payments" wording so a deleted-but-Completed plan doesn't read as though a payment is
// still outstanding (it isn't: the deletion is historical only, see RecurringOccurrenceManager;
// the plan is never reactivated, its installment count is never changed, and the occurrence is
// never regenerated). Kept as a top-level pure function - not private, unlike its sibling label
// helpers in this file - specifically so it's directly unit-testable (see RecurringPlansLogicTest).
fun installmentProgressText(
    status: RecurringPlanStatus,
    totalInstallments: Int,
    remainingGeneratedInstallments: Int,
    deletedInstallmentOccurrences: Int
): List<String> {
    if (status != RecurringPlanStatus.COMPLETED || deletedInstallmentOccurrences <= 0) {
        return listOf("$remainingGeneratedInstallments of $totalInstallments payments")
    }
    val deletedLine = if (deletedInstallmentOccurrences == 1) {
        "1 payment was deleted manually"
    } else {
        "$deletedInstallmentOccurrences payments were deleted manually"
    }
    return listOf(
        "$remainingGeneratedInstallments of $totalInstallments payments remain in history",
        deletedLine
    )
}

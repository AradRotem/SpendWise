package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.R
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatDate
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    groupId: Long,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onViewSettlement: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupDetailsViewModel = viewModel(
        factory = GroupDetailsViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.groupExpenseRepository,
            groupId
        )
    ),
    app: SpendWiseApplication = LocalContext.current.applicationContext as SpendWiseApplication,
    sharingViewModel: GroupSharingViewModel = viewModel(
        factory = GroupSharingViewModel.factory(
            app.repositories.groupExpenseRepository,
            app.repositories.groupCloudRepository,
            groupId,
            app.authRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sharingUiState by sharingViewModel.uiState.collectAsStateWithLifecycle()
    var expensePendingDelete by remember { mutableStateOf<GroupExpenseEntity?>(null) }
    var showInviteDialog by remember { mutableStateOf(false) }

    // Step 19: refresh just this group on entry - once its groupSyncId is known (the local group
    // row has loaded), rather than every group like the Groups list screen does. A local-only
    // group never has a groupSyncId, so this is a no-op for the pre-Step-19 common case.
    val groupSyncId = uiState.group?.groupSyncId
    LaunchedEffect(groupSyncId) {
        if (groupSyncId != null) {
            app.sharedGroupSyncEngine.syncGroup(groupSyncId, uiState.group?.name ?: "")
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.group?.name ?: "Group") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                },
                actions = {
                    if (app.repositories.groupCloudRepository != null) {
                        TextButton(onClick = { showInviteDialog = true }) { Text("Invite") }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.canAddExpense) {
                ExtendedFloatingActionButton(onClick = onAddExpense) { Text("+ Add expense") }
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                // Extra bottom padding beyond the usual 16.dp so the "+ Add expense" FAB - which
                // floats on top of the list, outside Scaffold's innerPadding - never visually or
                // functionally covers the last expense card's overflow menu button.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    GroupOverviewCard(uiState, onViewSettlement, memberCount = uiState.members.size)
                }
                if (!uiState.canAddExpense) {
                    item {
                        Text(
                            "Add at least two members to this group before recording a shared expense.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Text("Expenses", style = MaterialTheme.typography.titleMedium)
                }
                if (uiState.expenses.isEmpty()) {
                    item {
                        Text(
                            "No shared expenses yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(uiState.expenses, key = { it.expense.id }) { item ->
                    GroupExpenseRow(
                        item = item,
                        onEdit = { onEditExpense(item.expense.id) },
                        onRequestDelete = { expensePendingDelete = item.expense }
                    )
                }
            }
        }
    }

    if (showInviteDialog) {
        InviteByEmailDialog(
            uiState = sharingUiState,
            onInvite = sharingViewModel::inviteByEmail,
            onDismiss = {
                showInviteDialog = false
                sharingViewModel.dismissMessage()
            }
        )
    }

    expensePendingDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expensePendingDelete = null },
            title = { Text("Delete \"${expense.title}\"?") },
            text = { Text("Balances will update immediately. " + stringResource(R.string.dialog_action_cannot_be_undone)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteExpense(expense)
                    expensePendingDelete = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { expensePendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

// Step 19 Part 5: invite a real SpendWise user to this group by email. Upgrading a purely local
// group to a shared one (if it isn't already) happens transparently inside
// GroupSharingViewModel.inviteByEmail the first time this is used - nothing here needs to know
// whether the group was already shared.
@Composable
private fun InviteByEmailDialog(uiState: GroupSharingUiState, onInvite: (String) -> Unit, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite by email") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                uiState.inviteError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                uiState.inviteSuccessMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (uiState.sentInvitations.isNotEmpty()) {
                    Text("Already invited:", style = MaterialTheme.typography.labelMedium)
                    uiState.sentInvitations.forEach { invitation ->
                        Text("${invitation.inviteeEmail} — ${invitation.status}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onInvite(email) }) { Text("Send invite") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun GroupOverviewCard(uiState: GroupDetailsUiState, onViewSettlement: () -> Unit, memberCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$memberCount member${if (memberCount == 1) "" else "s"}")
            Text("Total spent: ${formatAmountInCents(uiState.totalSpentCents)}")
            Text("Balances", style = MaterialTheme.typography.titleSmall)
            uiState.balances.forEach { balance ->
                val name = uiState.memberNameById[balance.memberId] ?: "Unknown"
                Text(balanceLine(name, balance.netBalanceCents))
            }
            TextButton(onClick = onViewSettlement) { Text("View settlement suggestions") }
        }
    }
}

// Not private: exercised directly by GroupExpenseRowTest, mirroring GroupSummaryCard.
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupExpenseRow(
    item: GroupExpenseListItem,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val expense = item.expense

    // Tapping the card opens Edit (regardless of split method - see GroupExpenseFormScreen, which
    // already loads EQUAL and CUSTOM expenses identically). The overflow button below is a nested
    // clickable target, so its own taps are consumed there and never also trigger this card's
    // onClick.
    Card(modifier = modifier.fillMaxWidth().combinedClickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(expense.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatEpochDay(expense.dateEpochDay))
                Text("${formatAmountInCents(expense.amountCents)} · paid by ${item.payerName}")
                Text(
                    "Split ${splitMethodLabel(expense.splitMethod)} between ${item.participantNames.joinToString(", ")}",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                TextButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.semantics { contentDescription = "More options for ${expense.title}" }
                ) { Text("⋮") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuExpanded = false; onRequestDelete() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_cancel)) }, onClick = { menuExpanded = false })
                }
            }
        }
    }
}

private fun splitMethodLabel(method: GroupSplitMethod): String = when (method) {
    GroupSplitMethod.EQUAL -> "equally"
    GroupSplitMethod.CUSTOM -> "with custom amounts"
}

private fun formatEpochDay(epochDay: Long): String {
    val millis = LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return formatDate(millis)
}

// "X owes Y" / "Y is owed X" style line matching the spec's settlement wording, but per-member.
private fun balanceLine(name: String, netBalanceCents: Long): String = when {
    netBalanceCents > 0L -> "$name should receive ${formatAmountInCents(netBalanceCents)}"
    netBalanceCents < 0L -> "$name owes ${formatAmountInCents(-netBalanceCents)}"
    else -> "$name is settled"
}

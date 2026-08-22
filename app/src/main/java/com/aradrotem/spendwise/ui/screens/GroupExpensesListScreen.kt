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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.R
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import com.aradrotem.spendwise.domain.GroupInvitation
import com.aradrotem.spendwise.ui.format.formatAmountInCents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupExpensesListScreen(
    onAddGroup: () -> Unit,
    onEditGroup: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsListViewModel = viewModel(
        factory = GroupsListViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.groupExpenseRepository
        )
    ),
    app: SpendWiseApplication = LocalContext.current.applicationContext as SpendWiseApplication,
    invitationsViewModel: IncomingInvitationsViewModel = viewModel(
        factory = IncomingInvitationsViewModel.factory(
            app.repositories.groupCloudRepository,
            app.authRepository,
            app.sharedGroupSyncEngine
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val invitationsUiState by invitationsViewModel.uiState.collectAsStateWithLifecycle()
    var groupPendingDelete by remember { mutableStateOf<ExpenseGroupEntity?>(null) }

    // Step 19: refresh shared-group membership/data on every entry to this screen. Room's existing
    // Flow-based observeGroups already shows cached data immediately - this only runs the network
    // pull in the background, same "show cached, sync in background, let Flow update the UI"
    // pattern as the rest of the app.
    LaunchedEffect(Unit) { app.sharedGroupSyncEngine.syncAll() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Group expenses") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddGroup,
                modifier = Modifier.semantics { contentDescription = "Add group" }
            ) { Text("+") }
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.items.isEmpty() && invitationsUiState.invitations.isEmpty() -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { EmptyGroupsState(onAddGroup) }

            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                // Extra bottom padding beyond the usual 16.dp so the "+" FAB - which floats on top
                // of the list, outside Scaffold's innerPadding - never covers the last group's
                // overflow menu button. Matches GroupDetailsScreen's LazyColumn for the same reason.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (invitationsUiState.invitations.isNotEmpty()) {
                    item {
                        PendingInvitationsSection(
                            invitations = invitationsUiState.invitations,
                            actionError = invitationsUiState.actionError,
                            onAccept = invitationsViewModel::accept,
                            onDecline = invitationsViewModel::decline
                        )
                    }
                }
                if (uiState.items.isEmpty()) {
                    item { EmptyGroupsState(onAddGroup) }
                }
                items(uiState.items, key = { it.group.id }) { item ->
                    GroupSummaryCard(
                        item = item,
                        onClick = { onOpenGroup(item.group.id) },
                        onEdit = { onEditGroup(item.group.id) },
                        onRequestDelete = { groupPendingDelete = item.group }
                    )
                }
            }
        }
    }

    groupPendingDelete?.let { group ->
        DeleteGroupDialog(
            groupName = group.name,
            onConfirm = {
                viewModel.onDeleteGroup(group)
                groupPendingDelete = null
            },
            onDismiss = { groupPendingDelete = null }
        )
    }
}

// Step 19 Part 5: invitations the current user received, addressed to their signed-in email.
// Accept/decline are optimistic - the list updates itself via observeIncomingInvitations once
// Firestore reflects the status change, so there's no local "pending" state to manage here.
@Composable
private fun PendingInvitationsSection(
    invitations: List<GroupInvitation>,
    actionError: String?,
    onAccept: (GroupInvitation) -> Unit,
    onDecline: (GroupInvitation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pending invitations", style = MaterialTheme.typography.titleMedium)
        // Previously silently dropped: an accept/decline failure (e.g. a permission error) left
        // the user with zero feedback and an invitation that just never resolved.
        actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        invitations.forEach { invitation ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("\"${invitation.groupName}\"", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Invited by ${invitation.inviterEmail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onAccept(invitation) }) { Text("Accept") }
                        TextButton(onClick = { onDecline(invitation) }) { Text("Decline") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGroupsState(onAddGroup: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("No groups yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Create a group to split shared expenses with friends or family and track who owes whom.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onAddGroup) { Text("+ Create group") }
    }
}

// Not private: exercised directly (without an Application/database dependency) by
// GroupSummaryCardTest, mirroring RecurringPlanTypeSelectorTest.
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupSummaryCard(
    item: GroupSummary,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // The whole card opens Group Details on tap or long press. The overflow button below is a
    // nested clickable target, so its own taps are consumed there and never fall through to this
    // outer combinedClickable - opening the menu never also navigates.
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.group.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    TextButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.semantics { contentDescription = "More options for ${item.group.name}" }
                    ) { Text("⋮") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Edit group") }, onClick = { menuExpanded = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete group") }, onClick = { menuExpanded = false; onRequestDelete() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_cancel)) }, onClick = { menuExpanded = false })
                    }
                }
            }
            Text("${item.memberCount} member${if (item.memberCount == 1) "" else "s"} · ${item.expenseCount} expense${if (item.expenseCount == 1) "" else "s"}")
            Text("Total spent: ${formatAmountInCents(item.totalSpentCents)}")
            Text(
                text = if (item.isSettled) "Settled" else "Unsettled",
                color = if (item.isSettled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun DeleteGroupDialog(groupName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$groupName\"?") },
        text = {
            Text(
                "All members and shared expenses in this group will also be deleted. " +
                    stringResource(R.string.dialog_action_cannot_be_undone)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

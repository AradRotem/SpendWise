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
import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
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
            (LocalContext.current.applicationContext as SpendWiseApplication).groupExpenseRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var groupPendingDelete by remember { mutableStateOf<ExpenseGroupEntity?>(null) }

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
            FloatingActionButton(onClick = onAddGroup) { Text("+") }
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.items.isEmpty() -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { EmptyGroupsState(onAddGroup) }

            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                Text(item.group.name, style = MaterialTheme.typography.titleMedium)
                Box {
                    TextButton(onClick = { menuExpanded = true }) { Text("⋮") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Edit group") }, onClick = { menuExpanded = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete group") }, onClick = { menuExpanded = false; onRequestDelete() })
                        DropdownMenuItem(text = { Text("Cancel") }, onClick = { menuExpanded = false })
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
        text = { Text("All members and shared expenses in this group will also be deleted. This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

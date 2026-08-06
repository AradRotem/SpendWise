package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.R
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.GroupMemberEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFormScreen(
    groupId: Long?,
    onSaveSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupFormViewModel = viewModel(
        factory = GroupFormViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).groupExpenseRepository,
            groupId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var memberPendingDelete by remember { mutableStateOf<GroupMemberEntity?>(null) }
    var memberPendingRename by remember { mutableStateOf<GroupMemberEntity?>(null) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaveSuccess()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit group" else "Create group") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Group name") },
                    singleLine = true,
                    isError = uiState.nameError != null,
                    supportingText = { uiState.nameError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Text("Members (${uiState.memberCount})", style = MaterialTheme.typography.titleMedium)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.newMemberText,
                        onValueChange = viewModel::onNewMemberTextChange,
                        label = { Text("Member name") },
                        singleLine = true,
                        isError = uiState.newMemberError != null,
                        supportingText = { uiState.newMemberError?.let { Text(it) } },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::onAddMember) { Text(stringResource(R.string.action_add)) }
                }

                if (uiState.isEditMode) {
                    uiState.existingMembers.forEach { member ->
                        MemberRow(
                            name = member.name,
                            onRename = { memberPendingRename = member },
                            onDelete = { memberPendingDelete = member }
                        )
                    }
                } else {
                    uiState.pendingMemberNames.forEach { name ->
                        MemberRow(
                            name = name,
                            onRename = null,
                            onDelete = { viewModel.onRemovePendingMember(name) }
                        )
                    }
                }

                if (!uiState.isEditMode) {
                    Text(
                        text = "You can save a group with fewer than two members, but adding shared expenses " +
                            "requires at least two.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.saveError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = viewModel::save,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isEditMode) "Save" else "Create group")
                }
            }
        }
    }

    memberPendingDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberPendingDelete = null },
            title = { Text("Delete \"${member.name}\"?") },
            text = { Text("This member can only be removed if they have no related expenses in this group.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteExistingMember(member)
                    memberPendingDelete = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    memberPendingRename?.let { member ->
        RenameMemberDialog(
            member = member,
            onConfirm = { newName ->
                viewModel.onRenameExistingMember(member, newName)
                memberPendingRename = null
            },
            onDismiss = { memberPendingRename = null }
        )
    }

    uiState.memberActionError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMemberActionError,
            title = { Text("Can't complete this action") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMemberActionError) { Text(stringResource(R.string.action_ok)) }
            }
        )
    }
}

@Composable
private fun MemberRow(name: String, onRename: (() -> Unit)?, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name)
        Row {
            onRename?.let { TextButton(onClick = it) { Text("Rename") } }
            TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun RenameMemberDialog(member: GroupMemberEntity, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(member.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename member") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

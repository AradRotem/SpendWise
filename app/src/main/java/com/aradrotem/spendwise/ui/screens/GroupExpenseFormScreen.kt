package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.R
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.GroupMemberEntity
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import com.aradrotem.spendwise.ui.components.DateField
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupExpenseFormScreen(
    groupId: Long,
    expenseId: Long?,
    onSaveSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupExpenseFormViewModel = viewModel(
        factory = GroupExpenseFormViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).groupExpenseRepository,
            groupId,
            expenseId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaveSuccess()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit expense" else "Add expense") },
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
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text("Title") },
                    singleLine = true,
                    isError = uiState.titleError != null,
                    supportingText = { uiState.titleError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.amountText,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text("Amount") },
                    singleLine = true,
                    isError = uiState.amountError != null,
                    supportingText = { uiState.amountError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                DateField(
                    dateMillis = epochDayToMillis(uiState.dateEpochDay),
                    onDateSelected = { millis -> viewModel.onDateChange(millisToEpochDay(millis)) },
                    label = "Date"
                )

                HorizontalDivider()
                Text("Paid by", style = MaterialTheme.typography.titleMedium)
                uiState.payerError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                uiState.members.forEach { member ->
                    PayerOption(member = member, selected = uiState.payerMemberId == member.id, onSelect = { viewModel.onPayerChange(member.id) })
                }

                HorizontalDivider()
                Text("Participants", style = MaterialTheme.typography.titleMedium)
                uiState.participantsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                uiState.members.forEach { member ->
                    ParticipantRow(
                        member = member,
                        checked = member.id in uiState.participantMemberIds,
                        onCheckedChange = { viewModel.onToggleParticipant(member.id) },
                        equalShareCents = uiState.equalSharesPreview?.get(member.id).takeIf { uiState.splitMethod == GroupSplitMethod.EQUAL },
                        splitMethod = uiState.splitMethod,
                        customShareText = uiState.customShareTexts[member.id].orEmpty(),
                        onCustomShareTextChange = { text -> viewModel.onCustomShareTextChange(member.id, text) },
                        customShareError = uiState.customShareErrors[member.id]
                    )
                }

                HorizontalDivider()
                Text("Split method", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SplitMethodOption("Equal", uiState.splitMethod == GroupSplitMethod.EQUAL) {
                        viewModel.onSplitMethodChange(GroupSplitMethod.EQUAL)
                    }
                    SplitMethodOption("Custom", uiState.splitMethod == GroupSplitMethod.CUSTOM) {
                        viewModel.onSplitMethodChange(GroupSplitMethod.CUSTOM)
                    }
                }

                if (uiState.splitMethod == GroupSplitMethod.CUSTOM) {
                    val difference = uiState.customSplitDifferenceCents
                    if (difference != null) {
                        val label = when {
                            difference > 0L -> "Unallocated: ${formatAmountInCents(difference)}"
                            difference < 0L -> "Over-allocated: ${formatAmountInCents(-difference)}"
                            else -> "Fully allocated"
                        }
                        Text(
                            label,
                            color = if (difference == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                uiState.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(
                    onClick = viewModel::save,
                    enabled = !uiState.isSaving && uiState.isCustomSplitBalanced,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.isEditMode) "Update" else stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun PayerOption(member: GroupMemberEntity, selected: Boolean, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(member.name)
    }
}

@Composable
private fun ParticipantRow(
    member: GroupMemberEntity,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    equalShareCents: Long?,
    splitMethod: GroupSplitMethod,
    customShareText: String,
    onCustomShareTextChange: (String) -> Unit,
    customShareError: String?
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(member.name, modifier = Modifier.width(120.dp))
            if (checked) {
                when (splitMethod) {
                    GroupSplitMethod.EQUAL -> equalShareCents?.let { Text(formatAmountInCents(it)) }
                    GroupSplitMethod.CUSTOM -> OutlinedTextField(
                        value = customShareText,
                        onValueChange = onCustomShareTextChange,
                        label = { Text(stringResource(R.string.action_share)) },
                        singleLine = true,
                        isError = customShareError != null,
                        supportingText = { customShareError?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitMethodOption(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

private fun epochDayToMillis(epochDay: Long): Long =
    LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun millisToEpochDay(millis: Long): Long =
    java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

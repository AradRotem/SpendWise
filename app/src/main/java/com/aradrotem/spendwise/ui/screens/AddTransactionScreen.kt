package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.ui.components.CategoryDropdownField
import com.aradrotem.spendwise.ui.components.DateField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    transactionId: Long?,
    onSaveSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddTransactionViewModel = viewModel(
        factory = AddTransactionViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.transactionRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.categoryRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.recurringOccurrenceManager,
            transactionId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val isEditMode = transactionId != null

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSaveSuccess()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Transaction" else "Add Transaction") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.loadError != null) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.loadError.orEmpty())
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isGeneratedOccurrence) {
                // This screen is reused for single-occurrence edits (see TransactionsScreen's
                // "Edit this transaction" action). Type is fixed - changing Income/Expense on one
                // occurrence of a recurring plan would be inconsistent with the rest of the series.
                Text(
                    "Editing this transaction only (${uiState.scheduledYearMonth.orEmpty()}). " +
                        "The recurring plan and other months are not affected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Type: ${uiState.type.name}", style = MaterialTheme.typography.labelLarge)

                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text("Title") },
                    singleLine = true,
                    isError = uiState.titleError != null,
                    supportingText = { uiState.titleError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { type ->
                        val selected = type == uiState.type
                        if (selected) {
                            Button(onClick = { viewModel.onTypeChange(type) }) {
                                Text(type.name)
                            }
                        } else {
                            OutlinedButton(onClick = { viewModel.onTypeChange(type) }) {
                                Text(type.name)
                            }
                        }
                    }
                }
            }

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

            CategoryDropdownField(
                selected = uiState.category,
                error = uiState.categoryError,
                options = availableCategories.map { it.name },
                onCategorySelected = viewModel::onCategoryChange
            )

            DateField(
                dateMillis = uiState.dateMillis,
                onDateSelected = viewModel::onDateChange
            )
            if (uiState.isGeneratedOccurrence) {
                Text(
                    "Must stay within ${uiState.scheduledYearMonth.orEmpty()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            uiState.saveError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::save,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (isEditMode) "Update" else "Save")
                }
            }
        }
    }
}

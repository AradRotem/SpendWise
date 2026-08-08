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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.R
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.ui.components.CategoryDropdownField
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.util.formatCategoryDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    modifier: Modifier = Modifier,
    viewModel: BudgetsViewModel = viewModel(
        factory = BudgetsViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.budgetRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.categoryRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).repositories.transactionRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Budgets") }) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = viewModel::onAddBudgetClick) {
                    Text("+ Add budget")
                }
            }

            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.budgets.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyBudgetsState(onAddClick = viewModel::onAddBudgetClick)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.budgets, key = { it.budget.id }) { progress ->
                        BudgetCard(
                            progress = progress,
                            onEdit = { viewModel.onEditBudgetClick(progress.budget) },
                            onDelete = { viewModel.onDeleteClick(progress.budget) }
                        )
                    }
                }
            }
        }
    }

    uiState.dialog?.let { dialog ->
        BudgetDialog(
            dialog = dialog,
            allCategories = uiState.expenseCategories.map { it.name },
            disabledCategories = uiState.budgetedCategoryNames,
            onCategorySelected = viewModel::onDialogCategoryChange,
            onAmountChange = viewModel::onDialogAmountChange,
            onConfirm = viewModel::onDialogSave,
            onDismiss = viewModel::onDialogDismiss
        )
    }

    uiState.budgetPendingDelete?.let { budget ->
        DeleteBudgetDialog(
            categoryName = budget.categoryName,
            onConfirm = viewModel::onDeleteConfirm,
            onDismiss = viewModel::onDeleteDismiss
        )
    }
}

@Composable
private fun EmptyBudgetsState(onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("No budgets yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Create a monthly spending limit for an Expense category to track how much you have left to spend.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onAddClick) {
            Text("+ Add budget")
        }
    }
}

@Composable
private fun BudgetCard(
    progress: BudgetProgress,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatCategoryDisplayName(progress.categoryName), style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                }
            }

            LinearProgressIndicator(
                progress = { progress.clampedProgressFraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (progress.isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )

            Text("Spent ${formatAmountInCents(progress.spentCents)} of ${formatAmountInCents(progress.limitCents)}")

            if (progress.isExceeded) {
                Text(
                    text = "Over budget by ${formatAmountInCents(-progress.remainingCents)}",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text("Remaining: ${formatAmountInCents(progress.remainingCents)}")
            }
        }
    }
}

@Composable
private fun BudgetDialog(
    dialog: BudgetDialogUiState,
    allCategories: List<String>,
    disabledCategories: Set<String>,
    onCategorySelected: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dialog.isEdit) "Edit budget" else "Add budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dialog.isEdit) {
                    Text("Category: ${dialog.categoryName?.let { formatCategoryDisplayName(it) }}")
                } else {
                    CategoryDropdownField(
                        selected = dialog.categoryName,
                        error = dialog.categoryError,
                        options = allCategories,
                        onCategorySelected = onCategorySelected,
                        disabledOptions = disabledCategories
                    )
                }
                OutlinedTextField(
                    value = dialog.amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Monthly limit") },
                    singleLine = true,
                    isError = dialog.amountError != null,
                    supportingText = { dialog.amountError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                dialog.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !dialog.isSaving) {
                Text(if (dialog.isEdit) "Update" else "Add")
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
private fun DeleteBudgetDialog(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete budget?") },
        text = {
            Text(
                "Delete the monthly budget for \"${formatCategoryDisplayName(categoryName)}\"? " +
                    "This will not affect the category or its transactions."
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

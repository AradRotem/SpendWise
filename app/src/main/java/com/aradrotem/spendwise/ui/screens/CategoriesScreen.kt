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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.R
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.util.formatCategoryDisplayName
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel(
        factory = CategoriesViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).categoryRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).transactionRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).budgetRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var addCategoryName by remember { mutableStateOf("") }
    var addCategoryError by remember { mutableStateOf<String?>(null) }
    var isAddingCategory by remember { mutableStateOf(false) }

    var categoryPendingDelete by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteImpactCount by remember { mutableStateOf(0) }
    var deleteHasBudget by remember { mutableStateOf(false) }

    fun requestDelete(category: CategoryEntity) {
        coroutineScope.launch {
            deleteImpactCount = viewModel.countTransactionsUsing(category)
            deleteHasBudget = viewModel.hasBudget(category)
            categoryPendingDelete = category
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back")
                    }
                }
            )
        },
        floatingActionButton = {
            // One persistent, obvious add action (spec: don't rely on a button at the bottom of
            // a long category list) - stays reachable regardless of scroll position or how many
            // categories exist.
            FloatingActionButton(
                onClick = {
                    addDialogType = TransactionType.EXPENSE
                    addCategoryName = ""
                    addCategoryError = null
                    showAddDialog = true
                },
                modifier = Modifier.semantics { contentDescription = "Add category" }
            ) {
                Text("+")
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                CategoryGroup(
                    title = "Income",
                    categories = uiState.incomeCategories,
                    onDeleteClick = ::requestDelete
                )
                CategoryGroup(
                    title = "Expense",
                    categories = uiState.expenseCategories,
                    onDeleteClick = ::requestDelete
                )
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            type = addDialogType,
            onTypeChange = {
                addDialogType = it
                addCategoryError = null
            },
            name = addCategoryName,
            onNameChange = {
                addCategoryName = it
                addCategoryError = null
            },
            errorMessage = addCategoryError,
            isSaving = isAddingCategory,
            onConfirm = {
                if (addCategoryName.isBlank()) {
                    addCategoryError = "Category name is required"
                } else if (!isAddingCategory) {
                    isAddingCategory = true
                    coroutineScope.launch {
                        val error = viewModel.addCategory(addCategoryName, addDialogType)
                        isAddingCategory = false
                        if (error == null) {
                            showAddDialog = false
                        } else {
                            addCategoryError = error
                        }
                    }
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    categoryPendingDelete?.let { category ->
        DeleteCategoryDialog(
            categoryName = formatCategoryDisplayName(category.name),
            affectedCount = deleteImpactCount,
            hasBudget = deleteHasBudget,
            onConfirm = {
                viewModel.deleteCategory(category)
                categoryPendingDelete = null
            },
            onDismiss = { categoryPendingDelete = null }
        )
    }
}

@Composable
private fun CategoryGroup(
    title: String,
    categories: List<CategoryEntity>,
    onDeleteClick: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (categories.isEmpty()) {
            Text(
                text = "No $title categories yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        categories.forEach { category ->
            CategoryRow(category = category, onDeleteClick = { onDeleteClick(category) })
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatCategoryDisplayName(category.name),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (category.isBuiltIn) {
            Text(
                text = "Built-in",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TextButton(onClick = onDeleteClick) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(
    type: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    errorMessage: String?,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryTypeOption(
                        label = "Expense",
                        selected = type == TransactionType.EXPENSE,
                        onClick = { onTypeChange(TransactionType.EXPENSE) }
                    )
                    CategoryTypeOption(
                        label = "Income",
                        selected = type == TransactionType.INCOME,
                        onClick = { onTypeChange(TransactionType.INCOME) }
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Category name") },
                    isError = errorMessage != null,
                    supportingText = { errorMessage?.let { Text(it) } },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun CategoryTypeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun DeleteCategoryDialog(
    categoryName: String,
    affectedCount: Int,
    hasBudget: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cannotBeUndoneText = stringResource(R.string.dialog_action_cannot_be_undone)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete category?") },
        text = { Text(buildDeleteCategoryMessage(categoryName, affectedCount, hasBudget, cannotBeUndoneText)) },
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

private fun buildDeleteCategoryMessage(categoryName: String, affectedCount: Int, hasBudget: Boolean, cannotBeUndoneText: String): String {
    val sentences = mutableListOf<String>()
    if (affectedCount > 0) {
        val noun = if (affectedCount == 1) "transaction" else "transactions"
        val pronoun = if (affectedCount == 1) "that transaction" else "those transactions"
        sentences += "This category is used by $affectedCount $noun. Deleting it will move $pronoun to OTHER."
    }
    if (hasBudget) {
        sentences += "Its monthly budget will also be deleted."
    }
    if (sentences.isEmpty()) {
        sentences += "Delete \"$categoryName\"?"
    }
    sentences += cannotBeUndoneText
    return sentences.joinToString(" ")
}

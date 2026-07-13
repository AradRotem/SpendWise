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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.CategoryEntity
import com.aradrotem.spendwise.data.local.TransactionType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel(
        factory = CategoriesViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).categoryRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).transactionRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var addDialogType by remember { mutableStateOf<TransactionType?>(null) }
    var addCategoryName by remember { mutableStateOf("") }
    var addCategoryError by remember { mutableStateOf<String?>(null) }

    var categoryPendingDelete by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteImpactCount by remember { mutableStateOf(0) }

    fun requestDelete(category: CategoryEntity) {
        coroutineScope.launch {
            deleteImpactCount = viewModel.countTransactionsUsing(category)
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
                    onAddClick = {
                        addDialogType = TransactionType.INCOME
                        addCategoryName = ""
                        addCategoryError = null
                    },
                    onDeleteClick = ::requestDelete
                )
                CategoryGroup(
                    title = "Expense",
                    categories = uiState.expenseCategories,
                    onAddClick = {
                        addDialogType = TransactionType.EXPENSE
                        addCategoryName = ""
                        addCategoryError = null
                    },
                    onDeleteClick = ::requestDelete
                )
            }
        }
    }

    addDialogType?.let { type ->
        AddCategoryDialog(
            type = type,
            name = addCategoryName,
            onNameChange = {
                addCategoryName = it
                addCategoryError = null
            },
            errorMessage = addCategoryError,
            onConfirm = {
                if (addCategoryName.isBlank()) {
                    addCategoryError = "Category name is required"
                } else {
                    coroutineScope.launch {
                        val error = viewModel.addCategory(addCategoryName, type)
                        if (error == null) {
                            addDialogType = null
                        } else {
                            addCategoryError = error
                        }
                    }
                }
            },
            onDismiss = { addDialogType = null }
        )
    }

    categoryPendingDelete?.let { category ->
        DeleteCategoryDialog(
            categoryName = category.name,
            affectedCount = deleteImpactCount,
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
    onAddClick: () -> Unit,
    onDeleteClick: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        categories.forEach { category ->
            CategoryRow(category = category, onDeleteClick = { onDeleteClick(category) })
        }
        TextButton(onClick = onAddClick) {
            Text("+ Add category")
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
        Text(text = category.name)
        if (category.isBuiltIn) {
            Text(
                text = "Built-in",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TextButton(onClick = onDeleteClick) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(
    type: TransactionType,
    name: String,
    onNameChange: (String) -> Unit,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val typeLabel = if (type == TransactionType.INCOME) "Income" else "Expense"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $typeLabel category") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Category name") },
                isError = errorMessage != null,
                supportingText = { errorMessage?.let { Text(it) } },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteCategoryDialog(
    categoryName: String,
    affectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete category?") },
        text = {
            Text(
                if (affectedCount > 0) {
                    val noun = if (affectedCount == 1) "transaction" else "transactions"
                    val pronoun = if (affectedCount == 1) "that transaction" else "those transactions"
                    "This category is used by $affectedCount $noun. Deleting it will move $pronoun to OTHER. Continue?"
                } else {
                    "Delete \"$categoryName\"? This action cannot be undone."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

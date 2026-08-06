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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.RecurringPlanType
import com.aradrotem.spendwise.ui.components.CategoryDropdownField
import com.aradrotem.spendwise.ui.components.DateField
import com.aradrotem.spendwise.ui.components.RecurringPlanTypeSelector
import com.aradrotem.spendwise.ui.components.recurringPlanTypeLabel
import com.aradrotem.spendwise.ui.format.formatAmountInCents
import com.aradrotem.spendwise.ui.format.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurringPlanScreen(
    onSaveSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    planId: Long? = null,
    occurrenceTransactionId: Long? = null,
    viewModel: AddRecurringPlanViewModel = viewModel(
        factory = AddRecurringPlanViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).recurringPaymentRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).categoryRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).transactionRepository,
            (LocalContext.current.applicationContext as SpendWiseApplication).recurringPaymentGenerator,
            (LocalContext.current.applicationContext as SpendWiseApplication).recurringOccurrenceManager,
            planId,
            occurrenceTransactionId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val isEditMode = uiState.isEditMode

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSaveSuccess()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Recurring Transaction" else "Add Recurring Transaction") },
                navigationIcon = {
                    // Text button with a visible arrow, matching the existing Add Category
                    // screen's back navigation exactly; the text itself is the accessible label.
                    TextButton(onClick = onBack) {
                        Text("← Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.loadError != null) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
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
            if (uiState.occurrenceTransactionId != null) {
                Text(
                    text = "Editing plan — these changes also apply to " +
                        "${uiState.occurrenceScheduledYearMonth.orEmpty()} onward. Earlier transactions are not affected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isEditMode) {
                // Plan type is fixed once created: changing it would leave already-generated
                // history inconsistent with the new type, so it's shown but not editable.
                Text("Type: ${recurringPlanTypeLabel(uiState.type)}", style = MaterialTheme.typography.labelLarge)
            } else {
                RecurringPlanTypeSelector(
                    selectedType = uiState.type,
                    onTypeSelected = viewModel::onTypeChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                isError = uiState.titleError != null,
                supportingText = { uiState.titleError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            CategoryDropdownField(
                selected = uiState.category,
                error = uiState.categoryError,
                options = availableCategories.map { it.name },
                onCategorySelected = viewModel::onCategoryChange
            )

            when (uiState.type) {
                RecurringPlanType.MONTHLY_RECURRING, RecurringPlanType.MONTHLY_SALARY -> MonthlyPlanFields(uiState, viewModel)
                RecurringPlanType.INSTALLMENT -> InstallmentFields(uiState, viewModel)
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

@Composable
private fun MonthlyPlanFields(uiState: AddRecurringPlanUiState, viewModel: AddRecurringPlanViewModel) {
    OutlinedTextField(
        value = uiState.amountText,
        onValueChange = viewModel::onAmountChange,
        label = { Text("Monthly amount") },
        singleLine = true,
        isError = uiState.amountError != null,
        supportingText = { uiState.amountError?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )

    if (uiState.isEditMode) {
        // Start date anchors already-generated history, so it isn't editable after creation.
        Text("Start date: ${formatDate(uiState.startDateMillis)}", style = MaterialTheme.typography.bodyMedium)
    } else {
        DateField(label = "Start date", dateMillis = uiState.startDateMillis, onDateSelected = viewModel::onStartDateChange)
    }

    OutlinedTextField(
        value = uiState.preferredDayOfMonthText,
        onValueChange = viewModel::onPreferredDayChange,
        label = { Text("Preferred day of month (1-31)") },
        singleLine = true,
        isError = uiState.preferredDayError != null,
        supportingText = { uiState.preferredDayError?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    if (uiState.showPreferredDayWarning) {
        Text(
            text = AddRecurringPlanUiState.RISKY_DAY_WARNING,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = uiState.hasEndDate, onCheckedChange = viewModel::onHasEndDateChange)
        Text("Set an end date")
    }
    if (uiState.hasEndDate) {
        DateField(
            label = "End date",
            dateMillis = uiState.endDateMillis ?: uiState.startDateMillis,
            onDateSelected = viewModel::onEndDateChange
        )
        uiState.endDateError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InstallmentFields(uiState: AddRecurringPlanUiState, viewModel: AddRecurringPlanViewModel) {
    val fieldsLocked = uiState.isInstallmentFieldsLocked

    OutlinedTextField(
        value = uiState.totalAmountText,
        onValueChange = viewModel::onTotalAmountChange,
        label = { Text("Total amount") },
        singleLine = true,
        enabled = !fieldsLocked,
        isError = uiState.totalAmountError != null,
        supportingText = { uiState.totalAmountError?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = uiState.installmentCountText,
        onValueChange = viewModel::onInstallmentCountChange,
        label = { Text("Number of installments") },
        singleLine = true,
        enabled = !fieldsLocked,
        isError = uiState.installmentCountError != null,
        supportingText = { uiState.installmentCountError?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )

    if (fieldsLocked) {
        Text(
            text = "Total amount and installment count can't be changed after payments have started.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (uiState.isEditMode) {
        // Like the monthly plans' start date, this anchors already-generated installment dates.
        Text("First payment date: ${formatDate(uiState.firstPaymentDateMillis)}", style = MaterialTheme.typography.bodyMedium)
    } else {
        DateField(
            label = "First payment date",
            dateMillis = uiState.firstPaymentDateMillis,
            onDateSelected = viewModel::onFirstPaymentDateChange
        )
    }
    if (uiState.showFirstPaymentDayWarning) {
        Text(
            text = AddRecurringPlanUiState.RISKY_DAY_WARNING,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val preview = uiState.installmentPreviewCents
    if (preview != null) {
        Text(
            "Estimated monthly payment: ${formatAmountInCents(preview.first())}",
            style = MaterialTheme.typography.bodyMedium
        )
        if (preview.toSet().size > 1) {
            Text(
                text = "The final payment may differ slightly (${formatAmountInCents(preview.last())}) " +
                    "to account for rounding.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

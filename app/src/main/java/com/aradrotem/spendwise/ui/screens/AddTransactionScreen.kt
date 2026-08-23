package com.aradrotem.spendwise.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.data.receipt.ReceiptImageProcessor
import com.aradrotem.spendwise.ui.components.CategoryDropdownField
import com.aradrotem.spendwise.ui.components.DateField
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    transactionId: Long?,
    onSaveSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onViewReceipt: (Long) -> Unit = {},
    app: SpendWiseApplication = LocalContext.current.applicationContext as SpendWiseApplication,
    viewModel: AddTransactionViewModel = viewModel(
        factory = AddTransactionViewModel.factory(
            app.repositories.transactionRepository,
            app.repositories.categoryRepository,
            app.repositories.recurringOccurrenceManager,
            transactionId,
            app.repositories.receiptRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val isEditMode = transactionId != null

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val receiptImageProcessor = remember { ReceiptImageProcessor(context.applicationContext) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showRemoveReceiptConfirm by remember { mutableStateOf(false) }

    fun processPickedImage(uri: Uri) {
        coroutineScope.launch {
            receiptImageProcessor.process(uri)
                .onSuccess { processed -> viewModel.onReceiptImageProcessed(processed) }
                .onFailure { error -> viewModel.onReceiptProcessingFailed(error.message ?: "Could not read this image. Try a different one.") }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) processPickedImage(uri)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) processPickedImage(uri)
    }

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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
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
                label = { Text("Amount (₪)") },
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

            HorizontalDivider()
            ReceiptSection(
                uiState = uiState,
                onTakePhoto = {
                    val uri = createReceiptCaptureUri(context)
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                },
                onChooseFromGallery = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onView = { transactionId?.let(onViewReceipt) },
                onRemove = { showRemoveReceiptConfirm = true },
                onDismissError = viewModel::dismissReceiptError
            )

            if (showRemoveReceiptConfirm) {
                RemoveReceiptDialog(
                    onConfirm = {
                        viewModel.onRemoveReceipt()
                        showRemoveReceiptConfirm = false
                    },
                    onDismiss = { showRemoveReceiptConfirm = false }
                )
            }

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
private fun ReceiptSection(
    uiState: AddTransactionUiState,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onView: () -> Unit,
    onRemove: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Receipt", style = MaterialTheme.typography.titleMedium)

        if (uiState.hasReceipt) {
            AsyncImage(
                model = uiState.receiptLocalPath?.let { File(it) } ?: uiState.receiptRemoteUrl,
                contentDescription = "Receipt thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onView, enabled = !uiState.isReceiptBusy) { Text("View") }
                OutlinedButton(onClick = onTakePhoto, enabled = !uiState.isReceiptBusy) { Text("Replace") }
                OutlinedButton(onClick = onRemove, enabled = !uiState.isReceiptBusy) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTakePhoto, enabled = !uiState.isReceiptBusy) { Text("Take photo") }
                OutlinedButton(onClick = onChooseFromGallery, enabled = !uiState.isReceiptBusy) { Text("Choose image") }
            }
        }

        if (uiState.isReceiptBusy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Uploading receipt…", style = MaterialTheme.typography.bodySmall)
            }
        }

        uiState.receiptError?.let { error ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissError) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun RemoveReceiptDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove receipt?") },
        text = { Text("The attached receipt image will be removed from this transaction.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// A fresh app-private file exposed only via FileProvider (see res/xml/file_paths.xml), handed to
// the system Camera app as the destination for a full-resolution capture - never a raw file://
// Uri, and never any broader storage access.
private fun createReceiptCaptureUri(context: android.content.Context): Uri {
    val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val captureFile = File(receiptsDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)
}

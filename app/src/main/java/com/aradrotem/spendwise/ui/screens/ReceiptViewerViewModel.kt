package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.repository.ReceiptRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReceiptViewerUiState(
    val isLoading: Boolean = true,
    // Either a java.io.File (local cache, preferred - works offline) or a String download URL
    // (resolved from Storage when there's no local cache, e.g. viewing a receipt pulled from
    // another device) - AsyncImage accepts either as a model.
    val displayModel: Any? = null,
    val error: String? = null
)

class ReceiptViewerViewModel(
    private val transactionRepository: TransactionRepository,
    private val receiptRepository: ReceiptRepository?,
    private val transactionId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiptViewerUiState())
    val uiState: StateFlow<ReceiptViewerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val transaction = transactionRepository.getById(transactionId)
            val localPath = transaction?.receiptLocalUri
            val storagePath = transaction?.receiptStoragePath
            when {
                localPath != null -> _uiState.update { it.copy(isLoading = false, displayModel = File(localPath)) }
                storagePath != null && receiptRepository != null -> {
                    receiptRepository.getDownloadUrl(storagePath)
                        .onSuccess { url -> _uiState.update { it.copy(isLoading = false, displayModel = url) } }
                        .onFailure { error ->
                            _uiState.update { it.copy(isLoading = false, error = error.message ?: "Could not load the receipt.") }
                        }
                }
                else -> _uiState.update { it.copy(isLoading = false, error = "No receipt found for this transaction.") }
            }
        }
    }

    companion object {
        fun factory(transactionRepository: TransactionRepository, receiptRepository: ReceiptRepository?, transactionId: Long) =
            viewModelFactory {
                initializer { ReceiptViewerViewModel(transactionRepository, receiptRepository, transactionId) }
            }
    }
}

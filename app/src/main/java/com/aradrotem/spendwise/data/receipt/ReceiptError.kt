package com.aradrotem.spendwise.data.receipt

// Friendly, UI-displayable receipt-storage failure reasons - mirrors data.auth.AuthError. Raw
// Firebase Storage exceptions never cross the ReceiptStorageRepository boundary.
sealed class ReceiptError(override val message: String) : Exception(message) {
    data object NetworkError : ReceiptError("Network error. Check your connection and try again.")
    data object PermissionDenied : ReceiptError("You don't have permission to access this receipt.")
    data object NotFound : ReceiptError("Receipt not found.")
    data object StorageUnavailable : ReceiptError("Cloud storage isn't set up for this build yet.")
    data object UnreadableImage : ReceiptError("This image couldn't be read. Try a different one.")
    data class Unknown(val reason: String) : ReceiptError(reason)
}

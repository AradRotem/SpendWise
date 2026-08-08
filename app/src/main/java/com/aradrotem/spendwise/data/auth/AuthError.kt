package com.aradrotem.spendwise.data.auth

// Friendly, UI-displayable auth failure reasons. Screens/ViewModels only ever see this type -
// raw Firebase exceptions never cross the AuthRepository boundary.
sealed class AuthError(override val message: String) : Exception(message) {
    data object InvalidEmail : AuthError("Enter a valid email address.")
    data object WeakPassword : AuthError("Password must be at least 6 characters.")
    data object EmailAlreadyInUse : AuthError("An account with this email already exists.")
    data object WrongPassword : AuthError("Incorrect email or password.")
    data object UserNotFound : AuthError("No account found with this email.")
    data object NetworkError : AuthError("Network error. Check your connection and try again.")
    data object FirebaseUnavailable : AuthError("Cloud sign-in isn't set up for this build yet.")
    data class Unknown(val reason: String) : AuthError(reason)
}

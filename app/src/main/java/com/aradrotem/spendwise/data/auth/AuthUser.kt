package com.aradrotem.spendwise.data.auth

// Decoupled from FirebaseUser so nothing outside this package needs to import Firebase types.
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?
)

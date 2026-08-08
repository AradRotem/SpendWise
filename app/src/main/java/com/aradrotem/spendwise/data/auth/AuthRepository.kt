package com.aradrotem.spendwise.data.auth

import kotlinx.coroutines.flow.Flow

// The sole abstraction over authentication state/actions. No Composable, screen, ViewModel, or
// navigation class may depend on FirebaseAuth directly - everything goes through this interface,
// so it can be faked in tests and so a build without Firebase configured can supply a safe stub
// (see FirebaseUnavailableAuthRepository) instead of crashing.
interface AuthRepository {

    // Emits the current user (or null when signed out) immediately on collection and on every
    // change thereafter.
    val authState: Flow<AuthUser?>

    // Synchronous snapshot of the current uid, used for one-shot startup routing decisions
    // (e.g. Splash) that must not wait on a Flow collection to avoid a UI flash.
    val currentUid: String?

    suspend fun signUp(displayName: String, email: String, password: String): Result<Unit>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun logout()
}

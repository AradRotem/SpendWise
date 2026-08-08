package com.aradrotem.spendwise.data.auth

import kotlinx.coroutines.flow.flowOf

// Used in place of FirebaseAuthRepository when this build has no google-services.json (see
// FirebaseAvailability). Always reports signed-out and fails every action with a clear
// "Firebase setup required" error - never pretends sign-in succeeded.
class FirebaseUnavailableAuthRepository : AuthRepository {

    override val authState = flowOf<AuthUser?>(null)
    override val currentUid: String? = null

    override suspend fun signUp(displayName: String, email: String, password: String): Result<Unit> =
        Result.failure(AuthError.FirebaseUnavailable)

    override suspend fun login(email: String, password: String): Result<Unit> =
        Result.failure(AuthError.FirebaseUnavailable)

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        Result.failure(AuthError.FirebaseUnavailable)

    override suspend fun logout() = Unit
}

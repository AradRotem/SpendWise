package com.aradrotem.spendwise.auth

import com.aradrotem.spendwise.data.auth.AuthError
import com.aradrotem.spendwise.data.auth.AuthRepository
import com.aradrotem.spendwise.data.auth.AuthUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

// In-memory AuthRepository test double. Every action is scriptable so ViewModel/coordinator
// tests never need a live FirebaseAuth instance.
class FakeAuthRepository(initialUser: AuthUser? = null) : AuthRepository {

    private val userState = MutableStateFlow(initialUser)
    override val authState = userState

    override val currentUid: String?
        get() = userState.value?.uid

    var nextSignUpResult: Result<Unit> = Result.success(Unit)
    var nextLoginResult: Result<Unit> = Result.success(Unit)
    var nextResetResult: Result<Unit> = Result.success(Unit)
    var signUpCallCount = 0
    var loginCallCount = 0
    var logoutCallCount = 0
    var sendPasswordResetCallCount = 0

    // When set, sendPasswordReset suspends until this is completed - lets a test observe the
    // ViewModel's isLoading=true state mid-flight before resolving the call.
    var resetCompletionGate: CompletableDeferred<Unit>? = null

    // Test-only helper to simulate what a successful sign-up/login would set authState to.
    var userToSetOnNextSuccess: AuthUser? = null

    override suspend fun signUp(displayName: String, email: String, password: String): Result<Unit> {
        signUpCallCount++
        if (nextSignUpResult.isSuccess) {
            userState.value = userToSetOnNextSuccess ?: AuthUser(uid = "fake-uid", email = email, displayName = displayName)
        }
        return nextSignUpResult
    }

    override suspend fun login(email: String, password: String): Result<Unit> {
        loginCallCount++
        if (nextLoginResult.isSuccess) {
            userState.value = userToSetOnNextSuccess ?: AuthUser(uid = "fake-uid", email = email, displayName = null)
        }
        return nextLoginResult
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        sendPasswordResetCallCount++
        resetCompletionGate?.await()
        return nextResetResult
    }

    override suspend fun logout() {
        logoutCallCount++
        userState.value = null
    }

    fun emitUser(user: AuthUser?) {
        userState.value = user
    }

    companion object {
        fun networkError() = AuthError.NetworkError
    }
}

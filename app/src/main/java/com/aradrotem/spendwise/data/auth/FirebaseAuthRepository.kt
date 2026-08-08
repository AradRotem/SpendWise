package com.aradrotem.spendwise.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// The only class in the app that touches FirebaseAuth directly.
class FirebaseAuthRepository(private val firebaseAuth: FirebaseAuth) : AuthRepository {

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    override suspend fun signUp(displayName: String, email: String, password: String): Result<Unit> =
        runAuthCatching {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            result.user?.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
            )?.await()
        }

    override suspend fun login(email: String, password: String): Result<Unit> =
        runAuthCatching {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
        }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        runAuthCatching {
            firebaseAuth.sendPasswordResetEmail(email).await()
        }

    override suspend fun logout() {
        firebaseAuth.signOut()
    }

    private inline fun runAuthCatching(block: () -> Unit): Result<Unit> {
        return try {
            block()
            Result.success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(AuthError.WeakPassword)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(AuthError.EmailAlreadyInUse)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(AuthError.WrongPassword)
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(AuthError.UserNotFound)
        } catch (e: FirebaseNetworkException) {
            Result.failure(AuthError.NetworkError)
        } catch (e: Exception) {
            Result.failure(AuthError.Unknown(e.message ?: "Something went wrong. Please try again."))
        }
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthUser() =
        AuthUser(uid = uid, email = email, displayName = displayName)
}

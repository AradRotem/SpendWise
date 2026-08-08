package com.aradrotem.spendwise.data.auth

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreUserProfileRepository(private val firestore: FirebaseFirestore) : UserProfileRepository {

    private fun profileDoc(uid: String) = firestore.collection("users").document(uid)

    override suspend fun getProfile(uid: String): UserProfile? {
        val snapshot = profileDoc(uid).get().await()
        return snapshot.toProfile()
    }

    override suspend fun upsertProfile(profile: UserProfile) {
        profileDoc(profile.uid).set(profile.toMap()).await()
    }

    override fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = profileDoc(uid).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toProfile())
        }
        awaitClose { registration.remove() }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toProfile(): UserProfile? {
        if (!exists()) return null
        return UserProfile(
            uid = getString("uid") ?: id,
            displayName = getString("displayName") ?: "",
            email = getString("email") ?: "",
            preferredCurrency = getString("preferredCurrency") ?: "USD",
            monthlyBudget = getLong("monthlyBudget") ?: 0L,
            createdAt = getLong("createdAt") ?: 0L,
            updatedAt = getLong("updatedAt") ?: 0L
        )
    }

    private fun UserProfile.toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "displayName" to displayName,
        "email" to email,
        "preferredCurrency" to preferredCurrency,
        "monthlyBudget" to monthlyBudget,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}

package com.aradrotem.spendwise.data.auth

import kotlinx.coroutines.flow.flowOf

// Used in place of FirestoreUserProfileRepository when this build has no google-services.json.
class FirebaseUnavailableUserProfileRepository : UserProfileRepository {
    override suspend fun getProfile(uid: String): UserProfile? = null
    override suspend fun upsertProfile(profile: UserProfile) = Unit
    override fun observeProfile(uid: String) = flowOf<UserProfile?>(null)
}

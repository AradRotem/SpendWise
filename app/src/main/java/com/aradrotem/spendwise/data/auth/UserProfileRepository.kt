package com.aradrotem.spendwise.data.auth

import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    suspend fun getProfile(uid: String): UserProfile?
    suspend fun upsertProfile(profile: UserProfile)
    fun observeProfile(uid: String): Flow<UserProfile?>
}

package com.aradrotem.spendwise.auth

import com.aradrotem.spendwise.data.auth.UserProfile
import com.aradrotem.spendwise.data.auth.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUserProfileRepository : UserProfileRepository {

    private val profiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

    override suspend fun getProfile(uid: String): UserProfile? = profiles.value[uid]

    override suspend fun upsertProfile(profile: UserProfile) {
        profiles.value = profiles.value + (profile.uid to profile)
    }

    override fun observeProfile(uid: String) = profiles.map { it[uid] }
}

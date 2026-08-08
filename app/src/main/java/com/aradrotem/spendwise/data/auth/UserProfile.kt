package com.aradrotem.spendwise.data.auth

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val preferredCurrency: String = "USD",
    val monthlyBudget: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

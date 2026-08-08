package com.aradrotem.spendwise.data.sync

sealed class SyncStatus {
    data object Offline : SyncStatus()
    data object Syncing : SyncStatus()
    data object Synced : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

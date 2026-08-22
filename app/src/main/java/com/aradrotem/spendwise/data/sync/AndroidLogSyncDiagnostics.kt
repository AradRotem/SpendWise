package com.aradrotem.spendwise.data.sync

import android.util.Log

// Real SyncDiagnostics implementation - see that interface for why this is a separate class
// rather than SharedGroupSyncEngine calling android.util.Log directly. Debug-level only; safe to
// leave in place permanently or strip via ProGuard/R8 log-stripping rules if desired later.
class AndroidLogSyncDiagnostics : SyncDiagnostics {
    override fun log(stage: String, message: String) {
        Log.d(TAG, "[$stage] $message")
    }

    private companion object {
        const val TAG = "SharedGroupSync"
    }
}

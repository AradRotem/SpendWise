package com.aradrotem.spendwise.data.sync

// Optional diagnostic hook for the shared-group sync pipeline (Step 19). Kept as its own small
// interface - not a direct android.util.Log call inside SharedGroupSyncEngine - so that engine
// stays plain-JVM unit-testable (SharedGroupSyncEngineTest runs without Robolectric/a mocked
// android.jar; a direct Log call there would throw "not mocked" and break every test). The real
// implementation (AndroidLogSyncDiagnostics) is wired in only at the SpendWiseApplication level.
// Never logs passwords, tokens, API keys, or receipt contents; only counts and ids needed to tell
// which stage of the pipeline a real-device sync issue is happening at.
fun interface SyncDiagnostics {
    fun log(stage: String, message: String)
}

package com.aradrotem.spendwise.data.auth

// Bridges auth-state changes to "which account's data is active". Deliberately Android-free (no
// SpendWiseApplication/Context reference) so it's unit-testable with a FakeAuthRepository - the
// actual DB-rebuild/legacy-import/recreate side effects are injected as plain suspend lambdas.
//
// Dedup rule: only calls onUidChanged when the *effective* uid actually changes (null -> A,
// A -> null, A -> B). Redundant emissions of the same uid (A -> A, null -> null - e.g. Firebase's
// AuthStateListener can fire more than once for the same signed-in user) are silently ignored, so
// the app never recreates its Activity in a loop. The very first emission (cold start) is applied
// but flagged isInitial = true so the caller can skip the Activity recreate for it - the process
// hasn't rendered anything yet, so there's nothing to invalidate.
class AuthSessionCoordinator(
    private val authRepository: AuthRepository,
    private val onNewLogin: suspend (uid: String) -> Unit,
    private val onUidChanged: suspend (uid: String?, isInitial: Boolean) -> Unit
) {
    private var lastAppliedUid: String? = null
    private var initialized = false
    var uidChangeCount: Int = 0
        private set

    suspend fun run() {
        authRepository.authState.collect { user ->
            val newUid = user?.uid
            if (initialized && newUid == lastAppliedUid) return@collect

            val isInitial = !initialized
            if (newUid != null) {
                onNewLogin(newUid)
            }
            onUidChanged(newUid, isInitial)

            lastAppliedUid = newUid
            initialized = true
            uidChangeCount++
        }
    }
}

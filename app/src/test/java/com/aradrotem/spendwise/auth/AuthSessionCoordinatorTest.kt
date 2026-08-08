package com.aradrotem.spendwise.auth

import com.aradrotem.spendwise.data.auth.AuthSessionCoordinator
import com.aradrotem.spendwise.data.auth.AuthUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthSessionCoordinatorTest {

    @Test
    fun run_appliesInitialStateOnceWithoutTreatingItAsAChange() = runBlocking {
        val fakeAuth = FakeAuthRepository(initialUser = null)
        val uidChanges = mutableListOf<Pair<String?, Boolean>>()
        val coordinator = AuthSessionCoordinator(
            authRepository = fakeAuth,
            onNewLogin = {},
            onUidChanged = { uid, isInitial -> uidChanges.add(uid to isInitial) }
        )

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = scope.launch { coordinator.run() }
        yield()

        assertEquals(listOf(null to true), uidChanges)
        job.cancel()
        scope.cancel()
    }

    @Test
    fun run_recreatesOnlyOnRealUidChange_notOnRepeatedSameUidEmissions() = runBlocking {
        val fakeAuth = FakeAuthRepository(initialUser = null)
        val uidChanges = mutableListOf<Pair<String?, Boolean>>()
        var newLoginCalls = 0
        val coordinator = AuthSessionCoordinator(
            authRepository = fakeAuth,
            onNewLogin = { newLoginCalls++ },
            onUidChanged = { uid, isInitial -> uidChanges.add(uid to isInitial) }
        )

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = scope.launch { coordinator.run() }
        yield()

        val userA = AuthUser(uid = "A", email = "a@test.com", displayName = "A")
        fakeAuth.emitUser(userA)
        yield()
        // Redundant re-emission of the same user - must NOT trigger another change.
        fakeAuth.emitUser(userA)
        yield()

        fakeAuth.emitUser(null) // logout
        yield()
        fakeAuth.emitUser(null) // redundant logout emission
        yield()

        val userB = AuthUser(uid = "B", email = "b@test.com", displayName = "B")
        fakeAuth.emitUser(userB)
        yield()

        assertEquals(
            listOf(
                null to true,   // cold start, no user
                "A" to false,   // null -> A: real change
                null to false,  // A -> null: real change
                "B" to false    // null -> B: real change
            ),
            uidChanges
        )
        assertEquals(2, newLoginCalls) // one for A, one for B - never for the redundant re-emissions
        job.cancel()
        scope.cancel()
    }
}

package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.auth.FakeAuthRepository
import com.aradrotem.spendwise.data.auth.AuthRepository
import com.aradrotem.spendwise.data.auth.AuthUser
import com.aradrotem.spendwise.data.repository.FakeGroupCloudRepository
import com.aradrotem.spendwise.data.repository.GroupExpenseRepository
import com.aradrotem.spendwise.data.repository.IncomingInvitationsLoadError
import com.aradrotem.spendwise.data.sync.SharedGroupSyncEngine
import com.aradrotem.spendwise.domain.FakeExpenseGroupDao
import com.aradrotem.spendwise.domain.FakeGroupExpenseDao
import com.aradrotem.spendwise.domain.FakeGroupMemberDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Covers the invited-user side of the Account A -> Account B invitation flow (Bug fix: group
// invitations intermittently invisible to the invitee). Two things are exercised here that
// SharedGroupSyncEngineTest (repository-level only) does not cover:
//  1. IncomingInvitationsViewModel end-to-end, wired the same way GroupExpensesListScreen wires it
//     (uiState is what the screen actually renders).
//  2. The listener-lifecycle race: Firebase's AuthStateListener firing more than once for the SAME
//     signed-in user (a real, documented quirk - see AuthSessionCoordinator) must not tear down and
//     recreate the live Firestore invitations listener each time.
@OptIn(ExperimentalCoroutinesApi::class)
class IncomingInvitationsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val cloud = FakeGroupCloudRepository()

    private val userA = AuthUser(uid = "uid-a", email = "alice@example.com", displayName = "Alice")
    private val userB = AuthUser(uid = "uid-b", email = "Bob@Example.com", displayName = "Bob")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModelForB(authRepository: AuthRepository): IncomingInvitationsViewModel {
        val repository = GroupExpenseRepository(FakeExpenseGroupDao(), FakeGroupMemberDao(), FakeGroupExpenseDao())
        val engine = SharedGroupSyncEngine(repository, cloud, authRepository)
        return IncomingInvitationsViewModel(cloud, authRepository, engine)
    }

    private suspend fun TestScope.collectUiState(viewModel: IncomingInvitationsViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    @Test
    fun invitationSentByA_isVisibleToB_throughTheSameViewModelTheScreenUses() = runTest(testDispatcher) {
        val syncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        // B's account email is stored/typed with different case than the invite is addressed to -
        // both must still normalize to the same value (see FirestoreGroupCloudRepository).
        cloud.sendInvitation(syncId, "Trip", userA.uid, userA.email!!, "  bob@example.com  ")

        val viewModelB = newViewModelForB(FakeAuthRepository(userB))
        collectUiState(viewModelB)

        val invitations = viewModelB.uiState.value.invitations
        assertEquals(1, invitations.size)
        assertEquals("Trip", invitations.single().groupName)
    }

    // Full regression test for the reported flow: "A invites B -> B sees pending invitation ->
    // Accept -> group appears". Exercises the exact chain the real screen wires together
    // (IncomingInvitationsViewModel for the invitation list/accept action, GroupsListViewModel for
    // the resulting groups list), not just the underlying repository calls. GroupsListViewModel is
    // deliberately constructed/collected AFTER accept() (rather than kept subscribed throughout),
    // since the fake Room DAOs back observeAll() with a plain non-reactive flowOf snapshot (unlike
    // real Room) - matching the same "read fresh after the mutation" style SharedGroupDiscoveryTest
    // already uses for the identical reason.
    @Test
    fun fullInviteAcceptFlow_groupAppearsInGroupsListAfterAccept() = runTest(testDispatcher) {
        val syncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        cloud.sendInvitation(syncId, "Trip", userA.uid, userA.email!!, userB.email!!)

        val repositoryB = GroupExpenseRepository(FakeExpenseGroupDao(), FakeGroupMemberDao(), FakeGroupExpenseDao())
        val authRepositoryB = FakeAuthRepository(userB)
        val engineB = SharedGroupSyncEngine(repositoryB, cloud, authRepositoryB)
        val invitationsViewModelB = IncomingInvitationsViewModel(cloud, authRepositoryB, engineB)
        backgroundScope.launch { invitationsViewModelB.uiState.collect {} }

        // Before acceptance: B sees the pending invitation, but the group isn't in B's local Room
        // yet at all.
        val invitation = invitationsViewModelB.uiState.value.invitations.single()
        assertEquals("Trip", invitation.groupName)
        assertEquals(emptyList<Any>(), repositoryB.observeGroups().first())

        invitationsViewModelB.accept(invitation)

        // After acceptance: the invitation is gone, and the group is now visible through the same
        // GroupsListViewModel the Shared Groups screen renders.
        assertEquals(emptyList<Any>(), invitationsViewModelB.uiState.value.invitations)
        val groupsListViewModelB = GroupsListViewModel(repositoryB, cloud, authRepositoryB)
        backgroundScope.launch { groupsListViewModelB.uiState.collect {} }
        val groupItem = groupsListViewModelB.uiState.value.items.single()
        assertEquals("Trip", groupItem.group.name)
        assertEquals(syncId, groupItem.group.groupSyncId)
    }

    @Test
    fun acceptedInvitation_disappearsFromIncomingList() = runTest(testDispatcher) {
        val syncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        cloud.sendInvitation(syncId, "Trip", userA.uid, userA.email!!, userB.email!!)

        val viewModelB = newViewModelForB(FakeAuthRepository(userB))
        collectUiState(viewModelB)
        val invitation = viewModelB.uiState.value.invitations.single()

        viewModelB.accept(invitation)

        assertEquals(emptyList<Any>(), viewModelB.uiState.value.invitations)
    }

    // Regression test: a listener error must surface as loadError, not silently look like "no
    // pending invitations" - see FirestoreGroupCloudRepository's close(error) and
    // IncomingInvitationsUiState.loadError.
    @Test
    fun listenerError_surfacesAsLoadError_notAsAnEmptyList() = runTest(testDispatcher) {
        cloud.observeIncomingInvitationsError = RuntimeException("PERMISSION_DENIED")

        val viewModelB = newViewModelForB(FakeAuthRepository(userB))
        collectUiState(viewModelB)

        val state = viewModelB.uiState.value
        assertEquals(emptyList<Any>(), state.invitations)
        assertEquals("Could not load pending invitations. Please try again.", state.loadError)
    }

    // Regression test: diagnose, don't hide - a missing/rebuilding composite index
    // (FAILED_PRECONDITION) must produce a message distinct from a generic failure, and different
    // again from a rules rejection (PERMISSION_DENIED), so the actual cause is legible from the UI
    // itself. See FirestoreGroupCloudRepository.toIncomingInvitationsLoadError and
    // IncomingInvitationsViewModel.toLoadErrorMessage.
    @Test
    fun indexUnavailableError_surfacesADistinctMessageFromAGenericFailure() = runTest(testDispatcher) {
        cloud.observeIncomingInvitationsError = IncomingInvitationsLoadError.IndexUnavailable("the query requires an index")

        val viewModelB = newViewModelForB(FakeAuthRepository(userB))
        collectUiState(viewModelB)

        assertEquals(
            "Pending invitations are temporarily unavailable while a one-time setup finishes. Please try again shortly.",
            viewModelB.uiState.value.loadError
        )
    }

    @Test
    fun permissionDeniedError_surfacesADistinctMessageFromAGenericFailure() = runTest(testDispatcher) {
        cloud.observeIncomingInvitationsError = IncomingInvitationsLoadError.PermissionDenied("Missing or insufficient permissions")

        val viewModelB = newViewModelForB(FakeAuthRepository(userB))
        collectUiState(viewModelB)

        assertEquals(
            "Could not load pending invitations due to a permissions issue. Please sign out and sign back in, or contact support if this continues.",
            viewModelB.uiState.value.loadError
        )
    }

    // Regression test for the listener-lifecycle race behind the "sometimes not visible" bug:
    // authState re-firing with an unchanged user must not cancel and re-subscribe
    // observeIncomingInvitations. A plain `flow {}` (not a StateFlow) is used deliberately here,
    // because a StateFlow already conflates equal consecutive values on its own and could never
    // reproduce Firebase's real callbackFlow behavior of re-emitting the same user twice.
    @Test
    fun redundantAuthStateEmission_forSameUser_doesNotRestartTheInvitationsListener() = runTest(testDispatcher) {
        val syncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        cloud.sendInvitation(syncId, "Trip", userA.uid, userA.email!!, userB.email!!)

        val flakyAuthRepository = object : AuthRepository {
            override val authState: Flow<AuthUser?> = flow {
                emit(userB)
                emit(userB) // redundant re-fire of the SAME user, mirroring the real SDK's quirk
            }
            override val currentUid: String? = userB.uid
            override suspend fun signUp(displayName: String, email: String, password: String) = Result.success(Unit)
            override suspend fun login(email: String, password: String) = Result.success(Unit)
            override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
            override suspend fun logout() {}
        }

        val viewModelB = newViewModelForB(flakyAuthRepository)
        collectUiState(viewModelB)

        assertEquals(1, cloud.observeIncomingInvitationsCallCount)
        assertEquals(1, viewModelB.uiState.value.invitations.size)
    }
}

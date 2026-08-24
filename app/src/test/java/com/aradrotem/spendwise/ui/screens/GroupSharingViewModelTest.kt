package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.auth.FakeAuthRepository
import com.aradrotem.spendwise.data.auth.AuthUser
import com.aradrotem.spendwise.data.repository.FakeGroupCloudRepository
import com.aradrotem.spendwise.data.repository.GroupExpenseRepository
import com.aradrotem.spendwise.domain.FakeExpenseGroupDao
import com.aradrotem.spendwise.domain.FakeGroupExpenseDao
import com.aradrotem.spendwise.domain.FakeGroupMemberDao
import com.aradrotem.spendwise.domain.GroupInvitationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// Covers the cancel-invitation addition to GroupSharingViewModel: an owner can cancel a still-
// pending sent invitation, and the change is reflected via the same live sentInvitations flow
// used by the invite dialog (see GroupDetailsScreen's InviteByEmailDialog).
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSharingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var groupExpenseRepository: GroupExpenseRepository
    private lateinit var groupDao: FakeExpenseGroupDao
    private lateinit var cloudRepository: FakeGroupCloudRepository
    private lateinit var authRepository: FakeAuthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        groupDao = FakeExpenseGroupDao()
        groupExpenseRepository = GroupExpenseRepository(groupDao, FakeGroupMemberDao(), FakeGroupExpenseDao())
        cloudRepository = FakeGroupCloudRepository()
        authRepository = FakeAuthRepository(AuthUser(uid = "owner-uid", email = "owner@example.com", displayName = "Owner"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // uiState is a WhileSubscribed StateFlow, so it only starts computing once something collects
    // it - mirroring how the real screen's collectAsStateWithLifecycle would. backgroundScope
    // (from runTest) collects it for the lifetime of the test and is cancelled automatically
    // afterward.
    private suspend fun TestScope.newSharedGroupViewModel(): GroupSharingViewModel {
        val groupId = groupExpenseRepository.createGroup("Trip", emptyList()).getOrThrow()
        val syncId = cloudRepository.createSharedGroup("Trip", "owner-uid", "Owner", "owner@example.com").getOrThrow()
        groupExpenseRepository.markGroupShared(groupId, syncId, "owner-uid")
        val viewModel = GroupSharingViewModel(groupExpenseRepository, cloudRepository, groupId, authRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun cancelInvitation_marksInvitationCancelled() = runTest(testDispatcher) {
        val viewModel = newSharedGroupViewModel()
        viewModel.inviteByEmail("friend@example.com")
        val pending = viewModel.uiState.value.sentInvitations.single()
        assertEquals(GroupInvitationStatus.PENDING, pending.status)

        viewModel.cancelInvitation(pending)

        val updated = viewModel.uiState.value.sentInvitations.single()
        assertEquals(GroupInvitationStatus.CANCELLED, updated.status)
    }

    @Test
    fun cancelInvitation_afterCancel_reinviteIsAllowed() = runTest(testDispatcher) {
        val viewModel = newSharedGroupViewModel()
        viewModel.inviteByEmail("friend@example.com")
        val pending = viewModel.uiState.value.sentInvitations.single()

        viewModel.cancelInvitation(pending)
        viewModel.inviteByEmail("friend@example.com")

        // A cancelled invitation must not block re-inviting the same email (mirrors declined),
        // so a fresh PENDING invitation should now exist alongside the cancelled one.
        assertEquals("Invitation sent.", viewModel.uiState.value.inviteSuccessMessage)
        assertNull(viewModel.uiState.value.inviteError)
        val statuses = viewModel.uiState.value.sentInvitations.map { it.status }
        assertEquals(listOf(GroupInvitationStatus.CANCELLED, GroupInvitationStatus.PENDING), statuses)
    }

    @Test
    fun cancelInvitation_repositoryFailure_setsErrorMessage() = runTest(testDispatcher) {
        val viewModel = newSharedGroupViewModel()
        viewModel.inviteByEmail("friend@example.com")
        val pending = viewModel.uiState.value.sentInvitations.single()
        cloudRepository.cancelInvitationResult = Result.failure(RuntimeException("network error"))

        viewModel.cancelInvitation(pending)

        assertEquals("Could not cancel the invitation. Please try again.", viewModel.uiState.value.inviteError)
        assertEquals(GroupInvitationStatus.PENDING, viewModel.uiState.value.sentInvitations.single().status)
    }
}

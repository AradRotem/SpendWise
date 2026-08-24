package com.aradrotem.spendwise.data.sync

import com.aradrotem.spendwise.auth.FakeAuthRepository
import com.aradrotem.spendwise.data.auth.AuthUser
import com.aradrotem.spendwise.data.repository.FakeGroupCloudRepository
import com.aradrotem.spendwise.data.repository.GroupExpenseRepository
import com.aradrotem.spendwise.domain.FakeExpenseGroupDao
import com.aradrotem.spendwise.domain.FakeGroupExpenseDao
import com.aradrotem.spendwise.domain.FakeGroupMemberDao
import com.aradrotem.spendwise.ui.screens.GroupsListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Regression coverage for the real bug: "Delete shared group -> press Sync -> deleted group comes
// back". Root cause: GroupExpenseRepository.deleteGroup only ever touched local Room - neither the
// canonical groups/{groupId} Firestore doc nor this account's own
// users/{uid}/groupMemberships/{groupId} discovery-index doc were removed, so the very next
// SharedGroupSyncEngine.syncAll (which rebuilds "my groups" purely from that index - see
// SharedGroupDiscoveryTest) rediscovered and recreated the "deleted" group locally. The fix layers
// GroupCloudRepository.deleteSharedGroup on top of the local delete in
// GroupsListViewModel.onDeleteGroup.
@OptIn(ExperimentalCoroutinesApi::class)
class GroupDeletionSyncTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val userA = AuthUser(uid = "uid-a", email = "alice@example.com", displayName = "Alice")
    private val userB = AuthUser(uid = "uid-b", email = "bob@example.com", displayName = "Bob")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newRepository() = GroupExpenseRepository(
        FakeExpenseGroupDao(), FakeGroupMemberDao(), FakeGroupExpenseDao()
    )

    @Test
    fun ownerDeletesGroup_thenSync_groupStaysDeletedLocally() = runTest(testDispatcher) {
        val cloud = FakeGroupCloudRepository()
        val syncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()

        val repositoryA = newRepository()
        val authRepositoryA = FakeAuthRepository(userA)
        val localGroupId = repositoryA.getOrCreateLocalGroupForSync(syncId, "Trip")
        repositoryA.markGroupShared(localGroupId, syncId, userA.uid)
        val group = repositoryA.getGroup(localGroupId)!!

        val viewModelA = GroupsListViewModel(repositoryA, cloud, authRepositoryA)
        viewModelA.onDeleteGroup(group)

        // Local delete happened immediately.
        assertTrue(repositoryA.observeGroups().first().isEmpty())
        // And the cloud side was torn down too - not just the local mirror.
        assertTrue(cloud.groups[syncId] == null)
        assertTrue(cloud.getMyMemberships(userA.uid).getOrThrow().isEmpty())

        // The actual regression: a Sync run after the delete must not resurrect the group.
        SharedGroupSyncEngine(repositoryA, cloud, authRepositoryA).syncAll()

        assertTrue(repositoryA.observeGroups().first().isEmpty())
    }

    @Test
    fun ownerDeletesGroup_membersInvitationsAndExpensesAreAlsoRemovedFromCloud() = runTest(testDispatcher) {
        val cloud = FakeGroupCloudRepository()
        val syncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        cloud.sendInvitation(syncId, "Trip", userA.uid, userA.email!!, "carol@example.com")

        val repositoryA = newRepository()
        val authRepositoryA = FakeAuthRepository(userA)
        val localGroupId = repositoryA.getOrCreateLocalGroupForSync(syncId, "Trip")
        repositoryA.markGroupShared(localGroupId, syncId, userA.uid)
        val group = repositoryA.getGroup(localGroupId)!!

        GroupsListViewModel(repositoryA, cloud, authRepositoryA).onDeleteGroup(group)

        assertTrue(cloud.getMembersOnce(syncId).getOrThrow().isEmpty())
        assertTrue(cloud.getExpensesOnce(syncId).getOrThrow().isEmpty())
        assertTrue(cloud.observeSentInvitations(syncId).first().isEmpty())
    }

    @Test
    fun nonOwnerMemberDeletesGroupLocally_onlyTheirOwnMembershipIsRemoved() = runTest(testDispatcher) {
        val cloud = FakeGroupCloudRepository()
        val syncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        cloud.sendInvitation(syncId, "Trip", userA.uid, userA.email!!, userB.email!!)
        val invitation = cloud.observeIncomingInvitations(userB.email!!).first().single()
        cloud.acceptInvitation(invitation, userB.uid, userB.displayName!!, userB.email!!)

        val repositoryB = newRepository()
        val authRepositoryB = FakeAuthRepository(userB)
        SharedGroupSyncEngine(repositoryB, cloud, authRepositoryB).syncAll()
        val groupB = repositoryB.observeGroups().first().single()

        GroupsListViewModel(repositoryB, cloud, authRepositoryB).onDeleteGroup(groupB)

        // B is not the owner - the group itself, A's ownership, and A's membership must be
        // untouched; only B's own membership disappears.
        assertTrue(cloud.groups.containsKey(syncId))
        assertTrue(cloud.getMyMemberships(userA.uid).getOrThrow().any { it.groupId == syncId })
        assertTrue(cloud.getMyMemberships(userB.uid).getOrThrow().none { it.groupId == syncId })

        // A syncing again must still see their own group untouched.
        val repositoryA = newRepository()
        val authRepositoryA = FakeAuthRepository(userA)
        val localGroupIdA = repositoryA.getOrCreateLocalGroupForSync(syncId, "Trip")
        repositoryA.markGroupShared(localGroupIdA, syncId, userA.uid)
        SharedGroupSyncEngine(repositoryA, cloud, authRepositoryA).syncAll()
        assertEquals(1, repositoryA.observeGroups().first().size)
    }
}

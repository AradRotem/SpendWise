package com.aradrotem.spendwise.data.sync

import com.aradrotem.spendwise.auth.FakeAuthRepository
import com.aradrotem.spendwise.data.auth.AuthUser
import com.aradrotem.spendwise.data.local.ExpenseGroupDao
import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import com.aradrotem.spendwise.data.local.GroupRole
import com.aradrotem.spendwise.data.repository.FakeGroupCloudRepository
import com.aradrotem.spendwise.data.repository.GroupCloudMember
import com.aradrotem.spendwise.data.repository.GroupExpenseRepository
import com.aradrotem.spendwise.domain.FakeExpenseGroupDao
import com.aradrotem.spendwise.domain.FakeGroupExpenseDao
import com.aradrotem.spendwise.domain.FakeGroupExpensePendingDeletionDao
import com.aradrotem.spendwise.domain.FakeGroupMemberDao
import com.aradrotem.spendwise.sync.FakeSyncMetadataDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Deterministically reproduces the real race behind duplicated shared groups: two overlapping
// Sync passes (e.g. app resume, a manual "Sync now", and a screen's own entry-point sync all
// firing close together - each one via SpendWiseApplication.sharedGroupSyncEngine, a computed
// property that hands out a brand new SharedGroupSyncEngine and therefore a brand new, independent
// Mutex on every access) can both see no existing local row for the same groupSyncId before either
// has inserted one. Real thread concurrency can't be reliably forced through coroutines against
// these single-threaded fakes, so this wraps getByGroupSyncId to inject a competing insert - into
// the SAME underlying dao - right after the first "no existing row" result, simulating the winning
// pass landing in the gap between this pass's own check and its insert.
private class RacyExpenseGroupDao(private val delegate: FakeExpenseGroupDao) : ExpenseGroupDao by delegate {
    var onFirstNullResult: (suspend () -> Unit)? = null

    override suspend fun getByGroupSyncId(groupSyncId: String): ExpenseGroupEntity? {
        val result = delegate.getByGroupSyncId(groupSyncId)
        if (result == null) {
            onFirstNullResult?.invoke()
            onFirstNullResult = null
        }
        return result
    }
}

// Regression coverage for the real-device bug: "invitation accepted, sync completes without
// error, but the shared group never appears in Account B's Groups screen". These tests exercise
// the discovery path (SharedGroupSyncEngine.syncAll -> getMyMemberships -> local group creation)
// directly and independently of the accept() flow, since the reported symptom is specifically
// about post-acceptance visibility, not about whether accept() itself succeeds.
class SharedGroupDiscoveryTest {

    private val userA = AuthUser(uid = "uid-a", email = "alice@example.com", displayName = "Alice")
    private val userB = AuthUser(uid = "uid-b", email = "bob@example.com", displayName = "Bob")

    private fun newRepository() = GroupExpenseRepository(
        FakeExpenseGroupDao(), FakeGroupMemberDao(), FakeGroupExpenseDao(),
        FakeSyncMetadataDao(), FakeGroupExpensePendingDeletionDao()
    )

    @Test
    fun cloudOnlyGroup_neverSeenLocally_materializesIntoEmptyRoomOnSync() = runTest {
        val cloud = FakeGroupCloudRepository()
        val groupSyncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        // B's membership exists in the cloud (as if accepted on another device, or synced by a
        // process this test isn't exercising) - B's own local Room has NEVER seen this group.
        cloud.members.getValue(groupSyncId).add(GroupCloudMember(userB.uid, userB.displayName!!, userB.email!!, GroupRole.MEMBER, 2_000L))

        val repoB = newRepository()
        val engineB = SharedGroupSyncEngine(repoB, cloud, FakeAuthRepository(userB))

        // Sanity: genuinely empty before sync.
        assertTrue(repoB.observeGroups().first().isEmpty())

        engineB.syncAll()

        val groupsAfterSync = repoB.observeGroups().first()
        assertEquals(1, groupsAfterSync.size)
        assertEquals(groupSyncId, groupsAfterSync.single().groupSyncId)
        assertEquals("Trip", groupsAfterSync.single().name)
    }

    @Test
    fun memberNotOwner_stillAppearsInGroupsList() = runTest {
        val cloud = FakeGroupCloudRepository()
        val groupSyncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        cloud.members.getValue(groupSyncId).add(GroupCloudMember(userB.uid, userB.displayName!!, userB.email!!, GroupRole.MEMBER, 2_000L))

        val repoB = newRepository()
        SharedGroupSyncEngine(repoB, cloud, FakeAuthRepository(userB)).syncAll()

        // B is a MEMBER, not the OWNER - GroupsListViewModel's underlying query
        // (ExpenseGroupDao.observeAll, unfiltered) must still return the group. Confirmed here at
        // the repository level, which is exactly what GroupsListViewModel.uiState is built from.
        val group = repoB.observeGroups().first().single()
        assertEquals(groupSyncId, group.groupSyncId)
        // The local group row itself carries no per-user role - role lives on the member row.
        val members = repoB.getMemberUidMap(group.id)
        assertTrue(userB.uid in members.keys)
    }

    @Test
    fun repeatedSyncAll_neverDuplicatesTheLocalGroup() = runTest {
        val cloud = FakeGroupCloudRepository()
        val groupSyncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        cloud.members.getValue(groupSyncId).add(GroupCloudMember(userB.uid, userB.displayName!!, userB.email!!, GroupRole.MEMBER, 2_000L))

        val repoB = newRepository()
        val engineB = SharedGroupSyncEngine(repoB, cloud, FakeAuthRepository(userB))

        engineB.syncAll()
        engineB.syncAll()
        engineB.syncAll()

        assertEquals(1, repoB.observeGroups().first().size)
    }

    // Regression test for the real bug: "Sync duplicates shared groups" - pressing Sync while
    // another sync pass is still in flight (e.g. resume + a manual tap) raced inside
    // GroupExpenseRepository.getOrCreateLocalGroupForSync, and BOTH passes could insert their own
    // local row for the same cloud groupSyncId. The fix is enforced at the database level (a UNIQUE
    // index on expense_groups.groupSyncId, see MIGRATION_13_14) with the repository catching and
    // recovering from the resulting insert conflict rather than crashing or duplicating.
    @Test
    fun concurrentSyncPasses_raceToCreateLocalGroup_resolveToExactlyOneLocalGroup() = runTest {
        val groupSyncId = "cloud-group-1"
        val delegateDao = FakeExpenseGroupDao()
        val racyDao = RacyExpenseGroupDao(delegateDao)
        val repository = GroupExpenseRepository(
            racyDao, FakeGroupMemberDao(), FakeGroupExpenseDao(), FakeSyncMetadataDao(), FakeGroupExpensePendingDeletionDao()
        )

        // Simulates a second, concurrently-running Sync pass inserting the SAME groupSyncId right
        // in the gap between THIS call's own "does a local row exist?" check and its own insert -
        // i.e. the "winner" of the race.
        var winnerId = -1L
        racyDao.onFirstNullResult = {
            winnerId = delegateDao.insert(ExpenseGroupEntity(name = "Trip", groupSyncId = groupSyncId))
        }

        // The actual call under test ("loser" of the race): its own getByGroupSyncId sees no row,
        // the hook above then inserts the winner row behind its back, and its own subsequent insert
        // must hit the fake DAO's uniqueness check (mirroring the real UNIQUE index) and recover to
        // the winner's id instead of throwing or creating a duplicate.
        val resultId = repository.getOrCreateLocalGroupForSync(groupSyncId, "Trip")

        assertEquals(winnerId, resultId)
        assertEquals(1, repository.observeGroups().first().size)
    }

    @Test
    fun acceptance_doesNotRequirePreExistingMembership() = runTest {
        // Reproduces the actual write ordering FirestoreGroupCloudRepository.acceptInvitation
        // uses: the invitation is marked ACCEPTED first, and only then is the member doc created -
        // B is not a member of anything at the moment acceptInvitation is called.
        val cloud = FakeGroupCloudRepository()
        val groupSyncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        cloud.sendInvitation(groupSyncId, "Trip", userA.uid, userA.email!!, userB.email!!)
        val invitation = cloud.observeIncomingInvitations(userB.email!!).first().single()

        assertTrue(cloud.members[groupSyncId].orEmpty().none { it.uid == userB.uid })

        val result = cloud.acceptInvitation(invitation, userB.uid, userB.displayName!!, userB.email!!)

        assertTrue(result.isSuccess)
        assertTrue(cloud.members.getValue(groupSyncId).any { it.uid == userB.uid })
    }

    @Test
    fun emailNormalization_mixedCaseAndWhitespaceInvite_stillDiscoveredByNormalizedAuthEmail() = runTest {
        val cloud = FakeGroupCloudRepository()
        val groupSyncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()

        // A types the invite with mixed case and stray whitespace.
        cloud.sendInvitation(groupSyncId, "Trip", userA.uid, userA.email!!, "  Bob@Example.COM  ")

        // B's authenticated email (as Firebase Auth would report it) is plain lowercase here, but
        // the point of this test is that the CLIENT-side query normalization (trim+lowercase, see
        // FirestoreGroupCloudRepository.observeIncomingInvitations) is what makes the match work
        // regardless of how the inviter typed it - not that both sides happen to already match.
        val discovered = cloud.observeIncomingInvitations(userB.email!!).first()

        assertEquals(1, discovered.size)
        assertEquals("bob@example.com", discovered.single().inviteeEmail)
    }

    @Test
    fun missingMembershipIndex_membershipCreatedAndThenDiscoveredBySync() = runTest {
        val cloud = FakeGroupCloudRepository()
        val groupSyncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        cloud.sendInvitation(groupSyncId, "Trip", userA.uid, userA.email!!, userB.email!!)
        val invitation = cloud.observeIncomingInvitations(userB.email!!).first().single()

        // Before acceptance: no membership discoverable for B at all.
        assertTrue(cloud.getMyMemberships(userB.uid).getOrThrow().isEmpty())

        cloud.acceptInvitation(invitation, userB.uid, userB.displayName!!, userB.email!!)

        // After acceptance: the membership index now exists and is what syncAll discovers.
        val memberships = cloud.getMyMemberships(userB.uid).getOrThrow()
        assertEquals(1, memberships.size)
        assertEquals(groupSyncId, memberships.single().groupId)

        val repoB = newRepository()
        SharedGroupSyncEngine(repoB, cloud, FakeAuthRepository(userB)).syncAll()
        assertEquals(1, repoB.observeGroups().first().size)
    }
}

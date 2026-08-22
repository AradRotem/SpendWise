package com.aradrotem.spendwise.data.sync

import com.aradrotem.spendwise.auth.FakeAuthRepository
import com.aradrotem.spendwise.data.auth.AuthUser
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import com.aradrotem.spendwise.data.notifications.FakeNotificationPreferencesStore
import com.aradrotem.spendwise.data.notifications.FakeNotificationSender
import com.aradrotem.spendwise.data.notifications.NotificationChannels
import com.aradrotem.spendwise.data.repository.FakeGroupCloudRepository
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

// End-to-end tests of the previously-missing sync path: two independent "devices" (Account A and
// Account B), each with their own in-memory Room-equivalent (fake DAOs) and their own
// GroupExpenseRepository/SharedGroupSyncEngine, sharing ONE FakeGroupCloudRepository instance as
// the common "Firestore backend". This is the setup the Step 19 completion pass specifically
// asked for: proof that an expense created on B's device has a real code path that lets A pull
// and reconcile it into local Room.
class SharedGroupSyncEngineTest {

    private val cloud = FakeGroupCloudRepository()

    private val userA = AuthUser(uid = "uid-a", email = "alice@example.com", displayName = "Alice")
    private val userB = AuthUser(uid = "uid-b", email = "bob@example.com", displayName = "Bob")
    private val userC = AuthUser(uid = "uid-c", email = "carol@example.com", displayName = "Carol")

    private fun newDevice(user: AuthUser): Triple<GroupExpenseRepository, SharedGroupSyncEngine, FakeAuthRepository> {
        val repository = GroupExpenseRepository(
            FakeExpenseGroupDao(), FakeGroupMemberDao(), FakeGroupExpenseDao(),
            FakeSyncMetadataDao(), FakeGroupExpensePendingDeletionDao()
        )
        val auth = FakeAuthRepository(user)
        val engine = SharedGroupSyncEngine(
            groupExpenseRepository = repository,
            groupCloudRepository = cloud,
            authRepository = auth,
            notificationSender = FakeNotificationSender(),
            notificationPreferencesStore = FakeNotificationPreferencesStore()
        )
        return Triple(repository, engine, auth)
    }

    @Test
    fun invitationFlow_acceptCreatesLocalGroupWithBothMembers() = runTest {
        val (repoA, engineA, _) = newDevice(userA)
        val (repoB, engineB, _) = newDevice(userB)

        val groupSyncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        cloud.sendInvitation(groupSyncId, "Trip", userA.uid, userA.email!!, userB.email!!)

        // B refreshes their incoming invitations.
        val incoming = cloud.observeIncomingInvitations(userB.email!!)
        val invitation = incoming.first().single()
        assertEquals("Trip", invitation.groupName)

        cloud.acceptInvitation(invitation, userB.uid, userB.displayName!!, userB.email!!)
        engineB.syncGroup(groupSyncId, "Trip")

        val groupB = repoB.observeGroups().first().single()
        assertEquals(groupSyncId, groupB.groupSyncId)
        val membersB = repoB.observeMembers(groupB.id).first()
        assertEquals(setOf(userA.uid, userB.uid), membersB.mapNotNull { it.memberUid }.toSet())

        // Sanity: A's own device state is untouched by B's local sync.
        assertTrue(repoA.observeGroups().first().isEmpty())
    }

    private suspend fun setUpSharedGroupOnBothDevices(): Pair<Long, Long> {
        val (repoA, engineA, _) = devices
        val (repoB, engineB, _) = devicesB

        val groupSyncId = cloud.createSharedGroup("Trip", userA.uid, userA.displayName!!, userA.email!!).getOrThrow()
        val localGroupIdA = repoA.getOrCreateLocalGroupForSync(groupSyncId, "Trip")
        repoA.markGroupShared(localGroupIdA, groupSyncId, userA.uid)

        cloud.sendInvitation(groupSyncId, "Trip", userA.uid, userA.email!!, userB.email!!)
        val invitation = cloud.observeIncomingInvitations(userB.email!!).first().single()
        cloud.acceptInvitation(invitation, userB.uid, userB.displayName!!, userB.email!!)

        engineA.syncGroup(groupSyncId, "Trip")
        engineB.syncGroup(groupSyncId, "Trip")

        val localGroupIdB = repoB.observeGroups().first().single { it.groupSyncId == groupSyncId }.id
        return localGroupIdA to localGroupIdB
    }

    // Devices are created once per test via lazy properties so setUpSharedGroupOnBothDevices and
    // the test body reference the exact same repository/engine instances.
    private lateinit var devices: Triple<GroupExpenseRepository, SharedGroupSyncEngine, FakeAuthRepository>
    private lateinit var devicesB: Triple<GroupExpenseRepository, SharedGroupSyncEngine, FakeAuthRepository>

    private fun initDevices() {
        devices = newDevice(userA)
        devicesB = newDevice(userB)
    }

    @Test
    fun expenseFlow_pushedByB_pulledByA_exactlyOnce_noDuplicateOnResync() = runTest {
        initDevices()
        val (localGroupIdA, localGroupIdB) = setUpSharedGroupOnBothDevices()
        val (repoA, engineA, _) = devices
        val (repoB, engineB, _) = devicesB

        val membersB = repoB.getMemberUidMap(localGroupIdB)
        val bobLocalId = membersB.getValue(userB.uid)
        val aliceLocalId = membersB.getValue(userA.uid)

        repoB.createExpense(
            localGroupIdB, "Dinner", 2_000L, 0L, bobLocalId, GroupSplitMethod.EQUAL, "",
            mapOf(bobLocalId to 1_000L, aliceLocalId to 1_000L)
        )
        val groupSyncId = repoB.getGroup(localGroupIdB)!!.groupSyncId!!
        engineB.syncGroup(groupSyncId, "Trip")

        assertEquals(1, cloud.expenses[groupSyncId]?.size)

        engineA.syncGroup(groupSyncId, "Trip")
        val expensesA = repoA.observeExpenses(localGroupIdA).first()
        assertEquals(1, expensesA.size)
        assertEquals("Dinner", expensesA.single().title)
        assertEquals(2_000L, expensesA.single().amountCents)

        // Re-sync must not duplicate.
        engineA.syncGroup(groupSyncId, "Trip")
        val expensesAfterResync = repoA.observeExpenses(localGroupIdA).first()
        assertEquals(1, expensesAfterResync.size)
    }

    @Test
    fun editFlow_editedByB_propagatesToA() = runTest {
        initDevices()
        val (localGroupIdA, localGroupIdB) = setUpSharedGroupOnBothDevices()
        val (repoA, engineA, _) = devices
        val (repoB, engineB, _) = devicesB

        val membersB = repoB.getMemberUidMap(localGroupIdB)
        val bobLocalId = membersB.getValue(userB.uid)
        val aliceLocalId = membersB.getValue(userA.uid)

        val expenseId = repoB.createExpense(
            localGroupIdB, "Dinner", 2_000L, 0L, bobLocalId, GroupSplitMethod.EQUAL, "",
            mapOf(bobLocalId to 1_000L, aliceLocalId to 1_000L)
        ).getOrThrow()
        val groupSyncId = repoB.getGroup(localGroupIdB)!!.groupSyncId!!
        engineB.syncGroup(groupSyncId, "Trip")
        engineA.syncGroup(groupSyncId, "Trip")

        repoB.updateExpense(
            expenseId, localGroupIdB, "Dinner (updated)", 3_000L, 0L, bobLocalId, GroupSplitMethod.EQUAL, "",
            mapOf(bobLocalId to 1_500L, aliceLocalId to 1_500L)
        )
        engineB.syncGroup(groupSyncId, "Trip")
        engineA.syncGroup(groupSyncId, "Trip")

        val expensesA = repoA.observeExpenses(localGroupIdA).first()
        assertEquals(1, expensesA.size)
        assertEquals("Dinner (updated)", expensesA.single().title)
        assertEquals(3_000L, expensesA.single().amountCents)
    }

    @Test
    fun deleteFlow_deletedByB_propagatesToA() = runTest {
        initDevices()
        val (localGroupIdA, localGroupIdB) = setUpSharedGroupOnBothDevices()
        val (repoA, engineA, _) = devices
        val (repoB, engineB, _) = devicesB

        val membersB = repoB.getMemberUidMap(localGroupIdB)
        val bobLocalId = membersB.getValue(userB.uid)
        val aliceLocalId = membersB.getValue(userA.uid)

        val expenseId = repoB.createExpense(
            localGroupIdB, "Dinner", 2_000L, 0L, bobLocalId, GroupSplitMethod.EQUAL, "",
            mapOf(bobLocalId to 1_000L, aliceLocalId to 1_000L)
        ).getOrThrow()
        val groupSyncId = repoB.getGroup(localGroupIdB)!!.groupSyncId!!
        engineB.syncGroup(groupSyncId, "Trip")
        engineA.syncGroup(groupSyncId, "Trip")
        assertEquals(1, repoA.observeExpenses(localGroupIdA).first().size)

        val expense = repoB.getExpense(expenseId)!!
        repoB.deleteExpense(expense)
        engineB.syncGroup(groupSyncId, "Trip")
        assertTrue(cloud.expenses[groupSyncId].orEmpty().isEmpty())

        engineA.syncGroup(groupSyncId, "Trip")
        val expensesAfterDelete = repoA.observeExpenses(localGroupIdA).first()
        assertTrue(expensesAfterDelete.isEmpty())

        // Re-sync must not resurrect the deleted expense.
        engineA.syncGroup(groupSyncId, "Trip")
        assertTrue(repoA.observeExpenses(localGroupIdA).first().isEmpty())
    }

    @Test
    fun offlinePendingFlow_expenseCreatedWithoutSync_uploadsOnNextSync() = runTest {
        initDevices()
        val (localGroupIdA, localGroupIdB) = setUpSharedGroupOnBothDevices()
        val (repoA, engineA, _) = devices
        val (repoB, _, _) = devicesB

        val membersB = repoB.getMemberUidMap(localGroupIdB)
        val bobLocalId = membersB.getValue(userB.uid)
        val aliceLocalId = membersB.getValue(userA.uid)
        val groupSyncId = repoB.getGroup(localGroupIdB)!!.groupSyncId!!

        // Simulates "B is offline": create the expense but do not sync.
        repoB.createExpense(
            localGroupIdB, "Offline snack", 500L, 0L, bobLocalId, GroupSplitMethod.EQUAL, "",
            mapOf(bobLocalId to 250L, aliceLocalId to 250L)
        )
        // Still usable locally and visible in B's own list immediately.
        assertEquals(1, repoB.observeExpenses(localGroupIdB).first().size)
        // Not yet on the "cloud" - connectivity hasn't returned.
        assertTrue(cloud.expenses[groupSyncId].orEmpty().isEmpty())

        // "Connectivity returns" - B's sync engine runs (e.g. ConnectivityObserver.onConnected).
        val (_, engineBReconnected, _) = devicesB
        engineBReconnected.syncGroup(groupSyncId, "Trip")
        assertEquals(1, cloud.expenses[groupSyncId]?.size)

        engineA.syncGroup(groupSyncId, "Trip")
        assertEquals(1, repoA.observeExpenses(localGroupIdA).first().size)
    }

    @Test
    fun accountIsolation_nonMemberNeverDiscoversOrReceivesGroup() = runTest {
        initDevices()
        setUpSharedGroupOnBothDevices()
        val (repoC, engineC, _) = newDevice(userC)

        engineC.syncAll()

        assertTrue(repoC.observeGroups().first().isEmpty())
    }

    @Test
    fun newRemoteExpense_firesSharedGroupNotification_forRecipientNotCreator() = runTest {
        initDevices()
        val (localGroupIdA, localGroupIdB) = setUpSharedGroupOnBothDevices()
        val (repoA, _, authA) = devices
        val (repoB, engineB, _) = devicesB

        val senderA = FakeNotificationSender()
        val prefsA = FakeNotificationPreferencesStore()
        val engineAWithNotifications = SharedGroupSyncEngine(repoA, cloud, authA, senderA, prefsA)

        val membersB = repoB.getMemberUidMap(localGroupIdB)
        val bobLocalId = membersB.getValue(userB.uid)
        val aliceLocalId = membersB.getValue(userA.uid)
        repoB.createExpense(
            localGroupIdB, "Dinner", 2_000L, 0L, bobLocalId, GroupSplitMethod.EQUAL, "",
            mapOf(bobLocalId to 1_000L, aliceLocalId to 1_000L)
        )
        val groupSyncId = repoB.getGroup(localGroupIdB)!!.groupSyncId!!
        engineB.syncGroup(groupSyncId, "Trip")

        engineAWithNotifications.syncGroup(groupSyncId, "Trip")

        assertEquals(1, senderA.sent.size)
        assertEquals(NotificationChannels.SHARED_GROUPS, senderA.sent.single().channelId)
    }

    @Test
    fun sharedGroupNotificationsDisabled_noNotificationFired() = runTest {
        initDevices()
        val (localGroupIdA, localGroupIdB) = setUpSharedGroupOnBothDevices()
        val (repoA, _, authA) = devices
        val (repoB, engineB, _) = devicesB

        val senderA = FakeNotificationSender()
        val prefsA = FakeNotificationPreferencesStore(sharedGroupNotificationsEnabled = false)
        val engineAWithNotifications = SharedGroupSyncEngine(repoA, cloud, authA, senderA, prefsA)

        val membersB = repoB.getMemberUidMap(localGroupIdB)
        val bobLocalId = membersB.getValue(userB.uid)
        val aliceLocalId = membersB.getValue(userA.uid)
        repoB.createExpense(
            localGroupIdB, "Dinner", 2_000L, 0L, bobLocalId, GroupSplitMethod.EQUAL, "",
            mapOf(bobLocalId to 1_000L, aliceLocalId to 1_000L)
        )
        val groupSyncId = repoB.getGroup(localGroupIdB)!!.groupSyncId!!
        engineB.syncGroup(groupSyncId, "Trip")

        engineAWithNotifications.syncGroup(groupSyncId, "Trip")

        assertTrue(senderA.sent.isEmpty())
    }
}

package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.GroupExpenseEntity
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupExpenseReconcilerTest {

    private fun remote(cloudId: String, paidByUid: String = "uid-a", shares: Map<String, Long> = mapOf("uid-a" to 1_000L)) =
        RemoteGroupExpense(
            cloudId = cloudId, title = "Dinner", amountCents = 1_000L, dateEpochDay = 0L, paidByUid = paidByUid,
            splitMethod = GroupSplitMethod.EQUAL, note = "", createdAtEpochMillis = 1_000L, createdByUid = paidByUid, shares = shares
        )

    @Test
    fun newRemoteExpense_notLocallyPresent_producesInsert() {
        val result = GroupExpenseReconciler.reconcile(
            groupId = 1L,
            remoteDocs = listOf(remote("cloud-1")),
            localExpensesByCloudId = emptyMap(),
            memberUidToLocalId = mapOf("uid-a" to 10L)
        )
        assertEquals(1, result.size)
        assertNull(result.single().existingLocalId)
        assertEquals(10L, result.single().entity.paidByMemberId)
        assertEquals(mapOf(10L to 1_000L), result.single().shares)
    }

    @Test
    fun existingLocalExpense_matchedByCloudId_producesUpdateInPlace() {
        val existing = GroupExpenseEntity(
            id = 99L, groupId = 1L, title = "Old title", amountCents = 500L, dateEpochDay = 0L,
            paidByMemberId = 10L, splitMethod = GroupSplitMethod.EQUAL, cloudId = "cloud-1"
        )
        val result = GroupExpenseReconciler.reconcile(
            groupId = 1L,
            remoteDocs = listOf(remote("cloud-1")),
            localExpensesByCloudId = mapOf("cloud-1" to existing),
            memberUidToLocalId = mapOf("uid-a" to 10L)
        )
        assertEquals(99L, result.single().existingLocalId)
        assertEquals("Dinner", result.single().entity.title)
    }

    @Test
    fun payerUidNotYetAMember_expenseSkipped() {
        // The payer hasn't accepted their invitation yet (no local member row for their uid) -
        // applying this would create an expense with a nonexistent payer, so it's skipped.
        val result = GroupExpenseReconciler.reconcile(
            groupId = 1L,
            remoteDocs = listOf(remote("cloud-1", paidByUid = "uid-unknown")),
            localExpensesByCloudId = emptyMap(),
            memberUidToLocalId = mapOf("uid-a" to 10L)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun participantUidNotYetAMember_expenseSkipped() {
        val result = GroupExpenseReconciler.reconcile(
            groupId = 1L,
            remoteDocs = listOf(remote("cloud-1", shares = mapOf("uid-a" to 500L, "uid-unknown" to 500L))),
            localExpensesByCloudId = emptyMap(),
            memberUidToLocalId = mapOf("uid-a" to 10L)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun localExpenseAbsentFromRemoteSnapshot_flaggedForDeletion() {
        val existing = GroupExpenseEntity(
            id = 99L, groupId = 1L, title = "Deleted remotely", amountCents = 500L, dateEpochDay = 0L,
            paidByMemberId = 10L, splitMethod = GroupSplitMethod.EQUAL, cloudId = "cloud-old"
        )
        val toDelete = GroupExpenseReconciler.localIdsToDelete(
            localExpensesByCloudId = mapOf("cloud-old" to existing),
            remoteCloudIds = setOf("cloud-1", "cloud-2")
        )
        assertEquals(listOf(99L), toDelete)
    }

    @Test
    fun localExpenseStillInRemoteSnapshot_notFlaggedForDeletion() {
        val existing = GroupExpenseEntity(
            id = 99L, groupId = 1L, title = "Still there", amountCents = 500L, dateEpochDay = 0L,
            paidByMemberId = 10L, splitMethod = GroupSplitMethod.EQUAL, cloudId = "cloud-1"
        )
        val toDelete = GroupExpenseReconciler.localIdsToDelete(
            localExpensesByCloudId = mapOf("cloud-1" to existing),
            remoteCloudIds = setOf("cloud-1")
        )
        assertTrue(toDelete.isEmpty())
    }

    @Test
    fun multipleRemoteExpenses_noDuplicatesProduced() {
        val result = GroupExpenseReconciler.reconcile(
            groupId = 1L,
            remoteDocs = listOf(remote("cloud-1"), remote("cloud-2")),
            localExpensesByCloudId = emptyMap(),
            memberUidToLocalId = mapOf("uid-a" to 10L)
        )
        assertEquals(setOf("cloud-1", "cloud-2"), result.map { it.cloudId }.toSet())
    }
}

package com.aradrotem.spendwise.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationPolicyTest {

    private fun invitation(groupId: String, email: String, status: GroupInvitationStatus) = GroupInvitation(
        id = "inv-1", groupId = groupId, groupName = "Trip", inviterUid = "owner-uid", inviterEmail = "owner@example.com",
        inviteeEmail = email, status = status, createdAtEpochMillis = 1_000L
    )

    @Test
    fun pendingInvitationExists_blocksDuplicate() {
        val existing = listOf(invitation("g1", "friend@example.com", GroupInvitationStatus.PENDING))
        assertTrue(InvitationPolicy.hasActivePendingInvitation(existing, "g1", "friend@example.com"))
    }

    @Test
    fun caseAndWhitespaceInsensitive() {
        val existing = listOf(invitation("g1", "Friend@Example.com", GroupInvitationStatus.PENDING))
        assertTrue(InvitationPolicy.hasActivePendingInvitation(existing, "g1", " friend@example.com "))
    }

    @Test
    fun declinedInvitation_doesNotBlockReinvite() {
        val existing = listOf(invitation("g1", "friend@example.com", GroupInvitationStatus.DECLINED))
        assertFalse(InvitationPolicy.hasActivePendingInvitation(existing, "g1", "friend@example.com"))
    }

    @Test
    fun cancelledInvitation_doesNotBlockReinvite() {
        val existing = listOf(invitation("g1", "friend@example.com", GroupInvitationStatus.CANCELLED))
        assertFalse(InvitationPolicy.hasActivePendingInvitation(existing, "g1", "friend@example.com"))
    }

    @Test
    fun acceptedInvitation_doesNotBlockReinvite() {
        // An accepted invitation means the invitee is already a member - re-inviting them is a
        // separate concern (should be prevented by "already a member" logic, not this policy).
        val existing = listOf(invitation("g1", "friend@example.com", GroupInvitationStatus.ACCEPTED))
        assertFalse(InvitationPolicy.hasActivePendingInvitation(existing, "g1", "friend@example.com"))
    }

    @Test
    fun pendingInvitation_differentGroup_doesNotBlock() {
        val existing = listOf(invitation("g1", "friend@example.com", GroupInvitationStatus.PENDING))
        assertFalse(InvitationPolicy.hasActivePendingInvitation(existing, "g2", "friend@example.com"))
    }

    @Test
    fun noExistingInvitations_neverBlocks() {
        assertFalse(InvitationPolicy.hasActivePendingInvitation(emptyList(), "g1", "friend@example.com"))
    }
}

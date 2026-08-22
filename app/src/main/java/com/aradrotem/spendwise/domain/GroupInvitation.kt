package com.aradrotem.spendwise.domain

enum class GroupInvitationStatus { PENDING, ACCEPTED, DECLINED, CANCELLED }

// Mirrors one groups/{groupId}/invitations/{invitationId} Firestore document (see
// GroupCloudRepository / firestore.rules). groupName is denormalized onto the invitation itself so
// the invitee's pending-invitations list can render without an extra read of the group document
// they aren't a member of yet (and which rules wouldn't let them read anyway before accepting).
data class GroupInvitation(
    val id: String,
    val groupId: String,
    val groupName: String,
    val inviterUid: String,
    val inviterEmail: String,
    val inviteeEmail: String,
    val status: GroupInvitationStatus,
    val createdAtEpochMillis: Long,
    val respondedAtEpochMillis: Long? = null
)

// Pure duplicate-prevention rule, kept separate from any Firestore query so it's directly
// unit-testable: a new invitation to the same (groupId, inviteeEmail) pair is blocked only while
// an earlier one for that pair is still PENDING - a previously declined/cancelled invitation
// doesn't permanently lock the invitee out of being invited again.
object InvitationPolicy {
    fun hasActivePendingInvitation(existing: List<GroupInvitation>, groupId: String, inviteeEmail: String): Boolean {
        val normalizedEmail = inviteeEmail.trim().lowercase()
        return existing.any {
            it.groupId == groupId && it.status == GroupInvitationStatus.PENDING && it.inviteeEmail.trim().lowercase() == normalizedEmail
        }
    }
}

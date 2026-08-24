package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.GroupRole
import com.aradrotem.spendwise.domain.GroupInvitation
import com.aradrotem.spendwise.domain.RemoteGroupExpense
import kotlinx.coroutines.flow.Flow

// A Firebase-free classification of why observeIncomingInvitations' live listener failed - lets
// IncomingInvitationsViewModel distinguish failure classes (and show a message that actually helps
// diagnose the problem) without importing any Firebase type itself, preserving the same isolation
// rule AuthRepository documents for FirebaseAuth: only FirestoreGroupCloudRepository may touch the
// Firebase SDK directly. See FirestoreGroupCloudRepository.observeIncomingInvitations for how a
// real FirebaseFirestoreException.Code is mapped into one of these.
sealed class IncomingInvitationsLoadError(message: String) : Exception(message) {
    // The collection-group query's required composite index isn't usable right now - either still
    // building right after a fresh deploy, or missing/not yet deployed (see firestore.indexes.json,
    // and note collection-group queries are never auto-indexed by Firestore, even for pure equality
    // filters like this one).
    class IndexUnavailable(message: String) : IncomingInvitationsLoadError(message)

    // Firestore rejected the read outright - most often the live security rules are stale relative
    // to firestore.rules (missing the invitee-email-match branch or its .lower() normalization), or
    // the signed-in account's ID token doesn't carry the email claim this rule needs.
    class PermissionDenied(message: String) : IncomingInvitationsLoadError(message)

    class Other(message: String) : IncomingInvitationsLoadError(message)
}

data class GroupCloudMember(
    val uid: String,
    val displayName: String,
    val email: String,
    val role: GroupRole,
    val joinedAtEpochMillis: Long
)

// One users/{uid}/groupMemberships/{groupId} document - the discovery index SharedGroupSyncEngine
// walks to find every shared group the current account belongs to.
data class GroupMembershipRef(
    val groupId: String,
    val groupName: String,
    val role: GroupRole
)

// The cloud-side half of a real shared group (see GroupExpenseRepository for the local-Room
// half). Kept as an interface, mirroring UserProfileRepository/ReceiptStorageRepository, so
// ViewModels can be unit-tested against a fake with no live Firestore involved - only
// FirestoreGroupCloudRepository itself touches the Firebase SDK directly.
interface GroupCloudRepository {

    // Creates the canonical groups/{groupId} document plus the owner's own membership doc, for
    // either a brand-new shared group or one being upgraded from a purely local group (Step 19
    // Part 3) - the caller is responsible for persisting the returned id onto the local
    // ExpenseGroupEntity.groupSyncId; nothing here touches local Room.
    suspend fun createSharedGroup(groupName: String, ownerUid: String, ownerDisplayName: String, ownerEmail: String): Result<String>

    // Called when [uid] deletes a shared group locally (see GroupsListViewModel.onDeleteGroup).
    // Always removes the caller's OWN users/{uid}/groupMemberships/{groupId} discovery-index doc -
    // this is what stops SharedGroupSyncEngine.syncAll's getMyMemberships from resurrecting the
    // group on this account's next sync, regardless of role. If [uid] is the group's owner, also
    // tears down the group doc itself plus its members/invitations/expenses subcollections (a
    // non-owner's attempt at that part is simply rejected by Firestore rules and ignored - their
    // own membership-index removal still goes through).
    suspend fun deleteSharedGroup(groupId: String, uid: String): Result<Unit>

    suspend fun sendInvitation(groupId: String, groupName: String, inviterUid: String, inviterEmail: String, inviteeEmail: String): Result<Unit>

    fun observeIncomingInvitations(email: String): Flow<List<GroupInvitation>>
    fun observeSentInvitations(groupId: String): Flow<List<GroupInvitation>>

    suspend fun acceptInvitation(invitation: GroupInvitation, uid: String, displayName: String, email: String): Result<Unit>
    suspend fun declineInvitation(invitation: GroupInvitation): Result<Unit>
    suspend fun cancelInvitation(invitation: GroupInvitation): Result<Unit>

    fun observeMembers(groupId: String): Flow<List<GroupCloudMember>>
    suspend fun removeMember(groupId: String, uid: String): Result<Unit>

    suspend fun pushExpense(groupId: String, expense: RemoteGroupExpense): Result<Unit>
    suspend fun deleteExpense(groupId: String, cloudId: String): Result<Unit>
    fun observeExpenses(groupId: String): Flow<List<RemoteGroupExpense>>

    // One-shot pull methods used by SharedGroupSyncEngine's reconciliation pass, which runs once
    // per sync rather than as a live subscription (the observe* Flow methods above remain for any
    // screen that wants a live view of one already-open group).
    suspend fun getMyMemberships(uid: String): Result<List<GroupMembershipRef>>
    suspend fun getMembersOnce(groupId: String): Result<List<GroupCloudMember>>
    suspend fun getExpensesOnce(groupId: String): Result<List<RemoteGroupExpense>>
}

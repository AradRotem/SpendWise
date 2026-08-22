package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.GroupRole
import com.aradrotem.spendwise.domain.GroupInvitation
import com.aradrotem.spendwise.domain.RemoteGroupExpense
import kotlinx.coroutines.flow.Flow

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

    suspend fun sendInvitation(groupId: String, groupName: String, inviterUid: String, inviterEmail: String, inviteeEmail: String): Result<Unit>

    fun observeIncomingInvitations(email: String): Flow<List<GroupInvitation>>
    fun observeSentInvitations(groupId: String): Flow<List<GroupInvitation>>

    suspend fun acceptInvitation(invitation: GroupInvitation, uid: String, displayName: String, email: String): Result<Unit>
    suspend fun declineInvitation(invitation: GroupInvitation): Result<Unit>

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

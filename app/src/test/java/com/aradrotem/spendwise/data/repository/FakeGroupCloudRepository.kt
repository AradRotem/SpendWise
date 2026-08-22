package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.GroupRole
import com.aradrotem.spendwise.domain.GroupInvitation
import com.aradrotem.spendwise.domain.GroupInvitationStatus
import com.aradrotem.spendwise.domain.RemoteGroupExpense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// In-memory GroupCloudRepository test double - lets ViewModel tests exercise the invite/accept/
// decline flow with no live Firestore involved, same style as FakeAuthRepository.
class FakeGroupCloudRepository : GroupCloudRepository {
    private var nextGroupId = 1
    private var nextInvitationId = 1
    val groups = mutableMapOf<String, Triple<String, String, String>>() // groupId -> (name, ownerUid, ownerEmail)
    private val invitationsState = MutableStateFlow<List<GroupInvitation>>(emptyList())
    val members = mutableMapOf<String, MutableList<GroupCloudMember>>()

    var createSharedGroupResult: Result<String>? = null
    var sendInvitationResult: Result<Unit> = Result.success(Unit)
    var acceptInvitationResult: Result<Unit> = Result.success(Unit)

    override suspend fun createSharedGroup(groupName: String, ownerUid: String, ownerDisplayName: String, ownerEmail: String): Result<String> {
        createSharedGroupResult?.let { return it }
        val groupId = "group-${nextGroupId++}"
        groups[groupId] = Triple(groupName, ownerUid, ownerEmail)
        members.getOrPut(groupId) { mutableListOf() }.add(GroupCloudMember(ownerUid, ownerDisplayName, ownerEmail, GroupRole.OWNER, 1_000L))
        return Result.success(groupId)
    }

    override suspend fun sendInvitation(groupId: String, groupName: String, inviterUid: String, inviterEmail: String, inviteeEmail: String): Result<Unit> {
        if (sendInvitationResult.isSuccess) {
            invitationsState.value = invitationsState.value + GroupInvitation(
                id = "inv-${nextInvitationId++}", groupId = groupId, groupName = groupName, inviterUid = inviterUid,
                inviterEmail = inviterEmail, inviteeEmail = inviteeEmail.trim().lowercase(), status = GroupInvitationStatus.PENDING,
                createdAtEpochMillis = 1_000L
            )
        }
        return sendInvitationResult
    }

    override fun observeIncomingInvitations(email: String): Flow<List<GroupInvitation>> =
        MutableStateFlow(invitationsState.value.filter { it.inviteeEmail == email.trim().lowercase() && it.status == GroupInvitationStatus.PENDING }).asStateFlow()

    override fun observeSentInvitations(groupId: String): Flow<List<GroupInvitation>> =
        MutableStateFlow(invitationsState.value.filter { it.groupId == groupId }).asStateFlow()

    override suspend fun acceptInvitation(invitation: GroupInvitation, uid: String, displayName: String, email: String): Result<Unit> {
        if (acceptInvitationResult.isSuccess) {
            invitationsState.value = invitationsState.value.map {
                if (it.id == invitation.id) it.copy(status = GroupInvitationStatus.ACCEPTED) else it
            }
            members.getOrPut(invitation.groupId) { mutableListOf() }.add(GroupCloudMember(uid, displayName, email, GroupRole.MEMBER, 2_000L))
        }
        return acceptInvitationResult
    }

    override suspend fun declineInvitation(invitation: GroupInvitation): Result<Unit> {
        invitationsState.value = invitationsState.value.map {
            if (it.id == invitation.id) it.copy(status = GroupInvitationStatus.DECLINED) else it
        }
        return Result.success(Unit)
    }

    override fun observeMembers(groupId: String): Flow<List<GroupCloudMember>> =
        MutableStateFlow(members[groupId].orEmpty()).asStateFlow()

    override suspend fun removeMember(groupId: String, uid: String): Result<Unit> {
        members[groupId]?.removeAll { it.uid == uid }
        return Result.success(Unit)
    }

    // groupId -> cloudId -> expense. A real map (not a Flow) since the test double's "cloud" state
    // must be readable/writable synchronously from two independent repository instances
    // simulating two devices sharing one Firestore backend.
    val expenses = mutableMapOf<String, MutableMap<String, RemoteGroupExpense>>()

    override suspend fun pushExpense(groupId: String, expense: RemoteGroupExpense): Result<Unit> {
        expenses.getOrPut(groupId) { mutableMapOf() }[expense.cloudId] = expense
        return Result.success(Unit)
    }

    override suspend fun deleteExpense(groupId: String, cloudId: String): Result<Unit> {
        expenses[groupId]?.remove(cloudId)
        return Result.success(Unit)
    }

    override fun observeExpenses(groupId: String): Flow<List<RemoteGroupExpense>> =
        MutableStateFlow(expenses[groupId]?.values?.toList().orEmpty()).asStateFlow()

    override suspend fun getMyMemberships(uid: String): Result<List<GroupMembershipRef>> = Result.success(
        groups.mapNotNull { (groupId, info) ->
            val membership = members[groupId]?.firstOrNull { it.uid == uid } ?: return@mapNotNull null
            GroupMembershipRef(groupId, info.first, membership.role)
        }
    )

    override suspend fun getMembersOnce(groupId: String): Result<List<GroupCloudMember>> =
        Result.success(members[groupId].orEmpty())

    override suspend fun getExpensesOnce(groupId: String): Result<List<RemoteGroupExpense>> =
        Result.success(expenses[groupId]?.values?.toList().orEmpty())
}

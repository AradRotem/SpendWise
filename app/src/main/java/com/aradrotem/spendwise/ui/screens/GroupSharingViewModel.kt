package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.auth.AuthRepository
import com.aradrotem.spendwise.data.local.ExpenseGroupEntity
import com.aradrotem.spendwise.data.repository.GroupCloudRepository
import com.aradrotem.spendwise.data.repository.GroupExpenseRepository
import com.aradrotem.spendwise.domain.GroupInvitation
import com.aradrotem.spendwise.domain.InvitationPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupSharingUiState(
    val isSharedGroup: Boolean = false,
    val sentInvitations: List<GroupInvitation> = emptyList(),
    val isSendingInvite: Boolean = false,
    val inviteError: String? = null,
    val inviteSuccessMessage: String? = null
)

// Deliberately independent of GroupDetailsViewModel (which owns the already-stable, well-tested
// group/expense/balance UI state) - sharing is an additive Step 19 concern layered on top of the
// existing screen, kept as its own small ViewModel so it can be null-safe about
// GroupCloudRepository (absent in legacy/no-Firebase builds) without touching that logic at all.
class GroupSharingViewModel(
    private val groupExpenseRepository: GroupExpenseRepository,
    private val groupCloudRepository: GroupCloudRepository?,
    private val groupId: Long,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val group = MutableStateFlow<ExpenseGroupEntity?>(null)
    private val messages = MutableStateFlow<Pair<String?, String?>>(null to null) // error, success

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<GroupSharingUiState> = combine(
        groupExpenseRepository.observeGroups(),
        messages
    ) { groups, msgs -> groups.firstOrNull { it.id == groupId } to msgs }
        .flatMapLatest { (currentGroup, msgs) ->
            group.value = currentGroup
            val syncId = currentGroup?.groupSyncId
            val invitationsFlow = if (syncId != null && groupCloudRepository != null) {
                groupCloudRepository.observeSentInvitations(syncId)
            } else {
                flowOf(emptyList())
            }
            invitationsFlow.map { invitations ->
                GroupSharingUiState(
                    isSharedGroup = currentGroup?.isSharedGroup == true,
                    sentInvitations = invitations,
                    inviteError = msgs.first,
                    inviteSuccessMessage = msgs.second
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupSharingUiState())

    // Invites a real SpendWise user by email. If the group is still purely local (never shared
    // before), it is first upgraded to a real shared group (see
    // GroupCloudRepository.createSharedGroup) - existing members/expenses are never touched by
    // this, only the group row itself gains a groupSyncId/ownerUid.
    fun inviteByEmail(email: String) {
        val cloudRepository = groupCloudRepository
        if (cloudRepository == null) {
            messages.value = "Sign in to invite people to this group." to null
            return
        }
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            messages.value = "Enter an email address." to null
            return
        }

        viewModelScope.launch {
            messages.value = null to null
            val user = authRepository.authState.first()
            val currentEmail = user?.email
            if (user == null || currentEmail == null) {
                messages.value = "Sign in to invite people to this group." to null
                return@launch
            }
            val currentDisplayName = user.displayName ?: currentEmail

            val currentGroup = group.value ?: groupExpenseRepository.getGroup(groupId)
            if (currentGroup == null) {
                messages.value = "Group not found." to null
                return@launch
            }

            var syncId = currentGroup.groupSyncId
            if (syncId == null) {
                val createResult = cloudRepository.createSharedGroup(currentGroup.name, user.uid, currentDisplayName, currentEmail)
                val newSyncId = createResult.getOrElse {
                    messages.value = "Could not enable sharing for this group. Please try again." to null
                    return@launch
                }
                groupExpenseRepository.markGroupShared(groupId, newSyncId, user.uid)
                syncId = newSyncId
            }

            // A one-shot check against the currently known sent-invitation list (already flowing
            // into uiState.sentInvitations) is sufficient here - the actual duplicate-prevention
            // guarantee is enforced server-side too (Firestore doesn't reject duplicates by
            // itself, but re-inviting the same still-pending email is harmless/idempotent from the
            // invitee's point of view since they'd just see two identical pending invitations;
            // this client-side check is what actually prevents that in the UI).
            if (InvitationPolicy.hasActivePendingInvitation(uiState.value.sentInvitations, syncId, trimmedEmail)) {
                messages.value = "An invitation to this email is already pending." to null
                return@launch
            }

            cloudRepository.sendInvitation(syncId, currentGroup.name, user.uid, currentEmail, trimmedEmail)
                .onSuccess { messages.value = null to "Invitation sent." }
                .onFailure { messages.value = "Could not send the invitation. Please try again." to null }
        }
    }

    fun dismissMessage() {
        messages.value = null to null
    }

    companion object {
        fun factory(
            groupExpenseRepository: GroupExpenseRepository,
            groupCloudRepository: GroupCloudRepository?,
            groupId: Long,
            authRepository: AuthRepository
        ) = viewModelFactory {
            initializer { GroupSharingViewModel(groupExpenseRepository, groupCloudRepository, groupId, authRepository) }
        }
    }
}

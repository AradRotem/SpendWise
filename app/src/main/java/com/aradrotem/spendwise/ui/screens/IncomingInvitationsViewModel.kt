package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.auth.AuthRepository
import com.aradrotem.spendwise.data.repository.GroupCloudRepository
import com.aradrotem.spendwise.data.sync.SharedGroupSyncEngine
import com.aradrotem.spendwise.domain.GroupInvitation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IncomingInvitationsUiState(
    val invitations: List<GroupInvitation> = emptyList(),
    val actionError: String? = null
)

// Powers the "pending invitations you received" section (Step 19 Part 5). Kept independent of
// GroupsListViewModel for the same isolation reason as GroupSharingViewModel - this whole feature
// is a no-op (empty list, disabled actions) when groupCloudRepository/the signed-in email are
// unavailable, which must never affect the existing local-groups list behavior. Depends on
// AuthRepository directly (reactively, via authState) rather than a static uid/email snapshot, so
// it behaves correctly across sign-in/sign-out without needing to be re-created.
class IncomingInvitationsViewModel(
    private val groupCloudRepository: GroupCloudRepository?,
    private val authRepository: AuthRepository,
    private val sharedGroupSyncEngine: SharedGroupSyncEngine
) : ViewModel() {

    private val actionError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<IncomingInvitationsUiState> = authRepository.authState
        .flatMapLatest { user ->
            val email = user?.email
            val invitationsFlow = if (groupCloudRepository != null && !email.isNullOrBlank()) {
                groupCloudRepository.observeIncomingInvitations(email)
            } else {
                flowOf(emptyList())
            }
            combine(invitationsFlow, actionError) { invitations, error -> IncomingInvitationsUiState(invitations, error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomingInvitationsUiState())

    // Accepting immediately triggers a real sync of that one group (see
    // SharedGroupSyncEngine.syncGroup) - this both creates the invitee's local group/member rows
    // AND pulls the owner's own member row plus any existing shared expenses in the same pass,
    // rather than leaving the group looking empty until the next background sync. If Firestore
    // accepted the invitation but the immediate sync fails (offline, transient error), acceptance
    // is still reported as successful - syncAll's regular trigger points will pick it up later.
    fun accept(invitation: GroupInvitation) {
        val cloudRepository = groupCloudRepository ?: return
        viewModelScope.launch {
            val user = authRepository.authState.first() ?: return@launch
            val displayName = user.displayName ?: user.email ?: "Member"
            val email = user.email ?: return@launch
            cloudRepository.acceptInvitation(invitation, user.uid, displayName, email)
                .onSuccess { sharedGroupSyncEngine.syncGroup(invitation.groupId, invitation.groupName) }
                .onFailure { actionError.value = "Could not accept the invitation. Please try again." }
        }
    }

    fun decline(invitation: GroupInvitation) {
        val cloudRepository = groupCloudRepository ?: return
        viewModelScope.launch {
            cloudRepository.declineInvitation(invitation)
                .onFailure { actionError.value = "Could not decline the invitation. Please try again." }
        }
    }

    fun dismissError() {
        actionError.value = null
    }

    companion object {
        fun factory(
            groupCloudRepository: GroupCloudRepository?,
            authRepository: AuthRepository,
            sharedGroupSyncEngine: SharedGroupSyncEngine
        ) = viewModelFactory {
            initializer { IncomingInvitationsViewModel(groupCloudRepository, authRepository, sharedGroupSyncEngine) }
        }
    }
}

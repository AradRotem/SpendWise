package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.auth.AuthRepository
import com.aradrotem.spendwise.data.repository.GroupCloudRepository
import com.aradrotem.spendwise.data.repository.IncomingInvitationsLoadError
import com.aradrotem.spendwise.data.sync.SharedGroupSyncEngine
import com.aradrotem.spendwise.domain.GroupInvitation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IncomingInvitationsUiState(
    val invitations: List<GroupInvitation> = emptyList(),
    // Non-null only when the live invitations listener itself failed (index/permission/network) -
    // deliberately separate from actionError (an accept/decline failure) so the UI can tell "we
    // don't actually know if there are pending invitations" apart from "there are genuinely none".
    val loadError: String? = null,
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

    // distinctUntilChanged is load-bearing, not cosmetic: Firebase's AuthStateListener can fire
    // more than once for the same signed-in user (see AuthSessionCoordinator's own dedup logic for
    // the same quirk), and flatMapLatest cancels + re-subscribes observeIncomingInvitations on
    // every upstream emission regardless of whether the user actually changed. Without this, a
    // redundant same-user re-fire tears down the live Firestore invitations listener and starts a
    // fresh one, which can make a still-pending invitation intermittently vanish from the list for
    // the moment it takes the new listener to re-populate - exactly the "sometimes not visible"
    // symptom this class exists to avoid.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<IncomingInvitationsUiState> = authRepository.authState
        .distinctUntilChanged()
        .flatMapLatest { user ->
            val email = user?.email
            // Pairs (invitations, loadError) rather than two separate combined flows: a listener
            // failure must replace the list wholesale with "we don't know" (a stale/empty list
            // paired with an error), never sit alongside a leftover invitations list from before
            // the failure.
            val invitationsFlow: Flow<Pair<List<GroupInvitation>, String?>> =
                if (groupCloudRepository != null && !email.isNullOrBlank()) {
                    groupCloudRepository.observeIncomingInvitations(email)
                        .map<List<GroupInvitation>, Pair<List<GroupInvitation>, String?>> { it to null }
                        .catch { emit(emptyList<GroupInvitation>() to it.toLoadErrorMessage()) }
                } else {
                    flowOf(emptyList<GroupInvitation>() to null)
                }
            combine(invitationsFlow, actionError) { (invitations, loadError), actionErr ->
                IncomingInvitationsUiState(invitations, loadError, actionErr)
            }
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

// Distinct message per failure class - "diagnose, don't just hide the error behind one generic
// string" (see IncomingInvitationsLoadError). None of these leak Firebase-specific vocabulary to
// the user, but they're distinguishable enough for support/QA to tell apart at a glance, and the
// full underlying exception (with the real FirebaseFirestoreException.Code) is always in Logcat
// too - see FirestoreGroupCloudRepository.observeIncomingInvitations.
private fun Throwable.toLoadErrorMessage(): String = when (this) {
    is IncomingInvitationsLoadError.IndexUnavailable ->
        "Pending invitations are temporarily unavailable while a one-time setup finishes. Please try again shortly."
    is IncomingInvitationsLoadError.PermissionDenied ->
        "Could not load pending invitations due to a permissions issue. Please sign out and sign back in, or contact support if this continues."
    else -> "Could not load pending invitations. Please try again."
}

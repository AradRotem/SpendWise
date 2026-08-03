package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.local.GroupMemberEntity
import com.aradrotem.spendwise.data.repository.GroupExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupFormViewModel(
    private val repository: GroupExpenseRepository,
    private val groupId: Long? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GroupFormUiState(groupId = groupId, isLoading = groupId != null)
    )

    // In edit mode, existingMembers is kept live from the repository so add/rename/delete below
    // (each an immediate DB write, mirroring CategoriesViewModel's inline category management)
    // reflect instantly without a manual refetch.
    val uiState: StateFlow<GroupFormUiState> = combine(
        _uiState,
        if (groupId != null) repository.observeMembers(groupId) else flowOf(emptyList())
    ) { state, members ->
        if (groupId != null) state.copy(existingMembers = members) else state
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupFormUiState(groupId = groupId, isLoading = groupId != null)
    )

    init {
        val id = groupId
        if (id != null) {
            viewModelScope.launch {
                val group = repository.getGroup(id)
                _uiState.update {
                    if (group != null) {
                        it.copy(isLoading = false, name = group.name)
                    } else {
                        it.copy(isLoading = false, loadError = "Group not found")
                    }
                }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, nameError = null, saveError = null) }
    }

    fun onNewMemberTextChange(text: String) {
        _uiState.update { it.copy(newMemberText = text, newMemberError = null) }
    }

    // Create mode: appends to the local pending list only (see save()). Edit mode: persists the
    // member immediately, surfacing the repository's duplicate-name check as newMemberError.
    fun onAddMember() {
        val state = _uiState.value
        val trimmed = state.newMemberText.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(newMemberError = "Member name is required") }
            return
        }

        if (state.isEditMode) {
            viewModelScope.launch {
                val result = repository.addMember(state.groupId!!, trimmed)
                result
                    .onSuccess { _uiState.update { it.copy(newMemberText = "", newMemberError = null) } }
                    .onFailure { error -> _uiState.update { it.copy(newMemberError = error.message) } }
            }
        } else {
            if (isDuplicateName(trimmed, state.pendingMemberNames)) {
                _uiState.update { it.copy(newMemberError = "A member with this name already exists in this group") }
                return
            }
            _uiState.update {
                it.copy(
                    pendingMemberNames = it.pendingMemberNames + trimmed,
                    newMemberText = "",
                    newMemberError = null
                )
            }
        }
    }

    fun onRemovePendingMember(name: String) {
        _uiState.update { it.copy(pendingMemberNames = it.pendingMemberNames - name) }
    }

    fun onRenameExistingMember(member: GroupMemberEntity, newName: String) {
        viewModelScope.launch {
            val result = repository.renameMember(member.id, newName)
            result.onFailure { error -> _uiState.update { it.copy(memberActionError = error.message) } }
        }
    }

    fun onDeleteExistingMember(member: GroupMemberEntity) {
        viewModelScope.launch {
            val result = repository.deleteMember(member)
            result.onFailure { error -> _uiState.update { it.copy(memberActionError = error.message) } }
        }
    }

    fun dismissMemberActionError() {
        _uiState.update { it.copy(memberActionError = null) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val trimmedName = state.name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(nameError = "Group name is required") }
            return
        }

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val result = if (state.isEditMode) {
                repository.renameGroup(state.groupId!!, trimmedName)
            } else {
                repository.createGroup(trimmedName, state.pendingMemberNames)
            }
            result
                .onSuccess { _uiState.update { it.copy(isSaving = false, isSaved = true) } }
                .onFailure { error -> _uiState.update { it.copy(isSaving = false, saveError = error.message) } }
        }
    }

    companion object {
        fun factory(repository: GroupExpenseRepository, groupId: Long? = null) = viewModelFactory {
            initializer { GroupFormViewModel(repository, groupId) }
        }
    }
}

// Case/whitespace-insensitive duplicate check against the not-yet-persisted pending list - kept
// top-level so it's directly unit-testable.
fun isDuplicateName(name: String, existingNames: List<String>): Boolean =
    existingNames.any { it.trim().equals(name.trim(), ignoreCase = true) }

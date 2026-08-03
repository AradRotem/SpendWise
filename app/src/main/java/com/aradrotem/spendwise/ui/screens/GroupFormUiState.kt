package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.GroupMemberEntity

data class GroupFormUiState(
    val groupId: Long? = null,
    val name: String = "",
    val nameError: String? = null,

    // Create mode only: members added but not yet persisted, saved together with the group.
    val pendingMemberNames: List<String> = emptyList(),

    // Edit mode only: the group's real, persisted members, loaded reactively so changes from
    // other actions (e.g. this same screen's add/rename/delete) show immediately.
    val existingMembers: List<GroupMemberEntity> = emptyList(),

    val newMemberText: String = "",
    val newMemberError: String? = null,
    // Surfaces a rename/delete failure (e.g. "cannot delete a referenced member") from the
    // repository's safe-delete/duplicate-name checks.
    val memberActionError: String? = null,

    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isSaved: Boolean = false,

    val isLoading: Boolean = false,
    val loadError: String? = null
) {
    val isEditMode: Boolean get() = groupId != null

    val memberCount: Int get() = if (isEditMode) existingMembers.size else pendingMemberNames.size
}

package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.data.local.GroupMemberEntity
import com.aradrotem.spendwise.data.local.GroupSplitMethod
import com.aradrotem.spendwise.domain.GroupExpenseSplit
import com.aradrotem.spendwise.ui.format.parseAmountToCents
import java.time.LocalDate

data class GroupExpenseFormUiState(
    val groupId: Long = 0L,
    val expenseId: Long? = null,
    val title: String = "",
    val amountText: String = "",
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val note: String = "",

    val members: List<GroupMemberEntity> = emptyList(),
    val payerMemberId: Long? = null,
    val participantMemberIds: Set<Long> = emptySet(),
    val splitMethod: GroupSplitMethod = GroupSplitMethod.EQUAL,
    // Raw text entered per participant for CUSTOM split, keyed by memberId. Entries for
    // non-participants are ignored (see customSharesCents).
    val customShareTexts: Map<Long, String> = emptyMap(),

    val titleError: String? = null,
    val amountError: String? = null,
    val payerError: String? = null,
    val participantsError: String? = null,
    val customShareErrors: Map<Long, String> = emptyMap(),

    val isSaving: Boolean = false,
    val saveError: String? = null,
    val isSaved: Boolean = false,

    val isLoading: Boolean = false,
    val loadError: String? = null
) {
    val isEditMode: Boolean get() = expenseId != null

    val amountCents: Long? get() = parseAmountToCents(amountText)

    // Live equal-split preview for the currently selected participants, in selection order.
    val equalSharesPreview: Map<Long, Long>?
        get() {
            val total = amountCents ?: return null
            if (participantMemberIds.isEmpty() || total <= 0L) return null
            return GroupExpenseSplit.equalShares(total, participantMemberIds.toList())
        }

    // Parsed custom share per selected participant; null means that participant's text is
    // currently blank or malformed.
    val customSharesCents: Map<Long, Long?>
        get() = participantMemberIds.associateWith { memberId -> parseAmountToCents(customShareTexts[memberId].orEmpty()) }

    // Positive: under-allocated. Negative: over-allocated. Zero: exactly balanced. Null while the
    // total amount or any participant's custom share isn't yet a valid number.
    val customSplitDifferenceCents: Long?
        get() {
            if (splitMethod != GroupSplitMethod.CUSTOM) return null
            val total = amountCents ?: return null
            val shares = customSharesCents.values
            if (shares.any { it == null }) return null
            return GroupExpenseSplit.customSplitDifferenceCents(total, shares.filterNotNull())
        }

    val isCustomSplitBalanced: Boolean
        get() = splitMethod != GroupSplitMethod.CUSTOM || customSplitDifferenceCents == 0L
}

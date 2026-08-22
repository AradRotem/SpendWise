package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class GroupSplitMethod {
    EQUAL,
    CUSTOM
}

// paidByMemberId has no cascade (default NO_ACTION): the repository's safe-delete rule already
// blocks deleting a member referenced by an expense, so this FK is a defensive backstop rather
// than the primary guard - see GroupExpenseRepository.deleteMember.
@Entity(
    tableName = "group_expenses",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroupMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["paidByMemberId"]
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["paidByMemberId"]),
        Index(value = ["cloudId"], unique = true)
    ]
)
data class GroupExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val title: String,
    val amountCents: Long,
    val dateEpochDay: Long,
    val paidByMemberId: Long,
    val splitMethod: GroupSplitMethod,
    val note: String = "",
    val createdAtEpochMillis: Long = System.currentTimeMillis(),

    // Step 19: null for an expense in a purely local group. For a shared group's expense, this is
    // the stable Firestore groups/{groupId}/expenses/{cloudId} document id - the identity
    // GroupExpenseReconciler dedups remote pulls against, since two devices' local Room ids for
    // "the same" shared expense are never the same number.
    val cloudId: String? = null,
    // The uid that originally created this expense - null for a local-only expense.
    val createdByUid: String? = null
)

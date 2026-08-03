package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// One row per participating member per expense; a member left out of an expense simply has no
// row here (see GroupExpenseRepository - excluded members are never inserted with a zero share).
@Entity(
    tableName = "group_expense_shares",
    foreignKeys = [
        ForeignKey(
            entity = GroupExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroupMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"]
        )
    ],
    indices = [
        Index(value = ["expenseId", "memberId"], unique = true),
        Index(value = ["memberId"])
    ]
)
data class GroupExpenseShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val memberId: Long,
    val shareAmountCents: Long
)

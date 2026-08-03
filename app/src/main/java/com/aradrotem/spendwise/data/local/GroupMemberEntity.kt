package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Cascades on group deletion (spec: deleting a group deletes its members too). Safe individual
// member deletion (only when not referenced by any expense/share) is enforced in the repository,
// not by this schema - see GroupExpenseRepository.deleteMember.
@Entity(
    tableName = "group_members",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

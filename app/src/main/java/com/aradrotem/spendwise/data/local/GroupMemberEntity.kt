package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Cascades on group deletion (spec: deleting a group deletes its members too). Safe individual
// member deletion (only when not referenced by any expense/share) is enforced in the repository,
// not by this schema - see GroupExpenseRepository.deleteMember.
// role/memberUid are null for a plain local (non-shared) member - unchanged pre-Step-19 shape.
enum class GroupRole { OWNER, MEMBER }

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
    val createdAtEpochMillis: Long = System.currentTimeMillis(),

    // Step 19: set once this member row corresponds to a real authenticated SpendWise account
    // (either the group's owner, or someone who accepted an invitation) - null for a
    // local-representation-only member, exactly as every pre-Step-19 group member already was.
    val memberUid: String? = null,
    val role: GroupRole? = null
)

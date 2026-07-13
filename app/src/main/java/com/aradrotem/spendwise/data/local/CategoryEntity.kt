package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["normalizedName", "type"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val type: TransactionType,
    val isBuiltIn: Boolean = false
)

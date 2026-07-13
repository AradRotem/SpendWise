package com.aradrotem.spendwise.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountInCents: Long,
    val type: TransactionType,
    val category: String,
    val timestamp: Long,
    val note: String = ""
)

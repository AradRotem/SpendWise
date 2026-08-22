package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType

data class TopPayeePoint(val name: String, val totalCents: Long, val transactionCount: Int)

object TopPayeeCalculator {

    // The app has no dedicated merchant/payee field, so the free-text note is used as an
    // approximate payee name - the only field a user would plausibly fill in with a business name.
    // Blank notes are excluded entirely (never shown as an unnamed "payee"), and comparison is
    // case/whitespace-insensitive so "Netflix" and "netflix " aggregate together, while the
    // originally-cased, trimmed form of the first occurrence is kept for display.
    fun calculate(transactions: List<TransactionEntity>, limit: Int = 5): List<TopPayeePoint> {
        val grouped = transactions
            .asSequence()
            .filter { it.type == TransactionType.EXPENSE && it.note.isNotBlank() }
            .groupBy { it.note.trim().lowercase() }
            .map { (_, txns) ->
                TopPayeePoint(
                    name = txns.first().note.trim(),
                    totalCents = txns.sumOf { it.amountInCents },
                    transactionCount = txns.size
                )
            }
        return grouped.sortedByDescending { it.totalCents }.take(limit)
    }
}

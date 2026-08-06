package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType

// One chart segment / breakdown row: a single expense category's total for the analyzed period.
data class CategorySpendingPoint(
    val categoryName: String,
    val amountCents: Long,
    val transactionCount: Int,
    // 0..100, safe against division by zero. A visual/display value only - amountCents remains
    // the exact source of truth, so rounding this never changes any stored or reported total.
    val percentage: Float
)

object CategoryDistributionCalculator {

    // Expense-only aggregation by category. Deterministic ordering: highest amount first, ties
    // broken by category name so repeated calls on the same data always produce the same order
    // (important for both chart segment order and tests). Category totals always sum exactly to
    // the period's total expense amount, since every point's amountCents comes directly from the
    // same transaction list with no rounding.
    fun calculate(transactions: List<TransactionEntity>): List<CategorySpendingPoint> {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
        val totalCents = expenses.sumOf { it.amountInCents }

        return expenses
            .groupBy { it.category }
            .map { (category, categoryTransactions) ->
                val amountCents = categoryTransactions.sumOf { it.amountInCents }
                CategorySpendingPoint(
                    categoryName = category,
                    amountCents = amountCents,
                    transactionCount = categoryTransactions.size,
                    percentage = if (totalCents > 0L) (amountCents.toFloat() / totalCents.toFloat()) * 100f else 0f
                )
            }
            .sortedWith(compareByDescending<CategorySpendingPoint> { it.amountCents }.thenBy { it.categoryName })
    }
}

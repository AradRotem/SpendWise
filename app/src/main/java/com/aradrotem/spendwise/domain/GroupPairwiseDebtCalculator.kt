package com.aradrotem.spendwise.domain

// One expense's payer together with the stored share of every participant (including the payer,
// if the payer is also a participant) - the minimal shape needed to derive who-owes-whom directly
// from the recorded expenses, independent of split method (EQUAL and CUSTOM both just contribute
// their already-stored GroupShareForBalance.shareAmountCents).
data class GroupExpensePayerShares(
    val payerMemberId: Long,
    val shares: List<GroupShareForBalance>
)

// The original, expense-level debt between two members, before any global settlement
// simplification redirects payments through other members. Deliberately a separate type from
// GroupSettlement (domain/GroupSettlementCalculator.kt): the two represent different concepts -
// this one traces directly back to specific expenses, the other only preserves final balances.
data class GroupPairwiseDebt(
    val debtorMemberId: Long,
    val creditorMemberId: Long,
    val amountCents: Long
)

object GroupPairwiseDebtCalculator {

    // For every expense, each participant (other than the payer) owes their stored share to the
    // payer. Debts accumulate across all expenses, then opposite-direction debts between the same
    // two members are offset - only the net positive direction is emitted, and only when it's
    // non-zero. Money is never redirected through a third member here (that's what
    // GroupSettlementCalculator's simplification is for); this is purely the sum of direct
    // per-expense obligations.
    fun calculatePairwiseDebts(expenses: List<GroupExpensePayerShares>): List<GroupPairwiseDebt> {
        val directed = mutableMapOf<Pair<Long, Long>, Long>()

        for (expense in expenses) {
            for (share in expense.shares) {
                if (share.memberId == expense.payerMemberId) continue // no self-debt
                if (share.shareAmountCents <= 0L) continue // never emit a zero/negative-value debt
                val key = share.memberId to expense.payerMemberId
                directed[key] = Math.addExact(directed[key] ?: 0L, share.shareAmountCents)
            }
        }

        val processedPairs = mutableSetOf<Pair<Long, Long>>()
        val results = mutableListOf<GroupPairwiseDebt>()
        for (key in directed.keys) {
            val (debtor, creditor) = key
            val pairKey = if (debtor <= creditor) debtor to creditor else creditor to debtor
            if (!processedPairs.add(pairKey)) continue // this unordered pair was already netted

            val forward = directed[debtor to creditor] ?: 0L
            val backward = directed[creditor to debtor] ?: 0L
            val net = forward - backward
            when {
                net > 0L -> results += GroupPairwiseDebt(debtor, creditor, net)
                net < 0L -> results += GroupPairwiseDebt(creditor, debtor, -net)
                // net == 0L: opposite debts cancel exactly, emit nothing.
            }
        }

        // Deterministic regardless of map iteration order or input order.
        return results.sortedWith(compareBy({ it.debtorMemberId }, { it.creditorMemberId }))
    }
}

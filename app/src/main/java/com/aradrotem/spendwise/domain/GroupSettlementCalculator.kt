package com.aradrotem.spendwise.domain

data class GroupSettlement(
    val fromMemberId: Long,
    val toMemberId: Long,
    val amountCents: Long
)

object GroupSettlementCalculator {

    // Simple greedy debtor-to-creditor algorithm: repeatedly matches the largest debtor against
    // the largest creditor for the amount they can cover, until everyone is settled. Sorted by
    // amount descending, then by memberId ascending as a tie-break, so the output is deterministic
    // for a given set of balances - it is not guaranteed to produce the minimum possible number of
    // transfers, which the spec explicitly does not require.
    fun simplify(balances: List<GroupMemberBalance>): List<GroupSettlement> {
        val creditors = balances.filter { it.netBalanceCents > 0 }
            .map { MutableAmount(it.memberId, it.netBalanceCents) }
            .sortedWith(compareByDescending<MutableAmount> { it.amountCents }.thenBy { it.memberId })
            .toMutableList()
        val debtors = balances.filter { it.netBalanceCents < 0 }
            .map { MutableAmount(it.memberId, -it.netBalanceCents) }
            .sortedWith(compareByDescending<MutableAmount> { it.amountCents }.thenBy { it.memberId })
            .toMutableList()

        val settlements = mutableListOf<GroupSettlement>()
        var creditorIndex = 0
        var debtorIndex = 0
        while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
            val debtor = debtors[debtorIndex]
            val creditor = creditors[creditorIndex]
            val amount = minOf(debtor.amountCents, creditor.amountCents)
            if (amount > 0) {
                settlements += GroupSettlement(debtor.memberId, creditor.memberId, amount)
                debtor.amountCents -= amount
                creditor.amountCents -= amount
            }
            if (debtor.amountCents == 0L) debtorIndex++
            if (creditor.amountCents == 0L) creditorIndex++
        }
        return settlements
    }

    private data class MutableAmount(val memberId: Long, var amountCents: Long)
}

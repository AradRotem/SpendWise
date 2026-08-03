package com.aradrotem.spendwise.domain

// Minimal shape of a group expense needed for balance math - decoupled from GroupExpenseEntity so
// this stays plain-JVM testable.
data class GroupExpenseForBalance(val paidByMemberId: Long, val amountCents: Long)

// Minimal shape of a share row needed for balance math - decoupled from GroupExpenseShareEntity.
data class GroupShareForBalance(val memberId: Long, val shareAmountCents: Long)

data class GroupMemberBalance(
    val memberId: Long,
    val paidCents: Long,
    val owedCents: Long
) {
    // Positive: this member should receive money. Negative: this member owes money. Zero: settled.
    val netBalanceCents: Long get() = paidCents - owedCents
}

object GroupBalanceCalculator {

    // paidCents = sum of expenses this member paid for; owedCents = sum of shares assigned to
    // this member. Every member in memberIds appears in the result even with zero activity, so
    // the group-details screen can list every member's balance, not just active ones.
    fun computeBalances(
        memberIds: List<Long>,
        expenses: List<GroupExpenseForBalance>,
        shares: List<GroupShareForBalance>
    ): List<GroupMemberBalance> {
        val paidByMember = expenses.groupBy { it.paidByMemberId }.mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
        val owedByMember = shares.groupBy { it.memberId }.mapValues { (_, rows) -> rows.sumOf { it.shareAmountCents } }

        return memberIds.map { memberId ->
            GroupMemberBalance(
                memberId = memberId,
                paidCents = paidByMember[memberId] ?: 0L,
                owedCents = owedByMember[memberId] ?: 0L
            )
        }
    }
}

package com.aradrotem.spendwise.domain

// Pure, Compose/Room-independent generator of a short human-readable explanation of why the
// simplified settlement transfers (GroupSettlement) differ from the original per-expense debts
// (GroupPairwiseDebt). Presentation logic only - nothing here is persisted to Room.
object GroupSettlementExplanation {

    // Returns null when there is nothing to explain: no simplified transfers at all (the group is
    // settled - the screen shows its own "everyone is settled" message instead), or the
    // simplified transfers are identical to the original debts (no redirection actually happened).
    fun explain(
        originalDebts: List<GroupPairwiseDebt>,
        simplifiedTransfers: List<GroupSettlement>,
        memberNameById: Map<Long, String>,
        formatAmountCents: (Long) -> String
    ): String? {
        if (simplifiedTransfers.isEmpty()) return null

        val originalEdges = originalDebts.map { Triple(it.debtorMemberId, it.creditorMemberId, it.amountCents) }.toSet()
        val simplifiedEdges = simplifiedTransfers.map { Triple(it.fromMemberId, it.toMemberId, it.amountCents) }.toSet()
        if (originalEdges == simplifiedEdges) return null

        val originalByPair = originalDebts.associateBy { it.debtorMemberId to it.creditorMemberId }
        val simplifiedByPair = simplifiedTransfers.associateBy { it.fromMemberId to it.toMemberId }

        // The "simple redirect" case this can describe concretely: exactly one original A->B debt
        // has disappeared from the simplified transfers, and that missing amount reappears as an
        // equal increase on some A->C transfer while B's own debt to that same C decreases (or
        // disappears) by exactly that amount - i.e. A now pays C directly instead of paying B who
        // would have forwarded it to C.
        val missingEdges = originalDebts.filter { debt -> simplifiedByPair[debt.debtorMemberId to debt.creditorMemberId] == null }
        if (missingEdges.size == 1) {
            val missing = missingEdges.single()
            val a = missing.debtorMemberId
            val b = missing.creditorMemberId
            val redirectedAmount = missing.amountCents

            val bsCreditorEdges = originalDebts.filter { it.debtorMemberId == b }
            for (bEdge in bsCreditorEdges) {
                val c = bEdge.creditorMemberId
                val originalBC = bEdge.amountCents
                val simplifiedBC = simplifiedByPair[b to c]?.amountCents ?: 0L
                val originalAC = originalByPair[a to c]?.amountCents ?: 0L
                val simplifiedAC = simplifiedByPair[a to c]?.amountCents ?: 0L

                val bcReducedByRedirect = originalBC - simplifiedBC == redirectedAmount
                val acIncreasedByRedirect = simplifiedAC - originalAC == redirectedAmount
                if (bcReducedByRedirect && acIncreasedByRedirect) {
                    val nameA = memberNameById[a] ?: "Unknown"
                    val nameB = memberNameById[b] ?: "Unknown"
                    val nameC = memberNameById[c] ?: "Unknown"
                    val bToCClause = if (simplifiedBC == 0L) {
                        "$nameB no longer needs to pay $nameC directly"
                    } else {
                        "$nameB's payment to $nameC is reduced from ${formatAmountCents(originalBC)} to ${formatAmountCents(simplifiedBC)}"
                    }
                    return "Instead of $nameA paying ${formatAmountCents(redirectedAmount)} to $nameB and $nameB forwarding " +
                        "that amount to $nameC, $nameA pays that ${formatAmountCents(redirectedAmount)} directly to $nameC. " +
                        "Therefore $nameA pays $nameC ${formatAmountCents(simplifiedAC)}, while $bToCClause."
                }
            }
        }

        return "Some debts were redirected to reduce the number of separate payments while preserving every member's final balance."
    }
}

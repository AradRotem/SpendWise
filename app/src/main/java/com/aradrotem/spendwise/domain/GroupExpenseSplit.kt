package com.aradrotem.spendwise.domain

// Pure, Room/Compose-independent split math shared by the Add/Edit Group Expense screen and its
// ViewModel, kept testable without Android.
object GroupExpenseSplit {

    // Splits totalCents evenly across participantIds, working entirely in cents. Any remainder
    // (totalCents not evenly divisible by participant count) is assigned one cent at a time to
    // the first selected participants, in the given order, until it is exhausted - a deterministic
    // rule so the same input always produces the same shares. The returned shares always sum
    // exactly to totalCents.
    fun equalShares(totalCents: Long, participantIds: List<Long>): Map<Long, Long> {
        require(participantIds.isNotEmpty()) { "participantIds must not be empty" }
        val count = participantIds.size
        val base = totalCents / count
        var remainder = totalCents - base * count
        return participantIds.associateWith { id ->
            if (remainder > 0) {
                remainder--
                base + 1
            } else {
                base
            }
        }
    }

    // Positive: under-allocated (more money needs to be assigned). Negative: over-allocated.
    // Zero: the custom shares exactly cover the expense, which is the only valid state to save.
    fun customSplitDifferenceCents(totalCents: Long, shareCents: Collection<Long>): Long =
        totalCents - shareCents.sum()
}

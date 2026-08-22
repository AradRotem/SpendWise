package com.aradrotem.spendwise.domain

data class MonthEndProjection(
    val elapsedDays: Int,
    val totalDaysInMonth: Int,
    val spentSoFarCents: Long,
    val projectedTotalCents: Long,
    // Null when there is no monthly budget total to compare against.
    val budgetCents: Long?,
    // True only when a budget exists and the projection exceeds it.
    val projectedToExceedBudget: Boolean,
    // How much the projection would exceed the budget by (only meaningful when
    // projectedToExceedBudget is true).
    val projectedOverageCents: Long
) {
    // Fewer than 3 elapsed days makes the daily-pace extrapolation too noisy to be a useful
    // projection (e.g. one large day-1 purchase would otherwise imply a wildly inflated month).
    val isReliable: Boolean get() = elapsedDays >= 3
}

object MonthEndProjectionCalculator {

    // budgetCents should be the sum of all category monthly limits (or null if the user has no
    // budgets configured) - callers decide what "budget" means for their screen.
    fun calculate(elapsedDays: Int, totalDaysInMonth: Int, spentSoFarCents: Long, budgetCents: Long?): MonthEndProjection {
        require(totalDaysInMonth > 0) { "totalDaysInMonth must be positive" }
        val clampedElapsed = elapsedDays.coerceIn(1, totalDaysInMonth)
        val dailyPace = spentSoFarCents.toDouble() / clampedElapsed.toDouble()
        val projectedTotal = (dailyPace * totalDaysInMonth).toLong()

        val exceedsBudget = budgetCents != null && projectedTotal > budgetCents
        val overage = if (exceedsBudget) projectedTotal - budgetCents!! else 0L

        return MonthEndProjection(
            elapsedDays = clampedElapsed,
            totalDaysInMonth = totalDaysInMonth,
            spentSoFarCents = spentSoFarCents,
            projectedTotalCents = projectedTotal,
            budgetCents = budgetCents,
            projectedToExceedBudget = exceedsBudget,
            projectedOverageCents = overage
        )
    }
}

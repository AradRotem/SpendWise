package com.aradrotem.spendwise.domain

enum class BudgetStatus {
    UNDER_BUDGET,
    NEAR_BUDGET,
    OVER_BUDGET
}

data class BudgetActualPoint(
    val categoryName: String,
    val budgetCents: Long,
    val actualCents: Long,
    // budgetCents - actualCents: positive means money left, negative means over budget by that
    // amount (callers display abs(remainingCents) with "Remaining"/"Over budget by" wording).
    val remainingCents: Long,
    // 0f when budgetCents <= 0 (division-by-zero guard); otherwise actual/budget, uncapped so an
    // over-budget category still reports its true usage (e.g. 1.4f = 140%).
    val usageFraction: Float,
    val status: BudgetStatus
)

object BudgetActualCalculator {

    // "Near budget" threshold: at or above 90% of the budget but not yet over it. Named constant
    // rather than an inline magic number, per the spec's explicit requirement.
    const val NEAR_BUDGET_THRESHOLD = 0.9f

    // Takes plain primitives (not BudgetProgress/BudgetEntity) so this stays independent of the
    // ui.screens/Room layers - callers (VisualAnalyticsViewModel) reuse the existing
    // computeBudgetProgress (BudgetsViewModel.kt) for the actual spend lookup, then map its result
    // into this pure status classification rather than recomputing spend from scratch.
    fun calculate(categoryName: String, budgetCents: Long, actualCents: Long): BudgetActualPoint {
        val usageFraction = if (budgetCents > 0L) actualCents.toFloat() / budgetCents.toFloat() else 0f
        val status = when {
            actualCents > budgetCents -> BudgetStatus.OVER_BUDGET
            usageFraction >= NEAR_BUDGET_THRESHOLD -> BudgetStatus.NEAR_BUDGET
            else -> BudgetStatus.UNDER_BUDGET
        }
        return BudgetActualPoint(
            categoryName = categoryName,
            budgetCents = budgetCents,
            actualCents = actualCents,
            remainingCents = budgetCents - actualCents,
            usageFraction = usageFraction,
            status = status
        )
    }
}

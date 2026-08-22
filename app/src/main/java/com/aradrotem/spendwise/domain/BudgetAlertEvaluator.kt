package com.aradrotem.spendwise.domain

enum class BudgetThresholdType { NEAR_BUDGET, OVER_BUDGET }

data class BudgetAlertEvent(
    val categoryName: String,
    val thresholdType: BudgetThresholdType,
    val actualCents: Long,
    val budgetCents: Long
)

// Pure threshold-crossing detection for budget notifications, kept separate from
// BudgetActualCalculator (which just classifies a single point) since this layer owns the
// dedup rule: a (categoryName, thresholdType) pair already present in alreadyNotified is skipped,
// so the same alert never fires twice for the same category in the same month. Callers are
// responsible for scoping alreadyNotified to the current calendar month (e.g. by only querying
// Room for rows tagged with the current yearMonth) - a new month naturally has an empty set for
// that month, which is what makes the monthly reset work without any explicit "clear" step here.
object BudgetAlertEvaluator {

    // 85% per Step 19's spec - intentionally lower than BudgetActualCalculator.NEAR_BUDGET_THRESHOLD
    // (0.9f, used for the Reports & Analytics "near budget" chart classification) since a
    // notification should fire earlier than the chart's visual warning color.
    const val ALERT_THRESHOLD = 0.85f

    fun evaluate(
        budgetActualPoints: List<BudgetActualPoint>,
        alreadyNotified: Set<Pair<String, BudgetThresholdType>>
    ): List<BudgetAlertEvent> = budgetActualPoints.mapNotNull { point ->
        val thresholdType = when {
            point.actualCents > point.budgetCents -> BudgetThresholdType.OVER_BUDGET
            point.usageFraction >= ALERT_THRESHOLD -> BudgetThresholdType.NEAR_BUDGET
            else -> null
        } ?: return@mapNotNull null

        if ((point.categoryName to thresholdType) in alreadyNotified) return@mapNotNull null

        BudgetAlertEvent(point.categoryName, thresholdType, point.actualCents, point.budgetCents)
    }
}

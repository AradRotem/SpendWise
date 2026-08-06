package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.util.formatCategoryDisplayName

data class FinancialInsight(val text: String, val priority: Int)

// Deterministic, rule-based textual insights - no AI/external service, purely derived from
// already-computed analytics data. Every rule below only asserts something the data can actually
// support (a comparison, a total, a threshold crossing) - never a cause, a recommendation, or
// judgment of the user.
object FinancialInsightGenerator {

    const val MAX_INSIGHTS = 5

    // Lower number = shown first. Budget problems are surfaced before general trend commentary
    // since they're the most actionable; ties within a rule (e.g. several exceeded budgets) keep
    // the input list's own order, which callers already sort deterministically (category name for
    // budgets, chronological for months) - Kotlin's sortedBy is stable, so this ordering is
    // preserved.
    private const val PRIORITY_BUDGET_EXCEEDED = 1
    private const val PRIORITY_HIGHEST_CATEGORY = 2
    private const val PRIORITY_MONTH_OVER_MONTH = 3
    private const val PRIORITY_CATEGORY_TREND = 4
    private const val PRIORITY_INCOME_VS_EXPENSE = 5
    private const val PRIORITY_INSUFFICIENT_DATA = 100

    fun generate(
        categoryPoints: List<CategorySpendingPoint>,
        monthlySpendingPoints: List<MonthlySpendingPoint>,
        monthlyIncomeExpensePoints: List<MonthlyIncomeExpensePoint>,
        budgetActualPoints: List<BudgetActualPoint>,
        categoryTrendPoints: List<CategoryTrendPoint>,
        selectedCategoryName: String?,
        formatAmountCents: (Long) -> String,
        formatPercent: (Float) -> String
    ): List<FinancialInsight> {
        val insights = mutableListOf<FinancialInsight>()

        budgetActualPoints.filter { it.status == BudgetStatus.OVER_BUDGET }.forEach { budget ->
            val overCents = budget.actualCents - budget.budgetCents
            insights += FinancialInsight(
                "You exceeded the ${formatCategoryDisplayName(budget.categoryName)} budget by ${formatAmountCents(overCents)}.",
                PRIORITY_BUDGET_EXCEEDED
            )
        }

        categoryPoints.filter { it.amountCents > 0L }.maxByOrNull { it.amountCents }?.let { top ->
            insights += FinancialInsight(
                "${formatCategoryDisplayName(top.categoryName)} was your highest spending category.",
                PRIORITY_HIGHEST_CATEGORY
            )
        }

        monthlySpendingPoints.lastOrNull()?.let { last ->
            val percent = last.changePercent
            when {
                percent != null && percent > 0f ->
                    insights += FinancialInsight(
                        "Your expenses increased by ${formatPercent(percent)}% compared with the previous month.",
                        PRIORITY_MONTH_OVER_MONTH
                    )
                percent != null && percent < 0f ->
                    insights += FinancialInsight(
                        "Your expenses decreased by ${formatPercent(-percent)}% compared with the previous month.",
                        PRIORITY_MONTH_OVER_MONTH
                    )
                // previous month was zero, current is positive: a from-zero start, not a percentage.
                last.hasPreviousData && percent == null && last.expenseCents > 0L ->
                    insights += FinancialInsight("Spending started this month.", PRIORITY_MONTH_OVER_MONTH)
                // no change at all - not worth a separate insight line.
            }
        }

        if (selectedCategoryName != null && categoryTrendPoints.size >= 3) {
            val lastThree = categoryTrendPoints.takeLast(3)
            if (lastThree[0].amountCents > lastThree[1].amountCents && lastThree[1].amountCents > lastThree[2].amountCents) {
                insights += FinancialInsight(
                    "${formatCategoryDisplayName(selectedCategoryName)} spending decreased for two consecutive months.",
                    PRIORITY_CATEGORY_TREND
                )
            } else if (lastThree[0].amountCents < lastThree[1].amountCents && lastThree[1].amountCents < lastThree[2].amountCents) {
                insights += FinancialInsight(
                    "${formatCategoryDisplayName(selectedCategoryName)} spending increased for two consecutive months.",
                    PRIORITY_CATEGORY_TREND
                )
            }
        }

        if (monthlyIncomeExpensePoints.size >= 2) {
            val monthsWithIncomeGreater = monthlyIncomeExpensePoints.count { it.incomeCents > it.expenseCents }
            if (monthsWithIncomeGreater > 0) {
                insights += FinancialInsight(
                    "Your income was higher than your expenses in $monthsWithIncomeGreater of the last " +
                        "${monthlyIncomeExpensePoints.size} months.",
                    PRIORITY_INCOME_VS_EXPENSE
                )
            }
        }

        if (insights.isEmpty()) {
            return listOf(
                FinancialInsight("Add more transactions over time to receive meaningful insights.", PRIORITY_INSUFFICIENT_DATA)
            )
        }

        return insights.sortedBy { it.priority }.take(MAX_INSIGHTS)
    }
}

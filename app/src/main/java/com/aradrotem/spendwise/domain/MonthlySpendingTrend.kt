package com.aradrotem.spendwise.domain

import java.time.YearMonth

data class MonthlySpendingPoint(
    val yearMonth: YearMonth,
    val expenseCents: Long,
    // Null only for the first month in the analyzed range - there is no earlier month in this
    // calculation to compare against (see hasPreviousData).
    val changeCents: Long?,
    // Null when there is no previous month, OR the previous month's spending was zero (a percent
    // change from zero is mathematically undefined/misleading - callers should show wording like
    // "No previous spending for comparison" instead of an infinite or invalid percentage).
    val changePercent: Float?,
    val hasPreviousData: Boolean
)

object MonthlySpendingTrendCalculator {

    // One point per month, each compared against the immediately preceding point in the same
    // list (not calendar-adjacent necessarily, though in practice this list is always built from
    // consecutive months - see monthsForAnalyticsRange). Deterministic: purely a function of the
    // input list's order and values.
    fun calculate(monthlyPoints: List<MonthlyIncomeExpensePoint>): List<MonthlySpendingPoint> =
        monthlyPoints.mapIndexed { index, point ->
            if (index == 0) {
                MonthlySpendingPoint(point.yearMonth, point.expenseCents, null, null, hasPreviousData = false)
            } else {
                val previousExpense = monthlyPoints[index - 1].expenseCents
                val change = point.expenseCents - previousExpense
                val changePercent = if (previousExpense > 0L) (change.toFloat() / previousExpense.toFloat()) * 100f else null
                MonthlySpendingPoint(point.yearMonth, point.expenseCents, change, changePercent, hasPreviousData = true)
            }
        }

    // Ties broken by the earliest month, so the result is deterministic.
    fun highestSpendingMonth(points: List<MonthlySpendingPoint>): MonthlySpendingPoint? =
        points.maxWithOrNull(compareBy<MonthlySpendingPoint> { it.expenseCents }.thenByDescending { it.yearMonth })

    // Lowest among months that actually had spending (>0) - a month with zero spending isn't a
    // meaningful "lowest spending month", it's an empty one.
    fun lowestSpendingMonth(points: List<MonthlySpendingPoint>): MonthlySpendingPoint? =
        points.filter { it.expenseCents > 0L }
            .minWithOrNull(compareBy<MonthlySpendingPoint> { it.expenseCents }.thenBy { it.yearMonth })
}

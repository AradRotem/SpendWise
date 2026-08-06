package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.util.monthRange
import java.time.YearMonth
import java.time.ZoneId

data class CategoryTrendPoint(val yearMonth: YearMonth, val amountCents: Long)

object CategoryTrendCalculator {

    // One point per requested month for a single expense category - months where the category had
    // no spending (or the category doesn't appear in the period at all) simply show zero, rather
    // than being omitted, so the chart's x-axis stays consistent across categories.
    fun calculate(
        categoryName: String,
        months: List<YearMonth>,
        transactions: List<TransactionEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<CategoryTrendPoint> = months.map { month ->
        val range = monthRange(month, zoneId)
        val amountCents = transactions
            .filter {
                it.type == TransactionType.EXPENSE && it.category == categoryName &&
                    it.timestamp >= range.startMillis && it.timestamp < range.endExclusiveMillis
            }
            .sumOf { it.amountInCents }
        CategoryTrendPoint(month, amountCents)
    }

    fun total(points: List<CategoryTrendPoint>): Long = points.sumOf { it.amountCents }

    // Integer-cents average (truncating), consistent with the rest of the app never using
    // floating point for money.
    fun average(points: List<CategoryTrendPoint>): Long = if (points.isEmpty()) 0L else total(points) / points.size

    fun highest(points: List<CategoryTrendPoint>): CategoryTrendPoint? =
        points.maxWithOrNull(compareBy<CategoryTrendPoint> { it.amountCents }.thenByDescending { it.yearMonth })

    // Change from the immediately preceding point in the list; null when there are fewer than two
    // points (nothing to compare against).
    fun changeFromPrevious(points: List<CategoryTrendPoint>): Long? {
        if (points.size < 2) return null
        return points.last().amountCents - points[points.size - 2].amountCents
    }
}

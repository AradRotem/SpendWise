package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

// One point per calendar day of the selected month: that day's own expense total and the running
// (cumulative) total through that day. Always covers every day of the month up to the last day
// with data (or the current day, for the current month) so a line/bar chart's x-axis is stable.
data class CumulativeSpendingPoint(val day: Int, val dayExpenseCents: Long, val cumulativeExpenseCents: Long)

object CumulativeSpendingCalculator {

    // dayLimit caps how many days are produced - the current day-of-month for the current month
    // (no point charting unreached future days as zero), or the full month length otherwise.
    fun calculate(
        month: YearMonth,
        transactions: List<TransactionEntity>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        dayLimit: Int = defaultDayLimit(month, zoneId)
    ): List<CumulativeSpendingPoint> {
        val dailyTotals = LongArray(dayLimit)
        transactions
            .asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .forEach { transaction ->
                val date = Instant.ofEpochMilli(transaction.timestamp).atZone(zoneId).toLocalDate()
                if (date.year == month.year && date.monthValue == month.monthValue) {
                    val dayIndex = date.dayOfMonth - 1
                    if (dayIndex in 0 until dayLimit) {
                        dailyTotals[dayIndex] += transaction.amountInCents
                    }
                }
            }

        var running = 0L
        return (0 until dayLimit).map { index ->
            running += dailyTotals[index]
            CumulativeSpendingPoint(day = index + 1, dayExpenseCents = dailyTotals[index], cumulativeExpenseCents = running)
        }
    }

    private fun defaultDayLimit(month: YearMonth, zoneId: ZoneId): Int {
        val today = YearMonth.now(zoneId)
        return if (month == today) java.time.LocalDate.now(zoneId).dayOfMonth else month.lengthOfMonth()
    }
}

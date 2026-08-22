package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

data class WeekdaySpendingPoint(val dayOfWeek: DayOfWeek, val expenseCents: Long)

object WeekdaySpendingCalculator {

    // Always returns all 7 days, Sunday-first (matches the app's existing week-start convention
    // elsewhere), with zero for days that had no expenses - a stable x-axis regardless of data.
    fun calculate(transactions: List<TransactionEntity>, zoneId: ZoneId = ZoneId.systemDefault()): List<WeekdaySpendingPoint> {
        val totals = transactions
            .asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zoneId).dayOfWeek }
            .mapValues { (_, txns) -> txns.sumOf { it.amountInCents } }

        val sundayFirstOrder = listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        )
        return sundayFirstOrder.map { day -> WeekdaySpendingPoint(day, totals[day] ?: 0L) }
    }

    // Ties broken by earliest day in the Sunday-first order, so the result is deterministic.
    fun highestSpendingDay(points: List<WeekdaySpendingPoint>): WeekdaySpendingPoint? =
        points.filter { it.expenseCents > 0L }.maxByOrNull { it.expenseCents }
}

package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.data.local.TransactionEntity
import com.aradrotem.spendwise.data.local.TransactionType
import com.aradrotem.spendwise.util.monthRange
import java.time.YearMonth
import java.time.ZoneId

data class MonthlyIncomeExpensePoint(
    val yearMonth: YearMonth,
    val incomeCents: Long,
    val expenseCents: Long
)

object MonthlyIncomeExpenseCalculator {

    // One point per requested month, in the given (chronological) order - including months with
    // no transactions at all, which simply get zero for both totals. `transactions` is expected to
    // already cover (at least) the full span of `months`; filtering here is purely by timestamp,
    // using the same half-open [start, endExclusive) convention as every other date-range query in
    // the app, so a transaction is never counted in two adjacent months.
    fun calculate(
        months: List<YearMonth>,
        transactions: List<TransactionEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<MonthlyIncomeExpensePoint> = months.map { month ->
        val range = monthRange(month, zoneId)
        val inMonth = transactions.filter { it.timestamp >= range.startMillis && it.timestamp < range.endExclusiveMillis }
        MonthlyIncomeExpensePoint(
            yearMonth = month,
            incomeCents = inMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amountInCents },
            expenseCents = inMonth.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountInCents }
        )
    }
}

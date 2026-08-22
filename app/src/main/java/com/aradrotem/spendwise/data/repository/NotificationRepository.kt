package com.aradrotem.spendwise.data.repository

import com.aradrotem.spendwise.data.local.NotificationStateDao
import com.aradrotem.spendwise.data.local.NotifiedBudgetThresholdEntity
import com.aradrotem.spendwise.data.local.NotifiedRecurringReminderEntity
import com.aradrotem.spendwise.domain.BudgetThresholdType

class NotificationRepository(private val notificationStateDao: NotificationStateDao) {

    // Scoped to one calendar month, so a new month always starts with an empty set - the monthly
    // reset described in Step 19's spec falls out of this scoping automatically, with no explicit
    // "clear last month's rows" step required.
    suspend fun getNotifiedBudgetThresholdKeys(yearMonth: String): Set<Pair<String, BudgetThresholdType>> =
        notificationStateDao.getNotifiedBudgetThresholdKeys(yearMonth)
            .map { it.categoryName to BudgetThresholdType.valueOf(it.thresholdType) }
            .toSet()

    suspend fun recordBudgetThresholdNotified(categoryName: String, yearMonth: String, thresholdType: BudgetThresholdType, notifiedAtEpochMillis: Long) {
        notificationStateDao.insertBudgetThresholdNotified(
            NotifiedBudgetThresholdEntity(categoryName = categoryName, yearMonth = yearMonth, thresholdType = thresholdType.name, notifiedAtEpochMillis = notifiedAtEpochMillis)
        )
    }

    suspend fun getNotifiedRecurringReminderKeys(): Set<Pair<Long, String>> =
        notificationStateDao.getNotifiedRecurringReminderKeys()
            .map { it.planId to it.scheduledYearMonth }
            .toSet()

    suspend fun recordRecurringReminderNotified(planId: Long, scheduledYearMonth: String, notifiedAtEpochMillis: Long) {
        notificationStateDao.insertRecurringReminderNotified(
            NotifiedRecurringReminderEntity(planId = planId, scheduledYearMonth = scheduledYearMonth, notifiedAtEpochMillis = notifiedAtEpochMillis)
        )
    }
}

package com.aradrotem.spendwise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAlertEvaluatorTest {

    private fun point(category: String, budgetCents: Long, actualCents: Long) =
        BudgetActualCalculator.calculate(category, budgetCents, actualCents)

    @Test
    fun under85Percent_noAlert() {
        val points = listOf(point("FOOD", 10_000L, 5_000L))
        assertTrue(BudgetAlertEvaluator.evaluate(points, emptySet()).isEmpty())
    }

    @Test
    fun at85Percent_nearBudgetAlert() {
        val points = listOf(point("FOOD", 10_000L, 8_500L))
        val alerts = BudgetAlertEvaluator.evaluate(points, emptySet())
        assertEquals(1, alerts.size)
        assertEquals(BudgetThresholdType.NEAR_BUDGET, alerts.single().thresholdType)
    }

    @Test
    fun overBudget_overBudgetAlert() {
        val points = listOf(point("FOOD", 10_000L, 12_000L))
        val alerts = BudgetAlertEvaluator.evaluate(points, emptySet())
        assertEquals(1, alerts.size)
        assertEquals(BudgetThresholdType.OVER_BUDGET, alerts.single().thresholdType)
    }

    @Test
    fun alreadyNotified_sameThreshold_suppressed() {
        val points = listOf(point("FOOD", 10_000L, 8_500L))
        val alreadyNotified = setOf("FOOD" to BudgetThresholdType.NEAR_BUDGET)
        assertTrue(BudgetAlertEvaluator.evaluate(points, alreadyNotified).isEmpty())
    }

    @Test
    fun nearBudgetAlreadyNotified_thenCrossesOverBudget_stillFiresOverBudgetAlert() {
        // Distinct threshold tiers are distinct dedup keys, so a category can still escalate from
        // "near" to "over" even after the near-budget alert already fired this month.
        val points = listOf(point("FOOD", 10_000L, 12_000L))
        val alreadyNotified = setOf("FOOD" to BudgetThresholdType.NEAR_BUDGET)
        val alerts = BudgetAlertEvaluator.evaluate(points, alreadyNotified)
        assertEquals(1, alerts.size)
        assertEquals(BudgetThresholdType.OVER_BUDGET, alerts.single().thresholdType)
    }

    @Test
    fun monthlyReset_emptyAlreadyNotifiedSet_firesAgainForNewMonth() {
        // Simulates NotificationRepository.getNotifiedBudgetThresholdKeys scoped to a new month:
        // a fresh empty set means the same category/threshold notifies again.
        val points = listOf(point("FOOD", 10_000L, 8_500L))
        val previousMonthNotified = setOf("FOOD" to BudgetThresholdType.NEAR_BUDGET)
        assertTrue(BudgetAlertEvaluator.evaluate(points, previousMonthNotified).isEmpty())

        val newMonthNotified = emptySet<Pair<String, BudgetThresholdType>>()
        assertEquals(1, BudgetAlertEvaluator.evaluate(points, newMonthNotified).size)
    }

    @Test
    fun multipleCategories_onlyCrossingOnesReported() {
        val points = listOf(
            point("FOOD", 10_000L, 8_500L),
            point("TRANSPORT", 10_000L, 2_000L),
            point("SHOPPING", 10_000L, 11_000L)
        )
        val alerts = BudgetAlertEvaluator.evaluate(points, emptySet())
        assertEquals(setOf("FOOD", "SHOPPING"), alerts.map { it.categoryName }.toSet())
    }
}

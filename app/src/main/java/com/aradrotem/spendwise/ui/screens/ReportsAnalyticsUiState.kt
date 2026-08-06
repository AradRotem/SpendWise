package com.aradrotem.spendwise.ui.screens

import com.aradrotem.spendwise.domain.AnalyticsTimeRange
import com.aradrotem.spendwise.domain.CategoryExpenseDetails
import com.aradrotem.spendwise.domain.FinancialInsight
import java.time.YearMonth

// Unified state for the merged "Reports & analytics" screen (Step 15 final refinement - replaces
// the separate Monthly Report and Visual Analytics screens/ViewModels/routes).
//
// Rather than flattening both screens' data into one huge field list, this reuses the two
// existing, already-tested UiState types unchanged: `monthly` (MonthlyReportUiState, built by
// buildMonthlyReportUiState) is populated when AnalyticsTimeRange.SELECTED_MONTH is active,
// `period` (VisualAnalyticsUiState, built by buildVisualAnalyticsUiState) is populated for the
// 3/6/12-month ranges. Exactly one of the two is non-null at a time - see isSingleMonth.
data class ReportsAnalyticsUiState(
    val isLoading: Boolean = true,
    val selectedMonth: YearMonth = YearMonth.now(),
    val timeRange: AnalyticsTimeRange = AnalyticsTimeRange.SELECTED_MONTH,
    val monthly: MonthlyReportUiState? = null,
    val period: VisualAnalyticsUiState? = null,
    // Insights for monthly mode only - period mode's insights already live inside
    // VisualAnalyticsUiState.insights (reused as-is).
    val monthlyInsights: List<FinancialInsight> = emptyList(),
    // Category drill-down (tap a row in "Spending by category" to see its expenses). Shared by
    // both modes - independent of period mode's separate "Category trend" selector, which drives
    // a different chart. Null selectedDrillDownCategory means nothing is expanded;
    // drillDownDetails is null whenever the category is unselected OR no longer appears in the
    // current period's category distribution (auto-cleared - see ReportsAnalyticsViewModel).
    val selectedDrillDownCategory: String? = null,
    val drillDownDetails: CategoryExpenseDetails? = null
) {
    val isSingleMonth: Boolean get() = timeRange == AnalyticsTimeRange.SELECTED_MONTH
}

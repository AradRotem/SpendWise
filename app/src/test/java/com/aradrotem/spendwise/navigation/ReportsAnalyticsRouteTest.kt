package com.aradrotem.spendwise.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

// Confirms the single unified route for the merged Monthly Report + Visual Analytics feature.
// The old Screen.MonthlyReport/Screen.VisualAnalytics objects no longer exist at all (compilation
// itself proves they were removed, not just hidden) - this only pins down the one route that
// replaced them.
class ReportsAnalyticsRouteTest {

    @Test
    fun reportsAnalyticsRoute_isDefined() {
        assertEquals("visual_analytics", Screen.ReportsAnalytics.route)
    }
}

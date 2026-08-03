package com.aradrotem.spendwise.domain

import com.aradrotem.spendwise.ui.format.formatAmountInCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSettlementExplanationTest {

    private val names = mapOf(1L to "Bar", 2L to "Shai", 3L to "Dor")

    @Test
    fun physicalDeviceExample_producesSpecificRedirectExplanation() {
        val originalDebts = listOf(
            GroupPairwiseDebt(2L, 1L, 20_000L), // Shai -> Bar
            GroupPairwiseDebt(2L, 3L, 95_000L), // Shai -> Dor
            GroupPairwiseDebt(1L, 3L, 80_000L)  // Bar -> Dor
        )
        val simplified = listOf(
            GroupSettlement(2L, 3L, 115_000L), // Shai -> Dor
            GroupSettlement(1L, 3L, 60_000L)   // Bar -> Dor
        )

        val explanation = GroupSettlementExplanation.explain(originalDebts, simplified, names, ::formatAmountInCents)

        requireNotNull(explanation)
        assertTrue(explanation.contains("Shai"))
        assertTrue(explanation.contains("Bar"))
        assertTrue(explanation.contains("Dor"))
        assertTrue(explanation.contains("200.00"))
        assertTrue(explanation.contains("1150.00"))
        assertTrue(explanation.contains("800.00"))
        assertTrue(explanation.contains("600.00"))
    }

    @Test
    fun noSimplifiedTransfers_producesNoExplanation() {
        val explanation = GroupSettlementExplanation.explain(emptyList(), emptyList(), names, ::formatAmountInCents)

        assertNull(explanation)
    }

    @Test
    fun simplifiedIdenticalToOriginal_producesNoExplanation() {
        val originalDebts = listOf(GroupPairwiseDebt(2L, 1L, 1_000L))
        val simplified = listOf(GroupSettlement(2L, 1L, 1_000L))

        val explanation = GroupSettlementExplanation.explain(originalDebts, simplified, names, ::formatAmountInCents)

        assertNull(explanation)
    }

    @Test
    fun complexCase_fallsBackToGenericExplanation() {
        // Four members with a redirection pattern that doesn't match the single-intermediary case.
        val originalDebts = listOf(
            GroupPairwiseDebt(2L, 1L, 1_000L),
            GroupPairwiseDebt(3L, 1L, 1_000L),
            GroupPairwiseDebt(4L, 1L, 1_000L)
        )
        val simplified = listOf(
            GroupSettlement(2L, 1L, 1_500L),
            GroupSettlement(3L, 1L, 1_500L)
        )

        val explanation = GroupSettlementExplanation.explain(originalDebts, simplified, names, ::formatAmountInCents)

        assertEquals(
            "Some debts were redirected to reduce the number of separate payments while preserving every member's final balance.",
            explanation
        )
    }

    @Test
    fun explanation_neverHardCodesNamesOrAmounts_reflectsSuppliedData() {
        val originalDebts = listOf(
            GroupPairwiseDebt(10L, 20L, 50_000L),
            GroupPairwiseDebt(10L, 30L, 90_000L),
            GroupPairwiseDebt(20L, 30L, 80_000L)
        )
        val simplified = listOf(
            GroupSettlement(10L, 30L, 140_000L),
            GroupSettlement(20L, 30L, 30_000L)
        )
        val otherNames = mapOf(10L to "Alpha", 20L to "Beta", 30L to "Gamma")

        val explanation = GroupSettlementExplanation.explain(originalDebts, simplified, otherNames, ::formatAmountInCents)

        requireNotNull(explanation)
        assertTrue(explanation.contains("Alpha"))
        assertTrue(explanation.contains("Beta"))
        assertTrue(explanation.contains("Gamma"))
        assertTrue(explanation.contains("500.00"))
    }
}

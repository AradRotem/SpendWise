package com.aradrotem.spendwise.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupExpenseFormLogicTest {

    @Test
    fun validateCustomShareText_validAmount_succeedsWithCents() {
        val result = validateCustomShareText("45.50")

        assertEquals(4_550L, result.getOrNull())
    }

    @Test
    fun validateCustomShareText_blank_fails() {
        val result = validateCustomShareText("")

        assertTrue(result.isFailure)
    }

    @Test
    fun validateCustomShareText_negative_fails() {
        val result = validateCustomShareText("-10")

        assertTrue(result.isFailure)
    }

    @Test
    fun validateCustomShareText_malformed_fails() {
        val result = validateCustomShareText("abc")

        assertTrue(result.isFailure)
    }

    @Test
    fun validateCustomShareText_tooManyDecimalPlaces_fails() {
        val result = validateCustomShareText("10.999")

        assertTrue(result.isFailure)
    }

    @Test
    fun validateCustomShareText_zero_isAllowed() {
        val result = validateCustomShareText("0")

        assertEquals(0L, result.getOrNull())
    }
}

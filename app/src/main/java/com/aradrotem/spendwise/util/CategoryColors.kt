package com.aradrotem.spendwise.util

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Fixed, deterministic palette - no category color is stored anywhere (Step 15 adds no schema
// change), so the same category name always maps to the same color purely by name hashing. Chosen
// to be distinguishable without relying on red/green alone.
private val CATEGORY_COLOR_PALETTE = listOf(
    Color(0xFF4C6EF5), Color(0xFFF76707), Color(0xFF2F9E44), Color(0xFFE64980),
    Color(0xFFAE3EC9), Color(0xFF1098AD), Color(0xFFF08C00), Color(0xFF868E96)
)

fun colorForCategory(categoryName: String): Color =
    CATEGORY_COLOR_PALETTE[abs(categoryName.hashCode()) % CATEGORY_COLOR_PALETTE.size]

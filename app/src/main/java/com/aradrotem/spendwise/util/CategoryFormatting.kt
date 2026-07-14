package com.aradrotem.spendwise.util

// Sentence-style display formatting only; stored category identifiers (e.g. "FOOD") are untouched.
fun formatCategoryDisplayName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return trimmed
    return trimmed.lowercase().replaceFirstChar { it.uppercase() }
}

// Case-insensitive alphabetical order with the built-in OTHER category always last.
// Mirrors the ordering CategoryDao.observeByType applies in SQL, for lists built in Kotlin.
fun compareCategoryNames(a: String, b: String): Int {
    val aIsOther = a.equals("OTHER", ignoreCase = true)
    val bIsOther = b.equals("OTHER", ignoreCase = true)
    if (aIsOther != bIsOther) return if (aIsOther) 1 else -1
    return a.compareTo(b, ignoreCase = true)
}

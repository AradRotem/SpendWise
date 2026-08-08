package com.aradrotem.spendwise.ui.screens

// Client-side pre-checks shared by SignUp/Login/ForgotPassword, so obviously invalid input never
// round-trips to Firebase. Kept top-level (not private to one ViewModel) so it's directly
// unit-testable and reusable.
private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

fun isValidPassword(password: String): Boolean = password.length >= 6

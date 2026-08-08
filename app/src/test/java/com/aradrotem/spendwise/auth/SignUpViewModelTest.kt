package com.aradrotem.spendwise.auth

import com.aradrotem.spendwise.ui.screens.SignUpViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignUpViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun signUp_blanksAllFields_showsValidationErrorsAndNeverCallsRepository() {
        val fakeAuth = FakeAuthRepository()
        val viewModel = SignUpViewModel(fakeAuth)

        viewModel.signUp()

        val state = viewModel.uiState.value
        assertEquals("Name is required.", state.displayNameError)
        assertEquals("Enter a valid email address.", state.emailError)
        assertEquals("Password must be at least 6 characters.", state.passwordError)
        assertEquals(0, fakeAuth.signUpCallCount)
    }

    @Test
    fun signUp_passwordMismatch_showsConfirmPasswordError() {
        val fakeAuth = FakeAuthRepository()
        val viewModel = SignUpViewModel(fakeAuth)
        viewModel.onDisplayNameChange("Ann")
        viewModel.onEmailChange("ann@test.com")
        viewModel.onPasswordChange("password1")
        viewModel.onConfirmPasswordChange("password2")

        viewModel.signUp()

        assertEquals("Passwords do not match.", viewModel.uiState.value.confirmPasswordError)
        assertEquals(0, fakeAuth.signUpCallCount)
    }

    @Test
    fun signUp_validInput_callsRepositoryAndReportsSuccess() {
        val fakeAuth = FakeAuthRepository()
        val viewModel = SignUpViewModel(fakeAuth)
        viewModel.onDisplayNameChange("Ann")
        viewModel.onEmailChange("ann@test.com")
        viewModel.onPasswordChange("password1")
        viewModel.onConfirmPasswordChange("password1")

        viewModel.signUp()

        assertEquals(1, fakeAuth.signUpCallCount)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.signUpError)
        assert(viewModel.uiState.value.isSignedUp)
    }
}

package com.aradrotem.spendwise.auth

import com.aradrotem.spendwise.data.auth.AuthError
import com.aradrotem.spendwise.ui.screens.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun login_blankPassword_showsValidationErrorAndNeverCallsRepository() {
        val fakeAuth = FakeAuthRepository()
        val viewModel = LoginViewModel(fakeAuth)
        viewModel.onEmailChange("ann@test.com")

        viewModel.login()

        assertEquals("Password is required.", viewModel.uiState.value.passwordError)
        assertEquals(0, fakeAuth.loginCallCount)
    }

    @Test
    fun login_success_setsIsLoggedIn() {
        val fakeAuth = FakeAuthRepository()
        val viewModel = LoginViewModel(fakeAuth)
        viewModel.onEmailChange("ann@test.com")
        viewModel.onPasswordChange("password1")

        viewModel.login()

        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertEquals(1, fakeAuth.loginCallCount)
    }

    @Test
    fun login_wrongPassword_showsFriendlyMessage_notRawException() {
        val fakeAuth = FakeAuthRepository()
        fakeAuth.nextLoginResult = Result.failure(AuthError.WrongPassword)
        val viewModel = LoginViewModel(fakeAuth)
        viewModel.onEmailChange("ann@test.com")
        viewModel.onPasswordChange("wrongpass")

        viewModel.login()

        assertEquals("Incorrect email or password.", viewModel.uiState.value.loginError)
    }
}

package com.aradrotem.spendwise.auth

import com.aradrotem.spendwise.data.auth.AuthError
import com.aradrotem.spendwise.ui.screens.ForgotPasswordViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendResetEmail_blankEmail_showsValidationErrorAndNeverCallsRepository() {
        val fakeAuth = FakeAuthRepository()
        val viewModel = ForgotPasswordViewModel(fakeAuth)

        viewModel.sendResetEmail()

        assertEquals("Enter a valid email address.", viewModel.uiState.value.emailError)
        assertEquals(0, fakeAuth.sendPasswordResetCallCount)
    }

    @Test
    fun sendResetEmail_invalidEmail_showsValidationErrorAndNeverCallsRepository() {
        val fakeAuth = FakeAuthRepository()
        val viewModel = ForgotPasswordViewModel(fakeAuth)
        viewModel.onEmailChange("not-an-email")

        viewModel.sendResetEmail()

        assertEquals("Enter a valid email address.", viewModel.uiState.value.emailError)
        assertEquals(0, fakeAuth.sendPasswordResetCallCount)
    }

    @Test
    fun sendResetEmail_validEmail_callsRepositoryWithTrimmedEmail() {
        val fakeAuth = FakeAuthRepository()
        val viewModel = ForgotPasswordViewModel(fakeAuth)
        viewModel.onEmailChange("  ann@test.com  ")

        viewModel.sendResetEmail()

        assertEquals(1, fakeAuth.sendPasswordResetCallCount)
    }

    @Test
    fun sendResetEmail_showsLoadingStateWhileRequestInFlight() {
        val fakeAuth = FakeAuthRepository()
        fakeAuth.resetCompletionGate = CompletableDeferred()
        val viewModel = ForgotPasswordViewModel(fakeAuth)
        viewModel.onEmailChange("ann@test.com")

        viewModel.sendResetEmail()

        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isEmailSent)

        fakeAuth.resetCompletionGate?.complete(Unit)

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isEmailSent)
    }

    @Test
    fun sendResetEmail_success_setsIsEmailSent() {
        val fakeAuth = FakeAuthRepository()
        val viewModel = ForgotPasswordViewModel(fakeAuth)
        viewModel.onEmailChange("ann@test.com")

        viewModel.sendResetEmail()

        assertTrue(viewModel.uiState.value.isEmailSent)
        assertNull(viewModel.uiState.value.requestError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun sendResetEmail_repositoryFailure_showsMappedFriendlyError_notRawException() {
        val fakeAuth = FakeAuthRepository()
        fakeAuth.nextResetResult = Result.failure(AuthError.UserNotFound)
        val viewModel = ForgotPasswordViewModel(fakeAuth)
        viewModel.onEmailChange("ann@test.com")

        viewModel.sendResetEmail()

        assertEquals("No account found with this email.", viewModel.uiState.value.requestError)
        assertFalse(viewModel.uiState.value.isEmailSent)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun sendResetEmail_duplicateTapsWhileLoading_doNotSendDuplicateRequests() {
        val fakeAuth = FakeAuthRepository()
        fakeAuth.resetCompletionGate = CompletableDeferred()
        val viewModel = ForgotPasswordViewModel(fakeAuth)
        viewModel.onEmailChange("ann@test.com")

        viewModel.sendResetEmail()
        viewModel.sendResetEmail()
        viewModel.sendResetEmail()

        assertEquals(1, fakeAuth.sendPasswordResetCallCount)
        fakeAuth.resetCompletionGate?.complete(Unit)
    }
}

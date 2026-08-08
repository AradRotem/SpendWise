package com.aradrotem.spendwise.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aradrotem.spendwise.data.auth.AuthRepository

enum class SplashDestination { HOME, LOGIN }

// Routes app startup through AuthRepository only - never touches FirebaseAuth directly (that
// dependency is fully hidden behind the repository interface). currentUid is a synchronous
// snapshot, so this resolves in the same frame the screen first composes: no loading flash.
class SplashViewModel(authRepository: AuthRepository) : ViewModel() {
    val destination: SplashDestination =
        if (authRepository.currentUid != null) SplashDestination.HOME else SplashDestination.LOGIN

    companion object {
        fun factory(authRepository: AuthRepository) = viewModelFactory {
            initializer { SplashViewModel(authRepository) }
        }
    }
}

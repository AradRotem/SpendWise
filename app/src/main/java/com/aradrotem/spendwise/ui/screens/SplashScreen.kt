package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication

@Composable
fun SplashScreen(
    onResolved: (SplashDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = viewModel(
        factory = SplashViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).authRepository
        )
    )
) {
    LaunchedEffect(Unit) {
        onResolved(viewModel.destination)
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

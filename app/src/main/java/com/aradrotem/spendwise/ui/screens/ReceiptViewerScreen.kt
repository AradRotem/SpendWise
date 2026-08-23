package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.aradrotem.spendwise.SpendWiseApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptViewerScreen(
    transactionId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    app: SpendWiseApplication = LocalContext.current.applicationContext as SpendWiseApplication,
    viewModel: ReceiptViewerViewModel = viewModel(
        factory = ReceiptViewerViewModel.factory(
            app.repositories.transactionRepository,
            app.repositories.receiptRepository,
            transactionId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Receipt") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.error != null -> Text(uiState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                uiState.displayModel != null -> ZoomableReceiptImage(model = uiState.displayModel)
            }
        }
    }
}

// Minimal pinch-to-zoom/pan, built directly on Compose's own gesture APIs rather than pulling in
// a dedicated zoom library for what's otherwise a small amount of behavior.
@Composable
private fun ZoomableReceiptImage(model: Any?, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Keyed by model so switching to a different receipt (e.g. once a remote download URL
    // resolves after a local-cache miss) re-attempts the load rather than staying stuck on a
    // previous failure.
    var loadFailed by remember(model) { mutableStateOf(false) }

    if (loadFailed) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Couldn't load this receipt image.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    AsyncImage(
        model = model,
        contentDescription = "Receipt, pinch to zoom",
        contentScale = ContentScale.Fit,
        onState = { state -> loadFailed = state is AsyncImagePainter.State.Error },
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    offset = if (newScale <= 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
    )
}

package com.aradrotem.spendwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.aradrotem.spendwise.navigation.SpendWiseApp
import com.aradrotem.spendwise.ui.theme.SpendWiseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpendWiseTheme {
                SpendWiseApp()
            }
        }
    }

    // Generation is idempotent, so re-running it whenever the app returns to the foreground
    // (e.g. reopened from Recents after being backgrounded for a while) is safe and picks up
    // any due payments without needing a background scheduler.
    override fun onResume() {
        super.onResume()
        val app = application as SpendWiseApplication
        lifecycleScope.launch(Dispatchers.IO) {
            app.recurringPaymentGenerator.generateDuePayments()
        }
    }
}

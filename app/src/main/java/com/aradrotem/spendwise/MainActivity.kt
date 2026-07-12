package com.aradrotem.spendwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aradrotem.spendwise.navigation.SpendWiseApp
import com.aradrotem.spendwise.ui.theme.SpendWiseTheme

class MainActivity git status: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpendWiseTheme {
                SpendWiseApp()
            }
        }
    }
}
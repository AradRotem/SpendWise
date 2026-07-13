package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onNavigateToCategories: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(text = "Settings", modifier = Modifier.padding(16.dp))
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Categories") },
            supportingContent = { Text("Manage income and expense categories") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToCategories)
        )
    }
}

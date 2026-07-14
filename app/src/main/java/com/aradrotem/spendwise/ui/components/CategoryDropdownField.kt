package com.aradrotem.spendwise.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aradrotem.spendwise.util.formatCategoryDisplayName

@Composable
fun CategoryDropdownField(
    selected: String?,
    error: String?,
    options: List<String>,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select category",
    disabledOptions: Set<String> = emptySet(),
    disabledNote: String = "Budget already exists"
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let { formatCategoryDisplayName(it) } ?: placeholder)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { categoryName ->
                    val isDisabled = categoryName in disabledOptions
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(formatCategoryDisplayName(categoryName))
                                if (isDisabled) {
                                    Text(
                                        text = disabledNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        enabled = !isDisabled,
                        onClick = {
                            onCategorySelected(categoryName)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

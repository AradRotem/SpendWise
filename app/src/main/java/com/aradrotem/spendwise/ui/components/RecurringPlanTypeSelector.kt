package com.aradrotem.spendwise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aradrotem.spendwise.data.local.RecurringPlanType

fun recurringPlanTypeLabel(type: RecurringPlanType): String = when (type) {
    RecurringPlanType.MONTHLY_RECURRING -> "Monthly expense"
    RecurringPlanType.INSTALLMENT -> "Installment purchase"
    RecurringPlanType.MONTHLY_SALARY -> "Monthly salary"
}

// Vertically stacked, full-width options rather than a single Row: three buttons with labels
// like "Installment purchase" don't reliably fit side by side on a normal phone width, and a Row
// with no wrapping silently pushes the last one off-screen instead of clipping visibly. Stacking
// guarantees every option stays fully visible and tappable regardless of screen width or label
// length/locale.
@Composable
fun RecurringPlanTypeSelector(
    selectedType: RecurringPlanType,
    onTypeSelected: (RecurringPlanType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RecurringPlanType.entries.forEach { type ->
            val label = recurringPlanTypeLabel(type)
            if (type == selectedType) {
                Button(onClick = { onTypeSelected(type) }, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = { onTypeSelected(type) }, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
            }
        }
    }
}

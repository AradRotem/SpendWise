package com.aradrotem.spendwise.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aradrotem.spendwise.SpendWiseApplication
import com.aradrotem.spendwise.domain.GroupPairwiseDebt
import com.aradrotem.spendwise.domain.GroupSettlement
import com.aradrotem.spendwise.ui.format.formatAmountInCents

// Reuses GroupDetailsViewModel (it already computes original debts, balances and simplified
// settlements - see buildGroupDetailsUiState) rather than duplicating those reactive queries for
// a screen that only presents a subset of the same data.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettlementScreen(
    groupId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupDetailsViewModel = viewModel(
        factory = GroupDetailsViewModel.factory(
            (LocalContext.current.applicationContext as SpendWiseApplication).groupExpenseRepository,
            groupId
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var explanationExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settlement suggestions") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Back") }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OriginalDebtsSection(uiState.originalDebts, uiState.memberNameById)

                HorizontalDivider()

                SimplifiedSettlementSection(uiState.settlements, uiState.memberNameById)

                uiState.settlementExplanation?.let { explanation ->
                    HorizontalDivider()
                    ExplanationSection(
                        expanded = explanationExpanded,
                        onToggle = { explanationExpanded = !explanationExpanded },
                        explanation = explanation
                    )
                }
            }
        }
    }
}

@Composable
private fun OriginalDebtsSection(originalDebts: List<GroupPairwiseDebt>, memberNameById: Map<Long, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Original debts", style = MaterialTheme.typography.titleMedium)
        Text(
            "These amounts show who owes whom based directly on the recorded expenses.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (originalDebts.isEmpty()) {
            Text("There are no outstanding debts between members.")
        } else {
            originalDebts.forEach { debt ->
                val debtorName = memberNameById[debt.debtorMemberId] ?: "Unknown"
                val creditorName = memberNameById[debt.creditorMemberId] ?: "Unknown"
                DebtCard("$debtorName owes $creditorName ${formatAmountInCents(debt.amountCents)}")
            }
        }
    }
}

@Composable
private fun SimplifiedSettlementSection(settlements: List<GroupSettlement>, memberNameById: Map<Long, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Simplified settlement", style = MaterialTheme.typography.titleMedium)
        Text(
            "These transfers settle the group using fewer payments.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (settlements.isEmpty()) {
            Text("Everyone is settled. No payments are required.")
        } else {
            settlements.forEach { settlement ->
                val fromName = memberNameById[settlement.fromMemberId] ?: "Unknown"
                val toName = memberNameById[settlement.toMemberId] ?: "Unknown"
                DebtCard("$fromName pays $toName ${formatAmountInCents(settlement.amountCents)}")
            }
        }
    }
}

@Composable
private fun ExplanationSection(expanded: Boolean, onToggle: () -> Unit, explanation: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onToggle) {
            Text(if (expanded) "How was this simplified? ▲" else "How was this simplified? ▼")
        }
        if (expanded) {
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DebtCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

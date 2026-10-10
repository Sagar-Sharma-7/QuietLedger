package com.quietledger.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietledger.models.TransactionStatus
import com.quietledger.repository.FinanceRepository
import com.quietledger.ui.components.EmptyState
import com.quietledger.ui.components.TransactionRow
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(repo: FinanceRepository, open: (Long) -> Unit) {
    val transactions by repo.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val reviewItems = transactions.filter {
        it.needsReview || it.isPossibleDuplicate || it.confidence < 70 || it.status != TransactionStatus.COMPLETED || it.merchant == null
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("Review", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Confirm detected payments before they affect your totals.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (reviewItems.isEmpty()) {
            EmptyState("Nothing needs review", "Low-confidence and possible duplicate transactions appear here.")
        } else {
            LazyColumn {
                items(reviewItems, key = { it.id }) { transaction ->
                    TransactionRow(transaction) { open(transaction.id) }
                    if (transaction.needsReview) {
                        androidx.compose.foundation.layout.Row(Modifier.padding(vertical = 6.dp)) {
                            Button(onClick = { scope.launch { repo.approve(transaction) } }, modifier = Modifier.weight(1f)) { Text("Add") }
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                            OutlinedButton(onClick = { scope.launch { repo.delete(transaction) } }, modifier = Modifier.weight(1f)) { Text("Ignore") }
                        }
                    }
                }
            }
        }
    }
}

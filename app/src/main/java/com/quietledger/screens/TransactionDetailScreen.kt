package com.quietledger.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quietledger.database.TransactionEntity
import com.quietledger.models.Category
import com.quietledger.models.TransactionType
import com.quietledger.repository.FinanceRepository
import com.quietledger.ui.components.money
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TransactionDetailScreen(repo: FinanceRepository, id: Long, onBack: () -> Unit) {
    val transaction by repo.transaction(id).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val item = transaction ?: return
    val inbound = item.type == TransactionType.CREDIT || item.type == TransactionType.REFUND

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ Back") }
        Spacer(Modifier.height(18.dp))
        Text(item.merchant ?: "Unknown transaction", style = MaterialTheme.typography.headlineMedium)
        Text(
            money(item.amountPaise, if (inbound) "+ " else "− "),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (inbound) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(24.dp))
        Detail("Type", item.type.name.lowercase().replaceFirstChar { it.uppercase() })
        Detail("Category", item.category.label)
        Detail("Date & time", Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")))
        Detail("Source", item.sourceApp)
        item.paymentMethod?.let { Detail("Payment method", it) }
        item.accountSuffix?.let { Detail("Account ending", it) }
        Detail("Status", item.status.name.lowercase().replaceFirstChar { it.uppercase() })
        item.notes?.let { Detail("Note", it) }
        Spacer(Modifier.height(20.dp))
        Text("Correct transaction", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        CategoryPicker(item.category) { category -> scope.launch { repo.assignCategory(item, category) } }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit merchant or note") }
        OutlinedButton(
            onClick = { scope.launch { repo.save(item.copy(type = TransactionType.TRANSFER, category = Category.TRANSFERS)) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Mark as transfer") }
        OutlinedButton(
            onClick = { scope.launch { repo.save(item.copy(isExcluded = !item.isExcluded)) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (item.isExcluded) "Include in calculations" else "Exclude from calculations") }
        TextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete transaction") }
    }

    if (editing) EditDialog(item, { editing = false }) { merchant, note ->
        scope.launch {
            repo.save(item.copy(merchant = merchant.ifBlank { null }, notes = note.ifBlank { null }))
            editing = false
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete transaction?") },
            text = { Text("This removes only the structured transaction. Raw notification text was never saved.") },
            confirmButton = { TextButton(onClick = { scope.launch { repo.delete(item); onBack() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EditDialog(transaction: TransactionEntity, dismiss: () -> Unit, save: (String, String) -> Unit) {
    var merchant by remember(transaction.id) { mutableStateOf(transaction.merchant.orEmpty()) }
    var note by remember(transaction.id) { mutableStateOf(transaction.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Edit transaction") },
        text = {
            Column {
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Note") })
            }
        },
        confirmButton = { TextButton(onClick = { save(merchant, note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Detail(label: String, value: String) {
    Column(Modifier.padding(vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

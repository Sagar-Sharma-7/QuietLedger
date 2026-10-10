package com.quietledger.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.quietledger.repository.FinanceRepository
import com.quietledger.statement.ImportMatch
import com.quietledger.statement.StatementRow
import com.quietledger.ui.components.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StatementImportScreen(repo: FinanceRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<StatementRow>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var message by remember { mutableStateOf("Choose a CSV exported by your bank. It is parsed only in memory.") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val preview = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { repo.previewStatement(it.readText()) }.orEmpty() }
            rows = preview; selected = preview.indices.filter { preview[it].match == ImportMatch.NEW }.toSet()
            message = if (preview.isEmpty()) "No supported CSV rows found. Use headers such as Date, Description, Debit, Credit, Amount, or Reference." else "Review rows before importing. Exact duplicates are not selected."
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Import bank statement", style = MaterialTheme.typography.headlineSmall)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "text/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Choose CSV statement") }
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("${selected.size} new transactions selected", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(rows) { index, row ->
                    val selectable = row.match != ImportMatch.EXACT_DUPLICATE && row.match != ImportMatch.INVALID
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Checkbox(checked = index in selected, enabled = selectable, onCheckedChange = { checked -> selected = if (checked) selected + index else selected - index })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(row.description, style = MaterialTheme.typography.bodyLarge)
                            Text("${if (row.type.name == "DEBIT") "Debit" else "Credit"} · ${money(row.amountPaise)} · ${label(row.match)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Button(onClick = { scope.launch { repo.importStatement(rows.filterIndexed { index, _ -> index in selected }); onBack() } }, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Import selected") }
        }
    }
}

private fun label(match: ImportMatch) = when (match) {
    ImportMatch.NEW -> "New"
    ImportMatch.EXACT_DUPLICATE -> "Already recorded"
    ImportMatch.POSSIBLE_DUPLICATE -> "Possible duplicate — review"
    ImportMatch.INVALID -> "Invalid"
}

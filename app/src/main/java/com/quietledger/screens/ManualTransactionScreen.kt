package com.quietledger.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quietledger.database.TransactionEntity
import com.quietledger.models.Category
import com.quietledger.models.TransactionType
import com.quietledger.parser.Categorizer
import com.quietledger.repository.FinanceRepository
import kotlinx.coroutines.launch

@Composable
fun ManualTransactionScreen(repo: FinanceRepository, onDone: () -> Unit) {
    var income by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.OTHER) }
    val scope = rememberCoroutineScope()
    val amountPaise = amount.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Add transaction", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))
        Row {
            FilterChip(selected = !income, onClick = { income = false }, label = { Text("Expense") })
            Spacer(Modifier.padding(4.dp))
            FilterChip(selected = income, onClick = { income = true }, label = { Text("Income") })
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Amount") },
            prefix = { Text("₹ ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Merchant or source") },
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        CategoryPicker(category) { category = it }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note (optional)") },
            minLines = 2
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                if (amountPaise > 0L) {
                    scope.launch {
                        val type = if (income) TransactionType.CREDIT else TransactionType.DEBIT
                        repo.save(
                            TransactionEntity(
                                amountPaise = amountPaise,
                                type = type,
                                merchant = merchant.ifBlank { null },
                                category = if (category == Category.OTHER) Categorizer.categoryFor(merchant, type) else category,
                                timestamp = System.currentTimeMillis(),
                                sourceApp = "Manual",
                                notes = notes.ifBlank { null }
                            )
                        )
                        onDone()
                    }
                }
            },
            enabled = amountPaise > 0L,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save transaction") }
    }
}

@Composable
fun CategoryPicker(value: Category, change: (Category) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value.label, Modifier.weight(1f))
            Text("Change")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Category.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label) },
                    onClick = { change(category); expanded = false }
                )
            }
        }
    }
}

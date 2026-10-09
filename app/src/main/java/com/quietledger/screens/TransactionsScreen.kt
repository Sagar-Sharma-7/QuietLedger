package com.quietledger.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quietledger.database.TransactionEntity
import com.quietledger.models.TransactionStatus
import com.quietledger.models.TransactionType
import com.quietledger.repository.FinanceRepository
import com.quietledger.ui.components.EmptyState
import com.quietledger.ui.components.QuietCard
import com.quietledger.ui.components.TransactionRow
import com.quietledger.ui.components.money
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionsScreen(
    repo: FinanceRepository,
    open: (Long) -> Unit,
    openMonth: (String) -> Unit,
    add: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val transactions by (if (query.isBlank()) repo.all() else repo.search(query.removePrefix("₹")))
        .collectAsState(initial = emptyList())
    val monthlyGroups = remember(transactions) {
        transactions.groupBy { transaction ->
            YearMonth.from(Instant.ofEpochMilli(transaction.timestamp).atZone(ZoneId.systemDefault()))
        }.toSortedMap(compareByDescending<YearMonth> { it })
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = add, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(22.dp))
            Text("Transactions", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search merchant, amount, category or note") },
                singleLine = true
            )

            if (transactions.isEmpty()) {
                EmptyState(
                    title = if (query.isBlank()) "No transactions yet" else "No matches",
                    body = if (query.isBlank()) "Add a transaction or wait for a selected app notification." else "Try a merchant, category or amount.",
                    action = if (query.isBlank()) "Add transaction" else null,
                    onAction = if (query.isBlank()) add else null
                )
            } else {
                LazyColumn {
                    if (query.isBlank()) {
                        monthlyGroups.forEach { (month, rows) ->
                            item(key = "month-$month") {
                                MonthHeader(month, rows, onOpen = { openMonth(month.toString()) })
                            }
                            items(rows, key = { it.id }) { transaction ->
                                TransactionRow(transaction) { open(transaction.id) }
                            }
                        }
                    } else {
                        items(transactions, key = { it.id }) { transaction ->
                            TransactionRow(transaction) { open(transaction.id) }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, rows: List<TransactionEntity>, onOpen: () -> Unit) {
    val totals = monthTotals(rows)
    Spacer(Modifier.height(20.dp))
    QuietCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountCaption("Spent", totals.spent, MaterialTheme.colorScheme.error)
                    AmountCaption("Received", totals.received, MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    "Net ${money(kotlin.math.abs(totals.net), if (totals.net >= 0) "+" else "−")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (totals.net >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "View ${month.month} category summary")
            }
        }
    }
}

@Composable
private fun AmountCaption(label: String, amount: Long, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(amount), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

data class MonthTotals(val spent: Long, val received: Long) {
    val net: Long get() = received - spent
}

fun monthTotals(rows: List<TransactionEntity>): MonthTotals {
    val included = rows.filter { !it.isExcluded && !it.isPossibleDuplicate && it.status == TransactionStatus.COMPLETED }
    return MonthTotals(
        spent = included.filter { it.type == TransactionType.DEBIT }.sumOf { it.amountPaise },
        received = included.filter { it.type == TransactionType.CREDIT || it.type == TransactionType.REFUND }.sumOf { it.amountPaise }
    )
}

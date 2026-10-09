package com.quietledger.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quietledger.database.TransactionEntity
import com.quietledger.models.Category
import com.quietledger.models.TransactionStatus
import com.quietledger.models.TransactionType
import com.quietledger.repository.FinanceRepository
import com.quietledger.ui.components.QuietCard
import com.quietledger.ui.components.money
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MonthOverviewScreen(repo: FinanceRepository, monthKey: String, onBack: () -> Unit) {
    val month = runCatching { YearMonth.parse(monthKey) }.getOrElse { YearMonth.now() }
    val allTransactions by repo.all().collectAsState(initial = emptyList())
    val rows = allTransactions.filter {
        YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault())) == month
    }
    val totals = monthTotals(rows)
    val categories = categoryTotals(rows)

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("‹ Transactions") }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(14.dp))
        QuietCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeaderTotal("Spent", totals.spent, MaterialTheme.colorScheme.error)
                HeaderTotal("Received", totals.received, MaterialTheme.colorScheme.tertiary)
                HeaderTotal(
                    "Net",
                    kotlin.math.abs(totals.net),
                    if (totals.net >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    sign = if (totals.net >= 0) "+" else "−"
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("By category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (categories.isEmpty()) {
            Text("No included transactions for this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(categories, key = { it.category.name }) { category ->
                    CategorySummaryRow(category)
                }
            }
        }
    }
}

@Composable
private fun HeaderTotal(
    label: String,
    amount: Long,
    color: androidx.compose.ui.graphics.Color,
    sign: String = ""
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            money(amount, sign),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun CategorySummaryRow(category: CategoryTotals) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(category.category.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CategoryAmount("Spent", category.spent, MaterialTheme.colorScheme.error)
            CategoryAmount("Received", category.received, MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun CategoryAmount(label: String, amount: Long, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(amount), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

data class CategoryTotals(val category: Category, val spent: Long, val received: Long)

private fun categoryTotals(rows: List<TransactionEntity>): List<CategoryTotals> = rows
    .filter { !it.isExcluded && !it.isPossibleDuplicate && it.status == TransactionStatus.COMPLETED }
    .groupBy { it.category }
    .map { (category, transactions) ->
        CategoryTotals(
            category = category,
            spent = transactions.filter { it.type == TransactionType.DEBIT }.sumOf { it.amountPaise },
            received = transactions
                .filter { it.type == TransactionType.CREDIT || it.type == TransactionType.REFUND }
                .sumOf { it.amountPaise }
        )
    }
    .sortedByDescending { it.spent + it.received }

package com.quietledger.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quietledger.database.TransactionEntity
import com.quietledger.models.FinanceSummary
import com.quietledger.models.Period
import com.quietledger.models.TransactionType
import com.quietledger.repository.FinanceRepository
import com.quietledger.ui.components.EmptyState
import com.quietledger.ui.components.QuietCard
import com.quietledger.ui.components.SectionTitle
import com.quietledger.ui.components.TransactionRow
import com.quietledger.ui.components.money
import com.quietledger.ui.notificationAccessEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(repo: FinanceRepository, openTransactions: () -> Unit, add: () -> Unit) {
    var period by remember { mutableStateOf(Period.THIS_MONTH) }
    var customRange by remember { mutableStateOf<ClosedRange<Long>?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val transactions by repo.transactions(period, customRange).collectAsState(initial = emptyList())
    val monthTransactions by repo.transactions(Period.THIS_MONTH).collectAsState(initial = emptyList())
    val monthlyIncome by repo.preferences.monthlyIncomePaise.collectAsState(initial = 0L)
    val monthlySavingsGoal by repo.preferences.monthlySavingsGoalPaise.collectAsState(initial = 0L)
    val summary = remember(transactions) { repo.summary(transactions) }
    val budget = remember(monthTransactions, monthlyIncome, monthlySavingsGoal) {
        repo.monthlyBudget(monthTransactions, monthlyIncome, monthlySavingsGoal)
    }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quiet Ledger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = add) { Text("+ Add") }
        }
        PeriodSelector(period) { selected ->
            if (selected == Period.CUSTOM) showDatePicker = true else period = selected
        }
        if (!notificationAccessEnabled(context)) {
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                label = { Text("Automatic tracking is paused · Enable") }
            )
        }
        Spacer(Modifier.height(18.dp))
        Summary(summary)
        if (monthlyIncome > 0L) {
            Spacer(Modifier.height(14.dp))
            BudgetCard(budget)
        }
        Spacer(Modifier.height(32.dp))
        Breakdown(transactions)
        Spacer(Modifier.height(30.dp))
        SectionTitle("Recent transactions", "See all", openTransactions)
        if (transactions.isEmpty()) {
            EmptyState(
                "No transactions yet",
                "Payments detected from your selected apps will appear here.",
                "Add transaction manually",
                add
            )
        } else {
            transactions.take(5).forEach { TransactionRow(it, onClick = {}) }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = pickerState.selectedStartDateMillis
                    val end = pickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        customRange = start..(end + 86_399_999L)
                        period = Period.CUSTOM
                        showDatePicker = false
                    }
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text("Choose dates", Modifier.padding(16.dp)) }
            )
        }
    }
}

@Composable
private fun BudgetCard(budget: com.quietledger.models.MonthlyBudget) {
    QuietCard {
        Text("Monthly budget", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("${money(budget.remainingPaise)} remaining of ${money(budget.spendablePaise)}", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { budget.usedPercent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (budget.usedPercent >= 100f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${money(budget.spentPaise)} spent · ${budget.usedPercent.toInt()}% used · ${money(budget.dailyGuidePaise)}/day for ${budget.daysRemaining} days",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PeriodSelector(selected: Period, choose: (Period) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(selected.label)
                androidx.compose.material3.Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Choose period",
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Period.entries.forEach { period ->
                DropdownMenuItem(
                    text = { Text(period.label) },
                    onClick = { choose(period); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun Summary(summary: FinanceSummary) {
    QuietCard {
        Text("This period", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Spent", money(summary.spent), color = MaterialTheme.colorScheme.error)
            Metric("Received", money(summary.received), color = MaterialTheme.colorScheme.tertiary)
            Metric(
                "Net",
                money(kotlin.math.abs(summary.net), if (summary.net >= 0) "+" else "−"),
                color = if (summary.net >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("${summary.count} transactions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun Breakdown(transactions: List<TransactionEntity>) {
    val spending = transactions
        .filter { it.type == TransactionType.DEBIT && !it.isExcluded && !it.isPossibleDuplicate && !it.needsReview }
        .groupBy { it.category }
        .mapValues { (_, rows) -> rows.sumOf { it.amountPaise } }
        .toList()
        .sortedByDescending { it.second }

    if (spending.isNotEmpty()) {
        SectionTitle("Spending breakdown")
        val total = spending.sumOf { it.second }.toFloat()
        spending.take(5).forEach { (category, amount) ->
            Column(Modifier.padding(vertical = 7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(category.label, style = MaterialTheme.typography.bodyMedium)
                    Text(money(amount), style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(
                    progress = { amount / total },
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(4.dp),
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

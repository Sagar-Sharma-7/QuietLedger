package com.quietledger.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quietledger.models.Period
import com.quietledger.repository.FinanceRepository
import com.quietledger.ui.components.QuietCard
import com.quietledger.ui.components.money
import kotlinx.coroutines.launch

@Composable
fun BudgetScreen(repo: FinanceRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val income by repo.preferences.monthlyIncomePaise.collectAsState(initial = 0L)
    val savings by repo.preferences.monthlySavingsGoalPaise.collectAsState(initial = 0L)
    val alertsEnabled by repo.preferences.budgetAlertsEnabled.collectAsState(initial = false)
    val transactions by repo.transactions(Period.THIS_MONTH).collectAsState(initial = emptyList())
    val budget = remember(transactions, income, savings) { repo.monthlyBudget(transactions, income, savings) }
    var incomeInput by remember(income) { mutableStateOf(rupeesInput(income)) }
    var savingsInput by remember(savings) { mutableStateOf(rupeesInput(savings)) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var explainNotificationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { repo.preferences.setBudgetAlertsEnabled(granted) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Monthly budget", style = MaterialTheme.typography.headlineSmall)
        Text("Stored only on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = incomeInput, onValueChange = { incomeInput = it; inputError = null },
            label = { Text("Monthly income / salary (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = savingsInput, onValueChange = { savingsInput = it; inputError = null },
            label = { Text("Monthly savings goal (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), singleLine = true, isError = inputError != null,
            supportingText = { inputError?.let { Text(it) } }
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val incomeValue = paiseInput(incomeInput)
            val savingsValue = paiseInput(savingsInput)
            if (incomeValue == null || savingsValue == null) inputError = "Enter valid non-negative amounts."
            else if (savingsValue > incomeValue) inputError = "Savings goal cannot exceed income."
            else scope.launch { repo.preferences.setBudget(incomeValue, savingsValue) }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save budget") }
        Spacer(Modifier.height(20.dp))
        QuietCard {
            Text("This month", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            BudgetLine("Spendable amount", money(budget.spendablePaise))
            BudgetLine("Money spent", money(budget.spentPaise))
            BudgetLine("Remaining", money(budget.remainingPaise))
            BudgetLine("Daily spending guide", money(budget.dailyGuidePaise))
            Text("${budget.usedPercent.toInt()}% of spendable budget used · ${budget.daysRemaining} days remaining", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        ListItem(
            headlineContent = { Text("Daily overspend alert") },
            supportingContent = { Text("A local alert when today's spending exceeds your current guide.") },
            trailingContent = { Switch(checked = alertsEnabled, onCheckedChange = { enabled ->
                if (!enabled) scope.launch { repo.preferences.setBudgetAlertsEnabled(false) }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) explainNotificationPermission = true
                else scope.launch { repo.preferences.setBudgetAlertsEnabled(true) }
            }) }
        )
        Spacer(Modifier.height(20.dp))
    }
    if (explainNotificationPermission) {
        AlertDialog(
            onDismissRequest = { explainNotificationPermission = false },
            title = { Text("Allow a local budget alert?") },
            text = { Text("Quiet Ledger needs notification permission only to show this on-device daily overspend alert. No financial data or notification text is shared.") },
            confirmButton = {
                TextButton(onClick = {
                    explainNotificationPermission = false
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Allow alert") }
            },
            dismissButton = { TextButton(onClick = { explainNotificationPermission = false }) { Text("Not now") } }
        )
    }
}

@Composable private fun BudgetLine(label: String, amount: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleSmall)
    }
    Spacer(Modifier.height(7.dp))
}

private fun paiseInput(value: String): Long? = value.trim().takeIf { it.isNotEmpty() }
    ?.replace(",", "")?.toBigDecimalOrNull()?.takeIf { it >= java.math.BigDecimal.ZERO }
    ?.movePointRight(2)?.toLong()
private fun rupeesInput(paise: Long): String = if (paise == 0L) "" else java.math.BigDecimal(paise).movePointLeft(2).stripTrailingZeros().toPlainString()

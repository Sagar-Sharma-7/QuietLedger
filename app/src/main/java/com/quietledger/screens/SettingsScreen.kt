package com.quietledger.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quietledger.models.Category
import com.quietledger.repository.FinanceRepository
import com.quietledger.ui.notificationAccessEnabled
import com.quietledger.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(repo: FinanceRepository, sources: () -> Unit, budget: () -> Unit, importStatement: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transactions by repo.all().collectAsState(initial = emptyList())
    val rules by repo.preferences.merchantRules.collectAsState(initial = emptyMap())
    val savedThemeMode by repo.preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM.name)
    val appLockEnabled by repo.preferences.appLockEnabled.collectAsState(initial = false)
    val themeMode = ThemeMode.fromStored(savedThemeMode)
    var confirmDeletion by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    OutputStreamWriter(output).use { writer ->
                        writer.appendLine("date,merchant,amount_rupees,type,category,source,status,notes")
                        transactions.forEach { transaction ->
                            writer.appendLine(
                                listOf(
                                    Instant.ofEpochMilli(transaction.timestamp)
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                    csv(transaction.merchant),
                                    transaction.amountPaise / 100.0,
                                    transaction.type,
                                    transaction.category.label,
                                    csv(transaction.sourceApp),
                                    transaction.status,
                                    csv(transaction.notes)
                                ).joinToString(",")
                            )
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Personal controls for a private ledger.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsSection("Appearance")
        SettingItem(
            title = "Theme",
            detail = themeMode.label,
            onClick = { showThemePicker = true }
        )

        SettingsSection("Security")
        ToggleSettingItem(
            title = "App lock",
            detail = "Use fingerprint or your phone screen lock",
            checked = appLockEnabled,
            onCheckedChange = { enabled ->
                scope.launch { repo.preferences.setAppLockEnabled(enabled) }
            }
        )
        Text(
            "Quiet Ledger never sees or stores your fingerprint, PIN, pattern, or password.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        SettingsSection("Automatic tracking")
        SettingItem(
            title = "Notification access",
            detail = if (notificationAccessEnabled(context)) "Enabled" else "Not enabled",
            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        )
        SettingItem("Financial sources", "Choose apps Quiet Ledger may analyze", sources)

        SettingsSection("Transactions")
        SettingItem("Import bank statement", "Review a local CSV; only new rows are imported", importStatement)

        SettingsSection("Budget")
        SettingItem("Monthly budget", "Income, savings goal, and local daily alert", budget)

        SettingsSection("Local rules")
        SettingItem(
            "Merchant rules",
            if (rules.isEmpty()) "No remembered corrections yet" else "${rules.size} remembered merchant categories",
            onClick = { showRules = true }
        )
        SettingItem(
            "Categories",
            "${Category.entries.size} built-in categories",
            onClick = { showCategories = true }
        )

        SettingsSection("Your data")
        Text(
            "Your financial data never leaves this device. Notification text is processed in memory and is not stored.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        SettingItem("Export CSV", "Create an offline file you choose") {
            exportLauncher.launch("quiet-ledger-export.csv")
        }
        SettingItem("Local backup", "Export a CSV for safe keeping") {
            exportLauncher.launch("quiet-ledger-backup.csv")
        }
        TextButton(onClick = { confirmDeletion = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete all financial data", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(28.dp))
    }

    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            title = { Text("Delete all financial data?") },
            text = { Text("All structured transactions will be permanently removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteAll() }
                    confirmDeletion = false
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeletion = false }) { Text("Cancel") } }
        )
    }
    if (showThemePicker) {
        ThemePicker(
            selected = themeMode,
            select = { selected ->
                scope.launch { repo.preferences.setThemeMode(selected.name) }
                showThemePicker = false
            },
            dismiss = { showThemePicker = false }
        )
    }
    if (showRules) {
        SimpleListDialog(
            title = "Merchant rules",
            rows = if (rules.isEmpty()) listOf("No merchant corrections saved yet.") else {
                rules.entries.sortedBy { it.key }.map { (merchant, category) ->
                    val label = Category.entries.firstOrNull { it.name == category }?.label ?: category
                    "$merchant · $label"
                }
            },
            dismiss = { showRules = false }
        )
    }
    if (showCategories) {
        SimpleListDialog("Categories", Category.entries.map { it.label }) { showCategories = false }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Spacer(Modifier.height(26.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SettingItem(title: String, detail: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Open $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ToggleSettingItem(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
    )
}

@Composable
private fun ThemePicker(selected: ThemeMode, select: (ThemeMode) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Choose theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = { select(option) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(option.label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SimpleListDialog(title: String, rows: List<String>, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = { Column { rows.forEach { Text(it, Modifier.padding(vertical = 5.dp)) } } },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } }
    )
}

private fun csv(value: String?): String = "\"${(value ?: "").replace("\"", "\"\"")}\""

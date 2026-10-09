package com.quietledger.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quietledger.models.SourceApp
import com.quietledger.repository.FinanceRepository
import kotlinx.coroutines.launch

@Composable
fun SourcePicker(
    repo: FinanceRepository,
    onboarding: Boolean = false,
    notificationReady: Boolean = true,
    onBack: () -> Unit,
    onFinish: () -> Unit = {}
) {
    val context = LocalContext.current
    val installedApps = remember { financeApps(context) }
    val selectedSources by repo.preferences.sources.collectAsState(initial = emptySet())
    var selected by remember(selectedSources) { mutableStateOf(selectedSources) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        if (!onboarding) {
            TextButton(onClick = onBack) { Text("‹ Settings") }
        }
        Text(
            if (onboarding) "Choose financial apps" else "Financial sources",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (notificationReady) "Only selected apps will be processed. Everything else is ignored."
            else "You can select sources now; enable notification access when ready.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (installedApps.isEmpty()) {
            Text("No launchable apps were available to list. Return after installing a financial app.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(installedApps, key = { it.packageName }) { app ->
                    SourceRow(
                        app = app,
                        checked = app.packageName in selected,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + app.packageName else selected - app.packageName
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    repo.preferences.setSources(selected)
                    if (onboarding) repo.preferences.setOnboardingDone()
                    onFinish()
                    if (!onboarding) onBack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (onboarding) "Finish" else "Save sources") }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun SourceRow(app: SourceApp, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                app.label,
                fontWeight = if (app.packageName in recommendedPackages) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val recommendedPackages = setOf(
    "com.google.android.apps.nbu.paisa.user",
    "com.phonepe.app",
    "net.one97.paytm",
    "com.google.android.apps.messaging"
)

private fun financeApps(context: Context): List<SourceApp> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(launcherIntent, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .map { appInfo ->
            SourceApp(
                packageName = appInfo.packageName,
                label = context.packageManager.getApplicationLabel(appInfo).toString(),
                selected = false
            )
        }
        .sortedWith(
            compareByDescending<SourceApp> { it.packageName in recommendedPackages }
                .thenBy { it.label.lowercase() }
        )
}

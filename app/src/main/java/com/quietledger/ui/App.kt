package com.quietledger.ui

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quietledger.notification.FinanceNotificationListener
import com.quietledger.repository.FinanceRepository
import com.quietledger.screens.HomeScreen
import com.quietledger.screens.BudgetScreen
import com.quietledger.screens.ManualTransactionScreen
import com.quietledger.screens.MonthOverviewScreen
import com.quietledger.screens.ReviewScreen
import com.quietledger.screens.SettingsScreen
import com.quietledger.screens.SourcePicker
import com.quietledger.screens.TransactionDetailScreen
import com.quietledger.screens.TransactionsScreen
import com.quietledger.screens.StatementImportScreen
import com.quietledger.ui.theme.QuietLedgerTheme
import com.quietledger.ui.theme.ThemeMode

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("home", "Home", Icons.Outlined.Home),
    Destination("transactions", "Transactions", Icons.Outlined.ReceiptLong),
    Destination("review", "Review", Icons.Outlined.FactCheck),
    Destination("settings", "Settings", Icons.Outlined.Settings)
)

@Composable
fun App() {
    val context = LocalContext.current
    val repo = remember { FinanceRepository.get(context) }
    val onboardingComplete by repo.preferences.onboardingDone.collectAsState(initial = false)
    val storedThemeMode by repo.preferences.themeMode.collectAsState(initial = "SYSTEM")
    val appLockEnabled by repo.preferences.appLockEnabled.collectAsState(initial = false)

    QuietLedgerTheme(ThemeMode.fromStored(storedThemeMode)) {
        if (onboardingComplete) {
            AppLockGate(enabled = appLockEnabled) { LedgerApp(repo) }
        } else {
            Onboarding(repo)
        }
    }
}

@Composable
private fun LedgerApp(repo: FinanceRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route in destinations.map { it.route }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    // Selecting any tab clears detail/subscreen history, including Home after See all.
                                    popUpTo(navController.graph.startDestinationId) { saveState = false }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            },
                            icon = { androidx.compose.material3.Icon(destination.icon, destination.label) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    repo = repo,
                    openTransactions = { navController.navigate("transactions") { launchSingleTop = true } },
                    add = { navController.navigate("add") }
                )
            }
            composable("transactions") {
                TransactionsScreen(
                    repo = repo,
                    open = { id -> navController.navigate("detail/$id") },
                    openMonth = { month -> navController.navigate("month/$month") },
                    add = { navController.navigate("add") }
                )
            }
            composable("review") {
                ReviewScreen(repo) { id -> navController.navigate("detail/$id") }
            }
            composable("settings") {
                SettingsScreen(repo, sources = { navController.navigate("sources") }, budget = { navController.navigate("budget") }, importStatement = { navController.navigate("import") })
            }
            composable("budget") { BudgetScreen(repo) { navController.popBackStack() } }
            composable("import") { StatementImportScreen(repo) { navController.popBackStack() } }
            composable("sources") {
                SourcePicker(repo = repo, onBack = { navController.popBackStack() })
            }
            composable("add") {
                ManualTransactionScreen(repo) { navController.popBackStack() }
            }
            composable(
                route = "month/{month}",
                arguments = listOf(navArgument("month") { type = NavType.StringType })
            ) { entry ->
                MonthOverviewScreen(
                    repo = repo,
                    monthKey = entry.arguments?.getString("month").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                TransactionDetailScreen(
                    repo = repo,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun Onboarding(repo: FinanceRepository) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val accessEnabled = remember(step) { notificationAccessEnabled(context) }

    when (step) {
        0 -> WelcomePage(
            title = "Track spending automatically",
            body = "Transactions are detected from payment and bank notifications.",
            action = "Continue",
            proceed = { step += 1 }
        )
        1 -> WelcomePage(
            title = "Private by design",
            body = "Everything stays on this device. Notification text is processed in memory and then discarded.",
            action = "Continue",
            proceed = { step += 1 }
        )
        2 -> WelcomePage(
            title = "Notification access",
            body = "Android notification access lets Quiet Ledger detect transactions from only the apps you approve.",
            action = "Enable notification access",
            proceed = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                step += 1
            }
        )
        else -> SourcePicker(
            repo = repo,
            onboarding = true,
            notificationReady = accessEnabled,
            onBack = {},
            onFinish = {}
        )
    }
}

@Composable
private fun WelcomePage(title: String, body: String, action: String, proceed: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Quiet Ledger", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(36.dp))
        Button(onClick = proceed, modifier = Modifier.fillMaxWidth()) { Text(action) }
    }
}

fun notificationAccessEnabled(context: Context): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java)
    return manager.isNotificationListenerAccessGranted(
        ComponentName(context, FinanceNotificationListener::class.java)
    )
}

package com.quietledger.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.quietledger.parser.Categorizer
import kotlinx.coroutines.flow.*

private val Context.dataStore by preferencesDataStore("quiet_ledger_preferences")
class AppPreferences(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val sources = stringSetPreferencesKey("approved_sources")
        val merchantRules = stringSetPreferencesKey("merchant_rules")
        val themeMode = stringPreferencesKey("theme_mode")
        val appLockEnabled = booleanPreferencesKey("app_lock_enabled")
        val monthlyIncomePaise = longPreferencesKey("monthly_income_paise")
        val monthlySavingsGoalPaise = longPreferencesKey("monthly_savings_goal_paise")
        val budgetAlertsEnabled = booleanPreferencesKey("budget_alerts_enabled")
        val lastOverspendAlertDate = stringPreferencesKey("last_overspend_alert_date")
        val ignoredMerchants = stringSetPreferencesKey("ignored_notification_merchants")
    }

    val onboardingDone = context.dataStore.data.map { it[Keys.onboarding] ?: false }
    val sources = context.dataStore.data.map { it[Keys.sources] ?: emptySet() }
    val themeMode = context.dataStore.data.map { it[Keys.themeMode] ?: "SYSTEM" }
    val appLockEnabled = context.dataStore.data.map { it[Keys.appLockEnabled] ?: false }
    val monthlyIncomePaise = context.dataStore.data.map { it[Keys.monthlyIncomePaise] ?: 0L }
    val monthlySavingsGoalPaise = context.dataStore.data.map { it[Keys.monthlySavingsGoalPaise] ?: 0L }
    val budgetAlertsEnabled = context.dataStore.data.map { it[Keys.budgetAlertsEnabled] ?: false }
    val lastOverspendAlertDate = context.dataStore.data.map { it[Keys.lastOverspendAlertDate] }
    val ignoredMerchants = context.dataStore.data.map { it[Keys.ignoredMerchants] ?: emptySet() }
    val merchantRules = context.dataStore.data.map { raw -> (raw[Keys.merchantRules] ?: emptySet()).mapNotNull { entry -> entry.split("|", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap() }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboarding] = true }
    suspend fun setSources(packages: Set<String>) = context.dataStore.edit { it[Keys.sources] = packages }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.themeMode] = mode }
    suspend fun setAppLockEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.appLockEnabled] = enabled }
    suspend fun setBudget(incomePaise: Long, savingsGoalPaise: Long) = context.dataStore.edit {
        it[Keys.monthlyIncomePaise] = incomePaise.coerceAtLeast(0L)
        it[Keys.monthlySavingsGoalPaise] = savingsGoalPaise.coerceAtLeast(0L)
    }
    suspend fun setBudgetAlertsEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.budgetAlertsEnabled] = enabled }
    suspend fun markOverspendAlertShown(date: String) = context.dataStore.edit { it[Keys.lastOverspendAlertDate] = date }
    suspend fun ignoreNotificationMerchant(merchant: String) = context.dataStore.edit { prefs ->
        Categorizer.normalizeMerchant(merchant)?.let { prefs[Keys.ignoredMerchants] = (prefs[Keys.ignoredMerchants] ?: emptySet()) + it }
    }
    suspend fun rememberMerchantCategory(merchant: String, category: String) = context.dataStore.edit { prefs ->
        val merchantKey = Categorizer.normalizeMerchant(merchant) ?: return@edit
        val old = prefs[Keys.merchantRules] ?: emptySet()
        prefs[Keys.merchantRules] = old.filterNot { it.substringBefore("|") == merchantKey }.toSet() + "$merchantKey|$category"
    }
}

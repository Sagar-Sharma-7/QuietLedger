package com.quietledger.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.quietledger.parser.TransactionParser
import com.quietledger.repository.FinanceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class FinanceNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onNotificationPosted(sbn: StatusBarNotification) { val extras = sbn.notification.extras; val title = extras.getCharSequence("android.title")?.toString(); val body = extras.getCharSequence("android.text")?.toString(); val packageName = sbn.packageName
        scope.launch { if (packageName !in FinanceRepository.get(applicationContext).preferences.sources.first()) return@launch; TransactionParser.parse(title, body)?.copy(timestamp = sbn.postTime)?.let { FinanceRepository.get(applicationContext).ingest(it, packageName) } }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

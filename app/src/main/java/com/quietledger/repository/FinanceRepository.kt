package com.quietledger.repository

import android.content.Context
import androidx.room.Room
import com.quietledger.data.AppPreferences
import com.quietledger.database.AppDatabase
import com.quietledger.database.TransactionEntity
import com.quietledger.models.Category
import com.quietledger.models.FinanceSummary
import com.quietledger.models.MonthlyBudget
import com.quietledger.models.ParsedTransaction
import com.quietledger.models.Period
import com.quietledger.models.TransactionStatus
import com.quietledger.models.TransactionType
import com.quietledger.parser.Categorizer
import com.quietledger.notification.BudgetAlertNotifier
import com.quietledger.statement.ImportMatch
import com.quietledger.statement.StatementImporter
import com.quietledger.statement.StatementRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class FinanceRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "quiet_ledger.db"
    ).addMigrations(AppDatabase.MIGRATION_1_2).build()
    private val dao = database.financeDao()

    val preferences = AppPreferences(appContext)

    fun transactions(
        period: Period,
        customRange: ClosedRange<Long>? = null
    ): Flow<List<TransactionEntity>> {
        val (start, end) = periodRange(period, customRange)
        return dao.observeRange(start, end)
    }

    fun all(): Flow<List<TransactionEntity>> = dao.observeAll()
    fun search(query: String): Flow<List<TransactionEntity>> = dao.search(query)
    fun transaction(id: Long): Flow<TransactionEntity?> = dao.observeById(id)

    suspend fun save(transaction: TransactionEntity) {
        if (transaction.id == 0L) dao.insert(transaction) else dao.update(transaction)
        if (transaction.type == TransactionType.DEBIT && transaction.status == TransactionStatus.COMPLETED) {
            notifyIfDailyGuideExceeded()
        }
    }

    suspend fun delete(transaction: TransactionEntity) = dao.delete(transaction)
    suspend fun deleteAll() = dao.deleteAll()

    suspend fun assignCategory(transaction: TransactionEntity, category: Category) {
        save(transaction.copy(category = category))
        transaction.merchant
            ?.takeIf { it.isNotBlank() }
            ?.let { preferences.rememberMerchantCategory(it, category.name) }
    }

    /** Saves structured data only; the original notification body is never persisted. */
    suspend fun ingest(parsed: ParsedTransaction, sourceApp: String) {
        if (parsed.type == TransactionType.REVERSED && parsed.reference != null) {
            val original = dao.findByReference(parsed.reference)
            if (original != null) {
                dao.update(original.copy(status = TransactionStatus.REVERSED))
                return
            }
        }

        // A reference ID is authoritative; otherwise require every available identity signal.
        // Similar amount and time alone must never hide separate repayments or transfers.
        val exactReferenceMatch = parsed.reference?.let { dao.findByReference(it) } != null
        val nearby = dao.findSimilar(parsed.amountPaise, parsed.type.name,
            parsed.timestamp - FIVE_MINUTES_MILLIS, parsed.timestamp + FIVE_MINUTES_MILLIS)
        val possibleDuplicate = exactReferenceMatch || nearby.any { existing ->
            strongDuplicateMatch(existing, parsed, sourceApp)
        }
        if (parsed.merchant?.let { Categorizer.normalizeMerchant(it) in preferences.ignoredMerchants.first() } == true) return
        val rememberedRules = preferences.merchantRules.first()

        save(
            TransactionEntity(
                amountPaise = parsed.amountPaise,
                type = parsed.type,
                merchant = parsed.merchant,
                category = Categorizer.categoryFor(parsed.merchant, parsed.type, rememberedRules),
                timestamp = parsed.timestamp,
                sourceApp = sourceApp,
                paymentMethod = parsed.paymentMethod,
                accountSuffix = parsed.accountSuffix,
                transactionReference = parsed.reference,
                status = parsed.status,
                isPossibleDuplicate = possibleDuplicate,
                needsReview = true,
                confidence = parsed.confidence
            )
        )
    }

    suspend fun previewStatement(csv: String): List<StatementRow> = StatementImporter.parse(csv, dao.allNow())

    suspend fun importStatement(rows: List<StatementRow>) {
        val rules = preferences.merchantRules.first()
        rows.filter { it.match != ImportMatch.EXACT_DUPLICATE && it.match != ImportMatch.INVALID }.forEach { row ->
            save(TransactionEntity(
                amountPaise = row.amountPaise, type = row.type, merchant = row.description,
                category = Categorizer.categoryFor(row.description, row.type, rules), timestamp = row.timestamp,
                sourceApp = "Bank statement", transactionReference = row.reference,
                isPossibleDuplicate = row.match == ImportMatch.POSSIBLE_DUPLICATE
            ))
        }
    }

    suspend fun approve(transaction: TransactionEntity) = save(transaction.copy(needsReview = false, isPossibleDuplicate = false))

    fun summary(items: List<TransactionEntity>): FinanceSummary {
        val included = items.filter {
            !it.isExcluded && !it.isPossibleDuplicate && !it.needsReview && it.status == TransactionStatus.COMPLETED
        }
        val spent = included.filter { it.type == TransactionType.DEBIT }.sumOf { it.amountPaise }
        val received = included
            .filter { it.type == TransactionType.CREDIT || it.type == TransactionType.REFUND }
            .sumOf { it.amountPaise }

        return FinanceSummary(
            spent = spent,
            received = received,
            net = received - spent,
            count = included.size,
            largestExpense = included.filter { it.type == TransactionType.DEBIT }
                .maxOfOrNull { it.amountPaise } ?: 0L,
            largestCredit = included.filter { it.type == TransactionType.CREDIT }
                .maxOfOrNull { it.amountPaise } ?: 0L
        )
    }

    fun monthlyBudget(items: List<TransactionEntity>, incomePaise: Long, savingsGoalPaise: Long): MonthlyBudget {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        val spent = items.asSequence()
            .filter { !it.isExcluded && !it.isPossibleDuplicate && !it.needsReview && it.status == TransactionStatus.COMPLETED && it.type == TransactionType.DEBIT }
            .sumOf { it.amountPaise }
        return MonthlyBudget(incomePaise, savingsGoalPaise, spent, java.time.temporal.ChronoUnit.DAYS.between(today, monthEnd).toInt() + 1)
    }

    private fun strongDuplicateMatch(existing: TransactionEntity, parsed: ParsedTransaction, sourceApp: String): Boolean {
        if (existing.type != parsed.type || existing.sourceApp != sourceApp) return false
        if (existing.accountSuffix != parsed.accountSuffix) return false
        val existingMerchant = normalizeIdentity(existing.merchant)
        val parsedMerchant = normalizeIdentity(parsed.merchant)
        return existingMerchant != null && parsedMerchant != null && existingMerchant == parsedMerchant
    }

    private fun normalizeIdentity(value: String?): String? = value
        ?.lowercase()
        ?.replace(Regex("[^a-z0-9]"), "")
        ?.takeIf { it.isNotBlank() }

    /** Checked locally after a confirmed transaction arrives; no notification content is retained. */
    private suspend fun notifyIfDailyGuideExceeded() {
        if (!preferences.budgetAlertsEnabled.first() || !BudgetAlertNotifier.canPost(appContext)) return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayKey = today.toString()
        if (preferences.lastOverspendAlertDate.first() == todayKey) return
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val monthTransactions = dao.inRange(monthStart, now)
        val budget = monthlyBudget(
            monthTransactions,
            preferences.monthlyIncomePaise.first(),
            preferences.monthlySavingsGoalPaise.first()
        )
        if (budget.spendablePaise <= 0L) return
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val spentToday = monthTransactions.asSequence()
            .filter { it.timestamp >= dayStart && !it.isExcluded && !it.isPossibleDuplicate && !it.needsReview && it.status == TransactionStatus.COMPLETED && it.type == TransactionType.DEBIT }
            .sumOf { it.amountPaise }
        if (spentToday > budget.dailyGuidePaise) {
            BudgetAlertNotifier.showOverspendWarning(appContext)
            preferences.markOverspendAlertShown(todayKey)
        }
    }

    private fun periodRange(period: Period, customRange: ClosedRange<Long>?): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        fun startOfDay(date: LocalDate): Long =
            date.atStartOfDay(zone).toInstant().toEpochMilli()

        val endOfToday = startOfDay(today.plusDays(1)) - 1L

        return when (period) {
            Period.TODAY -> startOfDay(today) to endOfToday
            Period.YESTERDAY -> startOfDay(today.minusDays(1)) to (startOfDay(today) - 1L)
            Period.THIS_WEEK -> {
                val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                startOfDay(monday) to endOfToday
            }
            Period.LAST_WEEK -> {
                val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                val lastMonday = thisMonday.minusWeeks(1)
                startOfDay(lastMonday) to (startOfDay(thisMonday) - 1L)
            }
            Period.THIS_MONTH -> startOfDay(today.withDayOfMonth(1)) to endOfToday
            Period.LAST_MONTH -> {
                val firstDay = today.withDayOfMonth(1).minusMonths(1)
                startOfDay(firstDay) to (startOfDay(firstDay.plusMonths(1)) - 1L)
            }
            Period.THIS_YEAR -> startOfDay(today.withDayOfYear(1)) to endOfToday
            Period.LAST_YEAR -> {
                val firstDay = today.withDayOfYear(1).minusYears(1)
                startOfDay(firstDay) to (startOfDay(firstDay.plusYears(1)) - 1L)
            }
            Period.ALL_TIME -> 0L to Long.MAX_VALUE
            Period.CUSTOM -> (customRange?.start ?: 0L) to
                (customRange?.endInclusive ?: Long.MAX_VALUE)
        }
    }

    companion object {
        const val FIVE_MINUTES_MILLIS = 5 * 60 * 1_000L
        @Volatile private var instance: FinanceRepository? = null

        fun get(context: Context): FinanceRepository = instance ?: synchronized(this) {
            instance ?: FinanceRepository(context).also { instance = it }
        }
    }
}

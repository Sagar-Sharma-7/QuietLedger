package com.quietledger.models

import java.time.Instant

enum class TransactionType { DEBIT, CREDIT, REFUND, TRANSFER, FAILED, REVERSED, PENDING }
enum class TransactionStatus { COMPLETED, FAILED, PENDING, REVERSED }
enum class Category(val label: String) {
    FOOD("Food & dining"), GROCERIES("Groceries"), TRANSPORT("Transport"), SHOPPING("Shopping"), BILLS("Bills & utilities"),
    SUBSCRIPTIONS("Subscriptions"), ENTERTAINMENT("Entertainment"), HEALTH("Health"), EDUCATION("Education"), TRAVEL("Travel"),
    RENT("Rent"), TRANSFERS("Transfers"), SALARY("Salary"), REFUNDS("Refunds"), GIFTS("Gifts"), INVESTMENT("Investment"), OTHER("Other")
}
enum class Period(val label: String) { TODAY("Today"), YESTERDAY("Yesterday"), THIS_WEEK("This week"), LAST_WEEK("Last week"), THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), LAST_YEAR("Last year"), ALL_TIME("All time"), CUSTOM("Custom") }

data class ParsedTransaction(val amountPaise: Long, val type: TransactionType, val merchant: String?, val timestamp: Long = Instant.now().toEpochMilli(), val paymentMethod: String? = null, val accountSuffix: String? = null, val reference: String? = null, val confidence: Int, val status: TransactionStatus = TransactionStatus.COMPLETED)
data class SourceApp(val packageName: String, val label: String, val selected: Boolean)
data class FinanceSummary(val spent: Long = 0, val received: Long = 0, val net: Long = 0, val count: Int = 0, val largestExpense: Long = 0, val largestCredit: Long = 0)
data class MonthlyBudget(
    val incomePaise: Long,
    val savingsGoalPaise: Long,
    val spentPaise: Long,
    val daysRemaining: Int
) {
    val spendablePaise: Long get() = (incomePaise - savingsGoalPaise).coerceAtLeast(0L)
    val remainingPaise: Long get() = (spendablePaise - spentPaise).coerceAtLeast(0L)
    val dailyGuidePaise: Long get() = if (daysRemaining > 0) remainingPaise / daysRemaining else remainingPaise
    val usedPercent: Float get() = if (spendablePaise > 0L) (spentPaise.toDouble() / spendablePaise * 100.0).toFloat().coerceAtMost(100f) else 0f
}

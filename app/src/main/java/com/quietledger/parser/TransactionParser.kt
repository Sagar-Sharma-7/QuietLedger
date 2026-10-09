package com.quietledger.parser

import com.quietledger.models.ParsedTransaction
import com.quietledger.models.TransactionStatus
import com.quietledger.models.TransactionType
import java.util.Locale

/**
 * Local, in-memory parser. It is intentionally conservative: a message with an
 * amount is not a transaction unless it also contains a confirmed financial-event signal.
 */
object TransactionParser {
    private val amount = Regex(
        "(?:₹|rs\\.?|inr)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )
    private val account = Regex(
        "(?:a/c|account|card)(?:\\s*(?:no)?\\.?)?\\s*(?:xx|x|\\*)*\\s*(\\d{4})",
        RegexOption.IGNORE_CASE
    )
    private val reference = Regex(
        "(?:ref(?:erence)?|utr|txn(?: id)?)[\\s:#-]*([A-Za-z0-9-]{6,})",
        RegexOption.IGNORE_CASE
    )
    private val merchantPatterns = listOf(
        Regex("(?:paid to|to|at|for|from|received from)\\s+([A-Za-z][A-Za-z0-9 .&'-]{1,40})", RegexOption.IGNORE_CASE),
        Regex("upi[- ](?:dr|cr)\\s+([A-Za-z0-9 .&'-]{2,40})", RegexOption.IGNORE_CASE)
    )
    private val otpOrAuthentication = Regex(
        "\\b(otp|one[ -]?time password|verification code|login code|authentication code|pin)\\b",
        RegexOption.IGNORE_CASE
    )
    private val futureOrScheduledDebit = Regex(
        "\\b(will|would|shall|scheduled|upcoming|expected|set)\\s+(?:be\\s+)?(?:auto[- ]?)?(debit(?:ed)?|deduct(?:ed)?|charg(?:e|ed)|withdraw(?:n)?)\\b" +
            "|\\b(will|scheduled|upcoming|expected|set)\\b.{0,45}\\b(debit(?:ed)?|deduct(?:ed)?|charg(?:e|ed)|withdraw(?:n)?|paid|pay(?:ment)?)\\b" +
            "|\\bauto[- ]?pay\\b.*\\b(due|scheduled|upcoming|reminder|will)\\b" +
            "|\\b(payment|debit|charge)\\s+(?:is\\s+)?(?:due|scheduled|upcoming)\\b",
        RegexOption.IGNORE_CASE
    )
    private val reminderOrFuturePayment = Regex(
        "\\b(autopay|auto[- ]?pay|subscription|sip|mandate|recharge|bill)\\b.{0,80}\\b" +
            "(will|due|scheduled|upcoming|reminder|process(?:ed|ing)?|deduct(?:ed|ion)?|debit(?:ed)?|paid|pay(?:ment)?)\\b" +
            "|\\b(will|due|scheduled|upcoming|reminder)\\b.{0,80}\\b" +
            "(autopay|auto[- ]?pay|subscription|sip|mandate|recharge|bill)\\b",
        RegexOption.IGNORE_CASE
    )
    private val informationalMessage = Regex(
        "\\b(plan|validity|expired|expires|expiry|offer|recharge reminder|bill reminder|due date|renewal reminder)\\b",
        RegexOption.IGNORE_CASE
    )

    fun parse(title: String?, body: String?): ParsedTransaction? {
        val text = listOfNotNull(title, body)
            .joinToString(" ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (text.isBlank() || otpOrAuthentication.containsMatchIn(text)) return null

        val lower = text.lowercase(Locale.ROOT)
        // Future AutoPay notices, bill/recharge reminders and plan expiry messages are not transactions.
        if (futureOrScheduledDebit.containsMatchIn(lower) ||
            (reminderOrFuturePayment.containsMatchIn(lower) && !hasPastTenseConfirmation(lower))
        ) return null

        val amountMatch = amount.find(text) ?: return null
        val amountPaise = amountMatch.groupValues[1]
            .replace(",", "")
            .toBigDecimalOrNull()
            ?.movePointRight(2)
            ?.toLong()
            ?: return null
        if (amountPaise <= 0L) return null

        val event = classifyConfirmedEvent(lower) ?: return null
        // Pending and failed attempts are useful bank notices but are not completed money movement.
        if (event.status != TransactionStatus.COMPLETED && event.status != TransactionStatus.REVERSED) return null
        // Plan/recharge copy needs a direct confirmed payment event; an amount alone never qualifies.
        if (informationalMessage.containsMatchIn(lower) && event.confidenceBoost < 20) return null

        val merchant = merchantPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', ',')
        }
        val confidence = (if (merchant != null) 78 else 62) + event.confidenceBoost

        return ParsedTransaction(
            amountPaise = amountPaise,
            type = event.type,
            merchant = merchant,
            paymentMethod = paymentMethod(lower),
            accountSuffix = account.find(text)?.groupValues?.getOrNull(1),
            reference = reference.find(text)?.groupValues?.getOrNull(1),
            confidence = confidence.coerceAtMost(99),
            status = event.status
        )
    }

    private fun classifyConfirmedEvent(text: String): ConfirmedEvent? = when {
        Regex("\\b(payment\\s+)?(failed|declined|unsuccessful)\\b").containsMatchIn(text) ->
            ConfirmedEvent(TransactionType.FAILED, TransactionStatus.FAILED, 25)
        Regex("\\b(refund|refunded)\\b").containsMatchIn(text) ->
            ConfirmedEvent(TransactionType.REFUND, TransactionStatus.COMPLETED, 25)
        Regex("\\b(reversed|reversal)\\b").containsMatchIn(text) ->
            ConfirmedEvent(TransactionType.REVERSED, TransactionStatus.REVERSED, 25)
        Regex("\\b(payment\\s+)?(pending|processing)\\b").containsMatchIn(text) ->
            ConfirmedEvent(TransactionType.PENDING, TransactionStatus.PENDING, 20)
        Regex("\\b(credited|received|deposited)\\b|\\bsalary\\s+(?:credited|received)\\b").containsMatchIn(text) ->
            ConfirmedEvent(TransactionType.CREDIT, TransactionStatus.COMPLETED, 25)
        Regex("\\b(self transfer|own account transfer|transfer completed)\\b").containsMatchIn(text) ->
            ConfirmedEvent(TransactionType.TRANSFER, TransactionStatus.COMPLETED, 20)
        Regex("\\b(debited|paid|spent|withdrawn|deducted|charged)\\b|\\bpayment\\s+(?:successful|completed|made)\\b|\\bupi[- ]?dr\\b").containsMatchIn(text) ->
            ConfirmedEvent(TransactionType.DEBIT, TransactionStatus.COMPLETED, 25)
        else -> null
    }

    /** Scheduled-payment vocabulary only becomes a ledger entry after an unambiguous completed event. */
    private fun hasPastTenseConfirmation(text: String): Boolean = Regex(
        "\\b(been\\s+)?(paid|debited|credited|refunded|reversed|received|deducted|charged)\\b" +
            "|\\b(payment|sip|autopay)\\s+(?:was\\s+)?(?:successful|completed)\\b"
    ).containsMatchIn(text)

    private fun paymentMethod(text: String): String? = when {
        "upi" in text -> "UPI"
        "card" in text -> "Card"
        "cash" in text -> "Cash"
        "neft" in text -> "NEFT"
        "imps" in text -> "IMPS"
        else -> null
    }

    private data class ConfirmedEvent(
        val type: TransactionType,
        val status: TransactionStatus,
        val confidenceBoost: Int
    )
}

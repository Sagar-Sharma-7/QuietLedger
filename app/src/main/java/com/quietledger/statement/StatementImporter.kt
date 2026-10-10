package com.quietledger.statement

import com.quietledger.database.TransactionEntity
import com.quietledger.models.TransactionType
import com.quietledger.parser.Categorizer
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ImportMatch { NEW, EXACT_DUPLICATE, POSSIBLE_DUPLICATE, INVALID }
data class StatementRow(
    val description: String,
    val amountPaise: Long,
    val type: TransactionType,
    val timestamp: Long,
    val reference: String? = null,
    val match: ImportMatch = ImportMatch.NEW
)

/** In-memory CSV reader. It never persists the statement's raw text or file. */
object StatementImporter {
    fun parse(csv: String, existing: List<TransactionEntity>): List<StatementRow> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()
        val headers = cells(lines.first()).map { it.lowercase().replace(Regex("[^a-z]"), "") }
        fun column(vararg names: String) = names.firstNotNullOfOrNull { name -> headers.indexOfFirst { it == name || it.contains(name) }.takeIf { it >= 0 } }
        val date = column("date", "transactiondate", "valuedate") ?: return emptyList()
        val description = column("description", "narration", "particulars", "merchant", "remarks") ?: return emptyList()
        val debit = column("debit", "withdrawal", "dr")
        val credit = column("credit", "deposit", "cr")
        val amount = column("amount", "transactionamount")
        val reference = column("reference", "utr", "transactionid", "txn")
        return lines.drop(1).mapNotNull { line ->
            val row = cells(line)
            fun at(index: Int?) = index?.let { row.getOrNull(it)?.trim().orEmpty() }.orEmpty()
            val debitAmount = paise(at(debit)); val creditAmount = paise(at(credit)); val flatAmount = paise(at(amount))
            val type = when { debitAmount > 0 -> TransactionType.DEBIT; creditAmount > 0 -> TransactionType.CREDIT; flatAmount > 0 && at(amount).trimStart().startsWith("-") -> TransactionType.DEBIT; flatAmount > 0 -> TransactionType.CREDIT; else -> return@mapNotNull null }
            val value = if (debitAmount > 0) debitAmount else if (creditAmount > 0) creditAmount else flatAmount
            val time = parseDate(at(date)) ?: return@mapNotNull null
            val ref = at(reference).takeIf { it.length >= 5 }
            val candidate = StatementRow(at(description).ifBlank { "Statement transaction" }, value, type, time, ref)
            candidate.copy(match = match(candidate, existing))
        }
    }

    private fun match(row: StatementRow, existing: List<TransactionEntity>): ImportMatch {
        if (row.reference != null && existing.any { it.transactionReference == row.reference }) return ImportMatch.EXACT_DUPLICATE
        val name = Categorizer.normalizeMerchant(row.description)
        val likely = existing.any { item ->
            item.type == row.type && item.amountPaise == row.amountPaise &&
                kotlin.math.abs(item.timestamp - row.timestamp) <= 2 * 86_400_000L &&
                name != null && Categorizer.normalizeMerchant(item.merchant)?.let { it.contains(name) || name.contains(it) } == true
        }
        return if (likely) ImportMatch.POSSIBLE_DUPLICATE else ImportMatch.NEW
    }

    private fun cells(line: String): List<String> = Regex(",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)").split(line).map { it.trim().trim('"') }
    private fun paise(value: String): Long = value.replace(",", "").replace(Regex("[^0-9.-]"), "").toBigDecimalOrNull()?.abs()?.movePointRight(2)?.toLong() ?: 0L
    private fun parseDate(value: String): Long? = listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd MMM yyyy").firstNotNullOfOrNull { pattern ->
        runCatching { LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern(pattern)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    }
}

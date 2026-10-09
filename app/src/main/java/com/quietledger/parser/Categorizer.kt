package com.quietledger.parser

import com.quietledger.models.*
import java.util.Locale

object Categorizer {
    private val rules = mapOf(Category.TRANSPORT to listOf("uber", "ola", "rapido", "metro"), Category.GROCERIES to listOf("blinkit", "zepto", "bigbasket", "instamart"), Category.FOOD to listOf("swiggy", "zomato", "starbucks", "restaurant"), Category.SUBSCRIPTIONS to listOf("netflix", "spotify", "youtube premium", "prime"), Category.BILLS to listOf("electricity", "recharge", "broadband"), Category.SALARY to listOf("salary"), Category.RENT to listOf("rent"), Category.REFUNDS to listOf("refund"))
    fun categoryFor(merchant: String?, type: TransactionType, remembered: Map<String, String> = emptyMap()): Category {
        if (type == TransactionType.TRANSFER) return Category.TRANSFERS
        if (type == TransactionType.REFUND) return Category.REFUNDS
        val normalized = normalizeMerchant(merchant) ?: return Category.OTHER
        remembered[normalized]?.let { return runCatching { Category.valueOf(it) }.getOrDefault(Category.OTHER) }
        // Merchant names can vary slightly between bank and UPI notifications.
        remembered.entries.firstOrNull { (savedMerchant, _) ->
            savedMerchant.length >= 5 && (normalized.contains(savedMerchant) || savedMerchant.contains(normalized))
        }?.value?.let { return runCatching { Category.valueOf(it) }.getOrDefault(Category.OTHER) }
        return rules.entries.firstOrNull { (_, terms) -> terms.any(normalized::contains) }?.key ?: Category.OTHER
    }

    fun normalizeMerchant(merchant: String?): String? = merchant
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex("[^a-z0-9]+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

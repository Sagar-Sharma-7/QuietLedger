package com.quietledger.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quietledger.models.Category
import com.quietledger.models.TransactionStatus
import com.quietledger.models.TransactionType

@Entity(tableName = "transactions", indices = [Index("timestamp"), Index("merchant")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long, val type: TransactionType, val merchant: String?, val category: Category,
    val timestamp: Long, val sourceApp: String, val paymentMethod: String? = null, val accountSuffix: String? = null,
    val transactionReference: String? = null, val status: TransactionStatus = TransactionStatus.COMPLETED,
    val isExcluded: Boolean = false, val isPossibleDuplicate: Boolean = false,
    /** A notification candidate is structured only; its original notification text is never stored. */
    val needsReview: Boolean = false, val notes: String? = null,
    val confidence: Int = 100, val createdAt: Long = System.currentTimeMillis()
)

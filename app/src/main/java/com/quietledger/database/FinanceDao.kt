package com.quietledger.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface FinanceDao {
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC") fun observeRange(from: Long, to: Long): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC") fun observeAll(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE id = :id") fun observeById(id: Long): Flow<TransactionEntity?>
    @Query("SELECT * FROM transactions WHERE (merchant LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' OR CAST(amountPaise / 100 AS TEXT) LIKE '%' || :query || '%') ORDER BY timestamp DESC") fun search(query: String): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE amountPaise = :amount AND type = :type AND timestamp BETWEEN :from AND :to") suspend fun findSimilar(amount: Long, type: String, from: Long, to: Long): List<TransactionEntity>
    @Query("SELECT * FROM transactions WHERE transactionReference = :reference ORDER BY timestamp DESC LIMIT 1") suspend fun findByReference(reference: String): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC") suspend fun inRange(from: Long, to: Long): List<TransactionEntity>
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC") suspend fun allNow(): List<TransactionEntity>
    @Insert suspend fun insert(transaction: TransactionEntity): Long
    @Update suspend fun update(transaction: TransactionEntity)
    @Delete suspend fun delete(transaction: TransactionEntity)
    @Query("DELETE FROM transactions") suspend fun deleteAll()
}

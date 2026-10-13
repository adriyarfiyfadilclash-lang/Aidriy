package com.aidriy.app.data.local

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Runs a block atomically: either every write inside it commits, or none do.
 * Abstracted so repositories don't depend on RoomDatabase directly.
 */
interface TransactionRunner {
    suspend fun <R> inTransaction(block: suspend () -> R): R
}

class RoomTransactionRunner(private val database: RoomDatabase) : TransactionRunner {
    override suspend fun <R> inTransaction(block: suspend () -> R): R =
        database.withTransaction(block)
}

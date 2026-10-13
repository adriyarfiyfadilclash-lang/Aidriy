package com.aidriy.app.data.repository

import android.database.sqlite.SQLiteException
import android.util.Log
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import kotlinx.coroutines.CancellationException

/**
 * Converts database exceptions into [DataError.Storage] instead of crashing or swallowing them.
 * Only the exception type is logged: record contents and SQL arguments never reach the log.
 */
internal suspend fun <T> storageCall(
    logTag: String,
    block: suspend () -> DataResult<T>,
): DataResult<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: SQLiteException) {
    Log.e(logTag, "Database operation failed: ${e.javaClass.simpleName}")
    DataResult.Failure(DataError.Storage(e))
} catch (e: IllegalStateException) {
    // Room throws this for misuse such as a closed database.
    Log.e(logTag, "Database state error: ${e.javaClass.simpleName}")
    DataResult.Failure(DataError.Storage(e))
}

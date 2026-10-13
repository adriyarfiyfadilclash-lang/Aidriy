package com.aidriy.app.domain

import java.time.LocalDate

/**
 * Result convention for every repository write.
 * A [Failure] always carries a typed [DataError]; nothing is swallowed, and callers
 * must handle both branches before telling the user that data was saved.
 */
sealed interface DataResult<out T> {
    data class Success<out T>(val value: T) : DataResult<T>
    data class Failure(val error: DataError) : DataResult<Nothing>
}

sealed interface DataError {
    /** A note needs at least a title or some content. */
    data object EmptyNote : DataError

    data object EmptyIdeaTitle : DataError

    data object EmptyJournal : DataError

    data class TitleTooLong(val max: Int) : DataError

    data class ContentTooLong(val max: Int) : DataError

    data object FutureJournalDate : DataError

    /** Another entry already exists for [date]; [existingId] lets the UI open it. */
    data class DuplicateJournalDate(val date: LocalDate, val existingId: String) : DataError

    data class InvalidTag(val problem: TagProblem) : DataError

    data object InvalidIdeaStatus : DataError

    /** The id is malformed, or the record no longer exists (for example, deleted elsewhere). */
    data object NotFound : DataError

    /** The database rejected or failed the operation. */
    data class Storage(val cause: Throwable) : DataError
}

enum class TagProblem { EMPTY, TOO_LONG, TOO_MANY }

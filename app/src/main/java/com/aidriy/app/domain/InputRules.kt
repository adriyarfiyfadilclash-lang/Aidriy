package com.aidriy.app.domain

import java.time.LocalDate
import java.util.Locale

/** Validation shared by repositories (and reusable by UI for early feedback). Pure Kotlin. */
object InputRules {
    const val TITLE_MAX = 200
    const val CONTENT_MAX = 100_000
    const val ID_MAX = 64

    fun validateNote(title: String, content: String): DataError? = when {
        title.isBlank() && content.isBlank() -> DataError.EmptyNote
        title.trim().length > TITLE_MAX -> DataError.TitleTooLong(TITLE_MAX)
        content.length > CONTENT_MAX -> DataError.ContentTooLong(CONTENT_MAX)
        else -> null
    }

    fun validateIdea(title: String, content: String): DataError? = when {
        title.isBlank() -> DataError.EmptyIdeaTitle
        title.trim().length > TITLE_MAX -> DataError.TitleTooLong(TITLE_MAX)
        content.length > CONTENT_MAX -> DataError.ContentTooLong(CONTENT_MAX)
        else -> null
    }

    /** [today] must be the user's local calendar date, never a UTC-derived one. */
    fun validateJournal(date: LocalDate, today: LocalDate, title: String, content: String): DataError? = when {
        date.isAfter(today) -> DataError.FutureJournalDate
        title.isBlank() && content.isBlank() -> DataError.EmptyJournal
        title.trim().length > TITLE_MAX -> DataError.TitleTooLong(TITLE_MAX)
        content.length > CONTENT_MAX -> DataError.ContentTooLong(CONTENT_MAX)
        else -> null
    }

    fun isValidId(id: String?): Boolean = !id.isNullOrBlank() && id.length <= ID_MAX
}

/** Tag name cleaning and duplicate detection. "#Kerja ", "kerja" and "KERJA" are the same tag. */
object TagNames {
    const val MAX_LENGTH = 30
    const val MAX_PER_RECORD = 10

    private val whitespace = Regex("\\s+")

    /** Display form: trimmed, leading '#' removed, inner whitespace collapsed. */
    fun clean(raw: String): String = raw.trim().removePrefix("#").trim().replace(whitespace, " ")

    /** Key used for uniqueness. */
    fun normalize(raw: String): String = clean(raw).lowercase(Locale.ROOT)

    fun validate(raw: String): TagProblem? {
        val cleaned = clean(raw)
        return when {
            cleaned.isEmpty() -> TagProblem.EMPTY
            cleaned.length > MAX_LENGTH -> TagProblem.TOO_LONG
            else -> null
        }
    }

    /** Validates, cleans and de-duplicates a list of names, keeping first-seen order. */
    fun cleanList(names: List<String>): DataResult<List<String>> {
        val result = LinkedHashMap<String, String>()
        for (name in names) {
            validate(name)?.let { return DataResult.Failure(DataError.InvalidTag(it)) }
            result.putIfAbsent(normalize(name), clean(name))
        }
        if (result.size > MAX_PER_RECORD) {
            return DataResult.Failure(DataError.InvalidTag(TagProblem.TOO_MANY))
        }
        return DataResult.Success(result.values.toList())
    }
}

/** Search input handling. Patterns are always bound as query parameters, never concatenated into SQL. */
object SearchQuery {
    /** Escape character used in every LIKE clause (`ESCAPE '!'`). */
    const val ESCAPE = '!'

    private val whitespace = Regex("\\s+")

    fun normalize(raw: String): String = raw.trim().replace(whitespace, " ")

    /** Builds a "contains" LIKE pattern; `%`, `_` and `!` in user input are matched literally. */
    fun toLikePattern(raw: String): String {
        val escaped = buildString {
            for (c in normalize(raw)) {
                if (c == ESCAPE || c == '%' || c == '_') append(ESCAPE)
                append(c)
            }
        }
        return "%$escaped%"
    }
}

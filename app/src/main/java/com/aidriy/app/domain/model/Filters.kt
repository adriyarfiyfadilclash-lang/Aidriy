package com.aidriy.app.domain.model

enum class SortOrder { UPDATED, CREATED }

/** Which notes a list shows. Archived notes never appear in [ACTIVE] or [FAVORITES]. */
enum class NoteScope { ACTIVE, FAVORITES, ARCHIVED }

data class NoteFilter(
    val scope: NoteScope = NoteScope.ACTIVE,
    val query: String = "",
    val tagId: String? = null,
    val sort: SortOrder = SortOrder.UPDATED,
)

data class IdeaFilter(
    /** null shows every status. */
    val status: IdeaStatus? = null,
    val query: String = "",
    val tagId: String? = null,
    val sort: SortOrder = SortOrder.UPDATED,
)

data class JournalFilter(
    val query: String = "",
    val tagId: String? = null,
)

enum class TaggableKind { NOTE, IDEA, JOURNAL }

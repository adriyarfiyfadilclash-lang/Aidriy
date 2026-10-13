package com.aidriy.app.domain.model

/** User input for creating or editing a record. Repositories validate drafts before writing. */
data class NoteDraft(
    val title: String,
    val content: String,
    val tagNames: List<String> = emptyList(),
)

data class IdeaDraft(
    val title: String,
    val content: String = "",
    val status: IdeaStatus = IdeaStatus.INBOX,
    val tagNames: List<String> = emptyList(),
)

data class JournalDraft(
    val title: String,
    val content: String,
    val tagNames: List<String> = emptyList(),
)

data class SearchResults(
    val notes: List<Note>,
    val ideas: List<Idea>,
    val journalEntries: List<JournalEntry>,
) {
    val isEmpty: Boolean get() = notes.isEmpty() && ideas.isEmpty() && journalEntries.isEmpty()

    companion object {
        val EMPTY = SearchResults(emptyList(), emptyList(), emptyList())
    }
}

data class RecentItems(
    val notes: List<Note>,
    val ideas: List<Idea>,
    val journalEntries: List<JournalEntry>,
) {
    val isEmpty: Boolean get() = notes.isEmpty() && ideas.isEmpty() && journalEntries.isEmpty()

    companion object {
        val EMPTY = RecentItems(emptyList(), emptyList(), emptyList())
    }
}

package com.aidriy.app.ui.navigation

import java.time.LocalDate

/** Non-tab destinations. Record ids are UUIDs and dates are ISO, so both are URL-safe. */
object Routes {
    const val ARG_NOTE_ID = "noteId"
    const val ARG_IDEA_ID = "ideaId"
    const val ARG_DATE = "date"

    const val NOTE_EDITOR = "notes/editor?$ARG_NOTE_ID={$ARG_NOTE_ID}"
    const val IDEA_EDITOR = "ideas/editor?$ARG_IDEA_ID={$ARG_IDEA_ID}"
    const val JOURNAL_EDITOR = "journal/editor/{$ARG_DATE}"
    const val SEARCH = "search"

    /** null opens an empty editor for a new note. */
    fun noteEditor(noteId: String? = null): String =
        if (noteId == null) "notes/editor" else "notes/editor?$ARG_NOTE_ID=$noteId"

    fun ideaEditor(ideaId: String? = null): String =
        if (ideaId == null) "ideas/editor" else "ideas/editor?$ARG_IDEA_ID=$ideaId"

    fun journalEditor(date: LocalDate): String = "journal/editor/$date"
}

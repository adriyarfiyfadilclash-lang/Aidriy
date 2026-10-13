package com.aidriy.app.ui.common

enum class EditorStatus {
    /** Reading the record from the database. */
    Loading,

    /** Editable. */
    Ready,

    /** The id is invalid or the record was deleted. Saving is disabled. */
    NotFound,

    /** Reading failed. Saving is disabled so stored data can't be overwritten with empty text. */
    LoadFailed,
}

/** One-shot effects from editor ViewModels. */
sealed interface EditorEvent {
    data class Message(val text: UiText) : EditorEvent

    /** Leave the editor (after a confirmed save, a delete, or discarding). */
    data object Close : EditorEvent

    /** Navigate to a note, e.g. one created from an idea. */
    data class OpenNote(val noteId: String) : EditorEvent
}

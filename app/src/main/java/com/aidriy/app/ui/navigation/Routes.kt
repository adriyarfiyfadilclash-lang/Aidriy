package com.aidriy.app.ui.navigation

import com.aidriy.app.domain.model.FileKind
import com.aidriy.app.domain.model.NoteScope
import java.time.LocalDate

/** Non-tab destinations. Record ids are UUIDs and dates are ISO, so both are URL-safe. */
object Routes {
    const val ARG_NOTE_ID = "noteId"
    const val ARG_IDEA_ID = "ideaId"
    const val ARG_DATE = "date"
    const val ARG_TEMPLATE = "template"
    const val ARG_PREFILL = "prefill"
    const val ARG_SCOPE = "scope"
    const val ARG_FOLDER_ID = "folderId"
    const val ARG_KIND = "kind"
    const val ARG_FILE_ID = "fileId"
    const val ARG_PROMPT = "prompt"
    const val ARG_CONVERSATION = "conversation"

    const val NOTES_LIST = "notes/list?$ARG_SCOPE={$ARG_SCOPE}"
    const val NOTE_EDITOR = "notes/editor?$ARG_NOTE_ID={$ARG_NOTE_ID}&$ARG_TEMPLATE={$ARG_TEMPLATE}&$ARG_PREFILL={$ARG_PREFILL}"
    const val IDEAS = "ideas"
    const val IDEA_EDITOR = "ideas/editor?$ARG_IDEA_ID={$ARG_IDEA_ID}"
    const val JOURNAL = "journal"
    const val JOURNAL_EDITOR = "journal/editor/{$ARG_DATE}"
    const val SEARCH = "search"
    const val TEMPLATES = "templates"
    const val SCAN = "scan"
    const val AI_TOOLS = "ai/tools"
    const val AI_HISTORY = "ai/history"
    const val FILES_FOLDER = "files/folder?$ARG_FOLDER_ID={$ARG_FOLDER_ID}&$ARG_KIND={$ARG_KIND}"
    const val FILE_VIEWER = "files/view/{$ARG_FILE_ID}"
    const val SETTINGS_PROFILE = "settings/profile"
    const val SETTINGS_ACCOUNT = "settings/account"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_NOTES = "settings/notes"
    const val SETTINGS_FILES = "settings/files"
    const val SETTINGS_SECURITY = "settings/security"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_ABOUT = "settings/about"

    fun notesList(scope: NoteScope = NoteScope.ACTIVE) = "notes/list?$ARG_SCOPE=${scope.name}"

    /** All arguments optional: no id = new note; [template] = template id; [prefill] = PrefillStore key. */
    fun noteEditor(noteId: String? = null, template: String? = null, prefill: String? = null): String {
        val params = listOfNotNull(
            noteId?.let { "$ARG_NOTE_ID=$it" },
            template?.let { "$ARG_TEMPLATE=$it" },
            prefill?.let { "$ARG_PREFILL=$it" },
        )
        return if (params.isEmpty()) "notes/editor" else "notes/editor?" + params.joinToString("&")
    }

    fun ideaEditor(ideaId: String? = null): String =
        if (ideaId == null) "ideas/editor" else "ideas/editor?$ARG_IDEA_ID=$ideaId"

    fun journalEditor(date: LocalDate): String = "journal/editor/$date"

    /** A user folder (by id) or a category (by kind); both null shows every file. */
    fun filesFolder(folderId: String? = null, kind: FileKind? = null): String {
        val params = listOfNotNull(folderId?.let { "$ARG_FOLDER_ID=$it" }, kind?.let { "$ARG_KIND=${it.name}" })
        return if (params.isEmpty()) "files/folder" else "files/folder?" + params.joinToString("&")
    }

    fun fileViewer(fileId: String) = "files/view/$fileId"
}

/**
 * Hands larger text (dictation, OCR results, AI answers) to the note editor without putting it
 * in a navigation URL. The editor copies it into its saved state on first open.
 */
object PrefillStore {
    data class Prefill(val title: String, val content: String)

    private val items = java.util.concurrent.ConcurrentHashMap<String, Prefill>()

    fun put(title: String, content: String): String {
        val key = java.util.UUID.randomUUID().toString()
        items[key] = Prefill(title, content)
        return key
    }

    fun take(key: String): Prefill? = items.remove(key)
}

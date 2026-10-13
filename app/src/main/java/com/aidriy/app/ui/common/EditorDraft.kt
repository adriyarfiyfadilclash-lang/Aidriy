package com.aidriy.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.TagNames
import com.aidriy.app.domain.TagProblem

/**
 * Editable text of an editor (title, content, tags, plus one optional extra field such as
 * an idea's status), held in Compose state for responsive typing and mirrored into
 * [SavedStateHandle] so unsaved edits survive configuration changes and process death.
 *
 * Unsaved-change detection compares against a hash of the last saved values, so the saved
 * state never stores the full text twice.
 *
 * Content longer than [MAX_SAVED_CONTENT] chars is not mirrored (Android's saved-state
 * Binder limit). It still survives rotation; after process death [contentWasDropped] is true
 * and the ViewModel must reload the stored record instead of trusting the empty restored text.
 */
class EditorDraft(private val handle: SavedStateHandle) {

    var title by mutableStateOf(handle.get<String>(KEY_TITLE).orEmpty())
        private set

    var content by mutableStateOf(handle.get<String>(KEY_CONTENT).orEmpty())
        private set

    var tags by mutableStateOf(handle.get<ArrayList<String>>(KEY_TAGS)?.toList().orEmpty())
        private set

    /** Free-form extra field (idea status key); included in change detection. */
    var extra by mutableStateOf(handle.get<String>(KEY_EXTRA).orEmpty())
        private set

    private var baselineHash by mutableStateOf(handle.get<Int>(KEY_BASELINE) ?: emptyHash())

    /** True once the editor was filled from the database or started as a new record. */
    val isInitialized: Boolean get() = handle.get<Boolean>(KEY_INITIALIZED) == true

    val hasUnsavedChanges: Boolean get() = currentHash() != baselineHash

    /** True when the last content was too long to mirror into saved state. */
    val contentWasDropped: Boolean get() = handle.get<Boolean>(KEY_CONTENT_DROPPED) == true

    fun updateTitle(value: String) {
        title = value
        handle[KEY_TITLE] = value
    }

    fun updateContent(value: String) {
        content = value
        val fits = value.length <= MAX_SAVED_CONTENT
        handle[KEY_CONTENT] = if (fits) value else null
        handle[KEY_CONTENT_DROPPED] = !fits
    }

    fun updateExtra(value: String) {
        extra = value
        handle[KEY_EXTRA] = value
    }

    /**
     * Adds a tag after validation. Returns a failure for invalid names or too many tags;
     * a duplicate (case-insensitive) is ignored.
     */
    fun addTag(raw: String): DataResult<Unit> {
        TagNames.validate(raw)?.let { return DataResult.Failure(DataError.InvalidTag(it)) }
        val normalized = TagNames.normalize(raw)
        if (tags.any { TagNames.normalize(it) == normalized }) return DataResult.Success(Unit)
        if (tags.size >= TagNames.MAX_PER_RECORD) {
            return DataResult.Failure(
                DataError.InvalidTag(TagProblem.TOO_MANY),
            )
        }
        setTags(tags + TagNames.clean(raw))
        return DataResult.Success(Unit)
    }

    fun removeTag(name: String) {
        setTags(tags.filterNot { it == name })
    }

    /** Fills the editor with stored values and marks them as the saved baseline. */
    fun load(title: String, content: String, tags: List<String>, extra: String = "") {
        updateTitle(title)
        updateContent(content)
        setTags(tags)
        updateExtra(extra)
        markSaved()
        handle[KEY_INITIALIZED] = true
    }

    /** Starts an empty editor (new record). */
    fun startNew(extra: String = "") {
        if (isInitialized) return
        updateExtra(extra)
        markSaved()
        handle[KEY_INITIALIZED] = true
    }

    /** The values to send to the repository; pass the same snapshot to [markSaved]. */
    fun snapshot(): DraftSnapshot = DraftSnapshot(title, content, tags, extra)

    /**
     * Call only after the database confirmed the save, with the snapshot that was saved.
     * Text typed while the save was running stays marked as unsaved.
     */
    fun markSaved(saved: DraftSnapshot = snapshot()) {
        baselineHash = saved.hashCode()
        handle[KEY_BASELINE] = baselineHash
    }

    private fun setTags(value: List<String>) {
        tags = value
        handle[KEY_TAGS] = ArrayList(value)
    }

    private fun currentHash(): Int = snapshot().hashCode()

    private fun emptyHash(): Int = DraftSnapshot("", "", emptyList(), extra).hashCode()

    companion object {
        const val MAX_SAVED_CONTENT = 20_000
        private const val KEY_TITLE = "draft_title"
        private const val KEY_CONTENT = "draft_content"
        private const val KEY_TAGS = "draft_tags"
        private const val KEY_EXTRA = "draft_extra"
        private const val KEY_BASELINE = "draft_baseline"
        private const val KEY_INITIALIZED = "draft_initialized"
        private const val KEY_CONTENT_DROPPED = "draft_content_dropped"
    }
}

data class DraftSnapshot(
    val title: String,
    val content: String,
    val tags: List<String>,
    val extra: String,
)

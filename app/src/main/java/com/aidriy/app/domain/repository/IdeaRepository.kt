package com.aidriy.app.domain.repository

import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.Idea
import com.aidriy.app.domain.model.IdeaDraft
import com.aidriy.app.domain.model.IdeaFilter
import com.aidriy.app.domain.model.IdeaStatus
import com.aidriy.app.domain.model.Note
import kotlinx.coroutines.flow.Flow

/** See [NoteRepository] for the shared repository conventions. */
interface IdeaRepository {
    fun observeIdeas(filter: IdeaFilter): Flow<List<Idea>>
    fun observeRecent(limit: Int): Flow<List<Idea>>
    fun observeIdea(id: String): Flow<Idea?>
    suspend fun getById(id: String): Idea?

    suspend fun create(draft: IdeaDraft): DataResult<Idea>
    /** One-step capture from a single line of text; status starts at Inbox. */
    suspend fun quickCapture(title: String): DataResult<Idea>
    suspend fun update(id: String, draft: IdeaDraft): DataResult<Idea>
    suspend fun setStatus(id: String, status: IdeaStatus): DataResult<Unit>
    suspend fun delete(id: String): DataResult<Unit>

    /**
     * Creates a new note from the idea's title, content and tags in one transaction.
     * The idea itself is kept; deleting it is a separate, explicit user action.
     */
    suspend fun convertToNote(id: String): DataResult<Note>
}

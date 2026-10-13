package com.aidriy.app.domain.repository

import com.aidriy.app.domain.model.Idea
import com.aidriy.app.domain.model.IdeaStatus
import kotlinx.coroutines.flow.Flow

/** See [NoteRepository] for the shared repository conventions. Implemented in Phase 2. */
interface IdeaRepository {
    fun observeIdeas(status: IdeaStatus? = null): Flow<List<Idea>>
    fun observeRecent(limit: Int): Flow<List<Idea>>
    suspend fun getById(id: String): Idea?
    suspend fun upsert(idea: Idea)
    suspend fun delete(id: String)
}

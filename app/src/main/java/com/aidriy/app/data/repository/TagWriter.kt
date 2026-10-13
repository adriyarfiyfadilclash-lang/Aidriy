package com.aidriy.app.data.repository

import com.aidriy.app.core.IdGenerator
import com.aidriy.app.data.local.dao.TagDao
import com.aidriy.app.data.local.entity.IdeaTagCrossRef
import com.aidriy.app.data.local.entity.JournalTagCrossRef
import com.aidriy.app.data.local.entity.NoteTagCrossRef
import com.aidriy.app.data.local.entity.TagEntity
import com.aidriy.app.domain.TagNames

/**
 * Shared tag assignment. Every method must run inside a transaction started by the caller,
 * so a record and its tag links are always written (or rolled back) together.
 * Names must already be validated with [TagNames.cleanList].
 */
internal class TagWriter(
    private val tagDao: TagDao,
    private val ids: IdGenerator,
) {
    /** Reuses existing tags by normalized name and creates the missing ones. */
    suspend fun resolve(cleanNames: List<String>): List<TagEntity> = cleanNames.map { name ->
        val normalized = TagNames.normalize(name)
        tagDao.getByNormalizedName(normalized)
            ?: TagEntity(id = ids.newId(), name = name, normalizedName = normalized, color = null)
                .also { tagDao.insert(it) }
    }

    suspend fun replaceNoteTags(noteId: String, cleanNames: List<String>) {
        tagDao.clearNote(noteId)
        tagDao.addToNote(resolve(cleanNames).map { NoteTagCrossRef(noteId = noteId, tagId = it.id) })
        tagDao.deleteUnused()
    }

    suspend fun replaceIdeaTags(ideaId: String, cleanNames: List<String>) {
        tagDao.clearIdea(ideaId)
        tagDao.addToIdea(resolve(cleanNames).map { IdeaTagCrossRef(ideaId = ideaId, tagId = it.id) })
        tagDao.deleteUnused()
    }

    suspend fun replaceJournalTags(journalId: String, cleanNames: List<String>) {
        tagDao.clearJournal(journalId)
        tagDao.addToJournal(resolve(cleanNames).map { JournalTagCrossRef(journalId = journalId, tagId = it.id) })
        tagDao.deleteUnused()
    }

    /** Call after deleting a record: cascades removed its links, this removes now-unused tags. */
    suspend fun pruneUnused() {
        tagDao.deleteUnused()
    }
}

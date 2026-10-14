package com.aidriy.app.data.local

import com.aidriy.app.data.local.entity.IdeaWithTags
import com.aidriy.app.data.local.entity.JournalWithTags
import com.aidriy.app.data.local.entity.NoteWithTags
import com.aidriy.app.data.local.entity.TagEntity
import com.aidriy.app.domain.model.Idea
import com.aidriy.app.domain.model.IdeaStatus
import com.aidriy.app.domain.model.JournalEntry
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.model.Tag
import java.time.LocalDate
import java.util.Locale

internal fun TagEntity.toDomain(): Tag = Tag(id = id, name = name, color = color)

private fun List<TagEntity>.toSortedDomain(): List<Tag> =
    map { it.toDomain() }.sortedBy { it.name.lowercase(Locale.ROOT) }

internal fun NoteWithTags.toDomain(): Note = Note(
    id = note.id,
    title = note.title,
    content = note.content,
    createdAt = note.createdAt,
    updatedAt = note.updatedAt,
    isFavorite = note.isFavorite,
    isArchived = note.isArchived,
    tags = tags.toSortedDomain(),
    reminderAt = note.reminderAt,
)

internal fun IdeaWithTags.toDomain(): Idea = Idea(
    id = idea.id,
    title = idea.title,
    content = idea.content,
    status = IdeaStatus.fromStorageKey(idea.status),
    createdAt = idea.createdAt,
    updatedAt = idea.updatedAt,
    tags = tags.toSortedDomain(),
)

internal fun JournalWithTags.toDomain(): JournalEntry = JournalEntry(
    id = entry.id,
    date = LocalDate.parse(entry.date),
    title = entry.title,
    content = entry.content,
    createdAt = entry.createdAt,
    updatedAt = entry.updatedAt,
    tags = tags.toSortedDomain(),
)

package com.aidriy.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** Room loads these in two queries; DAO methods returning them are annotated @Transaction. */

data class NoteWithTags(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NoteTagCrossRef::class,
            parentColumn = "note_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
)

data class IdeaWithTags(
    @Embedded val idea: IdeaEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = IdeaTagCrossRef::class,
            parentColumn = "idea_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
)

data class JournalWithTags(
    @Embedded val entry: JournalEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = JournalTagCrossRef::class,
            parentColumn = "journal_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
)

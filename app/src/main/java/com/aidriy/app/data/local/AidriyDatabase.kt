package com.aidriy.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.aidriy.app.data.local.dao.IdeaDao
import com.aidriy.app.data.local.dao.JournalDao
import com.aidriy.app.data.local.dao.NoteDao
import com.aidriy.app.data.local.dao.TagDao
import com.aidriy.app.data.local.entity.IdeaEntity
import com.aidriy.app.data.local.entity.IdeaTagCrossRef
import com.aidriy.app.data.local.entity.JournalEntryEntity
import com.aidriy.app.data.local.entity.JournalTagCrossRef
import com.aidriy.app.data.local.entity.NoteEntity
import com.aidriy.app.data.local.entity.NoteTagCrossRef
import com.aidriy.app.data.local.entity.TagEntity

@Database(
    entities = [
        NoteEntity::class,
        IdeaEntity::class,
        JournalEntryEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        IdeaTagCrossRef::class,
        JournalTagCrossRef::class,
    ],
    version = AidriyDatabase.VERSION,
    exportSchema = true,
)
abstract class AidriyDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun ideaDao(): IdeaDao
    abstract fun journalDao(): JournalDao
    abstract fun tagDao(): TagDao

    companion object {
        const val VERSION = 1
        const val NAME = "aidriy.db"

        /**
         * No destructive fallback: if a future version ships without a matching migration,
         * Room fails loudly instead of silently wiping the user's notes.
         */
        fun create(context: Context): AidriyDatabase =
            Room.databaseBuilder(context.applicationContext, AidriyDatabase::class.java, NAME)
                .addMigrations(*AidriyMigrations.ALL)
                .build()
    }
}

/**
 * Registry of schema migrations. To change the schema:
 * 1. Bump [AidriyDatabase.VERSION].
 * 2. Add a `Migration(old, new)` here with explicit SQL.
 * 3. Build once to export `app/schemas/.../<new>.json`, commit it, and add a MigrationTestHelper test.
 */
object AidriyMigrations {
    val ALL: Array<Migration> = emptyArray()
}

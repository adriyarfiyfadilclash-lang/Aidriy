package com.aidriy.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aidriy.app.data.local.dao.AiDao
import com.aidriy.app.data.local.dao.BackupDao
import com.aidriy.app.data.local.dao.FileDao
import com.aidriy.app.data.local.dao.IdeaDao
import com.aidriy.app.data.local.dao.JournalDao
import com.aidriy.app.data.local.dao.NoteDao
import com.aidriy.app.data.local.dao.TagDao
import com.aidriy.app.data.local.entity.AiConversationEntity
import com.aidriy.app.data.local.entity.AiMessageEntity
import com.aidriy.app.data.local.entity.FileEntity
import com.aidriy.app.data.local.entity.FolderEntity
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
        FolderEntity::class,
        FileEntity::class,
        AiConversationEntity::class,
        AiMessageEntity::class,
    ],
    version = AidriyDatabase.VERSION,
    exportSchema = true,
)
abstract class AidriyDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun ideaDao(): IdeaDao
    abstract fun journalDao(): JournalDao
    abstract fun tagDao(): TagDao
    abstract fun fileDao(): FileDao
    abstract fun aiDao(): AiDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 2
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
 * 2. Add a `Migration(old, new)` here with explicit SQL that matches the entities exactly.
 * 3. Build once to export `app/schemas/.../<new>.json`, commit it, and add a MigrationTestHelper test.
 */
object AidriyMigrations {

    /** v1 → v2: note reminders, Berkas (folders + files) and AI chat history. Existing data is untouched. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `notes` ADD COLUMN `reminder_at` INTEGER")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_name` ON `folders` (`name`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `files` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `local_path` TEXT NOT NULL, " +
                    "`folder_id` TEXT, `note_id` TEXT, `created_at` INTEGER NOT NULL, `extracted_text` TEXT, " +
                    "`extraction_status` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`folder_id`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                    "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_files_folder_id` ON `files` (`folder_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_files_note_id` ON `files` (`note_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_files_created_at` ON `files` (`created_at`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ai_conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ai_conversations_updated_at` ON `ai_conversations` (`updated_at`)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ai_messages` (`id` TEXT NOT NULL, `conversation_id` TEXT NOT NULL, " +
                    "`role` TEXT NOT NULL, `content` TEXT NOT NULL, `provider` TEXT, `model` TEXT, " +
                    "`sources` TEXT NOT NULL, `no_local_match` INTEGER NOT NULL, `is_error` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`conversation_id`) REFERENCES `ai_conversations`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ai_messages_conversation_id_created_at` " +
                    "ON `ai_messages` (`conversation_id`, `created_at`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

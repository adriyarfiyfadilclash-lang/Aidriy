package com.aidriy.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aidriy.app.data.local.AidriyDatabase
import com.aidriy.app.data.local.AidriyMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades a database with the exact v1 layout (as shipped in AIDRIY 0.1) to v2.
 * Opening it with Room afterwards validates every table, column, index and foreign key
 * against the v2 entities, so a wrong migration fails here instead of on users' phones.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun v1_database_upgrades_to_latest_and_keeps_notes() {
        val v1 = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        V1_SCHEMA.forEach(v1::execSQL)
        v1.execSQL(
            "INSERT INTO notes (id, title, content, created_at, updated_at, is_favorite, is_archived) " +
                "VALUES ('n1', 'Catatan lama', 'isi', 1, 2, 1, 0)",
        )
        v1.execSQL("INSERT INTO tags (id, name, normalized_name, color) VALUES ('t1', 'Kerja', 'kerja', NULL)")
        v1.execSQL("INSERT INTO note_tags (note_id, tag_id) VALUES ('n1', 't1')")
        v1.version = 1
        v1.close()

        val db = Room.databaseBuilder(context, AidriyDatabase::class.java, name)
            .addMigrations(*AidriyMigrations.ALL)
            .build()
        try {
            val sql = db.openHelper.writableDatabase
            sql.query("SELECT title, is_favorite, reminder_at FROM notes WHERE id = 'n1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Catatan lama", c.getString(0))
                assertEquals(1, c.getInt(1))
                assertTrue(c.isNull(2))
            }
            sql.query("SELECT COUNT(*) FROM note_tags").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
            // New tables accept rows and keep their foreign keys.
            sql.execSQL("INSERT INTO folders (id, name, created_at) VALUES ('f1', 'Kuliah', 3)")
            sql.execSQL(
                "INSERT INTO files (id, name, mime_type, size_bytes, local_path, folder_id, note_id, created_at, " +
                    "extracted_text, extraction_status) VALUES ('x1', 'a.pdf', 'application/pdf', 10, 'p', 'f1', 'n1', 4, NULL, 'PENDING')",
            )
            // v3: notes can join a folder; deleting the folder keeps the note.
            sql.execSQL("UPDATE notes SET folder_id = 'f1' WHERE id = 'n1'")
            sql.execSQL("DELETE FROM folders WHERE id = 'f1'")
            sql.query("SELECT folder_id, note_id FROM files WHERE id = 'x1'").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
                assertEquals("n1", c.getString(1))
            }
            sql.query("SELECT folder_id FROM notes WHERE id = 'n1'").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
            }
            // v4: AI messages get a rating column defaulting to 0.
            sql.execSQL("INSERT INTO ai_conversations (id, title, created_at, updated_at) VALUES ('c1', 'Chat', 1, 1)")
            sql.execSQL(
                "INSERT INTO ai_messages (id, conversation_id, role, content, provider, model, sources, no_local_match, " +
                    "is_error, created_at) VALUES ('m1', 'c1', 'user', 'halo', NULL, NULL, '', 0, 0, 1)",
            )
            sql.query("SELECT rating FROM ai_messages WHERE id = 'm1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            assertEquals(AidriyDatabase.VERSION, sql.version)
        } finally {
            db.close()
        }
    }

    private companion object {
        /** The v1 tables exactly as Room 2.6 created them for schema version 1. */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `is_favorite` INTEGER NOT NULL, " +
                "`is_archived` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_notes_updated_at` ON `notes` (`updated_at`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_created_at` ON `notes` (`created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_notes_is_archived_is_favorite` ON `notes` (`is_archived`, `is_favorite`)",
            "CREATE TABLE IF NOT EXISTS `ideas` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_ideas_status` ON `ideas` (`status`)",
            "CREATE INDEX IF NOT EXISTS `index_ideas_updated_at` ON `ideas` (`updated_at`)",
            "CREATE INDEX IF NOT EXISTS `index_ideas_created_at` ON `ideas` (`created_at`)",
            "CREATE TABLE IF NOT EXISTS `journal_entries` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_journal_entries_date` ON `journal_entries` (`date`)",
            "CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, " +
                "`color` TEXT, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_normalized_name` ON `tags` (`normalized_name`)",
            "CREATE TABLE IF NOT EXISTS `note_tags` (`note_id` TEXT NOT NULL, `tag_id` TEXT NOT NULL, " +
                "PRIMARY KEY(`note_id`, `tag_id`), " +
                "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_note_tags_tag_id` ON `note_tags` (`tag_id`)",
            "CREATE TABLE IF NOT EXISTS `idea_tags` (`idea_id` TEXT NOT NULL, `tag_id` TEXT NOT NULL, " +
                "PRIMARY KEY(`idea_id`, `tag_id`), " +
                "FOREIGN KEY(`idea_id`) REFERENCES `ideas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_idea_tags_tag_id` ON `idea_tags` (`tag_id`)",
            "CREATE TABLE IF NOT EXISTS `journal_tags` (`journal_id` TEXT NOT NULL, `tag_id` TEXT NOT NULL, " +
                "PRIMARY KEY(`journal_id`, `tag_id`), " +
                "FOREIGN KEY(`journal_id`) REFERENCES `journal_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_journal_tags_tag_id` ON `journal_tags` (`tag_id`)",
        )
    }
}

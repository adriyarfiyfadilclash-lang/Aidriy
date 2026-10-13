package com.aidriy.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.model.NoteDraft
import com.aidriy.app.domain.model.NoteFilter
import com.aidriy.app.domain.model.NoteScope
import com.aidriy.app.domain.model.SortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteRepositoryTest : DatabaseTestBase() {

    private val notes get() = data.noteRepository

    private suspend fun create(title: String, content: String = "", tags: List<String> = emptyList()): Note =
        (notes.create(NoteDraft(title, content, tags)) as DataResult.Success).value

    private suspend fun list(filter: NoteFilter = NoteFilter()) = notes.observeNotes(filter).first()

    @Test
    fun insert_and_retrieve() = runTest {
        val note = create("  Rapat  ", "Agenda Q4")
        val loaded = notes.getById(note.id)!!
        assertEquals("Rapat", loaded.title) // title trimmed
        assertEquals("Agenda Q4", loaded.content)
        assertEquals(loaded.createdAt, loaded.updatedAt)
    }

    @Test
    fun empty_note_is_rejected_and_nothing_is_written() = runTest {
        val result = notes.create(NoteDraft(" ", " "))
        assertEquals(DataResult.Failure(DataError.EmptyNote), result)
        assertTrue(list().isEmpty())
    }

    @Test
    fun update_changes_content_and_updatedAt_only() = runTest {
        val note = create("Awal", "isi")
        clock.advanceMinutes(5)
        val updated = (notes.update(note.id, NoteDraft("Akhir", "isi baru")) as DataResult.Success).value
        assertEquals("Akhir", updated.title)
        assertEquals(note.createdAt, updated.createdAt)
        assertTrue(updated.updatedAt > note.updatedAt)
    }

    @Test
    fun update_of_missing_note_returns_not_found() = runTest {
        assertEquals(
            DataResult.Failure(DataError.NotFound),
            notes.update("00000000-0000-0000-0000-000000000000", NoteDraft("x", "y")),
        )
    }

    @Test
    fun delete_removes_the_note() = runTest {
        val note = create("Hapus")
        assertEquals(DataResult.Success(Unit), notes.delete(note.id))
        assertNull(notes.getById(note.id))
        assertEquals(DataResult.Failure(DataError.NotFound), notes.delete(note.id))
    }

    @Test
    fun search_matches_title_and_content_case_insensitively() = runTest {
        create("Resep Rendang", "daging, santan")
        create("Belanja", "Beli SANTAN kelapa")
        create("Olahraga", "lari pagi")

        assertEquals(setOf("Resep Rendang", "Belanja"), list(NoteFilter(query = "santan")).map { it.title }.toSet())
        assertEquals(listOf("Resep Rendang"), list(NoteFilter(query = "  rendang ")).map { it.title })
        assertTrue(list(NoteFilter(query = "tidak-ada")).isEmpty())
    }

    @Test
    fun like_wildcards_in_the_query_are_literal() = runTest {
        create("Diskon 50%", "")
        create("Diskon 500", "")
        create("nama_file", "")
        create("namaXfile", "")

        assertEquals(listOf("Diskon 50%"), list(NoteFilter(query = "50%")).map { it.title })
        assertEquals(listOf("nama_file"), list(NoteFilter(query = "a_f")).map { it.title })
    }

    @Test
    fun favorites_and_archive_scopes() = runTest {
        val a = create("A")
        val b = create("B")
        val c = create("C")
        notes.setFavorite(a.id, true)
        notes.setFavorite(c.id, true)
        notes.setArchived(c.id, true)

        assertEquals(setOf("A", "B"), list(NoteFilter(scope = NoteScope.ACTIVE)).map { it.title }.toSet())
        // Archived favorites stay out of the favorites view.
        assertEquals(listOf("A"), list(NoteFilter(scope = NoteScope.FAVORITES)).map { it.title })
        assertEquals(listOf("C"), list(NoteFilter(scope = NoteScope.ARCHIVED)).map { it.title })

        notes.setArchived(c.id, false)
        assertEquals(setOf("A", "B", "C"), list().map { it.title }.toSet())
        assertTrue(notes.getById(b.id)!!.isFavorite.not())
    }

    @Test
    fun sorting_by_updated_and_created() = runTest {
        val first = create("Pertama")
        clock.advanceMinutes(1)
        create("Kedua")
        clock.advanceMinutes(1)
        notes.update(first.id, NoteDraft("Pertama", "diedit"))

        assertEquals(listOf("Pertama", "Kedua"), list(NoteFilter(sort = SortOrder.UPDATED)).map { it.title })
        assertEquals(listOf("Kedua", "Pertama"), list(NoteFilter(sort = SortOrder.CREATED)).map { it.title })
    }

    @Test
    fun tags_are_reused_filterable_and_pruned_after_delete() = runTest {
        val a = create("A", tags = listOf("Kerja", "Ide"))
        create("B", tags = listOf("#kerja")) // same tag, different spelling

        val tags = data.tagRepository.observeAll().first()
        assertEquals(listOf("Ide", "Kerja"), tags.map { it.name })

        val kerja = tags.first { it.name == "Kerja" }
        assertEquals(setOf("A", "B"), list(NoteFilter(tagId = kerja.id)).map { it.title }.toSet())

        // Removing a tag from a note via update.
        notes.update(a.id, NoteDraft("A", "", listOf("Kerja")))
        assertEquals(listOf("Kerja"), notes.getById(a.id)!!.tags.map { it.name })
        // "Ide" is no longer used anywhere, so it was pruned.
        assertEquals(listOf("Kerja"), data.tagRepository.observeAll().first().map { it.name })

        // Deleting notes cascades their tag links and prunes unused tags.
        list().forEach { notes.delete(it.id) }
        assertTrue(data.tagRepository.observeAll().first().isEmpty())
        assertEquals(0, countRows("note_tags"))
    }

    @Test
    fun invalid_tag_rejects_the_whole_save() = runTest {
        val result = notes.create(NoteDraft("Judul", "", listOf("ok", "   ")))
        assertTrue(result is DataResult.Failure)
        assertTrue(list().isEmpty())
    }

    private fun countRows(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}

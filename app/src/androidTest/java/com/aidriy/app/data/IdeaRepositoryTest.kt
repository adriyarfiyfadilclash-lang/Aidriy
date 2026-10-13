package com.aidriy.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.Idea
import com.aidriy.app.domain.model.IdeaDraft
import com.aidriy.app.domain.model.IdeaFilter
import com.aidriy.app.domain.model.IdeaStatus
import com.aidriy.app.domain.model.NoteFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdeaRepositoryTest : DatabaseTestBase() {

    private val ideas get() = data.ideaRepository

    @Test
    fun quick_capture_lands_in_inbox() = runTest {
        val idea = (ideas.quickCapture("  Aplikasi resep  ") as DataResult.Success).value
        assertEquals("Aplikasi resep", idea.title)
        assertEquals(IdeaStatus.INBOX, idea.status)
    }

    @Test
    fun quick_capture_requires_a_title() = runTest {
        assertEquals(DataResult.Failure(DataError.EmptyIdeaTitle), ideas.quickCapture("   "))
    }

    @Test
    fun status_transitions_persist_and_filter() = runTest {
        val a = (ideas.quickCapture("A") as DataResult.Success).value
        val b = (ideas.quickCapture("B") as DataResult.Success).value
        clock.advanceMinutes(1)

        assertEquals(DataResult.Success(Unit), ideas.setStatus(a.id, IdeaStatus.IN_PROGRESS))
        assertEquals(DataResult.Success(Unit), ideas.setStatus(b.id, IdeaStatus.COMPLETED))

        assertEquals(IdeaStatus.IN_PROGRESS, ideas.getById(a.id)!!.status)
        assertTrue(ideas.getById(a.id)!!.updatedAt > a.updatedAt)
        assertEquals(listOf("B"), ideas.observeIdeas(IdeaFilter(status = IdeaStatus.COMPLETED)).first().map { it.title })
        assertEquals(2, ideas.observeIdeas(IdeaFilter()).first().size)
    }

    @Test
    fun stored_status_values_are_exactly_the_spec_keys() = runTest {
        val idea = (ideas.create(IdeaDraft("X", status = IdeaStatus.IN_PROGRESS)) as DataResult.Success).value
        val raw = db.ideaDao().getById(idea.id)!!.status
        assertEquals("IN_PROGRESS", raw)
    }

    @Test
    fun convert_to_note_copies_content_and_tags_and_keeps_the_idea() = runTest {
        val idea: Idea = (
            ideas.create(IdeaDraft("Podcast", "Format wawancara", IdeaStatus.IN_PROGRESS, listOf("Konten")))
                as DataResult.Success
            ).value

        val note = (ideas.convertToNote(idea.id) as DataResult.Success).value

        assertEquals("Podcast", note.title)
        assertEquals("Format wawancara", note.content)
        assertEquals(listOf("Konten"), note.tags.map { it.name })
        assertNotNull("idea must be kept", ideas.getById(idea.id))
        assertEquals(1, data.noteRepository.observeNotes(NoteFilter()).first().size)
        // The tag is shared, not duplicated.
        assertEquals(1, data.tagRepository.observeAll().first().size)
    }

    @Test
    fun convert_missing_idea_fails_without_creating_a_note() = runTest {
        val result = ideas.convertToNote("00000000-0000-0000-0000-000000000000")
        assertEquals(DataResult.Failure(DataError.NotFound), result)
        assertTrue(data.noteRepository.observeNotes(NoteFilter()).first().isEmpty())
    }

    @Test
    fun search_ideas_by_title_and_content() = runTest {
        ideas.create(IdeaDraft("Kebun", "tanam cabai"))
        ideas.create(IdeaDraft("Cabai bubuk", ""))
        ideas.create(IdeaDraft("Lain", "tidak relevan"))
        assertEquals(2, ideas.observeIdeas(IdeaFilter(query = "CABAI")).first().size)
    }
}

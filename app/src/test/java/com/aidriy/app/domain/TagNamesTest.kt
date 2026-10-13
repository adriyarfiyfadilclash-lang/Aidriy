package com.aidriy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagNamesTest {

    @Test
    fun clean_removes_hash_and_extra_whitespace() {
        assertEquals("Kerja Tim", TagNames.clean("  #Kerja   Tim "))
    }

    @Test
    fun normalize_is_case_insensitive_key() {
        assertEquals(TagNames.normalize("KERJA"), TagNames.normalize(" #kerja "))
    }

    @Test
    fun validate_rejects_empty_and_too_long_names() {
        assertEquals(TagProblem.EMPTY, TagNames.validate("   "))
        assertEquals(TagProblem.EMPTY, TagNames.validate("#"))
        assertEquals(TagProblem.TOO_LONG, TagNames.validate("a".repeat(TagNames.MAX_LENGTH + 1)))
        assertNull(TagNames.validate("a".repeat(TagNames.MAX_LENGTH)))
    }

    @Test
    fun cleanList_deduplicates_case_insensitively_keeping_first_spelling() {
        val result = TagNames.cleanList(listOf("Kerja", "#kerja", "Pribadi", "KERJA "))
        assertEquals(DataResult.Success(listOf("Kerja", "Pribadi")), result)
    }

    @Test
    fun cleanList_fails_on_invalid_name() {
        val result = TagNames.cleanList(listOf("ok", "  "))
        assertEquals(DataResult.Failure(DataError.InvalidTag(TagProblem.EMPTY)), result)
    }

    @Test
    fun cleanList_fails_when_too_many_distinct_tags() {
        val names = (1..TagNames.MAX_PER_RECORD + 1).map { "tag$it" }
        val result = TagNames.cleanList(names)
        assertTrue(result is DataResult.Failure)
        assertEquals(DataError.InvalidTag(TagProblem.TOO_MANY), (result as DataResult.Failure).error)
    }
}

package com.aidriy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryTest {

    @Test
    fun trims_and_collapses_whitespace() {
        assertEquals("rapat tim", SearchQuery.normalize("   rapat \t  tim \n"))
    }

    @Test
    fun empty_or_blank_query_matches_everything() {
        assertEquals("%%", SearchQuery.toLikePattern(""))
        assertEquals("%%", SearchQuery.toLikePattern("   "))
    }

    @Test
    fun wraps_query_in_contains_pattern() {
        assertEquals("%kopi%", SearchQuery.toLikePattern(" kopi "))
    }

    @Test
    fun escapes_like_wildcards_so_they_match_literally() {
        assertEquals("%50!%%", SearchQuery.toLikePattern("50%"))
        assertEquals("%a!_b%", SearchQuery.toLikePattern("a_b"))
    }

    @Test
    fun escapes_the_escape_character_itself() {
        assertEquals("%wow!!%", SearchQuery.toLikePattern("wow!"))
    }

    @Test
    fun keeps_case_for_sqlite_case_insensitive_like() {
        assertEquals("%Jakarta%", SearchQuery.toLikePattern("Jakarta"))
    }
}

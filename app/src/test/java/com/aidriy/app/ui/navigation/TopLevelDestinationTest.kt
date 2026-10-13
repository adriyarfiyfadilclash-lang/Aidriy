package com.aidriy.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun has_exactly_five_destinations_in_required_order() {
        assertEquals(
            listOf("home", "notes", "ideas", "journal", "insight"),
            TopLevelDestination.entries.map { it.route },
        )
    }

    @Test
    fun routes_are_unique() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}

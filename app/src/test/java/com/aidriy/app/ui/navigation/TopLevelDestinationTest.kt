package com.aidriy.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun has_four_tabs_in_the_order_of_the_design() {
        assertEquals(
            listOf("catatan", "ai", "berkas", "pengaturan"),
            TopLevelDestination.entries.map { it.route },
        )
    }

    @Test
    fun routes_are_unique() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}

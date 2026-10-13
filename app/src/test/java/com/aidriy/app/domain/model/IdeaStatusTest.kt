package com.aidriy.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdeaStatusTest {

    @Test
    fun storage_keys_round_trip() {
        IdeaStatus.entries.forEach { status ->
            assertEquals(status, IdeaStatus.fromStorageKeyOrNull(status.storageKey))
        }
    }

    @Test
    fun storage_keys_match_spec() {
        assertEquals(listOf("INBOX", "IN_PROGRESS", "COMPLETED"), IdeaStatus.entries.map { it.storageKey })
    }

    @Test
    fun strict_lookup_rejects_unknown_keys() {
        assertNull(IdeaStatus.fromStorageKeyOrNull("DONE"))
        assertNull(IdeaStatus.fromStorageKeyOrNull("inbox"))
    }

    @Test
    fun lenient_lookup_falls_back_to_inbox_for_reading() {
        assertEquals(IdeaStatus.INBOX, IdeaStatus.fromStorageKey("something-else"))
    }

    @Test
    fun labels_match_product_spec() {
        assertEquals(
            listOf("Inbox", "Dikembangkan", "Selesai"),
            IdeaStatus.entries.map { it.label },
        )
    }
}

package com.aidriy.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class IdeaStatusTest {

    @Test
    fun storage_keys_round_trip() {
        IdeaStatus.entries.forEach { status ->
            assertEquals(status, IdeaStatus.fromStorageKey(status.storageKey))
        }
    }

    @Test
    fun unknown_storage_key_falls_back_to_inbox() {
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

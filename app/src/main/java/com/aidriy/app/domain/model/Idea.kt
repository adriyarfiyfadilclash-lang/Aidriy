package com.aidriy.app.domain.model

/** A quickly captured idea that moves through [IdeaStatus]. */
data class Idea(
    val id: String,
    val title: String,
    val content: String,
    val status: IdeaStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<Tag> = emptyList(),
)

/**
 * [storageKey] is what gets persisted, so renaming the enum constant or its
 * display label never corrupts stored data.
 */
enum class IdeaStatus(val storageKey: String, val label: String) {
    INBOX("INBOX", "Inbox"),
    IN_PROGRESS("IN_PROGRESS", "Dikembangkan"),
    COMPLETED("COMPLETED", "Selesai");

    companion object {
        /** Strict lookup used for validation: unknown keys return null. */
        fun fromStorageKeyOrNull(key: String): IdeaStatus? =
            entries.firstOrNull { it.storageKey == key }

        /** Lenient lookup used when reading stored rows: unknown keys fall back to [INBOX]. */
        fun fromStorageKey(key: String): IdeaStatus = fromStorageKeyOrNull(key) ?: INBOX
    }
}

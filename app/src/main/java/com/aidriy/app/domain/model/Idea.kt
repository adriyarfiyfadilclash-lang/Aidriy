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
    INBOX("inbox", "Inbox"),
    DEVELOPING("developing", "Dikembangkan"),
    DONE("done", "Selesai");

    companion object {
        fun fromStorageKey(key: String): IdeaStatus =
            entries.firstOrNull { it.storageKey == key } ?: INBOX
    }
}

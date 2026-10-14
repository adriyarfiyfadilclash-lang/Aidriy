package com.aidriy.app.domain.model

/** A structured personal note. Timestamps are epoch milliseconds (UTC). */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val tags: List<Tag> = emptyList(),
    /** Pending reminder (UTC epoch millis), or null. */
    val reminderAt: Long? = null,
    /** Folder (shared with Berkas), or null. */
    val folderId: String? = null,
)

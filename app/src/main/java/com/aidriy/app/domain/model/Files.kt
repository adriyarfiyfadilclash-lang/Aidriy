package com.aidriy.app.domain.model

enum class FileKind(val filterKey: String) {
    DOCUMENT("document"),
    IMAGE("image"),
    AUDIO("audio"),
    VIDEO("video");

    companion object {
        fun of(mimeType: String): FileKind = when {
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("audio/") -> AUDIO
            mimeType.startsWith("video/") -> VIDEO
            else -> DOCUMENT
        }
    }
}

/** Text extraction state of a stored file. Persisted by [name]. */
enum class ExtractionStatus {
    /** Waiting or running. */
    PENDING,

    /** Readable text was extracted and stored. */
    DONE,

    /** The file has no text layer (e.g. a scanned PDF or a photo). OCR can be tried. */
    NO_TEXT,

    /** Password-protected document. */
    PROTECTED,

    /** Format not supported for text extraction (audio, video, office files…). */
    UNSUPPORTED,

    /** Extraction failed (damaged or unreadable file). */
    FAILED;

    companion object {
        fun from(value: String): ExtractionStatus = entries.firstOrNull { it.name == value } ?: FAILED
    }
}

data class StoredFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val folderId: String?,
    val noteId: String?,
    val createdAt: Long,
    val extractedText: String?,
    val extractionStatus: ExtractionStatus,
) {
    val kind: FileKind get() = FileKind.of(mimeType)
    val isPdf: Boolean get() = mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
    val isImage: Boolean get() = kind == FileKind.IMAGE
    val isText: Boolean get() = mimeType.startsWith("text/")
}

data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
    val latest: Long?,
)

/** Item count and newest timestamp of a group (category or folder). */
data class GroupStats(val count: Int, val latest: Long?) {
    companion object {
        val EMPTY = GroupStats(0, null)
    }
}

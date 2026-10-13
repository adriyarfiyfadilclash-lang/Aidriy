package com.aidriy.app.domain.model

data class Tag(
    val id: String,
    val name: String,
    /** Optional hex color such as "#1F5EDB". */
    val color: String? = null,
)

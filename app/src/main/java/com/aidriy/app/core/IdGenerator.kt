package com.aidriy.app.core

import java.util.UUID

/** Creates stable unique record ids. Injected so tests can use predictable ids. */
fun interface IdGenerator {
    fun newId(): String
}

object UuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

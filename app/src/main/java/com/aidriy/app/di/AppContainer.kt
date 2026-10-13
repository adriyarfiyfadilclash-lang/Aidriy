package com.aidriy.app.di

import com.aidriy.app.core.time.SystemTimeProvider
import com.aidriy.app.core.time.TimeProvider

/**
 * Manual dependency container, created once per process by [com.aidriy.app.AidriyApplication].
 *
 * Manual DI keeps the dependency list small. Phase 2 adds the Room database and
 * repository implementations here, exposed through the interfaces in domain/repository.
 */
interface AppContainer {
    val timeProvider: TimeProvider
}

class DefaultAppContainer : AppContainer {
    override val timeProvider: TimeProvider = SystemTimeProvider
}

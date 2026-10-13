package com.aidriy.app.core.time

import java.time.LocalTime

/**
 * Indonesian time-of-day greeting. The UI maps each value to a string resource,
 * keeping this logic free of Android dependencies.
 */
enum class Greeting {
    PAGI,
    SIANG,
    SORE,
    MALAM;

    companion object {
        fun forTime(time: LocalTime): Greeting = when (time.hour) {
            in 4..10 -> PAGI
            in 11..14 -> SIANG
            in 15..17 -> SORE
            else -> MALAM
        }
    }
}

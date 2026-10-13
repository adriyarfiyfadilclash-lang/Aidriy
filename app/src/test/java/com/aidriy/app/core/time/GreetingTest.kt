package com.aidriy.app.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class GreetingTest {

    private fun at(hour: Int, minute: Int = 0) = Greeting.forTime(LocalTime.of(hour, minute))

    @Test
    fun pagi_from_four_until_before_eleven() {
        assertEquals(Greeting.PAGI, at(4, 0))
        assertEquals(Greeting.PAGI, at(7, 30))
        assertEquals(Greeting.PAGI, at(10, 59))
    }

    @Test
    fun siang_from_eleven_until_before_fifteen() {
        assertEquals(Greeting.SIANG, at(11, 0))
        assertEquals(Greeting.SIANG, at(14, 59))
    }

    @Test
    fun sore_from_fifteen_until_before_eighteen() {
        assertEquals(Greeting.SORE, at(15, 0))
        assertEquals(Greeting.SORE, at(17, 59))
    }

    @Test
    fun malam_from_eighteen_until_before_four() {
        assertEquals(Greeting.MALAM, at(18, 0))
        assertEquals(Greeting.MALAM, at(23, 59))
        assertEquals(Greeting.MALAM, at(0, 0))
        assertEquals(Greeting.MALAM, at(3, 59))
    }
}

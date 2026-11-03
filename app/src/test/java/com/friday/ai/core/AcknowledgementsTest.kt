package com.friday.ai.core

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcknowledgementsTest {

    @Test
    fun `russian is the default and the requested phrasing is in the pool`() {
        val seen = (1..40).map { Acknowledgements().next(null) }.toSet()
        assertTrue(seen.any { it.contains("К вашим услугам") })
        assertTrue("everything must be Russian: $seen", seen.all { it.first() in 'А'..'я' })
    }

    @Test
    fun `english wake call gets an english greeting`() {
        val a = Acknowledgements()
        repeat(20) {
            val s = a.next(WakePhrases.Language.ENGLISH)
            assertTrue("got '$s'", s.first() in 'A'..'z')
        }
    }

    @Test
    fun `spanish wake call gets a spanish greeting`() {
        val a = Acknowledgements()
        repeat(20) {
            val phrase = a.next(WakePhrases.Language.SPANISH)
            assertTrue("got '$phrase'", phrase.contains("¿") || phrase.contains("A tu") || phrase.contains("Dime"))
        }
    }

    @Test
    fun `it never says the same thing twice in a row`() {
        // A fixed phrase every single time makes an assistant sound like a
        // doorbell rather than something that answered you.
        val a = Acknowledgements()
        var previous = a.next(null)
        repeat(50) {
            val next = a.next(null)
            assertNotEquals("repeated back to back", previous, next)
            previous = next
        }
    }

    @Test
    fun `a degenerate random source still returns a usable phrase`() {
        // random() is documented as [0,1); guard against 1.0 anyway rather
        // than letting an index error reach the voice loop.
        listOf(0.0, 0.999999, 1.0).forEach { r ->
            val s = Acknowledgements(random = { r }).next(null)
            assertTrue("empty greeting for random=$r", s.isNotBlank())
        }
    }
}

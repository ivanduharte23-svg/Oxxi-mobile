package com.friday.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhrasesTest {

    private fun wakes(text: String) = WakePhrases.isWakeCall(text)

    @Test
    fun `Oxxi wakes in Spanish spelling and strips the call from a command`() {
        listOf("oxxi", "oksi", "oxi", "oxsi", "ocsi").forEach {
            assertTrue("'$it' should wake", wakes(it))
        }
        assertEquals(WakePhrases.Language.SPANISH, WakePhrases.detectLanguage("oxxi abre WhatsApp"))
        assertEquals("abre WhatsApp", WakePhrases.stripCall("oxxi, abre WhatsApp"))
    }

    @Test
    fun `Spanish attention words are accepted`() {
        assertTrue(wakes("oye oxxi"))
        assertTrue(wakes("hola Oxxi, abre la cámara"))
        assertTrue(wakes("ey oksi"))
        assertTrue(wakes("vale oxxi"))
    }

    @Test
    fun `legacy aliases still work but are not in Spanish Vosk grammar`() {
        assertTrue(wakes("пятница"))
        assertTrue(wakes("фрайдей"))
        assertTrue(wakes("окей пятница"))
        assertEquals(WakePhrases.Language.RUSSIAN, WakePhrases.detectLanguage("пятница открой камеру"))
        assertEquals(WakePhrases.Language.ENGLISH, WakePhrases.detectLanguage("hey friday"))
    }

    @Test
    fun `wake name followed by a command is accepted`() {
        assertTrue(wakes("oxxi abre la cámara"))
        assertTrue(wakes("oksi, pon música"))
        assertTrue(wakes("hola oxxi, qué tiempo hace"))
    }

    @Test
    fun `a weekday mentioned in a sentence does not wake Oxxi`() {
        assertFalse(wakes("встретимся в пятницу"))
        assertFalse(wakes("давай перенесём на пятницу"))
        assertFalse(wakes("в эту пятницу я занят"))
        assertFalse(wakes("hoy es viernes"))
    }

    @Test
    fun `unrelated speech does not wake`() {
        assertFalse(wakes("hola cómo estás"))
        assertFalse(wakes("what is the weather today"))
        assertFalse(wakes("abre la cámara"))
        assertFalse(wakes(""))
        assertFalse(wakes("   "))
    }

    @Test
    fun `Spanish Vosk grammar contains only Spanish words`() {
        val vocabulary = WakePhrases.grammarVocabulary()
        vocabulary.forEach { word ->
            assertTrue(
                "'$word' must be compatible with Spanish Vosk grammar",
                word.all { it in 'a'..'z' || it in "áéíóúüñ " }
            )
        }
        assertTrue(vocabulary.contains("oxxi"))
        assertTrue(vocabulary.contains("oksi"))
        assertTrue(vocabulary.contains("oye"))
        assertTrue(vocabulary.contains("hola"))
    }

    @Test
    fun `attention word alone does not wake`() {
        assertFalse(wakes("oye"))
        assertFalse(wakes("hola"))
        assertFalse(wakes("hey"))
    }

    @Test
    fun `unknown tokens do not wake`() {
        assertFalse(wakes("[unk]"))
        assertFalse(wakes("[unk] [unk] пятница"))
    }

    @Test
    fun `Spanish wake call is detected as Spanish`() {
        assertEquals(WakePhrases.Language.SPANISH, WakePhrases.detectLanguage("hola oxxi"))
    }

    @Test
    fun `language is null when it was not a wake call`() {
        assertNull(WakePhrases.detectLanguage("встретимся в пятницу"))
        assertNull(WakePhrases.detectLanguage("hello there"))
    }
}

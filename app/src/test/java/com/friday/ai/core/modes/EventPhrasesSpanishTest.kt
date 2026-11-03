package com.friday.ai.core.modes

import org.junit.Assert.assertEquals
import org.junit.Test

class EventPhrasesSpanishTest {

    @Test
    fun `Spanish Wi-Fi connection activates a mode`() {
        assertEquals(
            EventPhrases.Request(
                rest = "oficina",
                trigger = Trigger.WIFI,
                target = "casa",
                onConnect = true,
                exit = false
            ),
            EventPhrases.parse("Oxxi, activa el modo oficina cuando me conecto a la red Wi-Fi de casa")
        )
    }

    @Test
    fun `Spanish charger connection activates a mode`() {
        assertEquals(
            EventPhrases.Request(
                rest = "descanso",
                trigger = Trigger.CHARGER,
                target = "",
                onConnect = true,
                exit = false
            ),
            EventPhrases.parse("Oxxi, activa el modo descanso cuando pongo el móvil a cargar")
        )
    }
}

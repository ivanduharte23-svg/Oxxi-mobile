package com.friday.ai.core

import com.friday.ai.domain.model.CommandResult
import com.friday.ai.domain.model.DeviceAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCommandsTest {

    private val router = CommandRouter()

    @Test
    fun `Spanish grammar contains Spanish words`() {
        OfflineCommands.GRAMMAR.forEach { phrase ->
            assertTrue(
                "'$phrase' is not a Spanish phrase",
                phrase.all { it in 'a'..'z' || it in "áéíóúüñ " }
            )
        }
    }

    @Test
    fun `every offline grammar phrase is actionable or ends the conversation`() {
        val unroutable = OfflineCommands.GRAMMAR.filter { phrase ->
            val command = router.route(phrase)
            !OfflineCommands.isOfflineCapable(command) && !ConversationControl.isFarewell(phrase)
        }
        assertTrue("These offline phrases do nothing: $unroutable", unroutable.isEmpty())
    }

    @Test
    fun `phone-only actions are offline capable`() {
        assertTrue(OfflineCommands.isOfflineCapable(CommandResult.ToggleFlashlight))
        assertTrue(OfflineCommands.isOfflineCapable(CommandResult.SetAlarm(7, 30, null)))
        assertTrue(OfflineCommands.isOfflineCapable(CommandResult.SetTimer(60, null)))
        assertTrue(OfflineCommands.isOfflineCapable(CommandResult.OpenApp("cámara", null)))
        assertTrue(
            OfflineCommands.isOfflineCapable(
                CommandResult.DeviceControl(DeviceAction.VOLUME_UP)
            )
        )
    }

    @Test
    fun `anything requiring the internet is not offline capable`() {
        assertFalse(OfflineCommands.isOfflineCapable(CommandResult.ChatMessage("cuéntame un chiste")))
        assertFalse(OfflineCommands.isOfflineCapable(CommandResult.Weather(null)))
        assertFalse(OfflineCommands.isOfflineCapable(CommandResult.WebSearch("gatos")))
        assertFalse(OfflineCommands.isOfflineCapable(CommandResult.MorningBrief))
        assertFalse(OfflineCommands.isOfflineCapable(null))
    }

    @Test
    fun `offline refusal is in Spanish`() {
        val answer = OfflineCommands.offlineRefusal(russian = false)
        assertTrue(answer, answer.contains("conexión"))
        assertTrue(answer, answer.contains("teléfono"))
    }

    @Test
    fun `grammar includes common Spanish phone actions`() {
        val joined = OfflineCommands.GRAMMAR.joinToString(" ")
        listOf("linterna", "volumen", "cámara", "foto", "bluetooth", "no molestar").forEach {
            assertTrue("Offline grammar is missing '$it'", joined.contains(it))
        }
    }

    @Test
    fun `offline grammar avoids bare alarms and timers without a time`() {
        assertFalse(OfflineCommands.GRAMMAR.any { it == "alarma" })
        assertFalse(OfflineCommands.GRAMMAR.any { it == "temporizador" })
    }

    @Test
    fun `Spanish flashlight commands route to device control`() {
        assertTrue(router.route("enciende la linterna") is CommandResult.Flashlight)
        assertTrue(router.route("apaga la linterna") is CommandResult.Flashlight)
        assertTrue(router.route("linterna") is CommandResult.ToggleFlashlight)
    }
}

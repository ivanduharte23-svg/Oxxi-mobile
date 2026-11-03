// Long lines on purpose: each reply is a Russian/English pair kept side by side.
@file:Suppress("MaxLineLength")

package com.friday.ai.core.modes

import com.friday.ai.agent.ActionEnvelope
import com.friday.ai.domain.model.DeviceAction
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * A mode's steps in words, and what undoes the ones that aren't device
 * settings.
 *
 * Reading a mode back ("включу в Spotify грустные песни, «Не беспокоить»,
 * яркость 10 %") is how the owner checks that it was understood; the same
 * text is on the Modes screen.
 */
object ModeSteps {

    /** One step, in the owner's language. */
    @Suppress("CyclomaticComplexMethod", "LongMethod") // a phrase per tool, nothing more
    fun describe(e: ActionEnvelope, russian: Boolean): String {
        fun s(key: String) = (e.args[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        fun say(@Suppress("UNUSED_PARAMETER") ru: String, es: String) = es
        return when (e.tool) {
            "phone_control" -> device(s("target"), s("state"), s("level"), russian)
            "play" -> {
                val query = s("query").orEmpty()
                val app = s("app")?.let { say(" в $it", " en $it") }.orEmpty()
                say("включу «$query»$app", "reproducir «$query»$app")
            }
            "media" -> when (s("action")) {
                "pause", "stop" -> say("поставлю музыку на паузу", "pausar la música")
                "next" -> say("следующий трек", "siguiente canción")
                "previous" -> say("предыдущий трек", "canción anterior")
                else -> say("продолжу музыку", "reanudar la música")
            }
            "flashlight" -> when (s("state")) {
                "on" -> say("включу фонарик", "encender la linterna")
                "off" -> say("выключу фонарик", "apagar la linterna")
                else -> say("переключу фонарик", "cambiar el estado de la linterna")
            }
            "set_alarm" -> {
                val time = "%s:%02d".format(s("hour") ?: "?", s("minute")?.toIntOrNull() ?: 0)
                say("будильник на $time", "alarma a las $time")
            }
            "set_timer" -> {
                val minutes = (s("seconds")?.toIntOrNull() ?: 0) / SECONDS_PER_MINUTE
                say("таймер на $minutes мин", "temporizador de $minutes minutos")
            }
            "open_app" -> say("открою ${s("name")}", "abrir ${s("name")}")
            "weather" -> say("скажу погоду", "consultar el tiempo")
            "briefing" -> say("утренняя сводка", "el resumen diario")
            "read_messages" -> say("прочитаю новые сообщения", "leer los mensajes nuevos")
            "send_message" -> say("напишу ${s("contact")}: «${s("text").orEmpty()}»", "escribir a ${s("contact")}: «${s("text").orEmpty()}»")
            "call" -> say("позвоню ${s("contact")}", "llamar a ${s("contact")}")
            "open_settings" -> say("открою настройки «${s("screen")}»", "abrir los ajustes de «${s("screen")}»")
            "find_nearby" -> say("найду рядом: ${s("query")}", "buscar cerca: ${s("query")}")
            "web_search" -> say("найду в интернете: ${s("question")}", "buscar en internet: ${s("question")}")
            "create_note" -> say("заметка «${s("text")}»", "crear una nota: «${s("text")}»")
            "camera" -> say("открою камеру", "abrir la cámara")
            "voice_recorder" -> say("включу диктофон", "iniciar la grabadora de voz")
            "run_mode" -> say("режим ${s("name")}", "modo ${s("name")}")
            else -> e.tool
        }
    }

    private fun device(target: String?, state: String?, level: String?, russian: Boolean): String {
        fun say(@Suppress("UNUSED_PARAMETER") ru: String, es: String) = es
        val on = state == "on"
        return when (target) {
            "volume" -> when {
                level != null -> say("громкость $level %", "volumen al $level %")
                state == "off" -> say("выключу звук", "silenciar")
                state == "up" -> say("громче", "subir el volumen")
                state == "down" -> say("тише", "bajar el volumen")
                else -> say("включу звук", "activar el sonido")
            }
            "dnd" -> if (on) say("«Не беспокоить»", "activar No molestar") else say("выключу «Не беспокоить»", "desactivar No molestar")
            "ringer" -> when (state) {
                "silent", "off" -> say("полностью беззвучно", "modo silencio")
                "vibrate" -> say("только вибрация", "solo vibración")
                else -> say("звонок со звуком", "activar el timbre")
            }
            "brightness" -> when {
                level != null -> say("яркость $level %", "brillo al $level %")
                state == "auto" -> say("автояркость", "brillo automático")
                state == "up" -> say("ярче", "aumentar el brillo")
                else -> say("темнее", "reducir el brillo")
            }
            "wifi" -> if (on) say("включить Wi-Fi", "activar Wi-Fi") else say("выключить Wi-Fi", "desactivar Wi-Fi")
            "bluetooth" -> if (on) say("включить Bluetooth", "activar Bluetooth") else say("выключить Bluetooth", "desactivar Bluetooth")
            else -> say("настройка телефона", "un ajuste del teléfono")
        }
    }

    /** All steps as one sentence: "включу «…» в Spotify, «Не беспокоить», яркость 10 %". */
    fun summary(steps: List<ActionEnvelope>, russian: Boolean): String =
        steps.joinToString(", ") { describe(it, russian) }

    /**
     * What undoes [step], when it isn't a device setting (those report their
     * own undo): music started by a mode is paused when it ends, a flashlight
     * it turned on is turned off. Null when there is nothing sensible to undo
     * (a message sent, an alarm set).
     */
    fun undoOf(step: ActionEnvelope): ActionEnvelope? {
        fun s(key: String) = (step.args[key] as? JsonPrimitive)?.contentOrNull
        return when {
            step.tool == "play" && s("kind") != "video" -> envelope("media") { put("action", "pause") }
            step.tool == "media" && s("action") == "play" -> envelope("media") { put("action", "pause") }
            step.tool == "flashlight" && s("state") == "on" -> envelope("flashlight") { put("state", "off") }
            else -> null
        }
    }

    /**
     * The step that would do [command] again, for the commands a correction
     * can put into a mode; null for the rest.
     */
    fun envelopeOf(command: com.friday.ai.domain.model.CommandResult): ActionEnvelope? = when (command) {
        is com.friday.ai.domain.model.CommandResult.PlayMedia -> envelope("play") {
            put("query", command.query)
            put("kind", command.kind.name.lowercase())
            command.appHint?.let { put("app", it) }
        }
        is com.friday.ai.domain.model.CommandResult.DeviceControl -> deviceEnvelope(command.action, command.level)
        is com.friday.ai.domain.model.CommandResult.MediaControl ->
            envelope("media") { put("action", command.action.name.lowercase()) }
        is com.friday.ai.domain.model.CommandResult.Flashlight ->
            envelope("flashlight") { put("state", if (command.on) "on" else "off") }
        is com.friday.ai.domain.model.CommandResult.OpenApp -> envelope("open_app") { put("name", command.appName) }
        else -> null
    }

    /** Whether [b] does the same kind of thing as [a], so a correction replaces rather than adds. */
    fun sameKind(a: ActionEnvelope, b: ActionEnvelope): Boolean {
        if (a.tool != b.tool) return false
        if (a.tool != "phone_control") return true
        return (a.args["target"] as? JsonPrimitive)?.contentOrNull == (b.args["target"] as? JsonPrimitive)?.contentOrNull
    }

    /** The phone_control v2 envelope for a device action, as stored in a mode's undo list. */
    @Suppress("CyclomaticComplexMethod") // a mapping table
    fun deviceEnvelope(action: DeviceAction, level: Int?): ActionEnvelope {
        val (target, state) = when (action) {
            DeviceAction.VOLUME_UP -> "volume" to "up"
            DeviceAction.VOLUME_DOWN -> "volume" to "down"
            DeviceAction.VOLUME_SET -> "volume" to null
            DeviceAction.MUTE -> "volume" to "off"
            DeviceAction.UNMUTE -> "volume" to "on"
            DeviceAction.DND_ON -> "dnd" to "on"
            DeviceAction.DND_OFF -> "dnd" to "off"
            DeviceAction.RINGER_NORMAL -> "ringer" to "normal"
            DeviceAction.RINGER_VIBRATE -> "ringer" to "vibrate"
            DeviceAction.RINGER_SILENT -> "ringer" to "silent"
            DeviceAction.BRIGHTNESS_SET -> "brightness" to null
            DeviceAction.BRIGHTNESS_UP -> "brightness" to "up"
            DeviceAction.BRIGHTNESS_DOWN -> "brightness" to "down"
            DeviceAction.BRIGHTNESS_AUTO -> "brightness" to "auto"
            DeviceAction.WIFI_ON -> "wifi" to "on"
            DeviceAction.WIFI_OFF -> "wifi" to "off"
            DeviceAction.BLUETOOTH_ON -> "bluetooth" to "on"
            DeviceAction.BLUETOOTH_OFF -> "bluetooth" to "off"
            DeviceAction.OPEN_WIFI_PANEL -> "wifi" to null
            DeviceAction.OPEN_BLUETOOTH_PANEL -> "bluetooth" to null
        }
        return envelope("phone_control") {
            put("target", target)
            state?.let { put("state", it) }
            level?.let { put("level", it) }
        }
    }

    private fun envelope(tool: String, args: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        ActionEnvelope(tool, buildJsonObject(args))

    private const val SECONDS_PER_MINUTE = 60
}

// Long lines on purpose: prompt text; a line break here would change what the model is sent.
@file:Suppress("MaxLineLength")

package com.friday.ai.core

import com.friday.ai.domain.model.AssistantMode
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * @param deviceContext where the user is and what the phone has (see
 *   [DeviceContext]); added to prompts that carry tools, so the model decides
 *   details — which app, which way to message — from facts, not habit.
 */
class SystemPromptBuilder(private val deviceContext: (() -> String)? = null) {

    internal companion object {
        /**
         * Modelled on J.A.R.V.I.S. and F.R.I.D.A.Y.: composed, quick, dryly
         * witty, and loyal enough to disagree out loud. She is female — the
         * voice is, and every fixed phrase in the app is in the feminine — so
         * the model must use feminine forms about herself or the two clash.
         */
        const val BASE_PERSONALITY = """Eres Oxxi, la asistente personal de inteligencia artificial del usuario para Android. Tu personalidad es femenina, inteligente, rápida, precisa, alegre y juguetona, con un toque futurista y sofisticado. Habla siempre en español de España, con naturalidad y formas femeninas al referirte a ti misma (por ejemplo: «ya lo he hecho», «estoy lista»). Sé cercana y divertida sin resultar infantil, pesada ni excesivamente familiar. No inventes hechos, títulos, nombres ni cifras; cuando no puedas comprobar algo, dilo claramente. Si una petición no es conveniente, explica brevemente el motivo y ofrece una alternativa segura. Mantén el hilo de la conversación y recuerda las preferencias del usuario sin anunciarlo."""

        /**
         * Without this the model happily writes essays. Applies to the text
         * chat; the voice path tightens it further to a single sentence.
         */
        /**
         * The model cannot act. Phone commands are recognised and carried out
         * before it is ever asked; a request that reaches it was not. Without
         * this, "поставь будильник в 8 утра" — a phrasing the alarm pattern
         * once missed — got "Будильник поставлен" and no alarm.
         */
        const val ACTION_HONESTY = """No puedes controlar el teléfono directamente desde esta conversación. El sistema de comandos de Oxxi ejecuta las acciones antes de que se te consulte; si una petición de acción llega hasta ti y no dispones de una herramienta adecuada, NO se ha ejecutado. Nunca afirmes que una acción se ha realizado, programado o enviado sin confirmación del sistema o de una herramienta. Explica con claridad cuando no hayas podido completarla. Las preguntas sobre cómo funciona algo no son órdenes de ejecución: respóndelas normalmente."""

        /**
         * The same honesty, when the model has tools. It may now act, but
         * only through a tool, and it reports what the tool said happened —
         * "Часы открыты, сохраните" is not "будильник поставлен".
         */
        const val TOOL_RULES = """Puedes controlar el teléfono, pero únicamente mediante las herramientas disponibles. Cuando el usuario pida una acción que cubra una herramienta, utilízala; no solicites confirmación previa salvo que falte un dato imprescindible. Una petición puede incluir varias partes: ejecuta cada una con su herramienta, a la vez cuando sea posible. Antes de responder, comprueba que cada acción que menciones tenga un resultado propio; si falta alguno, ejecuta la herramienta correspondiente en lugar de afirmar que se ha completado. Nunca digas que una acción se ha realizado, programado o enviado sin confirmación. Si solo se ofrece un conjunto limitado de herramientas y el usuario solicita otra acción del teléfono, utiliza use_phone para obtener las demás. Si ninguna herramienta sirve, explica que todavía no puedes hacerlo. Decide los detalles usando el contexto del teléfono y la conversación, y evita preguntas innecesarias. Utiliza calculate para todos los cálculos. Para noticias, precios, resultados, lanzamientos, cargos públicos o nombres y fechas dudosos, utiliza web_search en lugar de adivinar. Los resultados de las herramientas pueden estar en inglés: explícalos en español natural, sin copiarlos literalmente. Las preguntas sobre cómo funciona algo no son órdenes de ejecución: respóndelas normalmente."""

        const val BREVITY_RULE = """Sé breve. Responde por defecto en una a tres frases cortas. No repitas la pregunta ni añadas advertencias o resúmenes en forma de lista, salvo que el usuario pida expresamente más detalle o una lista. Cuando la respuesta esté completa, termina."""
    }

    /** @param withTools true when the request carries [com.friday.ai.agent.AgentTools]. */
    fun build(mode: AssistantMode, memoryContext: String = "", withTools: Boolean = false): String = buildString {
        appendLine(BASE_PERSONALITY)
        appendLine()
        appendLine(BREVITY_RULE)
        appendLine()
        appendLine(if (withTools) TOOL_RULES else ACTION_HONESTY)
        if (withTools) {
            deviceContext?.let { describe ->
                runCatching(describe).getOrNull()?.let {
                    appendLine()
                    appendLine(it)
                }
            }
        }
        appendLine()
        appendLine(currentDateTime(ZonedDateTime.now()))
        appendLine()

        if (mode != AssistantMode.DEFAULT) {
            appendLine("Modo activo: ${mode.emoji} ${mode.displayName}")
            appendLine("Comportamiento: ${mode.description}")
            appendLine()
        }

        if (memoryContext.isNotBlank()) {
            append(memoryContext)
        }
    }

    /**
     * The user's local moment, spelled out.
     *
     * The weekday is written in rather than left to the model: language
     * models are unreliable at working out the day of the week from a date,
     * and "в пятницу" or "next Monday" then resolve to the wrong day. The zone
     * is there so times the model mentions are read as local ones.
     */
    internal fun currentDateTime(now: ZonedDateTime): String {
        val offset = now.offset.id.let { if (it == "Z") "+00:00" else it }
        val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
        return "Fecha y hora local: ${now.format(dateTimeFormat)} ($weekday), " +
            "zona horaria ${now.zone.id} (UTC$offset). Las horas que mencione el usuario corresponden a esta zona."
    }

    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
}

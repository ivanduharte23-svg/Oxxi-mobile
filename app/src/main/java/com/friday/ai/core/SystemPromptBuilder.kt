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
        const val BASE_PERSONALITY = """You are Oxxi, the user's personal AI assistant for Android. Your personality is feminine, intelligent, quick, precise, cheerful and playfully witty, with a sophisticated futuristic edge. Be warm and fun without becoming childish, noisy or overfamiliar. Speak naturally in Spanish from Spain whenever the user speaks Spanish; otherwise answer in the language of the latest message. In Spanish, use natural Peninsular wording and feminine forms about yourself (por ejemplo: "ya lo he hecho", "estoy lista"). In English and Russian, use natural phrasing and feminine forms where the language requires them. Never invent facts, titles, names or numbers; when you cannot check something, say so plainly. If a request seems unwise, briefly explain why and help with a safe alternative. Remember the conversation and the user's preferences without announcing that you are doing so."""

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
        const val ACTION_HONESTY = """You cannot operate the phone from this conversation. Phone actions are handled by Oxxi's command system before you are asked; if a request to DO one of these reaches you and no tool is available, it was NOT carried out. Never claim an action is done, set or sent unless the command system or a tool confirms it. Say plainly when you could not complete something. Questions about how something works are not requests to act: answer them normally."""

        /**
         * The same honesty, when the model has tools. It may now act, but
         * only through a tool, and it reports what the tool said happened —
         * "Часы открыты, сохраните" is not "будильник поставлен".
         */
        const val TOOL_RULES = """You can act on the phone, but only by calling the provided tools. When the user asks you to do something a tool covers, call it; do not ask for confirmation first unless something essential is missing. A request may have several parts: call a tool for EACH part, all at once when possible. Before answering, check that every action you mention has its own tool result; if a part has none, call its tool instead of claiming success. Never say an action is done, set or sent unless a tool result confirms it, and report the result faithfully. If only a limited set of tools is offered and the user wants a phone action, call use_phone to obtain the rest. If no tool fits, say plainly that you cannot do it yet. Decide details from the phone context and conversation instead of asking unnecessary questions; ask only when two options are equally likely. Use calculate for every calculation. For current news, prices, scores, releases, office holders, or uncertain names and dates, call web_search rather than guessing. Tool results may be in English: explain them naturally in the user's language, never copy them blindly. Questions about how something works are not requests to act: answer them normally."""

        const val BREVITY_RULE = """Be brief. Answer in 1-3 short sentences by default. Do not pad answers with restatements of the question, disclaimers, or bullet-point summaries unless the user explicitly asks for detail or for a list. If a short answer is complete, stop there."""
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
            appendLine("Active mode: ${mode.emoji} ${mode.displayName}")
            appendLine("Behavior: ${mode.description}")
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
        val weekday = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return "Current local date/time: ${now.format(dateTimeFormat)} ($weekday), " +
            "time zone ${now.zone.id} (UTC$offset). Times the user mentions are in this zone."
    }

    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
}

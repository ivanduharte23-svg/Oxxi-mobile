package com.friday.ai.core.messages

import com.friday.ai.core.ContactMatcher

/** One incoming message, as the messenger's notification shows it. */
data class InboxMessage(val sender: String, val text: String, val time: Long)

/** A chat that has notified recently — a person or a group, in one app. */
data class Conversation(
    val key: String,
    val packageName: String,
    val app: String,
    val title: String,
    val messages: List<InboxMessage>,
    val canReply: Boolean,
    val updatedAt: Long,
    val isGroup: Boolean = false
)

/** Finding and retelling chats, with Oxxi's Spanish wording. */
object Conversations {
    private val PRONOUNS = setOf("le", "les", "él", "ella", "ellos", "ellas", "him", "her", "them", "он", "она", "они")
    private const val SPOKEN_MESSAGE_CHARS = 160
    private const val SPOKEN_MESSAGES = 3

    fun isPronoun(who: String?): Boolean = who?.trim()?.lowercase() in PRONOUNS

    fun find(who: String?, chats: List<Conversation>): Conversation? {
        val newestFirst = chats.sortedByDescending { it.updatedAt }
        if (who.isNullOrBlank() || isPronoun(who)) return newestFirst.firstOrNull()
        return ContactMatcher.ranked(who, newestFirst) { it.title }.firstOrNull()?.first
    }

    @Suppress("UNUSED_PARAMETER")
    fun retell(chats: List<Conversation>, russian: Boolean): String {
        if (chats.isEmpty()) return "No hay mensajes nuevos."
        return chats.sortedByDescending { it.updatedAt }.joinToString(" ") { chat ->
            val said = chat.messages.takeLast(SPOKEN_MESSAGES).joinToString(" ") { message ->
                val who = if (chat.isGroup) "${message.sender}: " else ""
                "«$who${message.text.take(SPOKEN_MESSAGE_CHARS)}»"
            }
            "${chat.title} en ${chat.app}: $said."
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun incomingCall(caller: String, app: String?, russian: Boolean): String =
        if (app == null) "$caller te está llamando."
        else "$caller te está llamando por $app."

    @Suppress("UNUSED_PARAMETER")
    fun confirmReply(chat: Conversation, text: String, russian: Boolean): String =
        "¿Quieres responder a ${chat.title} en ${chat.app}: «$text»?"
}

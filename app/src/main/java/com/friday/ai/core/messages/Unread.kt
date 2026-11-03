package com.friday.ai.core.messages

import com.friday.ai.core.ContactMatcher

/** Helpers for naming and reading the user's unread conversations, in Spanish. */
object Unread {
    private const val LONG_MESSAGES = 2
    private const val LONG_CHARS = 200
    private const val MAX_PICK_WORDS = 3
    private const val PICK_SCORE = ContactMatcher.FUZZY

    private val PICK_FILLER = setOf(
        "vamos", "primero", "empieza", "empecemos", "con", "de", "lee", "leer", "léelo", "por", "favor",
        "first", "from", "read", "please", "start", "with"
    )

    @Suppress("UNUSED_PARAMETER")
    fun whoWrote(chats: List<Conversation>, russian: Boolean): String =
        "Tienes mensajes de: ${names(chats)}. ¿Cuál quieres que lea primero?"

    @Suppress("UNUSED_PARAMETER")
    fun stillUnread(chats: List<Conversation>, russian: Boolean): String =
        "Siguen sin leerse: ${names(chats)}."

    @Suppress("UNUSED_PARAMETER")
    fun tell(chat: Conversation, summary: String?, russian: Boolean): String {
        if (summary == null) return Conversations.retell(listOf(chat), false)
        val brief = summary.trim().trimEnd('.')
        return "${chat.title} en ${chat.app}. En resumen: $brief."
    }

    fun isLong(chat: Conversation): Boolean =
        chat.messages.size > LONG_MESSAGES || chat.messages.sumOf { it.text.length } > LONG_CHARS

    fun picked(text: String, chats: List<Conversation>): Conversation? {
        val words = text.lowercase().split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.isNotBlank() && it !in PICK_FILLER }
        if (words.isEmpty() || words.size > MAX_PICK_WORDS) return null
        return ContactMatcher.ranked(words.joinToString(" "), chats) { it.title }.firstOrNull()
            ?.takeIf { it.second >= PICK_SCORE }?.first
    }

    private fun names(chats: List<Conversation>): String {
        val names = chats.sortedByDescending { it.updatedAt }.map { it.title }
        if (names.size == 1) return names.single()
        return names.dropLast(1).joinToString(", ") + " y " + names.last()
    }
}

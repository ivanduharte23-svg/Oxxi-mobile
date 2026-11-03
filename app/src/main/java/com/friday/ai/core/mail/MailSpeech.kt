package com.friday.ai.core.mail

/**
 * What Oxxi says about mail, kept short enough to be listened to.
 *
 * Sender names stay in the nominative rather than being forced into a
 * grammatical case: arbitrary names cannot safely be declined.
 */
object MailSpeech {

    /** How many senders are named before the rest are just counted. */
    const val NAMED = 3

    @Suppress("UNUSED_PARAMETER")
    fun unread(total: Int, newest: List<GmailMessages.Mail>, russian: Boolean): String {
        if (total <= 0 || newest.isEmpty()) return "No hay correos nuevos."
        val count = maxOf(total, newest.size)
        val head = "$count ${if (count == 1) "correo sin leer" else "correos sin leer"}."
        val named = newest.take(NAMED).joinToString(" ") { line(it) }
        val rest = count - minOf(NAMED, newest.size)
        val tail = if (rest <= 0) "" else " Y $rest más."
        return "$head $named$tail"
    }

    private fun line(m: GmailMessages.Mail): String {
        val subject = m.subject.ifBlank { m.snippet.take(SUBJECT_FALLBACK) }
        return if (subject.isBlank()) "${m.fromName}." else "${m.fromName} — «$subject»."
    }

    /** Opening of a read-out: who and about what. */
    @Suppress("UNUSED_PARAMETER")
    fun header(m: GmailMessages.Mail, russian: Boolean): String {
        val subject = m.subject.takeIf { it.isNotBlank() }?.let { "«$it»" }
        return if (subject == null) "${m.fromName} ha escrito."
        else "${m.fromName} ha escrito: $subject."
    }

    @Suppress("UNUSED_PARAMETER")
    fun confirmReply(name: String, subject: String, body: String, russian: Boolean): String =
        "Respuesta a «$subject» ($name): «$body». ¿La envío?"

    @Suppress("UNUSED_PARAMETER")
    fun confirmNew(name: String, address: String, subject: String, body: String, russian: Boolean): String =
        "Nuevo correo: $name, $address. Asunto: «$subject». Mensaje: «$body». ¿Lo envío?"

    private const val SUBJECT_FALLBACK = 60
}

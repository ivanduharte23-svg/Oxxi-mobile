package com.friday.ai.core

import java.time.LocalTime
import java.util.Locale

/** Builds concise Spanish voice summaries from notifications, weather and agenda items. */
object BriefComposer {
    data class NotificationSummary(val appName: String, val title: String, val text: String)
    data class AgendaItem(val title: String, val timeLabel: String)

    private const val MAX_APPS_NAMED = 3
    private const val MAX_AGENDA_ITEMS = 4

    @Suppress("UNUSED_PARAMETER")
    fun missedSummary(notifications: List<NotificationSummary>, russian: Boolean): String {
        if (notifications.isEmpty()) return "No hay notificaciones nuevas."
        val byApp = notifications.groupBy { it.appName }.entries.sortedByDescending { it.value.size }
        val total = notifications.size
        val named = byApp.take(MAX_APPS_NAMED).joinToString(", ") { (app, items) ->
            if (items.size == 1) app else "$app (${items.size})"
        }
        val remaining = byApp.size - MAX_APPS_NAMED
        return buildString {
            append("Tienes $total ${if (total == 1) "notificación" else "notificaciones"}: $named")
            if (remaining > 0) append(" y $remaining más")
            append(".")
            if (total == 1) {
                val only = notifications.first()
                val body = listOf(only.title, only.text).filter { it.isNotBlank() }.joinToString(" — ")
                if (body.isNotBlank()) append(" $body")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun morningBrief(
        weather: String?,
        agenda: List<AgendaItem>,
        missedCount: Int,
        russian: Boolean,
        localTime: LocalTime
    ): String {
        val parts = mutableListOf<String>()
        parts += BriefTiming.greeting(localTime, false)
        weather?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (agenda.isEmpty()) {
            parts += "Hoy no tienes eventos en el calendario."
        } else {
            val shown = agenda.take(MAX_AGENDA_ITEMS)
                .joinToString(", ") { "${it.title} a las ${it.timeLabel}" }
            val extra = agenda.size - MAX_AGENDA_ITEMS
            parts += "Hoy: $shown" + if (extra > 0) " y $extra más." else "."
        }
        if (missedCount > 0) {
            val unread = if (missedCount == 1) "notificación sin leer" else "notificaciones sin leer"
            parts += "Tienes $missedCount $unread."
        }
        return parts.joinToString(" ")
    }

    /** Russian plural forms retained for legacy mail formatting compatibility. */
    internal fun pluralRu(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        if (mod100 in 11..14) return many
        return when (n % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }

    fun timeLabel(hour: Int, minute: Int): String =
        String.format(Locale.US, "%d:%02d", hour, minute)
}

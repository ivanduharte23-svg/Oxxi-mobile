package com.friday.ai.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Turns spoken time expressions into an actual instant.
 *
 * Deliberately narrow: it handles the phrasings people actually use when
 * telling an assistant to remember something ("завтра в 15:00", "через час",
 * "in 2 hours", "в понедельник в 10"), and returns null for anything it
 * isn't confident about, so a misread never silently books the wrong slot.
 */
object DateTimeParser {

    /**
     * @param instant when the event starts
     * @param hadExplicitTime false when only a day was given, so the caller
     *        can fall back to a sensible default hour instead of midnight.
     */
    data class Parsed(val instant: LocalDateTime, val hadExplicitTime: Boolean)

    /** Used when a day is named without a time ("напомни завтра"). */
    const val DEFAULT_HOUR = 9

    /**
     * How far ahead a reminder fires by default. Ten minutes is too late to
     * act on anything that needs travel or preparation, so an hour is the
     * more useful default for a personal assistant.
     */
    const val DEFAULT_REMINDER_MINUTES = 60

    private val LEAD_TIME = Regex(
        """(?:за|)\s*(\d+)?\s*(минут\p{L}*|мин|час\p{L}*|hours?|minutes?|mins?)\s*(?:до|before)""",
        RegexOption.IGNORE_CASE
    )

    private val SPANISH_LEAD = Regex(
        """(?:con\s+)?(?:(\d+|un|uno|una)\s+)?(minuto\p{L}*|hora\p{L}*)\s+antes\s+de""",
        RegexOption.IGNORE_CASE
    )

    private val BARE_LEAD = Regex(
        """за\s+(?:(\d+)\s*)?(минут\p{L}*|мин|час\p{L}*)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Reads how long before the event the reminder should fire:
     * "за час" → 60, "за 2 часа" → 120, "за 30 минут" → 30.
     * Returns null when the user didn't say, so the caller keeps its default.
     */
    fun parseReminderLeadMinutes(text: String): Int? {
        val lower = text.lowercase()
        val match = SPANISH_LEAD.find(lower) ?: LEAD_TIME.find(lower) ?: BARE_LEAD.find(lower) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: 1
        val unit = match.groupValues[2]
        val minutes = when {
            unit.startsWith("hora") || unit.startsWith("час") || unit.startsWith("hour") -> amount * 60
            else -> amount
        }
        return minutes.takeIf { it in 1..(14 * 24 * 60) }
    }

    private val WEEKDAYS: Map<String, DayOfWeek> = mapOf(
        "понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY,
        "среда" to DayOfWeek.WEDNESDAY, "среду" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY, "пятниц" to DayOfWeek.FRIDAY,
        "суббот" to DayOfWeek.SATURDAY, "воскресень" to DayOfWeek.SUNDAY,
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY,
        "miércoles" to DayOfWeek.WEDNESDAY, "miercoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY, "viernes" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY
    )

    private val RELATIVE_UNITS = Regex(
        """(?:через|in|en|dentro de)\s+(\d+)\s*(minuto\p{L}*|min|hora\p{L}*|día\p{L}*|dia\p{L}*|semana\p{L}*|минут\w*|мин|час\w*|дн\w*|день|недел\w*|minutes?|mins?|hours?|days?|weeks?)""",
        RegexOption.IGNORE_CASE
    )

    private val EXPLICIT_TIME = Regex(
        """(?:в|at|к|a\s+las?|a|en)\s*(\d{1,2})(?:[:.](\d{2}))?\s*(de\s+la\s+mañana|de\s+la\s+tarde|de\s+la\s+noche|madrugada|mañana|tarde|noche|утра|дня|вечера|ночи|am|pm)?""",
        RegexOption.IGNORE_CASE
    )

    private val BARE_TIME = Regex("""\b(\d{1,2})[:.](\d{2})\b""")

    fun parse(text: String, now: LocalDateTime): Parsed? {
        val lower = text.lowercase()

        relativeOffset(lower, now)?.let { return Parsed(it, hadExplicitTime = true) }

        val day = resolveDay(lower, now)
        val time = resolveTime(lower)

        return when {
            time != null -> {
                val base = day ?: now.toLocalDate()
                var candidate = LocalDateTime.of(base, time)
                // "в 9" said at 22:00 with no day means tomorrow morning.
                if (day == null && candidate.isBefore(now)) candidate = candidate.plusDays(1)
                Parsed(candidate, hadExplicitTime = true)
            }
            day != null ->
                Parsed(LocalDateTime.of(day, LocalTime.of(DEFAULT_HOUR, 0)), hadExplicitTime = false)
            else -> null
        }
    }

    /** Convenience overload for callers that only have a zone. */
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Parsed? =
        parse(text, LocalDateTime.now(zone))

    private fun relativeOffset(text: String, now: LocalDateTime): LocalDateTime? {
        val m = RELATIVE_UNITS.find(text) ?: return null
        val amount = m.groupValues[1].toLongOrNull() ?: return null
        val unit = m.groupValues[2]
        return when {
            unit.startsWith("min") || unit.startsWith("мин") -> now.plusMinutes(amount)
            unit.startsWith("hora") || unit.startsWith("час") || unit.startsWith("hour") -> now.plusHours(amount)
            unit.startsWith("día") || unit.startsWith("dia") || unit.startsWith("дн") || unit.startsWith("день") || unit.startsWith("day") ->
                now.plusDays(amount)
            unit.startsWith("semana") || unit.startsWith("недел") || unit.startsWith("week") -> now.plusWeeks(amount)
            else -> null
        }
    }

    private fun resolveDay(text: String, now: LocalDateTime) = when {
        text.contains("послезавтра") || text.contains("pasado mañana") || text.contains("day after tomorrow") ->
            now.toLocalDate().plusDays(2)
        text.contains("завтра") || text.contains("mañana") || text.contains("tomorrow") ->
            now.toLocalDate().plusDays(1)
        text.contains("сегодня") || text.contains("hoy") || text.contains("today") ->
            now.toLocalDate()
        else -> WEEKDAYS.entries
            .firstOrNull { (word, _) -> text.contains(word) }
            ?.let { (_, dow) -> now.toLocalDate().with(TemporalAdjusters.next(dow)) }
    }

    private fun resolveTime(text: String): LocalTime? {
        EXPLICIT_TIME.find(text)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            val qualifier = m.groupValues[3]
            val adjusted = applyDayPart(hour, qualifier)
            if (adjusted in 0..23 && minute in 0..59) return LocalTime.of(adjusted, minute)
        }
        BARE_TIME.find(text)?.let { m ->
            val hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: return@let
            if (hour in 0..23 && minute in 0..59) return LocalTime.of(hour, minute)
        }
        return null
    }

    /** "в 3 дня" is 15:00; "at 3pm" is 15:00; "в 8 утра" stays 08:00. */
    private fun applyDayPart(hour: Int, qualifier: String): Int = when {
        qualifier.isBlank() -> hour
        qualifier.startsWith("mañana") || qualifier.startsWith("madrugada") || qualifier.contains("de la mañana") || qualifier == "am" || qualifier.contains("de la madrugada") -> if (hour == 12) 0 else hour
        qualifier.startsWith("ноч") -> if (hour >= 12) hour else (hour % 12)
        qualifier.startsWith("tarde") || qualifier.startsWith("вечера") || qualifier.startsWith("дня") || qualifier.contains("de la tarde") || qualifier.contains("de la noche") || qualifier == "pm" ->
            if (hour < 12) hour + 12 else hour
        else -> hour
    }
}

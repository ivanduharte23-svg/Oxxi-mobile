package com.friday.ai.core.modes

import com.friday.ai.core.DateTimeParser
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

/** When a mode turns on ([exit] false) or off by itself. */
data class Schedule(
    val id: String,
    val modeId: String,
    val exit: Boolean,
    val time: LocalTime,
    val days: Set<DayOfWeek>,
    val lastFiredAt: Long = 0
)

/** "при подключении к «Toyota Touch»", "когда телефон ставят на зарядку". */
@Suppress("UNUSED_PARAMETER")
fun describeEvent(e: ModeEvent, russian: Boolean): String = when (e.trigger) {
    Trigger.CHARGER -> if (e.onConnect) "cuando el teléfono se conecta al cargador"
        else "cuando el teléfono se desconecta del cargador"
    Trigger.BLUETOOTH -> if (e.onConnect) "cuando se conecta a «${e.value}»"
        else "cuando se desconecta de «${e.value}»"
    Trigger.WIFI -> if (e.onConnect) "cuando se conecta a la red «${e.value}»"
        else "cuando se desconecta de la red «${e.value}»"
}

/** Day sets as one bitmask (Monday = bit 0), the way they are stored. */
object Days {
    val ALL: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    val WEEKDAYS: Set<DayOfWeek> = ALL - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun mask(days: Set<DayOfWeek>): Int = days.fold(0) { m, d -> m or (1 shl (d.value - 1)) }
    fun of(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
}

object ScheduleMath {

    /** The next time [s] fires after [now]: today if still ahead, else the next allowed day. */
    fun next(s: Schedule, now: LocalDateTime): LocalDateTime {
        val days = s.days.ifEmpty { Days.ALL }
        var candidate = now.toLocalDate().atTime(s.time)
        repeat(DAYS_TO_SCAN) {
            if (candidate.isAfter(now) && candidate.dayOfWeek in days) return candidate
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    /** "каждый день в 23:00", "по будням в 9:00", "по пн, ср в 7:30". */
    @Suppress("UNUSED_PARAMETER")
    fun describe(days: Set<DayOfWeek>, time: LocalTime, russian: Boolean): String {
        val at = "%d:%02d".format(time.hour, time.minute)
        val which = when (days) {
            Days.ALL, emptySet<DayOfWeek>() -> "todos los días"
            Days.WEEKDAYS -> "entre semana"
            Days.WEEKEND -> "los fines de semana"
            else -> "los " + days.sorted().joinToString(", ") { short(it) }
        }
        return "$which a las $at"
    }

    private val ES_DAYS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

    private fun short(d: DayOfWeek): String = ES_DAYS[d.value - 1]

    private const val DAYS_TO_SCAN = 8
    private const val ABBREVIATION = 3
}

/**
 * Schedule requests in words: "включай режим отдыха каждый день в 23:00",
 * "каждый вечер в 11 режим отдыха", "по будням в 7 утра выключай режим сна",
 * "убери расписание режима отдыха".
 */
object SchedulePhrases {

    sealed interface Request {
        /** [rest]: the words after "режим", name first; the engine finds which mode it is. */
        data class Set(
            val rest: String,
            val exit: Boolean,
            val time: LocalTime?,
            val days: kotlin.collections.Set<DayOfWeek>
        ) : Request
        data class Clear(val rest: String) : Request
    }

    private val cue = Regex(
        "(?:кажд\\p{L}*|ежедневно|по будням|в будни|по выходным|в выходные|" +
            "по (?:понедельникам|вторникам|средам|четвергам|пятницам|субботам|воскресеньям)|" +
            "cada\\s+(?:día|dia|noche|mañana|lunes|martes|miércoles|jueves|viernes|sábado|domingo)|" +
            "diariamente|entre semana|los fines de semana|los (?:lunes|martes|miércoles|jueves|viernes|sábados|domingos)|" +
            "every|daily|weekdays|weekends)"
    )
    private val clear = Regex("(?:расписани|по времени|horari|programaci|schedule)")
    private val clearVerb = Regex("^(?:убери|отмени|удали|сними|quita|elimina|borra|cancela|desactiva|no\\s|stop|remove|cancel|delete)")
    private val exitVerb = Regex("(?:^|\\s)(?:выключай|отключай|выключать|отключать|выходи|apaga|desactiva|desconecta|termina|sal|turn off|switch off|end)\\s")
    private val modeWord = Regex("(?:режим\\p{L}*|modo|mode)\\s+(.+)$")
    private val evening = Regex("(?:вечер|ночь|ночи|tarde|noche|evening|night)")
    private val wake = Regex("^(?:пятница|friday|oxxi)[,!.\\s]+")
    private val trailingSchedule = Regex("\\s*(?:по расписанию|con horario|por horario).*$")
    private val leadingSchedule = Regex("^(?:расписани\\p{L}*|horario|programación)\\s+")

    private val weekdayStems = mapOf(
        "понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY, "сред" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY, "пятниц" to DayOfWeek.FRIDAY, "суббот" to DayOfWeek.SATURDAY,
        "воскресень" to DayOfWeek.SUNDAY,
        "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY, "miércoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY, "viernes" to DayOfWeek.FRIDAY, "sábado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY
    )

    private const val NOON = 12
    private const val LATE_NIGHT = 4

    fun parse(text: String, now: LocalDateTime): Request? {
        val t = text.trim().lowercase().replace('ё', 'е').replace(wake, "").trimEnd('.', '!', '?', ' ')
        val after = modeWord.find(t)?.groupValues?.get(1) ?: return null
        return when {
            clear.containsMatchIn(t) && clearVerb.containsMatchIn(t) ->
                Request.Clear(after.replace(trailingSchedule, "").replace(leadingSchedule, ""))
            !cue.containsMatchIn(t) -> null
            else -> Request.Set(
                rest = after, exit = exitVerb.containsMatchIn(" $t "), time = time(t, now), days = days(t)
            )
        }
    }

    private fun time(t: String, now: LocalDateTime): LocalTime? {
        val parsed = DateTimeParser.parse(t, now)?.takeIf { it.hadExplicitTime } ?: return null
        var time = parsed.instant.toLocalTime().withSecond(0).withNano(0)
        // "каждый вечер в 11" is 23:00; "каждую ночь в 2" stays 2:00.
        if (evening.containsMatchIn(t) && time.hour in LATE_NIGHT until NOON) time = time.plusHours(NOON.toLong())
        return time
    }

    private fun days(t: String): Set<DayOfWeek> = when {
        Regex("будн|weekday|entre semana|días laborables").containsMatchIn(t) -> Days.WEEKDAYS
        Regex("выходн|weekend|fin de semana").containsMatchIn(t) -> Days.WEEKEND
        else -> weekdayStems.filterKeys { Regex("(?:по\\s+|los\\s+)?$it").containsMatchIn(t) }.values.toSet().ifEmpty { Days.ALL }
    }
}

/**
 * "You turn it on around 23:00 every evening": a time of day the owner
 * keeps running a mode at by hand, worth offering as a schedule.
 */
object Habits {

    /** Runs on at least this many different days, close together in time of day. */
    const val MIN_DAYS = 3
    private val CLOSE = Duration.ofMinutes(45)
    private const val ROUND_TO = 5
    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60
    private const val HOURS_PER_DAY = 24
    private const val DAY = HOURS_PER_DAY * MINUTES_PER_HOUR
    private const val HALF_DAY = DAY / 2

    fun suggest(runs: List<LocalDateTime>): LocalTime? {
        val perDay = runs.groupBy { it.toLocalDate() }.values.map { it.first().toLocalTime() }
        if (perDay.size < MIN_DAYS) return null
        val best = perDay
            .map { anchor -> perDay.filter { minutesApart(it, anchor) <= CLOSE.toMinutes() } }
            .maxBy { it.size }
        if (best.size < MIN_DAYS) return null
        var minutes = best.map { it.toSecondOfDay() / SECONDS_PER_MINUTE }.sorted()
        // A cluster across midnight (23:50, 00:10): count the small hours as the day before's late ones.
        if (minutes.last() - minutes.first() > HALF_DAY) {
            minutes = minutes.map { if (it < HALF_DAY) it + DAY else it }.sorted()
        }
        val median = minutes[minutes.size / 2]
        val rounded = (median + ROUND_TO / 2) / ROUND_TO * ROUND_TO
        return LocalTime.of((rounded / MINUTES_PER_HOUR) % HOURS_PER_DAY, rounded % MINUTES_PER_HOUR)
    }

    /** Across midnight too: 23:50 and 00:10 are 20 minutes apart. */
    private fun minutesApart(a: LocalTime, b: LocalTime): Long {
        val d = abs(a.toSecondOfDay() - b.toSecondOfDay()) / SECONDS_PER_MINUTE.toLong()
        return minOf(d, DAY - d)
    }
}

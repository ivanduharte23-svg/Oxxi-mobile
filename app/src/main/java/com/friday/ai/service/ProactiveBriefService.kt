package com.friday.ai.service

import android.content.Context
import android.util.Log
import com.friday.ai.core.BriefComposer
import com.friday.ai.core.CalendarWriter
import com.friday.ai.data.local.dao.NotificationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Builds the "what did I miss" answer and the optional daily briefing. */
class ProactiveBriefService(
    private val context: Context,
    private val notificationDao: NotificationDao,
    private val calendarWriter: CalendarWriter,
    private val weatherHere: WeatherHere
) {
    private companion object {
        const val TAG = "ProactiveBrief"
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun whatDidIMiss(russian: Boolean): String = withContext(Dispatchers.IO) {
        if (!FridayNotificationListener.isEnabled(context)) {
            FridayNotificationListener.openSettings(context)
            return@withContext "Necesito acceso a las notificaciones; he abierto los ajustes."
        }
        val since = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val unseen = runCatching { notificationDao.unseenSince(since) }.getOrDefault(emptyList())
        val summary = BriefComposer.missedSummary(
            unseen.map { BriefComposer.NotificationSummary(it.appName, it.title, it.text) },
            false
        )
        runCatching { notificationDao.markSeen(System.currentTimeMillis()) }
        summary
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun morningBrief(russian: Boolean): String = withContext(Dispatchers.IO) {
        val weather = runCatching {
            weatherHere.summary(place = null, dayOffset = 0, russian = false)
        }.getOrNull()
        val agenda = runCatching { todaysAgenda() }.getOrDefault(emptyList())
        val missed = runCatching {
            if (FridayNotificationListener.isEnabled(context)) notificationDao.unseenCount() else 0
        }.getOrDefault(0)
        BriefComposer.morningBrief(weather, agenda, missed, false, java.time.LocalTime.now())
    }

    private fun todaysAgenda(): List<BriefComposer.AgendaItem> {
        val now = System.currentTimeMillis()
        val endOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis
        return calendarWriter.upcomingEvents(fromMillis = now, limit = 10)
            .filter { it.startMillis <= endOfDay }
            .map { event ->
                val cal = Calendar.getInstance().apply { timeInMillis = event.startMillis }
                BriefComposer.AgendaItem(
                    title = event.title.ifBlank { "evento" },
                    timeLabel = BriefComposer.timeLabel(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                )
            }
            .also { Log.i(TAG, "Agenda has ${it.size} item(s) today") }
    }
}

package com.friday.ai

import android.app.Application
import android.content.Intent
import com.friday.ai.core.ConversationControl
import com.friday.ai.data.local.dao.UserPreferenceDao
import com.friday.ai.data.local.entity.UserPreferenceEntity
import com.friday.ai.di.appModule
import com.friday.ai.service.FridayWakeWordService
import com.friday.ai.service.MorningBriefWorker
import com.friday.ai.service.SessionSummarizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class FridayApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FridayApp)
            modules(appModule)
        }
        installOxxiDeactivation()
        prefillDefaults()
        scheduleMorningBrief()
        wireSessionSummaries()
        learnOwnerFromDevice()
        bookModeSchedules()
        publishModeShortcuts()
    }

    /** Voice command "desactívate" disables background listening and removes the hologram. */
    private fun installOxxiDeactivation() {
        ConversationControl.onDeactivate = {
            appScope.launch {
                runCatching {
                    val prefs: UserPreferenceDao =
                        org.koin.java.KoinJavaComponent.get(UserPreferenceDao::class.java)
                    prefs.set(UserPreferenceEntity("wake_word_enabled", "false"))
                    stopService(Intent(this@FridayApp, FridayWakeWordService::class.java))
                }
            }
        }
    }

    /** Long press on the icon offers the most used modes; kept in step as modes change. */
    private fun publishModeShortcuts() {
        appScope.launch {
            val store: com.friday.ai.core.modes.ModeStore =
                org.koin.java.KoinJavaComponent.get(com.friday.ai.core.modes.ModeStore::class.java)
            store.observe().collect { com.friday.ai.service.ModeShortcuts.publish(this@FridayApp, it) }
        }
    }

    /** Alarms don't survive an app update or a force-stop; booking again is cheap and idempotent. */
    private fun bookModeSchedules() {
        appScope.launch {
            val schedules: com.friday.ai.core.modes.ModeSchedules =
                org.koin.java.KoinJavaComponent.get(com.friday.ai.core.modes.ModeSchedules::class.java)
            com.friday.ai.service.ModeAlarms(this@FridayApp).rebook(schedules.all(), emptySet())
        }
    }

    /**
     * Reads the phone's own contact card so "cómo me llamo?" is answerable
     * without the user ever having said it. Runs on every start because
     * contacts permission may only have been granted after the first one.
     */
    private fun learnOwnerFromDevice() {
        appScope.launch {
            val owner = com.friday.ai.core.OwnerProfile.read(this@FridayApp) ?: return@launch
            val memory: com.friday.ai.service.FridayMemory =
                org.koin.java.KoinJavaComponent.get(com.friday.ai.service.FridayMemory::class.java)
            owner.givenName?.let { memory.saveOrUpdate("fact", "name", it, "device") }
            owner.displayName
                ?.takeIf { it != owner.givenName }
                ?.let { memory.saveOrUpdate("fact", "full_name", it, "device") }
        }
    }

    /** The briefing only counts as proactive if it arrives unprompted. */
    private fun scheduleMorningBrief() {
        appScope.launch {
            val prefDao: UserPreferenceDao =
                org.koin.java.KoinJavaComponent.get(UserPreferenceDao::class.java)
            if (prefDao.get(MorningBriefWorker.PREF_ENABLED) == "false") return@launch
            val hour = prefDao.get(MorningBriefWorker.PREF_HOUR)?.toIntOrNull()
                ?: MorningBriefWorker.DEFAULT_HOUR
            MorningBriefWorker.schedule(this@FridayApp, hour)
        }
    }

    /** A finished conversation gets a written summary for the dashboard. */
    private fun wireSessionSummaries() {
        val memory: com.friday.ai.service.FridayMemory =
            org.koin.java.KoinJavaComponent.get(com.friday.ai.service.FridayMemory::class.java)
        val summarizer: SessionSummarizer =
            org.koin.java.KoinJavaComponent.get(SessionSummarizer::class.java)
        memory.onSessionClosed = { sessionId -> summarizer.summarizeInBackground(sessionId) }
    }

    private fun prefillDefaults() {
        appScope.launch {
            sealStoredKeys()
            org.koin.java.KoinJavaComponent.get<com.friday.ai.core.DeviceContext>(
                com.friday.ai.core.DeviceContext::class.java
            ).refresh()
            org.koin.java.KoinJavaComponent.get<com.friday.ai.service.ModelCatalog>(
                com.friday.ai.service.ModelCatalog::class.java
            ).refresh()
        }
    }

    /** Encrypts API keys an older version stored in plain text. */
    private suspend fun sealStoredKeys() {
        val prefs = org.koin.java.KoinJavaComponent.get<com.friday.ai.data.local.secure.SecurePreferenceDao>(
            com.friday.ai.data.local.secure.SecurePreferenceDao::class.java
        )
        if (prefs.migrate() == 0) return
        runCatching {
            org.koin.java.KoinJavaComponent.get<com.friday.ai.data.local.FridayDatabase>(
                com.friday.ai.data.local.FridayDatabase::class.java
            ).openHelper.writableDatabase.execSQL("VACUUM")
        }.onFailure { android.util.Log.w("FridayApp", "VACUUM after sealing keys failed: ${it.message}") }
    }
}

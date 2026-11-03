package com.friday.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.lifecycleScope
import com.friday.ai.core.DeviceContext
import com.friday.ai.data.local.dao.UserPreferenceDao
import com.friday.ai.service.FridayWakeWordService
import com.friday.ai.ui.navigation.FridayNavGraph
import com.friday.ai.ui.theme.FridayTheme
import com.friday.ai.ui.update.UpdateDialogHost
import com.friday.ai.ui.voice.InAppVoiceLayer
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    var onWakeWordActivated: (() -> Unit)? = null

    private val deviceContext: DeviceContext by inject()
    private val prefs: UserPreferenceDao by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleWakeWordIntent(intent)

        setContent {
            FridayTheme {
                Box(Modifier.fillMaxSize()) {
                    FridayNavGraph(activity = this@MainActivity)
                    InAppVoiceLayer()
                    UpdateDialogHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.friday.ai.core.apps.AppVisibility.activityResumed()
        lifecycleScope.launch {
            deviceContext.refresh()
            if (prefs.get("wake_word_enabled") == "true") {
                FridayWakeWordService.start(this@MainActivity)
            }
        }
    }

    override fun onPause() {
        com.friday.ai.core.apps.AppVisibility.activityPaused()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWakeWordIntent(intent)
    }

    private fun handleWakeWordIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("wake_word_activated", false) == true) {
            onWakeWordActivated?.invoke()
        }
    }
}

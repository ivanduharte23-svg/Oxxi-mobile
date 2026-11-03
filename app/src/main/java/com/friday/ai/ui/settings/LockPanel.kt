package com.friday.ai.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.friday.ai.command.LockPolicy
import com.friday.ai.service.lock.LockScreen
import com.friday.ai.ui.theme.ArcCyan
import com.friday.ai.ui.theme.ErrorColor
import com.friday.ai.ui.theme.HudNote
import com.friday.ai.ui.theme.HudOutlinedButton
import com.friday.ai.ui.theme.HudPanel
import com.friday.ai.ui.theme.HudReadout
import com.friday.ai.ui.theme.HudSwitchRow
import org.koin.androidx.compose.koinViewModel

private val choices = listOf(
    Triple(
        LockPolicy.Level.SAFE_ONLY, "Solo acciones seguras",
        "Música, alarmas, linterna y tiempo. Las llamadas y los mensajes requieren desbloquear el teléfono."
    ),
    Triple(
        LockPolicy.Level.VOICE, "Tras verificar tu voz",
        "Tu perfil de voz te identifica; si no está disponible, Android solicitará desbloquear el teléfono."
    ),
    Triple(
        LockPolicy.Level.UNLOCK, "Solo tras desbloquear",
        "Las llamadas y los mensajes requieren huella, reconocimiento facial o PIN."
    )
)

@Composable
fun LockPanel(viewModel: LockLevelViewModel = koinViewModel()) {
    val context = LocalContext.current
    val level by viewModel.level.collectAsStateWithLifecycle()
    var fullScreen by remember { mutableStateOf(LockScreen.fullScreenAllowed(context)) }
    var notifications by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val outcome by LockScreen.lastOutcome.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        fullScreen = LockScreen.fullScreenAllowed(context)
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }

    HudPanel("Pantalla de bloqueo", index = 14) {
        HudNote(
            "Qué puede hacer Oxxi cuando el teléfono está bloqueado. El desbloqueo corresponde a Android: " +
                "Oxxi no ve ni almacena tu PIN."
        )
        choices.forEach { (option, title, note) ->
            HudSwitchRow(
                title = title,
                subtitle = note,
                checked = level == option,
                enabled = true,
                onChange = { on -> if (on) viewModel.choose(option) }
            )
        }
        HudNote(
            "En la pantalla de bloqueo solo se muestra qué hace Oxxi: escucha, procesa o responde. " +
                "Tus palabras y la respuesta se muestran después de «Desbloquear para ver detalles»."
        )
        HudReadout(
            "Notificaciones",
            if (notifications) "PERMITIDAS" else "DESACTIVADAS",
            valueColor = if (notifications) ArcCyan else ErrorColor
        )
        HudReadout(
            "Notificaciones a pantalla completa",
            if (fullScreen) "PERMITIDAS" else "NO PERMITIDAS",
            valueColor = if (fullScreen) ArcCyan else ErrorColor
        )
        HudReadout("Última visualización", outcomeText(outcome))
        if (!fullScreen || !notifications) {
            HudNote(
                "Para mostrar respuestas y la pantalla de desbloqueo sobre la pantalla bloqueada, " +
                    "permite a Oxxi mostrar notificaciones a pantalla completa."
            )
            HudOutlinedButton(onClick = { openFullScreenSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Permitir notificaciones a pantalla completa")
            }
        }
    }
}

/** What the last attempt really showed — an allowed permission alone proves nothing. */
private fun outcomeText(outcome: LockScreen.Outcome?): String = when (outcome) {
    null -> "AÚN NO SE HA MOSTRADO"
    LockScreen.Outcome.ACTIVITY_VISIBLE -> "PANEL EN PANTALLA"
    LockScreen.Outcome.NOTIFICATION_ONLY -> "SOLO NOTIFICACIÓN"
    LockScreen.Outcome.FSI_DENIED -> "NOTIFICACIÓN (SIN PANTALLA COMPLETA)"
    LockScreen.Outcome.NOTIFICATIONS_BLOCKED -> "SOLO VOZ"
    LockScreen.Outcome.FAILED -> "NO SE HA PODIDO MOSTRAR"
}

private fun openFullScreenSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

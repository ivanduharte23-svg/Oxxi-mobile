package com.friday.ai.ui.settings

import android.Manifest
import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.friday.ai.core.VoiceProfile
import com.friday.ai.service.FridayNotificationListener
import com.friday.ai.service.visualizer.AudioVisualizerService
import com.friday.ai.service.VoiceCalibrator
import com.friday.ai.service.VoskModelManager
import com.friday.ai.ui.theme.ArcCyan
import com.friday.ai.ui.theme.ErrorColor
import com.friday.ai.ui.theme.HudBackground
import com.friday.ai.ui.theme.HudButton
import com.friday.ai.ui.theme.HudLabelStyle
import com.friday.ai.ui.theme.HudNote
import com.friday.ai.ui.theme.HudOutlinedButton
import com.friday.ai.ui.theme.HudPanel
import com.friday.ai.ui.theme.HudReadout
import com.friday.ai.ui.theme.HudStatus
import com.friday.ai.ui.theme.HudSwitchRow
import com.friday.ai.ui.theme.HudTopBar
import com.friday.ai.ui.theme.OnBackground
import com.friday.ai.ui.theme.hudFieldColors
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Friday's settings as a stack of numbered HUD panels, one per system:
 * intelligence, voice, calibration, voice profile, chats, mail, learned
 * commands, maps, memory and moving to another phone.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenDashboard: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenModes: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { text -> scope.launch { snackbarHost.showSnackbar(text) } }
    LaunchedEffect(Unit) { viewModel.refreshLearned() }

    HudBackground {
        Column(Modifier.fillMaxSize()) {
            HudTopBar(
                title = "SISTEMAS",
                status = HudStatus("Ajustes"),
                navigation = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Filled.MonitorHeart, contentDescription = "Diagnóstico", tint = ArcCyan)
                    }
                }
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                IntelligencePanel(viewModel)
                VoicePanel(viewModel, say)
                MusicVisualizerPanel()
                CalibrationPanel(viewModel)
                VoiceProfilePanel(viewModel)
                ChatsPanel(viewModel)
                MailPanel(viewModel)
                LearnedPanel(viewModel)
                MapsPanel(viewModel)
                HudPanel("Modos", index = 9) {
                    HudNote(
                        "Varias acciones bajo un mismo nombre, creadas por voz: «crea el modo descanso: " +
                            "silencio y brillo al mínimo». «Desactiva el modo» restaura los ajustes anteriores."
                    )
                    HudOutlinedButton(onClick = onOpenModes, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir modos")
                    }
                }
                HudPanel("Memoria", index = 10) {
                    HudNote("Resumen de lo que Oxxi sabe de ti: datos y conversaciones de las que proceden.")
                    HudOutlinedButton(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
                        Text("Qué sabe Oxxi de mí")
                    }
                }
                HudPanel("Transferir a otro teléfono", index = 11) { TransferSection() }
                LocationPanel()
                QuickLinksPanel()
                LockPanel()
                UpdatePanel()
                Spacer(Modifier.height(24.dp))
            }
        }
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { data ->
            Snackbar(snackbarData = data)
        }
    }
}

@Composable
private fun MusicVisualizerPanel() {
    val context = LocalContext.current
    val running by AudioVisualizerService.running.collectAsStateWithLifecycle()
    val message by AudioVisualizerService.message.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            AudioVisualizerService.start(context, result.resultCode, data)
        }
    }

    HudPanel("Visualizador musical", index = 15) {
        HudNote(
            "El holograma puede reaccionar al ritmo y a la intensidad de la música de otras aplicaciones. " +
                "Android pedirá autorización antes de compartir el audio; Oxxi no guarda ni envía la música."
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (running) ArcCyan else OnBackground
        )
        HudNote(
            "La sincronización depende de que el reproductor permita capturar su audio. " +
                "Algunas aplicaciones protegidas no comparten el sonido. Puedes detenerlo aquí cuando quieras."
        )
        HudButton(
            onClick = {
                if (running) {
                    AudioVisualizerService.stop(context)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val manager = context.getSystemService(MediaProjectionManager::class.java)
                    launcher.launch(manager.createScreenCaptureIntent())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = running || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            Text(if (running) "Detener visualizador" else "Activar visualizador musical")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntelligencePanel(viewModel: SettingsViewModel) {
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    HudPanel("Inteligencia · Groq", index = 1) {
        HudReadout(
            "Estado",
            if (apiKey.isBlank()) "SIN CLAVE" else "CLAVE CONFIGURADA",
            valueColor = if (apiKey.isBlank()) ErrorColor else ArcCyan
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = viewModel::onApiKeyChange,
            label = { Text("Clave de API de Groq") },
            placeholder = { Text("gsk_…") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = hudFieldColors()
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = model,
                onValueChange = {},
                readOnly = true,
                label = { Text("Modelo") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = hudFieldColors(),
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                models.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            viewModel.onModelChange(name)
                            expanded = false
                        }
                    )
                }
            }
        }
        HudButton(onClick = viewModel::saveSettings, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSaved) "Guardado" else "Guardar")
        }
        HudNote("La clave se guarda cifrada en Android Keystore y solo se envía a Groq.")
    }
}

@Composable
private fun ChatsPanel(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val announceCalls by viewModel.announceCalls.collectAsStateWithLifecycle()
    // Needs the notification listener; turning it on without it goes
    // straight to the screen that grants it.
    val needsAccess = { on: Boolean ->
        if (on && !FridayNotificationListener.isEnabled(context)) FridayNotificationListener.openSettings(context)
    }
    HudPanel("Llamadas y mensajes", index = 5) {
        HudNote(
            "Oxxi reúne las notificaciones de WhatsApp, Telegram y SMS sin leerlas en voz alta y filtra " +
                "la publicidad. Pregunta «¿tengo mensajes sin leer?» para saber quién ha escrito; después puedes decir «contéstale que voy de camino». " +
                "Necesita permiso para acceder a las notificaciones."
        )
        HudSwitchRow(
            title = "Anunciar llamadas",
            subtitle = "Indica quién llama: teléfono, WhatsApp o Telegram",
            checked = announceCalls,
            onChange = { needsAccess(it); viewModel.onAnnounceCallsChange(it) }
        )
    }
}

@Composable
private fun MailPanel(viewModel: SettingsViewModel) {
    val gmail by viewModel.gmail.collectAsStateWithLifecycle()
    // Google's consent screen. A cancelled screen comes back without data,
    // which the view model reports as "Вход отменён".
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onGmailConsent(result.data, completed = result.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(gmail.consent) {
        gmail.consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    HudPanel("Correo · Gmail", index = 6) {
        HudReadout(
            "Cuenta",
            gmail.account ?: "NO CONECTADA",
            valueColor = if (gmail.account != null) ArcCyan else OnBackground
        )
        HudNote(
            if (gmail.account != null) {
                "Oxxi revisa la bandeja de entrada, lee los correos y prepara respuestas; solo las envía cuando confirmas."
            } else {
                "Cuando esté conectado, podrás preguntar «¿hay correos nuevos?», «lee el correo de Ana» o «responde que llegaré a las cinco»."
            }
        )
        gmail.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ArcCyan) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton(onClick = viewModel::connectGmail, enabled = !gmail.busy, modifier = Modifier.weight(1f)) {
                Text(if (gmail.account == null) "Conectar Gmail" else "Volver a conectar")
            }
            if (gmail.account != null) {
                HudOutlinedButton(onClick = viewModel::disconnectGmail, enabled = !gmail.busy) { Text("Desconectar") }
            }
        }
    }
}

@Composable
private fun LearnedPanel(viewModel: SettingsViewModel) {
    val learned by viewModel.learnedCommands.collectAsStateWithLifecycle()
    HudPanel("Comandos aprendidos", index = 7) {
        HudNote(
            "Frases que la IA interpretó una vez y que ahora Oxxi puede ejecutar directamente, sin consultar de nuevo al modelo. " +
                "Di «no, eso no» justo después de un error para que lo olvide, o elimínalo desde aquí."
        )
        if (learned.isEmpty()) HudReadout("Aprendidos", "0")
        learned.forEach { (phrase, action) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("«$phrase»", style = MaterialTheme.typography.bodyLarge, color = OnBackground)
                    Text(action, style = HudLabelStyle, color = ArcCyan.copy(alpha = 0.7f))
                }
                IconButton(onClick = { viewModel.forgetLearned(phrase) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Olvidar «$phrase»",
                        tint = ArcCyan.copy(alpha = 0.7f)
                    )
                }
            }
        }
        if (learned.isNotEmpty()) {
            HudOutlinedButton(onClick = viewModel::forgetAllLearned) { Text("Olvidar todos") }
        }
    }
}

@Composable
private fun MapsPanel(viewModel: SettingsViewModel) {
    val provider by viewModel.mapsProvider.collectAsStateWithLifecycle()
    HudPanel("Mapas", index = 8) {
        HudNote("Elige qué aplicación abrir al pedir «busca una cafetería o un banco cerca». «Predeterminada» usa la aplicación de mapas del teléfono.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto" to "Predeterminada", "google" to "Google Maps", "yandex" to "Yandex Maps").forEach { (value, label) ->
                Box(Modifier.weight(1f)) {
                    HudOutlinedButton(
                        onClick = { viewModel.onMapsProviderChange(value) },
                        selected = provider == value,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
    }
}

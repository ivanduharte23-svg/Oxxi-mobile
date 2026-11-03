package com.friday.ai.ui.oxxi

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.friday.ai.domain.model.ChatUiState
import com.friday.ai.domain.model.MessageRole
import com.friday.ai.ui.chat.ChatViewModel
import com.friday.ai.ui.theme.BackgroundDark
import com.friday.ai.ui.theme.OnBackground
import com.friday.ai.ui.theme.OnSurfaceMuted
import com.friday.ai.ui.theme.SurfaceDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.koin.androidx.compose.koinViewModel

private val NeonBlue = Color(0xFF62C9FF)
private val NeonPurple = Color(0xFFB46CFF)
private val NeonCyan = Color(0xFF28F4FF)

private enum class OxxiTab { HOME, CHAT, MIC, TASKS }

@Composable
fun OxxiMainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = koinViewModel()
) {
    var tab by remember { mutableStateOf(OxxiTab.HOME) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val input by viewModel.inputText.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.onMicClick() }

    fun startMic() {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.onMicClick()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        when (tab) {
            OxxiTab.HOME, OxxiTab.MIC -> HomePage(
                state = state,
                onSettings = onNavigateToSettings,
                onMic = { startMic() }
            )
            OxxiTab.CHAT -> ChatPage(
                state = state,
                input = input,
                onInput = viewModel::onInputChange,
                onSend = viewModel::onSend,
                onMic = { startMic() }
            )
            OxxiTab.TASKS -> TasksPage()
        }

        OxxiBottomBar(
            selected = if (tab == OxxiTab.MIC) OxxiTab.HOME else tab,
            onSelect = { selected -> if (selected == OxxiTab.MIC) startMic() else tab = selected },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun HomePage(state: ChatUiState, onSettings: () -> Unit, onMic: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 92.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "OXXI",
                color = OnBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = OnBackground.copy(alpha = 0.85f))
            }
        }
        Text("Tu asistente inteligente", color = NeonBlue, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        NexusBlob(modifier = Modifier.size(310.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                state.isVoiceListening -> "Te escucho…"
                state.isLoading -> "Estoy pensando…"
                else -> "Hola, soy Oxxi. ¿Qué necesitas?"
            },
            color = if (state.isVoiceListening) NeonCyan else OnBackground,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        state.error?.let { ErrorBanner(it) }
        MicButton(onClick = onMic, description = "Hablar con Oxxi", enabled = !state.isLoading)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ChatPage(
    state: ChatUiState,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 92.dp)) {
        Spacer(Modifier.height(20.dp))
        NexusBlob(modifier = Modifier.align(Alignment.CenterHorizontally).size(220.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            when {
                state.isVoiceListening -> "Te escucho…"
                state.isLoading -> "Oxxi está preparando la respuesta…"
                else -> "¿En qué puedo ayudarte?"
            },
            color = OnSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        state.error?.let { ErrorBanner(it) }
        if (state.messages.isEmpty()) {
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.messages, key = { it.id }) { message ->
                    Text(
                        message.content,
                        color = if (message.role == MessageRole.USER) OnBackground else OnSurfaceMuted,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = if (message.role == MessageRole.USER) TextAlign.End else TextAlign.Start
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ChatComposer(input, onInput, onSend, onMic, isBusy = state.isLoading)
    }
}

@Composable
private fun ChatComposer(
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    isBusy: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = input,
            onValueChange = onInput,
            enabled = !isBusy,
            singleLine = true,
            placeholder = { Text("Habla con Oxxi…", color = OnSurfaceMuted) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = NeonCyan,
                unfocusedIndicatorColor = OnSurfaceMuted.copy(alpha = 0.45f),
                cursorColor = NeonCyan,
                focusedTextColor = OnBackground,
                unfocusedTextColor = OnBackground
            )
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { if (input.isNotBlank()) onSend() else onMic() }, enabled = !isBusy) {
            Icon(Icons.Filled.Mic, contentDescription = "Micrófono", tint = NeonCyan)
        }
    }
}

@Composable
private fun TasksPage() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.TaskAlt, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            "Recordatorios y tareas",
            color = OnBackground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pídele a Oxxi que cree un recordatorio o añada una tarea. Aquí aparecerán cuando estén conectados.",
            color = OnSurfaceMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Text(
        message,
        color = Color(0xFFFFA0A8),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF351820))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun MicButton(onClick: () -> Unit, description: String, enabled: Boolean) {
    Box(
        Modifier
            .size(76.dp)
            .shadow(18.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color.White, NeonCyan, NeonBlue).map {
                        it.copy(alpha = if (enabled) 1f else 0.35f)
                    }
                )
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = Color(0xFF05212A), modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun OxxiBottomBar(selected: OxxiTab, onSelect: (OxxiTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(SurfaceDark.copy(alpha = 0.96f))
            .border(1.dp, Color.White.copy(alpha = 0.05f)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomItem(OxxiTab.HOME, selected, "Inicio", Icons.Filled.Home, onSelect)
        BottomItem(OxxiTab.CHAT, selected, "Chat", Icons.Filled.Search, onSelect)
        BottomItem(OxxiTab.MIC, selected, "Micrófono", Icons.Filled.Mic, onSelect)
        BottomItem(OxxiTab.TASKS, selected, "Tareas", Icons.Filled.TaskAlt, onSelect)
    }
}

@Composable
private fun BottomItem(
    tab: OxxiTab,
    selected: OxxiTab,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: (OxxiTab) -> Unit
) {
    val active = tab == selected
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(78.dp).clickable(onClick = { onSelect(tab) })
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) NeonCyan else OnSurfaceMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (active) NeonCyan else OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
    }
}

/** Animated 3D holographic mesh: a deforming sphere projected from latitude/longitude. */
private data class OrbProjection(
    val position: Offset,
    val depth: Float,
    val lowerSurface: Boolean
)

@Composable
private fun NexusBlob(modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "oxxi_hologram")
    val rotation by motion.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val breathing by motion.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathing"
    )

    Canvas(modifier) {
        val width = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = width * 0.315f * breathing
        val rows = 18
        val columns = 40
        val turn = rotation

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonCyan.copy(alpha = 0.26f),
                    NeonBlue.copy(alpha = 0.12f),
                    NeonPurple.copy(alpha = 0.025f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = width * 0.49f
            ),
            radius = width * 0.49f,
            center = Offset(cx, cy)
        )
        drawCircle(NeonBlue.copy(alpha = 0.09f), radius = radius * 1.48f, center = Offset(cx, cy))

        fun project(row: Int, column: Int): OrbProjection {
            val latitude = -PI.toFloat() / 2f + PI.toFloat() * row / rows
            val longitude = 2f * PI.toFloat() * column / columns + turn
            val wave = 1f +
                0.105f * sin(longitude * 3f + latitude * 2.2f + turn * 0.35f) +
                0.055f * cos(latitude * 5f - longitude * 2f)
            val x = cos(latitude) * sin(longitude)
            val y = sin(latitude) * 0.86f
            val z = cos(latitude) * cos(longitude)
            val perspective = 1f / (1f - z * 0.16f)
            return OrbProjection(
                position = Offset(
                    cx + x * radius * wave * perspective,
                    cy + y * radius * wave * perspective
                ),
                depth = ((z + 1f) / 2f).coerceIn(0f, 1f),
                lowerSurface = y > 0.20f
            )
        }

        // Longitude and latitude strands create a real spherical projection,
        // rather than a flat particle cloud. Rear strands stay faint.
        for (row in 0..rows) {
            for (column in 0 until columns) {
                val point = project(row, column)
                val next = project(row, (column + 1) % columns)
                val color = when {
                    point.lowerSurface -> NeonPurple
                    point.depth < 0.32f -> NeonBlue
                    else -> NeonCyan
                }
                val lineAlpha = 0.075f + point.depth * 0.24f
                drawLine(
                    color = color.copy(alpha = lineAlpha),
                    start = point.position,
                    end = next.position,
                    strokeWidth = 0.75f
                )
                if (row < rows) {
                    val below = project(row + 1, column)
                    drawLine(
                        color = color.copy(alpha = lineAlpha * 0.9f),
                        start = point.position,
                        end = below.position,
                        strokeWidth = 0.72f
                    )
                }

                val dotRadius = 0.65f + point.depth * 0.72f
                drawCircle(
                    color = color.copy(alpha = 0.22f + point.depth * 0.66f),
                    radius = dotRadius,
                    center = point.position
                )
                if (point.depth > 0.68f) {
                    drawCircle(
                        color = Color.White.copy(alpha = (point.depth - 0.55f) * 0.42f),
                        radius = 0.55f,
                        center = point.position
                    )
                }
            }
        }

        // Three luminous orbital rings and small orbiting particles reinforce
        // the hologram effect without loading a heavy 3D engine or model file.
        drawOval(
            brush = Brush.sweepGradient(listOf(NeonCyan, NeonBlue, NeonPurple, NeonCyan)),
            topLeft = Offset(cx - radius * 1.34f, cy - radius * 0.54f),
            size = androidx.compose.ui.geometry.Size(radius * 2.68f, radius * 1.08f),
            style = Stroke(width = 1.25f)
        )
        drawOval(
            color = NeonCyan.copy(alpha = 0.28f),
            topLeft = Offset(cx - radius * 1.19f, cy - radius * 1.19f),
            size = androidx.compose.ui.geometry.Size(radius * 2.38f, radius * 2.38f),
            style = Stroke(width = 0.8f)
        )
        drawOval(
            color = NeonPurple.copy(alpha = 0.22f),
            topLeft = Offset(cx - radius * 1.48f, cy - radius * 0.62f),
            size = androidx.compose.ui.geometry.Size(radius * 2.96f, radius * 1.24f),
            style = Stroke(width = 0.7f)
        )
        for (index in 0 until 72) {
            val angle = index.toFloat() / 72f * 2f * PI.toFloat() + turn * 0.16f
            val orbitRadius = radius * (1.34f + 0.045f * sin(angle * 4f))
            val particle = Offset(
                cx + cos(angle) * orbitRadius,
                cy + sin(angle) * orbitRadius * 0.56f
            )
            val tint = if (index % 4 == 0) NeonPurple else NeonCyan
            drawCircle(
                color = tint.copy(alpha = 0.2f + 0.32f * (0.5f + 0.5f * sin(angle * 3f))),
                radius = 0.9f,
                center = particle
            )
        }
    }
}

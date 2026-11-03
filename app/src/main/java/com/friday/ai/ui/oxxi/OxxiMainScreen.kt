package com.friday.ai.ui.oxxi

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.friday.ai.domain.model.MessageRole
import com.friday.ai.ui.chat.ChatViewModel
import com.friday.ai.ui.theme.ArcCyan
import com.friday.ai.ui.theme.BackgroundDark
import com.friday.ai.ui.theme.OnBackground
import com.friday.ai.ui.theme.OnSurfaceMuted
import com.friday.ai.ui.theme.SurfaceDark
import com.friday.ai.ui.theme.SurfaceVariant
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
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.onMicClick() }

    fun startMic() {
        if (ContextCompat.checkSelfPermission(
                androidx.compose.ui.platform.LocalContext.current,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) viewModel.onMicClick() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        when (tab) {
            OxxiTab.HOME -> HomePage(onSettings = onNavigateToSettings, onMic = { startMic() })
            OxxiTab.CHAT -> ChatPage(
                state = state,
                input = input,
                onInput = viewModel::onInputChange,
                onSend = viewModel::onSend,
                onMic = { startMic() }
            )
            OxxiTab.MIC -> {
                tab = OxxiTab.HOME
                startMic()
            }
            OxxiTab.TASKS -> TasksPage()
        }

        OxxiBottomBar(
            selected = tab,
            onSelect = { selected ->
                if (selected == OxxiTab.MIC) startMic() else tab = selected
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun HomePage(onSettings: () -> Unit, onMic: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 92.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OXXI", color = OnBackground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = OnBackground.copy(alpha = 0.85f))
            }
        }

        Spacer(Modifier.weight(1f))
        NexusBlob(modifier = Modifier.size(300.dp))
        Spacer(Modifier.weight(1f))
        MicButton(onClick = onMic, description = "Hablar con Oxxi")
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ChatPage(
    state: com.friday.ai.domain.model.ChatUiState,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 92.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        NexusBlob(modifier = Modifier.align(Alignment.CenterHorizontally).size(220.dp))
        Spacer(Modifier.height(18.dp))
        if (state.messages.isEmpty()) {
            Text(
                "¿En qué puedo ayudarte?",
                color = OnSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
        ChatComposer(input, onInput, onSend, onMic)
    }
}

@Composable
private fun ChatComposer(
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = input,
            onValueChange = onInput,
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
        IconButton(onClick = { if (input.isNotBlank()) onSend() else onMic() }) {
            Icon(Icons.Filled.Mic, contentDescription = "Micrófono", tint = NeonCyan)
        }
    }
}

@Composable
private fun TasksPage() {
    var query by remember { mutableStateOf("") }
    val tasks = remember {
        listOf(
            "Crear un recordatorio" to "Hoy · 20:00",
            "Revisar mi agenda" to "Mañana · 09:00",
            "Preparar la lista de tareas" to "Viernes · 18:00"
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 92.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            Text("Recordatorios", color = OnBackground, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = OnSurfaceMuted) },
            placeholder = { Text("Buscar…", color = OnSurfaceMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = OnBackground,
                unfocusedTextColor = OnBackground
            )
        )
        Spacer(Modifier.height(18.dp))
        tasks.filter { it.first.contains(query, true) }.forEach { (title, date) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = OnBackground, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(date, color = OnSurfaceMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MicButton(onClick: () -> Unit, description: String) {
    Box(
        Modifier
            .size(76.dp)
            .shadow(18.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color.White, NeonCyan, NeonBlue)))
            .clickable(role = Role.Button, onClick = onClick)
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
        Icon(icon, contentDescription = label, tint = if (active) NeonCyan else OnSurfaceMuted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (active) NeonCyan else OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
    }
}

/** Dotted, holographic orb inspired directly by the supplied reference. */
@Composable
private fun NexusBlob(modifier: Modifier = Modifier) {
    val dots = remember {
        buildList {
            for (y in -22..22) {
                for (x in -30..30) {
                    val nx = x / 30f
                    val ny = y / 22f
                    val edge = (nx * nx * 0.95f + ny * ny * 1.15f)
                    if (edge < 1.05f) add(Offset(nx, ny))
                }
            }
        }
    }
    Canvas(modifier) {
        val w = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = w * 0.33f

        drawCircle(
            brush = Brush.radialGradient(
                listOf(NeonCyan.copy(alpha = 0.22f), NeonBlue.copy(alpha = 0.08f), NeonPurple.copy(alpha = 0.0f)),
                center = Offset(cx, cy), radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = Offset(cx, cy)
        )
        drawCircle(NeonBlue.copy(alpha = 0.12f), radius = r * 1.45f, center = Offset(cx, cy))

        val points = dots.map { p ->
            val bulge = 1f + 0.10f * cos(p.x * 3.0f) - 0.06f * sin(p.y * 4.2f)
            val px = cx + p.x * r * bulge
            val py = cy + p.y * r * (0.72f + 0.16f * cos(p.x * 2.1f))
            val depth = 1f - (p.y + 1f) / 2f
            Triple(px, py, depth)
        }

        for ((px, py, depth) in points) {
            val glow = (0.25f + 0.75f * depth).coerceIn(0f, 1f)
            val purple = (py - cy) / r
            val color = if (purple > 0.15f) NeonPurple else if (px < cx) NeonBlue else NeonCyan
            drawCircle(color.copy(alpha = 0.18f * glow), radius = 3.2f, center = Offset(px, py))
            drawCircle(Color.White.copy(alpha = 0.38f + 0.38f * glow), radius = 1.15f, center = Offset(px, py))
        }

        drawOval(
            brush = Brush.sweepGradient(listOf(NeonCyan, NeonBlue, NeonPurple, NeonCyan)),
            topLeft = Offset(cx - r * 1.10f, cy - r * 0.72f),
            size = androidx.compose.ui.geometry.Size(r * 2.2f, r * 1.44f),
            style = Stroke(width = 1.2f)
        )
    }
}

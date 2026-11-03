package com.friday.ai.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.friday.ai.core.update.OxxiUpdateManager
import com.friday.ai.ui.theme.HudButton
import com.friday.ai.ui.theme.HudNote
import com.friday.ai.ui.theme.HudPanel
import kotlinx.coroutines.launch

@Composable
fun UpdatePanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Comprueba si hay una versión nueva de Oxxi.") }
    var busy by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<OxxiUpdateManager.UpdateInfo?>(null) }

    HudPanel("Actualizaciones", index = 16) {
        HudNote(status)
        HudButton(
            onClick = {
                if (busy) return@HudButton
                busy = true
                status = "Buscando actualización…"
                scope.launch {
                    val found = OxxiUpdateManager.check(context)
                    update = found
                    busy = false
                    status = if (found == null) {
                        "Oxxi ya está actualizado."
                    } else {
                        "Nueva versión disponible: ${found.versionName}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) {
            Text(if (busy) "Buscando…" else "Buscar actualización")
        }

        update?.let { info ->
            HudButton(
                onClick = {
                    busy = true
                    status = "Descargando Oxxi ${info.versionName}…"
                    scope.launch {
                        val result = OxxiUpdateManager.downloadAndInstall(context, info)
                        busy = false
                        if (result.isFailure) {
                            status = result.exceptionOrNull()?.message
                                ?: "No se pudo iniciar la actualización."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                Text("Actualizar ahora")
            }
        }
    }
}

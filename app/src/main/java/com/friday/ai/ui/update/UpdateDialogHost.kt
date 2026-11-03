package com.friday.ai.ui.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.friday.ai.core.update.OxxiUpdateManager
import kotlinx.coroutines.launch

/** Small update prompt shown when Oxxi opens; the APK download stays inside the app. */
@Composable
fun UpdateDialogHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<OxxiUpdateManager.UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        update = OxxiUpdateManager.check(context)
    }

    update?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!downloading) update = null },
            title = { Text("Nueva versión de Oxxi") },
            text = {
                Text(
                    if (downloading) {
                        "Descargando Oxxi ${info.versionName}…"
                    } else {
                        "Hay una actualización disponible: Oxxi ${info.versionName}. Puedes instalarla directamente desde Oxxi."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        downloading = true
                        scope.launch {
                            val result = OxxiUpdateManager.downloadAndInstall(context, info)
                            if (result.isFailure) {
                                downloading = false
                                update = null
                            }
                        }
                    }
                ) { Text(if (downloading) "Descargando…" else "Actualizar") }
            },
            dismissButton = {
                TextButton(enabled = !downloading, onClick = { update = null }) {
                    Text("Ahora no")
                }
            }
        )
    }
}

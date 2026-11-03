package com.friday.ai.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.friday.ai.data.backup.SettingsArchive
import com.friday.ai.data.backup.SettingsTransfer
import com.friday.ai.service.FridayWakeWordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings export and import, step by step: pick a file, type the
 * passphrase, (for import) see what the file holds, confirm.
 */
class TransferViewModel(
    private val appContext: Context,
    private val transfer: SettingsTransfer,
    private val modes: com.friday.ai.core.modes.ModeStore? = null,
    private val schedules: com.friday.ai.core.modes.ModeSchedules? = null
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Working : State
        /** A file opened fine; nothing is replaced until [confirmImport]. */
        data class Review(val summary: SettingsTransfer.Summary) : State
        data class Done(val message: String) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var pending: SettingsArchive.Content? = null

    fun export(uri: Uri, passphrase: CharArray) = work {
        val bytes = transfer.export(passphrase)
        withContext(Dispatchers.IO) {
            appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("No se ha podido abrir el archivo para escribir.")
        }
        State.Done("Guardado (${bytes.size / KB} KB). Guarda la contraseña por separado.")
    }

    fun open(uri: Uri, passphrase: CharArray) = work {
        val bytes = withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("No se ha podido abrir el archivo.")
        }
        val content = transfer.read(bytes, passphrase)
        pending = content
        State.Review(transfer.summary(content))
    }

    fun confirmImport() {
        val content = pending ?: return
        work {
            transfer.apply(content)
            pending = null
            // The database was replaced under the in-memory copy.
            modes?.load()
            schedules?.let { com.friday.ai.service.ModeAlarms(appContext).rebook(it.all(), emptySet()) }
            // Wake word, thresholds and voice profile are read when the service starts.
            if (FridayWakeWordService.running) {
                FridayWakeWordService.stop(appContext)
                FridayWakeWordService.start(appContext)
            }
            State.Done("Listo: se han transferido los ajustes.")
        }
    }

    fun dismiss() {
        pending = null
        _state.value = State.Idle
    }

    private fun work(block: suspend () -> State) {
        _state.value = State.Working
        viewModelScope.launch {
            _state.value = try {
                block()
            } catch (e: SettingsArchive.Failure) {
                State.Failed(describe(e))
            } catch (e: Exception) {
                Log.e(TAG, "Transfer failed: ${e.javaClass.simpleName}")
                State.Failed(e.message ?: "No se ha podido completar la operación.")
            }
        }
    }

    private fun describe(failure: SettingsArchive.Failure): String = when (failure) {
        is SettingsArchive.Failure.NotAnArchive -> "Este no es un archivo de ajustes de Oxxi."
        is SettingsArchive.Failure.WrongPassphraseOrDamaged -> "La contraseña no es correcta o el archivo está dañado."
        is SettingsArchive.Failure.NewerFormat ->
            "El archivo se creó con una versión más reciente de Oxxi; actualiza la aplicación primero."
    }

    private companion object {
        const val TAG = "Transfer"
        const val KB = 1024
    }
}

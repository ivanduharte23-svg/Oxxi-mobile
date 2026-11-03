package com.friday.ai.service

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The offline wake-word model: downloaded once, verified, installed
 * atomically ([ModelInstaller]).
 */
class VoskModelManager(private val context: Context) {

    companion object {
        private const val TAG = "VoskModelManager"
        private const val MODEL_DIR_NAME = "vosk-model-es"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        private const val MODEL_MARKER = "conf/model.conf"

        /**
         * Spanish small Vosk model (39,817,833 bytes). SHA-256 is pinned to
         * the published archive; a changed upstream file fails installation.
         */
        val ARTIFACT = ModelInstaller.Artifact(
            sha256 = "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f",
            maxBytes = 48L * 1024 * 1024
        )

        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER = 64 * 1024
        private const val PERCENT = 100
    }

    sealed class DownloadState {
        data object NotDownloaded : DownloadState()
        data class Downloading(val progress: Int) : DownloadState()
        data object Extracting : DownloadState()
        data object Ready : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.NotDownloaded)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val installer = ModelInstaller(
        filesDir = context.filesDir,
        cacheDir = context.cacheDir,
        folderName = MODEL_DIR_NAME,
        marker = MODEL_MARKER,
        expected = ARTIFACT
    )

    fun getModelPath(): String? = installer.modelRoot()?.absolutePath

    fun isModelReady(): Boolean = getModelPath() != null

    fun checkState() {
        _downloadState.value = if (isModelReady()) DownloadState.Ready else DownloadState.NotDownloaded
    }

    suspend fun downloadModel() = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _downloadState.value = DownloadState.Ready
            return@withContext
        }
        try {
            _downloadState.value = DownloadState.Downloading(0)
            installer.install { archive ->
                downloadFile(MODEL_URL, archive)
                _downloadState.value = DownloadState.Extracting
            }
            _downloadState.value =
                if (isModelReady()) DownloadState.Ready else DownloadState.Error("El modelo de voz no se ha instalado.")
            Log.i(TAG, "Modelo de voz instalado.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Error al instalar el modelo de voz: ${e.message}")
            _downloadState.value = DownloadState.Error(e.message ?: "Error al descargar el modelo de voz.")
        }
    }

    /** Downloads [urlStr] into [target], stopping past the expected size instead of filling the storage. */
    private suspend fun downloadFile(urlStr: String, target: File) {
        val connection = URL(urlStr).openConnection()
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.connect()
        val totalSize = connection.contentLengthLong
        if (totalSize > ARTIFACT.maxBytes) throw ModelInstaller.Rejected("La descarga supera el tamaño permitido para el modelo.")
        connection.getInputStream().use { input ->
            FileOutputStream(target).use { output -> copyCapped(input, output, totalSize) }
        }
    }

    private suspend fun copyCapped(input: InputStream, output: OutputStream, totalSize: Long) {
        val buffer = ByteArray(BUFFER)
        var downloaded = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            downloaded += read
            if (downloaded > ARTIFACT.maxBytes) throw ModelInstaller.Rejected("La descarga supera el tamaño permitido para el modelo.")
            output.write(buffer, 0, read)
            if (totalSize > 0) {
                _downloadState.value = DownloadState.Downloading((downloaded * PERCENT / totalSize).toInt())
            }
        }
    }
}

package com.friday.ai.service.visualizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.friday.ai.service.voice.VoiceUi
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures only app playback explicitly allowed by Android's AudioPlaybackCapture API.
 * The system consent token is one-use and is never saved.
 */
class AudioVisualizerService : Service() {

    companion object {
        private const val TAG = "OxxiAudioVisualizer"
        private const val ACTION_STOP = "com.oxxi.visualizer.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "oxxi_music_visualizer"
        private const val NOTIFICATION_ID = 4120
        private const val SAMPLE_RATE = 48_000
        private const val READ_SAMPLES = 2_048

        private val _running = kotlinx.coroutines.flow.MutableStateFlow(false)
        val running = _running

        private val _message = kotlinx.coroutines.flow.MutableStateFlow("Visualizador musical desactivado")
        val message = _message

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, AudioVisualizerService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudioVisualizerService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var readJob: Job? = null
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _message.value = "El visualizador musical requiere Android 10 o posterior."
            stopSelf()
            return START_NOT_STICKY
        }
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification("Sincronizando el holograma con la música…"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            }
        )
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let {
            androidx.core.content.IntentCompat.getParcelableExtra(
                it,
                EXTRA_RESULT_DATA,
                Intent::class.java
            )
        }
        if (data == null) {
            _message.value = "Android no ha concedido permiso para capturar el audio."
            stopSelf()
            return START_NOT_STICKY
        }
        startCapture(resultCode, data)
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val activeProjection = manager.getMediaProjection(resultCode, data)
                ?: error("Android no ha concedido el acceso al audio.")
            projection = activeProjection
            activeProjection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (!stopping) {
                            _message.value = "La captura de audio se ha detenido."
                            stopSelf()
                        }
                    }
                },
                Handler(Looper.getMainLooper())
            )

            val capture = AudioPlaybackCaptureConfiguration.Builder(activeProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val minimum = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minimum > 0) { "El teléfono no admite este formato de audio." }

            val audioRecord = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minimum * 2, READ_SAMPLES * 2))
                .setAudioPlaybackCaptureConfig(capture)
                .build()
            check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                "No se ha podido iniciar el capturador de audio."
            }
            recorder = audioRecord
            audioRecord.startRecording()
            _running.value = true
            _message.value = "Visualizador musical activo. Esperando audio compatible…"
            updateNotification("Oxxi reacciona al audio de aplicaciones compatibles.")
            readJob = scope.launch { readLevels(audioRecord) }
        } catch (e: Exception) {
            Log.e(TAG, "Playback capture failed: ${e.javaClass.simpleName}", e)
            _message.value = e.message ?: "No se ha podido iniciar el visualizador musical."
            stopSelf()
        }
    }

    private suspend fun readLevels(audioRecord: AudioRecord) {
        val samples = ShortArray(READ_SAMPLES)
        var smoothed = 0f
        try {
            while (scope.isActive && !stopping) {
                val count = audioRecord.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    var energy = 0.0
                    for (index in 0 until count) {
                        val sample = samples[index] / 32768.0
                        energy += sample * sample
                    }
                    val rms = sqrt(energy / count).toFloat()
                    val level = (rms * 5.2f).coerceIn(0f, 1f)
                    smoothed = smoothed * 0.68f + level * 0.32f
                    VoiceUi.store.level(smoothed)
                } else if (count < 0) {
                    break
                } else {
                    delay(10)
                }
            }
        } catch (e: Exception) {
            if (!stopping) {
                Log.w(TAG, "Audio capture ended: ${e.javaClass.simpleName}")
                _message.value = "Se ha perdido la señal de audio."
            }
        } finally {
            if (!stopping) stopSelf()
        }
    }

    override fun onDestroy() {
        stopping = true
        readJob?.cancel()
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { projection?.stop() }
        projection = null
        VoiceUi.store.level(0f)
        _running.value = false
        if (_message.value.startsWith("Visualizador musical activo")) {
            _message.value = "Visualizador musical desactivado"
        }
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Visualizador musical de Oxxi",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Indica cuándo Oxxi está sincronizando el holograma con el audio."
                    setSound(null, null)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Oxxi · visualizador musical")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }
}

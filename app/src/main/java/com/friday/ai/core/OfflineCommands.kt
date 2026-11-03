package com.friday.ai.core

/**
 * Frases que Oxxi puede reconocer y ejecutar sin conexión.
 *
 * La gramática está en español porque el reconocedor sin conexión utiliza
 * el modelo acústico español de Vosk.
 */
object OfflineCommands {

    /** Frases de control del teléfono admitidas por el reconocedor sin red. */
    val GRAMMAR: List<String> = listOf(
        // Linterna y volumen
        "enciende la linterna", "apaga la linterna", "linterna",
        "sube el volumen", "baja el volumen",
        "apaga el sonido", "activa el sonido",
        // Abrir aplicaciones y controles del dispositivo
        "abre la cámara", "abre whatsapp", "abre los mensajes", "abre el navegador",
        "abre los ajustes", "abre wifi", "abre bluetooth",
        // Cámara y grabadora
        "haz una foto", "saca una foto", "selfi", "graba un vídeo", "abre la grabadora",
        // Ajustes rápidos y No molestar
        "abre nfc", "abre la pantalla", "abre el sonido", "abre la batería", "abre la ubicación",
        "activa no molestar", "desactiva no molestar",
        // Fin de conversación
        "adiós", "hasta luego", "nos vemos", "eso es todo"
    )

    /** True when [text] can be handled entirely by the phone, without Groq. */
    fun isOfflineCapable(command: Any?): Boolean = when (command) {
        is com.friday.ai.domain.model.CommandResult.ToggleFlashlight,
        is com.friday.ai.domain.model.CommandResult.Flashlight,
        is com.friday.ai.domain.model.CommandResult.SendMessage,
        is com.friday.ai.domain.model.CommandResult.DeviceControl,
        is com.friday.ai.domain.model.CommandResult.SetAlarm,
        is com.friday.ai.domain.model.CommandResult.SetTimer,
        is com.friday.ai.domain.model.CommandResult.OpenApp,
        is com.friday.ai.domain.model.CommandResult.CloseApp,
        is com.friday.ai.domain.model.CommandResult.OpenSettings,
        is com.friday.ai.domain.model.CommandResult.OpenCamera,
        is com.friday.ai.domain.model.CommandResult.RecordAudio -> true
        else -> false
    }

    /** What Oxxi says when a request needs a connection. */
    fun offlineRefusal(russian: Boolean): String =
        if (russian) "Нет сети — могу только управлять телефоном."
        else "No hay conexión; ahora solo puedo controlar el teléfono."
}

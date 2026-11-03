package com.friday.ai.service.voice

import com.friday.ai.data.remote.GroqApiException
import java.io.IOException

/**
 * Turns a failed answer into something worth hearing.
 */
object VoiceErrors {

    private const val UNAUTHORISED = 401
    private const val RATE_LIMITED = 429

    @Suppress("UNUSED_PARAMETER")
    fun spoken(e: Throwable, russian: Boolean, spanish: Boolean = false): String = when {
        e is GroqApiException && e.modelMissing ->
            "Groq ha cambiado sus modelos; ya he seleccionado otro. Repítemelo, por favor."
        e is GroqApiException && e.code == UNAUTHORISED ->
            "La clave de Groq no es válida. Revísala en los ajustes."
        e is GroqApiException && e.code == RATE_LIMITED ->
            "Groq me pide esperar: se ha alcanzado el límite de solicitudes."
        e is IOException ->
            "No puedo conectar con Groq. Comprueba la conexión a internet."
        else -> "Groq no ha respondido: ${e.message ?: e::class.simpleName}"
    }

    /** Said when the model answered with nothing at all. */
    @Suppress("UNUSED_PARAMETER")
    fun empty(russian: Boolean, spanish: Boolean = false): String =
        "La respuesta ha llegado vacía. Prueba de nuevo."
}

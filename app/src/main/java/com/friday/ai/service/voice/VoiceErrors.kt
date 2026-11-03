package com.friday.ai.service.voice

import com.friday.ai.data.remote.GroqApiException
import java.io.IOException

/**
 * Turns a failed answer into something worth hearing.
 */
object VoiceErrors {

    private const val UNAUTHORISED = 401
    private const val RATE_LIMITED = 429

    fun spoken(e: Throwable, russian: Boolean, spanish: Boolean = false): String = when {
        e is GroqApiException && e.modelMissing -> when {
            russian -> "Groq ha cambiado sus modelos; ya he seleccionado otro. Repítemelo, por favor."
            spanish -> "Groq ha cambiado sus modelos; ya he seleccionado otro. Repítemelo, por favor."
            else -> "Groq changed its models; I've switched over. Please say that again."
        }
        e is GroqApiException && e.code == UNAUTHORISED -> when {
            russian -> "La clave de Groq no es válida. Revísala en los ajustes."
            spanish -> "La clave de Groq no es válida. Revísala en los ajustes."
            else -> "The Groq key was rejected. Please check it in settings."
        }
        e is GroqApiException && e.code == RATE_LIMITED -> when {
            russian -> "Groq просит подождать: исчерпан лимит запросов."
            spanish -> "Groq me pide esperar: se ha alcanzado el límite de solicitudes."
            else -> "Groq asks me to wait: the request limit is used up."
        }
        e is IOException -> when {
            russian -> "Не могу связаться с Groq. Проверьте интернет."
            spanish -> "No puedo conectar con Groq. Comprueba la conexión a internet."
            else -> "I can't reach Groq. Check the connection."
        }
        else -> when {
            russian -> "Groq не ответил: ${e.message ?: e::class.simpleName}"
            spanish -> "Groq no ha respondido: ${e.message ?: e::class.simpleName}"
            else -> "Groq didn't answer: ${e.message ?: e::class.simpleName}"
        }
    }

    /** Said when the model answered with nothing at all. */
    fun empty(russian: Boolean, spanish: Boolean = false): String = when {
        russian -> "Модель ответила пустотой. Попробуйте ещё раз."
        spanish -> "La respuesta ha llegado vacía. Prueba de nuevo."
        else -> "The model came back empty. Please try again."
    }
}

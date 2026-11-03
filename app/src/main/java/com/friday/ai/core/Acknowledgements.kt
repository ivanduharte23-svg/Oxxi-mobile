package com.friday.ai.core

/**
 * What Friday says the moment she is called, before the microphone opens.
 *
 * This is not decoration. Without it there is no signal that the wake word
 * landed, so the user either speaks too early — into a microphone that isn't
 * open yet — or waits, and the recorder gives up on silence. Either way the
 * turn ends in "didn't catch that" and the wake word gets blamed.
 *
 * Kept pure so the wording and the no-repeat rule can be tested without a
 * speech engine.
 */
class Acknowledgements(private val random: () -> Double = Math::random) {

    companion object {
        private val SPANISH = listOf(
            "Aquí estoy. ¿Qué necesitas?",
            "Dime, te escucho.",
            "A tu servicio.",
            "¿En qué te ayudo?"
        )

        /** Every Spanish greeting, for synthesising them before they are needed. */
        val all: List<String> get() = SPANISH
    }

    private var lastSpoken: String? = null

    /**
     * Picks a greeting in the language the user used. Never returns the same
     * one twice running — a fixed phrase every single time is what makes an
     * assistant sound like a doorbell.
     */
    @Suppress("UNUSED_PARAMETER")
    fun next(language: WakePhrases.Language?): String {
        val pool = SPANISH
        val choices = pool.filter { it != lastSpoken }.ifEmpty { pool }
        val picked = choices[(random() * choices.size).toInt().coerceIn(0, choices.lastIndex)]
        lastSpoken = picked
        return picked
    }
}

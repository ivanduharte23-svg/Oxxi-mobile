package com.friday.ai.core.modes

import com.friday.ai.core.SpokenText

/**
 * Whether two ways of saying a mode's name are the same name.
 *
 * Compared by word stems, so the case Russian puts them in doesn't matter:
 * "режим отдыха" finds a mode named "отдых", "грусть" finds "грусти",
 * "грустный" doesn't (a different word).
 */
object ModeNames {

    private const val MIN_STEM = 3
    private const val ENDING = 2

    private val stopWords = setOf("мой", "моя", "мое", "мои", "мне", "the", "my", "пожалуйста", "please")

    /** Cross-language names for phone settings, used when matching a spoken removal request. */
    private val overlapAliases = mapOf(
        "яркость" to setOf("brillo", "brightness"),
        "громкость" to setOf("volumen", "volume"),
        "звук" to setOf("sonido", "volume"),
        "звонок" to setOf("timbre", "ringer"),
        "фонарик" to setOf("linterna", "flashlight"),
        "музыка" to setOf("música", "music")
    )

    /**
     * Whether [spoken] names [name] (or one of its aliases): every word of the
     * name matches a spoken word by stem, in order, with at most one spoken
     * word to spare ("режим грусти пожалуйста").
     */
    fun same(spoken: String, name: String, spare: Int = 1): Boolean {
        val said = words(spoken)
        val wanted = words(name)
        if (wanted.isEmpty() || said.size - wanted.size !in 0..spare) return false
        var i = 0
        for (w in said) if (i < wanted.size && sameStem(w, wanted[i])) i++
        return i == wanted.size
    }

    private fun words(s: String) = SpokenText.normalise(s).split(' ').filter { it.isNotBlank() && it !in stopWords }

    /**
     * How much [phrase] sounds like [text]: the share of its words that
     * match one of the text's words by stem. For finding the step "убери
     * будильник" means.
     */
    fun overlap(phrase: String, text: String): Double {
        val wanted = words(phrase)
        val have = words(text)
        if (wanted.isEmpty()) return 0.0
        return wanted.count { w ->
            val aliases = overlapAliases[w].orEmpty()
            have.any { h ->
                sameStem(w, h) || aliases.any { alias -> sameStem(alias, h) } ||
                    overlapAliases[h].orEmpty().any { alias -> sameStem(w, alias) }
            }
        }.toDouble() / wanted.size
    }

    /** "грусти"/"грусть", "отдыха"/"отдых": the same word in another case. */
    private fun sameStem(a: String, b: String): Boolean {
        if (a == b) return true
        val common = a.zip(b).takeWhile { (x, y) -> x == y }.size
        return common >= maxOf(MIN_STEM, minOf(a.length, b.length) - ENDING)
    }
}

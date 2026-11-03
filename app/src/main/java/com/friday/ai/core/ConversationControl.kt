package com.friday.ai.core

/**
 * Decides when a spoken conversation is over.
 *
 * The voice loop keeps listening after each reply so you can talk naturally
 * without repeating the wake word. This also owns the explicit Oxxi shutdown
 * phrases so "desactívate" really turns the background listener off.
 */
object ConversationControl {

    /** Called by the application when the owner explicitly asks Oxxi to deactivate. */
    @Volatile
    var onDeactivate: (() -> Unit)? = null

    private val DEACTIVATION_PHRASES = setOf(
        "desactívate", "desactivate", "desactivaste",
        "apágate", "apagate",
        "desactiva oxxi", "desactive oxxi", "desactivar oxxi",
        "apaga oxxi", "apague oxxi", "apagar oxxi",
        "desconéctate oxxi", "desconectate oxxi",
        "cállate oxxi", "callate oxxi",
        "detente oxxi", "detente, oxxi",
        "oxxi desactívate", "oxxi desactivate", "oxxi desactivaste",
        "oxxi apágate", "oxxi apagate"
    )

    /** Single words that end a conversation when the utterance is short. */
    private val FAREWELL_WORDS = setOf(
        "adiós", "adios", "hasta luego", "hasta pronto", "nos vemos", "ya está", "ya esta", "terminamos", "descansa", "desconéctate", "desconectate", "nada más", "nada mas", "eso es todo", "poco más", "poco mas", "пока", "покеда", "стоп", "хватит", "довольно", "всё", "все",
        "спасибо", "отбой", "закончили", "выйди", "отстань",
        "bye", "goodbye", "stop", "enough", "thanks", "thank", "quit",
        "exit", "cancel", "dismiss", "nevermind"
    )

    /** Multi-word endings that close a short utterance. */
    private val FAREWELL_PHRASES = listOf(
        "that's all", "thats all", "that is all", "nothing else", "never mind", "that'll be all", "hasta luego", "hasta pronto", "nos vemos", "ya está", "ya esta", "eso es todo", "no necesito nada más", "no necesito nada mas", "hemos terminado", "terminamos", "puedes descansar", "descansa", "desconéctate", "desconectate",
        "we're done", "i'm done", "это всё", "это все", "больше ничего", "на этом всё", "на этом все",
        "на этом пока", "на сегодня всё", "на сегодня все", "спасибо всё", "спасибо все", "до свидания",
        "всего доброго", "до связи", "можешь отдыхать", "ты свободна", "отключайся"
    )

    private val CLOSING_WORDS = setOf("пока", "покеда", "отбой", "bye", "goodbye")
    private val CLOSING_PHRASES = listOf(
        "на этом пока", "на этом всё", "на этом все", "на сегодня всё", "на сегодня все", "до связи",
        "до встречи", "до свидания", "спокойной ночи", "увидимся", "hasta luego", "hasta pronto", "nos vemos", "buenas noches", "puedes descansar", "descansa", "eso es todo", "see you", "talk later", "good night"
    )

    private val CLOSING_FILLER = setOf(
        "vale", "bueno", "de acuerdo", "entonces", "venga", "okey", "ok", "ya", "está", "esta", "eso", "todo", "gracias", "hasta", "luego", "nos", "vemos", "ну", "ладно", "тогда", "давай", "окей", "ок", "всё", "все", "хорошо", "отлично", "на", "этом", "сегодня",
        "спасибо", "okay", "alright", "thanks", "then", "for", "now"
    )

    private const val MAX_WORDS_FOR_PHRASE = 6
    private const val MAX_WORDS_FOR_SHORT_FAREWELL = 4
    private val clauseEnd = Regex("""[,.;!?…—]+""")

    /** True when the utterance explicitly asks Oxxi to deactivate. */
    fun isDeactivation(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return false
        return normalized in DEACTIVATION_PHRASES ||
            normalized.matches(Regex("^oxxi\\s+(?:desactiva|desactive|desactivar|apaga|apague|apagar|desconecta|desconectate|callate|cállate|detente)\\s*$"))
    }

    /** True when [text] is the user (or the assistant) closing the conversation. */
    fun isFarewell(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return false

        if (isDeactivation(normalized)) {
            onDeactivate?.invoke()
            return true
        }

        val words = words(normalized)
        if (words.size <= MAX_WORDS_FOR_PHRASE && FAREWELL_PHRASES.any { normalized.contains(it) }) return true
        if (words.isEmpty() || words.size > MAX_WORDS_FOR_SHORT_FAREWELL) return false

        return words.any { it in FAREWELL_WORDS }
    }

    /** What was said before a closing goodbye. */
    fun beforeFarewell(text: String): String? {
        var (rest, last) = lastClause(text) ?: return null
        val lastWords = words(last)
        val closes = CLOSING_PHRASES.any { last == it || last.endsWith(" $it") || last.startsWith("$it ") } ||
            (lastWords.any { it in CLOSING_WORDS } && lastWords.all { it in CLOSING_WORDS || it in CLOSING_FILLER })
        if (!closes) return null
        while (true) {
            val (before, clause) = lastClause(rest) ?: break
            if (!words(clause).all { it in CLOSING_FILLER }) break
            rest = before
        }
        return rest.takeIf { it.isNotEmpty() }
    }

    private fun lastClause(text: String): Pair<String, String>? {
        val trimmed = text.trim().trimEnd { it in ",.;!?…—" || it.isWhitespace() }
        val cut = clauseEnd.findAll(trimmed).lastOrNull() ?: return null
        val before = trimmed.substring(0, cut.range.first).trim()
        return before to trimmed.substring(cut.range.last + 1).lowercase().trim()
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .trim()
        .replace(Regex("[.,!?…]+$"), "")
        .replace(Regex("\\s+"), " ")

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotBlank() }
}

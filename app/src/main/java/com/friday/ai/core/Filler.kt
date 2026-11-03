package com.friday.ai.core

/**
 * Removes conversational lead-ins before command routing. Speech recognition
 * often returns "vale, ahora abre YouTube" or "bueno Oxxi, enciende la linterna";
 * those lead-ins must not make an otherwise valid command fall through to chat.
 */
object Filler {
    private val leading = Regex(
        """^(?:bueno|buena|vale|okey|ok|de acuerdo|perfecto|genial|bien|claro|entendido|exacto|gracias|por favor|oye|oye oxxi|hey|hey oxxi|bueno oxxi|vale oxxi|ok oxxi|gracias oxxi|хорошо|ладно|окей|отлично|понятно|ясно|спасибо|okay|alright|thanks|great)[,.!?:]?\s+(?:(?:ahora|entonces|pues|y|pero|también|tambien|so|and|but|then|а|но|и|тогда)\s+)?(?:(?:ahora|ahora mismo|ahorita|теперь|сейчас|now)\s+)?""",
        RegexOption.IGNORE_CASE
    )

    /** [text] without its lead-in; unchanged when nothing would be left. */
    fun strip(text: String): String {
        val original = text.trim()
        var rest = leading.replaceFirst(original, "").trim()
        // Common ASR punctuation can leave a second comma before the command.
        rest = rest.trimStart(',', '.', '!', '?', ':', ';', '—', '-')
        return if (rest.isEmpty()) original else rest.replaceFirstChar { it.uppercase() }
    }
}

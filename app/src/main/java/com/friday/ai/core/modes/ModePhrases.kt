package com.friday.ai.core.modes

import com.friday.ai.domain.model.CommandResult

/**
 * Spanish voice phrases for creating, running, editing and deleting saved modes.
 * Russian and English aliases remain accepted for backwards compatibility.
 */
object ModePhrases {
    private const val MAX_NAME_WORDS = 4
    private const val EN_DESCRIPTION = 3

    private val wake = Regex("^(?:oxxi|пятница|friday)[,!.\\s]+")

    private val create = Regex(
        "^(?:por favor\\s+|пожалуйста\\s+)?(?:crea|crear|configura|configurar|prepara|preparar|añade|añadir|" +
            "crea|создай|сделай|заведи|добавь|запомни|настрой|придумай)\\s+" +
            "(?:(?:el|un|nuevo|me|мне|новый)\\s+)*(?:modo|режим)\\s+(.+)$",
        RegexOption.DOT_MATCHES_ALL
    )
    private val createEn = Regex(
        "^(?:please\\s+)?(?:create|make|add|set up)\\s+(?:me\\s+)?(?:a\\s+|an\\s+|new\\s+)*" +
            "(?:mode\\s+(?:called\\s+)?(.+?)|(.+?)\\s+mode)" +
            "(?:\\s*[.:;,—–-]\\s*|\\s+(?=(?:where|when|that|which|it)\\s)|$)(.*)$",
        RegexOption.DOT_MATCHES_ALL
    )

    private val separator = Regex(
        "\\s*(?:[.:;!?—–]|\\s-\\s|,)\\s*|\\s+(?=(?:это|где|в котором|при котором|когда|чтобы|в нем|" +
            "que|cuando|con|donde|en el que|para)\\s)"
    )
    private val descriptionLead = Regex(
        "^(?:это\\s+(?:такой\\s+)?(?:режим|когда)?,?\\s*|где\\s+|в котором\\s+|при котором\\s+|когда\\s+|в нем\\s+|" +
            "que\\s+(?:es\\s+)?(?:el\\s+)?modo\\s+|cuando\\s+|con\\s+|donde\\s+|en el que\\s+|para\\s+|" +
            "чтобы\\s+|where\\s+|when\\s+|that\\s+|which\\s+|it\\s+)+"
    )
    private val nameLead = Regex(
        "^(?:под названием|который называется|называется|с названием|llamado|llamada|denominado|called|named)\\s+"
    )

    private val exit = listOf(
        Regex("^(?:apaga|desactiva|detén|deten|termina|cancela|elimina|borra|quita)\\s+(?:el\\s+)?modo\\s+(.+)$"),
        Regex("^(?:sal|salir)\\s+del\\s+modo\\s+(.+)$"),
        Regex("^(?:выключи|отключи|останови|заверши|отмени|сними|верни все из|убери)\\s+режим\\p{L}*\\s+(.+)$"),
        Regex("^(?:выйди|выйти|выход|выходим)\\s+из\\s+режим\\p{L}*\\s+(.+)$"),
        Regex("^(?:stop|deactivate|turn off|exit|end|disable|leave)\\s+(?:the\\s+)?(?:mode\\s+(.+)|(.+?)\\s+mode)$")
    )
    private val describe = listOf(
        Regex("^(?:qué hace|que hace|describe|explica|muestra)\\s+(?:el\\s+)?modo\\s+(.+)$"),
        Regex("^(?:что\\s+(?:делает|включает|будет в|в)|расскажи\\s+(?:про|о)|покажи)\\s+режим\\p{L}*\\s+(.+)$"),
        Regex("^what does (?:the\\s+)?(.+?)\\s+mode do$")
    )
    private val delete = listOf(
        Regex("^(?:elimina|borrar|borra|olvida|quita)\\s+(?:el\\s+)?modo\\s+(.+)$"),
        Regex("^(?:удали|сотри|забудь|delete|remove|forget)\\s+(?:режим\\p{L}*|(?:the\\s+)?mode)\\s+(.+)$"),
        Regex("^(?:delete|remove|forget)\\s+(?:the\\s+)?(.+?)\\s+mode$")
    )
    private val addTo = listOf(
        Regex("^(?:añade|agrega|mete)\\s+(?:al|en el)\\s+modo\\s+(.+)$"),
        Regex("^(?:добавь|допиши|включи)\\s+в\\s+режим\\p{L}*\\s+(.+)$"),
        Regex("^add\\s+to\\s+(?:the\\s+)?mode\\s+(.+)$")
    )
    private val removeFrom = listOf(
        Regex("^(?:quita|elimina|borra)\\s+(.+?)\\s+del\\s+modo\\s+(.+)$"),
        Regex("^(?:убери|удали|выкинь|исключи)\\s+(.+?)\\s+из\\s+режим\\p{L}*\\s+(.+)$"),
        Regex("^(?:remove|take)\\s+(.+?)\\s+(?:out\\s+)?(?:of|from)\\s+(?:the\\s+)?(.+?)\\s+mode$")
    )

    private val list = Regex(
        "^(?:qué\\s+modos\\s+tengo|que\\s+modos\\s+tengo|mis\\s+modos|lista\\s+de\\s+modos|" +
            "какие\\s+(?:у\\s+меня\\s+)?(?:есть\\s+)?режимы|мои\\s+режимы|список\\s+режимов|" +
            "(?:list|show)\\s+(?:my\\s+)?modes|what modes(?: do i have)?)$"
    )
    private val run = listOf(
        Regex("^(?:(?:activa|activar|inicia|iniciar|ejecuta|ejecutar|enciende|pon|cambia al)\\s+)?(?:el\\s+)?modo\\s+(.+)$"),
        Regex("^(?:(?:включи|запусти|активируй|вруби|давай|поставь|переключись на|перейди в)\\s+)?режим\\s+(.+)$"),
        Regex("^(?:(?:start|activate|turn on|enable|run|switch to)\\s+)?(?:the\\s+)?(.+?)\\s+mode$")
    )

    fun parse(text: String): CommandResult.Mode? {
        val t = clean(text)
        return parseCreate(t)
            ?: parseEdit(t)
            ?: CommandResult.Mode.ListAll.takeIf { list.containsMatchIn(t) }
            ?: firstName(t, describe)?.let { CommandResult.Mode.Describe(it) }
            ?: firstName(t, delete)?.let { CommandResult.Mode.Delete(it) }
            ?: firstName(t, exit)?.let { CommandResult.Mode.Exit(it) }
            ?: firstName(t, run)?.let { CommandResult.Mode.Run(it) }
    }

    private fun parseEdit(t: String): CommandResult.Mode? =
        addTo.firstNotNullOfOrNull { it.find(t) }?.let { CommandResult.Mode.AddTo(it.groupValues[1]) }
            ?: removeFrom.firstNotNullOfOrNull { it.find(t) }?.let { m ->
                name(m.groupValues[2])?.let { CommandResult.Mode.RemoveFrom(m.groupValues[1], it) }
            }

    private fun parseCreate(t: String): CommandResult.Mode.Create? =
        create.find(t)?.let { m -> split(m.groupValues[1]) }
            ?: createEn.find(t)?.let { m ->
                name(m.groupValues[1].ifEmpty { m.groupValues[2] })
                    ?.let { CommandResult.Mode.Create(it, description(m.groupValues[EN_DESCRIPTION])) }
            }

    private fun split(rest: String): CommandResult.Mode.Create? {
        val cut = separator.find(rest)
        val (rawName, rawDescription) = if (cut != null) {
            rest.substring(0, cut.range.first) to rest.substring(cut.range.last + 1)
        } else {
            val words = rest.split(' ').filter { it.isNotBlank() }
            if (words.size <= MAX_NAME_WORDS - 1) rest to "" else words.first() to words.drop(1).joinToString(" ")
        }
        val name = name(rawName) ?: return null
        return CommandResult.Mode.Create(name, description(rawDescription))
    }

    private fun name(raw: String): String? {
        val n = raw.replace(nameLead, "").trim(' ', '«', '»', '"', '\'', '“', '”', '.', ',', '!', '?')
        val words = n.split(' ').filter { it.isNotBlank() }
        return n.takeIf { words.isNotEmpty() && words.size <= MAX_NAME_WORDS }
    }

    private fun description(raw: String?): String? =
        raw.orEmpty().trim().replace(descriptionLead, "").trim(' ', '.', ',', '!', ';', ':').takeIf { it.length > 2 }

    private fun firstName(t: String, patterns: List<Regex>): String? = patterns.firstNotNullOfOrNull { p ->
        p.find(t)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.let(::name)
    }

    private fun clean(text: String): String =
        text.trim().lowercase().replace('ё', 'е').replace(wake, "").trim().trimEnd('.', '!', '?', ' ')
}

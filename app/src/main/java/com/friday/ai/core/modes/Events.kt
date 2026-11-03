package com.friday.ai.core.modes

/** What can start a mode besides the owner's voice and the clock. */
enum class Trigger(val key: String) {
    BLUETOOTH("bluetooth"), WIFI("wifi"), CHARGER("charger");

    companion object {
        fun of(key: String): Trigger? = entries.firstOrNull { it.key == key }
    }
}

/** One external condition that can activate or deactivate a saved mode. */
data class ModeEvent(
    val id: String,
    val modeId: String,
    val trigger: Trigger,
    /** The Bluetooth device's name or the network's SSID; empty for the charger. */
    val value: String,
    val onConnect: Boolean,
    val exit: Boolean
)

interface NfcTags {
    fun available(): Boolean
    fun startWriting(modeId: String, modeName: String)
}

interface PhoneLinks {
    data class Bluetooth(val permitted: Boolean, val paired: List<String>, val connected: List<String>)
    suspend fun bluetooth(): Bluetooth
    suspend fun wifi(): String?
}

/** Spanish and legacy phrases for automatic mode triggers. */
object EventPhrases {
    data class Request(
        val rest: String,
        val trigger: Trigger,
        val target: String,
        val onConnect: Boolean,
        val exit: Boolean
    )

    private val wake = Regex("^(?:oxxi|пятница|friday)[,!.\\s]+")
    private val whenWord = Regex("(?:^|\\s)(?:cuando|en cuanto|al|как только|когда|при|when|whenever)\\s")

    private val chargerOn = Regex(
        "(?:pongo|dejo|conecto|enchufo)\\s+(?:(?:el|mi)\\s+)?(?:teléfono|móvil)\\s+(?:a cargar|al cargador|en el cargador)|" +
            "(?:pongo|dejo)\\s+(?:el\\s+)?cargador|" +
            "(?:ставлю|поставлю|кладу|положу|подключаю|подключу)\\s+(?:телефон\\s+)?(?:на|к)\\s+зарядк|" +
            "(?:подключаю|подключу|втыкаю)\\s+зарядк|подключени\\p{L}* зарядк|" +
            "(?:put|plug)\\s+(?:it\\s+|the phone\\s+)?(?:on|in)"
    )
    private val chargerOff = Regex(
        "(?:quito|desconecto|desenchufo)\\s+(?:(?:el|mi)\\s+)?(?:teléfono|móvil)\\s+(?:del cargador|de cargar)|" +
            "(?:quito|desenchufo)\\s+(?:el\\s+)?cargador|" +
            "(?:снимаю|сниму|убираю|уберу)\\s+(?:телефон\\s+)?с\\s+зарядк|" +
            "(?:отключаю|отключу|вынимаю|выну)\\s+зарядк|unplug"
    )
    private val connectTo = Regex(
        "(?:me conecto|conecto|se conecta|conectas|conecto el teléfono|conectamos|" +
            "подключаюсь|подключусь|подключается|подключился|подключении|сажусь|сяду|захожу|зайду|connect(?:s|ed)?)" +
            "\\s+(?:(?:(?:el|mi)\\s+)?(?:teléfono|móvil)\\s+)?(?:к|в|a la|al|a|con|en|to|in)\\s+" +
            "(.+?)(?=,|\\s+(?:—|-|activa|desactiva|enciende|apaga|включай|выключай|запускай|отключай|режим|modo)|$)"
    )
    private val disconnectFrom = Regex(
        "(?:me desconecto|desconecto|se desconecta|sales|отключаюсь|отключусь|отключается|отключился|отключении|выхожу|выйду|disconnect(?:s|ed)?)" +
            "\\s+(?:(?:del|de la|de|от|из|from|of)\\s+)?(.+?)(?=,|\\s+(?:—|-|activa|desactiva|apaga|включай|выключай|запускай|отключай|режим|modo)|$)"
    )
    private val home = Regex(
        "(?:llego|vuelvo|regreso)\\s+a\\s+casa|(?:прихожу|приду|возвращаюсь|вернусь)\\s+домой|get home|arrive home"
    )
    private val wifiWords = Regex(
        "(?:\\b(?:la\\s+)?red(?:\\s+wi-?fi)?\\b|\\bwi-?fi\\b|\\bwifi\\b|\\bnetwork\\b|вай-?фа\\p{L}*|сет\\p{L}*|internet)"
    )
    private val exitVerb = Regex(
        "(?:^|\\s)(?:apaga|desactiva|termina|sal|выключай|отключай|выходи|turn off|end)\\s+(?:(?:el\\s+)?(?:режим|modo)|mode)"
    )
    private val modeName = Regex(
        "(?:режим\\p{L}*|modo|mode)\\s+(.+?)(?=,|\\s+(?:—|-)\\s|\\s+(?:когда|как только|при|cuando|en cuanto|al|when)\\s|$)"
    )
    private val btWords = Regex("\\s+(?:блютуз\\p{L}*|bluetooth|dispositivo bluetooth)")
    private val spaces = Regex("\\s+")

    fun parse(text: String): Request? {
        val t = text.trim().lowercase().replace('ё', 'е').replace(wake, "").trimEnd('.', '!', '?', ' ')
        val rest = modeName.find(t)?.groupValues?.get(1)?.trim()
        val found = trigger(t)
        if (!whenWord.containsMatchIn(" $t") || rest == null || found == null) return null
        val (trigger, target, onConnect) = found
        return Request(rest, trigger, target, onConnect, exit = exitVerb.containsMatchIn(" $t"))
    }

    private fun trigger(t: String): Triple<Trigger, String, Boolean>? {
        val connected = connectTo.find(t)?.groupValues?.get(1)?.trim()
        val disconnected = disconnectFrom.find(t)?.groupValues?.get(1)?.trim()
        return when {
            chargerOff.containsMatchIn(t) -> Triple(Trigger.CHARGER, "", false)
            chargerOn.containsMatchIn(t) -> Triple(Trigger.CHARGER, "", true)
            home.containsMatchIn(t) -> Triple(Trigger.WIFI, "casa", true)
            connected != null -> Triple(kindOf(connected), cleanTarget(connected), true)
            disconnected != null -> Triple(kindOf(disconnected), cleanTarget(disconnected), false)
            else -> null
        }
    }

    private fun kindOf(target: String) = if (wifiWords.containsMatchIn(target)) Trigger.WIFI else Trigger.BLUETOOTH

    private fun cleanTarget(target: String): String = target
        .replace(wifiWords, "")
        .replace(btWords, "")
        .replace(Regex("^(?:la|el)\\s+"), "")
        .replace(spaces, " ")
        .trim()

    /** Match a spoken device against paired names, refusing guesses below half overlap. */
    fun device(spoken: String, names: List<String>): String? = names
        .map { it to ModeNames.overlap(spoken, it) }
        .filter { it.second >= MIN_MATCH }
        .maxByOrNull { it.second }
        ?.first

    private const val MIN_MATCH = 0.5
}

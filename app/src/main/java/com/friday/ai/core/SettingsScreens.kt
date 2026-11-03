package com.friday.ai.core

import android.provider.Settings

/**
 * Maps what the user says to a system settings screen.
 *
 * Almost nothing in Android's settings can be *changed* by a third-party app
 * any more — NFC, Wi-Fi, Bluetooth and mobile data all lost their programmatic
 * toggles between Android 10 and 13. Opening the right screen is the whole of
 * what is actually available, so that is what this does, and Friday says so
 * rather than claiming to have flipped a switch she cannot reach.
 *
 * Pure and free of Android calls apart from the intent action constants, which
 * are compile-time strings.
 */
object SettingsScreens {

    /**
     * @param action  the system intent action to launch
     * @param label   what Friday calls it when confirming, in Russian
     * @param keywords spoken forms, all lower case, matched as whole words
     */
    enum class Screen(
        val action: String,
        val label: String,
        val keywords: List<String>
    ) {
        NFC(Settings.ACTION_NFC_SETTINGS, "NFC",
            listOf("nfc", "etiqueta nfc", "нфс", "энфси")),

        WIFI(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi",
            listOf("wifi", "wi-fi", "red wifi", "conexión wifi", "вайфай", "вай-фай", "вифи")),

        BLUETOOTH(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth",
            listOf("bluetooth", "activar bluetooth", "блютус", "блютуз", "блутуз")),

        // Not bare "интернет": "открой интернет" means a browser to most people.
        MOBILE_DATA(Settings.ACTION_DATA_ROAMING_SETTINGS, "datos móviles",
            listOf("datos móviles", "internet móvil", "red móvil", "itinerancia", "roaming", "mobile data", "мобильные данные", "мобильный интернет", "мобильную сеть", "роуминг")),

        HOTSPOT("android.settings.TETHER_SETTINGS", "punto de acceso",
            listOf("punto de acceso", "compartir internet", "zona wifi", "hotspot", "tethering", "модем")),

        AIRPLANE(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "modo avión",
            listOf("modo avión", "modo avion", "avión", "modo vuelo", "режим полета", "режим полёта", "авиарежим", "airplane")),

        LOCATION(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "ubicación",
            listOf("ubicación", "ubicacion", "localización", "localizacion", "gps", "location", "геолокация", "геолокацию", "локация", "местоположение")),

        BATTERY("android.settings.BATTERY_SAVER_SETTINGS", "batería",
            listOf("batería", "bateria", "ahorro de batería", "ahorro de energia", "battery", "батарея", "батарею", "аккумулятор", "энергосбережение")),

        DISPLAY(Settings.ACTION_DISPLAY_SETTINGS, "pantalla",
            listOf("pantalla", "brillo", "visualización", "display", "brightness", "экран", "дисплей", "яркость")),

        SOUND(Settings.ACTION_SOUND_SETTINGS, "sonido",
            listOf("sonido", "volumen", "tono de llamada", "sonidos", "sound", "звук", "звуки", "громкость", "рингтон")),

        // Qualified only. Bare "приложение" would hijack "открой приложение
        // телеграм", which belongs to the app launcher.
        APPS(Settings.ACTION_APPLICATION_SETTINGS, "ajustes de aplicaciones",
            listOf("ajustes de aplicaciones", "configuración de aplicaciones", "todas las aplicaciones", "gestor de aplicaciones", "app settings", "настройки приложений", "все приложения", "диспетчер приложений")),

        STORAGE(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "almacenamiento",
            listOf("almacenamiento", "espacio de almacenamiento", "memoria", "storage", "память", "хранилище")),

        SECURITY(Settings.ACTION_SECURITY_SETTINGS, "seguridad",
            listOf("seguridad", "bloqueo", "contraseña", "security", "безопасность", "блокировка", "пароль")),

        ACCESSIBILITY(Settings.ACTION_ACCESSIBILITY_SETTINGS, "accesibilidad",
            listOf("accesibilidad", "opciones de accesibilidad", "accessibility", "спец возможности", "специальные возможности", "доступность")),

        DEVELOPER(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "opciones de desarrollador",
            listOf("opciones de desarrollador", "opciones para desarrolladores", "depuración", "developer", "debugging", "разработчик", "разработчика", "разработчиков", "отладка")),

        // "покажи время" is a question, not a request for a settings screen,
        // so the bare words are left out.
        DATE_TIME(Settings.ACTION_DATE_SETTINGS, "fecha y hora",
            listOf("fecha y hora", "ajustes de fecha", "ajustes de hora", "zona horaria", "date and time", "дату и время", "настройки времени", "часовой пояс")),

        LANGUAGE(Settings.ACTION_LOCALE_SETTINGS, "idioma",
            listOf("idioma", "idiomas", "teclado", "language", "keyboard", "язык", "языки", "клавиатура")),

        NOTIFICATIONS("android.settings.NOTIFICATION_SETTINGS", "notificaciones",
            listOf("notificaciones", "notificación", "notifications", "уведомления", "уведомление")),

        VPN(Settings.ACTION_VPN_SETTINGS, "VPN",
            listOf("vpn", "впн")),

        ABOUT(Settings.ACTION_DEVICE_INFO_SETTINGS, "información del teléfono",
            listOf("información del teléfono", "acerca del teléfono", "versión de android", "about phone", "о телефоне", "о телефон", "версия андроид")),

        /** The fallback: the settings app itself. */
        ROOT(Settings.ACTION_SETTINGS, "ajustes",
            listOf("ajustes", "configuración", "configuraciones", "settings", "настройки", "настройка"));
    }

    /**
     * Which settings screen [phrase] is asking for, or null if none of them.
     *
     * Longest keyword first so "мобильные данные" beats "данные", and ROOT is
     * checked last so "открой настройки звука" lands on sound rather than the
     * settings root.
     */
    fun match(phrase: String): Screen? {
        val text = SpokenText.normalise(phrase)
        return Screen.values()
            .filter { it != Screen.ROOT }
            .flatMap { screen -> screen.keywords.map { screen to it } }
            .sortedByDescending { it.second.length }
            .firstOrNull { (_, keyword) -> containsPhrase(text, keyword) }
            ?.first
            ?: Screen.ROOT.takeIf { Screen.ROOT.keywords.any { k -> containsPhrase(text, k) } }
    }

    /**
     * Whole-word containment, tolerant of Russian case endings.
     *
     * Plain substring matching would make short keywords fire inside unrelated
     * words, and `\b` is ASCII-only in Java regex so it cannot guard Cyrillic
     * at all. But exact words are too strict in the other direction: "открой
     * настройки экрана" carries "экрана", not "экран", and used to fall
     * through to the settings root.
     */
    private fun containsPhrase(normalisedText: String, keyword: String): Boolean {
        val key = SpokenText.normalise(keyword)
        if (key.contains(' ')) {
            // Multi-word keywords are specific enough to match literally.
            return " $normalisedText ".contains(" $key ")
        }
        return normalisedText.split(' ').any { token -> sameWord(token, key) }
    }

    /** True when two words differ only by a case ending. */
    private fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        val x = stem(a)
        val y = stem(b)
        if (x.length < MIN_STEM || y.length < MIN_STEM) return false
        return x.startsWith(y) || y.startsWith(x)
    }

    /**
     * Drops one trailing character on longer words, which is enough to bring
     * "батарею" and "батарея" — or "экран" and "экрана" — to a common form.
     * A real morphological analyser would be overkill for a fixed keyword list.
     */
    private fun stem(w: String): String = if (w.length > 5) w.dropLast(1) else w

    /** Short stems collide too easily to be trusted ("сеть" vs "себя"). */
    private const val MIN_STEM = 4
}

// Long lines on purpose: a table of regexes, each kept on one line so it can be read whole.
@file:Suppress("MaxLineLength")

package com.friday.ai.core

import com.friday.ai.domain.model.CameraMode
import com.friday.ai.domain.model.CommandResult
import com.friday.ai.domain.model.DeviceAction
import com.friday.ai.core.mail.MailCommands
import com.friday.ai.domain.model.MediaAction

class CommandRouter(private val now: () -> java.time.LocalDateTime = java.time.LocalDateTime::now) {

    private companion object {
        /** "Громкость на минимум": quiet, but not the silence of mute. */
        const val MIN_VOLUME = 10
    }

    private val openPatterns = listOf(
        Regex("^(?:abre|abrir|abre|inicia|iniciar|открой|открыть|запусти|запустить|open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE)
    )

    private val closePatterns = listOf(
        Regex("^(?:cierra|cerrar|minimiza|sal de|salir|закрой|закрыть|сверни|свернуть|close|stop|quit)\\s+(.+)$", RegexOption.IGNORE_CASE)
    )

    private val analyzeScreenPatterns = listOf(
        Regex("(?:analiza|analizar|examina|проанализируй|анализируй|analyze|analyse)\\s+(?:la )?(?:pantalla|экран|screen|скрин|дисплей|display)", RegexOption.IGNORE_CASE),
        Regex("(?:qué hay en|que hay en|qué aparece en|que aparece en|что на|what'?s on)\\s+(?:la )?(?:pantalla|экране|screen|the screen)", RegexOption.IGNORE_CASE)
    )

    private val analyzeFilePatterns = listOf(
        Regex("(?:analiza|analizar|проанализируй|анализируй|analyze|analyse)\\s+(?:el )?(?:archivo|файл|file)(?:\\s+(.+))?", RegexOption.IGNORE_CASE)
    )

    private val timerPatterns = listOf(
        Regex("(?:pon (?:un )?temporizador|temporizador|cronómetro|cronometro|поставь таймер|таймер|засеки|set (?:a )?timer|timer)\\s+(?:durante|en|на|for)?\\s*(\\d+)\\s*(?:minuto?s?|min(?:ут)?|min(?:ute)?s?)", RegexOption.IGNORE_CASE),
        Regex("(?:pon (?:un )?temporizador|temporizador|cronómetro|cronometro|поставь таймер|таймер|засеки|set (?:a )?timer|timer)\\s+(?:durante|en|на|for)?\\s*(\\d+)\\s*(?:segundo?s?|сек(?:унд)?|sec(?:ond)?s?)", RegexOption.IGNORE_CASE)
    )

    private val flashlightOff = Regex("(?:apaga|desactiva|выключи|погаси|отключи|turn off)\\s+(?:la )?(?:linterna|фонарик|фонарь|flashlight|torch)", RegexOption.IGNORE_CASE)
    private val flashlightOn = Regex("(?:enciende|activa|включи|зажги|turn on)\\s+(?:la )?(?:linterna|фонарик|фонарь|flashlight|torch)", RegexOption.IGNORE_CASE)

    private val flashlightPatterns = listOf(
        Regex("toggle\\s+(?:фонарик|фонарь|linterna|flashlight|torch)", RegexOption.IGNORE_CASE),
        // Bare noun too: "фонарь" on its own was recognised but did nothing.
        Regex("^(?:linterna|фонарик|фонарь|flashlight|torch)$", RegexOption.IGNORE_CASE)
    )

    // Camera, recorder and settings. `\p{L}` throughout, never `\w`: `\w` is
    // ASCII-only in Java regex and silently never matches a Russian ending.
    private val cameraWordPattern =
        Regex("cámara|camara|camar|camera|fotoaparat|фотоаппарат", RegexOption.IGNORE_CASE)

    /** Capture asked for without naming the camera: "сфотографируй", "селфи". */
    private val bareCapturePattern = Regex(
        "haz\\s+(?:una\\s+)?foto|saca\\s+(?:una\\s+)?foto|toma\\s+(?:una\\s+)?foto|fotografía|fotografia|selfi|selfie|graba\\s+(?:un\\s+)?vídeo|graba\\s+(?:un\\s+)?video|saca\\s+una\\s+foto|сфотографир|сделай\\s+фото|сними\\s+фото|сделай\\s+снимок|селфи|" +
            "запиши\\s+виде|сними\\s+виде|take\\s+a?\\s*photo|record\\s+a?\\s*video",
        RegexOption.IGNORE_CASE
    )

    private val videoPattern = Regex("víde|video|graba|снимай", RegexOption.IGNORE_CASE)
    private val selfiePattern = Regex("selfi|selfie|cámara frontal|camara frontal|фронтал|front camera", RegexOption.IGNORE_CASE)
    private val photoPattern = Regex(
        "foto|fotografía|fotografia|saca una foto|haz una foto|сфотографир|снимок|снимк|photo|picture|shot", RegexOption.IGNORE_CASE
    )

    private val recorderPattern = Regex(
        "grabadora|graba\\s+(?:audio|sonido|voz)|dictáfono|dictafono|диктофон|record\\s+(?:audio|sound|voice)|voice\\s+record",
        RegexOption.IGNORE_CASE
    )

    // Music. "трек"/"песн" are here as well as "музык" so "следующий трек"
    // works without the user having to say the word music at all.
    private val musicNounPattern = Regex(
        "música|musica|canción|cancion|pista|reproductor|lista de reproducción|lista de reproduccion|музык|трек|песн|плеер|music|track|song|плейлист", RegexOption.IGNORE_CASE
    )
    private val trackNextPattern = Regex(
        "siguiente|próxima|proxima|adelante|salta|следующ|дальше|переключ|next|skip", RegexOption.IGNORE_CASE
    )
    private val trackPrevPattern = Regex(
        "anterior|previa|atrás|atras|назад|предыдущ|previous|prev|back", RegexOption.IGNORE_CASE
    )
    private val pausePattern = Regex(
        "pausa|paus|detén|deten|pause", RegexOption.IGNORE_CASE
    )
    private val stopPattern = Regex(
        "apaga|detén|deten|para|detener|desactiva|silencia|выключи|выруби|останов|прекрат|хватит|стоп|убери|заглуши|stop|turn off", RegexOption.IGNORE_CASE
    )

    /** "… в <something>": possibly a player's name. */
    private val inPlayer = Regex("\\s(?:en el|en la|en|de|в|во|на|in|on)\\s+\\p{L}{2,}", RegexOption.IGNORE_CASE)

    /** A pause said on its own needs no word for music: "пауза", "поставь на паузу". */
    private val barePause = Regex(
        "^(?:pon\\s+en\\s+pausa|pausa|detén\\s+la\\s+música|deten\\s+la\\s+musica|поставь\\s+на\\s+паузу|на\\s+паузу|пауза|сделай\\s+паузу|pause)[.!]*$", RegexOption.IGNORE_CASE
    )

    private val nowPlayingPattern = Regex(
        "(?:qué|que|cuál|cual|qué canción|que cancion)\\s+(?:suena|está sonando|esta sonando)|(?:что|какая\\s+(?:песня|музыка)|какой\\s+трек)\\s+(?:сейчас\\s+)?(?:играет|звучит)|" +
            "what'?s\\s+(?:this\\s+song|playing)|what\\s+song\\s+is\\s+(?:this|playing)",
        RegexOption.IGNORE_CASE
    )

    private val timePattern = Regex(
        "^(?:qué\\s+hora\\s+es|que\\s+hora\\s+es|dime\\s+la\\s+hora|qué\\s+hora\\s+tenemos|que\\s+hora\\s+tenemos|который\\s+(?:сейчас\\s+)?час|сколько\\s+(?:сейчас\\s+)?(?:время|времени)|какое\\s+(?:сейчас\\s+)?время|" +
            "what\\s+time\\s+is\\s+it|what'?s\\s+the\\s+time)[?.!]*$",
        RegexOption.IGNORE_CASE
    )
    private val playPattern = Regex(
        "pon|reproduce|reproducir|escucha|suena|inicia|reanuda|включ|поставь|запусти|играй|продолж|play|resume|put\\s+on", RegexOption.IGNORE_CASE
    )

    /** A verb that means "take me there", not "do it for me". */
    private val settingsVerbPattern = Regex(
        "abre|abrir|muestra|muéstrame|muestrame|entra en|ve a|ajustes|configuración|configuracion|configura|открой|откройте|покажи|зайди|перейди|включи|выключи|настройк\\p{L}*|настрой|" +
            "open|show|go\\s+to|settings",
        RegexOption.IGNORE_CASE
    )

    private val searchPatterns = listOf(
        Regex("^(?:busca|búscame|buscame|busca en internet|investiga|googlea|загугли|найди в интернете|поищи|google|search|search for|look up)\\s+(.+)$", RegexOption.IGNORE_CASE)
    )

    /**
     * Calendar entries. Matched before alarms because "напомни ... в 15:00"
     * and "поставь будильник на 15:00" look alike, but a reminder with a
     * subject belongs in the calendar, not the clock.
     */
    /**
     * Moving something already in the calendar. Checked before [calendarPatterns]
     * so "перенеси встречу на 11" doesn't create a second event.
     * Group 1 is the optional name, group 2 is the new time.
     */
    private val reschedulePatterns = listOf(
        Regex("^(?:mueve|mover|reprograma|cambia la hora de|перенеси|перенести|подвинь|сдвинь)\\s+(?:(.+?)\\s+)?(?:a|para|на|в)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:move|reschedule|shift)\\s+(?:(?:the\\s+)?(.+?)\\s+)?(?:to|at)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:cambia la hora|modifica la hora|измени время|поменяй время)\\s+(?:(.+?)\\s+)?(?:a|para|на|в)\\s+(.+)$", RegexOption.IGNORE_CASE)
    )

    private val calendarPatterns = listOf(
        Regex("^(?:recuérdame|recuerdame|recuérdame que|recuerdame que|pon un recordatorio|añade un recordatorio|anade un recordatorio|напомни(?:ть)?(?:\\s+мне)?|поставь напоминание|добавь напоминание)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:añade|anade|crea|apunta|pon)\\s+(?:al calendario|en el calendario|un evento|una cita|una reunión|una reunion|un recordatorio|в календарь|событие|встречу|напоминание)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:remind me(?:\\s+to)?|add (?:an? )?(?:event|reminder|meeting))\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:schedule|create (?:an? )?(?:event|meeting))\\s+(.+)$", RegexOption.IGNORE_CASE)
    )

    // NB: \w is ASCII-only in Java regex and never matches Cyrillic, so
    // "погода" would not match `погод\w*`. \p{L} is the Unicode-aware form.
    private val weatherPatterns = listOf(
        Regex("(?:какая|какая сейчас|что с)\\s+погод\\p{L}*(?:\\s+(?:в|во)\\s+(.+?))?[?.!]*$", RegexOption.IGNORE_CASE),
        Regex("^погод\\p{L}*(?:\\s+(?:в|во)\\s+(.+?))?[?.!]*$", RegexOption.IGNORE_CASE),
        Regex("(?:what'?s|hows|how is)\\s+the\\s+weather(?:\\s+in\\s+(.+?))?[?.!]*$", RegexOption.IGNORE_CASE),
        Regex("^weather(?:\\s+in\\s+(.+?))?[?.!]*$", RegexOption.IGNORE_CASE)
    )

    private val missedPatterns = listOf(
        Regex("^(?:что я пропустил\\p{L}*|что нового|какие уведомления|что там)[?.!]*$", RegexOption.IGNORE_CASE),
        Regex("^(?:what did i miss|what'?s new|any notifications)[?.!]*$", RegexOption.IGNORE_CASE)
    )

    private val briefPatterns = listOf(
        Regex("^(?:resumen|resumen de la mañana|resumen de la manana|informe matutino|buenos días|buenos dias|сводка|утренняя сводка|briefing|brief|доброе утро|good morning)[?.!]*$", RegexOption.IGNORE_CASE),
        Regex("^(?:qué hay hoy|que hay hoy|cuéntame el día|cuentame el dia|qué tengo hoy|que tengo hoy|что сегодня|расскажи про день|what'?s today)[?.!]*$", RegexOption.IGNORE_CASE)
    )

    private val notePatterns = listOf(
        Regex("^(?:anota|apunta|guarda una nota|recuerda que|guarda nota|запиши|заметка|запомни заметку|сохрани заметку)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:note(?: that| down)?|save a note)\\s+(.+)$", RegexOption.IGNORE_CASE)
    )

    private val volumeSetPattern =
        Regex("(?:volumen|sonido|громкость|звук|volume)\\s*(?:a|en|на|to)?\\s*(\\d{1,3})\\s*(?:%|por ciento|процент\\p{L}*)?", RegexOption.IGNORE_CASE)

    private val volumeMaxPattern =
        Regex("(?:volumen|sonido|громкость|звук|volume)\\s+(?:a\\s+|en\\s+|на\\s+|to\\s+)?(?:máximo|maximo|al máximo|al maximo|máxima|maxima|максимум|максимальн\\p{L}*|max(?:imum)?|полную)", RegexOption.IGNORE_CASE)
    private val volumeMinPattern =
        Regex("(?:volumen|sonido|громкость|звук|volume)\\s+(?:a\\s+|en\\s+|на\\s+|to\\s+)?(?:mínimo|minimo|al mínimo|al minimo|mínima|minima|минимум|минимальн\\p{L}*|min(?:imum)?)", RegexOption.IGNORE_CASE)

    private val findNearbyPatterns = listOf(
        Regex("(?:busca|encuentra|dónde está|donde esta)\\s+(?:el|la|un|una)?\\s*(?:más cercano|mas cercano|más cercana|mas cercana|ближайш\\p{L}+)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("(?:find|show)\\s+(?:the\\s+)?nearest\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("where(?:'?s| is)\\s+the\\s+nearest\\s+(.+)$", RegexOption.IGNORE_CASE)
    )

    fun route(said: String): CommandResult {
        val input = Filler.strip(said)
        val whole = routeOne(input)
        // The screen and files need the app; they are never handed to the model.
        if (whole is CommandResult.AnalyzeScreen || whole is CommandResult.AnalyzeFile) return whole
        // "Поставь будильник на 7 и включи фонарик": the patterns would carry
        // out the first half and drop the rest.
        val routed = when (val split = CompoundRequest.split(input, ::routeOne)) {
            is CompoundRequest.Split.Steps -> CommandResult.Sequence(split.steps)
            // "Какой уровень осадков и когда он закончится?" is one weather question, not two requests.
            CompoundRequest.Split.Mixed ->
                if (whole is CommandResult.Weather) whole else CommandResult.ChatMessage(input.trim())
            CompoundRequest.Split.Single -> whole
        }
        // Talk goes to the model as it was said, lead-in and all.
        return if (routed is CommandResult.ChatMessage) CommandResult.ChatMessage(said.trim()) else routed
    }

    private fun routeOne(input: String): CommandResult {
        val trimmed = input.trim()

        for (p in analyzeScreenPatterns) {
            if (p.containsMatchIn(trimmed)) return CommandResult.AnalyzeScreen
        }

        for (p in analyzeFilePatterns) {
            val m = p.find(trimmed) ?: continue
            return CommandResult.AnalyzeFile(m.groupValues.getOrNull(1)?.trim()?.ifBlank { null })
        }

        // On and off are what was asked, not a flip of what Friday thinks it is.
        if (flashlightOff.containsMatchIn(trimmed)) return CommandResult.Flashlight(on = false)
        if (flashlightOn.containsMatchIn(trimmed)) return CommandResult.Flashlight(on = true)
        for (p in flashlightPatterns) {
            if (p.containsMatchIn(trimmed)) return CommandResult.ToggleFlashlight
        }

        // Before notes: "запиши видео" and "запиши голос" both start with the
        // verb the note pattern claims, and were being filed as notes.
        routeCamera(trimmed)?.let { return it }
        // A search ("включи Believer в спотифае") before transport controls,
        // which would read it as a bare "play".
        MediaRequest.parse(trimmed)?.let { return it }
        routeMedia(trimmed)?.let { return it }
        // Chats before mail: "ответь маме" means her WhatsApp more often than
        // her e-mail, and the reply falls back to mail when there is no chat.
        ChatRequest.parse(trimmed)?.let { return it }
        MailCommands.parse(trimmed)?.let { mail ->
            return if (mail is MailCommands.Request.Reply) {
                CommandResult.ReplyMessage(mail.to, mail.body, ChatRequest.looseReply(trimmed))
            } else {
                CommandResult.Mail(mail)
            }
        }
        MessageRequest.parse(trimmed)?.let { return it }
        ChatRequest.parseBareReply(trimmed)?.let { return it }

        if (timePattern.matches(trimmed)) return CommandResult.TellTime

        for (p in missedPatterns) {
            if (p.containsMatchIn(trimmed)) return CommandResult.WhatDidIMiss
        }

        for (p in briefPatterns) {
            if (p.containsMatchIn(trimmed)) return CommandResult.MorningBrief
        }

        for (p in notePatterns) {
            val m = p.find(trimmed) ?: continue
            val text = m.groupValues[1].trim()
            if (text.isNotBlank()) return CommandResult.CreateNote(text)
        }

        routeDeviceControl(trimmed)?.let { return it }
        // After device control so "включи вайфай" still reaches the panel
        // action, and after camera so "открой настройки камеры" is unambiguous.
        routeSettings(trimmed)?.let { return it }

        // Day words are pulled out first rather than woven into the pattern:
        // "какая погода завтра" and "какая погода завтра в Москве" put them in
        // different places, and one anchored regex can't cover both cleanly.
        val dayOffset = dayOffsetOf(trimmed)
        WeatherRequest.parse(trimmed, dayOffset)?.let { return it }
        val weatherText = if (dayOffset == 0) trimmed else stripDayWords(trimmed)
        for (p in weatherPatterns) {
            val m = p.find(weatherText) ?: continue
            val place = m.groupValues.getOrNull(1)?.trim()?.ifBlank { null }
            return CommandResult.Weather(place, dayOffset)
        }

        for (p in reschedulePatterns) {
            val m = p.find(trimmed) ?: continue
            val whenText = m.groupValues[2].trim()
            if (whenText.isBlank() || DateTimeParser.parse(whenText) == null) continue
            val hint = m.groupValues[1].trim()
                .replace(
                    Regex("^(?:встречу|событие|напоминание|meeting|event|reminder)\\s*", RegexOption.IGNORE_CASE),
                    ""
                )
                .trim()
                .ifBlank { null }
            return CommandResult.RescheduleEvent(hint, whenText)
        }

        // Only treat it as a calendar entry when a time can actually be read
        // out of it — otherwise "напомни купить молоко" would create an event
        // at an arbitrary hour instead of being answered conversationally.
        for (p in calendarPatterns) {
            val m = p.find(trimmed) ?: continue
            val rest = m.groupValues[1].trim()
            if (rest.isBlank()) continue
            if (DateTimeParser.parse(rest) == null) {
                // No time, but if it's an errand it belongs to a place
                // instead: "напомни купить молоко" is about where you are.
                if (ErrandPlaces.isLocationWorthy(rest)) {
                    return CommandResult.CreateErrand(cleanErrand(rest))
                }
                continue
            }
            return CommandResult.CreateEvent(title = stripTimeWords(rest), whenText = rest)
        }

        AlarmRequest.parse(trimmed, now())?.let { a ->
            return CommandResult.SetAlarm(a.time.hour, a.time.minute, label = null, date = a.date)
        }

        for (p in timerPatterns) {
            val m = p.find(trimmed) ?: continue
            val value = m.groupValues[1].toIntOrNull() ?: continue
            val isMinutes = m.value.lowercase().let {
                it.contains("мин") || it.contains("min")
            }
            val seconds = if (isMinutes) value * 60 else value
            return CommandResult.SetTimer(seconds, null)
        }

        CallRequest.parse(trimmed)?.let { return it }

        for (p in findNearbyPatterns) {
            val m = p.find(trimmed) ?: continue
            return CommandResult.FindNearby(m.groupValues[1].trim())
        }

        for (p in searchPatterns) {
            val m = p.find(trimmed) ?: continue
            // Answered aloud, Jarvis-style, rather than left in a browser tab.
            return CommandResult.LookUp(m.groupValues[1].trim())
        }

        for (p in openPatterns) {
            val m = p.find(trimmed) ?: continue
            val appName = m.groupValues[1].trim().lowercase()
            return CommandResult.OpenApp(appName, com.friday.ai.core.apps.AppMatcher.aliasFor(appName))
        }

        for (p in closePatterns) {
            val m = p.find(trimmed) ?: continue
            val appName = m.groupValues[1].trim().lowercase()
            return CommandResult.CloseApp(appName, com.friday.ai.core.apps.AppMatcher.aliasFor(appName))
        }

        return CommandResult.ChatMessage(trimmed)
    }

    /** Trims the verb so the errand reads as the thing itself: "молоко". */
    private fun cleanErrand(text: String): String = text
        .replace(
            Regex("^(?:мне\\s+)?(?:купить|забрать|зайти за|взять|buy|get|pick up)\\s+",
                RegexOption.IGNORE_CASE),
            ""
        )
        .trim()
        .ifBlank { text.trim() }

    private fun stripDayWords(text: String): String = text
        .replace(
            Regex("(?:на\\s+)?(?:сегодня|завтра|послезавтра|today|tomorrow|day after tomorrow)",
                RegexOption.IGNORE_CASE),
            " "
        )
        .replace(Regex("\\s+"), " ")
        .trim()


    /**
     * Volume / Do Not Disturb / connectivity. Returns null when the text isn't
     * about device settings at all.
     */
    /**
     * Camera and voice recorder.
     *
     * Checked before the generic "открой <app>" handler, which would otherwise
     * swallow "открой камеру" and lose the mode the user asked for.
     */
    private fun routeCamera(text: String): CommandResult? {
        val lower = text.lowercase().replace('ё', 'е')

        if (recorderPattern.containsMatchIn(lower)) return CommandResult.RecordAudio
        if (!cameraWordPattern.containsMatchIn(lower) &&
            !bareCapturePattern.containsMatchIn(lower)
        ) return null

        return when {
            videoPattern.containsMatchIn(lower) -> CommandResult.OpenCamera(CameraMode.VIDEO)
            selfiePattern.containsMatchIn(lower) -> CommandResult.OpenCamera(CameraMode.SELFIE)
            photoPattern.containsMatchIn(lower) -> CommandResult.OpenCamera(CameraMode.PHOTO)
            else -> CommandResult.OpenCamera(CameraMode.JUST_OPEN)
        }
    }

    /**
     * Music transport.
     *
     * A named player counts on its own — "включи спотифай" needs no word for
     * music — but a bare verb must not, or "включи свет" would reach for the
     * media session.
     */
    private fun routeMedia(text: String): CommandResult? {
        val lower = text.lowercase().replace('ё', 'е')
        val named = MusicApps.match(lower)
        val aboutMusic = musicNounPattern.containsMatchIn(lower) || named != null
        return when {
            nowPlayingPattern.containsMatchIn(lower) -> CommandResult.NowPlaying
            barePause.matches(lower) -> CommandResult.MediaControl(MediaAction.PAUSE, null)
            !aboutMusic -> null
            // "в VK Музыке": a player the patterns don't know may still be installed,
            // so the phrase goes along for the player to be found by its label.
            else -> mediaAction(lower)?.let {
                CommandResult.MediaControl(it, if (named != null || inPlayer.containsMatchIn(lower)) lower else null)
            }
        }
    }

    private fun mediaAction(lower: String): MediaAction? = when {
        trackNextPattern.containsMatchIn(lower) -> MediaAction.NEXT
        trackPrevPattern.containsMatchIn(lower) -> MediaAction.PREVIOUS
        pausePattern.containsMatchIn(lower) -> MediaAction.PAUSE
        stopPattern.containsMatchIn(lower) -> MediaAction.STOP
        playPattern.containsMatchIn(lower) -> MediaAction.PLAY
        else -> null
    }

    private fun routeSettings(text: String): CommandResult? {
        val lower = text.lowercase().replace('ё', 'е')
        if (!settingsVerbPattern.containsMatchIn(lower)) return null
        // Only a phrase that names a screen counts; "открой инстаграм" must
        // still go to the app launcher.
        return if (SettingsScreens.match(lower) != null) CommandResult.OpenSettings(lower) else null
    }

    private fun routeDeviceControl(text: String): CommandResult? {
        val lower = text.lowercase().replace('ё', 'е')
        (DevicePhrases.ringer(lower) ?: DevicePhrases.brightness(lower))?.let { return it }

        volumeSetPattern.find(lower)?.let { m ->
            m.groupValues[1].toIntOrNull()?.let { level ->
                return CommandResult.DeviceControl(DeviceAction.VOLUME_SET, level.coerceIn(0, 100))
            }
        }

        val mentionsVolume = lower.contains("volumen") || lower.contains("громкост") ||
            lower.contains("volume") || lower.contains("sonido") || lower.contains("звук")

        return when {
            volumeMaxPattern.containsMatchIn(lower) -> CommandResult.DeviceControl(DeviceAction.VOLUME_SET, 100)
            volumeMinPattern.containsMatchIn(lower) -> CommandResult.DeviceControl(DeviceAction.VOLUME_SET, MIN_VOLUME)

            // "(?<!un)mute": "unmute" contains "mute" and used to mute.
            lower.matches(Regex(".*(?:apaga el sonido|quita el sonido|silencia|sin sonido|выключи звук|без звука|беззвучн\\p{L}*|(?<!un)mute|тише некуда).*")) ->
                CommandResult.DeviceControl(DeviceAction.MUTE)

            lower.matches(Regex(".*(?:enciende el sonido|activa el sonido|quita el silencio|включи звук|unmute|верни звук).*")) ->
                CommandResult.DeviceControl(DeviceAction.UNMUTE)

            mentionsVolume && (lower.contains("sube") || lower.contains("aumenta") ||
                lower.contains("incrementa") || lower.contains("gira") ||
                lower.contains("громче") || lower.contains("прибав") ||
                lower.contains("up") || lower.contains("увеличь")) ->
                CommandResult.DeviceControl(DeviceAction.VOLUME_UP)

            mentionsVolume && (lower.contains("baja") || lower.contains("reduce") ||
                lower.contains("disminuye") || lower.contains("тише") || lower.contains("убав") ||
                lower.contains("down") || lower.contains("уменьш")) ->
                CommandResult.DeviceControl(DeviceAction.VOLUME_DOWN)

            lower.contains("громче") -> CommandResult.DeviceControl(DeviceAction.VOLUME_UP)
            lower.contains("тише") -> CommandResult.DeviceControl(DeviceAction.VOLUME_DOWN)

            lower.matches(Regex(".*(?:no molestar|modo no molestar|не беспокоить|режим тишины|do not disturb|dnd).*")) ->
                if (lower.contains("desactiva") || lower.contains("apaga") || lower.contains("выключ") || lower.contains("off") || lower.contains("отключ")) {
                    CommandResult.DeviceControl(DeviceAction.DND_OFF)
                } else {
                    CommandResult.DeviceControl(DeviceAction.DND_ON)
                }

            lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("вай-фай") ||
                lower.contains("вайфай") ->
                CommandResult.DeviceControl(
                    DevicePhrases.onOff(lower, DeviceAction.WIFI_ON, DeviceAction.WIFI_OFF, DeviceAction.OPEN_WIFI_PANEL)
                )

            lower.contains("bluetooth") || lower.contains("блютус") || lower.contains("блютуз") ->
                CommandResult.DeviceControl(
                    DevicePhrases.onOff(lower, DeviceAction.BLUETOOTH_ON, DeviceAction.BLUETOOTH_OFF, DeviceAction.OPEN_BLUETOOTH_PANEL)
                )

            else -> null
        }
    }

    /**
     * Strips the scheduling words so the event title reads naturally:
     * "завтра в 15:00 встреча с врачом" becomes "встреча с врачом".
     */
    private fun stripTimeWords(text: String): String {
        val cleaned = text
            .replace(
                Regex(
                    """(?:сегодня|завтра|послезавтра|today|tomorrow|day after tomorrow)""",
                    RegexOption.IGNORE_CASE
                ), " "
            )
            .replace(
                Regex(
                    """(?:в|во|at|к|on)\s*\d{1,2}(?:[:.]\d{2})?\s*(?:утра|дня|вечера|ночи|am|pm)?""",
                    RegexOption.IGNORE_CASE
                ), " "
            )
            .replace(
                Regex(
                    """(?:через|in)\s+\d+\s*(?:минут\p{L}*|мин|час\p{L}*|дн\p{L}*|день|недел\p{L}*|minutes?|mins?|hours?|days?|weeks?)""",
                    RegexOption.IGNORE_CASE
                ), " "
            )
            .replace(
                Regex(
                    """(?:понедельник|вторник|сред\p{L}|четверг|пятниц\p{L}|суббот\p{L}|воскресень\p{L}|monday|tuesday|wednesday|thursday|friday|saturday|sunday)""",
                    RegexOption.IGNORE_CASE
                ), " "
            )
            .replace(Regex("""^\s*(?:о|про|about|that)\s+""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', '-', '—')

        return cleaned.ifBlank { text.trim() }
    }
}

/** Which day a weather question is about. */
private fun dayOffsetOf(text: String): Int {
    val lower = text.lowercase()
    return when {
        lower.contains("послезавтра") || lower.contains("day after tomorrow") -> 2
        lower.contains("завтра") || lower.contains("tomorrow") -> 1
        else -> 0
    }
}

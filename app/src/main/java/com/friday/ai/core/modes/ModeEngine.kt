// Long lines on purpose: each reply is a Russian/English pair kept side by side.
@file:Suppress("MaxLineLength")

package com.friday.ai.core.modes

import com.friday.ai.agent.ActionEnvelope
import com.friday.ai.agent.AgentTools
import com.friday.ai.agent.LearnedCommands
import com.friday.ai.command.CommandExecutor
import com.friday.ai.core.DeviceController
import com.friday.ai.domain.model.CommandResult
import java.time.LocalDateTime

/**
 * Modes, end to end: "создай режим грусти: …" → compiled, saved and read
 * back; "режим грусти" → its steps run, with no modol call; "выключи режим
 * грусти" → what the steps changed is put back.
 *
 * Device settings report their own undo ([DeviceController]); music a modo
 * started is paused and a flashlight it lit is turned off ([ModeSteps.undoOf]).
 * Things that can't be taken back — a message sent, an alarm set — stay.
 * Every reply is assembled from what the steps actually reported, so a
 * missing permission is said, not hidden.
 */
// One handler per kind of request; the constructor takes its collaborators and the two clocks tests replace.
@Suppress("TooManyFunctions", "LongParameterList")
class ModeEngine(
    private val store: ModeStore,
    /** [ModeCompiler.compile]; a function so the engine is tested without Groq. */
    private val compile: suspend (name: String, description: String) -> ModeCompiler.Result,
    private val device: DeviceController,
    private val clock: () -> Long = System::currentTimeMillis,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    /** Schedules and run history; without them, modos simply have no timetable. */
    private val schedules: ModeSchedules? = null,
    /** The phone's Bluetooth and Wi-Fi, to tell which device "машина" is. */
    private val links: PhoneLinks? = null,
    /** NFC tags for modos; null where the phone has none. */
    private val nfc: NfcTags? = null
) {

    private companion object {
        /** "Отмена" this soon after creating a modo removes it. */
        const val UNDO_WINDOW_MS = 2 * 60 * 1000L
        const val MAX_NAME_WORDS = 4

        /**
         * Steps that act for the owner towards other people. A modo that has
         * one never starts on a timer, with nobody there to stop it.
         */
        val NEEDS_OWNER = setOf("call", "send_message", "reply_message", "mail")

        /** At least half of "убери …"'s words must be in a step for it to be that step. */
        const val MIN_STEP_MATCH = 0.5
    }

    @Volatile
    private var lastCreated: Pair<String, Long>? = null

    /** The modo that just ran, for "нет, включи другое". */
    @Volatile
    private var lastRun: Pair<String, Long>? = null

    /** A correction waiting for "да": put [step] into the modo, in place of [replaces] if set. */
    private data class Proposal(val modoId: String, val step: ActionEnvelope, val replaces: Int?, val at: Long)

    @Volatile
    private var proposal: Proposal? = null

    /** "Включать каждый день в 23:00?" after a habit was noticed, waiting for "да". */
    private data class HabitOffer(val modoId: String, val time: java.time.LocalTime, val at: Long)

    @Volatile
    private var habit: HabitOffer? = null

    private val correctionLead = Regex(
        "^(?:нет|не то|не так|неправильно|не это|я не это|no|not that|wrong)[,.!\\s]+(?:а\\s+|лучше\\s+|instead\\s+)?"
    )

    /**
     * The modo request in [text], or null. Run, exit and describe only count
     * when the name is one of the owner's modos: "включи режим полёта" is a
     * system setting, not a modo Friday doesn't have.
     */
    fun route(text: String): CommandResult.Mode? =
        correction(text) ?: tagRequest(text) ?: eventRequest(text) ?: scheduleRequest(text) ?: cancelRequest(text) ?: request(text)

    private val tagPhrases = listOf(
        Regex("(?:привяжи|запиши|сохрани|поставь)\\s+режим\\p{L}*\\s+(.+?)\\s+(?:к|на|в)\\s+(?:nfc[- ]?)?метк\\p{L}*"),
        Regex("(?:сделай|создай|запиши)\\s+(?:nfc[- ]?)?метку\\s+(?:для|на)\\s+режим\\p{L}*\\s+(.+)$"),
        Regex("(?:link|write|put)\\s+(?:the\\s+)?(.+?)\\s+mode\\s+(?:to|on)\\s+(?:an?\\s+)?(?:nfc\\s+)?tag")
    )

    private fun tagRequest(text: String): CommandResult.Mode? {
        val t = text.trim().lowercase().replace('ё', 'е').trimEnd('.', '!', '?')
        val rest = tagPhrases.firstNotNullOfOrNull { it.find(t)?.groupValues?.get(1) } ?: return null
        return nameAtStart(rest)?.let { CommandResult.Mode.Tag(it.id) }
    }

    private fun eventRequest(text: String): CommandResult.Mode? {
        if (schedules == null || links == null) return null
        val r = EventPhrases.parse(text) ?: return null
        return nameAtStart(r.rest)?.let { CommandResult.Mode.OnEvent(it.id, r.trigger.key, r.target, r.onConnect, r.exit) }
    }

    /** "Отмена" / "нет, не так" just after creating a modo. */
    private fun cancelRequest(text: String): CommandResult.Mode? {
        val (id, at) = lastCreated ?: return null
        if (clock() - at > UNDO_WINDOW_MS || !LearnedCommands.isCorrection(text)) return null
        lastCreated = null
        return CommandResult.Mode.CancelCreated(id)
    }

    private fun request(text: String): CommandResult.Mode? = when (val request = ModePhrases.parse(text)) {
        is CommandResult.Mode.Run -> request.takeIf { store.find(it.name) != null }
        is CommandResult.Mode.Exit -> request.takeIf { store.find(it.name) != null }
        is CommandResult.Mode.Describe -> request.takeIf { store.find(it.name) != null }
        is CommandResult.Mode.AddTo -> request.takeIf { split(it.rest) != null }
        is CommandResult.Mode.RemoveFrom -> request.takeIf { store.find(it.name) != null }
        else -> request
    }

    private fun scheduleRequest(text: String): CommandResult.Mode? {
        if (schedules == null) return null
        return when (val r = SchedulePhrases.parse(text, now())) {
            null -> null
            is SchedulePhrases.Request.Set -> nameAtStart(r.rest)?.let {
                CommandResult.Mode.Schedule(it.id, r.exit, r.time?.hour, r.time?.minute, Days.mask(r.days))
            }
            is SchedulePhrases.Request.Clear -> nameAtStart(r.rest)?.let { CommandResult.Mode.Unschedule(it.id) }
        }
    }

    /** The saved modo whose whole name the words begin with, longest first. */
    private fun nameAtStart(rest: String): Mode? {
        val words = rest.trim().split(' ').filter { it.isNotBlank() }
        return (minOf(MAX_NAME_WORDS, words.size) downTo 1).firstNotNullOfOrNull { k ->
            store.find(words.take(k).joinToString(" "), spare = 0)
        }
    }

    /** "Нет, включи lo-fi" within the window after a run: the part after "нет" is the wish. */
    private fun correction(text: String): CommandResult.Mode.Correct? {
        val (id, at) = lastRun ?: return null
        if (clock() - at > UNDO_WINDOW_MS) return null
        val lower = text.trim().lowercase().replace('ё', 'е')
        val lead = correctionLead.find(lower) ?: return null
        val instead = text.trim().substring(lead.range.last + 1).trim()
        return CommandResult.Mode.Correct(id, instead).takeIf { instead.split(' ').size >= 2 }
    }

    /** "Да" / "нет" to "запомнить это для режима …?"; null when nothing is asked or it isn't an answer. */
    suspend fun answerPending(text: String, russian: Boolean): String? =
        answerHabit(text, russian) ?: answerProposal(text, russian)

    private suspend fun answerProposal(text: String, russian: Boolean): String? {
        val p = proposal?.takeIf { clock() - it.at <= UNDO_WINDOW_MS } ?: return null
        val say = Say(russian)
        return when (com.friday.ai.core.mail.Confirmation.classify(text)) {
            com.friday.ai.core.mail.Confirmation.Answer.OTHER -> null
            com.friday.ai.core.mail.Confirmation.Answer.NO -> {
                proposal = null
                say("Хорошо, режим остаётся как был.", "De acuerdo, el modo se queda como estaba.")
            }
            com.friday.ai.core.mail.Confirmation.Answer.YES -> {
                proposal = null
                val modo = store.byId(p.modeId) ?: return say("Этого режима уже нет.", "Ese modo ya no existe.")
                val steps = modo.steps.toMutableList()
                if (p.replaces != null && p.replaces in steps.indices) steps[p.replaces] = p.step else steps += p.step
                store.put(mode.copy(steps = steps))
                say(
                    "Запомнила. Режим ${mode.name}: ${ModeSteps.summary(steps, russian)}.",
                    "Entendido. ${mode.name}:${ModeSteps.summary(steps, russian)}."
                )
            }
        }
    }

    /** Carries out [request]; [runner] runs one ordinary command, [router] reads a phrase into one. */
    suspend fun handle(
        request: CommandResult.Mode,
        russian: Boolean,
        router: (String) -> CommandResult = { CommandResult.ChatMessage(it) },
        runner: suspend (CommandResult) -> String
    ): String {
        val say = Say(russian)
        return when (request) {
            is CommandResult.Mode.Create -> create(request.name, request.description, say)
            is CommandResult.Mode.Run -> store.find(request.name)?.let { run(it, say, runner) } ?: missing(request.name, say)
            is CommandResult.Mode.Exit -> store.find(request.name)?.let { exit(it, say, runner) } ?: missing(request.name, say)
            is CommandResult.Mode.Describe -> store.find(request.name)?.let { describe(it, say) } ?: missing(request.name, say)
            is CommandResult.Mode.Delete -> store.find(request.name)?.let { delete(it, say) } ?: missing(request.name, say)
            is CommandResult.Mode.CancelCreated -> cancel(request.id, say)
            is CommandResult.Mode.Correct -> correct(request, say, router, runner)
            is CommandResult.Mode.AddTo -> add(request.rest, say)
            is CommandResult.Mode.RemoveFrom -> store.find(request.name)?.let { remove(it, request.what, say) }
                ?: missing(request.name, say)
            is CommandResult.Mode.Schedule -> schedule(request, say)
            is CommandResult.Mode.Unschedule -> unschedule(request.id, say)
            is CommandResult.Mode.OnEvent -> onEvent(request, say)
            is CommandResult.Mode.Tag -> tag(request.id, say)
            CommandResult.Mode.ListAll -> list(say)
        }
    }

    private suspend fun create(name: String, description: String?, say: Say): String {
        if (description == null) {
            return say(
                "Что должно происходить в режиме $name? Скажите целиком, например: " +
                    "«создай режим $name: включи грустную музыку в Spotify и «Не беспокоить»».",
                "What should $name modo do? Say it in one go, e.g. \"create $name:play calm music and Do Not Disturb\"."
            )
        }
        return when (val compiled = compile(name, description)) {
            is ModeCompiler.Result.Failed -> if (compiled.reason == "no_key") {
                say("Чтобы разобрать описание режима, нужен ключ Groq в настройках.", "Necesito la clave de Groq en los ajustes para entender la descripción del modo.")
            } else {
                say(
                    "Не поняла, что делать в режиме $name. Опишите конкретнее: что включить, что выключить.",
                    "No he podido determinar qué debe hacer $name en el modo. Describe con más detalle qué hay que activar o desactivar."
                )
            }
            is ModeCompiler.Result.Steps -> {
                val (mode, replaced) = store.save(name, description, compiled.steps)
                lastCreated = modo.id to clock()
                val what = ModeSteps.summary(mode.steps, say.russian)
                val skipped = if (compiled.skipped.isEmpty()) "" else say(
                    " Часть описания сделать не умею — её пропустила.",
                    " Hay una parte que no puedo realizar y la he omitido."
                )
                val head = if (replaced) say("Обновила режим $name", "He actualizado $name modo") else say("Режим $name", "$name modo")
                say(
                    "$head: $what.$skipped Скажите «режим $name», чтобы включить, или «отмена», если я поняла не так.",
                    "$head: $what.$skipped Say \"$name modo\" to start it, or \"cancel\" if I got it wrong."
                )
            }
        }
    }

    private suspend fun run(
       :Mode,
        say: Say,
        runner: suspend (CommandResult) -> String,
        automatic: Boolean = false,
        steps: List<ActionEnvelope> = modo.steps
    ): String {
        val (messages, undo) = carryOut(steps, say, runner)
        schedules?.logRun(mode.id, clock(), automatic)
        lastRun = modo.id to clock()
        // Already on: the phone's state from before the first run is what "выключи" must restore.
        store.put(
            modo.copy(undo = modo.undo ?: undo, lastRunAt = clock(), runCount = modo.runCount + 1)
        )
        val reply = say("Режим ${mode.name}. ", "${mode.name} modo. ") + sentences(messages)
        return if (automatic) reply else reply + habitQuestion(mode, say)
    }

    // --- NFC tags ---------------------------------------------------------------

    private fun tag(id: String, say: Say): String {
        val modo = store.byId(id) ?: return say("Этого режима уже нет.", "Ese modo ya no existe.")
        val tags = nfc?.takeIf { it.available() }
            ?: return say("NFC выключен или его нет — включите NFC в настройках и скажите ещё раз.", "El NFC está desactivado o no está disponible. Actívalo en los ajustes y vuelve a intentarlo.")
        tags.startWriting(mode.id, modo.name)
        return say(
            "Поднесите NFC-метку к задней панели телефона. Потом касание метки будет включать и выключать режим ${mode.name}.",
            "Acerca una etiqueta NFC a la parte trasera del teléfono. Después, al tocarla, activarás y desactivarás el modo ${mode.name} modo on and off."
        )
    }

    /**
     * A tag or a launcher shortcut for [modeId]: the modo is toggled. Like a
     * schedule, a tag can be copied, so steps that act towards people are
     * left out.
     */
    suspend fun toggle(modeId: String, russian: Boolean, runner: suspend (CommandResult) -> String): String {
        val say = Say(russian)
        val modo = store.byId(modeId) ?: return say(
            "Эта метка была для режима, которого больше нет.", "This tag was for a modo that no longer exists."
        )
        return if (mode.active) exit(mode, say, runner)
        else run(mode, say, runner, automatic = true, steps = modo.steps.filterNot { it.tool in NEEDS_OWNER })
    }

    // --- events --------------------------------------------------------------

    private suspend fun onEvent(request: CommandResult.Mode.OnEvent, say: Say): String {
        val modo = store.byId(request.id) ?: return say("Этого режима уже нет.", "Ese modo ya no existe.")
        val timetable = schedules ?: return say("Недоступно.", "No está disponible.")
        val trigger = Trigger.of(request.trigger) ?: return say("Не поняла, при чём включать.", "No he entendido qué debe activar el modo.")
        if (!request.exit && modo.steps.any { it.tool in NEEDS_OWNER }) {
            return say(
                "В режиме ${mode.name} есть звонок или сообщение — сам по себе, без вас, такой режим я не запускаю.",
                "${mode.name} modo incluye una llamada o un mensaje; no lo iniciaré automáticamente sin que estés presente."
            )
        }
        val (value, note) = when (trigger) {
            Trigger.CHARGER -> "" to ""
            Trigger.BLUETOOTH -> bluetoothDevice(request.target, say) ?: return bluetoothHelp(request.target, say)
            Trigger.WIFI -> links?.wifi()?.let { it to "" } ?: return say(
                "Подключитесь к этой сети Wi-Fi и скажите ещё раз — и проверьте, что геопозиция включена: без неё Android не называет сеть.",
                "Conéctate a esa red Wi-Fi y vuelve a intentarlo. Activa también la ubicación, porque Android no permite identificar la red sin ella."
            )
        }
        val event = timetable.addEvent(mode.id, trigger, value, request.onConnect, request.exit)
        val verb = if (request.exit) say("выключать", "desactivar") else say("включать", "activar")
        val described = describeEvent(event, say.russian)
        val gap = if (described.startsWith("когда") || described.startsWith("when")) ", " else " "
        return say("Буду $verb режим ${mode.name}", "Voy a $verb ${mode.name} modo") + gap + described + "." + note
    }

    /** The device "к машине" means: a paired one by name, else the one connected right now. */
    private suspend fun bluetoothDevice(target: String, say: Say): Pair<String, String>? {
        val bt = links?.bluetooth() ?: return null
        if (!bt.permitted) return null
        EventPhrases.device(target, bt.paired)?.let { return it to "" }
        val connected = bt.connected.singleOrNull() ?: return null
        return connected to say(" Взяла то, что подключено сейчас: «$connected».", " He tomado el dispositivo que está conectado ahora: \"$connected\".")
    }

    private suspend fun bluetoothHelp(target: String, say: Say): String {
        val bt = links?.bluetooth()
        return if (bt != null && !bt.permitted) {
            say(
                "Нужно разрешение «Устройства поблизости», чтобы видеть Bluetooth. Включите его в Диагностике и скажите ещё раз.",
                "Necesito el permiso de dispositivos cercanos para detectar Bluetooth. Actívalo en Diagnóstico y vuelve a intentarlo."
            )
        } else {
            say(
                "Не знаю, какое устройство — «$target». Подключитесь к нему и скажите это ещё раз.",
                "No reconozco el dispositivo \"$target\" . Conéctalo y vuelve a intentarlo."
            )
        }
    }

    /**
     * Something happened to the phone's links: runs or ends the modos waiting
     * for it, without steps that need the owner there. Returns what was done,
     * to be said or shown; null when nothing was waiting for this.
     */
    suspend fun onLink(trigger: Trigger, value: String, connected: Boolean, russian: Boolean, runner: suspend (CommandResult) -> String): String? {
        val timetable = schedules ?: return null
        val say = Say(russian)
        val replies = timetable.events()
            .filter { it.trigger == trigger && it.onConnect == connected && (trigger == Trigger.CHARGER || it.value == value) }
            .mapNotNull { e ->
                val modo = store.byId(e.modeId) ?: return@mapNotNull null
                when {
                    e.exit && modo.active -> exit(mode, say, runner)
                    e.exit -> null
                    else -> run(mode, say, runner, automatic = true, steps = modo.steps.filterNot { it.tool in NEEDS_OWNER })
                }
            }
        return replies.joinToString(" ").ifBlank { null }
    }

    // --- schedules ---------------------------------------------------------

    private suspend fun schedule(request: CommandResult.Mode.Schedule, say: Say): String {
        val modo = store.byId(request.id) ?: return say("Этого режима уже нет.", "Ese modo ya no existe.")
        val timetable = schedules ?: return say("Расписания недоступны.", "Los horarios no están disponibles.")
        val verb = if (request.exit) say("выключать", "desactivar") else say("включать", "activar")
        scheduleRefusal(request, modo, verb, say)?.let { return it }
        val time = java.time.LocalTime.of(request.hour ?: 0, request.minute ?: 0)
        val s = timetable.set(mode.id, request.exit, time, Days.of(request.days))
        return say("Буду $verb режим ${mode.name} ", "Voy a $verb ${mode.name} modo ") +
            ScheduleMath.describe(s.days, s.time, say.russian) + "."
    }

    /** Why [request] can't be scheduled as it is: no time said, or a modo that acts towards people. */
    private fun scheduleRefusal(request: CommandResult.Mode.Schedule,:Mode, verb: String, say: Say): String? = when {
        request.hour == null || request.minute == null -> say(
            "Во сколько $verb режим ${mode.name}? Скажите целиком, например: «$verb режим ${mode.name} каждый день в 23:00».",
            "¿A qué hora debo $verb ${mode.name} modo? For example: \"$verb ${mode.name} modo every day at 11 pm\"."
        )
        !request.exit && modo.steps.any { it.tool in NEEDS_OWNER } -> say(
            "В режиме ${mode.name} есть звонок или сообщение — сам по себе, без вас, такой режим я не запускаю.",
            "${mode.name} modo calls or messages someone, so I won't start it on a timer with nobody there."
        )
        else -> null
    }

    private suspend fun unschedule(id: String, say: Say): String {
        val modo = store.byId(id) ?: return say("Этого режима уже нет.", "Ese modo ya no existe.")
        schedules?.clear(id)
        return say("Режим ${mode.name} больше не включается по расписанию.", "${mode.name} modo no longer runs on a schedule.")
    }

    /**
     * A schedule's alarm went off. Runs (or ends) the modo without the steps
     * that need the owner there, and returns what happened, for a quiet
     * notification; null when there was nothing to do.
     */
    suspend fun fire(scheduleId: String, russian: Boolean, runner: suspend (CommandResult) -> String): String? {
        val s = schedules?.byId(scheduleId) ?: return null
        val modo = store.byId(s.modeId)
        if (mode == null) {
            schedules.delete(s.id)
            return null
        }
        schedules.fired(s, clock())
        val say = Say(russian)
        return when {
            s.exit && modo.active -> exit(mode, say, runner)
            s.exit -> null
            else -> run(mode, say, runner, automatic = true, steps = modo.steps.filterNot { it.tool in NEEDS_OWNER })
        }
    }

    /**
     * After a run by hand: if the owner keeps turning this modo on around the
     * same time, offer to do it by itself. Once per modo, whatever the answer.
     */
    private suspend fun habitQuestion(mode: Mode, say: Say): String {
        val s = schedules ?: return ""
        if (s.forMode(mode.id).isNotEmpty() || s.wasSuggested(mode.id)) return ""
        val time = Habits.suggest(s.manualRuns(mode.id, clock())) ?: return ""
        s.markSuggested(mode.id)
        habit = HabitOffer(mode.id, time, clock())
        val at = "%d:%02d".format(time.hour, time.minute)
        return say(
            " Вы включаете его около $at уже не первый день — включать каждый день в $at само?",
            " Llevas varios días activándolo alrededor de las $at. ¿Quieres que lo active automáticamente todos los días a las $at?"
        )
    }

    private suspend fun answerHabit(text: String, russian: Boolean): String? {
        val offer = habit?.takeIf { clock() - it.at <= UNDO_WINDOW_MS } ?: return null
        val say = Say(russian)
        return when (com.friday.ai.core.mail.Confirmation.classify(text)) {
            com.friday.ai.core.mail.Confirmation.Answer.OTHER -> null
            com.friday.ai.core.mail.Confirmation.Answer.NO -> {
                habit = null
                say("Хорошо, только когда скажете.", "De acuerdo, solo lo activaré cuando me lo pidas.")
            }
            com.friday.ai.core.mail.Confirmation.Answer.YES -> {
                habit = null
                val modo = store.byId(offer.modeId) ?: return say("Этого режима уже нет.", "Ese modo ya no existe.")
                schedule(CommandResult.Mode.Schedule(mode.id, false, offer.time.hour, offer.time.minute, Days.mask(Days.ALL)), say)
            }
        }
    }

    /**
     * Does what the owner asked instead, then offers to keep it: in place of
     * the step of the same kind ("lo-fi" instead of "грустные песни"), or as
     * a new step.
     */
    private suspend fun correct(
        request: CommandResult.Mode.Correct,
        say: Say,
        router: (String) -> CommandResult,
        runner: suspend (CommandResult) -> String
    ): String {
        lastRun = null
        val modo = store.byId(request.id) ?: return say("Этого режима уже нет.", "Ese modo ya no existe.")
        val command = router(request.instead)
        if (command is CommandResult.ChatMessage || command is CommandResult.Mode) {
            return say(
                "Скажите, что сделать вместо этого, например «нет, включи lo-fi».",
                "Dime qué debo hacer en su lugar. Por ejemplo: \"no, play lo-fi\"."
            )
        }
        val reply = when (command) {
            is CommandResult.DeviceControl -> device.apply(command.action, command.level, say.russian).message
            else -> runner(command)
        }
        redesactivarer(mode, command, reply, say)
    }

    /** After a correction was carried out: the question whether to keep it in [mode]. */
    private fun offer(mode: Mode, command: CommandResult, reply: String, say: Say): String {
        val step = ModeSteps.envelopeOf(command)
        if (step == null || CommandExecutor.isFailure(reply)) return reply
        val replaces = modo.steps.indexOfFirst { ModeSteps.sameKind(it, step) }.takeIf { it >= 0 }
        proposal = Proposal(mode.id, step, replaces, clock())
        val question = if (replaces != null) {
            val old = ModeSteps.describe(mode.steps[replaces], say.russian)
            say(
                " Запомнить это для режима ${mode.name} вместо «$old»?",
                " ¿Quieres guardar este cambio en ${mode.name} modo instead of \"$old\"?"
            )
        } else {
            say(" Добавить это в режим ${mode.name}?", " ¿Quieres añadirlo al ${mode.name} modo?")
        }
        return reply.trim().let { if (it.last() in ".!?…") it else "$it." } + question
    }

    /** "грусти тёплый свет" → (режим грусти, "тёплый свет"): the longest saved name at the start. */
    private fun split(rest: String): Pair<Mode, String>? {
        val words = rest.trim().split(' ').filter { it.isNotBlank() }
        for (k in minOf(MAX_NAME_WORDS, words.size - 1) downTo 1) {
            // Exact: with a spare word allowed, "отдыха будильник" would pass for "отдыха".
            val modo = store.find(words.take(k).joinToString(" "), spare = 0) ?: continue
            return modo to words.drop(k).joinToString(" ")
        }
        return null
    }

    private suspend fun add(rest: String, say: Say): String {
        val (mode, what) = split(rest) ?: return say("Не поняла, в какой режим добавить.", "Which modo should I add it to?")
        return when (val compiled = compile(mode.name, what)) {
            is ModeCompiler.Result.Failed -> say(
                "Не поняла, что добавить в режим ${mode.name}.", "No he podido determinar qué debe hacer to add to ${mode.name} modo."
            )
            is ModeCompiler.Result.Steps -> {
                val steps = modo.steps + compiled.steps
                store.put(mode.copy(steps = steps, description = modo.description + "; " + what))
                say(
                    "Добавила в режим ${mode.name}: ${ModeSteps.summary(compiled.steps, say.russian)}. Теперь в нём: ${ModeSteps.summary(steps, say.russian)}.",
                    "He añadido esto al ${mode.name}:${ModeSteps.summary(compiled.steps, say.russian)}.. Ahora incluye: ${ModeSteps.summary(steps, say.russian)}."
                )
            }
        }
    }

    /** The step [what] sounds most like, by its words; removed if it's a clear match. */
    private suspend fun remove(mode: Mode, what: String, say: Say): String {
        val scored = modo.steps.mapIndexed { i, step ->
            i to ModeNames.overlap(what, ModeSteps.describe(step, true) + " " + ModeSteps.describe(step, false) + " " + step.toJson())
        }
        val best = scored.maxByOrNull { it.second }?.takeIf { it.second >= MIN_STEP_MATCH }
            ?: return say(
                "В режиме ${mode.name} такого нет. В нём: ${ModeSteps.summary(mode.steps, say.russian)}.",
                "${mode.name} modo no incluye esa acción. Actualmente hace lo siguiente: ${ModeSteps.summary(mode.steps, say.russian)}."
            )
        val steps = modo.steps.filterIndexed { i, _ -> i != best.first }
        store.put(mode.copy(steps = steps))
        val removed = ModeSteps.describe(mode.steps[best.first], say.russian)
        val left = if (steps.isEmpty()) say("В режиме больше ничего нет.", "No queda ninguna acción en el modo.")
        else say("Осталось: ${ModeSteps.summary(steps, say.russian)}.", "Queda lo siguiente: ${ModeSteps.summary(steps, say.russian)}.")
        return say("Убрала из режима ${mode.name}: $removed. ", "He quitado del ${mode.name}:$removed. ") + left
    }

    private suspend fun exit(mode: Mode, say: Say, runner: suspend (CommandResult) -> String): String {
        val undo = modo.undo ?: return say("Режим ${mode.name} сейчас не включён.", "${mode.name} modo isn't on.")
        val (messages, _) = carryOut(undo, say, runner)
        store.put(mode.copy(undo = null))
        return if (messages.isEmpty()) {
            say("Режим ${mode.name} выключен — возвращать было нечего.", "${mode.name} modo is off — nothing to put back.")
        } else {
            say("Режим ${mode.name} выключен. ", "${mode.name} modo is off. ") + sentences(messages)
        }
    }

    /** Runs [steps] in order; returns what each said and what undoes them, newest first. */
    private suspend fun carryOut(
        steps: List<ActionEnvelope>,
        say: Say,
        runner: suspend (CommandResult) -> String
    ): Pair<List<String>, List<ActionEnvelope>> {
        val messages = mutableListOf<String>()
        val undo = ArrayDeque<ActionEnvelope>()
        steps.forEach { step ->
            when (val c = (AgentTools.interpret(step.tool, step.args, now()) as? AgentTools.Call.Command)?.command) {
                null -> messages += say("Шаг «${ModeSteps.describe(step, say.russian)}» не выполнить", "No he podido ejecutar \"${ModeSteps.describe(step, say.russian)}\"")
                is CommandResult.DeviceControl -> {
                    val result = device.apply(c.action, c.level, say.russian)
                    messages += result.message
                    // Newest first, but a result's own steps keep their order (level, then mute).
                    result.undo?.steps?.asReversed()?.forEach { undo.addFirst(ModeSteps.deviceEnvelope(it.action, it.level)) }
                }
                else -> {
                    val reply = runner(c)
                    messages += reply
                    if (!CommandExecutor.isFailure(reply)) ModeSteps.undoOf(step)?.let(undo::addFirst)
                }
            }
        }
        return messages to undo.toList()
    }

    private fun describe(mode: Mode, say: Say): String {
        val state = if (mode.active) say("Сейчас включён.", "Está activado.") else say("Сейчас выключен.", "Está desactivado.")
        return say("Режим ${mode.name}: ", "${mode.name}:") + ModeSteps.summary(mode.steps, say.russian) + ". " + state
    }

    private suspend fun delete(mode: Mode, say: Say): String {
        store.delete(mode.id)
        schedules?.clear(mode.id)
        val note = if (mode.active) {
            say(" Настройки, которые он менял, оставила как есть.", " Los ajustes que modificó se quedan tal como están.")
        } else {
            ""
        }
        return say("Удалила режим ${mode.name}.", "He eliminado ${mode.name} modo.") + note
    }

    private suspend fun cancel(id: String, say: Say): String {
        val modo = store.byId(id) ?: return say("Отменять нечего.", "No hay nada que cancelar.")
        store.delete(id)
        return say(
            "Убрала режим ${mode.name}. Опишите его ещё раз, по-другому.",
            "He eliminado ${mode.name} modo. Descríbelo de otra manera."
        )
    }

    private fun list(say: Say): String {
        val modos = store.all()
        if (modes.isEmpty()) {
            return say(
                "Режимов пока нет. Скажите, например: «создай режим отдыха: беззвучный режим и яркость на минимум».",
                "No modos yet. Say, for example: \"create rest:silent ringer and lowest brightness\"."
            )
        }
        val names = modos.joinToString { it.name + if (it.active) say(" (включён)", " (activado)") else "" }
        return say("Ваши режимы: $names.", "Your modos: $names.")
    }

    private fun missing(name: String, say: Say) = say(
        "Режима «$name» у меня нет. Скажите «создай режим $name» и что в нём делать.",
        "No tengo ningún modo llamado \"$name\" modo. Say \"create $name modo\" y explícame qué debe hacer."
    )

    /** Joined so it reads as one spoken answer: each reply a sentence. */
    private fun sentences(messages: List<String>) =
        messages.filter { it.isNotBlank() }.joinToString(" ") { m -> m.trim().let { if (it.last() in ".!?…") it else "$it." } }

    private class Say(@Suppress("UNUSED_PARAMETER") val russian: Boolean) {
        operator fun invoke(@Suppress("UNUSED_PARAMETER") ru: String, es: String) = es
    }
}

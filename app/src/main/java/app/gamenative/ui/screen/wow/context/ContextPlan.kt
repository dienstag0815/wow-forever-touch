package app.gamenative.ui.screen.wow.context

import app.gamenative.ui.screen.wow.klick.WheelButton

/** Ein Schritt, den die Touch-Ansicht für einen gewählten Menüeintrag ausführt. */
enum class PlanKind {
    /** Die Taste [PlanStep.key] des Addons (WoW-Schreibweise) drücken. */
    KEY,

    /** Die Taste des Platzes [PlanStep.barSlot] der Aktionsleiste 1 drücken. */
    BAR_KEY,

    /** Klick mit [PlanStep.button] an der Stelle des Fingers beim Aufsetzen, mit Umschalt wenn [PlanStep.shift]. */
    CLICK,

    /** Die Taste, die das Spiel für die Aktion [PlanStep.wowAction] kennt (Fenster umschalten). */
    WOW_KEY,

    /** Die Spracheingabe der App für das Flüstern starten (das Chatfeld mit "/w Name " ist offen, der Text wird angehängt). */
    VOICE_WHISPER,

    /** Die Bildschirmtastatur öffnen (Flüstern mit Tastatur). */
    KEYBOARD,

    /** [PlanStep.text] als Chat-Befehl in den Chat tippen. */
    CHAT,
}

/**
 * @property waitAfterMs so lange warten, bevor der nächste Schritt kommt (das Addon braucht die Taste gedrückt und gelöst, bis der
 *                       Cursor leer ist)
 */
data class PlanStep(
    val kind: PlanKind,
    val key: String? = null,
    val barSlot: Int = 0,
    val button: WheelButton? = null,
    val shift: Boolean = false,
    val wowAction: String? = null,
    val text: String? = null,
    val waitAfterMs: Long = 0L,
)

/**
 * Was beim Loslassen auf einem Eintrag passiert, als Folge von Schritten. Rein, getestet.
 *
 * Die Reihenfolge ist wichtig: Hängt ein Gegenstand am Cursor (ein früherer Tipp hat ihn aufgenommen) und der Eintrag klickt auf einen
 * Gegenstand ([ContextEntry.clearsCursor]), kommt zuerst die Taste "Cursor leeren" des Addons (ClearCursor legt den Gegenstand an
 * seinen Platz zurück), dann [CLEAR_WAIT_MS] Pause, erst dann der Klick. Ein Rechtsklick mit dem Gegenstand am Cursor würde ihn sonst
 * nur ablegen oder tauschen, statt ihn anzulegen oder zu benutzen. Der Linksklick (Ziel wählen, Verschieben) räumt nie auf: er soll
 * den Gegenstand ja aufnehmen oder ablegen.
 */
object ContextPlan {
    /** Pause nach der Taste "Cursor leeren": Taste gedrückt (60 ms) und gelöst, das Addon führt ClearCursor beim Lösen aus. */
    const val CLEAR_WAIT_MS = 260L

    /** Pause nach der Flüster-Taste: das Addon öffnet das Chatfeld beim Lösen der Taste, das Spiel braucht ein, zwei Bilder dafür. */
    const val WHISPER_WAIT_MS = 320L

    @JvmStatic
    fun stepsFor(entry: ContextEntry, cursorHasItem: Boolean): List<PlanStep> {
        val steps = ArrayList<PlanStep>(3)
        if (entry.clearsCursor && cursorHasItem) {
            steps.add(PlanStep(PlanKind.KEY, key = ContextKeys.CLEAR_CURSOR, waitAfterMs = CLEAR_WAIT_MS))
        }
        when (entry.action) {
            EntryAction.LEFT_CLICK -> steps.add(PlanStep(PlanKind.CLICK, button = WheelButton.LEFT))
            EntryAction.RIGHT_CLICK -> steps.add(PlanStep(PlanKind.CLICK, button = WheelButton.RIGHT))
            EntryAction.SHIFT_LEFT_CLICK -> steps.add(PlanStep(PlanKind.CLICK, button = WheelButton.LEFT, shift = true))
            EntryAction.KEY -> if (entry.key != null) steps.add(PlanStep(PlanKind.KEY, key = entry.key))
            EntryAction.BAR_SLOT -> steps.add(PlanStep(PlanKind.BAR_KEY, barSlot = entry.barSlot))
            EntryAction.WOW_ACTION -> if (entry.wowAction != null) steps.add(PlanStep(PlanKind.WOW_KEY, wowAction = entry.wowAction))
            EntryAction.WHISPER_VOICE -> {
                // erst die Taste des Addons (öffnet "/w Name "), dann hat das Spiel Zeit für das Chatfeld, dann hört die App zu
                steps.add(PlanStep(PlanKind.KEY, key = entry.key ?: ContextKeys.WHISPER, waitAfterMs = WHISPER_WAIT_MS))
                steps.add(PlanStep(PlanKind.VOICE_WHISPER))
            }
            EntryAction.WHISPER_KEYBOARD -> {
                steps.add(PlanStep(PlanKind.KEY, key = entry.key ?: ContextKeys.WHISPER, waitAfterMs = WHISPER_WAIT_MS))
                steps.add(PlanStep(PlanKind.KEYBOARD))
            }
            EntryAction.CHAT_COMMAND -> if (entry.text != null) steps.add(PlanStep(PlanKind.CHAT, text = entry.text))
        }
        return steps
    }
}

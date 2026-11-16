package app.gamenative.lab

import org.json.JSONObject

/**
 * Die Werte der Spielsteuerung, so wie ein Container sie trägt: Touch-Schalter, Schussansicht, Gesten, die Extras `wowTouch`/`profileId` und ob die
 * Bildschirmsteuerung sichtbar ist. [OFF] ist, was das Labor heute schreibt (`LabContainer.config` und `ControlsVisibilityPref.save(..., false)`);
 * [fromGameContainer] liest, was der Spielbereich für seinen Container (`wow_forever`) geschrieben hat. Rein, ohne Android.
 */
data class LabControlValues(
    val touchscreenMode: Boolean,
    val shooterMode: Boolean,
    /** JSON der Gesten; leer = keine eigene Angabe (der Container fällt dann auf seine Vorgabe zurück). */
    val gestureConfig: String,
    val shooterConfig: String,
    /** Extra `wowTouch` ("1" = Touch (WoW)); null = nicht gesetzt. */
    val wowTouch: String?,
    /** Extra `profileId` (Profil der Bildschirmsteuerung); null = nicht gesetzt. */
    val profileId: String?,
    val controlsVisible: Boolean,
) {
    /** Das Profil als Zahl (0 = keine Angabe, es gilt das Standardprofil wie im Spiel). */
    val profileNumber: Int get() = profileId?.toIntOrNull() ?: 0
}

object LabGameControls {
    /** Extra des Spielbereichs, das „Touch (WoW)“ kennzeichnet (`WowTouch.EXTRA_KEY`). */
    const val EXTRA_WOW_TOUCH = "wowTouch"
    const val EXTRA_PROFILE_ID = "profileId"

    /** Schalter aus = die Labor-Bedienung von heute: Tipp = Klick, keine Schussansicht, keine Bildschirmsteuerung. */
    val OFF = LabControlValues(
        touchscreenMode = true,
        shooterMode = false,
        gestureConfig = "",
        shooterConfig = "",
        wowTouch = null,
        profileId = null,
        controlsVisible = false,
    )

    /**
     * Die Werte des Spiel-Containers aus seiner Datei `.container` ([json]) und dem gespeicherten Sichtbarkeitswert ([storedVisible], null = nie
     * gesetzt). Null, wenn es den Container nicht gibt. Nichts wird erfunden: fehlt ein Wert, gilt die Vorgabe von `Container.java`
     * (Touch aus, Schussansicht an, keine Gesten); die Bildschirmsteuerung ist sichtbar nach der Regel des Spielbereichs (`XServerScreen` 2619–2620:
     * „Touch (WoW)“ immer, sonst nur ohne Touch-Schalter) und dem Sichtbarkeitswert des Spiel-Containers (Vorgabe sichtbar).
     */
    fun fromGameContainer(json: JSONObject?, storedVisible: Boolean?): LabControlValues? {
        json ?: return null
        val extras = json.optJSONObject("extraData")
        val wowTouch = extras?.optString(EXTRA_WOW_TOUCH, "")?.takeIf { it.isNotEmpty() }
        val profile = extras?.optString(EXTRA_PROFILE_ID, "")?.takeIf { it.isNotEmpty() }
        val touch = json.optBoolean("touchscreenMode", false)
        val ruleShows = wowTouch == "1" || !touch
        return LabControlValues(
            touchscreenMode = touch,
            shooterMode = json.optBoolean("shooterMode", true),
            gestureConfig = json.optString("gestureConfig", ""),
            shooterConfig = json.optString("shooterConfig", ""),
            wowTouch = wowTouch,
            profileId = profile,
            controlsVisible = ruleShows && (storedVisible ?: true),
        )
    }

    /** Wo die Werte im Spiel-Container zu finden sind (Pfad der Datei kommt von `LabDirs`); nur für den Protokolltext. */
    fun describe(values: LabControlValues): String =
        "touch=${if (values.touchscreenMode) "on" else "off"} shooter=${if (values.shooterMode) "on" else "off"} " +
            "wowTouch=${values.wowTouch ?: "-"} profile=${values.profileId ?: "-"} " +
            "gestures=${if (values.gestureConfig.isBlank()) "default" else "own"} controls=${if (values.controlsVisible) "shown" else "hidden"}"
}

/** Wer den Zustand der Spielsteuerung zuletzt gesetzt hat. */
enum class LabGcSource(val text: String) { MANUAL("manual"), AUTO("auto") }

/**
 * Zustand der Spielsteuerung im Labor-Lauf. [windowSeen]: im letzten Schnappschuss gab es ein Fenster der Klasse [LabGameControlsLogic.GAME_CLASS];
 * [absentSinceMs]: seit wann es fehlt; [autoSuppressed]: der Nutzer hat bei sichtbarem Spielfenster ausgeschaltet, die Automatik schaltet erst
 * wieder ein, wenn das Fenster zwischendurch verschwunden war.
 */
data class LabGcState(
    val on: Boolean = false,
    val source: LabGcSource = LabGcSource.MANUAL,
    val windowSeen: Boolean = false,
    val absentSinceMs: Long? = null,
    val autoSuppressed: Boolean = false,
)

/** Eine Umschaltung, die der Aufrufer anwenden und ins Protokoll schreiben soll. */
data class LabGcChange(val on: Boolean, val source: LabGcSource, val reason: String)

data class LabGcStep(val state: LabGcState, val change: LabGcChange? = null)

/** Umschalt-Regeln: Handschalter und Automatik über die Fensterliste. Rein, ohne Android, JUnit-getestet. */
object LabGameControlsLogic {
    /** Fensterklasse des Spiels in der Fensterliste (Protokoll: `class=wow-arm64.exe`). */
    const val GAME_CLASS = "wow-arm64.exe"

    /** So lange muss das Spielfenster fehlen, bevor die Automatik ausschaltet (das Fenster öffnet sich beim Start kurz mehrfach neu). */
    const val GONE_AFTER_MS = 3_000L

    fun isGameWindow(entry: LabWinEntry): Boolean = entry.className.trim().equals(GAME_CLASS, ignoreCase = true)

    fun gameWindow(snapshot: LabWinSnapshot): LabWinEntry? = snapshot.entries.firstOrNull { isGameWindow(it) }

    /** Der Kopfzeilen-Text des Protokolls zum Zustand. */
    fun headerLine(state: LabGcState): String = "Game controls: ${if (state.on) "on" else "off"} (${state.source.text})"

    /** Handtipp auf den Schalter. Schaltet man bei sichtbarem Spielfenster aus, bleibt die Automatik aus, bis das Fenster verschwunden war. */
    fun onTap(state: LabGcState): LabGcStep {
        val on = !state.on
        val next = state.copy(on = on, source = LabGcSource.MANUAL, autoSuppressed = if (on) false else state.windowSeen)
        return LabGcStep(next, LabGcChange(on, LabGcSource.MANUAL, "switch"))
    }

    /** Neuer Schnappschuss der Fensterliste zur Zeit [nowMs]. */
    fun onSnapshot(state: LabGcState, snapshot: LabWinSnapshot, nowMs: Long): LabGcStep {
        val game = gameWindow(snapshot)
        if (game != null) {
            val seen = state.copy(windowSeen = true, absentSinceMs = null)
            if (!seen.on && game.focused && !seen.autoSuppressed) {
                val on = seen.copy(on = true, source = LabGcSource.AUTO)
                return LabGcStep(on, LabGcChange(true, LabGcSource.AUTO, "window $GAME_CLASS has focus"))
            }
            return LabGcStep(seen)
        }
        val since = state.absentSinceMs ?: nowMs
        val gone = state.copy(windowSeen = false, absentSinceMs = since)
        if (nowMs - since < GONE_AFTER_MS) return LabGcStep(gone)
        val cleared = gone.copy(autoSuppressed = false)
        if (cleared.on && cleared.source == LabGcSource.AUTO) {
            return LabGcStep(cleared.copy(on = false), LabGcChange(false, LabGcSource.AUTO, "window $GAME_CLASS is gone"))
        }
        return LabGcStep(cleared)
    }

    /** Das Anwenden ging nicht (z. B. kein Spiel-Container): zurück auf aus, die Automatik versucht es erst nach dem nächsten Fenster wieder. */
    fun applyFailed(state: LabGcState): LabGcState = state.copy(on = false, source = LabGcSource.MANUAL, autoSuppressed = true)

    /** Zeile im Protokoll für eine Umschaltung, mit Zeit seit Start des Laufs. */
    fun changeLine(change: LabGcChange, elapsedMs: Long, applied: Boolean): String {
        val seconds = "%d.%d".format(elapsedMs / 1000, (elapsedMs % 1000) / 100)
        val what = if (change.on) "on" else "off"
        val tail = if (applied) "" else " - NOT applied (no values of the game container)"
        return "-- Game controls: $what (${change.source.text}, ${change.reason}) after $seconds s$tail"
    }
}

package app.gamenative.lab

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ein abgeschlossener Lauf in der Liste "Letzte Läufe". Nur Zahlen und Namen der Einstellungen, keine Kontodaten, keine Befehlszeile.
 * [target] ist der Name von [LabTarget] in Kleinbuchstaben, [translator] der Wert von `emulator` (`Box64`, `FEXCore`, `none`).
 */
data class LabRunEntry(
    val startedMs: Long,
    val target: String,
    val durationSec: Long,
    val stage: Int,
    val stageName: String,
    val translator: String,
    val crashes: Int,
    /** Die Ausgabe war gekürzt (die Zahl der Abstürze gilt trotzdem für den ganzen Lauf). */
    val outputCut: Boolean = false,
    /** Grund des Endes wie im Protokoll („exit requested: force_close“, „Wine process ended“); leer bei Läufen aus älteren Bauten. */
    val endReason: String = "",
    /** Zustand der Hänger-Erkennung am Ende ([LabWatchState.text]); leer = nicht erhoben (nur Ziel Battle.net). */
    val watchEnd: String = "",
    /** Die Hänger-Erkennung hat im Lauf mindestens einmal „kein Fenster“ gemeldet. */
    val noWindowSeen: Boolean = false,
    /** Ein Fenster des Launchers mit mindestens [LabGuide.MAIN_WINDOW_MIN_WIDTH] Breite wurde gesehen (Hauptfenster, nicht das Anmeldefenster). */
    val mainWindowSeen: Boolean = false,
    /** Ein Spielfenster (`wow-arm64.exe`) wurde gesehen. */
    val gameWindowSeen: Boolean = false,
    /** Der Nutzer hat „Launcher neu starten“ getippt. */
    val restartRequested: Boolean = false,
)

/** Was ein Lauf am Ende über sich weiß (aus der Sitzung des Protokolls), für [LabRunHistory.entryOf]. */
data class LabRunOutcome(
    val endReason: String = "",
    val watchEnd: String = "",
    val noWindowSeen: Boolean = false,
    val mainWindowSeen: Boolean = false,
    val gameWindowSeen: Boolean = false,
    val restartRequested: Boolean = false,
)

/** Die Liste der letzten Läufe (höchstens [MAX]), neuester zuerst; rein, JUnit-getestet. Gespeichert wird sie als Text im App-Speicher des Labors. */
object LabRunHistory {
    const val MAX = 10

    fun entryOf(
        plan: LabPlan,
        startedMs: Long,
        endedMs: Long,
        crashes: Int,
        outputCut: Boolean,
        outcome: LabRunOutcome = LabRunOutcome(),
    ): LabRunEntry = LabRunEntry(
        startedMs = startedMs,
        target = plan.target.name.lowercase(),
        durationSec = ((endedMs - startedMs) / 1000).coerceAtLeast(0),
        stage = plan.stability.level,
        stageName = plan.stability.englishName,
        translator = plan.emulator + (plan.fexVersion?.let { " $it" } ?: ""),
        crashes = crashes,
        outputCut = outputCut,
        endReason = cleanReason(outcome.endReason),
        watchEnd = outcome.watchEnd,
        noWindowSeen = outcome.noWindowSeen,
        mainWindowSeen = outcome.mainWindowSeen,
        gameWindowSeen = outcome.gameWindowSeen,
        restartRequested = outcome.restartRequested,
    )

    /** Der Grund des Endes ohne Steuerzeichen und höchstens 60 Zeichen (er steht in der Anzeige und im Speicher). */
    fun cleanReason(text: String): String = text.filter { !it.isISOControl() }.trim().take(60)

    /** Fügt vorn einen Lauf an und kürzt auf [MAX]; ein Lauf mit gleicher Startzeit ersetzt den alten. */
    fun add(list: List<LabRunEntry>, entry: LabRunEntry): List<LabRunEntry> =
        (listOf(entry) + list.filter { it.startedMs != entry.startedMs }).sortedByDescending { it.startedMs }.take(MAX)

    fun toJson(list: List<LabRunEntry>): String = JSONArray().also { arr ->
        list.take(MAX).forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("t", e.startedMs)
                    put("target", e.target)
                    put("sec", e.durationSec)
                    put("stage", e.stage)
                    put("stageName", e.stageName)
                    put("translator", e.translator)
                    put("crashes", e.crashes)
                    put("cut", e.outputCut)
                    put("end", e.endReason)
                    put("watchEnd", e.watchEnd)
                    put("noWin", e.noWindowSeen)
                    put("main", e.mainWindowSeen)
                    put("game", e.gameWindowSeen)
                    put("restart", e.restartRequested)
                },
            )
        }
    }.toString()

    /** Liest die gespeicherte Liste; Kaputtes oder Fehlendes ergibt eine kürzere oder leere Liste, nie einen Fehler. */
    fun parse(text: String?): List<LabRunEntry> {
        if (text.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<LabRunEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.has("t")) continue
            out += LabRunEntry(
                startedMs = o.optLong("t"),
                target = o.optString("target", "?"),
                durationSec = o.optLong("sec"),
                stage = o.optInt("stage"),
                stageName = o.optString("stageName", "?"),
                translator = o.optString("translator", "?"),
                crashes = o.optInt("crashes"),
                outputCut = o.optBoolean("cut"),
                endReason = o.optString("end", ""),
                watchEnd = o.optString("watchEnd", ""),
                noWindowSeen = o.optBoolean("noWin"),
                mainWindowSeen = o.optBoolean("main"),
                gameWindowSeen = o.optBoolean("game"),
                restartRequested = o.optBoolean("restart"),
            )
        }
        return out.sortedByDescending { it.startedMs }.take(MAX)
    }
}

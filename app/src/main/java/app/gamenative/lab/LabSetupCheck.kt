package app.gamenative.lab

import org.json.JSONArray
import org.json.JSONObject

/** Die Punkte des Selbsttests „Einrichtung prüfen“, in der Reihenfolge der Anzeige. [label] steht im Abschnitt des Berichts. */
enum class SetupItem(val label: String) {
    GAME_CONTAINER("Game container"),
    FEX_PACKAGE("FEX package"),
    PACKAGE_LIST("Package list"),
    WINE("Wine"),
    PREFIX("Prefix"),
    LAUNCHER_FILE("Launcher file"),
    FREE_SPACE("Free space"),
    COPY("Game copy"),
    COPY_DRIVE("Copy drive"),
    ORIGINAL_FOLDER("Original game folder"),
    ORIGINAL_UNCHANGED("Original unchanged"),
    ORIGINAL_NOT_REACHABLE("Original not reachable"),
}

enum class SetupLevel(val text: String) { OK("ok"), NOTE("note"), ERROR("error") }

/** Was der Selbsttest zu einem Punkt festgestellt hat. [text] ist der englische Wortlaut für den Bericht; die Seite hat eigene Texte (de/en). */
enum class SetupReason(val text: String) {
    CONTAINER_PRESENT("present"),
    CONTAINER_MISSING("missing: the game has to be started once from the app"),
    FEX_INSTALLED("installed"),
    FEX_MISSING("listed but not installed"),
    FEX_NOT_LISTED("not in the package list"),
    LIST_OK("list readable, FEX entry has size and SHA-256 (an installed package is not hashed again: the download is deleted after installing)"),
    LIST_ERRORS("list has broken entries"),
    LIST_FEX_ENTRY_INCOMPLETE("FEX entry lacks size or SHA-256"),
    WINE_PRESENT("folder present"),
    WINE_MISSING("folder missing"),
    WINE_NOT_CHECKABLE("not checkable (no folder known)"),
    PREFIX_PRESENT("present"),
    PREFIX_NOT_CREATED("not created yet (it is created at the first start)"),
    LAUNCHER_PRESENT("Battle.net.exe present"),
    LAUNCHER_MISSING("Battle.net.exe not found (step 2)"),
    SPACE_OK("enough"),
    SPACE_LOW("below 3 GB"),
    COPY_COMPLETE("complete"),
    COPY_NONE("no copy"),
    COPY_RUNNING("being created"),
    COPY_PARTIAL("started, not complete"),
    COPY_LAYOUT_FAILED("could not be moved into the game folder"),
    COPY_SWITCH_ON_NOT_READY("switch on, but no complete copy: the start runs without it"),
    DRIVE_MOUNTED("K: mounted at the next start"),
    DRIVE_OFF("copy complete, but the switch Use game copy is off"),
    DRIVE_NOT_USED("no copy in use"),
    ORIGINAL_PRESENT("present"),
    ORIGINAL_MISSING("not found (nothing to copy)"),
    UNCHANGED("unchanged at the last check"),
    CHANGED_UNCONFIRMED("changed, not confirmed"),
    CHANGED_CONFIRMED("changed at the last check, confirmed"),
    NEVER_CHECKED("no check yet (a check runs at every start and end)"),
    NO_DRIVE_HAS_ORIGINAL("no drive or variable of the next start reaches it"),
    ORIGINAL_REACHABLE("a drive or variable of the next start would reach it"),
    NOT_CHECKABLE("not checkable"),
}

/** Ein Ergebnis. [number]: Zahl dazu (Bytes, Einträge, Zeitpunkt), [name]: Name dazu (Paket, Wine-Kennung); beides nie ein Pfad. */
data class SetupFinding(val item: SetupItem, val level: SetupLevel, val reason: SetupReason, val number: Long? = null, val name: String? = null)

data class LabSetupCheckResult(val atMs: Long, val findings: List<SetupFinding>) {
    fun count(level: SetupLevel): Int = findings.count { it.level == level }
}

/** Die Eingaben des Selbsttests: nur Existenz, Größen, Einstellungen und Marken der App; nichts vom Launcher außer „Datei da“. */
data class LabSetupInputs(
    val gameContainerPresent: Boolean,
    val fexListed: Boolean,
    val fexEntryComplete: Boolean,
    val listErrors: Int,
    val listEntries: Int,
    val fexInstalledName: String?,
    val wineName: String,
    /** null = kein Wine-Ordner bekannt (das Hauptwine ohne Pfad); sonst ob der Ordner da ist. */
    val wineFolderPresent: Boolean?,
    val prefixPresent: Boolean,
    val launcherExePresent: Boolean,
    val freeBytes: Long,
    val useGameCopy: Boolean,
    val copy: LabGuideCopy,
    /** Würde der nächste Start die Kopie als `K:` einbinden (Schalter, fertige Marke, Aufbau, Abstand zum Original)? */
    val copyWouldMount: Boolean,
    val originalPresent: Boolean,
    val original: OriginalCheckState,
    /** Erreicht der nächste Start das Original über ein Laufwerk oder eine Variable (die vorhandene Prüfung `LabContainer.leaksOriginal`)? null = nicht prüfbar. */
    val originalReachable: Boolean?,
)

/**
 * Selbsttest „Einrichtung prüfen“: prüft ohne Wine-Start (Dateien, Einstellungen, Marken) und liefert je Punkt ok, Hinweis oder Fehler. Rein, ohne
 * Android, JUnit-getestet. Das Ergebnis geht als Abschnitt `== Setup check ==` in den nächsten Labor-Bericht ([reportLines]).
 */
object LabSetupCheck {
    /** Unter so viel freiem Platz im App-Speicher gibt es einen Hinweis. */
    const val LOW_SPACE_BYTES = 3L * 1024 * 1024 * 1024

    const val TITLE = "Setup check"

    fun run(i: LabSetupInputs, atMs: Long): LabSetupCheckResult {
        val f = mutableListOf<SetupFinding>()
        fun add(item: SetupItem, level: SetupLevel, reason: SetupReason, number: Long? = null, name: String? = null) {
            f += SetupFinding(item, level, reason, number, name)
        }

        if (i.gameContainerPresent) add(SetupItem.GAME_CONTAINER, SetupLevel.OK, SetupReason.CONTAINER_PRESENT)
        else add(SetupItem.GAME_CONTAINER, SetupLevel.ERROR, SetupReason.CONTAINER_MISSING)

        when {
            i.fexInstalledName != null -> add(SetupItem.FEX_PACKAGE, SetupLevel.OK, SetupReason.FEX_INSTALLED, name = i.fexInstalledName)
            i.fexListed -> add(SetupItem.FEX_PACKAGE, SetupLevel.ERROR, SetupReason.FEX_MISSING)
            else -> add(SetupItem.FEX_PACKAGE, SetupLevel.ERROR, SetupReason.FEX_NOT_LISTED)
        }

        when {
            i.listErrors > 0 -> add(SetupItem.PACKAGE_LIST, SetupLevel.ERROR, SetupReason.LIST_ERRORS, number = i.listErrors.toLong())
            i.fexListed && !i.fexEntryComplete -> add(SetupItem.PACKAGE_LIST, SetupLevel.ERROR, SetupReason.LIST_FEX_ENTRY_INCOMPLETE)
            else -> add(SetupItem.PACKAGE_LIST, SetupLevel.OK, SetupReason.LIST_OK, number = i.listEntries.toLong())
        }

        when (i.wineFolderPresent) {
            true -> add(SetupItem.WINE, SetupLevel.OK, SetupReason.WINE_PRESENT, name = i.wineName)
            false -> add(SetupItem.WINE, SetupLevel.ERROR, SetupReason.WINE_MISSING, name = i.wineName)
            null -> add(SetupItem.WINE, SetupLevel.NOTE, SetupReason.WINE_NOT_CHECKABLE, name = i.wineName)
        }

        if (i.prefixPresent) add(SetupItem.PREFIX, SetupLevel.OK, SetupReason.PREFIX_PRESENT)
        else add(SetupItem.PREFIX, SetupLevel.NOTE, SetupReason.PREFIX_NOT_CREATED)

        if (i.launcherExePresent) add(SetupItem.LAUNCHER_FILE, SetupLevel.OK, SetupReason.LAUNCHER_PRESENT)
        else add(SetupItem.LAUNCHER_FILE, SetupLevel.NOTE, SetupReason.LAUNCHER_MISSING)

        if (i.freeBytes >= LOW_SPACE_BYTES) add(SetupItem.FREE_SPACE, SetupLevel.OK, SetupReason.SPACE_OK, number = i.freeBytes)
        else add(SetupItem.FREE_SPACE, SetupLevel.NOTE, SetupReason.SPACE_LOW, number = i.freeBytes)

        val c = i.copy
        when {
            c.layoutFailed -> add(SetupItem.COPY, SetupLevel.ERROR, SetupReason.COPY_LAYOUT_FAILED)
            c.running -> add(SetupItem.COPY, SetupLevel.NOTE, SetupReason.COPY_RUNNING)
            c.complete -> add(SetupItem.COPY, SetupLevel.OK, SetupReason.COPY_COMPLETE, number = c.finishedMs.takeIf { it > 0L })
            i.useGameCopy -> add(SetupItem.COPY, SetupLevel.NOTE, SetupReason.COPY_SWITCH_ON_NOT_READY)
            c.partial -> add(SetupItem.COPY, SetupLevel.NOTE, SetupReason.COPY_PARTIAL)
            else -> add(SetupItem.COPY, SetupLevel.NOTE, SetupReason.COPY_NONE)
        }

        when {
            i.copyWouldMount -> add(SetupItem.COPY_DRIVE, SetupLevel.OK, SetupReason.DRIVE_MOUNTED)
            c.complete && !i.useGameCopy -> add(SetupItem.COPY_DRIVE, SetupLevel.NOTE, SetupReason.DRIVE_OFF)
            else -> add(SetupItem.COPY_DRIVE, SetupLevel.NOTE, SetupReason.DRIVE_NOT_USED)
        }

        if (i.originalPresent) add(SetupItem.ORIGINAL_FOLDER, SetupLevel.OK, SetupReason.ORIGINAL_PRESENT)
        else add(SetupItem.ORIGINAL_FOLDER, SetupLevel.NOTE, SetupReason.ORIGINAL_MISSING)

        val o = i.original
        when {
            o.needsAck -> add(SetupItem.ORIGINAL_UNCHANGED, SetupLevel.ERROR, SetupReason.CHANGED_UNCONFIRMED, number = o.checkedMs.takeIf { it > 0L })
            o.unchanged == false -> add(SetupItem.ORIGINAL_UNCHANGED, SetupLevel.NOTE, SetupReason.CHANGED_CONFIRMED, number = o.checkedMs.takeIf { it > 0L })
            o.unchanged == true -> add(SetupItem.ORIGINAL_UNCHANGED, SetupLevel.OK, SetupReason.UNCHANGED, number = o.checkedMs.takeIf { it > 0L })
            else -> add(SetupItem.ORIGINAL_UNCHANGED, SetupLevel.NOTE, SetupReason.NEVER_CHECKED)
        }

        when (i.originalReachable) {
            false -> add(SetupItem.ORIGINAL_NOT_REACHABLE, SetupLevel.OK, SetupReason.NO_DRIVE_HAS_ORIGINAL)
            true -> add(SetupItem.ORIGINAL_NOT_REACHABLE, SetupLevel.ERROR, SetupReason.ORIGINAL_REACHABLE)
            null -> add(SetupItem.ORIGINAL_NOT_REACHABLE, SetupLevel.NOTE, SetupReason.NOT_CHECKABLE)
        }
        return LabSetupCheckResult(atMs, f)
    }

    /** Eine Zeile je Punkt: `ok     FEX package: installed (lab-2508)`. */
    fun line(f: SetupFinding, format: (Long) -> String = LabProtocol::timestamp): String {
        val extra = when {
            f.name != null -> " (${f.name})"
            f.number == null -> ""
            f.item == SetupItem.FREE_SPACE -> " (${f.number / (1024 * 1024)} MB)"
            f.reason == SetupReason.LIST_OK -> " (${f.number} entries)"
            f.reason == SetupReason.LIST_ERRORS -> " (${f.number})"
            else -> " (${format(f.number)})"
        }
        return "%-6s %s: %s%s".format(f.level.text, f.item.label, f.reason.text, extra)
    }

    /** Der Abschnitt `== Setup check ==` für den Bericht; [result] null = noch nie geprüft. */
    fun reportLines(result: LabSetupCheckResult?, format: (Long) -> String = LabProtocol::timestamp): List<String> {
        if (result == null) return listOf("== $TITLE ==", "not run yet (tap Check setup on the lab page)")
        return buildList {
            add("== $TITLE ==")
            add("checked: ${format(result.atMs)}")
            add("result: ${result.count(SetupLevel.OK)} ok, ${result.count(SetupLevel.NOTE)} notes, ${result.count(SetupLevel.ERROR)} errors")
            result.findings.forEach { add(line(it, format)) }
        }
    }

    fun toJson(r: LabSetupCheckResult): String = JSONObject().apply {
        put("at", r.atMs)
        put(
            "f",
            JSONArray().also { arr ->
                r.findings.forEach { f ->
                    arr.put(
                        JSONObject().apply {
                            put("i", f.item.name)
                            put("l", f.level.name)
                            put("r", f.reason.name)
                            f.number?.let { put("n", it) }
                            f.name?.let { put("s", it) }
                        },
                    )
                }
            },
        )
    }.toString()

    /** Liest ein gespeichertes Ergebnis; Kaputtes ergibt null. Unbekannte Namen (anderer Bau) lassen den Eintrag entfallen. */
    fun parse(text: String?): LabSetupCheckResult? {
        if (text.isNullOrBlank()) return null
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (!o.has("at")) return null
        val arr = o.optJSONArray("f") ?: return null
        val out = mutableListOf<SetupFinding>()
        for (k in 0 until arr.length()) {
            val e = arr.optJSONObject(k) ?: continue
            val item = SetupItem.entries.firstOrNull { it.name == e.optString("i") } ?: continue
            val level = SetupLevel.entries.firstOrNull { it.name == e.optString("l") } ?: continue
            val reason = SetupReason.entries.firstOrNull { it.name == e.optString("r") } ?: continue
            out += SetupFinding(item, level, reason, if (e.has("n")) e.optLong("n") else null, if (e.has("s")) e.optString("s") else null)
        }
        return LabSetupCheckResult(o.optLong("at"), out)
    }
}

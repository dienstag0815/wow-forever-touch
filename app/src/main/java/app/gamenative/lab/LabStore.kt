package app.gamenative.lab

import android.content.Context
import com.winlator.xenvironment.ImageFs
import java.io.File

/** Gemerkte Zustände des Labors (SharedPreferences `bnetlab`): ob der Eintrag sichtbar ist und die Einstellungen. */
object LabStore {
    private const val PREFS = "bnetlab"
    private const val KEY_ENTRY = "entry_enabled"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_RUNS = "recent_runs"
    private const val KEY_GUIDE = "guide_self"
    private const val KEY_SETUP_CHECK = "setup_check"
    private const val KEY_START_MODE = "start_mode"
    private const val KEY_ADVANCED_OPEN = "advanced_always_open"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ob die Hilfeseite den Eintrag "Labor" zeigt (nach siebenmal Tippen auf die Versionszeile). */
    fun entryEnabled(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY_ENTRY, false) }.getOrDefault(false)

    fun setEntryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENTRY, enabled).apply()
    }

    fun settings(context: Context): LabSettings =
        LabSettings.fromJson(runCatching { prefs(context).getString(KEY_SETTINGS, null) }.getOrNull())

    fun save(context: Context, settings: LabSettings) {
        prefs(context).edit().putString(KEY_SETTINGS, settings.toJson()).apply()
    }

    /** Die Liste "Letzte Läufe" (höchstens zehn, neuester zuerst). */
    fun runs(context: Context): List<LabRunEntry> =
        LabRunHistory.parse(runCatching { prefs(context).getString(KEY_RUNS, null) }.getOrNull())

    fun addRun(context: Context, entry: LabRunEntry) {
        val list = LabRunHistory.add(runs(context), entry)
        prefs(context).edit().putString(KEY_RUNS, LabRunHistory.toJson(list)).apply()
    }

    fun clearRuns(context: Context) {
        prefs(context).edit().remove(KEY_RUNS).apply()
    }

    /** Die Selbstauskünfte des Assistenten („ich bin angemeldet“, „ohne Spielkopie“), jede mit Datum. */
    fun guideSelf(context: Context): LabGuideSelf =
        LabGuideSelf.fromJson(runCatching { prefs(context).getString(KEY_GUIDE, null) }.getOrNull())

    fun saveGuideSelf(context: Context, self: LabGuideSelf) {
        prefs(context).edit().putString(KEY_GUIDE, self.toJson()).apply()
    }

    /** Das Ergebnis der letzten „Einrichtung prüfen“ (für den Abschnitt `== Setup check ==` im nächsten Bericht); null = noch nie geprüft. */
    fun setupCheck(context: Context): LabSetupCheckResult? =
        LabSetupCheck.parse(runCatching { prefs(context).getString(KEY_SETUP_CHECK, null) }.getOrNull())

    fun saveSetupCheck(context: Context, result: LabSetupCheckResult) {
        prefs(context).edit().putString(KEY_SETUP_CHECK, LabSetupCheck.toJson(result)).apply()
    }

    /** Die bewusste Wahl der Startart auf der Startseite; null = nie gewählt (dann gilt die Vorgabe, siehe [LabStartMode.effective]). */
    fun startMode(context: Context): StartMode? =
        StartMode.fromKey(runCatching { prefs(context).getString(KEY_START_MODE, null) }.getOrNull())

    fun saveStartMode(context: Context, mode: StartMode) {
        prefs(context).edit().putString(KEY_START_MODE, mode.key).apply()
    }

    /** „Erweitert immer geöffnet“ auf der Battle.net-Seite; Vorgabe aus (dann ist „Erweitert“ bei jedem Öffnen der Seite zu). Nur die Anordnung, kein Teil der Einstellungen. */
    fun advancedAlwaysOpen(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_ADVANCED_OPEN, false) }.getOrDefault(false)

    fun setAdvancedAlwaysOpen(context: Context, open: Boolean) {
        prefs(context).edit().putBoolean(KEY_ADVANCED_OPEN, open).apply()
    }

    /** "Zurücksetzen": zurück auf die Vorgaben. */
    fun reset(context: Context): LabSettings {
        val d = LabSettings()
        save(context, d)
        return d
    }
}

/** Ordner des Labors. Prefix und Container liegen wie beim Spiel unter `imagefs/home/xuser-<Kennung>`. */
object LabDirs {
    fun root(context: Context) = File(context.filesDir, LabIds.DIR_NAME)
    fun downloads(context: Context) = File(root(context), LabIds.DOWNLOAD_DIR)
    fun share(context: Context) = File(root(context), LabIds.SHARE_DIR)
    fun logs(context: Context) = File(root(context), LabIds.LOG_DIR)

    /**
     * Spielkopie: Ordner `gamecopy` mit dem Kopie-Bereich (`game`, wird als `K:` eingebunden) und den Marken daneben. Der Spielinhalt liegt im
     * Unterordner `World of Warcraft` des Kopie-Bereichs.
     */
    fun gameCopy(context: Context) = File(root(context), LabIds.COPY_DIR)
    fun gameCopyMount(context: Context) = File(gameCopy(context), LabIds.COPY_CONTENT_DIR)
    fun gameCopyContent(context: Context) = File(gameCopyMount(context), LabIds.COPY_GAME_DIR)
    fun gameCopyInfo(context: Context) = File(gameCopy(context), LabIds.COPY_INFO_FILE)
    fun originalCheckFile(context: Context) = File(root(context), LabIds.ORIGINAL_CHECK_FILE)

    private fun home(context: Context) = File(ImageFs.find(context).rootDir, "home")
    fun containerRoot(context: Context) = File(home(context), "${ImageFs.USER}-${LabIds.CONTAINER_ID}")
    fun wowContainerRoot(context: Context) = File(home(context), "${ImageFs.USER}-${LabIds.WOW_CONTAINER_ID}")
    fun driveC(context: Context) = File(containerRoot(context), ".wine/drive_c")

    /** Größe eines Ordners samt Inhalt in Bytes (ohne Symlinks zu folgen); nicht vorhanden = 0. */
    fun sizeOf(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (java.nio.file.Files.isSymbolicLink(f.toPath())) continue
            if (f.isDirectory) f.listFiles()?.forEach { stack.add(it) } else total += f.length()
        }
        return total
    }
}

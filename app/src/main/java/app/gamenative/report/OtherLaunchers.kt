package app.gamenative.report

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Eine bekannte andere Start-App für dieses Spiel: Paketname und Anzeigename. */
data class KnownLauncher(val packageName: String, val label: String)

/** Eine Datei oder ein Ordner im Spielordner, der auf eine andere Start-App deuten kann. Nur Name, Datum, Größe. */
data class FolderTrace(
    /** Pfad relativ zum Spielordner mit `/`. */
    val path: String,
    val modifiedMs: Long,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    /** Warum die Spur gemeldet wird. */
    val reason: String,
)

/** Zusammenfassung der Dateien in Logs und WTF, die älter sind als die Installation dieser App. */
data class OldFiles(val count: Int, val oldestMs: Long?, val newestMs: Long?, val examples: List<String>)

/**
 * Spuren anderer Start-Apps (reine Kotlin-Logik, JUnit-getestet): eine feste kurze Liste von Paketnamen (nur "installiert ja/nein") und
 * Dateien im Spielordner, die nicht von dieser App stammen. Es wird nichts gelesen außer Namen, Daten und Größen; nichts wird geändert.
 *
 * Die Paketnamen stammen aus den `build.gradle`-Dateien der öffentlichen Repos der Apps (Stand 14.11.2026, siehe
 * `docs/reports/logindiagnose2_2026-11-14.md`). `app.wowforever` (Original-Projekt und seine Forks, auch diese App) steht nicht in der
 * Liste: Diese App hat dieselbe Kennung, sie wäre immer "installiert".
 */
object OtherLaunchers {
    val PACKAGES = listOf(
        KnownLauncher("com.winlator", "Winlator"),
        KnownLauncher("app.gamenative", "GameNative"),
        KnownLauncher("app.gamenative.gold", "GameNative (gold build)"),
        KnownLauncher("app.wowforeverywhere", "WoW Foreverywhere"),
        KnownLauncher("com.wehavephones", "WeHavePhones"),
        KnownLauncher("app.aynthorwow", "AYN Thor WoW Launcher"),
    )

    const val MAX_TRACES = 30

    /** Wie viele Beispiele aus den alten Dateien im Bericht stehen. */
    const val MAX_EXAMPLES = 5

    private const val REASON_OLD = "older than the first install of this app"

    /** Ordner im Spielordner, die Blizzards eigene Namen tragen und von WoW Foreverywhere (`WowProduct.kt`) je Produkt angelegt werden; hier nicht von dieser App. */
    private val PRODUCT_DIRS = setOf("_classic_era_", "_anniversary_", "_classic_", "_retail_")

    /** Namensteile, die auf eine andere App oder eine Kennungsdatei hindeuten. */
    private val NAME_HINTS = listOf("identity", "winlator", "foreverywhere", "wehavephones", "gamenative", "aynthor")

    /** Was im Spielordner selbst (oberste Ebene) erwartet wird: die Ordner der beiden Client-Arten, `Data` und die Dateien dieser App und des Clients. */
    private val EXPECTED_ROOT = setOf(
        "data", "_forever_", "_classic_beta_", ".build.info", "login.txt",
        "wowforever-kennung.txt", "wowforever-kennung-kopie.key",
    )

    /** Der Zusatz für Name, Datum, Größe einer Datei oder eines Ordners. */
    fun describe(t: FolderTrace): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t.modifiedMs))
        val size = if (t.isDirectory) "folder" else "${t.sizeBytes} bytes"
        return "${t.path.take(80)} | $date | $size | ${t.reason}"
    }

    /** Eine Zeile je Paket: "installiert ja/nein", sonst nichts. */
    fun installedLines(isInstalled: (String) -> Boolean): List<String> =
        PACKAGES.map { "${it.packageName} (${it.label}): ${if (runCatching { isInstalled(it.packageName) }.getOrDefault(false)) "installed" else "not installed"}" }

    /** Die Namen der installierten Pakete (leer = keine). */
    fun installedLabels(isInstalled: (String) -> Boolean): List<String> =
        PACKAGES.filter { runCatching { isInstalled(it.packageName) }.getOrDefault(false) }.map { it.label }

    private fun hint(name: String): String? {
        val lower = name.lowercase()
        return NAME_HINTS.firstOrNull { lower.contains(it) }?.let { "name contains \"$it\"" }
    }

    private fun trace(root: File, f: File, reason: String) = FolderTrace(
        path = f.relativeTo(root).path.replace('\\', '/'),
        modifiedMs = f.lastModified(),
        sizeBytes = if (f.isFile) f.length() else 0L,
        isDirectory = f.isDirectory,
        reason = reason,
    )

    /**
     * Die Spuren im Spielordner [gameRoot]: (1) oberste Ebene: Produktordner, Namen mit einem Hinweis und alles, was weder zu dieser App
     * noch zum Client gehört; (2) je Ordner der Clientart (`flavorDirs`) Dateien mit einem Namenshinweis. Höchstens [MAX_TRACES] Einträge.
     * Alte Logs und WTF-Dateien gehören nicht dazu (siehe [oldFiles]): ein Spielordner, der älter ist als die App, ist nach einer
     * Neuinstallation normal.
     */
    fun scan(gameRoot: File, flavorDirs: List<String>): List<FolderTrace> {
        val traces = linkedMapOf<String, FolderTrace>()
        fun add(f: File, reason: String) {
            val t = trace(gameRoot, f, reason)
            traces.putIfAbsent(t.path, t)
        }
        for (f in gameRoot.listFiles().orEmpty().sortedBy { it.name }) {
            val lower = f.name.lowercase()
            when {
                f.isDirectory && lower in PRODUCT_DIRS -> add(f, "product folder of another launcher or a copy from a PC")
                hint(f.name) != null -> add(f, hint(f.name)!!)
                lower !in EXPECTED_ROOT -> add(f, "not expected here (origin unknown)")
            }
        }
        for (flavor in flavorDirs) {
            val base = File(gameRoot, flavor)
            for (f in base.listFiles().orEmpty().sortedBy { it.name }) hint(f.name)?.let { add(f, it) }
        }
        return traces.values.take(MAX_TRACES)
    }

    /** Logs und WTF-Dateien (je Ordner der Clientart), die vor [firstInstallMs] zuletzt geändert wurden, älteste zuerst. */
    fun oldFileTraces(gameRoot: File, flavorDirs: List<String>, firstInstallMs: Long?): List<FolderTrace> {
        if (firstInstallMs == null) return emptyList()
        val found = mutableListOf<FolderTrace>()
        for (flavor in flavorDirs) {
            val base = File(gameRoot, flavor)
            val files = File(base, "Logs").listFiles().orEmpty().toList() + wtfFiles(File(base, "WTF"))
            files.filter { it.isFile && it.lastModified() in 1 until firstInstallMs }.forEach { found += trace(gameRoot, it, REASON_OLD) }
        }
        return found.sortedBy { it.modifiedMs }
    }

    /** Die alten Dateien als Zusammenfassung: Anzahl, ältestes und jüngstes Datum, höchstens [MAX_EXAMPLES] Beispiele (die ältesten). */
    fun summarizeOld(files: List<FolderTrace>): OldFiles {
        if (files.isEmpty()) return OldFiles(0, null, null, emptyList())
        val sorted = files.sortedBy { it.modifiedMs }
        return OldFiles(files.size, sorted.first().modifiedMs, sorted.last().modifiedMs, sorted.take(MAX_EXAMPLES).map { "${it.path.take(80)} (${InstallTimeline.date(it.modifiedMs)})" })
    }

    private fun wtfFiles(wtf: File): List<File> {
        val files = mutableListOf<File>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue += wtf to 0
        while (queue.isNotEmpty() && files.size < 200) {
            val (dir, depth) = queue.removeFirst()
            for (c in dir.listFiles().orEmpty()) if (c.isDirectory) { if (depth < 3) queue += c to depth + 1 } else files += c
        }
        return files
    }

    /** Inhalt von `spuren.txt`. [originLine] ist die Zeile von [FolderOrigin.line]. */
    fun report(packages: List<String>, traces: List<FolderTrace>, originLine: String, old: OldFiles): String = buildString {
        appendLine("Traces of other launcher apps (only installed yes/no and file names, dates and sizes; nothing is changed)")
        appendLine()
        appendLine("Installed launcher apps from a fixed list:")
        packages.forEach { appendLine("  $it") }
        appendLine("  (app.wowforever, the original project and its forks, is not listed: this app has the same id)")
        appendLine()
        appendLine("Age of the game folder: $originLine")
        appendLine()
        appendLine("Files in the game folder that this app does not write (path | modified (UTC) | size | why), at most $MAX_TRACES:")
        if (traces.isEmpty()) appendLine("  (none)")
        traces.forEach { appendLine("  " + describe(it)) }
        appendLine()
        appendLine("Older files in Logs and WTF (last changed before this app was installed; normal after a reinstall, listed as a summary):")
        if (old.count == 0) {
            appendLine("  (none)")
        } else {
            appendLine("  ${old.count} files, oldest ${InstallTimeline.date(old.oldestMs)}, newest ${InstallTimeline.date(old.newestMs)}")
            appendLine("  examples (${old.examples.size} of ${old.count}): ${old.examples.joinToString("; ")}")
        }
    }

    /** Eine Zeile für `kurz.txt`: installierte Start-Apps, Alter des Spielordners und die Zahl der Dateien, die nicht von dieser App stammen. */
    fun compact(installedLabels: List<String>, traces: List<FolderTrace>, originLine: String): String =
        "other launcher apps installed: ${if (installedLabels.isEmpty()) "none" else installedLabels.joinToString(", ")}; " +
            "$originLine; other files in the game folder not from this app: ${traces.size}"
}

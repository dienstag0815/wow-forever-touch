package app.gamenative.lab

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

/** Ergebnis der Pfadprüfung vor jedem Kopieren, Einbinden und Löschen. */
enum class PathCheck { OK, NO_ORIGINAL, SAME, TARGET_IN_ORIGINAL, ORIGINAL_IN_TARGET }

/**
 * Pfadregeln für die Spielkopie. Alles mit kanonischen Pfaden (Verknüpfungen aufgelöst), damit weder ein Umweg über eine Verknüpfung noch
 * eine andere Schreibweise (`/sdcard/...`, `/data/media/0/...`) das Original als Ziel durchgehen lässt.
 */
object LabCopyPaths {
    /**
     * Kanonischer Pfad mit `/` als Trenner. Verknüpfungen löst der Aufruf selbst auf (`toRealPath`), weil `File.canonicalPath` sie unter
     * Windows nicht zuverlässig auflöst. Der noch nicht vorhandene Rest des Pfads wird ohne Auflösung angehängt.
     */
    fun canonical(f: File): String =
        norm(runCatching { realPath(f) }.getOrElse { runCatching { f.canonicalPath }.getOrElse { f.absolutePath } })

    /** Der tiefste vorhandene Vorfahr (eine Verknüpfung zählt als vorhanden) wird aufgelöst, der fehlende Rest normalisiert angehängt. */
    private fun realPath(f: File): String {
        var base: Path = f.absoluteFile.toPath()
        val rest = ArrayList<String>()
        while (!Files.exists(base, LinkOption.NOFOLLOW_LINKS)) {
            rest.add(0, base.fileName?.toString() ?: error("no name"))
            base = base.parent ?: error("no existing ancestor")
        }
        val real = base.toRealPath().toString()
        return if (rest.isEmpty()) real else Paths.get(real).resolve(rest.joinToString("/")).normalize().toString()
    }

    private fun norm(path: String): String = path.replace('\\', '/')

    /** [child] liegt in [parent] (oder ist gleich). */
    fun insideOrSame(child: String, parent: String): Boolean {
        val p = parent.trimEnd('/')
        return child == p || child.startsWith("$p/")
    }

    /** Ziel ungleich Original, Ziel nicht im Original, Original nicht im Ziel. */
    fun check(original: File?, target: File): PathCheck {
        if (original == null) return PathCheck.NO_ORIGINAL
        val o = canonical(original)
        val t = canonical(target)
        return when {
            o == t -> PathCheck.SAME
            insideOrSame(t, o) -> PathCheck.TARGET_IN_ORIGINAL
            insideOrSame(o, t) -> PathCheck.ORIGINAL_IN_TARGET
            else -> PathCheck.OK
        }
    }

    /** Wurzeln, unter denen derselbe geteilte Speicher von Android aus erreichbar ist. Nur zum Vergleichen, nie zum Zugriff. */
    private val SHARED_ROOTS = listOf(
        "/storage/emulated/0", "/sdcard", "/data/media/0", "/storage/self/primary", "/mnt/sdcard", "/mnt/user/0/emulated/0",
        "/mnt/runtime/default/emulated/0", "/mnt/runtime/read/emulated/0", "/mnt/runtime/write/emulated/0",
        "/mnt/pass_through/0/emulated/0",
    )

    /** Alle Schreibweisen des Originalpfads: wie angegeben, kanonisch und, liegt er im geteilten Speicher, unter jeder Wurzel davon. */
    fun spellings(original: File): Set<String> {
        val out = linkedSetOf(norm(original.path).trimEnd('/'), norm(original.absolutePath).trimEnd('/'), canonical(original).trimEnd('/'))
        for (path in out.toList()) {
            val root = SHARED_ROOTS.firstOrNull { insideOrSame(path, it) } ?: continue
            val rest = path.removePrefix(root)
            for (other in SHARED_ROOTS) out += (other + rest).trimEnd('/')
        }
        return out.filter { it.isNotEmpty() }.toSet()
    }

    /** Nennt [text] (Laufwerke, Umgebung) den Originalpfad oder einen Pfad darunter, in irgendeiner Schreibweise? */
    fun mentionsOriginal(text: String, original: File?): Boolean {
        if (original == null) return false
        return spellings(original).any { text.contains(it) }
    }

    /** Die Wirte-Pfade der Laufwerke in `drives` (`L:/pfadK:/pfad`), wie `Container.drivesIterator` sie trennt. */
    fun driveHosts(drives: String): List<String> {
        val out = ArrayList<String>()
        var index = drives.indexOf(':')
        while (index >= 0) {
            val next = drives.indexOf(':', index + 1)
            out += drives.substring(index + 1, if (next != -1) next - 1 else drives.length)
            index = next
        }
        return out
    }

    /** Berührt ein Laufwerk den Originalordner: gleich, darin oder ein Oberordner davon? */
    fun driveTouchesOriginal(hostPath: String, original: File): Boolean {
        val h = canonical(File(hostPath))
        val hn = norm(hostPath).trimEnd('/')
        return (spellings(original) + canonical(original)).any { o ->
            insideOrSame(h, o) || insideOrSame(o, h) || insideOrSame(hn, o) || insideOrSame(o, hn)
        }
    }
}

/** Zahlen über einen Ordner: Dateien, Bytes, Ordner, übergangene Verknüpfungen/Sonderdateien. */
class GameScan(
    val files: List<ScanFile>,
    val dirs: List<String>,
    val skipped: Int,
) {
    val fileCount: Int get() = files.size
    val bytes: Long get() = files.sumOf { it.size }
}

class ScanFile(val rel: String, val size: Long, val modifiedMs: Long)

/** Nur lesen: zählt und listet den Spielordner; folgt keiner Verknüpfung. */
object LabGameScan {
    fun scan(root: File, isCancelled: () -> Boolean = { false }): GameScan {
        val files = ArrayList<ScanFile>()
        val dirs = ArrayList<String>()
        var skipped = 0
        val stack = ArrayDeque<Pair<File, String>>()
        stack.add(root to "")
        while (stack.isNotEmpty()) {
            if (isCancelled()) break
            val (dir, rel) = stack.removeLast()
            for (f in dir.listFiles().orEmpty().sortedBy { it.name }) {
                val path = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                when {
                    Files.isSymbolicLink(f.toPath()) -> skipped++
                    f.isDirectory -> {
                        dirs += path
                        stack.add(f to path)
                    }
                    f.isFile -> files += ScanFile(path, f.length(), f.lastModified())
                    else -> skipped++
                }
            }
        }
        return GameScan(files, dirs, skipped)
    }
}

/** Platzregel: nötig ist die Größe plus 2 GB Reserve (bei einer angefangenen Kopie ohne das, was schon im Ziel liegt). */
object LabCopySpace {
    const val RESERVE_BYTES = 2L * 1024 * 1024 * 1024

    fun needed(originalBytes: Long, alreadyInTarget: Long = 0L): Long = (originalBytes - alreadyInTarget.coerceIn(0L, originalBytes)) + RESERVE_BYTES

    fun fits(freeBytes: Long, originalBytes: Long, alreadyInTarget: Long = 0L): Boolean = freeBytes >= needed(originalBytes, alreadyInTarget)
}

/**
 * Die Fertig-Marke einer Kopie (`copy.json` neben dem Kopie-Ordner, nie darin). [keepExtras]: die Kopie war schon einmal fertig und der Launcher
 * darf an ihr gearbeitet haben; dann löscht „Kopie fortsetzen“ nichts, was das Original nicht hat (nur `.labpart`-Teildateien). Die Marke bleibt, bis
 * die Kopie gelöscht wird; nur ein vollständiges Neuaufsetzen (Kopie löschen) räumt auf.
 */
data class LabCopyInfo(
    val complete: Boolean,
    val files: Int,
    val bytes: Long,
    val skipped: Int,
    val startedMs: Long,
    val finishedMs: Long,
    val keepExtras: Boolean = false,
) {
    val seconds: Long get() = (finishedMs - startedMs) / 1000

    fun toJson(): String = JSONObject().apply {
        put("complete", complete)
        put("files", files)
        put("bytes", bytes)
        put("skipped", skipped)
        put("startedMs", startedMs)
        put("finishedMs", finishedMs)
        put("keepExtras", keepExtras)
    }.toString()

    companion object {
        /** Ohne gültige Marke gilt die Kopie als nicht fertig (null). */
        fun parse(text: String?): LabCopyInfo? {
            if (text.isNullOrBlank()) return null
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
            return LabCopyInfo(
                complete = o.optBoolean("complete", false),
                files = o.optInt("files", 0),
                bytes = o.optLong("bytes", 0L),
                skipped = o.optInt("skipped", 0),
                startedMs = o.optLong("startedMs", 0L),
                finishedMs = o.optLong("finishedMs", 0L),
                keepExtras = o.optBoolean("keepExtras", false),
            )
        }
    }
}

/** Was „Kopie fortsetzen/anlegen“ mit einer Kopie macht, die schon einmal fertig war (der Launcher darf an ihr gearbeitet haben). */
object LabCopyRefresh {
    /** Bleibt beim Fortsetzen, was das Original nicht hat? Ja, sobald die Kopie fertig war; nur „Kopie löschen“ setzt neu auf. */
    fun keepsExtras(previous: LabCopyInfo?): Boolean = previous != null && (previous.complete || previous.keepExtras)

    /** Marke, solange kopiert wird: nicht fertig (nie einbindbar), aber mit dem Merker; ohne frühere fertige Kopie: keine Marke. */
    fun markerWhileRunning(previous: LabCopyInfo?): LabCopyInfo? =
        if (keepsExtras(previous)) previous!!.copy(complete = false, keepExtras = true) else null
}

class CopyProgress(val filesDone: Int, val filesTotal: Int, val bytesDone: Long, val bytesTotal: Long, val current: String)

sealed class CopyOutcome {
    data class Done(val info: LabCopyInfo) : CopyOutcome()
    data class Cancelled(val progress: CopyProgress?) : CopyOutcome()
    data class Refused(val check: PathCheck) : CopyOutcome()
    data class Failed(val reason: Reason, val rel: String = "") : CopyOutcome()

    enum class Reason { IO, NO_ORIGINAL, CHANGED_DURING_COPY, INCOMPLETE, LAYOUT }
}

/**
 * Das einzige Stück Code, das schreibt: alles Schreiben, Umbenennen, Zeit setzen und Löschen läuft über diese Klasse und trifft nur Pfade
 * unterhalb von [root] (dem Kopie-Ordner). Jeder Pfad wird vor dem Zugriff kanonisch geprüft; ein Pfad außerhalb wirft.
 */
class LabCopyTarget(val root: File, private val original: File) {
    private val rootCanon: String = LabCopyPaths.canonical(root)

    init {
        require(LabCopyPaths.check(original, root) == PathCheck.OK) { "target touches the original" }
    }

    fun resolve(rel: String): File {
        require(rel.isNotEmpty() && !rel.startsWith("/") && rel.split('/').none { it == ".." || it == "." }) { "bad relative path" }
        val f = File(root, rel)
        val c = LabCopyPaths.canonical(f)
        // Der Pfad liegt unter dem Kopie-Ordner und nirgends im oder über dem Original.
        require(c.startsWith("$rootCanon/")) { "outside the copy folder" }
        require(LabCopyPaths.check(original, f) == PathCheck.OK) { "touches the original" }
        return f
    }

    fun makeDir(rel: String) {
        val d = resolve(rel)
        if (!d.isDirectory && !d.mkdirs()) throw IOException("mkdir failed")
    }

    fun makeRoot() {
        if (!root.isDirectory && !root.mkdirs()) throw IOException("mkdir failed")
    }

    fun partFile(rel: String): File = resolve(rel + PART_SUFFIX)

    fun openPart(rel: String): FileOutputStream {
        val p = partFile(rel)
        p.parentFile?.let { if (!it.isDirectory) it.mkdirs() }
        return FileOutputStream(p)
    }

    /** Macht aus der Teildatei die fertige Datei und setzt die Änderungszeit des Originals darauf (beides im Kopie-Ordner). */
    fun commit(rel: String, modifiedMs: Long) {
        val part = partFile(rel)
        val dest = resolve(rel)
        // Eine ältere Fassung der Datei im Kopie-Ordner weicht der neuen (unter Android überschreibt rename, anderswo nicht).
        if (dest.exists()) removeTree(dest)
        if (!part.renameTo(dest)) throw IOException("rename failed")
        dest.setLastModified(modifiedMs)
    }

    fun discardPart(rel: String) {
        partFile(rel).delete()
    }

    /**
     * Benennt einen Eintrag innerhalb des Kopie-Ordners um (gleiches Dateisystem, es werden keine Daten kopiert). Beide Pfade laufen durch
     * [resolve]; ein vorhandenes Ziel wird nie überschrieben und der Zielordner muss da sein.
     */
    fun rename(fromRel: String, toRel: String) {
        val from = resolve(fromRel)
        val to = resolve(toRel)
        if (!from.exists()) throw IOException("source missing")
        if (to.exists() || Files.isSymbolicLink(to.toPath())) throw IOException("target exists")
        if (to.parentFile?.isDirectory != true) throw IOException("target folder missing")
        if (!from.renameTo(to)) throw IOException("rename failed")
    }

    /** Löscht [f] im Kopie-Ordner (eine Datei oder ein ganzer Teilbaum, ohne Verknüpfungen zu folgen). */
    fun remove(f: File) {
        val c = LabCopyPaths.canonical(f)
        require(c.startsWith("$rootCanon/")) { "outside the copy folder" }
        removeTree(f)
    }

    private fun removeTree(f: File) {
        if (Files.isSymbolicLink(f.toPath())) {
            f.delete()
            return
        }
        if (f.isDirectory) f.listFiles().orEmpty().forEach { removeTree(it) }
        f.delete()
    }

    companion object {
        const val PART_SUFFIX = ".labpart"
    }
}

/**
 * Kopiert den Spielordner in den Kopie-Ordner. Das Original wird nur gelesen (`listFiles`, `length`, `lastModified`,
 * `FileInputStream`); geschrieben wird ausschließlich über [LabCopyTarget]. Fortsetzbar: eine Datei mit gleicher Größe und Änderungszeit
 * (±2 s) im Ziel wird übersprungen. Teildateien tragen `.labpart` und werden nie als fertig gezählt.
 */
object LabGameCopier {
    private const val CHUNK = 256 * 1024
    private const val TIME_TOLERANCE_MS = 2000L

    fun sameFile(src: ScanFile, target: File): Boolean =
        target.isFile && target.length() == src.size && Math.abs(target.lastModified() - src.modifiedMs) < TIME_TOLERANCE_MS

    fun copy(
        original: File,
        content: File,
        isCancelled: () -> Boolean,
        onProgress: (CopyProgress) -> Unit,
        nowMs: () -> Long = System::currentTimeMillis,
        keepExtras: Boolean = false,
    ): CopyOutcome {
        if (!original.isDirectory) return CopyOutcome.Failed(CopyOutcome.Reason.NO_ORIGINAL)
        val check = LabCopyPaths.check(original, content)
        if (check != PathCheck.OK) return CopyOutcome.Refused(check)
        val started = nowMs()
        val target = LabCopyTarget(content, original)
        var done = 0
        var bytesDone = 0L
        var currentRel = ""
        try {
            val scan = LabGameScan.scan(original, isCancelled)
            if (isCancelled()) return CopyOutcome.Cancelled(null)
            val total = scan.fileCount
            val totalBytes = scan.bytes
            fun report() = onProgress(CopyProgress(done, total, bytesDone, totalBytes, currentRel))
            target.makeRoot()
            prune(target, scan, keepExtras)
            for (d in scan.dirs) target.makeDir(d)
            report()
            val buf = ByteArray(CHUNK)
            for (src in scan.files) {
                if (isCancelled()) return CopyOutcome.Cancelled(CopyProgress(done, total, bytesDone, totalBytes, currentRel))
                currentRel = src.rel
                val dest = target.resolve(src.rel)
                if (sameFile(src, dest)) {
                    done++
                    bytesDone += src.size
                    report()
                    continue
                }
                var copied = 0L
                FileInputStream(File(original, src.rel)).use { input ->
                    target.openPart(src.rel).use { out ->
                        while (true) {
                            if (isCancelled()) break
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            copied += n
                            bytesDone += n
                            if (copied % (8L * CHUNK) < n) report()
                        }
                    }
                }
                if (isCancelled()) {
                    target.discardPart(src.rel)
                    return CopyOutcome.Cancelled(CopyProgress(done, total, bytesDone - copied, totalBytes, currentRel))
                }
                if (copied != src.size) {
                    target.discardPart(src.rel)
                    return CopyOutcome.Failed(CopyOutcome.Reason.CHANGED_DURING_COPY, src.rel)
                }
                target.commit(src.rel, src.modifiedMs)
                done++
                report()
            }
            // Gegenprobe: jede Datei des Originals liegt mit gleicher Größe im Ziel.
            val missing = scan.files.firstOrNull { val f = target.resolve(it.rel); !f.isFile || f.length() != it.size }
            if (missing != null) return CopyOutcome.Failed(CopyOutcome.Reason.INCOMPLETE, missing.rel)
            return CopyOutcome.Done(LabCopyInfo(true, total, totalBytes, scan.skipped, started, nowMs(), keepExtras))
        } catch (e: IOException) {
            return CopyOutcome.Failed(CopyOutcome.Reason.IO, currentRel)
        }
    }

    /**
     * Entfernt im Ziel, was das Original nicht (mehr) hat: Teildateien, überzählige Dateien und Ordner. Nur im Kopie-Ordner.
     * Mit [keepExtras] (die Kopie war schon einmal fertig, der Launcher darf an ihr gearbeitet haben, z. B. `.product.db`) bleibt alles, was das
     * Original nicht hat, stehen; entfernt werden nur `.labpart`-Teildateien und ein Eintrag, dessen Art (Datei/Ordner) dem Original widerspricht.
     */
    private fun prune(target: LabCopyTarget, scan: GameScan, keepExtras: Boolean = false) {
        val keepFiles = scan.files.mapTo(HashSet()) { it.rel }
        val keepDirs = scan.dirs.toHashSet()
        fun walk(dir: File, rel: String) {
            for (f in dir.listFiles().orEmpty()) {
                val path = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                val isLink = Files.isSymbolicLink(f.toPath())
                if (keepExtras) {
                    when {
                        isLink -> if (path in keepFiles || path in keepDirs) target.remove(f)
                        f.isDirectory -> if (path in keepFiles) target.remove(f) else walk(f, path)
                        f.name.endsWith(LabCopyTarget.PART_SUFFIX) -> target.remove(f)
                        path in keepDirs -> target.remove(f)
                    }
                    continue
                }
                when {
                    isLink -> target.remove(f)
                    f.isDirectory -> if (path in keepDirs) walk(f, path) else target.remove(f)
                    path in keepFiles -> Unit
                    else -> target.remove(f)
                }
            }
        }
        walk(target.root, "")
    }
}

/** "Delete copy": löscht nur den Kopie-Ordner unterhalb von [allowedParent] und nie etwas, das das Original berührt. */
object LabCopyDelete {
    enum class Result { DELETED, NOTHING_THERE, REFUSED }

    fun delete(copyDir: File, allowedParent: File, original: File?): Result {
        val c = LabCopyPaths.canonical(copyDir)
        val p = LabCopyPaths.canonical(allowedParent)
        if (c == p || !c.startsWith(p.trimEnd('/') + "/")) return Result.REFUSED
        if (original != null && LabCopyPaths.check(original, copyDir) != PathCheck.OK) return Result.REFUSED
        if (!copyDir.exists() && !Files.isSymbolicLink(copyDir.toPath())) return Result.NOTHING_THERE
        return if (removeTree(copyDir)) Result.DELETED else Result.REFUSED
    }

    /** Löscht den Teilbaum; eine Verknüpfung wird selbst entfernt, ihr Ziel nie betreten. */
    private fun removeTree(f: File): Boolean {
        if (Files.isSymbolicLink(f.toPath())) return f.delete()
        var ok = true
        if (f.isDirectory) f.listFiles().orEmpty().forEach { ok = removeTree(it) && ok }
        return f.delete() && ok
    }
}

/**
 * Welche Kopie darf das Labor sehen? Nur eine fertige, bei eingeschaltetem Schalter, im neuen Aufbau (Spielinhalt im Unterordner
 * `World of Warcraft`, siehe [LabCopyLayout]), deren Ordner da ist und das Original nicht berührt. Eine Kopie, deren Umzug noch läuft oder
 * scheiterte, wird nie eingebunden. Sonst `null`: dann gibt es kein zusätzliches Laufwerk.
 */
object LabCopyMount {
    /** [mountRoot] ist der Kopie-Bereich (`gamecopy/game`), der als `K:` eingebunden würde. */
    fun resolve(useCopy: Boolean, info: LabCopyInfo?, mountRoot: File, original: File?): File? {
        if (!useCopy || info == null || !info.complete || !mountRoot.isDirectory) return null
        if (!LabCopyLayout.ready(mountRoot)) return null
        if (original != null && LabCopyPaths.check(original, mountRoot) != PathCheck.OK) return null
        return mountRoot
    }

    /** Windows-Pfad der Kopie im Protokoll (das Laufwerk) und des Spielordners darin. */
    fun windowsRoot(): String = "${LabIds.COPY_DRIVE}:\\"

    fun windowsGameRoot(): String = "${windowsRoot()}${LabIds.COPY_GAME_DIR}\\"

    fun protocolLine(mounted: File?, info: LabCopyInfo?, layoutProblem: String? = null): String = when {
        mounted != null && info != null ->
            "Game copy: on, drive ${LabIds.COPY_DRIVE}: -> Windows path ${windowsRoot()} (game folder ${windowsGameRoot()}, ${info.files} files, ${info.bytes} bytes; the original folder is not mounted)"
        layoutProblem != null -> "Game copy: off ($layoutProblem; the original folder is not mounted)"
        else -> "Game copy: off (the original folder is not mounted)"
    }
}

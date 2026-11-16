package app.gamenative.lab

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Warum der Umzug einer Kopie in den Unterordner nicht gelang. Nichts wird dabei gelöscht. */
enum class LayoutReason { NO_ORIGINAL, REFUSED, NAME_CLASH, IO }

sealed class LayoutResult {
    /** Die Kopie liegt schon im neuen Aufbau (oder es gab nichts umzuziehen und die Marke ist jetzt gesetzt). */
    data class Ready(val moved: Int) : LayoutResult()

    /** Die Kopie ist nicht einbindbar; [rel] nennt den Eintrag, an dem es scheiterte. */
    data class Failed(val reason: LayoutReason, val rel: String = "") : LayoutResult()
}

/**
 * Aufbau der Kopie auf der Platte. Alt (vor labkopie2): der Spielinhalt liegt direkt im Kopie-Bereich (`gamecopy/game`, als `K:` eingebunden).
 * Neu: der Spielinhalt liegt im Unterordner [LabIds.COPY_GAME_DIR] des Kopie-Bereichs, denn diesen Ordnernamen hängt der Launcher an den gewählten
 * Installationsort selbst an. Der Umzug ist reines Umbenennen innerhalb des Kopie-Bereichs (kein Kopieren von Daten), wiederholbar und nach
 * einem Abbruch fortsetzbar. Die Marken liegen in `gamecopy/`, nie im Kopie-Bereich:
 *
 *  - `layout.json`: der neue Aufbau gilt (wird erst nach dem letzten Umbenennen geschrieben);
 *  - `moving.json`: der Umzug läuft (wird nach dem ersten Schritt geschrieben und zuletzt entfernt).
 *
 * Heißt im alten Aufbau ein Eintrag schon `World of Warcraft`, wird er als Erstes in [OLD_NAME] umbenannt (dieser Name zeigt auch nach einem
 * Abbruch, dass der Umzug begonnen hat) und zuletzt als `World of Warcraft/World of Warcraft` an seinen Platz gesetzt.
 */
object LabCopyLayout {
    const val VERSION = 2
    const val OLD_NAME = LabIds.COPY_GAME_DIR + ".labold"

    enum class State { CURRENT, EMPTY, LEGACY, MOVING }

    /** `parent` ist `gamecopy/` (neben dem Kopie-Bereich), `area` der Kopie-Bereich. */
    fun isCurrent(parent: File): Boolean = layoutVersion(runCatching { File(parent, LabIds.COPY_LAYOUT_FILE).readText(Charsets.UTF_8) }.getOrNull()) >= VERSION

    fun layoutVersion(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        return runCatching { JSONObject(text).optInt("layout", 0) }.getOrDefault(0)
    }

    fun layoutJson(): String = JSONObject().put("layout", VERSION).toString()

    /** Nur lesen. */
    fun state(parent: File, area: File): State {
        if (isCurrent(parent)) return State.CURRENT
        val names = area.list()?.toSet().orEmpty()
        if (File(parent, LabIds.COPY_MOVING_FILE).isFile || OLD_NAME in names) return State.MOVING
        return if (names.isEmpty()) State.EMPTY else State.LEGACY
    }

    /** Ist die Kopie im neuen Aufbau und der Spielordner da? Nur dann ist sie einbindbar. */
    fun ready(area: File): Boolean {
        val parent = area.parentFile ?: return false
        return isCurrent(parent) && File(area, LabIds.COPY_GAME_DIR).isDirectory && !File(parent, LabIds.COPY_MOVING_FILE).exists()
    }

    /**
     * Bringt die Kopie in den neuen Aufbau (oder setzt einen abgebrochenen Umzug fort). Umbenannt wird nur über [LabCopyTarget] mit dem Kopie-Bereich
     * als Wurzel; die Marken werden nur direkt in [parent] geschrieben, und nur wenn [parent] das Original nicht berührt. Schlägt etwas fehl,
     * bleibt alles, wie es ist, und das Ergebnis ist [LayoutResult.Failed].
     */
    fun migrate(parent: File, area: File, original: File?): LayoutResult {
        val state = state(parent, area)
        if (state == State.CURRENT) return LayoutResult.Ready(0)
        if (original == null) return LayoutResult.Failed(LayoutReason.NO_ORIGINAL)
        val target = try {
            LabCopyTarget(area, original)
        } catch (e: IllegalArgumentException) {
            return LayoutResult.Failed(LayoutReason.REFUSED)
        }
        if (LabCopyPaths.check(original, parent) != PathCheck.OK) return LayoutResult.Failed(LayoutReason.REFUSED)
        var rel = ""
        try {
            if (state == State.EMPTY) {
                writeMark(parent, LabIds.COPY_LAYOUT_FILE, layoutJson())
                return LayoutResult.Ready(0)
            }
            val game = LabIds.COPY_GAME_DIR
            if (state == State.LEGACY) {
                val names = area.list()?.toSet().orEmpty()
                if (game in names) {
                    if (OLD_NAME in names) return LayoutResult.Failed(LayoutReason.NAME_CLASH, OLD_NAME)
                    rel = game
                    target.rename(game, OLD_NAME)
                }
                writeMark(parent, LabIds.COPY_MOVING_FILE, "{\"moving\":true}")
            }
            // Ab hier gilt: der Umzug läuft. Ein vorhandener Eintrag `World of Warcraft` ist unser Zielordner (oder fehlt noch).
            val dest = File(area, game)
            if (dest.exists() && !dest.isDirectory) return LayoutResult.Failed(LayoutReason.NAME_CLASH, game)
            rel = game
            target.makeDir(game)
            var moved = 0
            for (name in area.list().orEmpty().sorted()) {
                if (name == game || name == OLD_NAME) continue
                rel = name
                if (File(dest, name).exists() || Files.isSymbolicLink(File(dest, name).toPath())) return LayoutResult.Failed(LayoutReason.NAME_CLASH, name)
                target.rename(name, "$game/$name")
                moved++
            }
            if (File(area, OLD_NAME).exists()) {
                rel = OLD_NAME
                if (File(dest, game).exists()) return LayoutResult.Failed(LayoutReason.NAME_CLASH, game)
                target.rename(OLD_NAME, "$game/$game")
                moved++
            }
            // Erst die neue Marke, dann die alte weg: nach einem Abbruch dazwischen gilt der neue Aufbau schon.
            writeMark(parent, LabIds.COPY_LAYOUT_FILE, layoutJson())
            File(parent, LabIds.COPY_MOVING_FILE).delete()
            return LayoutResult.Ready(moved)
        } catch (e: IOException) {
            return LayoutResult.Failed(LayoutReason.IO, rel)
        } catch (e: IllegalArgumentException) {
            return LayoutResult.Failed(LayoutReason.REFUSED, rel)
        } catch (e: SecurityException) {
            return LayoutResult.Failed(LayoutReason.IO, rel)
        }
    }

    /** Eine Marke direkt in `gamecopy/` schreiben (Teildatei, dann Umbenennen). Wirft bei Fehlern. */
    internal fun writeMark(parent: File, name: String, text: String) {
        require(name == File(name).name) { "bad mark name" }
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("mkdir failed")
        val file = File(parent, name)
        val tmp = File(parent, "$name.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
    }
}

package app.gamenative.lab

import java.io.File
import java.nio.file.Files

/**
 * Was im Prefix des Labors liegt (Dateiprüfung von der App-Seite, ohne Wine): die Hilfsprogramme `winhandler.exe` und `wfm.exe` und die
 * Übersetzer-DLLs in `system32`. Der Prefix ist ein Overlay (oben die Änderungen des Labors, unten das Muster des Pakets); geprüft wird
 * in beiden Schichten. Rein bis auf Dateizugriffe, JUnit-getestet mit einem Ordner.
 */
object LabPrefixFacts {
    /** Pfade unterhalb von `.wine`, in der Reihenfolge des Protokolls. */
    val FILES = listOf(
        "drive_c/windows/winhandler.exe",
        "drive_c/windows/wfm.exe",
        "drive_c/windows/system32/wowbox64.dll",
        "drive_c/windows/system32/libwow64fex.dll",
        "drive_c/windows/system32/libarm64ecfex.dll",
    )

    /** Eine Schicht des Prefix (z. B. "upper", "lower") und ihr Ordner (der Ordner `.wine`); [root] null = Schicht gibt es nicht. */
    data class Layer(val name: String, val root: File?)

    /** Zustand einer Datei in einer Schicht als Text: `missing`, `present (N bytes)`, `present (N bytes, link)`, `dangling link`. */
    fun describe(root: File?, relative: String): String {
        if (root == null) return "no such layer"
        val f = File(root, relative)
        val link = runCatching { Files.isSymbolicLink(f.toPath()) }.getOrDefault(false)
        return when {
            f.isFile -> "present (${f.length()} bytes${if (link) ", link" else ""})"
            link -> "dangling link"
            else -> "missing"
        }
    }

    /** Eine Zeile je Datei: `<Datei>: upper=…; lower=…`. */
    fun lines(layers: List<Layer>): List<String> = FILES.map { rel ->
        val name = rel.substringAfterLast('/')
        name + ": " + layers.joinToString("; ") { "${it.name}=${describe(it.root, rel)}" }
    }

    /** Der tatsächliche Wert von `HODLL` für das Protokoll; leer = nicht gesetzt (Wine startet dann ohne Übersetzer-Eintrag). */
    fun hodllLine(hodll: String?): String = "HODLL=" + if (hodll.isNullOrEmpty()) "(not set)" else hodll
}

package app.gamenative.lab

import android.content.Context
import app.gamenative.ui.screen.wow.FpsLimit
import app.gamenative.ui.screen.wow.GameRegion
import app.gamenative.ui.screen.wow.GraphicsPreset
import app.gamenative.ui.screen.wow.WowClientDownloader
import app.gamenative.ui.screen.wow.ensureGameConfig
import app.gamenative.ui.screen.wow.graphicsCvars
import app.gamenative.wowicons.AddonSetup
import java.io.File

/**
 * Ein Schritt der Einrichtung der Spielkopie: [name] steht im Protokoll, [paths] sind alle Pfade (relativ zum Spielordner der Kopie), unter denen
 * der Schritt schreiben kann, und [action] tut es im übergebenen Spielordner. Gibt einen Fehlertext zurück oder null bei Erfolg.
 */
class LabSetupStep(val name: String, val paths: List<String>, val action: (File) -> String?)

/** Ergebnis: [skipped] = Grund, warum nichts lief; sonst die Namen der erledigten und der gescheiterten Schritte. */
class LabSetupResult(val done: List<String>, val failed: List<String>, val skipped: String?) {
    /** Die Zeile `Game setup on copy: ...` im Kopf des Protokolls. */
    fun line(): String = when {
        skipped != null -> "Game setup on copy: skipped ($skipped)"
        failed.isEmpty() -> "Game setup on copy: ${done.joinToString(", ").ifEmpty { "nothing to do" }}"
        else -> "Game setup on copy: ${done.joinToString(", ").ifEmpty { "nothing" }}; failed: ${failed.joinToString(", ")}"
    }
}

/**
 * Richtet die Spielkopie so ein, wie der normale Spielstart den Spielordner einrichtet (Spieleinstellungen `Config.wtf` und das Addon), bevor ein
 * Labor-Start mit eingeschalteter Kopie beginnt. Geschrieben wird **nur** unterhalb des Spielordners der Kopie und nur nach der kanonischen Prüfung
 * von [LabCopyTarget] (Verknüpfungen aufgelöst, nie im, über oder gleich dem Original); der Originalordner wird nie übergeben. Die Funktionen
 * des Spielstarts nehmen den Zielordner als Eingabe, sie werden hier nur aufgerufen, nicht geändert. Dateien der App zur Rechnerkennung im
 * Spielordner schreiben diese Funktionen nicht und dieser Code auch nicht.
 */
object LabGameSetup {
    /** Die Ordner, in die Spieleinstellungen und Addon schreiben (siehe `BridgeInstaller.FLAVOR_DIRS`). */
    private val FLAVORS = listOf("_forever_", "_classic_beta_")

    private const val ADDON = "Interface/AddOns/TouchForever/x"
    private const val OLD_ADDON = "Interface/AddOns/WowForeverBridge/x"

    /** Alle Pfade, unter denen Spieleinstellungen und Addon schreiben können (für die Prüfung gegen das Original). */
    internal val WRITE_PATHS: List<String> = FLAVORS.flatMap { f ->
        listOf("$f/.flavor.info", "$f/WTF/Config.wtf", "$f/WTF/Account/x", "$f/$ADDON", "$f/$OLD_ADDON")
    }

    /** Pfade nur für den Schritt Spieleinstellungen. */
    internal val CONFIG_PATHS: List<String> = FLAVORS.flatMap { f -> listOf("$f/.flavor.info", "$f/WTF/Config.wtf") }

    /** Pfade nur für den Schritt Addon. */
    internal val ADDON_PATHS: List<String> = FLAVORS.flatMap { f -> listOf("$f/WTF/Config.wtf", "$f/WTF/Account/x", "$f/$ADDON", "$f/$OLD_ADDON") }

    /**
     * Führt [steps] im Spielordner [content] der Kopie aus. Null, wenn keine Kopie eingebunden ist ([mounted] null): dann läuft nichts und es gibt
     * keine Zeile. Sonst ein Ergebnis; ohne bekanntes Original, mit einem Spielordner, der das Original berührt, oder ohne die Client-Dateien in
     * der Kopie läuft nichts (Grund in [LabSetupResult.skipped]).
     */
    fun run(mounted: File?, content: File, original: File?, steps: List<LabSetupStep>): LabSetupResult? {
        mounted ?: return null
        original ?: return LabSetupResult(emptyList(), emptyList(), "the original game folder is not known, so the copy cannot be told apart from it")
        val target = try {
            LabCopyTarget(content, original)
        } catch (e: IllegalArgumentException) {
            return LabSetupResult(emptyList(), emptyList(), "the copy folder touches the original game folder")
        }
        if (!content.isDirectory) return LabSetupResult(emptyList(), emptyList(), "there is no game folder in the copy")
        if (!hasClient(content)) return LabSetupResult(emptyList(), emptyList(), "the client files are not in the copy yet")
        val done = ArrayList<String>()
        val failed = ArrayList<String>()
        for (step in steps) {
            val allowed = runCatching { step.paths.forEach { target.resolve(it) } }.isSuccess
            if (!allowed) {
                failed += "${step.name} (a path leaves the copy or touches the original)"
                continue
            }
            val problem = try {
                step.action(content)
            } catch (e: Exception) {
                e.javaClass.simpleName
            }
            if (problem == null) done += step.name else failed += "${step.name} ($problem)"
        }
        return LabSetupResult(done, failed, null)
    }

    /** Die Kopie trägt einen Client (`_forever_` mit dem Programm und `.build.info`); sonst richtet der Launcher sie erst ein. Gross-/Kleinschreibung gleich. */
    internal fun hasClient(content: File): Boolean {
        val flavor = File(content, "_forever_")
        val exe = flavor.listFiles()?.any { it.isFile && it.name.equals(WowClientDownloader.EXE_NAME, ignoreCase = true) } == true
        return exe && File(content, WowClientDownloader.BUILD_INFO).isFile
    }

    /**
     * Die zwei Schritte des Spielstarts (`prepareLaunch`): Spieleinstellungen und Addon, mit den Einstellungen der App (Region, Bildrate,
     * Grafikvorgabe) und der Bildgröße des Labor-Containers [screenSize] (wie im Spiel: Auflösung = Container). `InputDeviceInterfaceStyle` bleibt
     * unverändert (null), das Labor wählt keine Steuerung für die Spielfenster-Oberfläche vor.
     */
    fun steps(context: Context, screenSize: String): List<LabSetupStep> = listOf(
        LabSetupStep("Config.wtf", CONFIG_PATHS) { root ->
            ensureGameConfig(
                root, screenSize, GameRegion.saved(context), FpsLimit.current(context).fps, null,
                graphicsCvars(GraphicsPreset.current(context)),
            )
            null
        },
        LabSetupStep("addon", ADDON_PATHS) { root ->
            AddonSetup.run(context, root).problem
        },
    )
}

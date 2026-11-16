package app.gamenative.lab

import android.content.Context
import app.gamenative.ui.screen.xserver.ControlsVisibilityPref
import com.winlator.container.ContainerManager
import com.winlator.container.ContainerOverlay
import com.winlator.core.FileUtils
import com.winlator.xenvironment.ImageFs
import org.json.JSONObject
import java.io.File

/**
 * Die zweite Umgebung `bnetlab`: eigener Container und Prefix (`imagefs/home/xuser-bnetlab`), getrennt vom WoW-Container. Die
 * Grafik-Einstellungen (Treiber, Wrapper, DXVK, Umgebung) werden aus der Datei `.container` des WoW-Containers nur **gelesen**; es wird
 * nie etwas in den WoW-Container oder den Spielordner geschrieben. Der Spielordner ist im Labor nicht eingebunden.
 */
object LabContainer {
    /** Schlüssel des WoW-Containers, die das Labor als Grundlage übernimmt (Grafik, Audio, Wine-Art). */
    /** Namen, wie sie in `Container.java` (`saveData`) im Container stehen. Die Test-Datei prüft sie gegen den Quelltext. */
    internal val COPY_KEYS = listOf(
        "graphicsDriver", "graphicsDriverVersion", "graphicsDriverConfig", "displayRendererMode", "rendererPresentMode",
        "dxwrapper", "dxwrapperConfig", "containerVariant", "wow64Mode", "wincomponents", "audioDriver", "startupSelection",
        "fexcoreVersion", "fexcorePreset", "box86Version", "box86Preset", "box64Version", "box64Preset",
        "cpuList", "cpuListWoW64", "steamType", "language", "lc_all", "inputType", "dinputMapperType", "desktopTheme",
        "sdlControllerAPI", "pulseaudioLowLatency", "useDRI3", "sfCompatMode",
    )

    /** Grundlage aus dem WoW-Container; null, wenn es ihn noch nicht gibt (dann fehlen auch die gebündelten Bausteine). */
    class Base(val json: JSONObject) {
        val envVars: String get() = json.optString("envVars", "")
        val wineVersion: String get() = json.optString("wineVersion", LabIds.FALLBACK_WINE)
    }

    fun readBase(context: Context): Base? {
        val file = File(LabDirs.wowContainerRoot(context), ".container")
        if (!file.isFile) return null
        return runCatching { Base(JSONObject(file.readText(Charsets.UTF_8))) }.getOrNull()
    }

    fun exists(context: Context): Boolean = File(LabDirs.containerRoot(context), ".container").isFile

    /** Windows-Laufwerk `L:` (der Installer liegt dort): Pfad im Spelling `/data/data/...`, wie der WoW-Container es für `E:` hat. */
    fun drivesText(context: Context): String =
        "${LabIds.SHARE_DRIVE}:${ContainerOverlay.dataDataSpelling(LabDirs.share(context).absolutePath)}"

    /**
     * `drives` des Labor-Containers: `L:` und, bei [mounted] (eine fertige, freigegebene Kopie, siehe [LabCopyMount]), das Laufwerk der Kopie.
     * Der Originalordner ist nie dabei. Pfad im selben Spelling wie bei `L:`.
     */
    fun drivesText(shareHost: String, mounted: String?): String =
        "${LabIds.SHARE_DRIVE}:${ContainerOverlay.dataDataSpelling(shareHost)}" +
            (mounted?.let { "${LabIds.COPY_DRIVE}:${ContainerOverlay.dataDataSpelling(it)}" } ?: "")

    /** Die Kopie, die dieser Start einbindet (null = keine). Prüft Schalter, Fertig-Marke und Abstand zum Original. */
    fun mountedCopy(context: Context, settings: LabSettings): File? = LabCopyMount.resolve(
        settings.useGameCopy,
        LabCopyInfo.parse(runCatching { LabDirs.gameCopyInfo(context).readText(Charsets.UTF_8) }.getOrNull()),
        LabDirs.gameCopyMount(context),
        LabOriginalCheck.originalFolder(context),
    )

    /**
     * Würde der Labor-Container den Originalordner erreichen? Wahr, wenn `drives` oder `envVars` ihn in irgendeiner Schreibweise nennen oder ein
     * Laufwerk (auch die beiden, die Wine beim Start von sich aus ergänzt: Downloads und `E:`) ihn enthält oder darin liegt.
     */
    internal fun leaksOriginal(drives: String, envVars: String, original: File, extraHosts: List<String> = defaultExtraHosts()): Boolean =
        LabCopyPaths.mentionsOriginal(drives, original) || LabCopyPaths.mentionsOriginal(envVars, original) ||
            (LabCopyPaths.driveHosts(drives) + extraHosts).any { LabCopyPaths.driveTouchesOriginal(it, original) }

    /** Die Laufwerke, die `WineUtils.createDosdevicesSymlinks` einem Container ohne `D:`/`E:` beim Start anfügt. */
    internal fun defaultExtraHosts(): List<String> = listOf(
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).path,
        "/data/data/app.wowforever/storage",
    )

    /** Die Felder des Labor-Containers aus Grundlage und Startplan. Rein bis auf die Eingaben, damit es testbar bleibt. */
    internal fun config(base: Base, plan: LabPlan, drives: String): JSONObject = JSONObject().apply {
        for (key in COPY_KEYS) if (base.json.has(key)) put(key, base.json.get(key))
        put("id", LabIds.CONTAINER_ID)
        put("name", "Battle.net Labor")
        put("screenSize", plan.screenSize)
        put("envVars", plan.envString)
        put("wineVersion", plan.wineVersion)
        put("emulator", plan.emulator)
        plan.fexVersion?.let { put("fexcoreVersion", it) }
        put("drives", drives)
        put("executablePath", plan.program)
        put("execArgs", plan.execArgs)
        put("showFPS", false)
        // Wie beim Spiel: im Hintergrund weiterlaufen (ein angehaltenes Battle.net bricht seine Verbindung ab).
        put("suspendPolicy", base.json.optString("suspendPolicy", "never"))
        // Ein normales Windows-Fenster: Tipp = Klick an der Stelle, keine Spielersteuerung, keine Schussansicht.
        put("touchscreenMode", true)
        put("shooterMode", false)
    }

    /**
     * Legt den Container an oder schreibt Startplan und Einstellungen in den vorhandenen. Wirft bei Fehlern. Der Prefix bleibt bei einem
     * zweiten Versuch erhalten (Battle.net bleibt installiert); nur "Labor löschen" entfernt ihn.
     */
    fun prepare(context: Context, base: Base, plan: LabPlan, mounted: File? = null) {
        LabDirs.share(context).mkdirs()
        LabDirs.logs(context).mkdirs()
        val drives = drivesText(LabDirs.share(context).absolutePath, mounted?.absolutePath)
        val data = config(base, plan, drives)
        LabOriginalCheck.originalFolder(context)?.let { original ->
            check(!leaksOriginal(data.optString("drives"), data.optString("envVars"), original)) { "The original game folder would be reachable from the lab" }
        }
        val manager = ContainerManager(context)
        val existing = manager.getContainerById(LabIds.CONTAINER_ID)
        if (existing == null) {
            val created = manager.createContainer(LabIds.CONTAINER_ID, data)
            check(created != null && manager.hasContainer(LabIds.CONTAINER_ID)) { "Lab container could not be created" }
        } else {
            existing.loadData(data)
            existing.saveData()
        }
        // Keine Bildschirmsteuerung über dem Fenster (nur der Finger als Zeiger und die Tastatur über das Menü).
        ControlsVisibilityPref.save(context, LabIds.CONTAINER_ID, false)
    }

    /** Bytes, die das Labor belegt: Prefix (ohne Verknüpfungen) und Ordner `files/bnetlab` mit Installer und Protokollen. */
    fun usedBytes(context: Context): Long = LabDirs.sizeOf(LabDirs.containerRoot(context)) + LabDirs.sizeOf(LabDirs.root(context))

    /** "Labor löschen": Prefix, Installer, Downloads und Protokolle. Der WoW-Container bleibt unberührt. */
    fun deleteAll(context: Context): Boolean {
        val c = LabDirs.containerRoot(context)
        require(c.name == "${ImageFs.USER}-${LabIds.CONTAINER_ID}") { "wrong folder" }
        val a = !c.exists() || FileUtils.delete(c)
        val b = !LabDirs.root(context).exists() || FileUtils.delete(LabDirs.root(context))
        return a && b
    }
}

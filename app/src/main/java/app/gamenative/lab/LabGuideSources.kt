package app.gamenative.lab

import android.content.Context
import com.winlator.contents.ContentsManager
import com.winlator.core.WineInfo
import java.io.File

/**
 * Die Dateifragen des Assistenten und des Selbsttests, gestellt von der App-Seite aus: **nur Existenz** (`File.isFile`, `isDirectory`), nie der
 * Inhalt einer Datei des Launchers, seiner Anmelde- oder Kontodateien. Läuft im Hintergrund (Dateizugriffe).
 */
data class LabFileFacts(
    val gameContainerPresent: Boolean,
    val installerFilePresent: Boolean,
    val launcherExePresent: Boolean,
    val prefixPresent: Boolean,
    val gameMarkerInCopy: Boolean,
    val gameMarkerInOriginal: Boolean,
    val freeBytes: Long,
)

object LabGuideSources {
    fun fileFacts(context: Context): LabFileFacts {
        val app = context.applicationContext
        val original = LabOriginalCheck.originalFolder(app)
        return LabFileFacts(
            gameContainerPresent = File(LabDirs.wowContainerRoot(app), ".container").isFile,
            installerFilePresent = File(LabDirs.share(app), LabIds.INSTALLER_FILE).isFile,
            launcherExePresent = LabProtocol.findBattleNetExe(LabDirs.driveC(app)) != null,
            prefixPresent = LabDirs.driveC(app).isDirectory,
            gameMarkerInCopy = File(LabDirs.gameCopyContent(app), LabGuide.GAME_MARKER).isFile,
            gameMarkerInOriginal = original?.let { File(it, LabGuide.GAME_MARKER).exists() } ?: false,
            freeBytes = freeBytes(LabDirs.root(app)),
        )
    }

    private fun freeBytes(dir: File): Long =
        generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() }
            ?.let { runCatching { android.os.StatFs(it.path).availableBytes }.getOrNull() } ?: 0L

    fun copyOf(view: LabCopyView?, running: Boolean): LabGuideCopy = LabGuideCopy(
        originalPresent = view?.originalPresent == true,
        running = running,
        complete = view?.complete == true,
        partial = view?.partial == true,
        layoutFailed = view?.layoutProblem != null,
        finishedMs = view?.info?.finishedMs ?: 0L,
        fits = view?.fits ?: true,
        neededBytes = view?.needed ?: 0L,
        freeBytes = view?.freeBytes ?: 0L,
    )

    fun guideInputs(
        facts: LabFileFacts,
        settings: LabSettings,
        installed: List<LabInstalledPackage>,
        packages: List<LabPackage>,
        self: LabGuideSelf,
        copy: LabGuideCopy,
        runs: List<LabRunEntry>,
    ): LabGuideInputs = LabGuideInputs(
        gameContainerPresent = facts.gameContainerPresent,
        fexListed = packages.any { it.kind == LabPackageKind.FEX },
        fexInstalled = LabQuickPicks.fex(installed, packages) != null,
        installerFilePresent = facts.installerFilePresent,
        launcherExePresent = facts.launcherExePresent,
        loginWayApplied = LabLoginWay.isAppliedCore(settings),
        useGameCopy = settings.useGameCopy,
        self = self,
        copy = copy,
        lastRun = LabGuide.lastLauncherRun(runs),
        gameMarkerInCopy = facts.gameMarkerInCopy,
        gameMarkerInOriginal = facts.gameMarkerInOriginal,
    )

    /**
     * Die Eingaben des Selbsttests. Ruft nur lesende Prüfungen auf: die Pakete laut `ContentsManager`, den Ordner des Wine-Pakets (Existenz), die
     * Prüfung der Laufwerke des nächsten Starts ([LabContainer.leaksOriginal], dieselbe wie beim Start) und den Stand der Originalprüfung.
     */
    fun setupInputs(
        context: Context,
        facts: LabFileFacts,
        settings: LabSettings,
        list: LabPackageList,
        installed: List<LabInstalledPackage>,
        copy: LabGuideCopy,
    ): LabSetupInputs {
        val app = context.applicationContext
        val base = LabContainer.readBase(app)
        val fexEntries = list.packages.filter { it.kind == LabPackageKind.FEX }
        val wineName = settings.wine.ifBlank { base?.wineVersion ?: LabIds.FALLBACK_WINE }
        val original = LabOriginalCheck.originalFolder(app)
        val mounted = LabContainer.mountedCopy(app, settings)
        return LabSetupInputs(
            gameContainerPresent = facts.gameContainerPresent,
            fexListed = fexEntries.isNotEmpty(),
            fexEntryComplete = fexEntries.any { it.sha256.isNotBlank() && it.bytes > 0L },
            listErrors = list.errors.size,
            listEntries = list.packages.size,
            fexInstalledName = LabQuickPicks.fex(installed, list.packages)?.verName,
            wineName = wineName,
            wineFolderPresent = wineFolderPresent(app, wineName),
            prefixPresent = facts.prefixPresent,
            launcherExePresent = facts.launcherExePresent,
            freeBytes = facts.freeBytes,
            useGameCopy = settings.useGameCopy,
            copy = copy,
            copyWouldMount = mounted != null,
            originalPresent = original != null,
            original = LabOriginalCheck.state(app),
            originalReachable = originalReachable(app, settings, base, original, mounted),
        )
    }

    private fun wineFolderPresent(context: Context, identifier: String): Boolean? = runCatching {
        val info = WineInfo.fromIdentifier(context, ContentsManager(context).apply { syncContents() }, identifier)
        info.path?.takeIf { it.isNotBlank() }?.let { File(it).isDirectory }
    }.getOrNull()

    private fun originalReachable(context: Context, settings: LabSettings, base: LabContainer.Base?, original: File?, mounted: File?): Boolean? {
        if (original == null || base == null) return null
        return runCatching {
            val plan = LabCommand.build(settings, LabTarget.BATTLENET, LabLauncher.context(context, base))
            LabContainer.leaksOriginal(LabContainer.drivesText(LabDirs.share(context).absolutePath, mounted?.absolutePath), plan.envString, original)
        }.getOrNull()
    }
}

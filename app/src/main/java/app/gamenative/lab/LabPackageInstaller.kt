package app.gamenative.lab

import android.content.Context
import android.net.Uri
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import timber.log.Timber
import java.io.File

/** Was nach dem Installieren eines Pakets herauskam. */
sealed class LabInstall {
    data class Installed(val kind: LabPackageKind, val verName: String, val verCode: Int) : LabInstall()
    data class Failed(val reason: ContentsManager.InstallFailedReason?, val wrongType: Boolean = false) : LabInstall()
}

/**
 * Installiert nachgeladene Pakete mit dem vorhandenen Paket-Installer der App (`ContentsManager`: Archiv entpacken, `profile.json`
 * lesen, Dateien prüfen, in den Paketordner verschieben). Im Labor sind nur Wine/Proton und FEX erlaubt; jede andere Art (Grafiktreiber,
 * DXVK ...) wird abgewiesen, damit das Labor das Spiel nicht verändern kann. Läuft im Hintergrund.
 */
object LabPackageInstaller {
    private val ALLOWED = setOf(
        ContentProfile.ContentType.CONTENT_TYPE_PROTON,
        ContentProfile.ContentType.CONTENT_TYPE_WINE,
        ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
    )

    /** Installiert ein `.wcp`-Paket aus [uri] (Datei im Download-Ordner des Labors oder von der Dateiauswahl). */
    fun installWcp(context: Context, uri: Uri): LabInstall {
        val cm = ContentsManager(context)
        var profile: ContentProfile? = null
        var failure: ContentsManager.InstallFailedReason? = null
        cm.extraContentFile(
            uri,
            object : ContentsManager.OnInstallFinishedCallback {
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                    failure = reason
                }

                override fun onSucceed(p: ContentProfile) {
                    profile = p
                }
            },
        )
        val extracted = profile ?: return LabInstall.Failed(failure)
        if (extracted.type !in ALLOWED) {
            ContentsManager.cleanTmpDir(context)
            return LabInstall.Failed(null, wrongType = true)
        }
        var finishFailure: ContentsManager.InstallFailedReason? = null
        var done = false
        cm.finishInstallContent(
            extracted,
            object : ContentsManager.OnInstallFinishedCallback {
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                    finishFailure = reason
                }

                override fun onSucceed(p: ContentProfile) {
                    done = true
                }
            },
        )
        if (!done) {
            ContentsManager.cleanTmpDir(context)
            return LabInstall.Failed(finishFailure)
        }
        cm.syncContents()
        val kind = if (extracted.type == ContentProfile.ContentType.CONTENT_TYPE_FEXCORE) LabPackageKind.FEX else LabPackageKind.WINE
        return LabInstall.Installed(kind, extracted.verName, extracted.verCode)
    }

    /**
     * Installiert ein FEX-Paket im Format `tzst` (zwei DLLs, kein `profile.json`): entpackt es in den Zwischenordner des Paket-Installers,
     * schreibt ein `profile.json` (Typ FEXCore, [LabPackages.fexProfileJson]) und übergibt es an `finishInstallContent`. Beim Start des
     * Labors kopiert die App die DLLs wie bei `fexcore-<ver>.tzst` nach `drive_c/windows/system32` des Labor-Prefix
     * (`BionicProgramLauncherComponent.extractEmulatorsDlls`, Name `lab-<ver>`). Das Spiel kennt diesen Namen nie.
     */
    fun installBareFex(context: Context, file: File, verName: String): LabInstall {
        val cm = ContentsManager(context)
        ContentsManager.cleanTmpDir(context)
        val tmp = ContentsManager.getTmpDir(context)
        if (!com.winlator.core.TarCompressorUtils.extract(com.winlator.core.TarCompressorUtils.Type.ZSTD, file, tmp)) {
            ContentsManager.cleanTmpDir(context)
            return LabInstall.Failed(ContentsManager.InstallFailedReason.ERROR_BADTAR)
        }
        // Die DLLs liegen im Wurzelordner (oder unter `./`); fehlt eine, ist es nicht das erwartete Paket.
        if (LabPackages.FEX_FILES.any { !File(tmp, it).isFile }) {
            ContentsManager.cleanTmpDir(context)
            return LabInstall.Failed(ContentsManager.InstallFailedReason.ERROR_MISSINGFILES)
        }
        val profileFile = File(tmp, ContentsManager.PROFILE_NAME)
        profileFile.writeText(LabPackages.fexProfileJson(verName), Charsets.UTF_8)
        val profile = cm.readProfile(profileFile)
        if (profile == null || profile.type != ContentProfile.ContentType.CONTENT_TYPE_FEXCORE) {
            ContentsManager.cleanTmpDir(context)
            return LabInstall.Failed(ContentsManager.InstallFailedReason.ERROR_BADPROFILE)
        }
        var finishFailure: ContentsManager.InstallFailedReason? = null
        var done = false
        cm.finishInstallContent(
            profile,
            object : ContentsManager.OnInstallFinishedCallback {
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                    finishFailure = reason
                }

                override fun onSucceed(p: ContentProfile) {
                    done = true
                }
            },
        )
        if (!done) {
            ContentsManager.cleanTmpDir(context)
            return LabInstall.Failed(finishFailure)
        }
        cm.syncContents()
        return LabInstall.Installed(LabPackageKind.FEX, profile.verName, profile.verCode)
    }

    /** Legt den geladenen Installer dorthin, wo ihn das Laufwerk `L:` zeigt (`files/bnetlab/share/Battle.net-Setup.exe`). */
    fun placeInstaller(context: Context, downloaded: File): File {
        val dir = LabDirs.share(context).apply { mkdirs() }
        val target = File(dir, LabIds.INSTALLER_FILE)
        downloaded.copyTo(target, overwrite = true)
        return target
    }

    /** Eine Zeile ins Paketprotokoll (kommt in den Labor-Bericht). Größe und SHA-256 stehen dort für jedes geladene oder gewählte Paket. */
    fun log(context: Context, line: String) {
        runCatching {
            val dir = LabDirs.logs(context).apply { mkdirs() }
            File(dir, LabReportSelect.INSTALL_LOG).appendText("${LabProtocol.timestamp(System.currentTimeMillis())} $line\n")
        }.onFailure { Timber.w(it, "Paketprotokoll nicht geschrieben") }
    }
}

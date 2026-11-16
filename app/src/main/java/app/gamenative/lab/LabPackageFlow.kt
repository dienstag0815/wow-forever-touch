package app.gamenative.lab

import android.content.Context
import android.net.Uri
import com.winlator.contents.ContentsManager
import timber.log.Timber
import java.io.File

/** Ausgang von "Laden und installieren" eines Pakets der Liste (die Seite übersetzt ihn in Text). */
sealed class LabFlow {
    data class Installed(val kind: LabPackageKind, val verName: String) : LabFlow()

    /** Das Paket war schon installiert: nichts wurde (erneut) geladen oder installiert. [name] ist der Versionsname oder der Name des Eintrags. */
    data class AlreadyInstalled(val kind: LabPackageKind?, val name: String) : LabFlow()

    /** Der Installer liegt bereit; [differs]: Größe oder SHA-256 weichen vom Eintrag ab (nur ein Hinweis). */
    data class InstallerSaved(val bytes: Long, val sha256: String, val differs: Boolean) : LabFlow()
    data class Rejected(val check: LabPackageCheck, val bytes: Long, val sha256: String) : LabFlow()
    data class InstallFailed(val failure: LabInstall.Failed) : LabFlow()
    data class DownloadFailed(val code: LabDownloader.DownloadException.Code?) : LabFlow()
    object Cancelled : LabFlow()
}

/** Ablauf für ein Paket der Liste und für eine gewählte Datei. Läuft im Hintergrund (Netz, Dateien); jede Datei wird protokolliert. */
object LabPackageFlow {
    /** Lädt [pkg] (nur https), prüft Größe und SHA-256, installiert es und räumt die geladene Datei weg (außer dem Installer, der nach `L:` kommt). */
    fun loadAndInstall(context: Context, pkg: LabPackage, isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit): LabFlow {
        val app = context.applicationContext
        // Schon installiert: nicht noch einmal laden (Netz, Platz) und nicht als Fehler melden.
        runCatching { LabPackages.installedMatch(pkg, LabLauncher.installedPackages(app)) }.getOrNull()?.let { have ->
            LabPackageInstaller.log(app, "skipped ${pkg.id}: already installed as ${have.kind.key} ${have.verName}-${have.verCode}")
            return LabFlow.AlreadyInstalled(have.kind, have.verName)
        }
        val target = File(LabDirs.downloads(app), LabPackages.fileName(pkg))
        val download = try {
            LabDownloader.download(pkg.url, target, pkg.bytes, isCancelled, onProgress)
        } catch (e: LabDownloader.DownloadException) {
            LabPackageInstaller.log(app, "download ${pkg.id} failed: ${e.code}")
            return if (e.code == LabDownloader.DownloadException.Code.CANCELLED) LabFlow.Cancelled else LabFlow.DownloadFailed(e.code)
        } catch (e: Exception) {
            LabPackageInstaller.log(app, "download ${pkg.id} failed: ${e.javaClass.simpleName}")
            return LabFlow.DownloadFailed(null)
        }
        val check = LabPackages.check(pkg, download.bytes, download.sha256)
        LabPackageInstaller.log(app, "downloaded ${pkg.id}: ${download.bytes} bytes, SHA-256 ${download.sha256}, check=${check.javaClass.simpleName}")
        val advisory = LabPackages.isAdvisory(pkg)
        if (!advisory && check != LabPackageCheck.Verified) {
            download.file.delete()
            return LabFlow.Rejected(check, download.bytes, download.sha256)
        }
        return try {
            when {
                pkg.kind == LabPackageKind.INSTALLER -> {
                    LabPackageInstaller.placeInstaller(app, download.file)
                    download.file.delete()
                    LabFlow.InstallerSaved(download.bytes, download.sha256, differs = check != LabPackageCheck.Verified && check != LabPackageCheck.Unverified)
                }
                LabPackages.isBareFex(pkg) -> finish(app, LabPackageInstaller.installBareFex(app, download.file, LabPackages.fexVerName(pkg)), download.file, pkg.id, pkg.name, pkg.kind)
                else -> finish(app, LabPackageInstaller.installWcp(app, Uri.fromFile(download.file)), download.file, pkg.id, pkg.name, pkg.kind)
            }
        } catch (e: Exception) {
            Timber.w(e, "Labor-Paket installieren")
            download.file.delete()
            LabFlow.InstallFailed(LabInstall.Failed(ContentsManager.InstallFailedReason.ERROR_UNKNOWN))
        }
    }

    /** [label] und [kind] benennen das Paket für die Meldung "schon installiert" (`ERROR_EXIST` des Paket-Installers: dieser Versionsname liegt schon da). */
    private fun finish(context: Context, result: LabInstall, file: File, id: String, label: String = id, kind: LabPackageKind? = null): LabFlow {
        file.delete()
        return when (result) {
            is LabInstall.Installed -> {
                LabPackageInstaller.log(context, "installed $id as ${result.kind.key} ${result.verName}-${result.verCode}")
                LabFlow.Installed(result.kind, result.verName)
            }
            is LabInstall.Failed -> {
                val reason = result.reason?.name
                LabPackageInstaller.log(context, LabPackages.installFailureLine(id, reason, result.wrongType))
                if (LabPackages.isAlreadyInstalledReason(reason)) LabFlow.AlreadyInstalled(kind, label) else LabFlow.InstallFailed(result)
            }
        }
    }

    /** Größe und SHA-256 einer gewählten Datei; null, wenn sie sich nicht lesen lässt. */
    fun hashFile(context: Context, uri: Uri): Pair<Long, String>? = runCatching {
        context.contentResolver.openInputStream(uri)?.let { LabDownloader.hash(it) }
    }.getOrNull()

    /**
     * Installiert eine gewählte Datei: erst als Paket mit `profile.json` (`.wcp`), fehlt es, als `tzst` mit den beiden FEX-DLLs. Die Datei
     * wird vorher mit Größe und SHA-256 ins Paketprotokoll geschrieben.
     */
    fun installFromFile(context: Context, uri: Uri, name: String, bytes: Long, sha256: String): LabFlow {
        val app = context.applicationContext
        LabPackageInstaller.log(app, "chosen file $name: $bytes bytes, SHA-256 $sha256")
        return try {
            val first = LabPackageInstaller.installWcp(app, uri)
            if (first is LabInstall.Failed && first.reason == ContentsManager.InstallFailedReason.ERROR_NOPROFILE) {
                val tmp = File(LabDirs.downloads(app).apply { mkdirs() }, "gewaehlt.tzst")
                app.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                finish(app, LabPackageInstaller.installBareFex(app, tmp, "lab-file-" + sha256.take(8)), tmp, "file:$name", name)
            } else {
                finish(app, first, File(app.cacheDir, "unused"), "file:$name", name)
            }
        } catch (e: Exception) {
            Timber.w(e, "Gewählte Datei installieren")
            LabFlow.InstallFailed(LabInstall.Failed(ContentsManager.InstallFailedReason.ERROR_UNKNOWN))
        }
    }
}

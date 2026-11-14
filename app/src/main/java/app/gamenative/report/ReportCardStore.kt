package app.gamenative.report

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import app.gamenative.R
import app.gamenative.ui.components.AppText
import app.gamenative.ui.util.SnackbarManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.File

/**
 * Hält die Karte "Bericht bereit" ([ReportCard]) für die Oberfläche und in `filesDir/meldekarte.txt`, damit sie die andere App und den
 * Neustart übersteht. Ein neuer Bericht ersetzt die Karte, [dismiss] entfernt sie.
 */
object ReportCardStore {
    private val state = MutableStateFlow<ReportCard?>(null)
    private var loaded = false

    private fun file(context: Context) = File(context.applicationContext.filesDir, ReportCardFile.FILE_NAME)

    /** Die Karte als Fluss; beim ersten Zugriff wird sie aus der Datei gelesen. */
    @Synchronized
    fun flow(context: Context): StateFlow<ReportCard?> {
        if (!loaded) {
            loaded = true
            state.value = ReportCardFile.load(file(context))
        }
        return state
    }

    @Synchronized
    fun set(context: Context, card: ReportCard) {
        loaded = true
        if (!ReportCardFile.save(file(context), card)) Timber.w("Meldekarte nicht gespeichert")
        state.value = card
    }

    @Synchronized
    fun dismiss(context: Context) {
        loaded = true
        ReportCardFile.clear(file(context))
        state.value = null
    }
}

/** Die vier Knöpfe der Karte "Bericht bereit" (Android-Teil; die Wahl des Browsers steht in [BrowserChoice]). */
object ReportCardActions {
    /** "In der App öffnen": der Standardweg, die App, die Android für den Link wählt (das kann die GitHub-App sein). */
    fun openIssue(context: Context, card: ReportCard): Boolean {
        val url = card.issueUrl ?: return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    /** Ein Link, den jeder Browser öffnet und keine Seiten-App für eine bestimmte Seite (zur Wahl des Browsers). */
    private const val PROBE_URL = "https://www.example.com/"

    private fun probe() = Intent(Intent.ACTION_VIEW, Uri.parse(PROBE_URL)).addCategory(Intent.CATEGORY_BROWSABLE)

    /** Der Browser, an den der Link gehen soll (siehe [BrowserChoice.pick]). */
    internal fun browserFor(context: Context): BrowserTarget {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_ALL
        val candidates = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.queryIntentActivities(probe(), PackageManager.ResolveInfoFlags.of(flags.toLong()))
            else @Suppress("DEPRECATION") pm.queryIntentActivities(probe(), flags)
        }.getOrDefault(emptyList()).map { it.activityInfo.packageName }
        val default = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.resolveActivity(probe(), PackageManager.ResolveInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.resolveActivity(probe(), 0)
        }.getOrNull()?.activityInfo?.packageName
        return BrowserChoice.pick(candidates, default, context.packageName)
    }

    /**
     * "Im Browser öffnen": gezielt ein Browser, nicht die App, die den Link abfängt. Gibt es keinen (oder schlägt es fehl), gilt der
     * Standardweg [openIssue].
     */
    fun openInBrowser(context: Context, card: ReportCard): Boolean {
        val url = card.issueUrl ?: return false
        val target = browserFor(context)
        if (target is BrowserTarget.Browser) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(target.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
            if (opened) return true
        }
        return openIssue(context, card)
    }

    /** Führt [action] mit der Zip aus; fehlt sie im Cache, wird der Bericht zuerst neu erstellt (die Karte zeigt dann auf die neue Zip). */
    private fun withZip(context: Context, card: ReportCard, onNote: (String) -> Unit, action: (Context, File) -> Unit) {
        if (ReportCardFile.zipExists(card)) {
            runCatching { action(context, File(card.zipPath)) }.onFailure { onNote(context.getString(R.string.game_er_failed)) }
        } else {
            ErrorReport.rebuild(context, card, onNote) { appContext, _, zip -> action(appContext, zip) }
        }
    }

    /** "Kurzfassung kopieren": die Kurzfassung aus der Zip in die Zwischenablage. */
    fun copySummary(context: Context, card: ReportCard, onNote: (String) -> Unit) = withZip(context, card, onNote) { appContext, zip ->
        val text = ReportCardFile.summaryFromZip(zip) ?: error("no summary in zip")
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WoW Forever report", text))
        appContext.getString(R.string.game_pm_card_copied).let { SnackbarManager.show(it); onNote(it) }
    }

    /** "Zip teilen": das Teilen-Menü; fehlt die Zip im Cache, wird der Bericht neu erstellt und danach geteilt. */
    fun shareZip(context: Context, card: ReportCard, onNote: (String) -> Unit) = withZip(context, card, onNote) { appContext, zip ->
        ErrorReport.share(appContext, zip)
    }

    /** "In Downloads zeigen": die Downloads-Ansicht der Dateien-App. Gelingt der Start nicht, sagt eine Meldung, wo man suchen soll. */
    fun showDownloads(context: Context, onNote: (String) -> Unit) {
        val opened = runCatching {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (!opened) {
            val text = AppText.get(R.string.game_pm_show_downloads_failed)
            SnackbarManager.show(text)
            onNote(text)
        }
    }

    /**
     * "Speichern unter ...": die Zip aus dem Cache in die vom Spieler gewählte Datei [target] kopieren. Fehlt die Zip, wird der Bericht
     * zuerst neu gebaut ([withZip]). Das Kopieren läuft im Hintergrund; die Meldung kommt danach im Hauptthread.
     */
    fun saveZipAs(context: Context, card: ReportCard, target: Uri, onNote: (String) -> Unit) = withZip(context, card, onNote) { appContext, zip ->
        val main = Handler(Looper.getMainLooper())
        Thread {
            val saved = runCatching {
                val out = appContext.contentResolver.openOutputStream(target) ?: error("no output stream")
                ReportZipCopy.copy(zip, out)
            }.onFailure { Timber.w(it, "Zip nicht gespeichert") }.isSuccess
            main.post {
                val text = AppText.get(if (saved) R.string.game_pm_saved_as else R.string.game_pm_save_failed)
                SnackbarManager.show(text)
                onNote(text)
            }
        }.start()
    }
}

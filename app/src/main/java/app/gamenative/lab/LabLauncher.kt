package app.gamenative.lab

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.utils.MachineIdentity
import app.gamenative.utils.MachineIdentityApply
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import timber.log.Timber
import java.io.File

/** Warum ein Versuch nicht beginnt (die Seite übersetzt das in einen Text). */
enum class LabFailure {
    /** Der WoW-Container fehlt: das Spiel wurde noch nie gestartet, die gebündelten Bausteine sind noch nicht eingerichtet. */
    NO_BASE,
    INSTALLER_MISSING,
    PREPARE_FAILED,

    /** Die Spielkopie wird gerade angelegt: kein Labor-Start, bis sie fertig oder abgebrochen ist. */
    COPY_RUNNING,
}

sealed class LabStart {
    data class Ready(val plan: LabPlan, val log: File) : LabStart()
    data class Blocked(val block: LabGuard.Block) : LabStart()

    /** Der letzte Lauf hat den Originalordner verändert und der Nutzer hat es noch nicht bestätigt: kein weiterer Start. */
    object OriginalChanged : LabStart()
    data class Invalid(val issues: List<LabIssue>) : LabStart()
    data class Failed(val failure: LabFailure, val detail: String = "") : LabStart()
}

/** Ein nachgeladenes Paket, das die App kennt (Wine oder FEX). */
data class LabInstalledPackage(val kind: LabPackageKind, val verName: String, val verCode: Int, val identifier: String?)

/** Bereitet einen Versuch vor: Sperre prüfen, Plan bauen, Container schreiben, Protokoll beginnen. Läuft im Hintergrund (Dateien). */
object LabLauncher {
    /** Nachgeladene Wine- und FEX-Pakete laut `ContentsManager` (das gebündelte Proton-Paket nicht). */
    fun installedPackages(context: Context): List<LabInstalledPackage> {
        val base = LabContainer.readBase(context)
        val bundled = (base?.wineVersion ?: LabIds.FALLBACK_WINE)
        val cm = ContentsManager(context).apply { syncContents() }
        val out = mutableListOf<LabInstalledPackage>()
        for (type in listOf(ContentProfile.ContentType.CONTENT_TYPE_PROTON, ContentProfile.ContentType.CONTENT_TYPE_WINE)) {
            for (p in cm.getProfiles(type).orEmpty()) {
                val id = LabPackages.wineIdentifier(p.verName, p.verCode)
                if (id != null && id == bundled) continue
                out += LabInstalledPackage(LabPackageKind.WINE, p.verName, p.verCode, id)
            }
        }
        for (p in cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE).orEmpty()) {
            out += LabInstalledPackage(LabPackageKind.FEX, p.verName, p.verCode, p.verName)
        }
        return out
    }

    fun context(context: Context, base: LabContainer.Base?): LabCommandContext {
        val installed = installedPackages(context)
        return LabCommandContext(
            baseEnv = base?.envVars.orEmpty(),
            battleNetExe = LabProtocol.findBattleNetExe(LabDirs.driveC(context)),
            bundledWine = base?.wineVersion ?: LabIds.FALLBACK_WINE,
            installedWine = installed.filter { it.kind == LabPackageKind.WINE }.mapNotNull { it.identifier }.toSet(),
            installedFex = installed.filter { it.kind == LabPackageKind.FEX }.map { it.verName }.toSet(),
            // Nur lesen: legt nie eine Kennung an. Ob sie verwendet wird, bestimmt der Schalter in den Einstellungen.
            identityHostname = runCatching { MachineIdentityApply.store(context).peek()?.computerName }.getOrNull()
                ?.takeIf { MachineIdentity.isValidHostname(it) },
        )
    }

    /** Prüft nur die Einstellungen (für die Anzeige auf der Seite), ohne etwas zu schreiben. */
    fun check(context: Context, settings: LabSettings, target: LabTarget): List<LabIssue> =
        LabCommand.build(settings, target, context(context, LabContainer.readBase(context))).issues

    /** Bereitet den Versuch vor; bei [LabStart.Ready] ruft der Aufrufer danach den normalen Start des X-Server-Bildschirms mit [LabIds.CONTAINER_ID]. */
    fun prepare(context: Context, target: LabTarget, origin: LabStartOrigin = LabStartOrigin.TEST_AREA): LabStart {
        val app = context.applicationContext
        val block = LabGuard.forLabStart(PluviaApp.keepAlive, LabRun.isActive())
        if (block != LabGuard.Block.NONE) return LabStart.Blocked(block)

        val base = LabContainer.readBase(app) ?: return LabStart.Failed(LabFailure.NO_BASE)
        val settings = LabStore.settings(app)
        val plan = LabCommand.build(settings, target, context(app, base))
        if (!plan.ok) return LabStart.Invalid(plan.issues)
        if (target == LabTarget.INSTALLER && !File(LabDirs.share(app), LabIds.INSTALLER_FILE).isFile) {
            return LabStart.Failed(LabFailure.INSTALLER_MISSING)
        }
        if (LabGameCopyJob.isRunning()) return LabStart.Failed(LabFailure.COPY_RUNNING)
        return try {
            // Ein offener Abdruck eines früheren Laufs (App dazwischen beendet) wird zuerst abgeschlossen; eine unbestätigte Abweichung sperrt.
            LabOriginalCheck.recover(app)
            if (LabOriginalCheck.blocksStart(app)) return LabStart.OriginalChanged
            val before = LabOriginalCheck.snapshot(app)
            if (target == LabTarget.STEP_TEST) LabStepTest.prepare(LabDirs.share(app))
            // Eine Kopie im alten Aufbau zieht vor dem Start in den Unterordner um; scheitert das, wird sie nicht eingebunden.
            LabGameCopyJob.ensureLayout(app)
            val mounted = LabContainer.mountedCopy(app, settings)
            // Spieleinstellungen und Addon wie beim normalen Spielstart, aber nur im Spielordner der Kopie (nie im Original); vor dem Abdruck
            // unten, damit der Abschnitt `== Game copy folder ==` nur zeigt, was der Lauf selbst ändert.
            val setup = LabGameSetup.run(
                mounted, LabDirs.gameCopyContent(app), LabOriginalCheck.originalFolder(app), LabGameSetup.steps(app, plan.screenSize),
            )
            // Abdruck des Kopie-Bereichs vor dem Start (liest nur), für den Abschnitt `== Game copy folder ==` am Ende.
            val copyBefore = LabCopyFolderCheck.snapshot(app)
            LabContainer.prepare(app, base, plan, mounted)
            val problem = LabGameCopyJob.layoutProblem?.let { "the copy could not be moved into the folder ${LabIds.COPY_GAME_DIR} (${it.reason.name.lowercase()})" }
            val log = LabRun.begin(app, settings, plan, LabCopyMount.protocolLine(mounted, LabGameCopyJob.info(app), problem), setup?.line(), startLines(app, target, origin))
            LabOriginalCheck.remember(app, before, log)
            LabCopyFolderCheck.remember(copyBefore, mounted != null, log)
            LabStart.Ready(plan, log)
        } catch (e: Exception) {
            Timber.w(e, "Labor-Start vorbereiten fehlgeschlagen")
            LabStart.Failed(LabFailure.PREPARE_FAILED, e.message.orEmpty())
        }
    }

    /** Die Protokollzeilen `Start mode:`, `Started from:` und `Guide part A/B done:` (nur beim Ziel Battle.net: der Weg, auf den sich die Startwahl bezieht). */
    private fun startLines(app: Context, target: LabTarget, origin: LabStartOrigin): List<String> {
        if (target != LabTarget.BATTLENET) return emptyList()
        val facts = LabStartSources.facts(app)
        return listOf(LabStartMode.modeLine(facts?.let { LabStartMode.effective(it) }), LabStartMode.originLine(origin)) + LabStartMode.guideLines(facts)
    }

    /** "Alles beenden": beendet Wine-Prozesse und Umgebung, aber nur wenn ein Labor-Versuch läuft oder gar nichts läuft (nie das Spiel). */
    fun killAll(): Boolean {
        val labActive = LabRun.isActive()
        if (!labActive && PluviaApp.keepAlive) return false
        LabRun.abort("stopped from the lab page")
        runCatching { PluviaApp.shutdownEnvironment() }.onFailure { Timber.w(it, "shutdownEnvironment") }
        runCatching { com.winlator.core.ProcessHelper.killAllWineProcesses() }.onFailure { Timber.w(it, "killAllWineProcesses") }
        return true
    }
}

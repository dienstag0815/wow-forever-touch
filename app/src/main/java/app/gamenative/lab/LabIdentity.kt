package app.gamenative.lab

import android.content.Context
import app.gamenative.utils.MachineIdentity
import app.gamenative.utils.MachineIdentityApply
import app.gamenative.utils.MachineIdentityLog
import com.winlator.container.Container
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * Gleiche Windows-Kennung wie das Spiel: Battle.net soll im Labor dasselbe "Gerät" sehen wie das Spiel. Die gespeicherte Kennung der
 * App ([MachineIdentity], vom Spiel im WoW-Prefix angewendet) wird mit demselben Mechanismus ([MachineIdentityApply.applyToPrefix]) auch
 * auf den Labor-Prefix angewendet. Der WoW-Prefix und die gespeicherte Kennung werden nur gelesen. Im Protokoll stehen nur die
 * Kurzkennungen (8 Hex) und je Merkmal "gleich ja/nein", nie ein Wert. Der Teil ohne Android ist JUnit-getestet.
 */
object LabIdentity {
    /** Die Merkmale, die [MachineIdentityApply] in einen Prefix schreibt (Reihenfolge wie dort). */
    val FEATURES = listOf("machineGuid", "computerName", "hostname", "installDate", "productId", "volumeSerial")

    /** Ergebnis für das Protokoll: Kurzkennungen und die Merkmale, die im Labor-Prefix (noch) von der Kennung abweichen. */
    data class Outcome(
        val enabled: Boolean,
        val hasIdentity: Boolean,
        val gameFingerprint: String?,
        val labFingerprint: String?,
        val deviations: List<String>,
    )

    /** Welche Merkmale werden in den Labor-Prefix übernommen: alle, wenn der Schalter an ist und eine gespeicherte Kennung existiert, sonst keines. */
    fun adopted(enabled: Boolean, hasIdentity: Boolean): List<String> = if (enabled && hasIdentity) FEATURES else emptyList()

    /**
     * Kurzkennung des Labor-Prefix: stimmen alle Merkmale mit der Kennung überein, dieselbe wie die des Spiels; sonst ein anderer Wert, der
     * aus der Kurzkennung und den abweichenden Merkmalsnamen gebildet wird (kein Wert der Kennung geht ein).
     */
    fun labFingerprint(gameFingerprint: String, deviations: List<String>): String {
        if (deviations.isEmpty()) return gameFingerprint
        val digest = MessageDigest.getInstance("SHA-256").digest("$gameFingerprint|lab|${deviations.sorted().joinToString(",")}".toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** Die Zeilen des Protokollabschnitts "Windows identity" (nur Kurzkennungen, Ja/Nein; keine Werte). */
    fun protocolLines(o: Outcome): List<String> = buildList {
        add("Same Windows identity as the game: ${if (o.enabled) "on" else "off"}")
        if (!o.hasIdentity || o.gameFingerprint == null) {
            add("Stored identity of the app: none (nothing applied)")
            return@buildList
        }
        add("Game identity: ${o.gameFingerprint}")
        add("Lab identity: ${o.labFingerprint ?: "unknown"}")
        add("Identical: ${if (o.deviations.isEmpty()) "yes" else "no"}")
        for (f in FEATURES) add("  $f: ${if (f in o.deviations) "no" else "yes"}")
    }

    /**
     * Wendet die Kennung (falls [enabled]) auf [wineDir] des Labor-Prefix an und misst danach, was von ihr abweicht. Schreibt nur in den
     * Labor-Prefix. [identity] null: keine gespeicherte Kennung (es wird keine neue angelegt).
     */
    fun applyAndMeasure(wineDir: File, identity: MachineIdentity?, enabled: Boolean): Outcome {
        if (identity == null) return Outcome(enabled, false, null, null, emptyList())
        if (enabled) MachineIdentityApply.applyToPrefix(wineDir, identity)
        val deviations = MachineIdentityApply.deviations(wineDir, identity)
        val game = MachineIdentityLog.fingerprint(identity)
        return Outcome(enabled, true, game, labFingerprint(game, deviations), deviations)
    }
}

/** Einhängestelle 4 in `XServerScreen` (nach der Kennung des Spiels): wendet die Kennung auf den Labor-Prefix an und schreibt das Ergebnis ins Protokoll. */
object LabIdentityHook {
    fun apply(context: Context, container: Container?) {
        if (container == null || container.id != LabIds.CONTAINER_ID) return
        try {
            // X11-Schalter des Labors (Eingabe-Versuche): gleiche Einhängestelle, eigener Abschnitt im Protokoll; Vorgabe schreibt nichts.
            LabX11Hook.apply(container, LabStore.settings(context).x11)
            val enabled = LabStore.settings(context).sameIdentity
            // peek: legt nie eine neue Kennung an (das darf nur der Spielstart).
            val identity = MachineIdentityApply.store(context).peek()
            val outcome = LabIdentity.applyAndMeasure(File(container.rootDir, ".wine"), identity, enabled)
            LabRun.appendSection(container.id, "Windows identity", LabIdentity.protocolLines(outcome))
        } catch (e: Exception) {
            Timber.w("Lab identity not applied: %s", e.javaClass.simpleName)
        }
    }
}

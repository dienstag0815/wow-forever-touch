package app.gamenative.utils

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Sicherungskopie der Windows-Kennung im Spielordner (rein, ohne Android, per JUnit geprüft).
 *
 * Der Spielordner bleibt beim Deinstallieren der App liegen; eine Kopie der Kennung dort überlebt so eine Neuinstallation. Die Datei
 * enthält die Kennung im Klartext (jede App mit Dateizugriff kann sie lesen), dazu ein Gerätemerkmal (8 Hex über Hersteller und Modell,
 * sonst nichts vom Gerät) und eine Prüfsumme über den Inhalt. Sie steht nie in Backup, Log, Fehlerbericht oder Verlaufsdatei.
 *
 * Außerdem: die Liste der Kurzkennungen, die schon für einen Spielstart angewendet wurden (App-Speicher), damit eine noch nie benutzte
 * neue Kennung durch eine später auftauchende Kopie ersetzt werden darf.
 */
object MachineIdentityKeep {
    /** Name der Kopie im Spielordner, neben der Verlaufsdatei. */
    const val FILE_NAME = "wowforever-kennung-kopie.key"

    /** Marker im App-Speicher: Datei da = Schalter "Kennung im Spielordner sichern" ist aus. */
    const val OFF_FILE_NAME = "wow_identity_keep_off"

    /** Kurzkennungen, die schon für einen Spielstart angewendet wurden (App-Speicher, nie im Backup). */
    const val USED_FILE_NAME = "wow_identity_used.txt"

    private const val HEADER = "wowforever-kennung-kopie 1"
    private const val USED_MAX = 20

    private val DEVICE = Regex("^[0-9a-f]{8}$")
    private val CHECK = Regex("^[0-9a-f]{16}$")
    private val FINGERPRINT = Regex("^[0-9a-f]{8}$")

    /** Gerätemerkmal: erste 8 Hex-Zeichen von SHA-256 über `hersteller|modell` (klein, getrimmt). Nichts weiter vom Gerät. */
    fun deviceTrait(manufacturer: String?, model: String?): String {
        val text = (manufacturer.orEmpty().trim() + "|" + model.orEmpty().trim()).lowercase(Locale.ROOT)
        return hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)), 4)
    }

    private fun hex(bytes: ByteArray, count: Int): String {
        val out = StringBuilder(count * 2)
        for (i in 0 until count) out.append("%02x".format(Locale.ROOT, bytes[i].toInt() and 0xff))
        return out.toString()
    }

    /** Das, worüber die Prüfsumme gebildet wird: Kopf, Kennung (kanonisch) und Gerätemerkmal. */
    private fun body(identity: MachineIdentity, trait: String): String = HEADER + "\n" + identity.serialize() + "device=" + trait + "\n"

    private fun check(identity: MachineIdentity, trait: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest(body(identity, trait).toByteArray(StandardCharsets.UTF_8)), 8)

    fun serialize(identity: MachineIdentity, trait: String): String = body(identity, trait) + "check=" + check(identity, trait) + "\n"

    /** Ergebnis von [parse]; die Gründe sind feste Wörter, nie ein Wert. */
    sealed class Reading {
        class Valid(val identity: MachineIdentity, val trait: String) : Reading()
        class Invalid(val reason: String) : Reading()
    }

    /** Gründe in [Reading.Invalid]; stehen im Protokoll. */
    val REASONS: Set<String> = setOf("kopf", "kennung_ungueltig", "geraet", "pruefsumme", "lesefehler")

    fun parse(text: String?): Reading {
        if (text == null) return Reading.Invalid("lesefehler")
        if (text.lines().firstOrNull()?.trim() != HEADER) return Reading.Invalid("kopf")
        val values = MachineIdentity.readValues(text)
        val identity = MachineIdentity.parse(text) ?: return Reading.Invalid("kennung_ungueltig")
        val trait = values["device"]?.takeIf { DEVICE.matches(it) } ?: return Reading.Invalid("geraet")
        val sum = values["check"]?.takeIf { CHECK.matches(it) } ?: return Reading.Invalid("pruefsumme")
        if (sum != check(identity, trait)) return Reading.Invalid("pruefsumme")
        return Reading.Valid(identity, trait)
    }

    // --- Angewendete Kennungen ---

    /** Die Kurzkennungen aus dem Text der Datei; null, wenn es sie nicht gibt (dann ist nichts über die Benutzung bekannt). */
    fun parseUsed(text: String?): List<String>? =
        text?.lines()?.map { it.trim() }?.filter { FINGERPRINT.matches(it) }?.distinct()?.takeLast(USED_MAX)

    fun serializeUsed(used: List<String>): String = used.distinct().takeLast(USED_MAX).joinToString("") { it + "\n" }

    /**
     * True, wenn die Kennung schon für einen Spielstart angewendet wurde. Ohne Datei ([used] null) ist nichts bekannt, und dann
     * gilt sie als benutzt: eine Kennung mit unbekannter Vorgeschichte wird nie ersetzt.
     */
    fun isUsed(used: List<String>?, fingerprint: String): Boolean = used == null || fingerprint in used

    /** Zustand der Kopie im Spielordner für die Karte. */
    enum class Status {
        /** Der Schalter ist aus. */
        OFF,

        /** Der Spielordner ist nicht lesbar oder nicht da. */
        NO_ACCESS,

        /** Keine Kopie da. */
        NONE,

        /** Kopie da und gleich der aktiven Kennung. */
        MATCHES,

        /** Gültige Kopie einer anderen Kennung. */
        OTHER_IDENTITY,

        /** Gültige Kopie vom anderen Modell. */
        OTHER_MODEL,

        /** Kopie vorhanden, aber unbrauchbar (Prüfsumme, Kopf, Kennung). */
        DAMAGED,
    }
}

package app.gamenative.report

import java.io.File

/**
 * Liest Rechnerkennung (MachineGuid) und Rechnernamen aus der Wine-Registry (`system.reg`), damit der Bericht sie
 * schwärzen kann, ohne von der Klasse abzuhängen, die sie schreibt. Reine Kotlin-Klasse, JUnit-getestet.
 */
object RegistryValues {
    private val MACHINE_GUID = Regex("""^"MachineGuid"="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val COMPUTER_NAME = Regex("""^"(?:ActiveComputerName|ComputerName)"="([^"]*)"""", RegexOption.IGNORE_CASE)

    /** MachineGuid und Rechnernamen, die im Text von `system.reg` stehen (leere Werte entfallen, ohne Doppelte). */
    fun secretsFrom(lines: Sequence<String>): Set<String> {
        val found = linkedSetOf<String>()
        for (line in lines) {
            val m = MACHINE_GUID.find(line) ?: COMPUTER_NAME.find(line) ?: continue
            m.groupValues[1].takeIf { it.isNotBlank() }?.let { found += it }
        }
        return found
    }

    /** Ob die Registry eine MachineGuid enthält (für die Zeile "gesetzt ja/nein" in geraet.txt). */
    fun hasMachineGuid(lines: Sequence<String>): Boolean = lines.any { MACHINE_GUID.containsMatchIn(it) }

    /** Die Werte aus einer `system.reg`-Datei; fehlt sie oder ist sie nicht lesbar, ist das Ergebnis leer. */
    fun secretsFromFile(systemReg: File): Set<String> =
        runCatching { systemReg.useLines(Charsets.UTF_8) { secretsFrom(it) } }.getOrDefault(emptySet())

    fun hasMachineGuidInFile(systemReg: File): Boolean =
        runCatching { systemReg.useLines(Charsets.UTF_8) { hasMachineGuid(it) } }.getOrDefault(false)
}

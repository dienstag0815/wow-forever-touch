package app.gamenative.lab

/**
 * Obergrenze für Wines Ausgabe je Lauf: der Anfang ([headBytes]) und das Ende ([tailBytes]) bleiben, die Mitte entfällt und wird
 * vermerkt. Rein, ohne Android, JUnit-getestet. [add] gibt die Zeile zurück, die sofort in die Datei darf (solange der Anfang noch nicht
 * voll ist); alles danach liegt im Ringspeicher für das Ende, [tailText] liefert ihn beim Abschluss.
 */
class LabOutputCap(
    private val headBytes: Long = DEFAULT_HEAD_BYTES,
    private val tailBytes: Long = DEFAULT_TAIL_BYTES,
) {
    private var headUsed = 0L
    private var headFull = false
    private val tail = ArrayDeque<String>()
    private var tailUsed = 0L

    /** Zeilen und Bytes, die in der Mitte entfallen sind. */
    var omittedLines = 0L
        private set
    var omittedBytes = 0L
        private set

    /** Anzahl der Zeilen, die insgesamt angenommen wurden. */
    var totalLines = 0L
        private set

    /** Gibt die Zeile für den Anfang der Datei zurück oder null, wenn sie in den Speicher für das Ende ging. */
    fun add(rawLine: String): String? {
        totalLines++
        val line = if (rawLine.length > MAX_LINE) rawLine.take(MAX_LINE) + " ...(line cut)" else rawLine
        val size = line.length + 1L
        if (!headFull) {
            if (headUsed + size <= headBytes) {
                headUsed += size
                return line
            }
            headFull = true
        }
        tail.addLast(line)
        tailUsed += size
        while (tailUsed > tailBytes && tail.size > 1) {
            val dropped = tail.removeFirst()
            tailUsed -= dropped.length + 1L
            omittedLines++
            omittedBytes += dropped.length + 1L
        }
        return null
    }

    /** Der Text für das Ende der Ausgabe: bei gekürzter Mitte erst der Vermerk, dann die letzten Zeilen. Leer, wenn nichts im Speicher liegt. */
    fun tailText(): String {
        if (tail.isEmpty()) return ""
        return buildString {
            if (omittedLines > 0) {
                append("(middle of the Wine output cut: ").append(omittedLines).append(" lines, ").append(omittedBytes / 1024)
                    .append(" kB omitted; kept the first ").append(headBytes / 1024).append(" kB and the last ").append(tailBytes / 1024)
                    .append(" kB)\n")
            }
            for (l in tail) append(l).append('\n')
        }
    }

    companion object {
        const val DEFAULT_HEAD_BYTES = 2_000_000L
        const val DEFAULT_TAIL_BYTES = 1_000_000L

        /** Eine einzelne Zeile wird bei dieser Länge gekürzt (ein Fehler, der Megabytes ohne Zeilenumbruch ausgibt). */
        const val MAX_LINE = 4000
    }
}

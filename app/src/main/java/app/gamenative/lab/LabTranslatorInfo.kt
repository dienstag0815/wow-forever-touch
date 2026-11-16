package app.gamenative.lab

/**
 * Zieht aus Wines Ausgabe, was der Übersetzer über seine Einstellungen meldet: den **ersten** Block `[BOX64] BOX64ENV: Variables overridden:`
 * (die Zeilen danach, eingerückt, `NAME=wert`) und die ersten Meldungen von FEX. Rein, ohne Android, JUnit-getestet. Box64 listet in dem Block nur
 * Werte, die von seinen eigenen Vorgaben abweichen.
 */
class LabTranslatorCapture {
    private val box = mutableListOf<String>()
    private var boxState = State.WAITING
    private var boxCut = false
    private val fex = mutableListOf<String>()
    private var fexCut = false

    private enum class State { WAITING, IN_BLOCK, DONE }

    /** Der erste Block von Box64 samt Kopfzeile (höchstens [MAX_LINES] Zeilen, die letzte vermerkt die Kürzung); leer, wenn keiner kam. */
    val boxBlock: List<String> get() = if (boxCut) box.take(MAX_LINES - 1) + CUT_NOTE else box.toList()

    /** Die ersten Meldungen von FEX (höchstens [MAX_LINES] Zeilen); leer, wenn keine kam. */
    val fexMessages: List<String> get() = if (fexCut) fex.take(MAX_LINES - 1) + CUT_NOTE else fex.toList()

    fun add(rawLine: String) {
        when (boxState) {
            State.WAITING -> if (rawLine.contains(BOX_HEAD)) {
                box += rawLine.trimEnd().take(MAX_LINE)
                boxState = State.IN_BLOCK
                return
            }
            State.IN_BLOCK -> {
                if (rawLine.isNotEmpty() && rawLine[0].isWhitespace() && rawLine.contains('=')) {
                    if (box.size >= MAX_LINES) boxCut = true else box += rawLine.trim().take(MAX_LINE)
                    return
                }
                boxState = State.DONE
            }
            State.DONE -> {}
        }
        if (fex.size < MAX_LINES + 1 && isFexMessage(rawLine)) {
            if (fex.size >= MAX_LINES) fexCut = true else fex += rawLine.trimEnd().take(MAX_LINE)
        }
    }

    companion object {
        const val MAX_LINES = 30
        private const val MAX_LINE = 300
        const val BOX_HEAD = "BOX64ENV: Variables overridden"
        const val CUT_NOTE = "(cut: more lines follow)"

        /** Zeilen von Wines Ablaufverfolgung (`trace:`, `warn:`, `fixme:`) nennen FEX nur als Namen einer Umgebungsvariablen: keine Meldung des Übersetzers. */
        private val WINE_TRACE = Regex("^(\\d+\\.\\d+:)?[0-9a-fA-F]{4,8}:(trace|warn|fixme):")

        fun isFexMessage(line: String): Boolean = line.contains("fex", ignoreCase = true) && !WINE_TRACE.containsMatchIn(line)

        /** Eine ganze Ausgabe auswerten (für Tests). */
        fun scan(lines: Iterable<String>): LabTranslatorCapture = LabTranslatorCapture().also { c -> lines.forEach { c.add(it) } }

        /**
         * Was von den gewünschten Zeilen `NAME=wert` im Block steht: `listed` (genau so), `differs` (anderer Wert) oder `not listed` (Box64 nennt nur
         * Abweichungen von seinen Vorgaben: gleich der Vorgabe oder nicht übernommen, das sagt der Block allein nicht).
         */
        fun check(requested: List<Pair<String, String>>, block: List<String>): List<String> {
            val shown = block.drop(1).mapNotNull { l ->
                val t = l.trim()
                val eq = t.indexOf('=')
                if (eq <= 0) null else t.substring(0, eq) to t.substring(eq + 1)
            }.toMap()
            return requested.map { (k, v) ->
                val got = shown[k]
                val verdict = when {
                    got == null -> "not listed"
                    got == v -> "listed"
                    else -> "differs (block shows $got)"
                }
                "  $k=$v: $verdict"
            }
        }
    }
}

/** Der Abschnitt `== Translator ==` des Protokolls (rein). */
object LabTranslatorReport {
    /**
     * Die Zeilen des Abschnitts: Übersetzer und Stufe, was das Labor angefordert hat, dann was der Übersetzer gemeldet hat. Bei Box64 der erste Block samt
     * Abgleich, bei FEX die ersten Meldungen, ohne Übersetzer ein Hinweis.
     */
    fun lines(plan: LabPlan, capture: LabTranslatorCapture): List<String> {
        val out = mutableListOf<String>()
        out += "Emulator: ${plan.emulator}${plan.fexVersion?.let { " (FEX $it)" } ?: ""}"
        out += "Stability: ${plan.stability.level} ${plan.stability.englishName}"
        val requested = LabStabilities.envLines(plan.stability)
        out += "Requested by the lab (stage lines): " + if (requested.isEmpty()) "(none)" else requested.joinToString(" ") { "${it.first}=${it.second}" }
        out += "Translator log: ${if (plan.translatorLog) "on (BOX64_LOG=1, BOX64_DYNAREC_MISSING=1)" else "off"}"
        when (plan.emulator) {
            LabTranslatorChoice.EMULATOR_FEX -> {
                val msgs = capture.fexMessages
                out += "-- First FEX messages in the Wine output: ${if (msgs.isEmpty()) "(none found)" else msgs.size}"
                out += msgs
            }
            LabTranslatorChoice.EMULATOR_NONE -> out += "(no translator chosen: nothing to report)"
            else -> {
                val block = capture.boxBlock
                out += "-- First Box64 block in the Wine output (Box64 lists only values that differ from its own defaults): " +
                    if (block.isEmpty()) "(none found)" else "${block.size} lines"
                out += block
                val boxLines = requested.filter { it.first.startsWith("BOX64_") }
                if (block.isNotEmpty() && boxLines.isNotEmpty()) {
                    out += "-- Requested lines against the block:"
                    out += LabTranslatorCapture.check(boxLines, block)
                }
            }
        }
        return out
    }
}

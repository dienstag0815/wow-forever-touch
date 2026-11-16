package app.gamenative.lab

import app.gamenative.report.ReportRedactor
import java.io.File

/** Wie weit der Stufentest gekommen ist, aus `labtest.txt` gelesen. */
data class LabStepResult(
    /** Höchste Stufe, deren Anfangszeile (`STEP n begin`) in der Datei steht; 0 = keine. */
    val lastBegun: Int,
    /** Höchste Stufe, deren Ergebniszeile (`STEP n result`) in der Datei steht; 0 = keine. */
    val lastCompleted: Int,
    /** Die Schlusszeile `STEP 6 done` steht in der Datei (alle Stufen gelaufen). */
    val finished: Boolean,
    /** Stufe 5 wurde übersprungen, weil der Installer fehlt. */
    val installerMissing: Boolean,
)

/**
 * Der Stufentest: eine Batch-Datei (`labtest.cmd`) im Laufwerk `L:`, die vor jedem Schritt eine Zeile nach `L:\labtest.txt` schreibt und
 * danach das Ergebnis. So zeigt ein einziger Lauf, an welcher Schicht es hängt: Wine-Start, Laufwerk `L:`, Hilfsprogramme im Prefix,
 * 32-Bit-Übersetzer, Installer. Rein (Text), JUnit-getestet; die Datei schreibt [prepare].
 */
object LabStepTest {
    const val CMD_EXE = "C:\\windows\\system32\\cmd.exe"
    const val BATCH_WIN_PATH = "L:\\" + LabIds.STEP_TEST_BATCH
    const val RESULT_WIN_PATH = "L:\\" + LabIds.STEP_TEST_RESULT
    val CMD_ARGS = listOf("/c", BATCH_WIN_PATH)

    /** Eingebaute 32-Bit-Datei: `syswow64` ist im Prefix-Muster und im Proton-Paket (`i386-windows`) vorgesehen. */
    const val X86_32_PROGRAM = "C:\\windows\\syswow64\\cmd.exe"

    private const val WINHANDLER = "C:\\windows\\winhandler.exe"
    private const val WFM = "C:\\windows\\wfm.exe"
    private const val INSTALLER = "L:\\" + LabIds.INSTALLER_FILE

    /** Obergrenze beim Einlesen in das Protokoll. */
    const val MAX_READ_BYTES = 64 * 1024

    private val STEP_LINE = Regex("STEP ([1-6]) (begin|result|done|SKIPPED)\\b.*")

    /** Inhalt der Batch-Datei, Zeilenenden CRLF, nur ASCII. Alle Pfade wörtlich. */
    fun batchText(): String {
        val r = RESULT_WIN_PATH
        val lines = listOf(
            "@echo off",
            ">$r echo STEP 1 begin: start %DATE% %TIME%",
            ">>$r echo STEP 1 result: batch is running",
            ">>$r echo STEP 2 begin: dir L:\\",
            ">>$r dir L:\\ 2>&1",
            ">>$r echo STEP 2 result: listing done",
            ">>$r echo STEP 3 begin: winhandler.exe and wfm.exe in C:\\windows (dir only, not started)",
            "if exist $WINHANDLER (>>$r echo winhandler.exe: found) else (>>$r echo winhandler.exe: MISSING)",
            "if exist $WFM (>>$r echo wfm.exe: found) else (>>$r echo wfm.exe: MISSING)",
            ">>$r dir $WINHANDLER $WFM 2>&1",
            ">>$r echo STEP 3 result: check done",
            ">>$r echo STEP 4 begin: 32-bit program $X86_32_PROGRAM",
            ">>$r $X86_32_PROGRAM /c echo x86-32 ok 2>&1",
            ">>$r echo STEP 4 result: exit code %ERRORLEVEL%",
            "if not exist $INSTALLER goto noinstaller",
            ">>$r echo STEP 5 begin: start /wait $INSTALLER",
            "start \"\" /wait \"$INSTALLER\"",
            ">>$r echo STEP 5 result: exit code %ERRORLEVEL%",
            ">>$r echo STEP 6 done",
            "goto end",
            ":noinstaller",
            ">>$r echo STEP 5 SKIPPED: $INSTALLER is missing, test ends after step 4",
            ":end",
        )
        return lines.joinToString("\r\n", postfix = "\r\n")
    }

    /** Liest aus dem Text von `labtest.txt`, wie weit der Test kam. */
    fun evaluate(text: String?): LabStepResult {
        var begun = 0
        var completed = 0
        var finished = false
        var skipped = false
        for (raw in text.orEmpty().lineSequence()) {
            val m = STEP_LINE.matchEntire(raw.trim()) ?: continue
            val n = m.groupValues[1].toInt()
            when (m.groupValues[2]) {
                "begin" -> begun = maxOf(begun, n)
                "result" -> {
                    completed = maxOf(completed, n)
                    begun = maxOf(begun, n)
                }
                "done" -> if (n == 6) finished = true
                "SKIPPED" -> if (n == 5) skipped = true
            }
        }
        return LabStepResult(begun, completed, finished, skipped)
    }

    /** Eine Zeile für die Seite und das Protokoll. */
    fun summary(result: LabStepResult): String = when {
        result.finished -> "Step test: all 6 steps ran to the end"
        result.installerMissing -> "Step test: ended after step ${result.lastCompleted} (installer missing)"
        result.lastBegun == 0 -> "Step test: no step reached (no result file, or the batch never ran)"
        else -> "Step test: last step reached ${result.lastBegun} of 6, finished up to ${result.lastCompleted}"
    }

    /** Schreibt die Batch-Datei in den Ordner von `L:` und entfernt eine alte Ergebnisdatei. Wirft bei Schreibfehlern. */
    fun prepare(shareDir: File) {
        shareDir.mkdirs()
        File(shareDir, LabIds.STEP_TEST_RESULT).delete()
        File(shareDir, LabIds.STEP_TEST_BATCH).writeBytes(batchText().toByteArray(Charsets.US_ASCII))
    }

    /** Der Inhalt von `labtest.txt` (höchstens [MAX_READ_BYTES]) ohne Schwärzung; null, wenn es die Datei nicht gibt. */
    private fun readRaw(shareDir: File): String? {
        val f = File(shareDir, LabIds.STEP_TEST_RESULT)
        if (!f.isFile) return null
        val buffer = ByteArray(MAX_READ_BYTES)
        val n = f.inputStream().use { it.readNBytes(buffer, 0, MAX_READ_BYTES) }
        val cut = f.length() > MAX_READ_BYTES
        return String(buffer, 0, n, Charsets.UTF_8) + if (cut) "\n(cut off at ${MAX_READ_BYTES / 1024} kB)\n" else ""
    }

    /** Der Inhalt von `labtest.txt` (höchstens [MAX_READ_BYTES]) durch den [ReportRedactor]; null, wenn es die Datei nicht gibt. */
    fun readResult(shareDir: File, redactor: ReportRedactor = ReportRedactor()): String? = readRaw(shareDir)?.let { redactor.redact(it) }

    /** Die letzte erreichte Stufe des jüngsten Stufentests für die Labor-Seite; null, wenn es kein Ergebnis gibt. */
    fun lastResult(shareDir: File): LabStepResult? = readRaw(shareDir)?.let { evaluate(it) }
}

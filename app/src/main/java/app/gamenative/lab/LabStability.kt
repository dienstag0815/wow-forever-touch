package app.gamenative.lab

/** Die sechs Stufen der Stabilitäts-Schnellwahl des Labors. [level] ist die Zahl im Protokoll und auf der Seite, [englishName] der Name im Protokoll. */
enum class LabStability(val level: Int, val key: String, val englishName: String) {
    OFF(0, "off", "Off"),
    CAREFUL(1, "careful", "Careful"),
    CAREFUL_NO_JIT(2, "careful_nojit", "Careful, no JIT"),
    SAFEST(3, "safest", "Safest"),
    FEX(4, "fex", "FEX"),
    FEX_CAREFUL(5, "fex_careful", "FEX, careful"),
    ;

    companion object {
        /** Ohne gespeicherte Angabe gilt Stufe 1. */
        val DEFAULT = CAREFUL

        fun fromKey(key: String?): LabStability = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Ergebnis von [LabStabilities.select]. */
sealed class LabStabilityResult {
    data class Applied(val settings: LabSettings) : LabStabilityResult()

    /** Stufe 4 ohne FEX-Paket: nichts geändert. */
    object FexMissing : LabStabilityResult()
}

/**
 * Was eine Stufe in den Start schreibt. Rein, JUnit-getestet. Die Werte sind wörtlich aus der App übernommen (Fundstellen im Bericht
 * `docs/reports/labstabil2_2026-11-15.md`); der Test prüft sie gegen den Quelltext von `Box86_64PresetManager` und `FEXCorePresetManager`.
 */
object LabStabilities {
    /** Der Startschalter von Stufe 2: ein Wort, ohne Anführungszeichen und Leerzeichen (der Weg über `ProcessHelper.splitCommand` zerlegt sonst). */
    const val JS_JITLESS = "--js-flags=--jitless"
    const val JS_FLAGS_PREFIX = "--js-flags="

    /** Stufe 1: was das öffentliche Box64 für `Battle.net.exe` selbst setzt. */
    val CAREFUL_LINES: List<Pair<String, String>> = listOf(
        "BOX64_DYNAREC_SAFEFLAGS" to "2",
        "BOX64_DYNAREC_STRONGMEM" to "1",
        "BOX64_DYNAREC_ALIGNED_ATOMICS" to "0",
    )

    /** Stufe 3: die Werte der App-Voreinstellung `STABILITY` (Präfix `BOX64`), dazu `BOX64_DYNAREC_ALIGNED_ATOMICS=0`. */
    val SAFEST_LINES: List<Pair<String, String>> = listOf(
        "BOX64_DYNAREC_SAFEFLAGS" to "2",
        "BOX64_DYNAREC_FASTNAN" to "0",
        "BOX64_DYNAREC_FASTROUND" to "0",
        "BOX64_DYNAREC_X87DOUBLE" to "1",
        "BOX64_DYNAREC_BIGBLOCK" to "0",
        "BOX64_DYNAREC_STRONGMEM" to "2",
        "BOX64_DYNAREC_FORWARD" to "128",
        "BOX64_DYNAREC_CALLRET" to "0",
        "BOX64_DYNAREC_WAIT" to "0",
        "BOX64_AVX" to "0",
        "BOX64_UNITYPLAYER" to "1",
        "BOX64_MMAP32" to "0",
        "BOX64_DYNAREC_ALIGNED_ATOMICS" to "0",
    )

    /** Stufe 4: die Werte der App-Voreinstellung `STABILITY` von FEX (`FEXCorePresetManager`); mehr setzt die Stufe nicht außer dem Übersetzer. */
    val FEX_LINES: List<Pair<String, String>> = listOf(
        "FEX_TSOENABLED" to "1",
        "FEX_VECTORTSOENABLED" to "1",
        "FEX_MEMCPYSETTSOENABLED" to "1",
        "FEX_HALFBARRIERTSOENABLED" to "1",
        "FEX_X87REDUCEDPRECISION" to "0",
        "FEX_MULTIBLOCK" to "0",
    )

    /** Die Einstellung `BOX64_LOG`/`BOX64_DYNAREC_MISSING` des Schalters "Übersetzer-Protokoll" (wie `addBox64EnvVars` bei `enableLogs`). */
    val TRANSLATOR_LOG_LINES: List<Pair<String, String>> = listOf(
        "BOX64_LOG" to "1",
        "BOX64_DYNAREC_MISSING" to "1",
    )

    /** Die Stufen 4 und 5 gehören zum Übersetzer FEX. */
    private val FEX_STAGES = setOf(LabStability.FEX, LabStability.FEX_CAREFUL)

    /**
     * Die Stufe, die wirklich gilt. Übersetzer-Wahl und Stufe gehören zusammen, in beide Richtungen: FEX gewählt → Stufe 4 (FEX), wenn die Stufe
     * keine FEX-Stufe ist; FEX nicht gewählt → Stufe 1, wenn die Stufe eine FEX-Stufe ist. Eine Quelle der Wahrheit ist die Übersetzer-Wahl.
     */
    fun effective(settings: LabSettings): LabStability = when {
        settings.translator == LabTranslator.FEX && settings.stability !in FEX_STAGES -> LabStability.FEX
        settings.translator != LabTranslator.FEX && settings.stability in FEX_STAGES -> LabStability.CAREFUL
        else -> settings.stability
    }

    /** Die Zeilen `NAME=wert`, die die Stufe in die Umgebung schreibt (Reihenfolge wie im Protokoll). Stufe 4 setzt keine Zeilen. */
    fun envLines(stage: LabStability): List<Pair<String, String>> = when (stage) {
        LabStability.OFF -> emptyList()
        LabStability.CAREFUL, LabStability.CAREFUL_NO_JIT -> CAREFUL_LINES
        LabStability.SAFEST -> SAFEST_LINES
        LabStability.FEX -> emptyList()
        LabStability.FEX_CAREFUL -> FEX_LINES
    }

    /** Der Startschalter der Stufe für Battle.net, null wenn sie keinen setzt. */
    fun startArg(stage: LabStability): String? = if (stage == LabStability.CAREFUL_NO_JIT) JS_JITLESS else null

    /** Hängt den Schalter der Stufe an die Schalter von Battle.net an; hat der Nutzer schon einen `--js-flags=`, bleibt seiner (er gewinnt). */
    fun withStartArg(args: List<String>, stage: LabStability): List<String> {
        val arg = startArg(stage) ?: return args
        return if (args.any { it.startsWith(JS_FLAGS_PREFIX) }) args else args + arg
    }

    /** Stufen 4 und 5 sind nur wählbar, wenn ein FEX-Paket installiert ist; die anderen immer. */
    fun selectable(stage: LabStability, installedFex: Collection<String>): Boolean = stage !in FEX_STAGES || installedFex.isNotEmpty()

    /**
     * Wählt eine Stufe. Stufen 0 bis 3 setzen den Übersetzer auf den eingebauten (Wine bleibt). Stufen 4 und 5 setzen die vorhandene FEX-Wahl
     * (Übersetzer FEX und ein installiertes Paket, Wine bleibt). Ohne FEX-Paket: [LabStabilityResult.FexMissing].
     */
    fun select(
        stage: LabStability,
        current: LabSettings,
        installed: List<LabInstalledPackage>,
        packages: List<LabPackage>,
    ): LabStabilityResult {
        if (stage !in FEX_STAGES) {
            return LabStabilityResult.Applied(current.copy(stability = stage, translator = LabTranslator.BUNDLED, fexVersion = ""))
        }
        val fexPackage = LabQuickPicks.fex(installed, packages) ?: return LabStabilityResult.FexMissing
        val keep = current.translator == LabTranslator.FEX && current.fexVersion in installed.filter { it.kind == LabPackageKind.FEX }.map { it.verName }
        return LabStabilityResult.Applied(
            current.copy(
                stability = stage,
                translator = LabTranslator.FEX,
                fexVersion = if (keep) current.fexVersion else fexPackage.verName,
            ),
        )
    }

    /** Nach jeder Änderung der Einstellungen: die gespeicherte Stufe wird auf die wirksame gesetzt (auch gespeichert, nicht nur in der Anzeige). */
    fun normalize(settings: LabSettings): LabSettings {
        val stage = effective(settings)
        return if (stage == settings.stability) settings else settings.copy(stability = stage)
    }

    /** Die Zeilen der Stufe für Protokoll und Karte: erst die Umgebung, dann der Startschalter. */
    fun describeLines(stage: LabStability): List<String> =
        envLines(stage).map { (k, v) -> "$k=$v" } + listOfNotNull(startArg(stage)?.let { "start switch: $it" })
}

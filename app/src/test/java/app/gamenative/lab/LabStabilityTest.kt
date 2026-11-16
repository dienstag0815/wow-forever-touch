package app.gamenative.lab

import com.winlator.core.ProcessHelper
import com.winlator.core.envvars.EnvVars
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Stabilitäts-Schnellwahl des Labors (Auftrag labstabil2, Punkt 1): Stufen, Reihenfolge im Start, Durchstich bis `EnvVars`. */
class LabStabilityTest {
    private val bnet = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe"
    private val box64Source = File("src/main/java/com/winlator/box86_64/Box86_64PresetManager.java").readText()
    private val fexSource = File("src/main/java/com/winlator/fexcore/FEXCorePresetManager.java").readText()

    private fun ctx(fex: Set<String> = emptySet()) =
        LabCommandContext(baseEnv = "MESA_GLTHREAD=true PULSE_LATENCY_MSEC=144", battleNetExe = bnet, installedFex = fex)

    private fun fexPkg(name: String) = LabInstalledPackage(LabPackageKind.FEX, name, 1, name)

    /** Die `put`-Zeilen eines Zweigs `if/else if (id.equals(<Preset>.<NAME>))` aus dem Quelltext, mit `box64` als Präfix (`ucPrefix` ersetzt). */
    private fun presetValues(source: String, presetName: String, boxPrefix: Boolean): List<Pair<String, String>> {
        val start = source.indexOf("id.equals(" + (if (boxPrefix) "Box86_64Preset" else "FEXCorePreset") + ".$presetName)")
        assertTrue("preset $presetName not found", start >= 0)
        val next = Regex("else if \\(id\\.(equals|startsWith)\\(").find(source, start + 10)?.range?.first ?: source.length
        val block = source.substring(start, next)
        val put = Regex("envVars\\.put\\((?:ucPrefix \\+ )?\"([A-Z0-9_]+)\", \"([^\"]*)\"\\)")
        return put.findAll(block).map { m ->
            val name = if (boxPrefix && m.value.contains("ucPrefix")) "BOX64" + m.groupValues[1] else m.groupValues[1]
            name to m.groupValues[2]
        }.toList()
    }

    @Test
    fun defaultIsStageOneCareful() {
        assertEquals(LabStability.CAREFUL, LabSettings().stability)
        assertEquals(1, LabSettings().stability.level)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), LabStability.entries.map { it.level })
        assertEquals(listOf("Off", "Careful", "Careful, no JIT", "Safest", "FEX", "FEX, careful"), LabStability.entries.map { it.englishName })
    }

    @Test
    fun settingsRoundTripAndOldDataGetsTheDefault() {
        for (stage in LabStability.entries) {
            assertEquals(stage, LabSettings.fromJson(LabSettings(stability = stage).toJson()).stability)
        }
        assertTrue(LabSettings.fromJson(LabSettings(translatorLog = true).toJson()).translatorLog)
        assertFalse(LabSettings.fromJson(LabSettings().toJson()).translatorLog)
        // Gespeichert vor dieser Änderung: kein Schlüssel `stability`.
        assertEquals(LabStability.CAREFUL, LabSettings.fromJson("{\"v\":2,\"wine\":\"\"}").stability)
        assertEquals(LabStability.CAREFUL, LabSettings.fromJson("{\"stability\":\"gibt-es-nicht\"}").stability)
        assertEquals(LabStability.CAREFUL, LabSettings.fromJson(null).stability)
    }

    @Test
    fun stageZeroChangesNothing() {
        assertTrue(LabStabilities.envLines(LabStability.OFF).isEmpty())
        assertNull(LabStabilities.startArg(LabStability.OFF))
        val off = LabCommand.build(LabSettings(stability = LabStability.OFF), LabTarget.BATTLENET, ctx())
        val names = off.env.map { it.first }
        assertTrue(names.none { it.startsWith("BOX64_") })
        assertEquals(listOf("--disable-gpu", "--disable-gpu-compositing", "--enable-features=NetworkServiceInProcess2"), off.args)
    }

    @Test
    fun stageOneSetsTheThreeLinesAfterTheBaseValuesAndNoSwitch() {
        val plan = LabCommand.build(LabSettings(), LabTarget.BATTLENET, ctx())
        assertTrue(plan.issues.toString(), plan.ok)
        assertEquals(LabStability.CAREFUL, plan.stability)
        val envPairs = plan.env
        val index = { name: String -> envPairs.indexOfFirst { it.first == name } }
        assertEquals("2", envPairs[index("BOX64_DYNAREC_SAFEFLAGS")].second)
        assertEquals("1", envPairs[index("BOX64_DYNAREC_STRONGMEM")].second)
        assertEquals("0", envPairs[index("BOX64_DYNAREC_ALIGNED_ATOMICS")].second)
        // Nach den Grundwerten des Containers, vor WINEDEBUG.
        assertTrue(index("PULSE_LATENCY_MSEC") < index("BOX64_DYNAREC_SAFEFLAGS"))
        assertTrue(index("BOX64_DYNAREC_ALIGNED_ATOMICS") < index("WINEDEBUG"))
        assertFalse(plan.args.any { it.startsWith("--js-flags") })
    }

    @Test
    fun stageThroughEnvVarsOverPerformancePresetGivesStrongmemOneAndSafeflagsTwo() {
        // Durchstich: Stufe 1 -> plan.envString -> mit der echten Klasse EnvVars über die Werte von PERFORMANCE (aus dem Quelltext).
        val plan = LabCommand.build(LabSettings(stability = LabStability.CAREFUL), LabTarget.BATTLENET, ctx())
        val performance = presetValues(box64Source, "PERFORMANCE", boxPrefix = true)
        assertTrue("PERFORMANCE not read: $performance", performance.size >= 12)
        val env = EnvVars()
        performance.forEach { (k, v) -> env.put(k, v) }
        assertEquals("0", env.get("BOX64_DYNAREC_STRONGMEM"))
        assertEquals("1", env.get("BOX64_DYNAREC_SAFEFLAGS"))
        env.putAll(plan.envString)
        assertEquals("1", env.get("BOX64_DYNAREC_STRONGMEM"))
        assertEquals("2", env.get("BOX64_DYNAREC_SAFEFLAGS"))
        assertEquals("0", env.get("BOX64_DYNAREC_ALIGNED_ATOMICS"))
        // Was die Stufe nicht nennt, bleibt von PERFORMANCE.
        assertEquals("3", env.get("BOX64_DYNAREC_BIGBLOCK"))
        assertEquals("512", env.get("BOX64_DYNAREC_FORWARD"))
    }

    @Test
    fun stageTwoAddsExactlyOneJitlessArgumentAfterTheExistingSwitches() {
        val plan = LabCommand.build(LabSettings(stability = LabStability.CAREFUL_NO_JIT), LabTarget.BATTLENET, ctx())
        assertEquals(
            listOf("--disable-gpu", "--disable-gpu-compositing", "--enable-features=NetworkServiceInProcess2", "--js-flags=--jitless"),
            plan.args,
        )
        assertEquals(LabStabilities.CAREFUL_LINES, LabStabilities.envLines(LabStability.CAREFUL_NO_JIT))
        // Der Weg wie in XServerScreen: "wine explorer /desktop=shell,<Größe> <Pfad mit \ vor Leerzeichen> " + execArgs, zerlegt von splitCommand.
        val line = "wine explorer /desktop=shell,1440x900 " + bnet.replace(" ", "\\ ") + " " + plan.execArgs
        val parts = ProcessHelper.splitCommand(line).toList()
        assertEquals(1, parts.count { it.contains("jitless") })
        assertEquals("--js-flags=--jitless", parts.single { it.contains("jitless") })
        assertEquals(bnet, parts[3])
    }

    @Test
    fun theJitlessSwitchHasNoQuotesAndNoSpaces() {
        assertFalse(LabStabilities.JS_JITLESS.any { it.isWhitespace() || it == '"' || it == '\'' || it == '\\' })
        assertEquals(LabStabilities.JS_JITLESS, LabCommand.quote(LabStabilities.JS_JITLESS))
    }

    @Test
    fun theSwitchIsOnlyForBattleNetAndOwnJsFlagsWin() {
        val installer = LabCommand.build(LabSettings(stability = LabStability.CAREFUL_NO_JIT), LabTarget.INSTALLER, ctx())
        assertTrue(installer.args.isEmpty())
        val own = LabCommand.build(
            LabSettings(stability = LabStability.CAREFUL_NO_JIT, battleNetLine = "--js-flags=--no-opt --disable-gpu"),
            LabTarget.BATTLENET,
            ctx(),
        )
        assertEquals(listOf("--js-flags=--no-opt", "--disable-gpu"), own.args)
        val once = LabStabilities.withStartArg(listOf("--disable-gpu"), LabStability.CAREFUL_NO_JIT)
        assertEquals(1, LabStabilities.withStartArg(once, LabStability.CAREFUL_NO_JIT).count { it.startsWith("--js-flags=") })
    }

    @Test
    fun stageThreeIsThePresetStabilityOfTheAppPlusAlignedAtomics() {
        val fromSource = presetValues(box64Source, "STABILITY", boxPrefix = true)
        assertEquals(12, fromSource.size)
        assertEquals(fromSource + ("BOX64_DYNAREC_ALIGNED_ATOMICS" to "0"), LabStabilities.SAFEST_LINES)
        assertEquals("2", fromSource.toMap().getValue("BOX64_DYNAREC_STRONGMEM"))
    }

    @Test
    fun stageFourFexLinesAreTheNamesOfThePresetStabilityOfFex() {
        val fromSource = presetValues(fexSource, "STABILITY", boxPrefix = false)
        assertEquals(6, fromSource.size)
        assertEquals(fromSource, LabStabilities.FEX_LINES)
        assertTrue(LabStabilities.FEX_LINES.all { it.first.startsWith("FEX_") })
    }

    @Test
    fun stageOneLinesAreTheOnesTheStageTableNames() {
        assertEquals(
            listOf("BOX64_DYNAREC_SAFEFLAGS=2", "BOX64_DYNAREC_STRONGMEM=1", "BOX64_DYNAREC_ALIGNED_ATOMICS=0"),
            LabStabilities.envLines(LabStability.CAREFUL).map { "${it.first}=${it.second}" },
        )
    }

    @Test
    fun ownEnvironmentLinesStillWinOverTheStage() {
        val plan = LabCommand.build(LabSettings(extraEnv = "BOX64_DYNAREC_STRONGMEM=3\nBOX64_DYNAREC_SAFEFLAGS=0"), LabTarget.BATTLENET, ctx())
        val env = EnvVars(plan.envString)
        assertEquals("3", env.get("BOX64_DYNAREC_STRONGMEM"))
        assertEquals("0", env.get("BOX64_DYNAREC_SAFEFLAGS"))
        assertEquals("0", env.get("BOX64_DYNAREC_ALIGNED_ATOMICS"))
    }

    @Test
    fun stageFourNeedsTheFexTranslatorOtherwiseItIsStageOne() {
        val s = LabSettings(stability = LabStability.FEX, translator = LabTranslator.BUNDLED)
        assertEquals(LabStability.CAREFUL, LabStabilities.effective(s))
        assertEquals(LabStability.CAREFUL, LabStabilities.normalize(s).stability)
        // Der Startplan zieht dieselbe Linie: Stufe 1, nicht FEX-Zeilen.
        val plan = LabCommand.build(s, LabTarget.BATTLENET, ctx())
        assertEquals(LabStability.CAREFUL, plan.stability)
        assertTrue(plan.env.none { it.first.startsWith("FEX_") })
        val withFex = LabSettings(stability = LabStability.FEX, translator = LabTranslator.FEX, fexVersion = "lab-2508")
        assertEquals(LabStability.FEX, LabStabilities.effective(withFex))
        val fexPlan = LabCommand.build(withFex, LabTarget.BATTLENET, ctx(fex = setOf("lab-2508")))
        assertTrue(fexPlan.issues.toString(), fexPlan.ok)
        assertEquals("FEXCore", fexPlan.emulator)
        assertEquals("lab-2508", fexPlan.fexVersion)
        // Stufe 4 setzt keine FEX-Zeilen, nur den Übersetzer.
        assertTrue(fexPlan.env.none { it.first.startsWith("FEX_") })
        assertFalse(fexPlan.env.any { it.first == "BOX64_DYNAREC_SAFEFLAGS" })
        val carefulFex = LabSettings(stability = LabStability.FEX_CAREFUL, translator = LabTranslator.FEX, fexVersion = "lab-2508")
        val carefulPlan = LabCommand.build(carefulFex, LabTarget.BATTLENET, ctx(fex = setOf("lab-2508")))
        val env = EnvVars(carefulPlan.envString)
        assertEquals("1", env.get("FEX_TSOENABLED"))
        assertEquals("0", env.get("FEX_MULTIBLOCK"))
    }

    @Test
    fun stageFourHasNoLinesAndStageFiveHasTheFexLines() {
        assertTrue(LabStabilities.envLines(LabStability.FEX).isEmpty())
        assertEquals(LabStabilities.FEX_LINES, LabStabilities.envLines(LabStability.FEX_CAREFUL))
        assertTrue(LabStabilities.selectable(LabStability.FEX_CAREFUL, listOf("lab-2508")))
        assertFalse(LabStabilities.selectable(LabStability.FEX_CAREFUL, emptyList<String>()))
    }

    @Test
    fun effectiveAndNormalizeGoBothWaysWithTheTranslator() {
        // FEX gewählt, gespeicherte Stufe 1 (alte Daten): wirksam Stufe 4.
        val upgraded = LabSettings(stability = LabStability.CAREFUL, translator = LabTranslator.FEX, fexVersion = "lab-2508")
        assertEquals(LabStability.FEX, LabStabilities.effective(upgraded))
        assertEquals(LabStability.FEX, LabStabilities.normalize(upgraded).stability)
        // FEX abgewählt, gespeicherte Stufe 5: wirksam Stufe 1, auch gespeichert.
        val fell = LabSettings(stability = LabStability.FEX_CAREFUL, translator = LabTranslator.BUNDLED)
        assertEquals(LabStability.CAREFUL, LabStabilities.effective(fell))
        assertEquals(LabStability.CAREFUL, LabStabilities.normalize(fell).stability)
        // Stufe 5 mit gewähltem FEX bleibt Stufe 5.
        assertEquals(LabStability.FEX_CAREFUL, LabStabilities.effective(LabSettings(stability = LabStability.FEX_CAREFUL, translator = LabTranslator.FEX)))
        // Ohne FEX-Wahl bleibt eine Nicht-FEX-Stufe unverändert.
        assertEquals(LabStability.SAFEST, LabStabilities.effective(LabSettings(stability = LabStability.SAFEST)))
    }

    @Test
    fun planWithFexAndStoredStageOneHasNoStageBox64Lines() {
        val plan = LabCommand.build(
            LabSettings(stability = LabStability.CAREFUL, translator = LabTranslator.FEX, fexVersion = "lab-2508"),
            LabTarget.BATTLENET,
            ctx(fex = setOf("lab-2508")),
        )
        assertEquals(LabStability.FEX, plan.stability)
        assertTrue(plan.env.none { it.first.startsWith("BOX64_DYNAREC_") })
    }

    @Test
    fun choosingStageOneToThreeSetsTheBundledTranslatorAndKeepsWine() {
        val s = LabSettings(wine = "w", translator = LabTranslator.FEX, fexVersion = "lab-2508")
        val r = (LabStabilities.select(LabStability.CAREFUL, s, emptyList(), emptyList()) as LabStabilityResult.Applied).settings
        assertEquals(LabStability.CAREFUL, r.stability)
        assertEquals(LabTranslator.BUNDLED, r.translator)
        assertEquals("", r.fexVersion)
        assertEquals("w", r.wine)
    }

    @Test
    fun choosingStageFourSetsTheExistingFexChoiceAndKeepsWine() {
        val packages = emptyList<LabPackage>()
        val start = LabSettings(wine = "proton-10-x", translator = LabTranslator.BUNDLED)
        val r = LabStabilities.select(LabStability.FEX, start, listOf(fexPkg("lab-2508")), packages)
        val applied = (r as LabStabilityResult.Applied).settings
        assertEquals(LabStability.FEX, applied.stability)
        assertEquals(LabTranslator.FEX, applied.translator)
        assertEquals("lab-2508", applied.fexVersion)
        assertEquals("proton-10-x", applied.wine)
        // Schon gewählte, installierte Version bleibt.
        val keep = LabSettings(translator = LabTranslator.FEX, fexVersion = "lab-2605")
        val kept = (LabStabilities.select(LabStability.FEX, keep, listOf(fexPkg("lab-2508"), fexPkg("lab-2605")), packages) as LabStabilityResult.Applied).settings
        assertEquals("lab-2605", kept.fexVersion)
        // Wird FEX danach abgewählt, fällt die Stufe auf 1 (das tut die Seite mit normalize bei jeder Änderung).
        assertEquals(LabStability.CAREFUL, LabStabilities.normalize(applied.copy(translator = LabTranslator.BUNDLED)).stability)
    }

    @Test
    fun stageFourWithoutAFexPackageIsRefusedAndNotSelectable() {
        assertEquals(LabStabilityResult.FexMissing, LabStabilities.select(LabStability.FEX, LabSettings(), emptyList(), emptyList()))
        assertFalse(LabStabilities.selectable(LabStability.FEX, emptyList<String>()))
        assertTrue(LabStabilities.selectable(LabStability.FEX, listOf("lab-2508")))
        for (stage in LabStability.entries.filter { it != LabStability.FEX && it != LabStability.FEX_CAREFUL }) assertTrue(LabStabilities.selectable(stage, emptyList<String>()))
    }

    @Test
    fun otherStagesSetTheBundledTranslatorAndKeepWine() {
        val s = LabSettings(translator = LabTranslator.FEX, fexVersion = "lab-2508", wine = "w")
        for (stage in listOf(LabStability.OFF, LabStability.CAREFUL, LabStability.CAREFUL_NO_JIT, LabStability.SAFEST)) {
            val r = (LabStabilities.select(stage, s, emptyList(), emptyList()) as LabStabilityResult.Applied).settings
            assertEquals(stage, r.stability)
            assertEquals(s.copy(stability = stage, translator = LabTranslator.BUNDLED, fexVersion = ""), r)
        }
    }

    @Test
    fun translatorLogSetsTheTwoNamesAfterTheStageAndBeforeOwnLines() {
        val off = LabCommand.build(LabSettings(), LabTarget.BATTLENET, ctx())
        assertTrue(off.env.none { it.first == "BOX64_LOG" || it.first == "BOX64_DYNAREC_MISSING" })
        val on = LabCommand.build(LabSettings(translatorLog = true, extraEnv = "BOX64_LOG=0"), LabTarget.BATTLENET, ctx())
        val env = EnvVars(on.envString)
        assertEquals("0", env.get("BOX64_LOG"))
        assertEquals("1", env.get("BOX64_DYNAREC_MISSING"))
        val plain = LabCommand.build(LabSettings(translatorLog = true), LabTarget.BATTLENET, ctx())
        assertEquals("1", EnvVars(plain.envString).get("BOX64_LOG"))
        assertTrue(plain.translatorLog)
    }

    @Test
    fun nextStartCardShowsTheStageAndItsLines() {
        val stage1 = LabNextStarts.describe(LabSettings(), emptyList())
        assertEquals(LabStability.CAREFUL, stage1.stability)
        assertEquals(listOf("BOX64_DYNAREC_SAFEFLAGS=2", "BOX64_DYNAREC_STRONGMEM=1", "BOX64_DYNAREC_ALIGNED_ATOMICS=0"), stage1.stabilityLines)
        val stage2 = LabNextStarts.describe(LabSettings(stability = LabStability.CAREFUL_NO_JIT), emptyList())
        assertEquals("start switch: --js-flags=--jitless", stage2.stabilityLines.last())
        val staleFex = LabNextStarts.describe(LabSettings(stability = LabStability.FEX, translator = LabTranslator.BUNDLED), emptyList())
        assertEquals(LabStability.CAREFUL, staleFex.stability)
        assertEquals(LabStability.FEX, LabNextStarts.describe(
            LabSettings(stability = LabStability.FEX, translator = LabTranslator.FEX, fexVersion = "lab-2508"),
            listOf(fexPkg("lab-2508")),
        ).stability)
        assertTrue(LabNextStarts.describe(LabSettings(stability = LabStability.OFF), emptyList()).stabilityLines.isEmpty())
        assertNotNull(LabNextStarts.describe(LabSettings(translatorLog = true), emptyList()).takeIf { it.translatorLog })
    }
}

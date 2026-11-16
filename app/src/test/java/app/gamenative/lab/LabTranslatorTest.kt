package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabTranslatorTest {
    @Test
    fun bundledFexAndNone() {
        val bundled = LabTranslatorChoice.resolve(LabSettings(translator = LabTranslator.BUNDLED), emptySet())
        assertEquals("Box64", bundled.emulator)
        assertNull(bundled.issue)
        val none = LabTranslatorChoice.resolve(LabSettings(translator = LabTranslator.NONE), emptySet())
        assertEquals("none", none.emulator)
        val fex = LabTranslatorChoice.resolve(LabSettings(translator = LabTranslator.FEX, fexVersion = "lab-2508"), setOf("lab-2508"))
        assertEquals("FEXCore", fex.emulator)
        assertEquals("lab-2508", fex.fexVersion)
        assertNull(fex.issue)
    }

    @Test
    fun fexNeedsAnInstalledPackage() {
        assertEquals(LabIssueCode.FEX_MISSING, LabTranslatorChoice.resolve(LabSettings(translator = LabTranslator.FEX), setOf("lab-2508")).issue?.code)
        assertEquals(LabIssueCode.FEX_MISSING, LabTranslatorChoice.resolve(LabSettings(translator = LabTranslator.FEX, fexVersion = "x"), setOf("lab-2508")).issue?.code)
    }

    @Test
    fun unknownKeysFallBackToTheDefaults() {
        assertEquals(LabTranslator.BUNDLED, LabTranslator.fromKey("nonsense"))
        assertEquals(LabTranslator.BUNDLED, LabTranslator.fromKey(null))
        assertEquals(LabWineDebug.DIAGNOSE, LabWineDebug.fromKey("x"))
    }

    @Test
    fun settingsRoundTripAndBrokenJsonGivesDefaults() {
        val s = LabSettings(wine = "w", translator = LabTranslator.FEX, fexVersion = "f", extraEnv = "A=1\nB=2", sameIdentity = false, wineDebug = LabWineDebug.ERRORS)
        assertEquals(s, LabSettings.fromJson(s.toJson()))
        assertEquals(LabSettings(), LabSettings.fromJson("{broken"))
        assertEquals(LabSettings(), LabSettings.fromJson(null))
        assertTrue(LabSettings().sameIdentity)
    }

    private fun installedFex(name: String) = LabInstalledPackage(LabPackageKind.FEX, name, 1, name)
    private fun installedWine(name: String) = LabInstalledPackage(LabPackageKind.WINE, name, 7, LabPackages.wineIdentifier(name, 7))
    private val packages = LabPackages.parse(
        "[{\"id\":\"fex-2508\",\"art\":\"fex\",\"name\":\"FEX 2508\",\"version\":\"2508 (x)\",\"url\":\"https://example.invalid/a.tzst\"," +
            "\"sha256\":\"" + "a".repeat(64) + "\",\"bytes\":10,\"format\":\"tzst\"}]",
    ).packages

    @Test
    fun quickPickOneNeedsNothing() {
        val r = LabQuickPicks.resolve(LabQuickPick.PROTON11_BUNDLED, LabSettings(wine = "x", translator = LabTranslator.FEX, fexVersion = "y"), emptyList(), packages)
        val s = (r as LabQuickPickResult.Applied).settings
        assertEquals("", s.wine)
        assertEquals(LabTranslator.BUNDLED, s.translator)
    }

    @Test
    fun quickPickTwoAndThreeNameWhatIsMissing() {
        val none = LabQuickPicks.resolve(LabQuickPick.PROTON11_FEX, LabSettings(), emptyList(), packages)
        assertEquals(listOf("fex-2508"), (none as LabQuickPickResult.Missing).packageIds)
        val three = LabQuickPicks.resolve(LabQuickPick.PROTON10_FEX, LabSettings(), emptyList(), packages)
        assertEquals(listOf("fex-2508", "wine-ge-proton-10.0-34-arm64ec"), (three as LabQuickPickResult.Missing).packageIds)
        val onlyFex = LabQuickPicks.resolve(LabQuickPick.PROTON10_FEX, LabSettings(), listOf(installedFex("lab-2508")), packages)
        assertEquals(listOf("wine-ge-proton-10.0-34-arm64ec"), (onlyFex as LabQuickPickResult.Missing).packageIds)
    }

    @Test
    fun quickPickSetsOnlyWineTranslatorAndFex() {
        val installed = listOf(installedFex("lab-2508"), installedWine("proton-10.0-34-arm64ec"))
        val base = LabSettings(battleNetLine = "--x", extraEnv = "A=1")
        val two = (LabQuickPicks.resolve(LabQuickPick.PROTON11_FEX, base, installed, packages) as LabQuickPickResult.Applied).settings
        assertEquals(base.copy(wine = "", translator = LabTranslator.FEX, fexVersion = "lab-2508"), two)
        val three = (LabQuickPicks.resolve(LabQuickPick.PROTON10_FEX, base, installed, packages) as LabQuickPickResult.Applied).settings
        assertEquals(base.copy(wine = "proton-10.0-34-arm64ec-7", translator = LabTranslator.FEX, fexVersion = "lab-2508"), three)
    }

    @Test
    fun everyEnvSuggestionThatGoesToTheEnvFieldIsAcceptedByTheEnvCheck() {
        for (suggestion in LabQuickPicks.ENV_SUGGESTIONS) {
            val (field, line) = LabQuickPicks.suggestionTarget(suggestion)
            if (field == LabField.ENV) {
                val (pairs, issues) = LabCommand.parseEnv(line)
                assertTrue(suggestion + " " + issues.toString(), issues.isEmpty())
                assertEquals(1, pairs.size)
            }
        }
    }

    @Test
    fun suggestionsGoToTheRightFieldAndAreNotAddedTwice() {
        assertEquals(LabField.ENV to "WINE_SIMULATE_WRITECOPY=1", LabQuickPicks.suggestionTarget("WINE_SIMULATE_WRITECOPY=1"))
        assertEquals(LabField.DLL to "locationapi=d", LabQuickPicks.suggestionTarget("WINEDLLOVERRIDES=locationapi=d"))
        assertEquals("A=1\nB=2", LabQuickPicks.addLine("A=1", "B=2"))
        assertEquals("A=1\nB=2", LabQuickPicks.addLine("A=1\nB=2", "B=2"))
        assertEquals("B=2", LabQuickPicks.addLine("", "B=2"))
        val s = LabSettings(extraEnv = LabQuickPicks.addLine("", "WINE_SIMULATE_WRITECOPY=1"), dllOverrides = LabQuickPicks.addLine("", "locationapi=d"))
        val plan = LabCommand.build(s, LabTarget.INSTALLER, LabCommandContext(baseEnv = "", battleNetExe = null))
        assertTrue(plan.issues.toString(), plan.ok)
    }
}

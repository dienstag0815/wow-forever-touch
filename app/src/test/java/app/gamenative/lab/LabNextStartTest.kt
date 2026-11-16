package app.gamenative.lab

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Punkt 5 und 6 des Auftrags labfenster: "schon installiert" statt `ERROR_EXIST`, und die Anzeige, womit der nächste Start läuft, samt der
 * Kette Schnellwahl 2 -> Einstellungen -> Startplan -> Container-Felder -> `HODLL`.
 */
class LabNextStartTest {
    private fun fex(name: String, code: Int = 1) = LabInstalledPackage(LabPackageKind.FEX, name, code, name)
    private fun wine(name: String, code: Int = 7) = LabInstalledPackage(LabPackageKind.WINE, name, code, LabPackages.wineIdentifier(name, code))

    private val packages = LabPackages.parse(
        "[{\"id\":\"fex-2508\",\"art\":\"fex\",\"name\":\"FEX 2508\",\"version\":\"2508 (Paket-ID); intern FEX-2601-116-g1268a39\",\"url\":\"https://example.invalid/a.tzst\"," +
            "\"sha256\":\"" + "a".repeat(64) + "\",\"bytes\":10,\"format\":\"tzst\"}," +
            "{\"id\":\"fex-2605\",\"art\":\"fex\",\"name\":\"FEX 2605\",\"version\":\"2605\",\"url\":\"https://example.invalid/b.tzst\"," +
            "\"sha256\":\"" + "b".repeat(64) + "\",\"bytes\":10,\"format\":\"tzst\"}," +
            "{\"id\":\"wine-ge-proton-10.0-34-arm64ec\",\"art\":\"wine\",\"name\":\"Proton 10\",\"version\":\"10.0-34\",\"url\":\"https://example.invalid/w.wcp\"," +
            "\"sha256\":\"" + "c".repeat(64) + "\",\"bytes\":10,\"format\":\"wcp\"}," +
            "{\"id\":\"installer-battlenet-setup\",\"art\":\"installer\",\"name\":\"Installer\",\"version\":\"1\",\"url\":\"https://example.invalid/i.exe\"," +
            "\"sha256\":\"\",\"bytes\":0,\"format\":\"exe\"}]",
    ).packages

    private fun ctx(installedFex: Set<String>) = LabCommandContext(baseEnv = "", battleNetExe = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe", installedFex = installedFex)

    // --- Punkt 5: schon installiert ---

    @Test
    fun theFexEntriesAreInstalledUnderTheNameTheQuickPickLooksFor() {
        assertEquals("lab-2508", LabPackages.fexVerName(packages.first { it.id == "fex-2508" }))
        assertEquals("lab-2605", LabPackages.fexVerName(packages.first { it.id == "fex-2605" }))
    }

    @Test
    fun anInstalledPackageIsRecognisedPerEntry() {
        val f2508 = packages.first { it.id == "fex-2508" }
        val f2605 = packages.first { it.id == "fex-2605" }
        val proton = packages.first { it.kind == LabPackageKind.WINE }
        val installer = packages.first { it.kind == LabPackageKind.INSTALLER }
        val installed = listOf(fex("lab-2508"), wine("proton-10.0-34-arm64ec"))
        assertEquals("lab-2508", LabPackages.installedMatch(f2508, installed)?.verName)
        assertNull(LabPackages.installedMatch(f2605, installed))
        assertEquals("proton-10.0-34-arm64ec", LabPackages.installedMatch(proton, installed)?.verName)
        assertNull("the installer is never installed", LabPackages.installedMatch(installer, installed))
        assertNull(LabPackages.installedMatch(f2508, emptyList()))
    }

    @Test
    fun wineMatchIgnoresCaseAndNeverMatchesAnEmptyName() {
        val proton = packages.first { it.kind == LabPackageKind.WINE }
        assertEquals("Proton-10.0-34-arm64ec", LabPackages.installedMatch(proton, listOf(wine("Proton-10.0-34-arm64ec")))?.verName)
        assertNull(LabPackages.installedMatch(proton, listOf(LabInstalledPackage(LabPackageKind.WINE, "proton-", 1, null))))
        assertNull(LabPackages.installedMatch(proton, listOf(wine("proton-11.0-90624-arm64ec"))))
    }

    @Test
    fun errorExistIsNotAFailure() {
        val line = LabPackages.installFailureLine("fex-2508", "ERROR_EXIST", wrongType = false)
        assertEquals("install of fex-2508: already installed (ERROR_EXIST), nothing changed", line)
        assertFalse(line.contains("failed"))
        assertTrue(LabPackages.isAlreadyInstalledReason("ERROR_EXIST"))
        assertFalse(LabPackages.isAlreadyInstalledReason("ERROR_BADTAR"))
        assertFalse(LabPackages.isAlreadyInstalledReason(null))
    }

    @Test
    fun otherReasonsStayFailures() {
        assertEquals("install of x failed: ERROR_BADTAR", LabPackages.installFailureLine("x", "ERROR_BADTAR", false))
        assertEquals("install of x failed: wrong type", LabPackages.installFailureLine("x", null, true))
        assertEquals("install of x failed: unknown", LabPackages.installFailureLine("x", null, false))
    }

    @Test
    fun theReasonNameMatchesTheEnumOfThePackageInstaller() {
        val source = File("src/main/java/com/winlator/contents/ContentsManager.java").readText()
        assertTrue(source.contains("ERROR_EXIST,"))
        assertTrue("ERROR_EXIST comes from an existing install folder", source.contains("installPath.exists()"))
    }

    // --- Punkt 6: womit der nächste Start läuft ---

    @Test
    fun bundledTranslatorWithAnInstalledFexPackageSaysItIsNotChosen() {
        val next = LabNextStarts.describe(LabSettings(), listOf(fex("lab-2508")))
        assertEquals(LabTranslator.BUNDLED, next.translator)
        assertEquals("Box64", next.emulator)
        assertEquals("wowbox64.dll", next.hodll)
        assertNull(next.fexVersion)
        assertFalse(next.fexActive)
        assertEquals(listOf(LabNextNote.FEX_INSTALLED_NOT_CHOSEN), next.notes)
        assertEquals("lab-2508", next.installedFex)
        assertFalse(next.startBlocked)
        assertTrue(next.wineBundled)
        assertEquals(LabIds.FALLBACK_WINE, next.wine)
    }

    @Test
    fun bundledTranslatorWithoutFexIsPlain() {
        val next = LabNextStarts.describe(LabSettings(), emptyList(), bundledWine = "proton-x-1")
        assertTrue(next.notes.isEmpty())
        assertEquals("proton-x-1", next.wine)
    }

    @Test
    fun fexChosenAndInstalledRunsWithFex() {
        val next = LabNextStarts.describe(LabSettings(translator = LabTranslator.FEX, fexVersion = "lab-2508"), listOf(fex("lab-2508")))
        assertTrue(next.fexActive)
        assertEquals("FEXCore", next.emulator)
        assertEquals("lab-2508", next.fexVersion)
        assertEquals("libwow64fex.dll", next.hodll)
        assertTrue(next.notes.isEmpty())
        assertFalse(next.startBlocked)
    }

    @Test
    fun fexChosenButNotUsableSaysWhy() {
        val none = LabNextStarts.describe(LabSettings(translator = LabTranslator.FEX), emptyList())
        assertEquals(listOf(LabNextNote.FEX_CHOSEN_NONE_INSTALLED), none.notes)
        assertTrue(none.startBlocked)
        assertFalse(none.fexActive)
        assertNull(none.fexVersion)

        val noVersion = LabNextStarts.describe(LabSettings(translator = LabTranslator.FEX), listOf(fex("lab-2508")))
        assertEquals(listOf(LabNextNote.FEX_CHOSEN_NO_VERSION), noVersion.notes)
        assertTrue(noVersion.startBlocked)

        val gone = LabNextStarts.describe(LabSettings(translator = LabTranslator.FEX, fexVersion = "lab-2605"), listOf(fex("lab-2508")))
        assertEquals(listOf(LabNextNote.FEX_VERSION_NOT_INSTALLED), gone.notes)
        assertEquals("lab-2605", gone.missingName)
        assertTrue(gone.startBlocked)
    }

    @Test
    fun aMissingWinePackageBlocksTheStart() {
        val next = LabNextStarts.describe(LabSettings(wine = "proton-10.0-34-arm64ec-7"), emptyList())
        assertEquals(listOf(LabNextNote.WINE_NOT_INSTALLED), next.notes)
        assertEquals("proton-10.0-34-arm64ec-7", next.missingName)
        assertFalse(next.wineBundled)
        assertTrue(next.startBlocked)
        val ok = LabNextStarts.describe(LabSettings(wine = "proton-10.0-34-arm64ec-7"), listOf(wine("proton-10.0-34-arm64ec")))
        assertTrue(ok.notes.isEmpty())
    }

    @Test
    fun noTranslatorHasNoHodllAndIsNamed() {
        val next = LabNextStarts.describe(LabSettings(translator = LabTranslator.NONE), emptyList())
        assertNull(next.hodll)
        assertEquals("none", next.emulator)
        assertEquals(listOf(LabNextNote.NO_TRANSLATOR), next.notes)
        assertFalse(next.startBlocked)
    }

    @Test
    fun theDescriptionAgreesWithWhatTheStartPlanRefuses() {
        val cases = listOf(
            LabSettings(translator = LabTranslator.FEX) to emptySet<String>(),
            LabSettings(translator = LabTranslator.FEX) to setOf("lab-2508"),
            LabSettings(translator = LabTranslator.FEX, fexVersion = "lab-2605") to setOf("lab-2508"),
            LabSettings(translator = LabTranslator.FEX, fexVersion = "lab-2508") to setOf("lab-2508"),
            LabSettings() to setOf("lab-2508"),
            LabSettings(wine = "proton-10.0-34-arm64ec-7") to emptySet<String>(),
        )
        for ((settings, installedFex) in cases) {
            val installed = installedFex.map { fex(it) }
            val next = LabNextStarts.describe(settings, installed)
            val plan = LabCommand.build(settings, LabTarget.BATTLENET, ctx(installedFex))
            assertEquals("$settings / $installedFex", !plan.ok, next.startBlocked)
            if (plan.ok) {
                assertEquals(plan.emulator, next.emulator)
                assertEquals(plan.fexVersion, next.fexVersion)
            }
        }
    }

    /** Die Kette: Schnellwahl 2 mit einem installierten FEX-Paket -> der nächste Lauf nutzt FEX (Einstellungen, Plan, Container-Felder, HODLL). */
    @Test
    fun quickPickTwoLeadsToFexInThePlanTheContainerAndTheLaunchEnvironment() {
        val installed = listOf(fex("lab-2508"))
        // 1. Schnellwahl 2 aus den Einstellungen "gebündelt".
        val picked = (LabQuickPicks.resolve(LabQuickPick.PROTON11_FEX, LabSettings(), installed, packages) as LabQuickPickResult.Applied).settings
        assertEquals(LabTranslator.FEX, picked.translator)
        assertEquals("lab-2508", picked.fexVersion)
        assertEquals("", picked.wine)
        // 2. gespeichert und wieder gelesen (so liest sie der Start).
        val stored = LabSettings.fromJson(picked.toJson())
        assertEquals(picked, stored)
        // 3. Startplan.
        val plan = LabCommand.build(stored, LabTarget.BATTLENET, ctx(setOf("lab-2508")))
        assertTrue(plan.issues.toString(), plan.ok)
        assertEquals("FEXCore", plan.emulator)
        assertEquals("lab-2508", plan.fexVersion)
        // 4. Container-Felder (so schreibt sie `LabContainer.prepare`): sie müssen unter denselben Namen stehen, die `Container.loadData` liest.
        val json: JSONObject = LabContainer.config(LabContainer.Base(JSONObject("{\"fexcoreVersion\":\"2605\"}")), plan, "")
        assertEquals("FEXCore", json.getString("emulator"))
        assertEquals("lab-2508", json.getString("fexcoreVersion"))
        // 5. Anzeige und HODLL.
        val next = LabNextStarts.describe(stored, installed)
        assertTrue(next.fexActive)
        assertEquals("libwow64fex.dll", next.hodll)
        assertTrue(next.notes.isEmpty())
    }

    @Test
    fun theBundledPickLeavesNoFexVersionInThePlan() {
        val plan = LabCommand.build(LabSettings(), LabTarget.BATTLENET, ctx(setOf("lab-2508")))
        assertEquals("Box64", plan.emulator)
        assertNull(plan.fexVersion)
        assertEquals("wowbox64.dll", LabNextStarts.hodll(plan.emulator))
    }

    @Test
    fun hodllFollowsTheLauncherRule() {
        assertEquals("libwow64fex.dll", LabNextStarts.hodll("FEXCore"))
        assertEquals("libwow64fex.dll", LabNextStarts.hodll("fexcore"))
        assertEquals("wowbox64.dll", LabNextStarts.hodll("Box64"))
        assertNull(LabNextStarts.hodll("none"))
    }

    /** Was nur der Quelltext belegt: die Namen und Regeln der Kette stehen im Code, den das Spiel und das Labor gemeinsam nutzen. */
    @Test
    fun theChainIsInTheSourceOfTheLauncher() {
        val container = File("src/main/java/com/winlator/container/Container.java").readText()
        val launcher = File("src/main/java/com/winlator/xenvironment/components/BionicProgramLauncherComponent.java").readText()
        val contents = File("src/main/java/com/winlator/contents/ContentsManager.java").readText()
        val profile = File("src/main/java/com/winlator/contents/ContentProfile.java").readText()
        for (key in listOf("emulator", "fexcoreVersion")) {
            assertTrue("$key is written", container.contains("data.put(\"$key\""))
            assertTrue("$key is read", container.contains("case \"$key\""))
        }
        // HODLL: `fexcore` -> libwow64fex.dll, `none` nichts, sonst wowbox64.dll
        assertTrue(launcher.contains("emulator.toLowerCase().equals(\"fexcore\")"))
        assertTrue(launcher.contains("envVars.put(\"HODLL\", \"libwow64fex.dll\")"))
        assertTrue(launcher.contains("envVars.put(\"HODLL\", \"wowbox64.dll\")"))
        // Die DLLs: bei einer anderen Version als der zuletzt angewendeten wird das Paket `fexcore-<Version>` gesucht und angewendet.
        assertTrue(launcher.contains("getProfileByEntryName(\"fexcore-\" + fexcoreVersion)"))
        assertTrue(launcher.contains("container.getExtra(\"appliedFexcoreVersion\")"))
        // Der Eintragsname `<Typ>-<Versionsname>` findet das Labor-Paket; Typname `FEXCore`.
        assertTrue(contents.contains("String typeAndVer = profile.type.toString() + \"-\" + verName;"))
        assertTrue(profile.contains("CONTENT_TYPE_FEXCORE(\"FEXCore\")"))
        assertTrue(LabPackages.fexProfileJson("lab-2508").contains("\"type\":\"FEXCore\""))
    }
}

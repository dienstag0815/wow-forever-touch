package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Selbsttest „Einrichtung prüfen“ (Auftrag labassistent, Punkt 4): Eingaben → ok/Hinweis/Fehler je Punkt, Abschnitt im Bericht. */
class LabSetupCheckTest {
    private val gb = 1024L * 1024 * 1024

    private val good = LabSetupInputs(
        gameContainerPresent = true,
        fexListed = true,
        fexEntryComplete = true,
        listErrors = 0,
        listEntries = 4,
        fexInstalledName = "lab-2508",
        wineName = "proton-11.0-90624-arm64ec-1",
        wineFolderPresent = true,
        prefixPresent = true,
        launcherExePresent = true,
        freeBytes = 50 * gb,
        useGameCopy = true,
        copy = LabGuideCopy(originalPresent = true, complete = true, finishedMs = 7_000L),
        copyWouldMount = true,
        originalPresent = true,
        original = OriginalCheckState(unchanged = true, checkedMs = 8_000L),
        originalReachable = false,
    )

    private fun level(r: LabSetupCheckResult, item: SetupItem) = r.findings.single { it.item == item }.level
    private fun reason(r: LabSetupCheckResult, item: SetupItem) = r.findings.single { it.item == item }.reason

    @Test fun everyItemHasExactlyOneFindingInOrder() {
        val r = LabSetupCheck.run(good, 1L)
        assertEquals(SetupItem.entries.toList(), r.findings.map { it.item })
    }

    @Test fun goodSetupIsAllOk() {
        val r = LabSetupCheck.run(good, 1L)
        assertTrue(r.findings.all { it.level == SetupLevel.OK })
        assertEquals(SetupItem.entries.size, r.count(SetupLevel.OK))
    }

    @Test fun gameContainerMissingIsAnError() {
        val r = LabSetupCheck.run(good.copy(gameContainerPresent = false), 1L)
        assertEquals(SetupLevel.ERROR, level(r, SetupItem.GAME_CONTAINER))
    }

    @Test fun fexPackageStates() {
        assertEquals(SetupReason.FEX_INSTALLED, reason(LabSetupCheck.run(good, 1L), SetupItem.FEX_PACKAGE))
        val missing = LabSetupCheck.run(good.copy(fexInstalledName = null), 1L)
        assertEquals(SetupReason.FEX_MISSING, reason(missing, SetupItem.FEX_PACKAGE))
        assertEquals(SetupLevel.ERROR, level(missing, SetupItem.FEX_PACKAGE))
        val notListed = LabSetupCheck.run(good.copy(fexInstalledName = null, fexListed = false), 1L)
        assertEquals(SetupReason.FEX_NOT_LISTED, reason(notListed, SetupItem.FEX_PACKAGE))
    }

    @Test fun packageListStates() {
        assertEquals(SetupLevel.ERROR, level(LabSetupCheck.run(good.copy(listErrors = 2), 1L), SetupItem.PACKAGE_LIST))
        assertEquals(SetupReason.LIST_FEX_ENTRY_INCOMPLETE, reason(LabSetupCheck.run(good.copy(fexEntryComplete = false), 1L), SetupItem.PACKAGE_LIST))
        // Ohne FEX-Eintrag in der Liste meldet der Punkt FEX den Fehler; die Liste selbst ist dann nicht „unvollständig“.
        assertEquals(SetupLevel.OK, level(LabSetupCheck.run(good.copy(fexListed = false, fexEntryComplete = false), 1L), SetupItem.PACKAGE_LIST))
    }

    @Test fun wineAndPrefixStates() {
        assertEquals(SetupLevel.ERROR, level(LabSetupCheck.run(good.copy(wineFolderPresent = false), 1L), SetupItem.WINE))
        assertEquals(SetupLevel.NOTE, level(LabSetupCheck.run(good.copy(wineFolderPresent = null), 1L), SetupItem.WINE))
        assertEquals(SetupLevel.NOTE, level(LabSetupCheck.run(good.copy(prefixPresent = false), 1L), SetupItem.PREFIX))
    }

    @Test fun launcherFileMissingIsANote() {
        assertEquals(SetupLevel.NOTE, level(LabSetupCheck.run(good.copy(launcherExePresent = false), 1L), SetupItem.LAUNCHER_FILE))
    }

    @Test fun freeSpaceWarnsBelowThreeGb() {
        assertEquals(SetupLevel.OK, level(LabSetupCheck.run(good.copy(freeBytes = 3 * gb), 1L), SetupItem.FREE_SPACE))
        assertEquals(SetupLevel.NOTE, level(LabSetupCheck.run(good.copy(freeBytes = 3 * gb - 1), 1L), SetupItem.FREE_SPACE))
        assertEquals(SetupLevel.NOTE, level(LabSetupCheck.run(good.copy(freeBytes = 0L), 1L), SetupItem.FREE_SPACE))
    }

    @Test fun copyStates() {
        fun r(copy: LabGuideCopy, use: Boolean = false, mount: Boolean = false) =
            LabSetupCheck.run(good.copy(copy = copy, useGameCopy = use, copyWouldMount = mount), 1L)
        val base = LabGuideCopy(originalPresent = true)
        assertEquals(SetupReason.COPY_NONE, reason(r(base), SetupItem.COPY))
        assertEquals(SetupReason.COPY_PARTIAL, reason(r(base.copy(partial = true)), SetupItem.COPY))
        assertEquals(SetupReason.COPY_RUNNING, reason(r(base.copy(running = true)), SetupItem.COPY))
        assertEquals(SetupLevel.ERROR, level(r(base.copy(layoutFailed = true)), SetupItem.COPY))
        assertEquals(SetupReason.COPY_SWITCH_ON_NOT_READY, reason(r(base, use = true), SetupItem.COPY))
        assertEquals(SetupReason.COPY_COMPLETE, reason(r(base.copy(complete = true)), SetupItem.COPY))
    }

    @Test fun copyDriveStates() {
        fun r(complete: Boolean, use: Boolean, mount: Boolean) =
            LabSetupCheck.run(good.copy(copy = LabGuideCopy(originalPresent = true, complete = complete), useGameCopy = use, copyWouldMount = mount), 1L)
        assertEquals(SetupReason.DRIVE_MOUNTED, reason(r(complete = true, use = true, mount = true), SetupItem.COPY_DRIVE))
        assertEquals(SetupReason.DRIVE_OFF, reason(r(complete = true, use = false, mount = false), SetupItem.COPY_DRIVE))
        assertEquals(SetupReason.DRIVE_NOT_USED, reason(r(complete = false, use = false, mount = false), SetupItem.COPY_DRIVE))
    }

    @Test fun originalFolderStates() {
        assertEquals(SetupLevel.NOTE, level(LabSetupCheck.run(good.copy(originalPresent = false), 1L), SetupItem.ORIGINAL_FOLDER))
    }

    @Test fun originalUnchangedStates() {
        fun r(o: OriginalCheckState) = LabSetupCheck.run(good.copy(original = o), 1L)
        assertEquals(SetupReason.UNCHANGED, reason(r(OriginalCheckState(unchanged = true, checkedMs = 5L)), SetupItem.ORIGINAL_UNCHANGED))
        assertEquals(SetupLevel.ERROR, level(r(OriginalCheckState(unchanged = false, acknowledged = false)), SetupItem.ORIGINAL_UNCHANGED))
        assertEquals(SetupReason.CHANGED_CONFIRMED, reason(r(OriginalCheckState(unchanged = false, acknowledged = true)), SetupItem.ORIGINAL_UNCHANGED))
        assertEquals(SetupReason.NEVER_CHECKED, reason(r(OriginalCheckState()), SetupItem.ORIGINAL_UNCHANGED))
        assertEquals(SetupLevel.NOTE, level(r(OriginalCheckState()), SetupItem.ORIGINAL_UNCHANGED))
    }

    @Test fun originalReachableStates() {
        assertEquals(SetupLevel.OK, level(LabSetupCheck.run(good.copy(originalReachable = false), 1L), SetupItem.ORIGINAL_NOT_REACHABLE))
        assertEquals(SetupLevel.ERROR, level(LabSetupCheck.run(good.copy(originalReachable = true), 1L), SetupItem.ORIGINAL_NOT_REACHABLE))
        assertEquals(SetupLevel.NOTE, level(LabSetupCheck.run(good.copy(originalReachable = null), 1L), SetupItem.ORIGINAL_NOT_REACHABLE))
    }

    @Test fun freshDeviceHasErrorsAndNotesButNoCrash() {
        val fresh = LabSetupInputs(
            gameContainerPresent = false, fexListed = true, fexEntryComplete = true, listErrors = 0, listEntries = 4, fexInstalledName = null,
            wineName = "x", wineFolderPresent = null, prefixPresent = false, launcherExePresent = false, freeBytes = 0L, useGameCopy = false,
            copy = LabGuideCopy(), copyWouldMount = false, originalPresent = false, original = OriginalCheckState(), originalReachable = null,
        )
        val r = LabSetupCheck.run(fresh, 1L)
        assertEquals(SetupItem.entries.size, r.findings.size)
        assertTrue(r.count(SetupLevel.ERROR) >= 2)
        assertTrue(r.count(SetupLevel.NOTE) >= 5)
    }

    // --- Bericht ---

    private val fmt: (Long) -> String = { "T$it" }

    @Test fun reportSectionHasTitleTimeSummaryAndOneLinePerItem() {
        val r = LabSetupCheck.run(good.copy(freeBytes = 1 * gb, launcherExePresent = false), 4_000L)
        val lines = LabSetupCheck.reportLines(r, fmt)
        assertEquals("== Setup check ==", lines[0])
        assertEquals("checked: T4000", lines[1])
        assertEquals("result: 10 ok, 2 notes, 0 errors", lines[2])
        assertEquals(3 + SetupItem.entries.size, lines.size)
        assertTrue(lines.any { it.startsWith("note   Free space: below 3 GB (1024 MB)") })
        assertTrue(lines.any { it.startsWith("ok     FEX package: installed (lab-2508)") })
    }

    @Test fun reportSectionSaysNotRunYet() {
        val lines = LabSetupCheck.reportLines(null, fmt)
        assertEquals("== Setup check ==", lines[0])
        assertTrue(lines[1].startsWith("not run yet"))
    }

    @Test fun reportContainsNoPathsOrSecrets() {
        val text = LabSetupCheck.reportLines(LabSetupCheck.run(good, 1L), fmt).joinToString("\n")
        assertTrue(!text.contains("/data/") && !text.contains("@") && !text.contains("\\"))
    }

    // --- Speichern ---

    @Test fun jsonRoundTrip() {
        val r = LabSetupCheck.run(good.copy(freeBytes = 1 * gb), 99L)
        val back = LabSetupCheck.parse(LabSetupCheck.toJson(r))
        assertNotNull(back)
        assertEquals(r, back)
    }

    @Test fun brokenJsonMeansNeverChecked() {
        assertNull(LabSetupCheck.parse(null))
        assertNull(LabSetupCheck.parse(""))
        assertNull(LabSetupCheck.parse("{kaputt"))
        assertNull(LabSetupCheck.parse("{\"f\":[]}"))
    }

    @Test fun unknownNamesFromAnotherBuildAreDropped() {
        val text = "{\"at\":5,\"f\":[{\"i\":\"GAME_CONTAINER\",\"l\":\"OK\",\"r\":\"CONTAINER_PRESENT\"},{\"i\":\"NEW_THING\",\"l\":\"OK\",\"r\":\"X\"}]}"
        val r = LabSetupCheck.parse(text)
        assertEquals(1, r?.findings?.size)
    }
}

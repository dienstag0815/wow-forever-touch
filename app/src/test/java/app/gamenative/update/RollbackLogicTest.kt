package app.gamenative.update

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import app.gamenative.update.RollbackLogic.GameFilesPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RollbackLogicTest {
    private fun release(tag: String, body: String = "", draft: Boolean = false, pre: Boolean = false, apk: Boolean = true, size: Long = 100L) = ReleaseEntry(
        tag = tag,
        title = tag,
        draft = draft,
        prerelease = pre,
        body = body,
        assets = if (apk) listOf(ReleaseAsset("app-$tag.apk", size, "https://git.example.org/d/$tag.apk", "", "")) else emptyList(),
    )

    private fun names(rows: List<VersionRow>) = rows.map { it.name }

    // ---- list: selection and sorting ----------------------------------------------------------------------------

    @Test
    fun rowsAreNewestFirstWhateverOrderTheServerSends() {
        val rows = RollbackLogic.rows(
            listOf(release("v2.3.1"), release("v2.3.3-touch9.5"), release("v2.3.3-touch9.4"), release("v2.3.2"), release("v2.10.0")),
            installedName = "2.3.2",
        )
        assertEquals(listOf("2.10.0", "2.3.3-touch9.5", "2.3.3-touch9.4", "2.3.2", "2.3.1"), names(rows))
    }

    @Test
    fun equalNamesKeepTheServerOrder() {
        val rows = RollbackLogic.rows(listOf(release("v2.3.2", "first"), release("2.3.2", "second")), "9.9.9")
        assertEquals(listOf("first", "second"), rows.map { it.notes })
    }

    @Test
    fun draftsReleasesWithoutApkOrTagAreLeftOut() {
        val rows = RollbackLogic.rows(
            listOf(release("v1.0.0", draft = true), release("v1.1.0", apk = false), release("", pre = false), release("v1.2.0")),
            "1.2.0",
        )
        assertEquals(listOf("1.2.0"), names(rows))
    }

    @Test
    fun prereleasesFollowTheSwitch() {
        val all = listOf(release("v1.0.0"), release("v1.1.0", pre = true))
        assertEquals(listOf("1.1.0", "1.0.0"), names(RollbackLogic.rows(all, "1.0.0", includePrereleases = true)))
        assertEquals(listOf("1.0.0"), names(RollbackLogic.rows(all, "1.0.0", includePrereleases = false)))
        assertTrue(RollbackLogic.rows(all, "1.0.0").first().prerelease)
    }

    @Test
    fun theSizeIsTheFirstApksSize() {
        assertEquals(4096L, RollbackLogic.rows(listOf(release("v1.0.0", size = 4096L)), "1.0.0").single().apkSize)
    }

    // ---- relation to the installed app --------------------------------------------------------------------------

    @Test
    fun relationIsNewerInstalledOlderByName() {
        val rows = RollbackLogic.rows(listOf(release("v2.3.3-touch9.5"), release("v2.3.3-touch9.4"), release("v2.3.3-touch9.3")), "2.3.3-touch9.4")
        assertEquals(listOf(VersionRelation.Newer, VersionRelation.Installed, VersionRelation.Older), rows.map { it.relation })
    }

    @Test
    fun theLeadingVOfATagIsIgnoredForTheInstalledMark() {
        assertEquals(VersionRelation.Installed, RollbackLogic.relation("v2.3.3", "V2.3.3"))
        assertEquals("2.3.3", RollbackLogic.nameOf(" v2.3.3 "))
    }

    private fun info(code: Int, name: String) = UpdateInfo(code, name, "x.apk", "https://git.example.org/x.apk", "", 1L, "")

    @Test
    fun theFeedsVersionCodeDecidesTheDirectionWhenThereIsOne() {
        assertEquals(VersionRelation.Older, RollbackLogic.relation(info(290, "9.9.9"), 293, "2.3.3"))
        assertEquals(VersionRelation.Newer, RollbackLogic.relation(info(300, "0.0.1"), 293, "2.3.3"))
        assertEquals(VersionRelation.Installed, RollbackLogic.relation(info(293, "0.0.1"), 293, "2.3.3"))
    }

    @Test
    fun withoutAVersionCodeTheNamesDecide() {
        assertEquals(VersionRelation.Older, RollbackLogic.relation(info(0, "2.3.2"), 293, "2.3.3"))
        assertEquals(VersionRelation.Newer, RollbackLogic.relation(info(0, "2.4.0"), 293, "2.3.3"))
        assertEquals(VersionRelation.Installed, RollbackLogic.relation(info(0, "v2.3.3"), 293, "2.3.3"))
    }

    // ---- short notes --------------------------------------------------------------------------------------------

    @Test
    fun shortNotesDropMarkdownMarks() {
        val body = "## Neu\n\n- **Radialmenü** schneller\n- `Backup` im Download-Ordner\n"
        assertEquals("Neu Radialmenü schneller Backup im Download-Ordner", RollbackLogic.shortNotes(body))
    }

    @Test
    fun shortNotesAreCutAtAWordWithAnEllipsis() {
        val body = "Wort ".repeat(60)
        val notes = RollbackLogic.shortNotes(body)
        assertTrue(notes.length <= 141)
        assertTrue(notes.endsWith("…"))
        assertFalse(notes.dropLast(1).endsWith(" "))
    }

    @Test
    fun shortNotesOfNothingAreEmpty() {
        assertEquals("", RollbackLogic.shortNotes(""))
        assertEquals("", RollbackLogic.shortNotes("\n  \n###\n"))
        assertEquals("Kurz", RollbackLogic.shortNotes("Kurz"))
    }

    // ---- file name of the old APK -------------------------------------------------------------------------------

    @Test
    fun apkFileNameKeepsAPlainReleaseNameElseBuildsOne() {
        assertEquals("WoW-Forever-2.3.2.apk", RollbackLogic.apkFileName(info(1, "2.3.2").copy(file = "WoW-Forever-2.3.2.apk")))
        assertEquals("wow-forever-2.3.2.apk", RollbackLogic.apkFileName(info(1, "2.3.2").copy(file = "../../etc/passwd")))
        assertEquals("wow-forever-2.3.2.apk", RollbackLogic.apkFileName(info(1, "2.3.2").copy(file = "")))
        assertEquals("wow-forever-2.3.2.apk", RollbackLogic.apkFileName(info(1, "2.3.2").copy(file = "notes.txt")))
        assertEquals("wow-forever-old.apk", RollbackLogic.apkFileName(info(1, "///").copy(file = "")))
    }

    // ---- what survives the uninstall ----------------------------------------------------------------------------

    private val pkg = "app.wowforever"
    private val dataDir = "/data/user/0/app.wowforever"

    @Test
    fun gameFilesOnSharedStorageSurvive() {
        assertEquals(GameFilesPlace.Shared, RollbackLogic.gameFilesPlace("/storage/emulated/0/WoW Forever", pkg, dataDir))
        assertEquals(GameFilesPlace.Shared, RollbackLogic.gameFilesPlace("/storage/SDKR-TEST/WoW Forever/", pkg, dataDir))
        assertEquals(GameFilesPlace.Shared, RollbackLogic.gameFilesPlace("/storage/emulated/0/Android/data/other.app/files", pkg, dataDir))
    }

    @Test
    fun gameFilesInTheAppsOwnFoldersAreDeleted() {
        assertEquals(GameFilesPlace.InAppStorage, RollbackLogic.gameFilesPlace("/storage/emulated/0/Android/data/app.wowforever/files/WoW", pkg, dataDir))
        assertEquals(GameFilesPlace.InAppStorage, RollbackLogic.gameFilesPlace("/storage/emulated/0/Android/obb/app.wowforever", pkg, dataDir))
        assertEquals(GameFilesPlace.InAppStorage, RollbackLogic.gameFilesPlace("/data/data/app.wowforever/files/game", pkg, dataDir))
        assertEquals(GameFilesPlace.InAppStorage, RollbackLogic.gameFilesPlace("/data/user/0/app.wowforever/files/game", pkg, dataDir))
    }

    @Test
    fun aSimilarPackageNameIsNotTheApp() {
        assertEquals(GameFilesPlace.Shared, RollbackLogic.gameFilesPlace("/data/user/0/app.wowforever2/files", pkg, dataDir))
    }

    @Test
    fun sizeTextUsesMegaAndGigabytes() {
        assertEquals("0 MB", RollbackLogic.sizeText(0))
        assertEquals("1 MB", RollbackLogic.sizeText(10))
        assertEquals("320 MB", RollbackLogic.sizeText(320L * 1024 * 1024))
        assertEquals("1.5 GB", RollbackLogic.sizeText(1536L * 1024 * 1024))
        assertEquals("27.0 GB", RollbackLogic.sizeText(27L * 1024 * 1024 * 1024))
    }

    // ---- dialog text --------------------------------------------------------------------------------------------

    private val target = VersionRow("v2.3.2", "2.3.2", "t", "", 1L, false, VersionRelation.Older)

    private fun ids(parts: List<DialogPart>) = parts.map { (it.text as UiText.Res).id }

    @Test
    fun dialogWithGameOnSharedStorageSaysItStays() {
        val parts = RollbackLogic.dialogParts(target, GameFilesPlace.Shared, "/storage/emulated/0/WoW Forever", 0L, "b.zip")
        assertEquals(
            listOf(
                R.string.launcher_rollback_why,
                R.string.launcher_rollback_steps,
                R.string.launcher_rollback_game_kept,
                R.string.launcher_rollback_lost_always,
                R.string.launcher_rollback_after,
            ),
            ids(parts),
        )
        assertEquals(UiText.res(R.string.launcher_rollback_game_kept, "/storage/emulated/0/WoW Forever"), parts[2].text)
        assertFalse(parts[2].warning)
        assertTrue(parts[3].warning)
    }

    @Test
    fun dialogWithGameInAppStorageWarnsWithTheSize() {
        val parts = RollbackLogic.dialogParts(target, GameFilesPlace.InAppStorage, "/data/x", 3L * 1024 * 1024 * 1024, "b.zip")
        assertEquals(UiText.res(R.string.launcher_rollback_game_lost_size, "3.0 GB"), parts[2].text)
        assertTrue(parts[2].warning)
    }

    @Test
    fun dialogWithGameInAppStorageAndUnknownSizeStillWarns() {
        val parts = RollbackLogic.dialogParts(target, GameFilesPlace.InAppStorage, "/data/x", 0L, "b.zip")
        assertEquals(UiText.res(R.string.launcher_rollback_game_lost), parts[2].text)
        assertTrue(parts[2].warning)
    }

    @Test
    fun dialogStepsNameBackupAndVersion() {
        val parts = RollbackLogic.dialogParts(target, GameFilesPlace.Shared, "/p", 0L, "wow-forever-touch-backup-x.zip")
        assertEquals(UiText.res(R.string.launcher_rollback_steps, "wow-forever-touch-backup-x.zip", "2.3.2"), parts[1].text)
    }

    // ---- backup after a fresh install ---------------------------------------------------------------------------

    private fun backup(stamp: String, device: String = "a1b2c3d4") = "wow-forever-touch-backup-${stamp}_$device.zip"

    @Test
    fun theNewestBackupWinsByItsStamp() {
        val names = listOf(
            backup("2026-11-10_09-00-00"),
            backup("2026-11-11_08-30-15"),
            backup("2026-11-11_08-30-14", "ffffffff"),
            "notes.zip",
            "wow-forever-touch-backup-broken.zip",
            backup("2026-11-11_08-30-15") + ".part",
        )
        assertEquals(backup("2026-11-11_08-30-15"), RollbackLogic.newestBackup(names))
    }

    @Test
    fun noBackupNoSuggestion() {
        assertNull(RollbackLogic.newestBackup(emptyList()))
        assertNull(RollbackLogic.newestBackup(listOf("a.zip", "wow-forever-touch-backup-.zip")))
        assertFalse(RollbackLogic.shouldSuggestImport(pending = true, candidate = null))
    }

    @Test
    fun theSuggestionComesOnceAfterAFreshInstall() {
        assertTrue(RollbackLogic.shouldSuggestImport(pending = true, candidate = backup("2026-11-11_08-30-15")))
        assertFalse(RollbackLogic.shouldSuggestImport(pending = false, candidate = backup("2026-11-11_08-30-15")))
    }

    @Test
    fun theNameTheBackupStoreWritesIsRecognised() {
        val name = app.gamenative.backup.BackupFormat.fileName(app.gamenative.backup.BackupFormat.fileStamp(java.util.Date()), app.gamenative.backup.BackupFormat.deviceTag("00112233445566778899aabbccddeeff"))
        assertTrue(name, RollbackLogic.isBackupName(name))
    }
}

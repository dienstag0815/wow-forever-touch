package app.gamenative.report

import app.gamenative.ui.screen.wow.WowClientDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConnectionLogFileTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun theFolderNamesAreTheOnesOfTheClientDownloader() {
        assertEquals(listOf(WowClientDownloader.FLAVOR_DIR, WowClientDownloader.LEGACY_FLAVOR_DIR), ConnectionLogFile.DEFAULT_FLAVOR_DIRS)
    }

    private fun log(root: File, dir: String, text: String, modified: Long) =
        File(root, "$dir/Logs").apply { mkdirs() }.let { File(it, "Connection.log").apply { writeText(text); setLastModified(modified) } }

    @Test
    fun theNewestNonEmptyLogOfTheTwoFoldersIsRead() {
        val root = tmp.newFolder("game")
        log(root, "_forever_", "forever", 2_000_000_000L)
        log(root, "_classic_beta_", "beta", 3_000_000_000L)
        assertEquals("beta", ConnectionLogFile.latestText(root))
        log(root, "_classic_beta_", "", 4_000_000_000L)
        assertEquals("forever", ConnectionLogFile.latestText(root))
    }

    @Test
    fun noLogGivesNull() {
        assertNull(ConnectionLogFile.latestText(tmp.newFolder("empty")))
        assertNull(ConnectionLogFile.latestText(File(tmp.root, "missing")))
    }
}

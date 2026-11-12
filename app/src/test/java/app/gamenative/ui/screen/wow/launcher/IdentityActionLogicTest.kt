package app.gamenative.ui.screen.wow.launcher

import app.gamenative.utils.MachineIdentityArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityActionLogicTest {
    private val archive = listOf(
        MachineIdentityArchive.Info("aaaaaaaa", 1, 2),
        MachineIdentityArchive.Info("bbbbbbbb", 3, 4),
    )

    @Test
    fun newIdentityIsOnlyPossibleWithAnIdentityNoRunningGameAndNoLock() {
        assertEquals(IdentityBlock.None, regenerateBlock(hasIdentity = true, gameBusy = false, lockedUntil = null))
        assertEquals(IdentityBlock.NoIdentity, regenerateBlock(hasIdentity = false, gameBusy = false, lockedUntil = null))
        assertEquals(IdentityBlock.GameRunning, regenerateBlock(hasIdentity = true, gameBusy = true, lockedUntil = null))
        assertEquals(IdentityBlock.Locked, regenerateBlock(hasIdentity = true, gameBusy = false, lockedUntil = 123L))
    }

    @Test
    fun runningGameBeatsTheLockInTheHint() {
        assertEquals(IdentityBlock.GameRunning, regenerateBlock(hasIdentity = true, gameBusy = true, lockedUntil = 123L))
    }

    @Test
    fun reuseIgnoresTheLockButNotTheRunningGame() {
        assertEquals(IdentityBlock.None, restoreBlock(hasIdentity = true, gameBusy = false))
        assertEquals(IdentityBlock.GameRunning, restoreBlock(hasIdentity = true, gameBusy = true))
        assertEquals(IdentityBlock.NoIdentity, restoreBlock(hasIdentity = false, gameBusy = false))
    }

    @Test
    fun dialogsOpenOnlyWhenTheButtonIsFree() {
        assertTrue(openRegenerate(IdentityBlock.None) is IdentityDialog.Regenerate)
        for (block in listOf(IdentityBlock.Locked, IdentityBlock.GameRunning, IdentityBlock.NoIdentity)) {
            assertTrue(openRegenerate(block) is IdentityDialog.None)
            assertTrue(openReuse(block, archive, 0) is IdentityDialog.None)
        }
    }

    @Test
    fun reuseDialogNamesTheChosenEntry() {
        val dialog = openReuse(IdentityBlock.None, archive, 1) as IdentityDialog.Reuse
        assertEquals(1, dialog.index)
        assertEquals("bbbbbbbb", dialog.fingerprint)
        assertTrue(openReuse(IdentityBlock.None, archive, 2) is IdentityDialog.None)
        assertTrue(openReuse(IdentityBlock.None, archive, -1) is IdentityDialog.None)
    }
}

package app.gamenative.ui.screen.wow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleNetSignInTest {

    @Test
    fun loginConsumed_missingHotfixLog_isFalse() {
        assertFalse(BattleNetSignIn.loginConsumed(null))
    }

    @Test
    fun loginConsumed_emptyHotfixLog_isFalse() {
        assertFalse(BattleNetSignIn.loginConsumed(0L))
    }

    @Test
    fun loginConsumed_atThreshold_isFalse() {
        assertFalse(BattleNetSignIn.loginConsumed(50_000L))
    }

    @Test
    fun loginConsumed_aboveThreshold_isTrue() {
        assertTrue(BattleNetSignIn.loginConsumed(50_001L))
    }
}

package app.gamenative.cooldown

import org.junit.Rule
import app.gamenative.TestTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonReloadRuleTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private fun rule() = AddonReloadRule(expectedBuild = 2, keyBuild = 2, settleMs = 4_000L)

    /** Feeds the same reading every 100 ms from [from] to [to]; returns the first action that is not NONE. */
    private fun feed(
        r: AddonReloadRule,
        build: Int,
        from: Long = 0L,
        to: Long = 5_000L,
        combat: Boolean = false,
        chat: Boolean? = false,
        disk: Boolean? = true,
    ): ReloadAction {
        var t = from
        while (t <= to) {
            val a = r.onStrip(build, combat, chat, disk, t)
            if (a != ReloadAction.NONE) return a
            t += 100
        }
        return ReloadAction.NONE
    }

    @Test
    fun bundledBuildMatchesTheRuleDefaults() {
        assertEquals(8, CooldownStrip.ADDON_BUILD)
        assertTrue(CooldownStrip.RELOAD_KEY_BUILD <= CooldownStrip.ADDON_BUILD)
    }

    @Test
    fun aCurrentAddonNeedsNothingAndFinishesTheQuestion() {
        val r = rule()
        assertFalse(r.finished)
        assertEquals(ReloadAction.NONE, feed(r, 2))
        assertTrue(r.finished)
        assertEquals("Addon: aktuell (Stand 2)", r.statusText())
        // a later older reading (cannot happen) is not acted on once the question is closed
        assertEquals(ReloadAction.NONE, feed(r, 0, 10_000, 20_000))
    }

    @Test
    fun anOldAddonIsReloadedOnlyAfterTheWaitInTheWorld() {
        val r = rule()
        assertEquals(ReloadAction.NONE, feed(r, 1, 0, 3_900))          // still settling
        assertEquals(ReloadAction.RELOAD_CHAT, feed(r, 1, 4_000, 5_000)) // build 1 has no reload key: typed /reload
    }

    @Test
    fun theKeyIsUsedOnlyWhereTheRunningAddonHasIt() {
        val withKey = AddonReloadRule(expectedBuild = 3, keyBuild = 2, settleMs = 1_000L)
        assertEquals(ReloadAction.RELOAD_KEY, feed(withKey, 2, 0, 2_000))
        val withoutKey = AddonReloadRule(expectedBuild = 3, keyBuild = 2, settleMs = 1_000L)
        assertEquals(ReloadAction.RELOAD_CHAT, feed(withoutKey, 1, 0, 2_000))
        // an addon from before the window section: build 0
        assertEquals(ReloadAction.RELOAD_CHAT, feed(rule(), 0, 0, 5_000))
    }

    @Test
    fun notInCombatAndNotWhileTheChatBoxIsOpen() {
        val r = rule()
        assertEquals(ReloadAction.NONE, feed(r, 0, 0, 10_000, combat = true))
        assertEquals(ReloadAction.NONE, feed(r, 0, 10_100, 12_000, chat = true))
        assertFalse(r.finished)
        // after the fight the action comes at once (the settle time has long passed)
        assertEquals(ReloadAction.RELOAD_CHAT, feed(r, 0, 12_100, 12_100))
    }

    @Test
    fun onlyOncePerSession() {
        val r = rule()
        assertEquals(ReloadAction.RELOAD_CHAT, feed(r, 0))
        assertTrue(r.finished)
        assertEquals(ReloadAction.NONE, feed(r, 0, 6_000, 30_000))
        assertTrue(r.statusText().contains("einmal neu laden"))
    }

    @Test
    fun anUnreadableSectionDecidesNothingAndRestartsTheWait() {
        val r = rule()
        assertEquals(ReloadAction.NONE, feed(r, 0, 0, 3_000))
        assertEquals(ReloadAction.NONE, feed(r, -1, 3_100, 3_200))   // interrupted
        assertEquals(ReloadAction.NONE, feed(r, 0, 3_300, 7_000))    // only 3.7 s since the restart
        assertEquals(ReloadAction.RELOAD_CHAT, feed(r, 0, 7_100, 8_000))
    }

    @Test
    fun aStaleDiskGivesAHintInsteadOfAReload() {
        val r = rule()
        assertEquals(ReloadAction.DISK_OUTDATED, feed(r, 1, disk = false))
        assertTrue(r.finished)
        assertTrue(r.statusText().contains("nicht aktuell"))
    }

    @Test
    fun anUnknownDiskStateCountsAsCurrent() {
        assertEquals(ReloadAction.RELOAD_CHAT, feed(rule(), 0, disk = null))
    }

    @Test
    fun combatComesFromTheFlagOfBuild2AndFromSecretValuesBefore() {
        assertTrue(AddonReloadRule.inCombat(2, CooldownStrip.WF_COMBAT, 0))
        assertFalse(AddonReloadRule.inCombat(2, 0, CooldownStrip.I_SECRET))
        assertTrue(AddonReloadRule.inCombat(1, 0, CooldownStrip.I_SECRET))
        assertFalse(AddonReloadRule.inCombat(0, CooldownStrip.WF_COMBAT, 0))
    }
}

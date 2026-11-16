package app.gamenative.lab

import app.gamenative.ui.screen.wow.LoginGuard
import app.gamenative.ui.screen.wow.voice.KeySink
import com.winlator.xserver.XKeycode
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anmelde-Knöpfe der Labor-Tastenleiste: Freigabe (alle Kombinationen), Prüfung vor dem Senden, Sperrzeit, Protokoll ohne Werte, Spielbereich unberührt. */
class LabSignInKeysTest {
    private class Recorder : KeySink {
        val events = ArrayList<String>()

        override fun press(key: XKeycode, keysym: Int) {
            events.add("+${key.name}")
        }

        override fun release(key: XKeycode) {
            events.add("-${key.name}")
        }
    }

    private val targets: List<LabTarget?> = listOf(null) + LabTarget.values().toList()

    // Frei erfundene Werte (keine echten Zugangsdaten).
    private val email = "qwv9k@zx-ymn.invalid"
    private val password = "P9#vLq~Zt2!xK"

    // --- Freigabe ---

    @Test
    fun stateForEveryCombination() {
        for (switchOn in listOf(false, true)) for (run in listOf(false, true)) for (target in targets) for (creds in listOf(false, true)) {
            val c = LabSignInContext(switchOn, run, target, creds)
            val expected = when {
                !switchOn || !run || target != LabTarget.BATTLENET -> LabSignInState.HIDDEN
                !creds -> LabSignInState.NO_CREDENTIALS
                else -> LabSignInState.READY
            }
            assertEquals("$c", expected, LabSignInKeys.state(c))
            assertEquals("$c", expected != LabSignInState.HIDDEN, LabSignInKeys.visible(c))
        }
    }

    @Test
    fun readyOnlyWithSwitchRunBattleNetAndCredentials() {
        assertEquals(LabSignInState.READY, LabSignInKeys.state(LabSignInContext(true, true, LabTarget.BATTLENET, true)))
        assertEquals(LabSignInState.NO_CREDENTIALS, LabSignInKeys.state(LabSignInContext(true, true, LabTarget.BATTLENET, false)))
        assertEquals(LabSignInState.HIDDEN, LabSignInKeys.state(LabSignInContext(false, true, LabTarget.BATTLENET, true)))
        assertEquals(LabSignInState.HIDDEN, LabSignInKeys.state(LabSignInContext(true, false, LabTarget.BATTLENET, true)))
    }

    @Test
    fun anyOtherTargetNeverShowsTheButtons() {
        for (target in LabTarget.values().filter { it != LabTarget.BATTLENET } + listOf(null)) for (creds in listOf(false, true)) {
            val c = LabSignInContext(switchOn = true, labRunActive = true, target = target, hasCredentials = creds)
            assertEquals("$c", LabSignInState.HIDDEN, LabSignInKeys.state(c))
            assertFalse(LabSignInKeys.mayTap(c, sequenceRunning = false, lastActivityMs = null, nowMs = 1_000_000L))
        }
    }

    @Test
    fun outsideALabRunNeverReleased() {
        for (switchOn in listOf(false, true)) for (target in targets) for (creds in listOf(false, true)) {
            val c = LabSignInContext(switchOn, labRunActive = false, target = target, hasCredentials = creds)
            assertEquals("$c", LabSignInState.HIDDEN, LabSignInKeys.state(c))
            assertFalse(LabSignInKeys.mayTap(c, false, null, 0L))
        }
        // Ohne Versuch hat die Labor-Sitzung weder Ziel noch Schalterstand.
        assertFalse(LabRun.isActive())
        assertEquals(null, LabRun.activeTarget())
        assertFalse(LabRun.signInKeysOn())
    }

    @Test
    fun theSwitchIsOffByDefaultAndSurvivesTheSettingsFormat() {
        assertFalse(LabSettings().signInKeys)
        assertTrue(LabSettings.fromJson(LabSettings(signInKeys = true).toJson()).signInKeys)
        assertFalse(LabSettings.fromJson(LabSettings(signInKeys = false).toJson()).signInKeys)
        assertFalse(LabSettings.fromJson(null).signInKeys)
        assertFalse(LabSettings.fromJson("{}").signInKeys)
        assertFalse(LabSettings.fromJson("{\"signInKeys\":\"yes\"}").signInKeys)
    }

    // --- Sperre ---

    @Test
    fun noTapWhileASequenceRunsOrWithinTwoSeconds() {
        val ready = LabSignInContext(true, true, LabTarget.BATTLENET, true)
        assertTrue(LabSignInKeys.mayTap(ready, sequenceRunning = false, lastActivityMs = null, nowMs = 10_000L))
        assertFalse("a sequence is running", LabSignInKeys.mayTap(ready, sequenceRunning = true, lastActivityMs = null, nowMs = 10_000L))
        assertFalse("double tap", LabSignInKeys.mayTap(ready, false, lastActivityMs = 10_000L, nowMs = 10_000L))
        assertFalse(LabSignInKeys.mayTap(ready, false, lastActivityMs = 10_000L, nowMs = 11_999L))
        assertTrue(LabSignInKeys.mayTap(ready, false, lastActivityMs = 10_000L, nowMs = 12_000L))
        assertFalse("clock went backwards", LabSignInKeys.mayTap(ready, false, lastActivityMs = 10_000L, nowMs = 9_000L))
        assertTrue(LabSignInKeys.COOLDOWN_MS >= 2_000L)
        assertEquals(0L, LabSignInKeys.cooldownLeftMs(null, 5L))
        assertEquals(1_500L, LabSignInKeys.cooldownLeftMs(1_000L, 1_500L))
    }

    // --- Prüfung vor dem Senden ---

    @Test
    fun checkCountsCharsWithoutAKey() {
        assertEquals(LabSignInCheck(0, false), LabSignInKeys.check(password.toCharArray()))
        assertEquals(LabSignInCheck(0, false), LabSignInKeys.check("Käse-ß".toCharArray()))
        assertEquals(LabSignInCheck(1, false), LabSignInKeys.check("ab\u0007cd".toCharArray()))
        val emoji = "a\uD83D\uDE00b"
        assertEquals(LabSignInCheck(2, false), LabSignInKeys.check(emoji.toCharArray()))
        assertEquals(LabSignInCheck(0, true), LabSignInKeys.check(CharArray(0)))
        assertTrue(LabSignInKeys.check(password.toCharArray()).sendable)
        assertFalse(LabSignInKeys.check(emoji.toCharArray()).sendable)
        assertFalse(LabSignInKeys.check(CharArray(0)).sendable)
    }

    @Test
    fun aSendableValueGoesOutWholeWithoutEnterOrTab() {
        val rec = Recorder()
        val value = password.toCharArray()
        assertTrue(LabSignInKeys.check(value).sendable)
        val result = LabKeySender(rec, sleep = {}, random = Random(1)).typeText(value)
        assertEquals(password.length, result.sent)
        assertEquals(0, result.skipped)
        assertFalse(result.cancelled)
        assertTrue("only characters: no Enter, Tab, Escape", rec.events.none { it.contains("ENTER") || it.contains("TAB") || it.contains("ESC") })
    }

    @Test
    fun takeKeepsOneValueAndWipesTheOther() {
        val values = LabSignInValues(email.toCharArray(), password.toCharArray())
        val taken = values.take(LabSignInKind.EMAIL)
        assertEquals(email, String(taken))
        assertTrue("the other value is wiped at once", values.password.all { it == '\u0000' })
        values.wipe()
        assertTrue(values.email.all { it == '\u0000' })
        assertTrue("the taken array is the same one, so wiping it wipes the value", taken.all { it == '\u0000' })
        val again = LabSignInValues(email.toCharArray(), password.toCharArray())
        assertEquals(password, String(again.take(LabSignInKind.PASSWORD)))
        assertTrue(again.email.all { it == '\u0000' })
    }

    // --- Protokoll ---

    @Test
    fun theLinesNameKindCountAndTimeOnly() {
        assertEquals("-- Sign-in key: email sent 18 chars after 12.3 s", LabSignInKeys.sentLine(LabSignInKind.EMAIL, 18, 12_340L))
        assertEquals("-- Sign-in key: password sent 13 chars after 0.0 s", LabSignInKeys.sentLine(LabSignInKind.PASSWORD, 13, -5L))
        assertEquals("-- Sign-in key: password not sent (unsupported chars: 2)", LabSignInKeys.notSentLine(LabSignInKind.PASSWORD, LabSignInCheck(2, false)))
        assertEquals("-- Sign-in key: email not sent (empty)", LabSignInKeys.notSentLine(LabSignInKind.EMAIL, LabSignInCheck(0, true)))
        assertEquals("-- Sign-in key: password cancelled after 4 chars at 20.0 s", LabSignInKeys.cancelledLine(LabSignInKind.PASSWORD, 4, 20_000L))
    }

    @Test
    fun theHeaderSaysWhetherTheButtonsAreOn() {
        assertEquals("Sign-in buttons: off", LabSignInKeys.headerLine(false))
        assertEquals("Sign-in buttons: on", LabSignInKeys.headerLine(true))
        for (on in listOf(false, true)) {
            val settings = LabSettings(signInKeys = on)
            val plan = LabCommand.build(settings, LabTarget.BATTLENET, LabCommandContext(baseEnv = "A=b", battleNetExe = "C:\\Bnet\\Battle.net.exe", identityHostname = null))
            val text = LabProtocol.header(LabProtocolInfo(1_700_000_000_000L, "1.0", "M", "14", settings, plan, 0L))
            assertTrue(text.lines().contains("Sign-in buttons: ${if (on) "on" else "off"}"))
        }
    }

    @Test
    fun aProtocolAfterARunHasNeitherTheValuesNorPartsOfThemFromFourChars() {
        val settings = LabSettings(signInKeys = true)
        val plan = LabCommand.build(settings, LabTarget.BATTLENET, LabCommandContext(baseEnv = "A=b", battleNetExe = "C:\\Bnet\\Battle.net.exe", identityHostname = null))
        val text = buildString {
            append(LabProtocol.header(LabProtocolInfo(1_700_000_000_000L, "1.0", "M", "14", settings, plan, 0L)))
            appendLine(LabSignInKeys.sentLine(LabSignInKind.EMAIL, email.length, 41_000L))
            appendLine(LabSignInKeys.sentLine(LabSignInKind.PASSWORD, password.length, 47_500L))
            appendLine(LabSignInKeys.notSentLine(LabSignInKind.PASSWORD, LabSignInCheck(1, false)))
            appendLine(LabSignInKeys.cancelledLine(LabSignInKind.EMAIL, 3, 50_000L))
            // Eingabe-Abschnitt nach den Tipps der Quelle "sign-in key".
            val stats = LabInputStats(startedMs = 1_000_000L, nowMs = { 1_000_500L })
            for (ch in email + password) stats.onKeyPress(LabInputSource.SIGNIN, LabFocus(0x20, "Chrome_WidgetWin_0", "Battle.net"), keyboardVisible = false)
            stats.lines().forEach { appendLine(it) }
            append(LabProtocol.footer(0, 0L, 60_000L, "ended"))
        }
        assertTrue(text.contains("sign-in key: ${email.length + password.length}"))
        for (secret in listOf(email, password)) {
            assertFalse(text.contains(secret))
            for (i in 0..secret.length - 4) {
                val part = secret.substring(i, i + 4)
                assertFalse("part \"$part\" of a value is in the protocol", text.contains(part))
            }
        }
    }

    @Test
    fun theInputSourceHasItsOwnLabel() {
        assertNotNull(LabInputSource.values().firstOrNull { it == LabInputSource.SIGNIN })
        assertEquals("sign-in key", LabInputSource.SIGNIN.label)
        val labels = LabInputSource.values().map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    // --- Spielbereich unverändert ---

    @Test
    fun theGameAreaGuardStaysFalseInTheLab() {
        assertFalse(LoginGuard.mayType(LabIds.CONTAINER_ID, labActive = true))
        assertFalse(LoginGuard.mayType(LabIds.CONTAINER_ID, labActive = false))
        assertFalse(LoginGuard.mayType(null, labActive = true))
        assertTrue(LoginGuard.mayType(LabIds.WOW_CONTAINER_ID, labActive = false))
        assertFalse(LoginGuard.mayWriteFile(labActive = true))
    }
}

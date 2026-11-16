package app.gamenative.ui.screen.wow

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Sperre der gespeicherten Zugangsdaten im Battle.net-Labor (Punkt 4 von labstart2). */
class LoginGuardTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val login = BattleNetSignIn.Login("someone@example.org", "secret-password")

    @Test
    fun typingIsRefusedInTheLabContainerAndDuringALabRun() {
        assertFalse(LoginGuard.mayType("bnetlab", labActive = false))
        assertFalse(LoginGuard.mayType("bnetlab", labActive = true))
        assertFalse(LoginGuard.mayType("wow_forever", labActive = true))
        assertFalse(LoginGuard.mayType(null, labActive = true))
    }

    @Test
    fun typingStaysAllowedForTheGame() {
        assertTrue(LoginGuard.mayType("wow_forever", labActive = false))
        assertTrue(LoginGuard.mayType(null, labActive = false))
        assertTrue(LoginGuard.mayType("any_other_container", labActive = false))
    }

    @Test
    fun theLoginFileIsNeverWrittenDuringALabRun() {
        assertFalse(LoginGuard.mayWriteFile(labActive = true))
        assertTrue(LoginGuard.mayWriteFile(labActive = false))
        var watched = 0
        prepareLoginFile(LoginLevel.SELF, tmp.root, load = { login }, watch = { watched++ }, mayWrite = { false })
        assertFalse(File(File(tmp.root, "_forever_"), "login.txt").exists())
        assertEquals(0, watched)
        var loaded = false
        prepareLoginFile(LoginLevel.SELF, tmp.root, load = { loaded = true; login }, watch = {}, mayWrite = { false })
        assertFalse("die Daten werden nicht einmal geladen", loaded)
    }

    @Test
    fun theGameStillWritesItsLoginFileWhenAllowed() {
        var watched = 0
        prepareLoginFile(LoginLevel.SELF, tmp.root, load = { login }, watch = { watched++ }, mayWrite = { true })
        assertEquals(1, watched)
    }

    // --- Es gibt keinen weiteren Weg: Prüfung der Quelltexte (Stelle x Sperre) ---

    private val wowDir = File("src/main/java/app/gamenative/ui/screen/wow")
    private fun text(name: String) = File(wowDir, name).readText()
    private fun allKotlin(): List<File> = File("src/main/java").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
    private fun filesContaining(needle: String): Set<String> = allKotlin().filter { needle in it.readText() }.map { it.name }.toSet()

    @Test
    fun onlyLoginFileTypesTheCredentials() {
        // LoginTypist tippt; erzeugt wird er nur in LoginFill.kt (Definition in LoginTyping.kt).
        assertEquals(setOf("LoginFill.kt", "LoginTyping.kt"), filesContaining("LoginTypist("))
    }

    @Test
    fun onlyTheButtonAndTheMenuCallLoginFillStart() {
        assertEquals(setOf("LoginFillButton.kt", "WowQuickSettings.kt"), filesContaining("LoginFill.start("))
    }

    @Test
    fun loginFillStartAsksTheGuardBeforeItLoadsTheSecret() {
        val source = text("LoginFill.kt")
        val start = source.indexOf("fun start(")
        val guard = source.indexOf("LoginGuard.mayType()", start)
        val load = source.indexOf("BattleNetSignIn.loadSecret(", start)
        assertTrue(start in 0 until guard)
        assertTrue("Sperre vor dem Laden der Daten", guard in 0 until load)
    }

    @Test
    fun theSecretIsLoadedForTypingOnlyInLoginFill() {
        // loadSecret: LoginFill (tippen), ErrorReport/LabReport (nur schwärzen), Definition in BattleNetSignIn.
        val callers = filesContaining("loadSecret(") - "BattleNetSignIn.kt"
        assertEquals(setOf("LoginFill.kt", "ErrorReport.kt", "LabReport.kt"), callers)
    }

    @Test
    fun theLoginFileIsWrittenOnlyThroughPrepareLoginFile() {
        assertEquals(setOf("BattleNetSignIn.kt", "LoginLevel.kt"), filesContaining("writeLoginFile("))
        val source = text("LoginLevel.kt")
        val prepare = source.indexOf("internal fun prepareLoginFile(")
        assertTrue(source.indexOf("if (!mayWrite()) return", prepare) > prepare)
        assertTrue(source.indexOf("if (!mayWrite()) return", prepare) < source.indexOf("BattleNetSignIn.writeLoginFile(", prepare))
    }

    @Test
    fun buttonAndMenuRowsAskTheGuard() {
        assertTrue(text("LoginFillButton.kt").contains("if (!LoginGuard.mayType(containerId)) return"))
        val menu = File("src/main/java/app/gamenative/ui/component/QuickMenu.kt").readText()
        assertTrue(menu.contains("inLogin = wowQuick.inLogin() && LoginGuard.mayType(container?.id)"))
        assertTrue(menu.contains("mutableStateOf(LoginGuard.mayType(container?.id))"))
        // Die Zeilen erscheinen nur bei inLogin (QuickMenuLayout).
        assertTrue(text("QuickMenuLayout.kt").contains("if (ctx.inLogin) {"))
    }

    @Test
    fun everyStartOfAnEnvironmentTellsTheGuardWhichContainerRuns() {
        val screen = File("src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt").readText()
        assertTrue(screen.contains("LoginGuard.setActiveContainer(container.id)"))
        assertTrue(screen.contains("LoginFillButton(menuOpen = showQuickMenu, containerId = container.id)"))
    }

    @Test
    fun noOtherCodeTypesTheStoredCredentials() {
        // Wer die gespeicherten Zugangsdaten sonst anfasst, hat mit Tippen nichts zu tun (Stelle S7 und S8 des Berichts): kein Bezug.
        for (name in listOf("ChatActionRunner.kt", "XServerKeySink.kt", "VoiceChatController.kt")) {
            val file = allKotlin().firstOrNull { it.name == name } ?: continue
            assertFalse("$name", file.readText().contains("BattleNetSignIn"))
            assertFalse("$name", file.readText().contains("loadSecret"))
        }
    }
}

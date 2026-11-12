package app.gamenative.ui.screen.wow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.gamenative.TestTexts
import app.gamenative.report.ErrorReport
import app.gamenative.ui.components.TextRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Die drei Stufen der Anmeldung: Wahl, Wechsel, Spielstart, Knopf, Bereitschaft, Texte. Die Daten sind erfundene Testwerte. */
@RunWith(RobolectricTestRunner::class)
class LoginLevelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testEmail = "mail@example.org"
    private val testPassword = "Pw!1-test"

    @Before
    fun clean() {
        wowPrefs(context).edit().clear().commit()
        BattleNetSignIn.forget(context)
        LoginTouchGate.setFillWanted(false)
        LoginTouchGate.setHideEnabled(context, false)
    }

    /** Legt Daten ab, ohne den Schlüsselspeicher zu brauchen (den gibt es im JVM-Test nicht): E-Mail und ein Platzhalter. */
    private fun saveData() {
        context.getSharedPreferences("battle_net_login", Context.MODE_PRIVATE)
            .edit().putString("email", testEmail).putString("password", "sealed-placeholder").commit()
    }

    private fun loginFile(dir: String = WowClientDownloader.FLAVOR_DIR): File = File(File(tmp.root, dir), "login.txt")

    private fun putLoginFile(dir: String = WowClientDownloader.FLAVOR_DIR) {
        loginFile(dir).apply { parentFile?.mkdirs(); writeText("$testEmail\n$testPassword\n") }
    }

    // --- Welche Stufe gilt ---

    @Test
    fun newPlayersStartOnOffAndOnlyNewDataMakesItFill() {
        assertEquals(LoginLevel.OFF, LoginLevels.resolve(null, hasSavedData = false))
        assertEquals(LoginLevel.FILL, LoginLevels.afterSave(LoginLevel.OFF))
        assertEquals(LoginLevel.OFF, LoginLevelStore.current(context))
        val before = LoginLevelStore.current(context)
        saveData()
        LoginLevelStore.recordSaved(context, before)
        assertEquals("Standard für neue Daten", LoginLevel.FILL, LoginLevelStore.current(context))
        assertTrue(LoginTouchGate.needsSignal())
    }

    @Test
    fun existingInstallationsWithSavedDataKeepSignInAutomatically() {
        saveData()
        assertNull("nichts gewählt", LoginLevelStore.stored(context))
        assertEquals(LoginLevel.SELF, LoginLevelStore.current(context))
        assertTrue(LoginLevels.writesLoginFile(LoginLevelStore.current(context)))
        // Neue Daten ändern eine gewählte Stufe nicht.
        LoginLevelStore.recordSaved(context, LoginLevelStore.current(context))
        assertEquals(LoginLevel.SELF, LoginLevelStore.current(context))
        assertEquals(LoginLevel.SELF, LoginLevels.afterSave(LoginLevel.SELF))
        assertEquals(LoginLevel.FILL, LoginLevels.afterSave(LoginLevel.FILL))
    }

    @Test
    fun theChosenLevelWinsOverTheSavedData() {
        saveData()
        LoginLevelStore.change(context, tmp.root, LoginLevel.FILL)
        assertEquals(LoginLevel.FILL, LoginLevelStore.current(context))
        assertTrue("Wechsel auf Ausfüllen lässt die Daten stehen", BattleNetSignIn.hasSaved(context))
    }

    @Test
    fun onlySignInAutomaticallyWritesTheLoginFile() {
        assertFalse(LoginLevels.writesLoginFile(LoginLevel.OFF))
        assertFalse(LoginLevels.writesLoginFile(LoginLevel.FILL))
        assertTrue(LoginLevels.writesLoginFile(LoginLevel.SELF))
    }

    // --- Wechsel entfernt die login.txt ---

    @Test
    fun switchingToFillRemovesALeftoverLoginFileAtOnce() {
        saveData()
        putLoginFile()
        putLoginFile(WowClientDownloader.LEGACY_FLAVOR_DIR)
        LoginLevelStore.change(context, tmp.root, LoginLevel.FILL)
        assertFalse(loginFile().exists())
        assertFalse(loginFile(WowClientDownloader.LEGACY_FLAVOR_DIR).exists())
    }

    @Test
    fun switchingToOffRemovesTheLoginFileAndTheSavedData() {
        saveData()
        putLoginFile()
        LoginLevelStore.change(context, tmp.root, LoginLevel.OFF)
        assertFalse(loginFile().exists())
        assertFalse("Aus heißt: nichts gespeichert", BattleNetSignIn.hasSaved(context))
        assertEquals(LoginLevel.OFF, LoginLevelStore.current(context))
        assertFalse(LoginTouchGate.needsSignal())
    }

    @Test
    fun switchingToSignInAutomaticallyKeepsAFileThatIsThere() {
        saveData()
        putLoginFile()
        LoginLevelStore.change(context, tmp.root, LoginLevel.SELF)
        assertTrue(loginFile().exists())
    }

    @Test
    fun forgettingTheLoginGoesBackToOffAndRemovesTheFile() {
        saveData()
        LoginLevelStore.change(context, tmp.root, LoginLevel.SELF)
        putLoginFile()
        LoginLevelStore.forget(context, tmp.root)
        assertEquals(LoginLevel.OFF, LoginLevelStore.current(context))
        assertFalse(loginFile().exists())
        assertFalse(BattleNetSignIn.hasSaved(context))
    }

    // --- Spielstart ---

    private val login = BattleNetSignIn.Login(testEmail, testPassword)

    @Test
    fun gameStartWritesTheLoginFileOnlyForSignInAutomatically() {
        var watched = 0
        prepareLoginFile(LoginLevel.SELF, tmp.root, load = { login }, watch = { watched++ })
        assertTrue(loginFile().exists())
        assertEquals(1, watched)
        loginFile().delete()
        for (level in listOf(LoginLevel.OFF, LoginLevel.FILL)) {
            var loaded = false
            prepareLoginFile(level, tmp.root, load = { loaded = true; login }, watch = { watched++ })
            assertFalse("$level schreibt keine login.txt", loginFile().exists())
            assertFalse("$level lädt die Daten nicht einmal", loaded)
        }
        assertEquals("keine Überwachung ohne Datei", 1, watched)
    }

    @Test
    fun gameStartInFillRemovesALeftoverLoginFile() {
        putLoginFile()
        prepareLoginFile(LoginLevel.FILL, tmp.root, load = { login }, watch = {})
        assertFalse(loginFile().exists())
    }

    // --- Knopf "Anmeldedaten ausfüllen" ---

    @Test
    fun fillButtonIsVisibleInTheLoginScreenOfTheFillLevelOnly() {
        for (level in LoginLevel.entries) for (saved in listOf(false, true)) for (world in listOf(false, true)) for (menu in listOf(false, true)) {
            val expected = level == LoginLevel.FILL && saved && !world && !menu
            assertEquals("$level gespeichert=$saved Welt=$world Menü=$menu", expected, LoginLevels.fillButtonVisible(level, saved, world, menu))
        }
        assertTrue("Login: ja", LoginLevels.fillButtonVisible(LoginLevel.FILL, true, inWorld = false, menuOpen = false))
        assertFalse("Welt: nein", LoginLevels.fillButtonVisible(LoginLevel.FILL, true, inWorld = true, menuOpen = false))
    }

    @Test
    fun theFillLevelAsksForTheWorldSignalEvenWithoutHidingTheTouchControls() {
        assertTrue(LoginLevels.needsWorldSignal(LoginLevel.FILL))
        assertFalse(LoginLevels.needsWorldSignal(LoginLevel.OFF))
        assertFalse(LoginLevels.needsWorldSignal(LoginLevel.SELF))
        LoginTouchGate.setHideEnabled(context, false)
        LoginLevelStore.change(context, tmp.root, LoginLevel.FILL)
        LoginTouchGate.reset(context)
        assertTrue(LoginTouchGate.needsSignal())
        assertFalse(LoginTouchGate.inWorld())
        LoginTouchGate.onPass(1_000, true)
        assertTrue("Welt erkannt ohne Ausblenden", LoginTouchGate.inWorld())
        LoginLevelStore.change(context, tmp.root, LoginLevel.SELF)
        LoginTouchGate.reset(context)
        assertFalse(LoginTouchGate.needsSignal())
        LoginTouchGate.setHideEnabled(context, true)
    }

    // --- Was eintippen ---

    @Test
    fun theTypedFieldsDefaultToEmailAndPasswordAndTheChoiceIsKept() {
        assertEquals(FillScope.EMAIL_AND_PASSWORD, LoginLevelStore.scope(context))
        assertEquals(LoginAction.FILL, FillScope.EMAIL_AND_PASSWORD.action)
        LoginLevelStore.setScope(context, FillScope.PASSWORD_ONLY)
        assertEquals(FillScope.PASSWORD_ONLY, LoginLevelStore.scope(context))
        assertEquals(LoginAction.PASSWORD_ONLY, FillScope.PASSWORD_ONLY.action)
        // Keine der beiden Eintipp-Arten sendet ab.
        assertTrue(FillScope.entries.none { it.action == LoginAction.FULL })
    }

    // --- Bereitschaft ---

    private fun readiness(level: LoginLevel, saved: Boolean) = readinessSummary(
        ReadinessInput(
            checked = true, programExists = true, dataExists = true, hasForever = true, isLegacyBeta = false, isWrongGame = false,
            hasStorageAccess = true, hasSavedLogin = saved, loginLevel = level,
        ),
    )

    @Test
    fun offAndFillWithDataAreNoDeficiencyAndBlockNeitherPlayNorCountdown() {
        assertTrue(LoginLevels.ready(LoginLevel.OFF, hasSavedData = false))
        assertTrue(LoginLevels.ready(LoginLevel.FILL, hasSavedData = true))
        assertTrue(LoginLevels.ready(LoginLevel.SELF, hasSavedData = true))
        assertFalse("ohne Daten fehlt die Anmeldung", LoginLevels.ready(LoginLevel.FILL, hasSavedData = false))
        assertTrue(readiness(LoginLevel.OFF, saved = false).allReady)
        assertTrue(readiness(LoginLevel.FILL, saved = true).allReady)
        assertTrue(readiness(LoginLevel.SELF, saved = true).allReady)
        val missing = readiness(LoginLevel.FILL, saved = false)
        assertFalse(missing.allReady)
        assertEquals(ReadyFix.Login, missing.open.single().fix)
    }

    @Test
    fun theReadinessLineShowsTheLevel() {
        fun text(level: LoginLevel, saved: Boolean, language: String): String {
            val item = readiness(level, saved).items.single { it.key == ReadyKey.Login }
            val ref = (item.label as app.gamenative.ui.screen.wow.launcher.UiText.Res)
            return TestTexts.text(ref.id, ref.args, language)
        }
        assertEquals("Anmeldung: Aus", text(LoginLevel.OFF, false, "de"))
        assertEquals("Anmeldung: Ausfüllen", text(LoginLevel.FILL, true, "de"))
        assertEquals("Anmeldung: Selbst anmelden", text(LoginLevel.SELF, true, "de"))
        assertEquals("Login: Off", text(LoginLevel.OFF, false, "en"))
        assertEquals("Login: Fill in", text(LoginLevel.FILL, true, "en"))
        assertEquals("Login: Sign in automatically", text(LoginLevel.SELF, true, "en"))
    }

    // --- Texte de + en ---

    @Test
    fun onlySwitchingToOffWithSavedDataAsksFirst() {
        for (target in LoginLevel.entries) {
            for (saved in listOf(true, false)) {
                val expected = target == LoginLevel.OFF && saved
                assertEquals("$target, gespeichert=$saved", expected, LoginLevels.needsConfirm(target, saved))
            }
        }
    }

    private fun both(res: Int): Pair<String, String> = TestTexts.text(res, emptyList(), "de") to TestTexts.text(res, emptyList(), "en")

    @Test
    fun everyTextOfTheNewLevelsExistsInGermanAndEnglish() {
        val resources = LoginLevel.entries.flatMap { listOf(it.labelRes, it.descriptionRes, it.readyRes) } +
            FillScope.entries.flatMap { listOf(it.labelRes, it.descriptionRes) } +
            listOf(
                LoginAction.FILL.doneRes, app.gamenative.R.string.game_fill_button, app.gamenative.R.string.game_qm_row_fill,
                app.gamenative.R.string.launcher_login_level_row, app.gamenative.R.string.launcher_fill_scope_row,
                app.gamenative.R.string.launcher_login_off_confirm_title, app.gamenative.R.string.launcher_login_off_confirm_text,
                app.gamenative.R.string.launcher_login_off_confirm_delete, app.gamenative.R.string.launcher_login_off_confirm_cancel,
            )
        for (res in resources) {
            val (de, en) = both(res)
            assertTrue("de leer: $res", de.isNotBlank())
            assertTrue("en leer: $res", en.isNotBlank())
            assertNotEquals("de und en gleich: $de", de, en)
        }
    }

    @Test
    fun theExplanationsSayWhatMattersToThePlayer() {
        val self = both(LoginLevel.SELF.descriptionRes)
        assertTrue(self.first.contains("Authenticator"))
        assertTrue(self.second.contains("authenticator"))
        val password = both(FillScope.PASSWORD_ONLY.descriptionRes)
        assertTrue(password.first, password.first.contains("Passwortfeld") && password.first.contains("E-Mail-Feld"))
        assertTrue(password.second, password.second.contains("password field") && password.second.contains("email field"))
        assertEquals("Ausfüllen, nicht senden", both(app.gamenative.R.string.game_qm_row_fill).first)
        assertEquals("Anmeldedaten ausfüllen", both(app.gamenative.R.string.game_fill_button).first)
        assertEquals(listOf("Aus", "Ausfüllen (ich sende selbst ab)", "Selbst anmelden"), LoginLevel.entries.map { both(it.labelRes).first })
    }

    // --- Fehlerbericht und Quelltext: keine Daten ---

    @Test
    fun theReportNamesTheLevelButNeverTheData() {
        saveData()
        LoginLevelStore.change(context, tmp.root, LoginLevel.FILL)
        val section = ErrorReport.loginSection(context)
        assertTrue(section, section.contains("Login saved: yes"))
        assertTrue(section, section.contains("Login mode: fill"))
        assertTrue(section, section.contains("Login fill scope: all"))
        for (secret in listOf(testEmail, testPassword, "sealed-placeholder", "example.org")) {
            assertFalse("Bericht enthält Daten: $secret", section.contains(secret))
        }
    }

    @Test
    fun noteTextsOfTheFillPathContainNoDataAndNoCount() {
        val resources = listOf(
            LoginAction.FILL.doneRes, app.gamenative.R.string.game_note_typing, app.gamenative.R.string.game_note_typing_cancelled,
            app.gamenative.R.string.game_note_typing_failed, app.gamenative.R.string.game_note_inworld, app.gamenative.R.string.game_note_notready,
            app.gamenative.R.string.game_note_nologin,
        )
        for (language in listOf("de", "en")) for (res in resources) {
            val text = TestTexts.text(TextRef(res), language)
            assertFalse(text, text.any { it.isDigit() })
            assertFalse(text, text.contains("@"))
        }
    }

    @Test
    fun theTypingCodeNeverLogsOrCopiesData() {
        val dir = File("src/main/java/app/gamenative/ui/screen/wow")
        for (name in listOf("LoginFill.kt", "LoginFillButton.kt", "LoginLevel.kt", "LoginTyping.kt")) {
            val source = File(dir, name).takeIf { it.isFile }?.readText() ?: continue
            assertFalse("$name: Zwischenablage", source.contains("Clipboard"))
            // Timber nur mit dem Klassennamen einer Ausnahme oder einem festen Text, nie mit Feldern der Anmeldung.
            for (line in source.lines().filter { it.contains("Timber.") }) {
                assertFalse("$name: $line", Regex("""(?i)email|password|secret|login\.""").containsMatchIn(line.substringAfter("Timber.")))
            }
        }
    }
}

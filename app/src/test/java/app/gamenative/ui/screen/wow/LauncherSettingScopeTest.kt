package app.gamenative.ui.screen.wow

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSettingScopeTest {

    private val infoOnly = setOf(LauncherSetting.AppVersion, LauncherSetting.ClientBuild, LauncherSetting.ControlHint)

    @Test
    fun everySettingOfThePageHasAScope() {
        // settingScope() ist ein vollständiges when; dazu: jede Einstellung der Gruppen hat ein Abzeichen, außer reine Anzeigen.
        val onPage = settingsGroups().flatMap { it.settings } + hiddenGameSettings()
        assertEquals(LauncherSetting.entries.toSet(), onPage.toSet())
        for (setting in onPage) {
            val scope = settingScope(setting)
            if (setting in infoOnly) {
                assertEquals(setting.name, ScopeKind.InfoOnly, scope.kind)
                assertNull(setting.name, scope.badgeText)
            } else {
                assertNotNull("kein Abzeichen für ${setting.name}", scope.badgeText)
                assertTrue(setting.name, scope.badgeText != null)
            }
        }
    }

    @Test
    fun onlyDisplayRowsCarryNoBadge() {
        val withoutBadge = LauncherSetting.entries.filter { settingScope(it).badgeText == null }.toSet()
        assertEquals(infoOnly, withoutBadge)
    }

    @Test
    fun inGameKindsNameAPlaceAndOnlyHereDoesNot() {
        for (setting in LauncherSetting.entries) {
            val scope = settingScope(setting)
            when (scope.kind) {
                ScopeKind.AlsoInGame, ScopeKind.Partly -> {
                    assertNotNull("Ort fehlt: ${setting.name}", scope.place)
                    val badge = scope.badgeText as UiText.Res
                    assertEquals(setting.name, listOf<Any>(scope.place!!), badge.args)
                    assertFalse(setting.name, scope.nextStart)
                }
                ScopeKind.OnlyHere, ScopeKind.InfoOnly -> assertNull(setting.name, scope.place)
            }
        }
    }

    @Test
    fun badgeTextsFollowTheAgreedWording() {
        assertEquals(UiText.res(R.string.launcher_badge_here_next), settingScope(LauncherSetting.Region).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_here_next), settingScope(LauncherSetting.Resolution).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_here_next), settingScope(LauncherSetting.Fps).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_here_next), settingScope(LauncherSetting.Graphics).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_here_next), settingScope(LauncherSetting.GameFolder).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_here), settingScope(LauncherSetting.AutoStart).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_here), settingScope(LauncherSetting.StartCheck).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_here), settingScope(LauncherSetting.Updates).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_game, UiText.res(R.string.launcher_place_controls)), settingScope(LauncherSetting.LayoutReset).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_game, UiText.res(R.string.launcher_place_controls)), settingScope(LauncherSetting.ClickWheel).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_game, UiText.res(R.string.launcher_place_game)), settingScope(LauncherSetting.VoiceLanguage).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_game, UiText.res(R.string.launcher_place_game)), settingScope(LauncherSetting.Cooldowns).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_game, UiText.res(R.string.launcher_place_controls)), settingScope(LauncherSetting.StickWalk).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_game, UiText.res(R.string.launcher_place_controls)), settingScope(LauncherSetting.StickCamera).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_game, UiText.res(R.string.launcher_place_controls)), settingScope(LauncherSetting.LoginTouch).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_game, UiText.res(R.string.launcher_place_controls)), settingScope(LauncherSetting.WindowHide).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_game, UiText.res(R.string.launcher_place_controls)), settingScope(LauncherSetting.TouchLook).badgeText)
        assertEquals(UiText.res(R.string.launcher_badge_game, UiText.res(R.string.launcher_place_help)), settingScope(LauncherSetting.ErrorReport).badgeText)
    }

    @Test
    fun blocksThatStayInTheLauncherAreOnlyHereAndSayWhatMovedToTheGame() {
        // Sprache und Symbole bleiben als Block; die Teile, die es im Spiel gibt, sind herausgelöst (VoiceLanguage, Cooldowns).
        for (setting in listOf(LauncherSetting.Voice, LauncherSetting.Icons)) {
            val scope = settingScope(setting)
            assertEquals(setting.name, ScopeKind.OnlyHere, scope.kind)
            assertEquals(setting.name, UiText.res(R.string.launcher_badge_here), scope.badgeText)
            assertNotNull("Notiz fehlt: ${setting.name}", scope.note)
        }
        // Die falsche Einordnung "Teils im Spiel: Zahnrad" gibt es nicht mehr.
        for (setting in LauncherSetting.entries) assertFalse(setting.name, settingScope(setting).kind == ScopeKind.Partly)
    }

    @Test
    fun settingsThatNeedARestartAreMarkedAsSuch() {
        // Was die Seite bisher mit "wirkt beim nächsten Start" kennzeichnete, trägt jetzt das Abzeichen "ab nächstem Start".
        for (setting in LauncherSetting.entries) {
            val scope = settingScope(setting)
            if (scope.kind == ScopeKind.OnlyHere && setting.nextStart) assertTrue(setting.name, scope.nextStart)
        }
        // Sofort wirkende Einstellungen tragen nie "ab nächstem Start".
        assertFalse(settingScope(LauncherSetting.StickWalk).nextStart)
        assertFalse(settingScope(LauncherSetting.TouchLook).nextStart)
        assertFalse(settingScope(LauncherSetting.AutoStart).nextStart)
    }

    @Test
    fun theControlPageBadgesWhatItShows() {
        // Gruppe Steuerung: der Steuerungsmodus trägt ein Abzeichen, die Hinweiskarte ist reine Anzeige.
        val control = settingsGroups().first { it.key == "controls" }.settings
        assertEquals(listOf(LauncherSetting.InputMode, LauncherSetting.ControlHint), control)
        assertNotNull(settingScope(LauncherSetting.InputMode).badgeText)
        assertNull(settingScope(LauncherSetting.ControlHint).badgeText)
        // Im zugeklappten Spiel-Bereich trägt jede Zeile ein Abzeichen.
        for (setting in hiddenGameSettings()) assertNotNull(setting.name, settingScope(setting).badgeText)
    }

    @Test
    fun theHintExplainsTheBadges() {
        assertEquals(UiText.res(R.string.launcher_scope_hint), SETTINGS_SCOPE_HINT)
    }

    @Test
    fun gameOnlyListCoversTheFourPlaces() {
        val entries = gameOnlyEntries()
        assertEquals(
            listOf(
                UiText.res(R.string.launcher_game_only_side_title),
                UiText.res(R.string.launcher_game_only_editor_title),
                UiText.res(R.string.launcher_game_only_tf_title),
                UiText.res(R.string.launcher_game_only_wow_title),
            ),
            entries.map { it.title },
        )
        assertEquals(entries.size, entries.map { it.text }.toSet().size)
        assertEquals(UiText.res(R.string.launcher_game_only_title), GAME_ONLY_TITLE)
        // Der Hinweis auf die neu geschriebene Config.wtf gehört dazu.
        assertEquals(UiText.res(R.string.launcher_game_only_wow_text), entries.last().text)
    }
}

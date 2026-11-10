package app.gamenative.ui.screen.wow

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.LauncherTexts
import app.gamenative.ui.screen.wow.launcher.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLogicTest {

    private fun names(vararg keys: ReadyKey): UiText = UiText.join(keys.map { it.shortName })

    private fun ready(
        checked: Boolean = true,
        program: Boolean = true,
        data: Boolean = true,
        forever: Boolean = true,
        legacy: Boolean = false,
        wrong: Boolean = false,
        storage: Boolean = true,
        login: Boolean = true,
        local: String? = "12.0.1.65432",
        remote: String? = "12.0.1.65432",
        outdated: Boolean = false,
    ) = ReadinessInput(checked, program, data, forever, legacy, wrong, storage, login, local, remote, outdated)

    // --- Bereitschaftszeile ---

    @Test
    fun everythingReadyIsOneCalmLineAndTheListStaysClosed() {
        val s = readinessSummary(ready())
        assertTrue(s.allReady)
        assertEquals(UiText.res(R.string.launcher_ready_all_ready_client, "12.0.1.65432"), s.headline)
        assertFalse(s.expandedByDefault)
        assertTrue(s.open.isEmpty())
    }

    @Test
    fun withoutClientVersionTheHeadlineHasNoClientPart() {
        val s = readinessSummary(ready(local = null, remote = null))
        assertEquals(UiText.res(R.string.launcher_ready_all_ready), s.headline)
        assertTrue(s.items.none { it.key == ReadyKey.Client })
    }

    @Test
    fun beforeTheFirstCheckNothingLooksMissing() {
        val s = readinessSummary(ready(checked = false, program = false, data = false, forever = false, storage = false))
        assertEquals(UiText.res(R.string.launcher_pill_checking_install), s.headline)
        assertFalse(s.allReady)
        assertFalse(s.expandedByDefault)
    }

    @Test
    fun aMissingLoginOpensTheListWithAButtonToFixIt() {
        val s = readinessSummary(ready(login = false))
        assertFalse(s.allReady)
        assertTrue(s.expandedByDefault)
        assertEquals(UiText.res(R.string.launcher_ready_open, names(ReadyKey.Login)), s.headline)
        val item = s.open.single()
        assertEquals(ReadyKey.Login, item.key)
        assertEquals(ReadyFix.Login, item.fix)
        assertEquals(UiText.res(R.string.launcher_ready_fix_login), item.fix.buttonText)
    }

    @Test
    fun missingFilesOfferOneInstallButtonOnly() {
        val s = readinessSummary(ready(program = false, data = false, forever = false))
        assertEquals(3, s.open.size)
        assertEquals(1, s.items.count { it.fix == ReadyFix.Install })
        assertEquals(ReadyKey.Program, s.items.first { it.fix == ReadyFix.Install }.key)
        assertEquals(UiText.res(R.string.launcher_ready_open, names(ReadyKey.Program, ReadyKey.Data, ReadyKey.InstallInfo)), s.headline)
    }

    @Test
    fun withoutStorageAccessThePermissionComesFirstAndInstallHasNoButton() {
        val s = readinessSummary(ready(storage = false, program = false, data = false, forever = false))
        assertEquals(ReadyFix.StoragePermission, s.items.first().fix)
        assertEquals(ReadyKey.Storage, s.items.first().key)
        assertTrue(s.items.none { it.fix == ReadyFix.Install })
    }

    @Test
    fun anOutdatedClientIsOpenWithAnUpdateButton() {
        val s = readinessSummary(ready(remote = "12.0.2.70000", outdated = true))
        assertEquals(UiText.res(R.string.launcher_ready_open, names(ReadyKey.Client)), s.headline)
        val item = s.open.single()
        assertEquals(ReadyFix.Update, item.fix)
        assertEquals(UiText.res(R.string.launcher_ready_client_outdated, "12.0.1.65432", "12.0.2.70000"), item.label)
    }

    @Test
    fun theLegacyBetaAsksForTheUpgradeAndAWrongGameHasNoFix() {
        val beta = readinessSummary(ready(forever = false, legacy = true))
        assertEquals(UiText.res(R.string.launcher_ready_beta), beta.items.first { it.key == ReadyKey.InstallInfo }.label)
        assertEquals(ReadyFix.Install, beta.items.first { it.key == ReadyKey.InstallInfo }.fix)

        val wrong = readinessSummary(ready(forever = false, wrong = true))
        val info = wrong.items.first { it.key == ReadyKey.InstallInfo }
        assertFalse(info.ready)
        assertEquals(ReadyFix.None, info.fix)
    }

    @Test
    fun manyOpenPointsAreCutInTheHeadline() {
        val s = readinessSummary(ready(storage = false, program = false, data = false, forever = false, login = false))
        assertEquals(UiText.res(R.string.launcher_ready_open_more, names(ReadyKey.Storage, ReadyKey.Program, ReadyKey.Data), 2), s.headline)
    }

    // --- Einstellungsgruppen ---

    @Test
    fun everySettingAppearsInExactlyOnePlace() {
        // Jede Einstellung steht entweder in einer Gruppe der normalen Ansicht oder im zugeklappten Spiel-Bereich, nie in beiden.
        val inGroups = settingsGroups().flatMap { it.settings }
        val hidden = hiddenGameSettings()
        assertEquals("keine Einstellung doppelt in den Gruppen", inGroups.size, inGroups.toSet().size)
        assertEquals("keine Einstellung doppelt im Spiel-Bereich", hidden.size, hidden.toSet().size)
        assertTrue("Gruppen und Spiel-Bereich überschneiden sich", inGroups.toSet().intersect(hidden.toSet()).isEmpty())
        assertEquals(LauncherSetting.entries.toSet(), inGroups.toSet() + hidden.toSet())
    }

    @Test
    fun groupsHaveStableKeysAndTheirOwnTitles() {
        val groups = settingsGroups()
        assertEquals(listOf("game", "start", "controls", "voice", "icons", "app", "help"), groups.map { it.key })
        assertEquals(groups.size, groups.map { it.titleRes }.toSet().size)
        assertEquals(LauncherTexts.TOUCH_SETTINGS_GROUP, groups[2].key)
    }

    @Test
    fun theControlGroupHoldsOnlyTheInputModeAndTheHint() {
        val control = settingsGroups().first { it.key == "controls" }.settings
        assertEquals(listOf(LauncherSetting.InputMode, LauncherSetting.ControlHint), control)
    }

    @Test
    fun startAndAppGroupsHoldTheirAgreedSettings() {
        val groups = settingsGroups().associate { it.key to it.settings }
        assertEquals(
            listOf(
                LauncherSetting.AutoStart, LauncherSetting.StartCheck, LauncherSetting.Updates,
                LauncherSetting.AppVersion, LauncherSetting.ClientBuild,
            ),
            groups["start"],
        )
        assertEquals(listOf(LauncherSetting.Screenshots, LauncherSetting.WowArt), groups["app"])
        assertEquals(listOf(LauncherSetting.ErrorReport), groups["help"])
    }

    @Test
    fun theNormalViewShowsNothingThatAlsoExistsInTheGame() {
        val shown = settingsGroups().flatMap { it.settings }.toSet()
        val removed = listOf(
            LauncherSetting.StickWalk, LauncherSetting.StickCamera, LauncherSetting.LoginTouch, LauncherSetting.WindowHide,
            LauncherSetting.TouchLook, LauncherSetting.ClickWheel, LauncherSetting.LayoutReset, LauncherSetting.VoiceLanguage,
            LauncherSetting.Cooldowns,
        )
        for (setting in removed) assertFalse(setting.name, setting in shown)
        // Was im Launcher bleibt, ist da.
        val stays = listOf(
            LauncherSetting.Region, LauncherSetting.Resolution, LauncherSetting.Fps, LauncherSetting.Graphics, LauncherSetting.GameFolder,
            LauncherSetting.InputMode, LauncherSetting.Voice, LauncherSetting.Icons, LauncherSetting.AutoStart,
            LauncherSetting.StartCheck, LauncherSetting.Updates, LauncherSetting.ErrorReport,
        )
        for (setting in stays) assertTrue(setting.name, setting in shown)
    }

    @Test
    fun theHiddenListIsCompleteAndEveryEntryIsMarkedAsAlsoInTheGame() {
        assertEquals(
            listOf(
                LauncherSetting.StickWalk, LauncherSetting.StickCamera, LauncherSetting.LoginTouch, LauncherSetting.WindowHide,
                LauncherSetting.TouchLook, LauncherSetting.ClickWheel, LauncherSetting.LayoutReset, LauncherSetting.VoiceLanguage,
                LauncherSetting.Cooldowns,
            ),
            hiddenGameSettings(),
        )
        for (setting in hiddenGameSettings()) {
            val scope = settingScope(setting)
            assertEquals(setting.name, ScopeKind.AlsoInGame, scope.kind)
            assertEquals(setting.name, UiText.res(R.string.launcher_badge_game, scope.place!!), scope.badgeText)
        }
    }

    @Test
    fun theGameFoldIsClosedByDefaultAndOpensOnlyAfterConfirming() {
        val start = GameSettingsFold()
        assertFalse(start.open)
        assertFalse(start.asking)
        // Tippen auf die Zeile fragt nur nach, der Bereich bleibt zu.
        val asked = start.onRowTap()
        assertTrue(asked.asking)
        assertFalse(asked.open)
        // Abbrechen: wieder wie vorher.
        assertEquals(start, asked.onCancel())
        // Öffnen: Frage weg, Bereich offen.
        val opened = asked.onConfirm()
        assertTrue(opened.open)
        assertFalse(opened.asking)
        // Erneutes Tippen klappt ohne Rückfrage zu.
        assertEquals(start, opened.onRowTap())
    }

    @Test
    fun theControlHintNamesThePathAndTheGroupsOfTheGame() {
        assertEquals(UiText.res(R.string.launcher_control_hint_where), LauncherTexts.CONTROL_HINT_WHERE)
        assertEquals(
            listOf(
                UiText.res(R.string.launcher_control_group_sticks),
                UiText.res(R.string.launcher_control_group_tap_hold),
                UiText.res(R.string.launcher_control_group_click_wheel),
                UiText.res(R.string.launcher_control_group_swipe),
                UiText.res(R.string.launcher_control_group_touch_elements),
            ),
            LauncherTexts.CONTROL_HINT_GROUPS,
        )
        assertEquals(UiText.res(R.string.launcher_game_fold_ask_text), LauncherTexts.GAME_FOLD_ASK_TEXT)
        assertEquals(UiText.res(R.string.launcher_open), LauncherTexts.GAME_FOLD_OPEN)
        assertEquals(UiText.res(R.string.launcher_cancel), LauncherTexts.GAME_FOLD_CANCEL)
    }

    @Test
    fun theGameGroupHoldsTheFormerGameOptions() {
        val game = settingsGroups().first { it.key == "game" }.settings
        assertEquals(
            listOf(
                LauncherSetting.Region, LauncherSetting.Resolution, LauncherSetting.Fps, LauncherSetting.Graphics,
                LauncherSetting.GameFolder,
            ),
            game,
        )
    }

    @Test
    fun settingsThatNeedARestartAreFlagged() {
        for (setting in listOf(LauncherSetting.Region, LauncherSetting.Resolution, LauncherSetting.Graphics, LauncherSetting.InputMode)) {
            assertTrue(setting.name, setting.nextStart)
        }
        // Stick-Empfindlichkeit, Abklingzeiten und Startoptionen wirken sofort.
        assertFalse(LauncherSetting.StickWalk.nextStart)
        assertFalse(LauncherSetting.StickCamera.nextStart)
        assertFalse(LauncherSetting.TouchLook.nextStart)
        assertFalse(LauncherSetting.AutoStart.nextStart)
    }

    // --- Spalten auf dem aufgeklappten Fold ---

    // --- Rest ---

    @Test
    fun stickGainCyclesThroughTheStepsAndStartsOverOnUnknownValues() {
        assertEquals(1.0f, nextGainStep(0.75f))
        assertEquals(0.75f, nextGainStep(STICK_GAIN_STEPS.last()))
        assertEquals(STICK_GAIN_STEPS.first(), nextGainStep(0.123f))
    }

    @Test
    fun clientBuildLineTellsVersionAndFreshness() {
        assertEquals(UiText.res(R.string.launcher_client_not_checked), clientBuildText(null, null, false))
        assertEquals(UiText.res(R.string.launcher_client_up_to_date, "12.0.1"), clientBuildText("12.0.1", "12.0.1", false))
        assertEquals(UiText.res(R.string.launcher_client_update_available, "12.0.1", "12.0.2"), clientBuildText("12.0.1", "12.0.2", true))
    }
}

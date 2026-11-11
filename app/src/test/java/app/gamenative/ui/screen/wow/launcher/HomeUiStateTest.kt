package app.gamenative.ui.screen.wow.launcher

import app.gamenative.R
import app.gamenative.ui.screen.wow.settingsGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {

    private val ready = HomeInput(clientLocal = "12.0.1.65432", clientRemote = "12.0.1.65432")

    // --- Zustände: Hero und Pille ---

    @Test
    fun readyGameShowsPlayWithCountdownAndCalmPill() {
        val ui = homeUiState(ready)
        assertEquals(HeroKind.Play, ui.hero.kind)
        assertEquals(UiText.res(R.string.launcher_play), ui.hero.title)
        assertEquals(UiText.raw("WoW Forever · 12.0.1.65432 · Europe · 1080p"), ui.hero.line)
        assertEquals(HeroButton(UiText.res(R.string.launcher_play), HeroAction.Play, enabled = true), ui.hero.primary)
        assertNull(ui.hero.secondary)
        assertTrue(ui.hero.showCountdown)
        assertFalse(ui.hero.showSpinner)
        assertEquals(PillTone.Ok, ui.pill.tone)
        assertEquals(UiText.res(R.string.launcher_join_dot, UiText.res(R.string.launcher_pill_ready), "12.0.1.65432"), ui.pill.text)
        assertEquals(UiText.res(R.string.launcher_pill_ready), ui.pill.shortText)
        assertNull(ui.errorBanner)
    }

    @Test
    fun playButtonFollowsCanPlay() {
        val ui = homeUiState(ready.copy(canPlay = false))
        assertEquals(false, ui.hero.primary?.enabled)
    }

    @Test
    fun missingStorageAccessAsksForAccessFirst() {
        val ui = homeUiState(ready.copy(hasStorageAccess = false, needsInstall = true))
        assertEquals(HeroKind.NeedAccess, ui.hero.kind)
        assertEquals(HeroAction.GrantStorage, ui.hero.primary?.action)
        assertEquals(UiText.res(R.string.launcher_grant_button), ui.hero.primary?.text)
        assertNull(ui.hero.secondary)
        assertFalse(ui.hero.showCountdown)
        assertEquals(PillTone.Warn, ui.pill.tone)
        assertEquals(UiText.res(R.string.launcher_pill_access), ui.pill.text)
    }

    @Test
    fun notInstalledOffersInstallAndFindingExistingFiles() {
        val ui = homeUiState(ready.copy(needsInstall = true, freeSpaceBytes = 112_000_000_000L))
        assertEquals(HeroKind.Install, ui.hero.kind)
        assertEquals(HeroButton(UiText.res(R.string.launcher_install_button), HeroAction.Install), ui.hero.primary)
        assertEquals(HeroButton(UiText.res(R.string.launcher_find_files_button), HeroAction.PickFolder), ui.hero.secondary)
        assertEquals(UiText.res(R.string.launcher_hero_install_line, UiText.res(R.string.launcher_free_suffix, UiText.res(R.string.launcher_gb, 112.0))), ui.hero.line)
        assertEquals(PillTone.Muted, ui.pill.tone)
        assertEquals(UiText.res(R.string.launcher_pill_not_installed), ui.pill.text)
    }

    @Test
    fun legacyBetaOffersTheUpgrade() {
        val ui = homeUiState(ready.copy(needsInstall = true, isLegacyBeta = true))
        assertEquals(HeroKind.Upgrade, ui.hero.kind)
        assertEquals(UiText.res(R.string.launcher_upgrade_button), ui.hero.primary?.text)
        assertEquals(HeroAction.Install, ui.hero.primary?.action)
        assertEquals(HeroAction.PickFolder, ui.hero.secondary?.action)
        assertEquals(UiText.res(R.string.launcher_pill_upgrade), ui.pill.text)
    }

    @Test
    fun outdatedGameOffersUpdateAndStartAnywayInRed() {
        val ui = homeUiState(ready.copy(clientRemote = "12.0.1.70000", clientOutdated = true))
        assertEquals(HeroKind.Update, ui.hero.kind)
        assertEquals(HeroButton(UiText.res(R.string.launcher_hero_update_to, "12.0.1.70000"), HeroAction.Update, enabled = true), ui.hero.primary)
        assertEquals(HeroButton(UiText.res(R.string.launcher_play_outdated_button), HeroAction.Play, enabled = true, danger = true), ui.hero.secondary)
        assertFalse(ui.hero.showCountdown)
        assertEquals(PillTone.Warn, ui.pill.tone)
        assertEquals(UiText.res(R.string.launcher_pill_update_version, "12.0.1.70000"), ui.pill.text)
    }

    @Test
    fun outdatedButtonsAreLockedWhenPlayIsNotPossible() {
        val ui = homeUiState(ready.copy(clientOutdated = true, canPlay = false))
        assertEquals(false, ui.hero.primary?.enabled)
        assertEquals(false, ui.hero.secondary?.enabled)
    }

    @Test
    fun runningInstallShowsProgressAndCancelOnlyForAutoInstall() {
        val auto = homeUiState(ready.copy(isUpdating = true, isAutoInstall = true, busyText = "Datei 3 von 9"))
        assertEquals(HeroKind.Installing, auto.hero.kind)
        assertNull(auto.hero.primary)
        assertEquals(HeroButton(UiText.res(R.string.launcher_cancel_install_button), HeroAction.CancelInstall), auto.hero.secondary)
        assertTrue(auto.hero.showSpinner)
        assertEquals(UiText.raw("Datei 3 von 9"), auto.hero.line)
        assertEquals(PillTone.Info, auto.pill.tone)

        val manual = homeUiState(ready.copy(isUpdating = true, isAutoInstall = false, busyText = "Lade ..."))
        assertNull(manual.hero.secondary)
    }

    @Test
    fun startingAndCheckingShowASpinnerWithoutButtons() {
        val start = homeUiState(ready.copy(isLaunching = true, busyText = "Starte Wine ..."))
        assertEquals(HeroKind.Starting, start.hero.kind)
        assertNull(start.hero.primary)
        assertNull(start.hero.secondary)
        assertTrue(start.hero.showSpinner)
        assertEquals(UiText.res(R.string.launcher_pill_starting), start.pill.text)

        val check = homeUiState(ready.copy(busyText = "Spielversion wird geprüft ..."))
        assertEquals(HeroKind.Checking, check.hero.kind)
        assertEquals(UiText.raw("Spielversion wird geprüft ..."), check.hero.line)
        assertEquals(UiText.res(R.string.launcher_pill_checking_version), check.pill.text)
    }

    @Test
    fun wrongGameKeepsTheOldDisabledStyleText() {
        val ui = homeUiState(ready.copy(isWrongGame = true))
        assertEquals(HeroKind.WrongGame, ui.hero.kind)
        assertEquals(UiText.res(R.string.launcher_wrong_game_button), ui.hero.primary?.text)
        assertEquals(PillTone.Error, ui.pill.tone)
        assertEquals(UiText.res(R.string.launcher_pill_wrong_game), ui.pill.text)
    }

    @Test
    fun errorTurnsThePillRedAndFillsTheBannerButKeepsTheHero() {
        val ui = homeUiState(ready.copy(errorMessage = "Wine startet nicht"))
        assertEquals(PillTone.Error, ui.pill.tone)
        assertEquals(UiText.res(R.string.launcher_pill_problem), ui.pill.text)
        assertEquals("Wine startet nicht", ui.errorBanner)
        assertEquals(HeroKind.Play, ui.hero.kind)
        val help = ui.tiles.first { it.tile == LauncherTile.Help }
        assertEquals(UiText.res(R.string.launcher_tile_problem_found), help.subtitle)
        assertEquals(UiText.raw("1"), help.badge)
        assertEquals(TileTone.Bad, help.tone)
    }

    @Test
    fun uncheckedFilesShowCheckingPillAndAHeroThatIsStillPlay() {
        val ui = homeUiState(ready.copy(filesChecked = false))
        assertEquals(UiText.res(R.string.launcher_pill_checking_install), ui.pill.text)
        assertEquals(PillTone.Muted, ui.pill.tone)
    }

    @Test
    fun launcherUpdateOnlyChangesThePillWhenTheGameIsReady() {
        val ui = homeUiState(ready.copy(launcherUpdateOffered = true))
        assertEquals(UiText.res(R.string.launcher_pill_ready_app_update), ui.pill.text)
        assertEquals(PillTone.Ok, ui.pill.tone)
        assertEquals(HeroKind.Play, ui.hero.kind)
        val needsInstall = homeUiState(ready.copy(launcherUpdateOffered = true, needsInstall = true))
        assertEquals(UiText.res(R.string.launcher_pill_not_installed), needsInstall.pill.text)
    }

    @Test
    fun everyHeroActionOfTheOldLauncherIsStillReachable() {
        val states = listOf(
            ready,
            ready.copy(hasStorageAccess = false),
            ready.copy(needsInstall = true),
            ready.copy(needsInstall = true, isLegacyBeta = true),
            ready.copy(clientOutdated = true),
            ready.copy(isUpdating = true, isAutoInstall = true, busyText = "x"),
        )
        val actions = states.flatMap { homeUiState(it).hero.let { h -> listOfNotNull(h.primary?.action, h.secondary?.action) } }.toSet()
        assertEquals(HeroAction.entries.toSet(), actions)
    }

    // --- Layout und Navigation ---

    @Test
    fun layoutSwitchesAtTheTwoColumnWidth() {
        assertEquals(HomeLayout.Narrow, homeLayoutFor(360f))
        assertEquals(HomeLayout.Narrow, homeLayoutFor(639.9f))
        assertEquals(HomeLayout.Wide, homeLayoutFor(640f))
        assertEquals(HomeLayout.Wide, homeLayoutFor(1088f))
        assertTrue(HomeLayout.Narrow.heroOnTop)
        assertFalse(HomeLayout.Wide.heroOnTop)
    }

    @Test
    fun sixTilesInThreeRowsOfTwo() {
        val rows = tileRows()
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.size == 2 })
        assertEquals(LauncherTile.entries.toList(), rows.flatten())
        assertEquals(6, homeUiState(ready).tiles.size)
        assertEquals(LauncherTile.entries.toList(), homeUiState(ready).tiles.map { it.tile })
    }

    @Test
    fun everyTileOpensItsOwnPageAndAboutIsReachedFromTheFooter() {
        val pages = LauncherTile.entries.map { it.page }
        assertEquals(pages.size, pages.toSet().size)
        assertEquals(LauncherPage.entries.toSet() - LauncherPage.About, pages.toSet())
        assertEquals(LauncherPage.Touch, LauncherTile.Touch.page)
        assertEquals(LauncherPage.Addons, LauncherTile.Addons.page)
        assertEquals(LauncherPage.Settings, LauncherTile.Settings.page)
        assertEquals(LauncherPage.Files, LauncherTile.Files.page)
        assertEquals(LauncherPage.News, LauncherTile.News.page)
        assertEquals(LauncherPage.Help, LauncherTile.Help.page)
    }

    @Test
    fun touchTileTargetsAnExistingSettingsGroup() {
        val group = settingsGroups().firstOrNull { it.key == LauncherTexts.TOUCH_SETTINGS_GROUP }
        assertNotNull("Gruppe ${LauncherTexts.TOUCH_SETTINGS_GROUP} fehlt auf der Einstellungsseite", group)
        assertTrue(group!!.settings.isNotEmpty())
    }

    @Test
    fun titlesAreFilledAndUnique() {
        val tileTitles = LauncherTile.entries.map { LauncherTexts.tileTitle(it) }
                assertEquals(tileTitles.size, tileTitles.toSet().size)
        val pageTitles = LauncherPage.entries.map { LauncherTexts.pageTitle(it) }
                assertEquals(pageTitles.size, pageTitles.toSet().size)
    }

    // --- Kacheln: Zeile 2, Badge, Sperre ---

    private fun tile(input: HomeInput, t: LauncherTile) = homeUiState(input).tiles.first { it.tile == t }

    @Test
    fun readyGameUnlocksEverythingAndDescribesEachTile() {
        val i = ready.copy(freeSpaceBytes = 50_000_000_000L, readinessOpen = 0)
        val tiles = homeUiState(i).tiles
        assertTrue(tiles.none { it.locked })
        assertEquals(UiText.res(R.string.launcher_tile_controls_sub, "Touch (WoW)"), tile(i, LauncherTile.Touch).subtitle)
        assertEquals(UiText.res(R.string.launcher_tile_controls), tile(i, LauncherTile.Touch).title)
        assertEquals(UiText.res(R.string.launcher_tile_settings_sub, "1080p", "60", "Europe"), tile(i, LauncherTile.Settings).subtitle)
        assertEquals(UiText.res(R.string.launcher_tile_installed, UiText.res(R.string.launcher_free_suffix, UiText.res(R.string.launcher_gb, 50.0))), tile(i, LauncherTile.Files).subtitle)
        assertEquals(UiText.res(R.string.launcher_tile_all_ready), tile(i, LauncherTile.Help).subtitle)
        assertNull(tile(i, LauncherTile.Help).badge)
        assertEquals(UiText.res(R.string.launcher_tile_new_in, "2.3.3"), tile(i, LauncherTile.News).subtitle)
        assertNull(tile(i, LauncherTile.News).badge)
    }

    @Test
    fun withoutInstalledGameTouchAndAddonsAreLockedButVisible() {
        val i = ready.copy(needsInstall = true)
        assertTrue(tile(i, LauncherTile.Touch).locked)
        assertTrue(tile(i, LauncherTile.Addons).locked)
        assertEquals(UiText.res(R.string.launcher_tile_install_first), tile(i, LauncherTile.Touch).subtitle)
        assertFalse(tile(i, LauncherTile.Settings).locked)
        assertFalse(tile(i, LauncherTile.Files).locked)
        assertEquals(UiText.res(R.string.launcher_pill_not_installed), tile(i, LauncherTile.Files).subtitle)
        assertEquals(TileTone.Warn, tile(i, LauncherTile.Files).tone)
    }

    @Test
    fun missingStorageAccessMarksTheFilesTileYellow() {
        val i = ready.copy(hasStorageAccess = false)
        assertEquals(TileTone.Warn, tile(i, LauncherTile.Files).tone)
        assertEquals(UiText.res(R.string.launcher_tile_access_missing), tile(i, LauncherTile.Files).subtitle)
        assertTrue(tile(i, LauncherTile.Touch).locked)
    }

    @Test
    fun whileSomethingRunsOnlyNewsAndHelpStayOpen() {
        val i = ready.copy(isUpdating = true, busyText = "Lade ...")
        for (t in homeUiState(i).tiles) {
            val expectedLocked = t.tile != LauncherTile.News && t.tile != LauncherTile.Help
            assertEquals("Sperre ${t.tile}", expectedLocked, t.locked)
        }
        val start = ready.copy(isLaunching = true, busyText = "Start")
        assertTrue(tile(start, LauncherTile.Settings).locked)
        assertFalse(tile(start, LauncherTile.Help).locked)
    }

    @Test
    fun outdatedGameBadgesTheFilesTile() {
        val i = ready.copy(clientOutdated = true, clientRemote = "12.0.1.70000")
        val files = tile(i, LauncherTile.Files)
        assertEquals(UiText.res(R.string.launcher_pill_update_version, "12.0.1.70000"), files.subtitle)
        assertEquals(UiText.res(R.string.launcher_tile_update_badge), files.badge)
        assertEquals(TileTone.Warn, files.tone)
    }

    @Test
    fun helpTileCountsOpenReadinessPoints() {
        val one = tile(ready.copy(readinessOpen = 1), LauncherTile.Help)
        assertEquals(UiText.plural(R.plurals.launcher_points_open, 1), one.subtitle)
        assertEquals(UiText.raw("1"), one.badge)
        assertEquals(TileTone.Warn, one.tone)
        val two = tile(ready.copy(readinessOpen = 2), LauncherTile.Help)
        assertEquals(UiText.plural(R.plurals.launcher_points_open, 2), two.subtitle)
        assertEquals(UiText.res(R.string.launcher_hero_checking), tile(ready.copy(filesChecked = false), LauncherTile.Help).subtitle)
    }

    @Test
    fun newsTileBadgesWhatsNewAndLauncherUpdate() {
        assertEquals(UiText.res(R.string.launcher_tile_new_badge), tile(ready.copy(whatsNewPending = true), LauncherTile.News).badge)
        val update = tile(ready.copy(launcherUpdateOffered = true), LauncherTile.News)
        assertEquals(UiText.res(R.string.launcher_tile_app_update), update.subtitle)
        assertEquals(UiText.res(R.string.launcher_tile_new_badge), update.badge)
    }

    // --- Seite Spieldateien ---

    @Test
    fun filesPageOffersExactlyTheFittingAction() {
        val ok = filesPageUi(ready)
        assertEquals(UiText.res(R.string.launcher_files_installed_client, "12.0.1.65432"), ok.status)
        assertFalse(ok.showGrantStorage || ok.showInstall || ok.showUpdate)
        assertTrue(ok.controlsEnabled)

        val access = filesPageUi(ready.copy(hasStorageAccess = false, needsInstall = true))
        assertTrue(access.showGrantStorage)
        assertFalse(access.showInstall)

        val install = filesPageUi(ready.copy(needsInstall = true))
        assertTrue(install.showInstall)
        assertFalse(install.installIsUpgrade)
        assertEquals(UiText.res(R.string.launcher_pill_not_installed), install.status)

        val upgrade = filesPageUi(ready.copy(needsInstall = true, isLegacyBeta = true))
        assertTrue(upgrade.showInstall)
        assertTrue(upgrade.installIsUpgrade)

        val update = filesPageUi(ready.copy(clientOutdated = true, clientRemote = "12.0.1.70000"))
        assertTrue(update.showUpdate)
        assertEquals(UiText.res(R.string.launcher_hero_update_to, "12.0.1.70000"), update.updateText)
        assertFalse(update.showInstall)
    }

    @Test
    fun filesPageControlsAreLockedWhileBusy() {
        assertFalse(filesPageUi(ready.copy(isUpdating = true, busyText = "Lade")).controlsEnabled)
    }

    // --- Hilfen ---

    @Test
    fun gbTextUsesOneDecimalAndNeverNegative() {
        // Das Komma oder der Punkt kommt aus der Ressource (App-Sprache); hier zählt der Wert.
        assertEquals(UiText.res(R.string.launcher_gb, 0.0), gbText(0L))
        assertEquals(UiText.res(R.string.launcher_gb, 38.0), gbText(38_000_000_000L))
        assertEquals(UiText.res(R.string.launcher_gb, 112.5), gbText(112_540_000_000L))
        assertEquals(UiText.res(R.string.launcher_gb, 0.0), gbText(-5L))
        assertEquals(UiText.raw(""), freeSpaceSuffix(null))
        assertEquals(UiText.res(R.string.launcher_free_suffix, UiText.res(R.string.launcher_gb, 1.0)), freeSpaceSuffix(1_000_000_000L))
    }
}

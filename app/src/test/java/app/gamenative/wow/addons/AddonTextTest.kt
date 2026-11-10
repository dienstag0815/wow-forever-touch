package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AddonTextTest {

    private val mb = 1024L * 1024
    private val entry = CatalogEntry("demo", "Demo", "k", "w", "l", AddonSource.GitHub("o", "demo", "x"), listOf("Demo"), 30 * mb, 100 * mb)

    private fun status(state: AddonInstallState, installed: String? = null, latest: String? = null, enable: EnableState = EnableState.ENABLED) =
        AddonStatus(entry, state, installed, latest, enable)

    @Test
    fun statusTitles() {
        assertEquals(
            UiText.res(R.string.launcher_addon_status_not_installed),
            AddonText.statusTitle(status(AddonInstallState.NOT_INSTALLED, enable = EnableState.MISSING)),
        )
        assertEquals(UiText.res(R.string.launcher_addon_status_foreign), AddonText.statusTitle(status(AddonInstallState.FOREIGN)))
        assertEquals(
            UiText.res(R.string.launcher_addon_status_installed_current, "v1.0"),
            AddonText.statusTitle(status(AddonInstallState.INSTALLED, "v1.0", "v1.0")),
        )
        assertEquals(
            UiText.res(R.string.launcher_addon_status_installed, "v1.0"),
            AddonText.statusTitle(status(AddonInstallState.INSTALLED, "v1.0", null)),
        )
        assertEquals(
            UiText.res(R.string.launcher_addon_status_update, "v1.0", "v1.1"),
            AddonText.statusTitle(status(AddonInstallState.UPDATE_AVAILABLE, "v1.0", "v1.1")),
        )
        // Der Pfadteil des Tags (bundle/) wird nicht angezeigt.
        assertEquals(
            UiText.res(R.string.launcher_addon_status_installed_current, "v12.6.4+v1.0.12"),
            AddonText.statusTitle(status(AddonInstallState.INSTALLED, "bundle/v12.6.4+v1.0.12", "bundle/v12.6.4+v1.0.12")),
        )
    }

    @Test
    fun switchedOffIsShownOnlyForInstalledAddons() {
        assertEquals(
            UiText.res(R.string.launcher_addon_status_off, UiText.res(R.string.launcher_addon_status_installed_current, "v1")),
            AddonText.statusTitle(status(AddonInstallState.INSTALLED, "v1", "v1", EnableState.DISABLED)),
        )
        assertEquals(
            UiText.res(R.string.launcher_addon_status_partly_off, UiText.res(R.string.launcher_addon_status_update, "v1", "v2")),
            AddonText.statusTitle(status(AddonInstallState.UPDATE_AVAILABLE, "v1", "v2", EnableState.MIXED)),
        )
        assertEquals(
            UiText.res(R.string.launcher_addon_status_foreign),
            AddonText.statusTitle(status(AddonInstallState.FOREIGN, enable = EnableState.DISABLED)),
        )
    }

    @Test
    fun sourceLabels() {
        assertEquals(UiText.res(R.string.launcher_addon_source_github, "o/demo"), AddonText.sourceLabel(entry))
        assertEquals(
            UiText.res(R.string.launcher_addon_source_wowi_file, "7"),
            AddonText.sourceLabel(entry.copy(source = AddonSource.WowInterface("7"))),
        )
    }

    @Test
    fun megabyteTexts() {
        assertEquals(UiText.raw("?"), AddonText.megabytes(0))
        assertEquals(UiText.res(R.string.launcher_addon_mb_under_one), AddonText.megabytes(200 * 1024))
        assertEquals(UiText.res(R.string.launcher_addon_mb_decimal, 1.5), AddonText.megabytes(1536 * 1024L))
        assertEquals(UiText.res(R.string.launcher_addon_mb_whole, 90L), AddonText.megabytes(93_860_673))
    }

    @Test
    fun progressTexts() {
        assertEquals(
            UiText.res(
                R.string.launcher_addon_progress_total, 50L,
                UiText.res(R.string.launcher_addon_mb_decimal, 5.0), UiText.res(R.string.launcher_addon_mb_whole, 10L),
            ),
            AddonText.progress(5 * mb, 10 * mb),
        )
        assertEquals(
            UiText.res(R.string.launcher_addon_progress_done, UiText.res(R.string.launcher_addon_mb_decimal, 3.0)),
            AddonText.progress(3 * mb, -1),
        )
        // Mehr gelesen als erwartet: die Prozentzahl bleibt bei 100.
        val over = AddonText.progress(20 * mb, 10 * mb) as UiText.Res
        assertEquals(R.string.launcher_addon_progress_total, over.id)
        assertEquals(100L, over.args[0])
    }

    @Test
    fun latestLines() {
        assertEquals(UiText.res(R.string.launcher_addon_latest_searching), AddonText.latestLine(null))
        val ok = LatestResult.Ok(PickedRelease("bundle/v1.2", null, ReleaseAsset("a.zip", "https://github.com/a", 20 * mb, "application/zip")))
        assertEquals(
            UiText.res(R.string.launcher_addon_latest_with_size, "v1.2", UiText.res(R.string.launcher_addon_mb_whole, 20L)),
            AddonText.latestLine(ok),
        )
        // Ohne bekannte Größe nur der Tag.
        val noSize = LatestResult.Ok(PickedRelease("v2.0", null, ReleaseAsset("a.zip", "https://github.com/a", 0, "application/zip")))
        assertEquals(UiText.raw("v2.0"), AddonText.latestLine(noSize))
        assertEquals(
            UiText.res(R.string.launcher_addon_latest_fail, UiText.raw("kein Netz")),
            AddonText.latestLine(LatestResult.Fail(UiText.raw("kein Netz"))),
        )
    }

    @Test
    fun theIntroAndTheHintsAreThreeDifferentTexts() {
        assertEquals(UiText.res(R.string.launcher_addon_intro), AddonText.INTRO)
        assertEquals(UiText.res(R.string.launcher_addon_busy_hint), AddonText.BUSY_HINT)
        assertEquals(UiText.res(R.string.launcher_addon_no_game_hint), AddonText.NO_GAME_HINT)
        assertNotEquals(AddonText.BUSY_HINT, AddonText.NO_GAME_HINT)
        assertNotEquals(AddonText.INTRO, AddonText.BUSY_HINT)
    }

    @Test
    fun theMissingGameFolderIsNamedOrDescribed() {
        assertEquals(
            UiText.res(R.string.launcher_addon_game_not_installed_folder, "_forever_"),
            AddonText.gameNotInstalled("_forever_"),
        )
        assertEquals(
            UiText.res(R.string.launcher_addon_game_not_installed_folder, UiText.res(R.string.launcher_addon_game_folder_default)),
            AddonText.gameNotInstalled(null),
        )
    }
}

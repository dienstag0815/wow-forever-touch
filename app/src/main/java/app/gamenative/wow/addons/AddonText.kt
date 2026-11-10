package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.IOException

/**
 * Text of a failure for the screen: our own exceptions carry a [UiText]; a foreign message (system, network library) stays as
 * it is; [fallback] when there is none.
 */
internal fun IOException.addonText(fallback: UiText): UiText = when (this) {
    is AddonInstallException -> text
    is AddonNetworkException -> text
    else -> message?.let { UiText.raw(it) } ?: fallback
}

/** Texts of the addon page as pure functions, so they can be checked in JUnit; the screen resolves the [UiText]. */
object AddonText {
    val BUSY_HINT: UiText = UiText.res(R.string.launcher_addon_busy_hint)
    val NO_GAME_HINT: UiText = UiText.res(R.string.launcher_addon_no_game_hint)
    val INTRO: UiText = UiText.res(R.string.launcher_addon_intro)

    fun statusTitle(s: AddonStatus): UiText {
        val base = when (s.state) {
            AddonInstallState.NOT_INSTALLED -> UiText.res(R.string.launcher_addon_status_not_installed)
            AddonInstallState.FOREIGN -> UiText.res(R.string.launcher_addon_status_foreign)
            AddonInstallState.UPDATE_AVAILABLE ->
                UiText.res(
                    R.string.launcher_addon_status_update,
                    AddonVersion.display(s.installedTag.orEmpty()),
                    AddonVersion.display(s.latestTag.orEmpty()),
                )
            AddonInstallState.INSTALLED -> {
                val tag = AddonVersion.display(s.installedTag.orEmpty())
                if (s.latestTag != null) UiText.res(R.string.launcher_addon_status_installed_current, tag)
                else UiText.res(R.string.launcher_addon_status_installed, tag)
            }
        }
        val installed = s.state == AddonInstallState.INSTALLED || s.state == AddonInstallState.UPDATE_AVAILABLE
        return when {
            installed && s.enable == EnableState.DISABLED -> UiText.res(R.string.launcher_addon_status_off, base)
            installed && s.enable == EnableState.MIXED -> UiText.res(R.string.launcher_addon_status_partly_off, base)
            else -> base
        }
    }

    fun sourceLabel(entry: CatalogEntry): UiText = when (val source = entry.source) {
        is AddonSource.GitHub -> UiText.res(R.string.launcher_addon_source_github, source.slug)
        is AddonSource.WowInterface -> UiText.res(R.string.launcher_addon_source_wowi_file, source.fileId)
    }

    /** "0.9 MB", "12 MB" (decimal separator by the device language, because the number is a format argument). */
    fun megabytes(bytes: Long): UiText {
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            bytes <= 0 -> UiText.raw("?")
            mb < 1.0 -> UiText.res(R.string.launcher_addon_mb_under_one)
            mb < 10.0 -> UiText.res(R.string.launcher_addon_mb_decimal, mb)
            else -> UiText.res(R.string.launcher_addon_mb_whole, Math.round(mb))
        }
    }

    fun progress(done: Long, total: Long): UiText =
        if (total > 0) {
            UiText.res(R.string.launcher_addon_progress_total, (done * 100 / total).coerceIn(0, 100), megabytes(done), megabytes(total))
        } else {
            UiText.res(R.string.launcher_addon_progress_done, megabytes(done))
        }

    /** Line below the install button: latest version and size, or the reason why it is not known. */
    fun latestLine(latest: LatestResult?): UiText = when (latest) {
        null -> UiText.res(R.string.launcher_addon_latest_searching)
        is LatestResult.Ok -> {
            val size = latest.picked.asset.size
            val tag = AddonVersion.display(latest.picked.tag)
            if (size > 0) UiText.res(R.string.launcher_addon_latest_with_size, tag, megabytes(size)) else UiText.raw(tag)
        }
        is LatestResult.Fail -> UiText.res(R.string.launcher_addon_latest_fail, latest.reason)
    }

    /** "The game is not installed yet, the folder X is missing" ([folderName] null: the game's folder is not known by name). */
    fun gameNotInstalled(folderName: String?): UiText =
        UiText.res(R.string.launcher_addon_game_not_installed_folder, folderName ?: UiText.res(R.string.launcher_addon_game_folder_default))
}

package app.gamenative.update

import app.gamenative.ui.screen.wow.launcher.UiText

/** The list "Versionen" in the tester area. */
sealed interface VersionsState {
    data object Idle : VersionsState
    data object Loading : VersionsState
    data class Loaded(val rows: List<VersionRow>) : VersionsState
    data class Failed(val reason: UiText) : VersionsState
}

/** The automatic part of a downgrade (backup, loading the old APK) and what is ready for the player afterwards. */
sealed interface RollbackState {
    data object Idle : RollbackState

    /** [step] runs; [fraction] is the download progress (null while there is none). */
    data class Working(val step: RollbackStep, val version: String, val fraction: Float? = null) : RollbackState

    /** Backup and APK lie in Downloads under these file names; the next step is the uninstall (the player's tap). */
    data class Ready(val version: String, val backupFile: String, val apkFile: String) : RollbackState

    data class Failed(val reason: UiText) : RollbackState
}

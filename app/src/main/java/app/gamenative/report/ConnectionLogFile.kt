package app.gamenative.report

import java.io.File

/** Wo die `Connection.log` des Spiels liegt und wie sie gelesen wird (ohne Android-Klassen, JUnit-getestet). */
object ConnectionLogFile {
    /** Ordner der beiden Clientarten im Spielordner (Vollversion, frühere Beta); gleich [app.gamenative.ui.screen.wow.WowClientDownloader.FLAVOR_DIR] und `LEGACY_FLAVOR_DIR`. */
    val DEFAULT_FLAVOR_DIRS = listOf("_forever_", "_classic_beta_")

    /** Text der jüngsten `Connection.log` der Ordner [flavorDirs] unter [gameRoot] (die letzten [ReportBuilder.PER_FILE_MAX_BYTES]); null, wenn keine da ist. */
    fun latestText(gameRoot: File, flavorDirs: List<String> = DEFAULT_FLAVOR_DIRS): String? = flavorDirs
        .map { File(File(gameRoot, "$it/Logs"), "Connection.log") }
        .filter { it.isFile && it.length() > 0 }
        .maxByOrNull { it.lastModified() }
        ?.let { runCatching { ReportBuilder.readTail(it, ReportBuilder.PER_FILE_MAX_BYTES) }.getOrNull() }
}

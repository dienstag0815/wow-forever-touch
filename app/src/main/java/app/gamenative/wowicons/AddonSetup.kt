package app.gamenative.wowicons

import android.content.Context
import java.io.File
import java.util.Locale
import timber.log.Timber

/**
 * Puts the addon TouchForever into the game folder BEFORE the game is started, on every start, whatever the setting
 * "Echte Symbole aus WoW" says (that setting only decides about reading and loading the icons): the addon also draws the
 * cooldown strip, reports the open windows and owns the reload key, so a game that loads an old addon (or none) is wrong in
 * several places at once. Called from the launch preparation, before the container and the game are started, so WoW reads the
 * current files at its start and nobody has to type /reload.
 *
 * The result is kept ([last]): the app compares the running addon (build number in the strip) with the bundled one, and when the
 * running one is older it must know whether the disk is current (then a reload helps) or not (then it does not).
 *
 * Logs (tag `AddonSetup`): "installed" with the number of written files and the verification result, errors with the cause.
 */
object AddonSetup {
    private const val TAG = "AddonSetup"

    /** What a run found: [written] files copied, [upToDate] = the disk holds exactly the bundled addon, [problem] = why not. */
    class Result(val written: Int, val upToDate: Boolean, val problem: String?)

    @Volatile
    var last: Result? = null
        private set

    /** Whether the addon on the disk is the bundled one: null before the first run in this process. */
    @JvmStatic
    fun diskCurrent(): Boolean? = last?.upToDate

    /** Reads the bundled addon from the assets, installs it into [gameRoot] and checks the copy. Never throws. */
    @JvmStatic
    fun run(context: Context, gameRoot: File): Result {
        val result = try {
            val assets = context.applicationContext.assets
            val files = LinkedHashMap<String, ByteArray>()
            for (name in assets.list(BridgeInstaller.ASSET_DIR).orEmpty()) {
                assets.open("${BridgeInstaller.ASSET_DIR}/$name").use { files[name] = it.readBytes() }
            }
            // The language of the app for the addon (TouchForeverLang.lua is loaded before TouchForeverLocale.lua).
            files[LANG_FILE] = addonLanguageFile(app.gamenative.ui.screen.wow.launcher.AppLanguageSetting.currentTag())
            install(gameRoot, files)
        } catch (e: Throwable) {
            Timber.tag(TAG).w(e, "addon installation failed")
            Result(0, false, "${e.javaClass.simpleName}: ${e.message}")
        }
        last = result
        return result
    }

    /** Name of the small file that tells the addon which language the app uses. */
    internal const val LANG_FILE = "TouchForeverLang.lua"

    /** Content of [LANG_FILE]: "de" for a German app, "en" for every other language (also an empty tag). */
    internal fun addonLanguageFile(languageTag: String): ByteArray {
        val language = if (languageTag.substringBefore('-').substringBefore('_').equals("de", ignoreCase = true)) "de" else "en"
        return "local _, ns = ...\nif ns then ns.appLanguage = \"$language\" end\n".toByteArray(Charsets.UTF_8)
    }

    /** The file part of [run] (no Android classes, tested): copy, allow out-of-date addons, verify. */
    internal fun install(gameRoot: File, files: Map<String, ByteArray>): Result {
        if (files.isEmpty()) {
            Timber.tag(TAG).w("no bundled addon files found")
            return Result(0, false, "no addon in the app")
        }
        return try {
            val written = BridgeInstaller.install(gameRoot, files)
            BridgeInstaller.allowOutOfDateAddons(File(gameRoot, "_forever_/WTF/Config.wtf"))
            val upToDate = BridgeInstaller.verify(gameRoot, files)
            Timber.tag(TAG).i("installed: %d files written, verified=%s", written, upToDate)
            Result(written, upToDate, if (upToDate) null else "files in the game folder differ or are missing")
        } catch (e: Throwable) {
            Timber.tag(TAG).w(e, "addon installation failed")
            Result(0, false, "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

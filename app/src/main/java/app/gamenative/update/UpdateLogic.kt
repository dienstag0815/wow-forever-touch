package app.gamenative.update

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Pure logic of the self-update (no Android classes, so it runs in plain JVM unit tests): feed parsing, version
 * comparison, check interval, URL resolution and the SHA-256/size check of the downloaded APK.
 *
 * Feed format (`latest.json` on the download page, fixed 08.11.2026):
 * `{"version":"2.3.3-fold7.1","datei":"WoW-Forever-Fold-2.3.3-fold7.1.apk","url":"https://github.com/owner/repo/releases/download/v2.3.3-fold7.1/WoW-Forever-Fold-2.3.3-fold7.1.apk",`
 * `"sha256":"<64 hex, lower case>","bytes":155788181,"zeit":"2026-11-08T12:00:00Z","text":"Notizen","versionCode":244}`
 * `versionCode`, `zeit`, `text` are optional; of `url` and `datei` at least one is needed (absolute `url` wins,
 * otherwise `datei` is resolved relative to the feed URL).
 */
data class UpdateInfo(
    /** 0 = the feed has no versionCode; then [versionName] decides. */
    val versionCode: Int,
    val versionName: String,
    /** Relative file name (`datei`), may be empty when [url] is given. */
    val file: String,
    /** Absolute download URL (`url`), may be empty when [file] is given. */
    val url: String,
    val sha256: String,
    val size: Long,
    val notes: String,
    /** Release tag of a custom source (for the release page in the browser); empty for the official feed. */
    val tag: String = "",
)

sealed class FeedResult {
    data class Ok(val info: UpdateInfo) : FeedResult()
    data class Invalid(val reason: UiText) : FeedResult()
}

/** What the launcher does next with an offered update when "Updates automatisch installieren" is on (see [UpdateLogic.autoInstallDecision]). */
enum class AutoInstallAction {
    /** Nothing: switch off, no offer, held back by the player, or Android may not install apps from here. */
    None,

    /** Offered but not yet on the device: load it in the background now. */
    Download,

    /** Loaded, but the launcher is not visible, a game starts/runs/loads, or the start countdown runs: wait for the next launcher visit. */
    Wait,

    /** Loaded and nothing in the way: show the notice with "Abbrechen", then install. */
    Install,
}

object UpdateLogic {
    const val FEED_FILE = "latest.json"
    const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

    /** Seconds the banner announces an automatic installation (with "Abbrechen") before it starts. */
    const val AUTO_INSTALL_NOTICE_SECONDS = 5

    /**
     * Decision of the automatic update. [enabled] is the switch; [updateOffered] a newer version is offered and the player did
     * not hold it back; [downloaded] its APK is already loaded and verified; [launcherVisible] the launcher start page is
     * on screen (app in front, no settings/news page on top); [gameBusy] a game starts or runs or game files are loading
     * (same condition as `canInstall` of the banner, negated); [countdownRunning] the automatic game start counts down;
     * [installAllowed] Android lets this app install packages. Loading starts at once; only the installation waits.
     */
    fun autoInstallDecision(
        enabled: Boolean,
        updateOffered: Boolean,
        downloaded: Boolean,
        launcherVisible: Boolean,
        gameBusy: Boolean,
        countdownRunning: Boolean,
        installAllowed: Boolean = true,
    ): AutoInstallAction = when {
        !enabled || !updateOffered || !installAllowed -> AutoInstallAction.None
        !downloaded -> AutoInstallAction.Download
        launcherVisible && !gameBusy && !countdownRunning -> AutoInstallAction.Install
        else -> AutoInstallAction.Wait
    }

    private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

    /** [requireSha] false (custom source cache only): a missing checksum is allowed; a given one must still be valid. */
    fun parseFeed(json: String?, requireSha: Boolean = true): FeedResult {
        if (json.isNullOrBlank()) return FeedResult.Invalid(UiText.res(R.string.launcher_update_feed_empty))
        val obj = try {
            JSONObject(json)
        } catch (e: Exception) {
            return FeedResult.Invalid(UiText.res(R.string.launcher_update_feed_invalid_json))
        }
        val name = obj.optString("version", "").trim()
        if (name.isEmpty()) return FeedResult.Invalid(UiText.res(R.string.launcher_update_feed_no_version))
        val file = obj.optString("datei", "").trim()
        val url = obj.optString("url", "").trim()
        if (file.isEmpty() && url.isEmpty()) return FeedResult.Invalid(UiText.res(R.string.launcher_update_feed_no_file))
        val sha = obj.optString("sha256", "").trim()
        if (!(sha.isEmpty() && !requireSha) && !SHA256_HEX.matches(sha)) return FeedResult.Invalid(UiText.res(R.string.launcher_update_feed_bad_sha))
        val size = obj.optLong("bytes", 0L)
        if (size <= 0L) return FeedResult.Invalid(UiText.res(R.string.launcher_update_feed_no_bytes))
        val code = obj.optInt("versionCode", 0).coerceAtLeast(0)
        val notes = obj.optString("text", "").trim()
        return FeedResult.Ok(UpdateInfo(code, name, file, url, sha.lowercase(), size, notes))
    }

    /**
     * True when the feed offers something newer than the installed app. A feed `versionCode` decides when present,
     * otherwise the version names are compared (see [compareVersionNames]).
     */
    fun isNewer(info: UpdateInfo, installedVersionCode: Int, installedVersionName: String): Boolean =
        if (info.versionCode > 0) {
            info.versionCode > installedVersionCode
        } else {
            compareVersionNames(info.versionName, installedVersionName) > 0
        }

    fun isNewer(feedVersionCode: Int, installedVersionCode: Int): Boolean = feedVersionCode > installedVersionCode

    /**
     * Compares names like `2.3.3-fold7.1`: the number parts before the first `-` first, then the number parts of
     * the rest (so `fold7.1` = [7, 1] > `fold7` = [7] > `fold6.2` = [6, 2]). Letters are ignored; missing parts count
     * as 0. Returns negative, 0 or positive like compareTo.
     */
    fun compareVersionNames(a: String, b: String): Int {
        val (baseA, restA) = splitName(a)
        val (baseB, restB) = splitName(b)
        val c = compareNumbers(baseA, baseB)
        return if (c != 0) c else compareNumbers(restA, restB)
    }

    private fun splitName(name: String): Pair<List<Int>, List<Int>> {
        val t = name.trim().trimStart('v', 'V')
        val dash = t.indexOf('-')
        val base = if (dash < 0) t else t.substring(0, dash)
        val rest = if (dash < 0) "" else t.substring(dash + 1)
        return numbers(base) to numbers(rest)
    }

    private fun numbers(text: String): List<Int> = Regex("[0-9]+").findAll(text).map { it.value.toIntOrNull() ?: 0 }.toList()

    private fun compareNumbers(x: List<Int>, y: List<Int>): Int {
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** True when a check is due: forced, never checked, clock went backwards, or [intervalMs] has passed. */
    fun shouldCheck(lastCheckMs: Long, nowMs: Long, force: Boolean = false, intervalMs: Long = CHECK_INTERVAL_MS): Boolean =
        force || lastCheckMs <= 0L || nowMs < lastCheckMs || nowMs - lastCheckMs >= intervalMs

    /**
     * Full feed URL for a base: a base whose path ends in `.json` is used as is, otherwise `latest.json` is appended
     * after a single slash. Returns null for blank input or anything but http/https.
     */
    fun feedUrl(base: String): String? {
        val b = base.trim()
        if (b.isEmpty()) return null
        val uri = parseHttp(b) ?: return null
        val path = uri.rawPath.orEmpty()
        if (path.endsWith(".json", ignoreCase = true)) return b
        return b.trimEnd('/') + "/" + FEED_FILE
    }

    /**
     * Download URL of an update: an absolute http(s) `url` of the feed is used as is, otherwise `datei` is resolved
     * relative to the feed URL. Returns null when neither gives a usable http(s) address. The SHA-256 of the feed and
     * Android's signature check protect the install, so the host is not restricted.
     */
    fun resolveApkUrl(feedUrl: String, info: UpdateInfo): String? {
        if (info.url.isNotEmpty()) {
            val abs = parseHttp(info.url)
            if (abs != null) return abs.toString()
            if (info.file.isEmpty()) return null
        }
        return resolveRelative(feedUrl, info.file)
    }

    /** Resolves [ref] like a link on the page [feedUrl]; only http(s) results are returned. */
    fun resolveRelative(feedUrl: String, ref: String): String? {
        val base = parseHttp(feedUrl) ?: return null
        if (ref.isBlank()) return null
        val resolved = try {
            base.resolve(URI(ref.trim()))
        } catch (e: Exception) {
            return null
        }
        return parseHttp(resolved.toString())?.toString()
    }

    private fun parseHttp(url: String): URI? {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrEmpty()) return null
        return uri
    }

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
        return hex(digest.digest())
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * Text of the card while Android shows its install confirmation. When the installer of record is not this app
     * (sideload, file manager, browser) Android asks once by design, so the card says so.
     */
    fun confirmText(installerOfRecord: String?, ownPackage: String): UiText =
        if (installerOfRecord == ownPackage) {
            UiText.res(R.string.launcher_update_confirm_own)
        } else {
            UiText.res(R.string.launcher_update_confirm_foreign)
        }

    /** "2.3.3-fold7.4 -> 2.3.3-fold7.6" for the update card (arrow as in the German UI). */
    fun versionTransition(installedName: String, newName: String): String = "${installedName.trim()} → ${newName.trim()}"

    /**
     * Install progress 0..1 for the card: copying the APK into the install session fills the first half, Android's own
     * session progress ([sessionProgress], 0..1, null while still copying) the second half. Values outside the range are
     * clamped; an unknown total (0) counts as no progress yet.
     */
    fun installFraction(copiedBytes: Long, totalBytes: Long, sessionProgress: Float?): Float {
        if (sessionProgress != null) return (0.5f + sessionProgress.coerceIn(0f, 1f) * 0.5f).coerceIn(0f, 1f)
        if (totalBytes <= 0L) return 0f
        val copied = copiedBytes.coerceIn(0L, totalBytes)
        return (copied.toFloat() / totalBytes.toFloat() * 0.5f).coerceIn(0f, 0.5f)
    }

    /** Download size as "148 MB" (rounded to the nearest MB). */
    fun megabytes(bytes: Long): String = "${(bytes + 524_288) / 1_048_576} MB"

    /**
     * Feed notes for the card: line breaks kept, blank lines and trailing spaces removed, cut at [maxChars] on a word
     * boundary with an ellipsis. Empty when the feed has no `text`.
     */
    fun cardNotes(notes: String, maxChars: Int = 600): String {
        val clean = notes.replace("\r\n", "\n").lines().map { it.trimEnd() }.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
            .joinToString("\n").replace(Regex("\n{3,}"), "\n\n")
        if (clean.length <= maxChars) return clean
        val cut = clean.substring(0, maxChars)
        val space = cut.lastIndexOfAny(charArrayOf(' ', '\n'))
        return (if (space > maxChars / 2) cut.substring(0, space) else cut).trimEnd() + "…"
    }

    /**
     * The small status line at the top of the launcher while no update card is shown: running check, up to date, or a
     * check that could not run (then the installed version is still shown, nothing blocks).
     */
    fun checkLine(phase: CheckPhase, installedName: String): UiText = when (phase) {
        CheckPhase.Running -> UiText.res(R.string.launcher_update_check_running)
        CheckPhase.Done -> UiText.res(R.string.launcher_update_check_done, installedName)
        CheckPhase.Failed -> UiText.res(R.string.launcher_update_check_failed, installedName)
        CheckPhase.Unknown -> UiText.res(R.string.launcher_update_check_unknown, installedName)
    }

    /** Text for a refused install: a signature mismatch gets its own explanation, anything else the generic one with Android's [message]. */
    fun rejectedText(message: String): UiText =
        if (UpdateSourceLogic.isSignatureMismatch(message)) {
            UiText.res(R.string.launcher_update_signature_mismatch)
        } else {
            UiText.res(R.string.launcher_update_rejected, message)
        }

    /** Null when the file has the expected size and SHA-256 (a blank [expectedSha256] = none reported, only the size is checked), otherwise the reason for the error line. */
    fun verifyFile(file: File, expectedSize: Long, expectedSha256: String): UiText? {
        if (!file.isFile) return UiText.res(R.string.launcher_update_verify_missing)
        if (file.length() != expectedSize) {
            return UiText.res(R.string.launcher_update_verify_size, file.length(), expectedSize)
        }
        if (expectedSha256.isBlank()) return null
        val actual = file.inputStream().use { sha256Hex(it) }
        if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) return UiText.res(R.string.launcher_update_verify_hash)
        return null
    }
}

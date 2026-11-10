package app.gamenative.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.Net
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Protocol
import okhttp3.Request
import timber.log.Timber

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val fraction: Float) : UpdateState
    /** [fraction] 0..1 when known (copy phase, then Android's session progress), null = indeterminate. */
    data class Installing(val info: UpdateInfo?, val text: UiText, val fraction: Float? = null) : UpdateState
    data class Error(val message: UiText, val info: UpdateInfo?) : UpdateState
    /** [info] is the offered update when it is known (same process), so the change list stays visible; null after a restart. */
    data class Installed(val versionName: String, val info: UpdateInfo? = null) : UpdateState
}

/** A failure of the update path that carries its text as resource id plus arguments (shown on the card, never as a raw message). */
private class UpdateFailure(val text: UiText) : IOException(text.toString())

private fun failureText(e: Throwable): UiText = (e as? UpdateFailure)?.text ?: UiText.raw(e.message ?: e.javaClass.simpleName)

/** Outcome of the feed check as the launcher shows it ("Suche nach Updates ..." / "Launcher aktuell"). */
enum class CheckPhase {
    /** No check has run in this process yet (or the interval gate skipped it). */
    Unknown,
    Running,
    /** The feed was read; whether it offers a newer launcher is in [UpdateManager.state]. */
    Done,
    /** No network, bad address, server or feed error. Never blocks anything. */
    Failed,
}

/**
 * Self-update from the own download page: feed check (at most every 6 h, silent on errors), APK download with
 * progress, SHA-256 and size check, then installation through a PackageInstaller session. State is process-wide so
 * the card survives recompositions and the download keeps running while the launcher screen is rebuilt.
 */
object UpdateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val _checkPhase = MutableStateFlow(CheckPhase.Unknown)
    val checkPhase: StateFlow<CheckPhase> = _checkPhase.asStateFlow()

    private val started = AtomicBoolean(false)
    private var job: Job? = null

    private val autoInstallLoaded = AtomicBoolean(false)
    private val _autoInstall = MutableStateFlow(false)

    /** Version name of the update the player held back ("Abbrechen"); automatic loading/installing leaves it alone until the next launcher visit. */
    private val _autoHeld = MutableStateFlow<String?>(null)
    val autoHeld: StateFlow<String?> = _autoHeld.asStateFlow()

    /** The offered update whose APK is loaded and verified (in [updatesDir]), so installing it needs no download. */
    private val _ready = MutableStateFlow<UpdateInfo?>(null)
    val readyInfo: StateFlow<UpdateInfo?> = _ready.asStateFlow()

    internal const val ACTION_INSTALL_STATUS = "app.wowforever.update.INSTALL_STATUS"
    private const val UPDATES_DIR = "updates"
    private const val APK_NAME = "update.apk"
    private const val SAFETY_MARGIN_BYTES = 64L * 1024 * 1024

    private val downloadHttp by lazy {
        Net.http.newBuilder()
            .readTimeout(2, TimeUnit.MINUTES)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    private val feedHttp by lazy {
        Net.http.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** The check at app start (first launcher visit of the process); [awaitStartCheck] waits for it. */
    @Volatile
    private var startCheck: Deferred<Unit>? = null

    /**
     * Call when the launcher screen appears. First time per process (= app start): setup, then a fresh check that ignores
     * the interval. Later visits: the interval-gated check.
     */
    fun onLauncherShown(context: Context) {
        val app = context.applicationContext
        autoInstallState(app)
        // A new launcher visit lifts the "Abbrechen" of the last one, so the automatic update gets its next chance.
        _autoHeld.value = null
        // Back in the launcher: an install still shown as running may already be done (no status came, or the app restarted).
        _state.update { settleInstalled(it, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME) }
        if (started.compareAndSet(false, true)) {
            startCheck = scope.async {
                runCatching {
                    val seen = UpdateSettings.seenVersionCode(app)
                    if (seen in 1 until BuildConfig.VERSION_CODE) {
                        _state.compareAndSet(UpdateState.Idle, UpdateState.Installed(BuildConfig.VERSION_NAME))
                    }
                    prepareWhatsNew(app, freshInstall = seen == 0)
                    if (seen != BuildConfig.VERSION_CODE) UpdateSettings.saveSeenVersionCode(app, BuildConfig.VERSION_CODE)
                    File(updatesDir(app)).deleteRecursively()
                    _ready.value = null
                    showCachedFeed(app)
                }.onFailure { Timber.w(it, "Update start-up failed") }
                checkNow(app, force = false, ignoreInterval = true)
            }
        } else {
            scope.launch { checkNow(app, force = false) }
        }
    }

    private val _whatsNew = MutableStateFlow<ChangelogEntry?>(null)

    /** "Neu in ..." for the running version, set once after an update until the player taps "Verstanden". */
    val whatsNew: StateFlow<ChangelogEntry?> = _whatsNew.asStateFlow()

    @Volatile
    private var bundledEntries: List<ChangelogEntry>? = null

    @Volatile
    private var bundledLanguage: String? = null

    /**
     * The changelog bundled with this build: `assets/CHANGELOG.de.md` when the app language is German and the file exists,
     * otherwise `assets/CHANGELOG.md` (English); empty when missing or unreadable.
     */
    fun bundledChangelog(context: Context): List<ChangelogEntry> {
        val language = context.resources.configuration.locales.get(0).language
        val cached = bundledEntries
        if (cached != null && bundledLanguage == language) return cached
        val assets = context.applicationContext.assets
        val names = runCatching { assets.list("")?.toSet() }.getOrNull().orEmpty()
        val asset = Changelog.assetName(language, Changelog.ASSET_DE in names)
        val entries = runCatching {
            assets.open(asset).use { Changelog.parse(it.readBytes().toString(Charsets.UTF_8)) }
        }.onFailure { Timber.w(it, "Changelog asset unreadable") }.getOrDefault(emptyList())
        bundledEntries = entries
        bundledLanguage = language
        return entries
    }

    private fun prepareWhatsNew(app: Context, freshInstall: Boolean) {
        val entry = Changelog.forVersion(bundledChangelog(app), BuildConfig.VERSION_NAME)
        val shown = UpdateSettings.whatsNewShownCode(app)
        if (Changelog.whatsNewDue(shown, BuildConfig.VERSION_CODE, entry != null, freshInstall)) {
            _whatsNew.value = entry
        } else if (shown < BuildConfig.VERSION_CODE) {
            // Fresh installation or no entry for this version: nothing to show, do not ask again.
            UpdateSettings.saveWhatsNewShownCode(app, BuildConfig.VERSION_CODE)
        }
    }

    /** "Verstanden": hides "Neu in ..." for good (until the next version). */
    fun dismissWhatsNew(context: Context) {
        _whatsNew.value = null
        UpdateSettings.saveWhatsNewShownCode(context.applicationContext, BuildConfig.VERSION_CODE)
    }

    /**
     * Before the game starts by itself: waits for the app-start check (at most [timeoutMs], no net = at once).
     * True = a newer launcher is offered (or already downloading/installing), so the launcher should stay open.
     */
    suspend fun awaitStartCheck(context: Context, timeoutMs: Long = START_CHECK_TIMEOUT_MS): Boolean {
        onLauncherShown(context)
        try {
            withTimeoutOrNull(timeoutMs) { startCheck?.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Update start check failed")
        }
        return when (_state.value) {
            is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Installing -> true
            else -> false
        }
    }

    const val START_CHECK_TIMEOUT_MS = 5_000L

    /** "Updates automatisch installieren" (Standard aus); the value is read from the settings on first use. */
    fun autoInstallState(context: Context): StateFlow<Boolean> {
        if (autoInstallLoaded.compareAndSet(false, true)) _autoInstall.value = UpdateSettings.autoInstall(context.applicationContext)
        return _autoInstall.asStateFlow()
    }

    fun setAutoInstall(context: Context, on: Boolean) {
        autoInstallLoaded.set(true)
        UpdateSettings.saveAutoInstall(context.applicationContext, on)
        _autoInstall.value = on
        // Switching it off must not leave a half-done automatic install waiting.
        if (!on) _autoHeld.value = null
    }

    /** "Abbrechen" on the automatic-install notice: no automatic loading or installing of [versionName] until the next launcher visit. */
    fun holdAutoInstall(versionName: String) {
        _autoHeld.value = versionName
    }

    /** "Nach Updates suchen": ignores the interval and reports the outcome. */
    fun checkManually(context: Context) {
        val app = context.applicationContext
        scope.launch { checkNow(app, force = true) }
    }

    fun dismiss() {
        when (val s = _state.value) {
            is UpdateState.Installed, is UpdateState.UpToDate -> _state.value = UpdateState.Idle
            // An error after a failed attempt falls back to the offer so the user can try again.
            is UpdateState.Error -> _state.value = s.info?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            is UpdateState.Installing -> _state.value = s.info?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            else -> Unit
        }
    }

    fun cancel() {
        val s = _state.value
        if (s is UpdateState.Downloading) {
            // The player stopped the download: the automatic update must not restart it by itself.
            _autoHeld.value = s.info.versionName
            job?.cancel()
            _state.value = UpdateState.Available(s.info)
        }
    }

    /**
     * Automatic update, step 1: load and verify the offered APK in the background and keep it for [install]. The state shows the
     * download (banner progress) and returns to the offer when done or failed; a failure is only logged (the manual
     * "Installieren" shows the real error). Does nothing without an offer, while a check or another job runs, without the
     * install permission, or when the player held the version back.
     */
    fun prefetch(context: Context) {
        val app = context.applicationContext
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        // The start check may still replace a cached offer; do not load a stale one.
        if (_checkPhase.value == CheckPhase.Running || _checkPhase.value == CheckPhase.Unknown) return
        if (_autoHeld.value == info.versionName) return
        if (!canInstallPackages(app) || readyApk(app, info) != null) return
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                downloadAndVerify(app, info)
                _ready.value = info
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Automatic update download failed")
                runCatching { File(updatesDir(app)).deleteRecursively() }
                _ready.value = null
                // No endless retry: the next launcher visit tries again.
                _autoHeld.value = info.versionName
            }
            if (_state.value is UpdateState.Downloading) _state.value = UpdateState.Available(info)
        }
    }

    /** The loaded APK of [info] when it is still there and intact, else null. */
    private fun readyApk(context: Context, info: UpdateInfo): File? {
        if (_ready.value != info) return null
        val file = File(updatesDir(context), APK_NAME)
        return file.takeIf { UpdateLogic.verifyFile(it, info.size, info.sha256) == null }
    }

    fun canInstallPackages(context: Context): Boolean = runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(false)

    private fun openInstallPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** Download, verify and install the offered update. The caller only offers this on the start screen, never during a game. */
    fun install(context: Context) {
        val app = context.applicationContext
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Error -> s.info
            else -> null
        } ?: return
        if (!canInstallPackages(app)) {
            openInstallPermissionSettings(app)
            SnackbarManager.show(app.getString(R.string.launcher_update_allow_unknown_apps))
            return
        }
        job?.cancel()
        job = scope.launch {
            try {
                val file = readyApk(app, info) ?: downloadAndVerify(app, info)
                _state.value = UpdateState.Installing(info, UiText.res(R.string.launcher_update_preparing), 0f)
                commitSession(app, file, info)
                val kept = (_state.value as? UpdateState.Installing)?.fraction ?: 0.5f
                _state.value = UpdateState.Installing(info, UiText.res(R.string.launcher_update_waiting_android), kept)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Update install failed")
                runCatching { File(updatesDir(app)).deleteRecursively() }
                _ready.value = null
                _state.value = UpdateState.Error(failureText(e), info)
            }
        }
    }

    // ---- check ------------------------------------------------------------------------------------------------

    private fun hasNetwork(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    private fun busy(): Boolean = _state.value.let {
        it is UpdateState.Checking || it is UpdateState.Downloading || it is UpdateState.Installing
    }

    private fun showCachedFeed(context: Context) {
        val cached = UpdateSettings.cachedFeed(context) ?: return
        val ok = UpdateLogic.parseFeed(cached) as? FeedResult.Ok ?: return
        if (UpdateLogic.isNewer(ok.info, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
            _state.compareAndSet(UpdateState.Idle, UpdateState.Available(ok.info))
        }
    }

    private fun checkNow(context: Context, force: Boolean, ignoreInterval: Boolean = false) {
        if (busy()) return
        val now = System.currentTimeMillis()
        if (!ignoreInterval && !UpdateLogic.shouldCheck(UpdateSettings.lastCheck(context), now, force)) return
        if (!hasNetwork(context)) {
            _checkPhase.value = CheckPhase.Failed
            if (force) _state.value = UpdateState.Error(UiText.res(R.string.launcher_update_no_network), null)
            return
        }
        val feedUrl = UpdateLogic.feedUrl(UpdateSettings.baseUrl(context))
        if (feedUrl == null) {
            _checkPhase.value = CheckPhase.Failed
            if (force) _state.value = UpdateState.Error(UiText.res(R.string.launcher_update_bad_address), null)
            return
        }
        val before = _state.value
        _checkPhase.value = CheckPhase.Running
        if (force) _state.value = UpdateState.Checking
        // Counts as an attempt even when it fails, so a broken server is not hit on every screen visit.
        UpdateSettings.saveLastCheck(context, now)
        try {
            val body = feedHttp.newCall(
                Request.Builder().url(feedUrl).header("User-Agent", "WoW-Forever-Android/${BuildConfig.VERSION_NAME}").build(),
            ).execute().use { rsp ->
                if (!rsp.isSuccessful) throw UpdateFailure(UiText.res(R.string.launcher_update_http_error, rsp.code))
                rsp.peekBody(64 * 1024).string()
            }
            when (val parsed = UpdateLogic.parseFeed(body)) {
                is FeedResult.Invalid -> throw UpdateFailure(UiText.res(R.string.launcher_update_feed_unusable, parsed.reason))
                is FeedResult.Ok -> {
                    UpdateSettings.saveFeed(context, body)
                    _checkPhase.value = CheckPhase.Done
                    if (UpdateLogic.isNewer(parsed.info, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
                        _state.value = UpdateState.Available(parsed.info)
                    } else {
                        _state.value = if (force) UpdateState.UpToDate else if (before is UpdateState.Available) UpdateState.Idle else before
                    }
                }
            }
        } catch (e: Exception) {
            Timber.i(e, "Update check failed")
            _checkPhase.value = CheckPhase.Failed
            if (force) {
                _state.value = UpdateState.Error(UiText.res(R.string.launcher_update_search_failed, (e as? UpdateFailure)?.text ?: (e.message ?: e.javaClass.simpleName)), null)
            } else if (_state.value is UpdateState.Checking) {
                _state.value = before
            }
        }
    }

    // ---- download ---------------------------------------------------------------------------------------------

    private fun updatesDir(context: Context): String =
        File(context.externalCacheDir ?: context.cacheDir, UPDATES_DIR).absolutePath

    private suspend fun downloadAndVerify(context: Context, info: UpdateInfo): File {
        val feedUrl = UpdateLogic.feedUrl(UpdateSettings.baseUrl(context)) ?: throw UpdateFailure(UiText.res(R.string.launcher_update_bad_address))
        val apkUrl = UpdateLogic.resolveApkUrl(feedUrl, info) ?: throw UpdateFailure(UiText.res(R.string.launcher_update_apk_address_unusable))
        val dir = File(updatesDir(context))
        dir.deleteRecursively()
        _ready.value = null
        dir.mkdirs()
        // The file plus the copy Android keeps in the install session.
        val needed = info.size * 2 + SAFETY_MARGIN_BYTES
        if (dir.usableSpace < needed) {
            throw UpdateFailure(UiText.res(R.string.launcher_update_low_storage_download, needed / (1024 * 1024)))
        }
        val target = File(dir, APK_NAME)
        val part = File(dir, target.name + ".part")
        _state.value = UpdateState.Downloading(info, 0f)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        downloadHttp.newCall(Request.Builder().url(apkUrl).header("User-Agent", "WoW-Forever-Android/${BuildConfig.VERSION_NAME}").build())
            .execute().use { rsp ->
                if (!rsp.isSuccessful) throw UpdateFailure(UiText.res(R.string.launcher_update_download_failed, rsp.code))
                val body = rsp.body ?: throw UpdateFailure(UiText.res(R.string.launcher_update_download_empty))
                var read = 0L
                var lastShown = -1
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            if (n == 0) continue
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                            read += n
                            if (read > info.size) throw UpdateFailure(UiText.res(R.string.launcher_update_download_too_big))
                            val percent = (read * 100 / info.size).toInt()
                            if (percent != lastShown) {
                                lastShown = percent
                                if (_state.value is UpdateState.Downloading) {
                                    _state.value = UpdateState.Downloading(info, read.toFloat() / info.size)
                                }
                            }
                            kotlin.coroutines.coroutineContext.ensureActive()
                        }
                    }
                }
            }
        if (part.length() != info.size) {
            part.delete()
            throw UpdateFailure(UiText.res(R.string.launcher_update_download_incomplete, part.length(), info.size))
        }
        val actual = UpdateLogic.hex(digest.digest())
        if (!actual.equals(info.sha256, ignoreCase = true)) {
            part.delete()
            throw UpdateFailure(UiText.res(R.string.launcher_update_hash_cancelled))
        }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        // Belt and braces: check the file as it now sits on disk.
        UpdateLogic.verifyFile(target, info.size, info.sha256)?.let {
            target.delete()
            throw UpdateFailure(UiText.res(R.string.launcher_update_verify_cancelled, it))
        }
        return target
    }

    // ---- install ----------------------------------------------------------------------------------------------

    private fun commitSession(context: Context, apk: File, info: UpdateInfo) {
        val installer = context.packageManager.packageInstaller
        // Clean up sessions of earlier attempts so they cannot pile up.
        runCatching { installer.mySessions.forEach { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android honours this only with UPDATE_PACKAGES_WITHOUT_USER_ACTION (manifest) and when this app is the
                // installer of record, i.e. from the second self-update on; the first one after a sideload still asks.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        // Android reports the install of the session itself here; the card shows it as the second half of the bar.
        val callback = object : PackageInstaller.SessionCallback() {
            override fun onCreated(id: Int) = Unit
            override fun onBadgingChanged(id: Int) = Unit
            override fun onActiveChanged(id: Int, active: Boolean) = Unit
            override fun onProgressChanged(id: Int, progress: Float) {
                if (id != sessionId) return
                if (_state.value is UpdateState.Installing) {
                    _state.value = UpdateState.Installing(
                        info,
                        UiText.res(R.string.launcher_update_android_installing),
                        UpdateLogic.installFraction(0L, 0L, progress),
                    )
                }
            }
            override fun onFinished(id: Int, success: Boolean) {
                if (id == sessionId) runCatching { installer.unregisterSessionCallback(this) }
            }
        }
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        val total = apk.length()
                        val buf = ByteArray(256 * 1024)
                        var copied = 0L
                        var lastShown = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            if (n == 0) continue
                            out.write(buf, 0, n)
                            copied += n
                            val percent = if (total > 0) (copied * 100 / total).toInt() else 100
                            if (percent != lastShown) {
                                lastShown = percent
                                if (_state.value is UpdateState.Installing) {
                                    _state.value = UpdateState.Installing(
                                        info,
                                        UiText.res(R.string.launcher_update_handing_over),
                                        UpdateLogic.installFraction(copied, total, null),
                                    )
                                }
                            }
                        }
                        session.fsync(out)
                    }
                }
                val intent = Intent(context, UpdateStatusReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
                // Mutable on purpose: the system adds the status extras to this intent.
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                installer.registerSessionCallback(callback, Handler(Looper.getMainLooper()))
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.unregisterSessionCallback(callback) }
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    /** Package that installed this app (null = sideloaded/unknown); Android only skips the confirmation when it is this app itself. */
    private fun installerOfRecord(context: Context): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

    /** The state once the install is done: the bar is full and still, the card says "Update fertig". Null info = unknown, nothing to show. */
    internal fun installedState(info: UpdateInfo?): UpdateState =
        if (info != null) UpdateState.Installed(info.versionName, info) else UpdateState.Idle

    /**
     * An install still shown as running ends when the installed app already is the offered version. Other states and
     * installs that are not yet done stay as they are.
     */
    internal fun settleInstalled(state: UpdateState, installedVersionCode: Int, installedVersionName: String): UpdateState {
        if (state !is UpdateState.Installing) return state
        val info = state.info ?: return state
        return if (UpdateLogic.isNewer(info, installedVersionCode, installedVersionName)) state else installedState(info)
    }

    /** Called by [UpdateStatusReceiver] with the status of the install session. */
    internal fun onInstallStatus(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val info = when (val s = _state.value) {
            is UpdateState.Installing -> s.info
            is UpdateState.Error -> s.info
            is UpdateState.Available -> s.info
            else -> null
        }
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    _state.value = UpdateState.Error(UiText.res(R.string.launcher_update_no_confirm), info)
                    return
                }
                val installer = installerOfRecord(context)
                Timber.i("Install needs user action; installer of record=$installer, own package=${context.packageName}")
                // The copy is done here, so the bar keeps its half instead of turning indeterminate during the confirmation.
                val kept = (_state.value as? UpdateState.Installing)?.fraction ?: 0.5f
                _state.value = UpdateState.Installing(info, UpdateLogic.confirmText(installer, context.packageName), kept)
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { _state.value = UpdateState.Error(UiText.res(R.string.launcher_update_confirm_dialog_failed, it.message.orEmpty()), info) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // The card ends here (full bar, "Update fertig"); Android restarts the app on its own, and a launcher visit
                // settles the state too, so it never waits on "startet neu" with an endless bar.
                _state.value = installedState(info)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                _state.value = if (info != null) UpdateState.Available(info) else UpdateState.Idle
                SnackbarManager.show(context.getString(R.string.launcher_update_install_cancelled))
            }
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                _state.value = UpdateState.Error(UiText.res(R.string.launcher_update_low_storage_install, message), info)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                _state.value = UpdateState.Error(UiText.res(R.string.launcher_update_rejected, message), info)
            else ->
                _state.value = UpdateState.Error(UiText.res(R.string.launcher_update_install_failed, status, message), info)
        }
    }
}

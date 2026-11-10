package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/*
 * Eigene Addons (Auftrag addonlink, 11.11.2026): Liste der selbst eingebauten Addons, Laden per Link oder Datei, Prüfen,
 * Einbauen, Aktualisieren. Alles ohne Android-Klassen, damit es in JUnit läuft; Netz kommt über [AddonHttp].
 *
 * Ablauf: [CustomAddonService.plan] (Link deuten, bei GitHub und WoWInterface die Download-Adresse erfragen) ->
 * [CustomAddonService.prepare] oder [CustomAddonService.prepareStream] (laden, höchstens 50 MB, prüfen: [CustomAddonScan]) ->
 * Anzeige und Bestätigung in der Oberfläche -> [CustomAddonService.install]. Erst dabei wird in Interface/AddOns geschrieben.
 * Aktiv-Schalter wie bei den kuratierten Addons ([AddonToggle]). Die Liste steht in `.wf-custom-addons.json` in Interface/AddOns.
 */

enum class CustomSourceKind(val key: String) {
    LINK("link"),
    FILE("datei"),
    GITHUB("github"),
    WOWINTERFACE("wowinterface"),
    ;

    companion object {
        fun of(key: String): CustomSourceKind = values().firstOrNull { it.key == key } ?: LINK
    }
}

/** Ein selbst eingebautes Paket (ein Zip, ein oder mehrere Ordner). */
data class CustomAddonRecord(
    val id: String,
    val name: String,
    val version: String?,
    /** Release-Tag (GitHub), `Zweig@Commit` (GitHub ohne Release) oder Version der Seite (WoWInterface). */
    val tag: String?,
    val kind: CustomSourceKind,
    /** Link oder Dateiname, wie der Nutzer ihn angegeben hat. */
    val source: String,
    /** `Besitzer/Name`, nur bei GitHub (dann gibt es "Auf Update prüfen"). */
    val repo: String?,
    val interfaces: List<Int>,
    val folders: List<String>,
    val sha256: String,
    val bytes: Long,
    val installedAt: Long,
)

object CustomAddonStore {
    const val FILE = ".wf-custom-addons.json"
    private const val VERSION = 1

    fun read(addonsDir: File): List<CustomAddonRecord> {
        val file = File(addonsDir, FILE)
        if (!file.isFile) return emptyList()
        return try {
            val list = JSONObject(file.readText(Charsets.UTF_8)).optJSONArray("addons") ?: return emptyList()
            (0 until list.length()).mapNotNull { parse(list.optJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun parse(o: JSONObject?): CustomAddonRecord? {
        o ?: return null
        val id = o.optString("id", "")
        val folders = o.optJSONArray("folders")?.let { a -> (0 until a.length()).map { a.optString(it, "") } }.orEmpty().filter { it.isNotEmpty() }
        if (id.isEmpty() || folders.isEmpty()) return null
        val interfaces = o.optJSONArray("interfaces")?.let { a -> (0 until a.length()).map { a.optInt(it, 0) } }.orEmpty().filter { it > 0 }
        return CustomAddonRecord(
            id = id,
            name = o.optString("name", id),
            version = o.optString("version", "").ifEmpty { null },
            tag = o.optString("tag", "").ifEmpty { null },
            kind = CustomSourceKind.of(o.optString("kind", "")),
            source = o.optString("source", ""),
            repo = o.optString("repo", "").ifEmpty { null },
            interfaces = interfaces,
            folders = folders,
            sha256 = o.optString("sha256", ""),
            bytes = o.optLong("bytes", 0L),
            installedAt = o.optLong("installedAt", 0L),
        )
    }

    fun write(addonsDir: File, records: List<CustomAddonRecord>) {
        addonsDir.mkdirs()
        val list = JSONArray()
        for (r in records) {
            list.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("version", r.version ?: "")
                    .put("tag", r.tag ?: "")
                    .put("kind", r.kind.key)
                    .put("source", r.source)
                    .put("repo", r.repo ?: "")
                    .put("interfaces", JSONArray(r.interfaces))
                    .put("folders", JSONArray(r.folders))
                    .put("sha256", r.sha256)
                    .put("bytes", r.bytes)
                    .put("installedAt", r.installedAt),
            )
        }
        val text = JSONObject().put("version", VERSION).put("addons", list).toString(2) + "\n"
        val tmp = File(addonsDir, "$FILE.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        val target = File(addonsDir, FILE)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw AddonInstallException(UiText.res(R.string.launcher_addon_store_save_failed))
        }
    }
}

/** Versionsvergleich für "Auf Update prüfen". */
object CustomVersion {
    /** Ist [latest] neuer als [installed]? Bei `Zweig@Commit` zählt jeder Unterschied, sonst der Vergleich der Zahlen im Tag. */
    fun isNewer(installed: String?, latest: String): Boolean {
        if (installed.isNullOrBlank()) return true
        if (installed == latest) return false
        if ('@' in installed || '@' in latest) return true
        return AddonVersion.isUpdate(installed, latest)
    }
}

/** Wahl unter mehreren Zip-Dateien eines GitHub-Releases. */
object CustomAssetPicker {
    private val FLAVOR = Regex(
        "(?:^|[^a-z])(classic|cata|cataclysm|bcc|tbc|wrath|wotlk|vanilla|mists|mop|era|retail|mainline)(?:[^a-z]|$)",
        RegexOption.IGNORE_CASE,
    )

    /** Forever-Archiv laut release.json, sonst ein Archiv ohne Spielversions-Zusatz im Namen, sonst das erste nach Name. */
    fun choose(zips: List<ReleaseAsset>, metaText: String?): ReleaseAsset? {
        if (zips.isEmpty()) return null
        val sorted = zips.sortedBy { it.name }
        val forever = AddonAssetPicker.foreverFiles(metaText).orEmpty()
        sorted.firstOrNull { it.name in forever }?.let { return it }
        sorted.filter { !FLAVOR.containsMatchIn(it.name.removeSuffix(".zip")) }
            .minWithOrNull(compareBy<ReleaseAsset>({ it.name.length }, { it.name }))?.let { return it }
        return sorted.first()
    }
}

/** Wohin und wie geladen wird. */
data class DownloadPlan(
    val url: String,
    val hosts: Set<String>,
    val kind: CustomSourceKind,
    val source: String,
    val repo: String?,
    val tag: String?,
    val md5: String?,
    val size: Long,
)

sealed class PlanResult {
    data class Ok(val plan: DownloadPlan) : PlanResult()
    data class Fail(val reason: UiText) : PlanResult()

    /** CurseForge/Wago: nur über den Browser; [pageUrl] ist die Seite zum Öffnen. */
    data class Browser(val site: String, val pageUrl: String, val message: UiText) : PlanResult()
}

/** Geladen und geprüft, aber noch nicht eingebaut. [zip] liegt im Zwischenordner. */
class Prepared(val zip: File, val plan: DownloadPlan, val sha256: String, val bytes: Long, val scan: ZipScan)

sealed class PrepareResult {
    data class Ok(val prepared: Prepared) : PrepareResult()
    data class Fail(val reason: UiText) : PrepareResult()
}

sealed class CustomInstallOutcome {
    data class Installed(val record: CustomAddonRecord) : CustomInstallOutcome()
    data class Failed(val reason: UiText) : CustomInstallOutcome()
}

sealed class UpdateCheck {
    data class Newer(val plan: DownloadPlan, val latestTag: String) : UpdateCheck()
    object Current : UpdateCheck()
    object NotApplicable : UpdateCheck()
    data class Fail(val reason: UiText) : UpdateCheck()
}

data class CustomAddonView(val record: CustomAddonRecord, val enable: EnableState)

class CustomAddonService(
    http: AddonHttp,
    private val tmpDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private val client = AddonHttp { url, headers -> http.get(url, mapOf("User-Agent" to USER_AGENT) + headers) }
    private val apiHeaders = mapOf("Accept" to "application/vnd.github+json")
    private val shaHeaders = mapOf("Accept" to "application/vnd.github.sha")
    private val githubApi = AddonHosts.GITHUB
    private val githubLoad = AddonHosts.GITHUB + AddonHosts.CODELOAD

    // ---- Link -> Download-Adresse ------------------------------------------------------------------------------------

    /** Deutet [link] und erfragt bei GitHub und WoWInterface die Download-Adresse. Blockiert (Netz). */
    fun plan(link: AddonLink): PlanResult = try {
        when (link) {
            is AddonLink.DirectZip ->
                PlanResult.Ok(DownloadPlan(link.url, AddonHosts.ANY_PUBLIC_HTTPS, CustomSourceKind.LINK, link.url, null, null, null, -1L))
            is AddonLink.NeedsBrowser -> PlanResult.Browser(link.site, link.pageUrl, link.message)
            is AddonLink.Unsupported -> PlanResult.Fail(link.reason)
            is AddonLink.WowInterface -> planWowInterface(link)
            is AddonLink.GitHubRepo -> planGitHub(link)
        }
    } catch (e: AddonNetworkException) {
        PlanResult.Fail(e.text)
    } catch (e: IOException) {
        PlanResult.Fail(UiText.res(R.string.launcher_addon_no_connection, e.javaClass.simpleName))
    }

    private fun planWowInterface(link: AddonLink.WowInterface): PlanResult {
        val details = try {
            val url = "https://api.mmoui.com/v3/game/WOW/filedetails/${link.fileId}.json"
            WowInterfaceParser.parseDetails(AddonFetch.getText(client, url, AddonHosts.WOWINTERFACE, now = clock))
        } catch (e: IOException) {
            null
        }
        // Ohne Antwort der Schnittstelle gilt der bekannte Direktlink der Seite (am 10.11.2026 mit curl geprüft).
        val url = details?.downloadUrl ?: "https://cdn.wowinterface.com/downloads/getfile.php?id=${link.fileId}"
        val page = "https://www.wowinterface.com/downloads/info${link.fileId}"
        return PlanResult.Ok(DownloadPlan(url, AddonHosts.WOWINTERFACE, CustomSourceKind.WOWINTERFACE, page, null, details?.version, details?.md5, -1L))
    }

    private fun planGitHub(link: AddonLink.GitHubRepo): PlanResult {
        val api = "https://api.github.com/repos/${link.slug}"
        val releaseUrl = if (link.tag != null) "$api/releases/tags/${encodePath(link.tag)}" else "$api/releases/latest"
        val json = try {
            AddonFetch.getText(client, releaseUrl, githubApi, apiHeaders, now = clock)
        } catch (e: AddonNetworkException) {
            if (e.notFound) null else throw e
        }
        val release = GitHubReleaseParser.parse(json)?.takeIf { !it.draft }
        if (release != null) return planRelease(link, api, release)
        if (link.tag != null) return PlanResult.Fail(UiText.res(R.string.launcher_addon_plan_release_missing, link.tag, link.slug))
        return planBranch(link, api)
    }

    private fun planRelease(link: AddonLink.GitHubRepo, api: String, release: GitHubRelease): PlanResult {
        val zips = release.assets.filter { a ->
            a.name.endsWith(".zip", ignoreCase = true) && !a.name.contains("nolib", ignoreCase = true) &&
                a.size <= MAX_DOWNLOAD_BYTES && AddonHosts.isAllowed(a.url, githubApi)
        }
        val chosen = when {
            zips.isEmpty() -> null
            zips.size == 1 -> zips.single()
            else -> {
                val meta = release.metaAsset?.let { m ->
                    try {
                        AddonFetch.getText(client, m.url, githubApi, now = clock)
                    } catch (e: IOException) {
                        null
                    }
                }
                CustomAssetPicker.choose(zips, meta)
            }
        }
        val page = "https://github.com/${link.slug}"
        if (chosen != null) {
            return PlanResult.Ok(DownloadPlan(chosen.url, githubLoad, CustomSourceKind.GITHUB, page, link.slug, release.tag, null, chosen.size))
        }
        // Das Release hat kein Zip: dann das Quellcode-Archiv des Tags.
        val url = "$api/zipball/${encodePath(release.tag)}"
        return PlanResult.Ok(DownloadPlan(url, githubLoad, CustomSourceKind.GITHUB, page, link.slug, release.tag, null, -1L))
    }

    private fun planBranch(link: AddonLink.GitHubRepo, api: String): PlanResult {
        val repoJson = try {
            AddonFetch.getText(client, api, githubApi, apiHeaders, now = clock)
        } catch (e: AddonNetworkException) {
            if (e.notFound) return PlanResult.Fail(UiText.res(R.string.launcher_addon_plan_repo_missing, link.slug))
            throw e
        }
        val branch = try {
            JSONObject(repoJson).optString("default_branch", "")
        } catch (e: JSONException) {
            ""
        }
        if (branch.isEmpty()) return PlanResult.Fail(UiText.res(R.string.launcher_addon_github_unreadable))
        val sha = try {
            AddonFetch.getText(client, "$api/commits/${encodePath(branch)}", githubApi, shaHeaders, now = clock).trim()
                .takeIf { Regex("^[0-9a-f]{40}$").matches(it) }
        } catch (e: IOException) {
            null
        }
        val tag = if (sha != null) "$branch@${sha.take(7)}" else branch
        val url = "$api/zipball/${encodePath(branch)}"
        return PlanResult.Ok(DownloadPlan(url, githubLoad, CustomSourceKind.GITHUB, "https://github.com/${link.slug}", link.slug, tag, null, -1L))
    }

    // ---- Laden und Prüfen --------------------------------------------------------------------------------------------

    /** Lädt [plan] in den Zwischenordner und prüft das Archiv. [cancelled] bricht ab, [onProgress] bekommt (Bytes, Gesamt oder -1). */
    fun prepare(plan: DownloadPlan, onProgress: (Long, Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false }): PrepareResult {
        tmpDir.mkdirs()
        val zip = File(tmpDir, "custom-${System.nanoTime()}.zip")
        val started = clock()
        return try {
            val download = AddonFetch.download(
                client, plan.url, plan.hosts, zip, MAX_DOWNLOAD_BYTES,
                { done, total ->
                    if (cancelled()) throw AddonNetworkException(UiText.res(R.string.launcher_addon_cancelled))
                    if (clock() - started > TIME_LIMIT_MS) throw AddonNetworkException(UiText.plural(R.plurals.launcher_addon_time_limit, (TIME_LIMIT_MS / 60_000).toInt()))
                    onProgress(done, total)
                },
                clock,
            )
            log("Eigenes Addon: ${download.bytes} Bytes, SHA-256 ${download.sha256}, ${plan.url}")
            if (plan.md5 != null && !plan.md5.equals(digest(zip, "MD5"), ignoreCase = true)) {
                zip.delete()
                return PrepareResult.Fail(UiText.res(R.string.launcher_addon_checksum_mismatch))
            }
            PrepareResult.Ok(Prepared(zip, plan, download.sha256, download.bytes, scanZip(zip)))
        } catch (e: AddonNetworkException) {
            zip.delete()
            PrepareResult.Fail(e.text)
        } catch (e: AddonInstallException) {
            zip.delete()
            PrepareResult.Fail(e.text)
        } catch (e: IOException) {
            zip.delete()
            PrepareResult.Fail(UiText.res(R.string.launcher_addon_file_error, e.message ?: e.javaClass.simpleName))
        }
    }

    /** Wie [prepare], aber aus einem geöffneten Strom (Android-Dateiauswahl); [displayName] ist der Dateiname für die Anzeige. */
    fun prepareStream(input: InputStream, displayName: String, cancelled: () -> Boolean = { false }): PrepareResult {
        tmpDir.mkdirs()
        val zip = File(tmpDir, "custom-${System.nanoTime()}.zip")
        return try {
            var total = 0L
            val md = MessageDigest.getInstance("SHA-256")
            zip.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (cancelled()) throw AddonInstallException(UiText.res(R.string.launcher_addon_cancelled))
                    total += n
                    if (total > MAX_DOWNLOAD_BYTES) throw AddonInstallException(UiText.res(R.string.launcher_addon_net_file_too_big_mb, MAX_DOWNLOAD_BYTES / (1024 * 1024)))
                    md.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
            }
            val plan = DownloadPlan("", emptySet(), CustomSourceKind.FILE, displayName, null, null, null, total)
            PrepareResult.Ok(Prepared(zip, plan, AddonFetch.hex(md.digest()), total, scanZip(zip)))
        } catch (e: AddonInstallException) {
            zip.delete()
            PrepareResult.Fail(e.text)
        } catch (e: IOException) {
            zip.delete()
            PrepareResult.Fail(UiText.res(R.string.launcher_addon_file_error, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun scanZip(zip: File): ZipScan {
        val magic = ByteArray(4)
        val read = zip.inputStream().use { it.read(magic) }
        val isZip = read == 4 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        if (!isZip) throw AddonInstallException(UiText.res(R.string.launcher_addon_not_zip))
        return CustomAddonScan.scan(zip)
    }

    /** Räumt Reste im Zwischenordner auf (abgebrochene Vorgänge). */
    fun cleanTemp() {
        tmpDir.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("custom-") }.forEach { it.delete() }
    }

    /** Verwirft ein geladenes Archiv, das nicht eingebaut wird. */
    fun discard(prepared: Prepared) {
        prepared.zip.delete()
    }

    // ---- Einbauen, Liste, An/Aus, Entfernen, Update -------------------------------------------------------------------

    /** Baut das geprüfte Archiv ein. [replace] muss gesetzt sein, wenn Ordner schon da sind (Rückfrage vorher). */
    fun install(prepared: Prepared, addonsDir: File, protectedFolders: Set<String>, replace: Boolean): CustomInstallOutcome {
        try {
            val flavorDir = addonsDir.parentFile?.parentFile
            if (flavorDir == null || !flavorDir.isDirectory) {
                return CustomInstallOutcome.Failed(AddonText.gameNotInstalled(flavorDir?.name))
            }
            if (freeBytes(addonsDir) < prepared.scan.declaredBytes + 16L * 1024 * 1024) {
                return CustomInstallOutcome.Failed(UiText.res(R.string.launcher_addon_not_enough_space))
            }
            val records = CustomAddonStore.read(addonsDir)
            val addons = prepared.scan.addons
            val names = addons.map { it.folder.lowercase() }
            val overlapped = records.filter { r -> r.folders.any { it.lowercase() in names } }
            val wasDisabled = overlapped.isNotEmpty() && overlapped.all { AddonToggle.state(addonsDir, it.folders) == EnableState.DISABLED }

            val folders = CustomAddonInstall.install(prepared.zip, addonsDir, addons, protectedFolders, replace, MAX_UNPACKED_BYTES)
            if (wasDisabled) AddonToggle.setEnabled(addonsDir, folders, false)

            val main = addons.first()
            val record = CustomAddonRecord(
                id = main.folder.lowercase(),
                name = if (addons.size > 1) "${main.title} (+${addons.size - 1})" else main.title,
                version = main.version ?: prepared.plan.tag,
                tag = prepared.plan.tag,
                kind = prepared.plan.kind,
                source = prepared.plan.source,
                repo = prepared.plan.repo,
                interfaces = main.interfaces,
                folders = folders,
                sha256 = prepared.sha256,
                bytes = prepared.bytes,
                installedAt = clock(),
            )
            val others = records.filter { it.id != record.id }.mapNotNull { r ->
                val left = r.folders.filter { f -> folders.none { it.equals(f, ignoreCase = true) } }
                if (left.isEmpty()) null else r.copy(folders = left)
            }
            CustomAddonStore.write(addonsDir, others + record)
            return CustomInstallOutcome.Installed(record)
        } catch (e: AddonInstallException) {
            return CustomInstallOutcome.Failed(e.text)
        } catch (e: IOException) {
            return CustomInstallOutcome.Failed(UiText.res(R.string.launcher_addon_file_error, e.message ?: e.javaClass.simpleName))
        } finally {
            prepared.zip.delete()
        }
    }

    /** Die eigenen Addons mit ihrem Aktiv-Zustand, ohne Netz. */
    fun list(addonsDir: File): List<CustomAddonView> =
        CustomAddonStore.read(addonsDir).map { CustomAddonView(it, AddonToggle.state(addonsDir, it.folders)) }

    /** Null = geklappt, sonst der Grund. */
    fun setEnabled(record: CustomAddonRecord, addonsDir: File, enabled: Boolean): UiText? = try {
        AddonToggle.setEnabled(addonsDir, record.folders, enabled)
        null
    } catch (e: IOException) {
        e.addonText(UiText.res(R.string.launcher_addon_toggle_failed))
    }

    /** Entfernt die Ordner (samt `_bak`) und den Listeneintrag. Null = geklappt, sonst der Grund. */
    fun remove(record: CustomAddonRecord, addonsDir: File, protectedFolders: Set<String>): UiText? = try {
        CustomAddonInstall.remove(addonsDir, record.folders, protectedFolders)
        CustomAddonStore.write(addonsDir, CustomAddonStore.read(addonsDir).filter { it.id != record.id })
        null
    } catch (e: IOException) {
        e.addonText(UiText.res(R.string.launcher_addon_remove_failed))
    }

    /** Fragt bei GitHub-Quellen nach dem neuesten Release (oder Commit); andere Quellen: [UpdateCheck.NotApplicable]. */
    fun checkUpdate(record: CustomAddonRecord): UpdateCheck {
        val parts = record.repo?.split('/').orEmpty()
        if (record.kind != CustomSourceKind.GITHUB || parts.size != 2) return UpdateCheck.NotApplicable
        return when (val p = plan(AddonLink.GitHubRepo(parts[0], parts[1], null))) {
            is PlanResult.Ok -> {
                val latest = p.plan.tag.orEmpty()
                if (latest.isNotEmpty() && CustomVersion.isNewer(record.tag, latest)) UpdateCheck.Newer(p.plan, latest) else UpdateCheck.Current
            }
            is PlanResult.Fail -> UpdateCheck.Fail(p.reason)
            is PlanResult.Browser -> UpdateCheck.Fail(p.message)
        }
    }

    private fun freeBytes(dir: File): Long =
        generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace ?: 0L

    private fun digest(file: File, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return AddonFetch.hex(md.digest())
    }

    private fun encodePath(value: String): String =
        value.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    companion object {
        const val USER_AGENT = "WowForeverFold"
        const val MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024
        const val MAX_UNPACKED_BYTES = 200L * 1024 * 1024
        const val TIME_LIMIT_MS = 5L * 60 * 1000
    }
}

/** Texte der Liste "Eigene Addons" als reine Funktionen ([UiText], die Oberfläche löst sie auf). */
object CustomAddonLabels {
    fun sourceLine(r: CustomAddonRecord): UiText = when (r.kind) {
        CustomSourceKind.GITHUB -> UiText.res(R.string.launcher_addon_source_github, r.repo ?: r.source)
        CustomSourceKind.WOWINTERFACE -> UiText.res(R.string.launcher_addon_custom_source_wowi, r.source.substringAfterLast('/'))
        CustomSourceKind.LINK -> UiText.res(R.string.launcher_addon_custom_source_link, r.source.removePrefix("https://").substringBefore('/'))
        CustomSourceKind.FILE -> UiText.res(R.string.launcher_addon_custom_source_file, r.source)
    }

    /** Quelle eines geladenen, noch nicht eingebauten Archivs. */
    fun planLine(plan: DownloadPlan): UiText = when (plan.kind) {
        CustomSourceKind.GITHUB ->
            if (plan.tag != null) {
                UiText.res(R.string.launcher_addon_custom_plan_github_tag, plan.repo.orEmpty(), AddonVersion.display(plan.tag))
            } else {
                UiText.res(R.string.launcher_addon_source_github, plan.repo.orEmpty())
            }
        CustomSourceKind.WOWINTERFACE -> UiText.res(R.string.launcher_addon_custom_source_wowi, plan.source.substringAfterLast('/'))
        CustomSourceKind.LINK -> UiText.res(R.string.launcher_addon_custom_source_link, plan.source.removePrefix("https://").substringBefore('/'))
        CustomSourceKind.FILE -> UiText.res(R.string.launcher_addon_custom_source_file, plan.source)
    }

    fun versionLine(r: CustomAddonRecord): UiText {
        val v = r.version ?: r.tag
        return if (v.isNullOrBlank()) UiText.res(R.string.launcher_addon_version_unknown) else UiText.res(R.string.launcher_addon_version_label, AddonVersion.display(v))
    }
}

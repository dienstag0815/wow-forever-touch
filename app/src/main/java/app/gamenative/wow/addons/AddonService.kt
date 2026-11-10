package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Ergebnis der Frage "was ist die neueste Version?". */
sealed class LatestResult {
    data class Ok(val picked: PickedRelease) : LatestResult()
    data class Fail(val reason: UiText) : LatestResult()
}

sealed class InstallOutcome {
    data class Installed(val record: InstalledRecord) : InstallOutcome()
    data class Failed(val reason: UiText) : InstallOutcome()
}

/**
 * Der Ablauf des Addon-Managers ohne Android-Klassen: neueste Version erfragen (1 Stunde zwischengespeichert), laden,
 * prüfen, sicher entpacken, im Verzeichnis eintragen. Netz kommt über [AddonHttp], damit alles in JUnit mit einer
 * Attrappe läuft. Aufrufe blockieren und gehören auf einen Hintergrund-Thread. Die Ordner der Katalogeinträge sind die
 * einzigen, die verändert werden; der Aufrufer sorgt dafür, dass das Spiel nicht läuft.
 */
class AddonService(
    private val http: AddonHttp,
    private val cache: AddonCache,
    private val tmpDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    private val apiHeaders = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to USER_AGENT)
    private val plainHeaders = mapOf("User-Agent" to USER_AGENT)

    /** Neueste installierbare Version des Eintrags; bei Netzfehler der zwischengespeicherte Stand, sonst [LatestResult.Fail]. */
    fun latest(entry: CatalogEntry): LatestResult = try {
        when (val source = entry.source) {
            is AddonSource.GitHub -> latestGitHub(entry, source)
            is AddonSource.WowInterface -> latestWowInterface(source)
        }
    } catch (e: AddonNetworkException) {
        LatestResult.Fail(e.text)
    } catch (e: IOException) {
        LatestResult.Fail(UiText.res(R.string.launcher_addon_no_connection, e.javaClass.simpleName))
    }

    private fun cachedText(key: String, url: String, hosts: Set<String>, headers: Map<String, String>): String =
        cache.fresh(key) ?: try {
            AddonFetch.getText(http, url, hosts, headers, now = clock).also { cache.put(key, it) }
        } catch (e: IOException) {
            cache.stale(key) ?: throw e
        }

    private fun latestGitHub(entry: CatalogEntry, source: AddonSource.GitHub): LatestResult {
        val key = "gh-${source.owner}-${source.repo}-latest"
        val json = cachedText(key, "https://api.github.com/repos/${source.slug}/releases/latest", AddonHosts.GITHUB, apiHeaders)
        val release = GitHubReleaseParser.parse(json) ?: return LatestResult.Fail(UiText.res(R.string.launcher_addon_github_unreadable))
        val metaText = release.metaAsset?.takeIf { source.requireForever }?.let { meta ->
            try {
                cachedText("gh-${source.owner}-${source.repo}-meta-${release.tag}", meta.url, AddonHosts.GITHUB, plainHeaders)
            } catch (e: IOException) {
                null
            }
        }
        return when (val r = AddonAssetPicker.pick(entry, source, release, metaText)) {
            is PickResult.Ok -> LatestResult.Ok(r.picked)
            is PickResult.Fail -> LatestResult.Fail(r.reason)
        }
    }

    private fun latestWowInterface(source: AddonSource.WowInterface): LatestResult {
        val url = "https://api.mmoui.com/v3/game/WOW/filedetails/${source.fileId}.json"
        val json = cachedText("wowi-${source.fileId}", url, AddonHosts.WOWINTERFACE, plainHeaders)
        val file = WowInterfaceParser.parseDetails(json) ?: return LatestResult.Fail(UiText.res(R.string.launcher_addon_wowi_unreadable))
        val asset = ReleaseAsset("wowinterface-${source.fileId}.zip", file.downloadUrl, 0L, "application/zip")
        return LatestResult.Ok(PickedRelease(file.version, null, asset, file.md5))
    }

    /** Installiert oder aktualisiert [entry] in [addonsDir]. Meldet den Fortschritt als (Bytes, Gesamt oder -1). */
    fun install(
        entry: CatalogEntry,
        addonsDir: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): InstallOutcome {
        val flavorDir = addonsDir.parentFile?.parentFile
        if (flavorDir == null || !flavorDir.isDirectory) {
            return InstallOutcome.Failed(AddonText.gameNotInstalled(flavorDir?.name))
        }
        val record = AddonManifest.read(addonsDir)[entry.id]
        if (record == null && File(addonsDir, entry.folders.first()).exists()) {
            return InstallOutcome.Failed(UiText.res(R.string.launcher_addon_folder_foreign, entry.folders.first()))
        }
        val picked = when (val l = latest(entry)) {
            is LatestResult.Ok -> l.picked
            is LatestResult.Fail -> return InstallOutcome.Failed(l.reason)
        }
        if (freeBytes(addonsDir) < entry.maxUnpackedBytes) {
            return InstallOutcome.Failed(UiText.res(R.string.launcher_addon_not_enough_space_for, entry.name))
        }
        val zip = File(tmpDir, "${entry.id}.zip")
        return try {
            val download = AddonFetch.download(
                http, picked.asset.url, AddonHosts.forSource(entry.source), zip, entry.maxDownloadBytes, onProgress, clock,
            )
            log("Addon ${entry.id} ${picked.tag}: ${download.bytes} Bytes, SHA-256 ${download.sha256}, ${picked.asset.url}")
            if (picked.md5 != null && !picked.md5.equals(digest(zip, "MD5"), ignoreCase = true)) {
                return InstallOutcome.Failed(UiText.res(R.string.launcher_addon_checksum_mismatch))
            }
            val wasDisabled = record != null && AddonToggle.state(addonsDir, entry.folders) == EnableState.DISABLED
            val folders = AddonZip.install(zip, addonsDir, entry.folders, entry.maxUnpackedBytes)
            if (wasDisabled) AddonToggle.setEnabled(addonsDir, folders, false)
            val installed = InstalledRecord(entry.id, picked.tag, download.sha256, download.bytes, clock(), folders, picked.asset.name)
            AddonManifest.put(addonsDir, installed)
            InstallOutcome.Installed(installed)
        } catch (e: AddonNetworkException) {
            InstallOutcome.Failed(e.text)
        } catch (e: AddonInstallException) {
            InstallOutcome.Failed(e.text)
        } catch (e: IOException) {
            InstallOutcome.Failed(UiText.res(R.string.launcher_addon_file_error, e.message ?: e.javaClass.simpleName))
        } finally {
            zip.delete()
        }
    }

    /** Entfernt [entry] (nur wenn es von hier installiert wurde). */
    fun remove(entry: CatalogEntry, addonsDir: File): UiText? {
        val record = AddonManifest.read(addonsDir)[entry.id]
            ?: return UiText.res(R.string.launcher_addon_remove_not_ours, entry.name)
        return try {
            AddonFiles.remove(addonsDir, entry.folders)
            AddonManifest.remove(addonsDir, record.id)
            null
        } catch (e: IOException) {
            e.addonText(UiText.res(R.string.launcher_addon_remove_failed))
        }
    }

    /** Schaltet [entry] aktiv/aus (nur wenn es von hier installiert wurde). Null = geklappt, sonst der Grund. */
    fun setEnabled(entry: CatalogEntry, addonsDir: File, enabled: Boolean): UiText? {
        if (AddonManifest.read(addonsDir)[entry.id] == null) return UiText.res(R.string.launcher_addon_toggle_not_ours, entry.name)
        return try {
            AddonToggle.setEnabled(addonsDir, entry.folders, enabled)
            null
        } catch (e: IOException) {
            e.addonText(UiText.res(R.string.launcher_addon_toggle_failed))
        }
    }

    /** Der Zustand aller Katalogeinträge, ohne Netz; [latest] sind die schon bekannten neuesten Tags je ID. */
    fun statuses(catalog: AddonCatalog, addonsDir: File, latest: Map<String, String>): List<AddonStatus> {
        val records = AddonManifest.read(addonsDir)
        return catalog.entries.map { AddonStatusLogic.compute(it, records[it.id], addonsDir, latest[it.id]) }
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

    companion object {
        const val USER_AGENT = "WoWForever-Launcher/addons"
    }
}

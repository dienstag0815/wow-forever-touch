package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/*
 * Eigene Addons (Auftrag addonlink): ein Zip prüfen, bevor etwas in Interface/AddOns geschrieben wird. Reine Dateilogik, läuft
 * in JUnit. Gefunden wird jeder Ordner mit `<Ordner>.toc`, im Zip-Wurzelordner, eine Ebene tiefer (`Addon/Addon.toc`) oder
 * unter einem Hüllordner (GitHub-zipball: `repo-main/Addon/Addon.toc` oder `repo-main/Addon.toc`). Liegt die .toc direkt im
 * Hüllordner oder im Wurzelordner, kommt der Ordnername aus dem Namen der .toc. Unsichere Pfade (Zip-Slip, absolut, `..`,
 * Laufwerk, `:`, NUL) lassen die ganze Prüfung scheitern.
 */

/** Titel, Version und Interface-Nummern aus einer .toc. */
data class TocInfo(val title: String?, val version: String?, val interfaces: List<Int>)

object TocParser {
    private val MARKUP = Regex("\\|c[0-9a-fA-F]{8}|\\|r|\\|T[^|]*\\|t|\\|n")
    private val SPLIT = Regex("[,\\s]+")

    fun parse(text: String): TocInfo {
        var title: String? = null
        var version: String? = null
        val interfaces = ArrayList<Int>()
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿").trim()
            if (!line.startsWith("##")) continue
            val body = line.removePrefix("##").trim()
            val colon = body.indexOf(':')
            if (colon < 0) continue
            val key = body.substring(0, colon).trim().lowercase()
            val value = body.substring(colon + 1).trim()
            when (key) {
                "title" -> if (title == null) title = MARKUP.replace(value, "").trim().ifEmpty { null }
                // "@project-version@" bleibt stehen, wenn ein Repository ohne Packprogramm geladen wurde.
                "version" -> if (version == null && '@' !in value) version = value.ifEmpty { null }
                "interface" -> if (interfaces.isEmpty()) {
                    value.split(SPLIT).mapNotNull { it.toIntOrNull() }.forEach { interfaces += it }
                }
            }
        }
        return TocInfo(title, version, interfaces)
    }
}

/** Ein im Zip gefundenes Addon: [folder] ist der Zielordner, [prefix] der Pfad im Zip, unter dem seine Dateien liegen. */
data class FoundAddon(
    val folder: String,
    val prefix: List<String>,
    val title: String,
    val version: String?,
    val interfaces: List<Int>,
)

data class ZipScan(val addons: List<FoundAddon>, val entryCount: Int, val declaredBytes: Long)

object CustomAddonScan {
    const val MAX_ADDONS = 30
    private const val MAX_TOC_BYTES = 128 * 1024
    private const val MAX_DEPTH = 2

    internal val FOLDER_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")

    private val FLAVOR = Regex(
        "^(.+?)[_-](mainline|classic|vanilla|bcc|tbc|wrath|wotlkc|cata|mists|mop|forever|era|retail|standard)$",
        RegexOption.IGNORE_CASE,
    )

    private class Raw(val segments: List<String>, val dir: Boolean, val size: Long)

    /** Name einer .toc ohne Endung und ohne Spielversions-Zusatz (`Questie_Forever.toc` -> `Questie`). */
    internal fun tocBase(fileName: String): String {
        val stem = fileName.substring(0, fileName.length - 4)
        return FLAVOR.matchEntire(stem)?.groupValues?.get(1) ?: stem
    }

    /** Sucht die Addons in [zip]. Wirft [AddonInstallException] bei unsicheren Pfaden, defektem Zip oder wenn kein Addon darin ist. */
    fun scan(zip: File): ZipScan {
        val raws = ArrayList<Raw>()
        try {
            ZipFile(zip).use { z ->
                val list = z.entries()
                while (list.hasMoreElements()) {
                    val e = list.nextElement()
                    if (raws.size >= AddonZip.MAX_ENTRIES) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_too_many_entries))
                    val segments = AddonZip.safeSegments(e.name)
                    if (segments.isEmpty() || segments[0] == "__MACOSX") continue
                    val isDir = e.isDirectory || e.name.endsWith("/") || e.name.endsWith("\\")
                    raws += Raw(segments, isDir, e.size)
                }
            }
        } catch (e: AddonInstallException) {
            throw e
        } catch (e: IOException) {
            throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_corrupt))
        }

        val tocsByDir = raws
            .filter { !it.dir && it.segments.last().endsWith(".toc", ignoreCase = true) && !it.segments.last().startsWith(".") }
            .filter { it.segments.size - 1 <= MAX_DEPTH }
            .groupBy { it.segments.dropLast(1) }

        val accepted = ArrayList<Pair<FoundAddon, Raw>>()
        val dirs = tocsByDir.keys.sortedWith(compareBy<List<String>>({ it.size }, { it.joinToString("/") }))
        for (dir in dirs) {
            val files = tocsByDir.getValue(dir).sortedBy { it.segments.last() }
            val bases = files.map { tocBase(it.segments.last()) }
            val dirName = dir.lastOrNull()
            val nested = accepted.any { (a, _) -> dir.size > a.prefix.size && dir.subList(0, a.prefix.size) == a.prefix }
            if (nested) continue
            val folder = when {
                dirName != null && bases.any { it.equals(dirName, ignoreCase = true) } -> dirName
                dir.size <= 1 -> bases.minWithOrNull(compareBy<String>({ it.length }, { it })) ?: continue
                else -> continue // zwei Ebenen tief und der Ordner heißt anders als die .toc: kein Addon-Ordner
            }
            if (!FOLDER_NAME.matches(folder)) throw AddonInstallException(UiText.res(R.string.launcher_addon_scan_bad_folder, folder))
            val infoFile = files.firstOrNull { it.segments.last().equals("$folder.toc", ignoreCase = true) } ?: files.first()
            accepted += FoundAddon(folder, dir, folder, null, emptyList()) to infoFile
        }
        if (accepted.isEmpty()) {
            throw AddonInstallException(UiText.res(R.string.launcher_addon_scan_no_addon))
        }
        if (accepted.size > MAX_ADDONS) throw AddonInstallException(UiText.res(R.string.launcher_addon_scan_too_many_addons, accepted.size))
        val names = accepted.map { it.first.folder.lowercase() }
        if (names.size != names.toSet().size) throw AddonInstallException(UiText.res(R.string.launcher_addon_scan_duplicate_folder))

        val result = ArrayList<FoundAddon>()
        try {
            ZipFile(zip).use { z ->
                for ((found, tocFile) in accepted) {
                    val wanted = tocFile.segments
                    val entry = z.entries().asSequence().firstOrNull { AddonZip.safeSegments(it.name) == wanted && !it.isDirectory }
                    val text = entry?.let { readLimited(z, it) }.orEmpty()
                    val info = TocParser.parse(text)
                    result += found.copy(title = info.title ?: found.folder, version = info.version, interfaces = info.interfaces)
                }
            }
        } catch (e: IOException) {
            throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_corrupt))
        }
        return ZipScan(result.sortedBy { it.folder.lowercase() }, raws.size, raws.filter { !it.dir && it.size > 0 }.sumOf { it.size })
    }

    private fun readLimited(z: ZipFile, entry: java.util.zip.ZipEntry): String {
        z.getInputStream(entry).use { input ->
            val buffer = ByteArray(MAX_TOC_BYTES)
            var total = 0
            while (total < buffer.size) {
                val n = input.read(buffer, total, buffer.size - total)
                if (n < 0) break
                total += n
            }
            return String(buffer, 0, total, Charsets.UTF_8)
        }
    }

    /** Passt eine der Interface-Nummern zum Client? Null, wenn die .toc keine nennt. */
    fun interfaceMatches(found: FoundAddon, client: IntRange = AddonAssetPicker.FOREVER_INTERFACES): Boolean? =
        if (found.interfaces.isEmpty()) null else found.interfaces.any { it in client }
}

/** Texte der Prüfung als reine Funktionen ([UiText], die Oberfläche löst sie auf). */
object CustomAddonText {
    fun interfaceLine(found: FoundAddon): UiText =
        if (found.interfaces.isEmpty()) {
            UiText.res(R.string.launcher_addon_interface_none)
        } else {
            UiText.res(R.string.launcher_addon_interface_line, found.interfaces.joinToString(", "))
        }

    /** Hinweis, wenn die .toc nicht zum Client passt; trotzdem einbaubar. */
    fun interfaceWarning(found: FoundAddon, client: IntRange = AddonAssetPicker.FOREVER_INTERFACES): UiText? =
        when (CustomAddonScan.interfaceMatches(found, client)) {
            false -> UiText.res(R.string.launcher_addon_interface_warning_other, found.interfaces.joinToString(", "), client.first, client.last)
            null -> UiText.res(R.string.launcher_addon_interface_warning_unknown)
            true -> null
        }
}

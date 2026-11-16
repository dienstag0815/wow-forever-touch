package app.gamenative.lab

import org.json.JSONArray
import org.json.JSONObject

/** Art eines nachladbaren Pakets. */
enum class LabPackageKind(val key: String) {
    WINE("wine"),
    FEX("fex"),
    INSTALLER("installer"),
    ;

    companion object {
        fun fromKey(key: String): LabPackageKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Ein Eintrag aus `assets/bnetlab_pakete.json`. [sha256] leer nur beim Installer (Blizzard tauscht die Datei aus); [bytes] 0 = unbekannt
 * (ebenfalls nur beim Installer). [format]: `wcp` (Paket für den Paket-Installer der App, Wine und FEX) oder `exe`.
 */
data class LabPackage(
    val id: String,
    val kind: LabPackageKind,
    val name: String,
    val version: String,
    val url: String,
    val sha256: String,
    val bytes: Long,
    val license: String,
    val format: String,
    val note: String,
)

enum class LabPackageErrorCode {
    FILE_BROKEN,
    NOT_AN_OBJECT,
    ID_MISSING,
    ID_DUPLICATE,
    KIND_UNKNOWN,
    NAME_MISSING,
    URL_NOT_HTTPS,
    SHA256_INVALID,
    SHA256_MISSING,
    BYTES_INVALID,
    FORMAT_INVALID,
}

/** Ein abgewiesener Eintrag ([index] ab 1, 0 = die ganze Datei). */
data class LabPackageError(val code: LabPackageErrorCode, val index: Int, val id: String = "")

data class LabPackageList(val packages: List<LabPackage>, val errors: List<LabPackageError>)

/** Ergebnis der Prüfung einer geladenen Datei. */
sealed class LabPackageCheck {
    /** Größe und SHA-256 stimmen mit dem Eintrag überein. */
    object Verified : LabPackageCheck()

    /** Der Eintrag hat keine feste Prüfsumme (Installer): Größe und SHA-256 werden nur angezeigt und protokolliert. */
    object Unverified : LabPackageCheck()
    data class SizeMismatch(val expected: Long, val actual: Long) : LabPackageCheck()
    data class HashMismatch(val expected: String, val actual: String) : LabPackageCheck()
    object InsecureUrl : LabPackageCheck()
}

/** Paketliste des Labors: Lesen der Liste und Prüfen geladener Dateien; rein, JUnit-getestet. */
object LabPackages {
    private val SHA256 = Regex("[0-9a-fA-F]{64}")
    private val FORMATS = setOf("wcp", "tzst", "exe")

    /** Nur `https://` mit einem Rechnernamen. */
    fun isHttps(url: String): Boolean {
        val u = url.trim()
        return u.length > 8 && u.startsWith("https://", ignoreCase = true) && !u.substring(8).startsWith("/")
    }

    /**
     * Liest die Liste (Wurzel: Array). Ein kaputter Eintrag entfällt und steht in [LabPackageList.errors]; eine kaputte Datei ergibt eine
     * leere Liste mit [LabPackageErrorCode.FILE_BROKEN].
     */
    fun parse(json: String?): LabPackageList {
        val array = try {
            JSONArray(json ?: return LabPackageList(emptyList(), listOf(LabPackageError(LabPackageErrorCode.FILE_BROKEN, 0))))
        } catch (_: Exception) {
            return LabPackageList(emptyList(), listOf(LabPackageError(LabPackageErrorCode.FILE_BROKEN, 0)))
        }
        val packages = mutableListOf<LabPackage>()
        val errors = mutableListOf<LabPackageError>()
        val seen = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            val n = i + 1
            val o = array.optJSONObject(i)
            if (o == null) {
                errors += LabPackageError(LabPackageErrorCode.NOT_AN_OBJECT, n)
                continue
            }
            val error = entryError(o, n, seen)
            if (error != null) {
                errors += error
                continue
            }
            val id = o.getString("id").trim()
            seen += id
            packages += LabPackage(
                id = id,
                kind = LabPackageKind.fromKey(o.getString("art").trim())!!,
                name = o.getString("name").trim(),
                version = o.optString("version", "").trim(),
                url = o.getString("url").trim(),
                sha256 = o.optString("sha256", "").trim().lowercase(),
                bytes = o.optLong("bytes", 0L),
                license = o.optString("lizenz", "").trim(),
                format = o.optString("format", "wcp").trim().lowercase(),
                note = o.optString("hinweis", "").trim(),
            )
        }
        return LabPackageList(packages, errors)
    }

    private fun entryError(o: JSONObject, n: Int, seen: Set<String>): LabPackageError? {
        val id = o.optString("id", "").trim()
        if (id.isEmpty()) return LabPackageError(LabPackageErrorCode.ID_MISSING, n)
        if (id in seen) return LabPackageError(LabPackageErrorCode.ID_DUPLICATE, n, id)
        val kind = LabPackageKind.fromKey(o.optString("art", "").trim()) ?: return LabPackageError(LabPackageErrorCode.KIND_UNKNOWN, n, id)
        if (o.optString("name", "").isBlank()) return LabPackageError(LabPackageErrorCode.NAME_MISSING, n, id)
        if (!isHttps(o.optString("url", ""))) return LabPackageError(LabPackageErrorCode.URL_NOT_HTTPS, n, id)
        val sha = o.optString("sha256", "").trim()
        val bytes = o.optLong("bytes", 0L)
        val format = o.optString("format", "wcp").trim().lowercase()
        if (format !in FORMATS) return LabPackageError(LabPackageErrorCode.FORMAT_INVALID, n, id)
        val formatOk = when (kind) {
            LabPackageKind.WINE -> format == "wcp"
            LabPackageKind.FEX -> format == "wcp" || format == "tzst"
            LabPackageKind.INSTALLER -> format == "exe"
        }
        if (!formatOk) return LabPackageError(LabPackageErrorCode.FORMAT_INVALID, n, id)
        if (sha.isNotEmpty() && !SHA256.matches(sha)) return LabPackageError(LabPackageErrorCode.SHA256_INVALID, n, id)
        if (kind != LabPackageKind.INSTALLER) {
            if (sha.isEmpty()) return LabPackageError(LabPackageErrorCode.SHA256_MISSING, n, id)
            if (bytes <= 0L) return LabPackageError(LabPackageErrorCode.BYTES_INVALID, n, id)
        } else if (bytes < 0L) {
            return LabPackageError(LabPackageErrorCode.BYTES_INVALID, n, id)
        }
        return null
    }

    /** Prüft eine geladene Datei gegen ihren Eintrag. Ein Eintrag ohne Prüfsumme (Installer) bleibt [LabPackageCheck.Unverified]. */
    fun check(pkg: LabPackage, actualBytes: Long, actualSha256: String): LabPackageCheck {
        if (!isHttps(pkg.url)) return LabPackageCheck.InsecureUrl
        if (pkg.bytes > 0L && actualBytes != pkg.bytes) return LabPackageCheck.SizeMismatch(pkg.bytes, actualBytes)
        if (pkg.sha256.isEmpty()) return LabPackageCheck.Unverified
        if (!pkg.sha256.equals(actualSha256, ignoreCase = true)) return LabPackageCheck.HashMismatch(pkg.sha256, actualSha256.lowercase())
        return LabPackageCheck.Verified
    }


    private val WINE_NAME = Regex("^(wine|proton|Proton)-([0-9.]+)(?:-([0-9.]+))?-(x86|x86_64|arm64ec)$")

    /**
     * Die Wine-Kennung eines installierten Pakets für den Container: `<Versionsname>-<Versionscode>`. `WineInfo.fromIdentifier` schneidet
     * zwei Zeichen ab (`-<Ziffer>`) und verlangt dann `wine|proton-<Version>[-<Build>]-<Architektur>`; passt der Name nicht, ist das
     * Paket im Labor nicht wählbar (null).
     */
    fun wineIdentifier(verName: String, verCode: Int): String? =
        if (verCode in 0..9 && WINE_NAME.matches(verName)) "$verName-$verCode" else null

    /**
     * Der Installer hat keine feste Prüfsumme (Blizzard tauscht die Datei aus): Größe und SHA-256 des Eintrags sind nur der Stand beim
     * Eintragen, eine Abweichung ist ein Hinweis, kein Fehler. Bei Wine- und FEX-Paketen ist sie ein Fehler.
     */
    fun isAdvisory(pkg: LabPackage): Boolean = pkg.kind == LabPackageKind.INSTALLER

    /** Ein FEX-Paket im Format `tzst`: zwei DLLs ohne `profile.json`; die App macht daraus ein eigenes Paket (siehe [fexProfileJson]). */
    fun isBareFex(pkg: LabPackage): Boolean = pkg.kind == LabPackageKind.FEX && pkg.format == "tzst"

    /** Die beiden DLLs eines FEX-Pakets (Ziel: `drive_c/windows/system32` des Prefix). */
    val FEX_FILES = listOf("libarm64ecfex.dll", "libwow64fex.dll")

    /**
     * Versionsname eines FEX-Pakets ohne `profile.json`: `lab-` und die erste Zahl der Versionsangabe (`2508 (Paket-ID); ...` -> `lab-2508`),
     * sonst die Kennung des Eintrags. Das Vorwort hält das Labor-Paket aus den Namen heraus, die das Spiel kennt (`2605`): das Spiel
     * findet so nie ein Labor-Paket.
     */
    fun fexVerName(pkg: LabPackage): String {
        val digits = Regex("[0-9]{3,}").find(pkg.version)?.value
        val base = digits ?: pkg.id.map { if (it.isLetterOrDigit() || it == '-' || it == '.') it else '-' }.joinToString("").take(40)
        return "lab-$base"
    }

    /** `profile.json` für ein FEX-Paket aus zwei DLLs: Typ FEXCore, beide Dateien nach `${system32}` (wie `fexcore-<ver>.tzst` der App). */
    fun fexProfileJson(verName: String, files: List<String> = FEX_FILES): String = JSONObject().apply {
        put("type", "FEXCore")
        put("versionName", verName)
        put("versionCode", 1)
        put("description", "FEX DLLs for the Battle.net lab")
        put("files", JSONArray().apply { files.forEach { put(JSONObject().put("source", it).put("target", "\${system32}/$it")) } })
    }.toString()

    /**
     * Welches installierte Paket zu einem Eintrag der Liste gehört (null = keines, der Eintrag ist nicht installiert). So erkennt die Labor-Seite
     * installierte Pakete: bei FEX am Versionsnamen `lab-<Zahl>` ([fexVerName]), bei Wine am Versionsnamen (ohne `proton-`/`wine-` am Anfang,
     * Groß- und Kleinschreibung egal), der in der Kennung des Eintrags vorkommt; der Installer ist nie "installiert".
     */
    fun installedMatch(pkg: LabPackage, installed: List<LabInstalledPackage>): LabInstalledPackage? = when (pkg.kind) {
        LabPackageKind.FEX -> installed.firstOrNull { it.kind == LabPackageKind.FEX && it.verName == fexVerName(pkg) }
        LabPackageKind.WINE -> installed.firstOrNull { it.kind == LabPackageKind.WINE && wineNameInId(it.verName, pkg.id) }
        LabPackageKind.INSTALLER -> null
    }

    /** Name des Grundes `ERROR_EXIST` im Paket-Installer: der Versionsname liegt schon im Paketordner. */
    const val REASON_EXIST = "ERROR_EXIST"

    fun isAlreadyInstalledReason(reasonName: String?): Boolean = reasonName == REASON_EXIST

    /** Die Zeile fürs Paketprotokoll, wenn der Paket-Installer das Paket nicht aufnahm; `ERROR_EXIST` ist kein Fehler, sondern "schon installiert". */
    fun installFailureLine(id: String, reasonName: String?, wrongType: Boolean): String =
        if (isAlreadyInstalledReason(reasonName)) "install of $id: already installed ($REASON_EXIST), nothing changed"
        else "install of $id failed: ${reasonName ?: if (wrongType) "wrong type" else "unknown"}"

    private fun wineNameInId(verName: String, id: String): Boolean {
        val core = verName.lowercase().removePrefix("proton-").removePrefix("wine-").trim()
        return core.isNotEmpty() && id.lowercase().contains(core)
    }

    /** Dateiname im Download-Ordner: nur Zeichen, die in keinem Pfad Ärger machen. */
    fun fileName(pkg: LabPackage): String {
        val ext = when (pkg.format) { "exe" -> "exe"; "tzst" -> "tzst"; else -> "wcp" }
        val base = pkg.id.map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.') it else '_' }.joinToString("").take(60)
        return "$base.$ext"
    }
}

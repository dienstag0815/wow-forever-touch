package app.gamenative.ui.screen.wow

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.gamenative.PluviaApp
import com.winlator.core.WineRegistryEditor
import com.winlator.xenvironment.ImageFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Web-Links des Spiels ("Account verwalten" im Anmeldebildschirm u. a.) im Android-Browser öffnen.
 *
 * Weg: Das Spiel ruft ShellExecute mit einer http(s)-Adresse. Wine schlägt den Handler in der Registry nach
 * (HKCR\http|https\shell\open\command) und startet sonst `winebrowser`, der keinen Browser mit Netz findet und auf den
 * Wine-internen Browser ohne Internet zurückfällt. [prepare] trägt vor dem Spielstart im Benutzerzweig des Containers
 * (HKCU\Software\Classes, überdeckt den Eintrag von Wine) einen Handler ein, der die Adresse mit dem mitgelieferten
 * `cmd.exe` von Wine an eine Datei im Laufwerk C: anhängt (C:\wowforever\open_url.txt). Kein neues Programm, nichts aus dem Netz.
 * Die App beobachtet die Datei ([startWatcher]), prüft jede Zeile mit [parseUrl] (nur http und https) und öffnet sie mit
 * Intent.ACTION_VIEW im Standardbrowser.
 */
internal object UrlHandoff {
    /** Ordner und Datei im Laufwerk C: des Containers (aus Wine: C:\wowforever\open_url.txt). */
    private const val DIR_NAME = "wowforever"
    private const val FILE_NAME = "open_url.txt"
    private const val POLL_MS = 600L
    private const val WAIT_FOR_START_MS = 180_000L

    /** Dieselbe Adresse wird innerhalb dieser Zeit nur einmal geöffnet (Wine startet den Handler manchmal doppelt). */
    private const val REPEAT_BLOCK_MS = 4_000L
    private const val MAX_PER_READ = 3

    /** Der Befehl, den Wine für http und https ausführt. %1 ersetzt Wine durch die Adresse. */
    internal const val HANDLER_COMMAND = "C:\\windows\\system32\\cmd.exe /c echo \"%1\">>C:\\wowforever\\open_url.txt"

    private val lock = Any()
    private var job: Job? = null
    private val recent = HashMap<String, Long>()

    /** Die Übergabedatei im Wine-Präfix: <Wurzel>/home/xuser/.wine/drive_c/wowforever/open_url.txt. */
    private fun handoffFile(context: Context): File {
        val prefix = File(ImageFs.find(context).rootDir, ImageFs.WINEPREFIX)
        return File(File(prefix, "drive_c/$DIR_NAME"), FILE_NAME)
    }

    /**
     * Vor dem Spielstart (Arbeitsthread): Übergabeordner anlegen, alte Datei löschen, Handler eintragen, Beobachter starten.
     * Fehler brechen den Start nie ab, dann bleibt es beim bisherigen Verhalten.
     */
    fun prepare(context: Context) {
        try {
            val file = handoffFile(context)
            file.parentFile?.mkdirs()
            file.delete()
            val userReg = File(ImageFs.find(context).rootDir, ImageFs.WINEPREFIX + "/user.reg")
            if (userReg.exists()) {
                WineRegistryEditor(userReg).use { registry ->
                    for ((scheme, title) in listOf("http" to "URL:HyperText Transfer Protocol", "https" to "URL:HyperText Transfer Protocol with Privacy")) {
                        registry.setStringValue("Software\\Classes\\$scheme", null, title)
                        registry.setStringValue("Software\\Classes\\$scheme", "URL Protocol", "")
                        registry.setStringValue("Software\\Classes\\$scheme\\shell\\open\\command", null, HANDLER_COMMAND)
                    }
                }
            } else {
                Timber.w("URL-Übergabe: user.reg fehlt, Handler nicht eingetragen")
            }
            startWatcher(context.applicationContext)
        } catch (e: Exception) {
            Timber.w(e, "URL-Übergabe: Vorbereitung fehlgeschlagen")
        }
    }

    private fun startWatcher(appContext: Context) {
        synchronized(lock) {
            job?.cancel()
            job = CoroutineScope(Dispatchers.IO).launch {
                val file = handoffFile(appContext)
                val startedAt = System.currentTimeMillis()
                var seenEnvironment = false
                while (isActive) {
                    delay(POLL_MS)
                    if (PluviaApp.xEnvironment != null) {
                        seenEnvironment = true
                    } else if (seenEnvironment || System.currentTimeMillis() - startedAt > WAIT_FOR_START_MS) {
                        break
                    }
                    try {
                        poll(appContext, file)
                    } catch (e: Exception) {
                        Timber.w(e, "URL-Übergabe: Lesen fehlgeschlagen")
                    }
                }
            }
        }
    }

    private fun poll(appContext: Context, file: File) {
        if (!file.exists() || file.length() == 0L) return
        // Erst umbenennen: Anhänge von Wine während des Lesens landen in einer neuen Datei und gehen nicht verloren.
        val taken = File(file.parentFile, "$FILE_NAME.read")
        val source = if (file.renameTo(taken)) taken else file
        val text = try {
            String(source.readBytes(), Charsets.ISO_8859_1)
        } finally {
            source.delete()
        }
        val now = System.currentTimeMillis()
        recent.entries.removeAll { now - it.value > REPEAT_BLOCK_MS }
        for (url in parseLines(text)) {
            if (recent.containsKey(url)) continue
            recent[url] = now
            open(appContext, url)
        }
    }

    private fun open(appContext: Context, url: String) {
        // Im Log nur der Host, die Adresse selbst kann Kennungen enthalten.
        Timber.i("URL-Übergabe: öffne %s im Browser", hostOf(url))
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w("URL-Übergabe: kein Browser gefunden")
        } catch (e: SecurityException) {
            Timber.w(e, "URL-Übergabe: Intent nicht startbar")
        }
    }

    // --- rein, getestet ---

    /** Höchste Länge einer Adresse. */
    internal const val MAX_URL_LENGTH = 2048

    /**
     * Prüft eine Zeile der Übergabedatei. Gibt die Adresse zurück, wenn es eine sichere http(s)-Adresse ist, sonst null.
     * Erlaubt: Schema http oder https (Groß/Klein egal), ein Host aus Buchstaben, Ziffern, Punkt und Bindestrich mit
     * optionalem Port, danach beliebig Pfad, Abfrage und Fragment ohne Leer- und Steuerzeichen. Abgelehnt: andere Schemata
     * (file, intent, javascript, market ...), Zugangsdaten vor dem Host (`user@host`), Leerzeichen, Anführungszeichen,
     * Klammern und Rückwärtsstriche in der Adresse. Umgebende Anführungszeichen und Leerraum (cmd echo) werden entfernt.
     */
    internal fun parseUrl(raw: String?): String? {
        if (raw == null) return null
        var s = raw.trim()
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1).trim()
        if (s.isEmpty() || s.length > MAX_URL_LENGTH) return null
        for (ch in s) {
            if (ch.code <= 0x20 || ch.code >= 0x7F) return null
            if (ch == '"' || ch == '<' || ch == '>' || ch == '\\' || ch == '`' || ch == '{' || ch == '}' || ch == '|' || ch == '^') return null
        }
        val schemeEnd = s.indexOf("://")
        if (schemeEnd < 0) return null
        val scheme = s.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val rest = s.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        if (authority.isEmpty() || authority.contains('@')) return null
        val hostPort = authority.split(':')
        if (hostPort.size > 2) return null
        val host = hostPort[0]
        if (hostPort.size == 2) {
            val port = hostPort[1]
            if (port.isEmpty() || port.length > 5 || !port.all { it in '0'..'9' } || port.toInt() !in 1..65535) return null
        }
        if (!isValidHost(host)) return null
        return s
    }

    private fun isValidHost(host: String): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        for (label in host.split('.')) {
            if (label.isEmpty() || label.length > 63) return false
            if (label.startsWith("-") || label.endsWith("-")) return false
            if (!label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }) return false
        }
        return true
    }

    /** Alle gültigen, verschiedenen Adressen aus dem Inhalt der Übergabedatei (eine je Zeile), höchstens [MAX_PER_READ]. */
    internal fun parseLines(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (line in text.split('\n', '\r')) {
            val url = parseUrl(line) ?: continue
            out.add(url)
            if (out.size >= MAX_PER_READ) break
        }
        return out.toList()
    }

    /** Der Host einer geprüften Adresse (für das Log). */
    internal fun hostOf(url: String): String {
        val rest = url.substringAfter("://", url)
        val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' || it == ':' }
        return if (end < 0) rest else rest.substring(0, end)
    }
}

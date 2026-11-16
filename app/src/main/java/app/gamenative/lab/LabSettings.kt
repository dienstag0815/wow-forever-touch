package app.gamenative.lab

import org.json.JSONObject

/** Kennungen und feste Werte des Battle.net-Labors (Versuchsaufbau, siehe docs/reports/bnetlabor_2026-11-15.md). */
object LabIds {
    /** Eigene Kennung der zweiten Umgebung; der WoW-Container heißt `wow_forever` und wird vom Labor nie angefasst. */
    const val CONTAINER_ID = "bnetlab"
    const val WOW_CONTAINER_ID = "wow_forever"

    /** Ordner in den App-Daten (`files/bnetlab`): Installer, Protokolle, Downloads. Der Prefix liegt im Container (`imagefs/home/xuser-bnetlab`). */
    const val DIR_NAME = "bnetlab"
    const val DOWNLOAD_DIR = "downloads"
    const val SHARE_DIR = "share"
    const val LOG_DIR = "protokolle"

    /** Laufwerk des Labors: zeigt auf `files/bnetlab/share` (dort liegt der Installer). */
    const val SHARE_DRIVE = "L"

    /**
     * Laufwerk der Spielkopie (nicht `G:`, das ist im Spiel-Container der Spielordner): zeigt auf `files/bnetlab/gamecopy/game` (der Kopie-Bereich,
     * Windows `K:\`). Der Spielinhalt liegt darin im Unterordner [COPY_GAME_DIR] (`K:\World of Warcraft\`): so heißt der Ordner, den der Launcher an den
     * gewählten Installationsort selbst anhängt.
     */
    const val COPY_DRIVE = "K"
    const val COPY_DIR = "gamecopy"
    const val COPY_CONTENT_DIR = "game"
    const val COPY_GAME_DIR = "World of Warcraft"

    /** Marken neben dem Kopie-Bereich (in `gamecopy/`, nie darin, damit der Launcher sie nicht als Spieldatei sieht). */
    const val COPY_INFO_FILE = "copy.json"
    const val COPY_LAYOUT_FILE = "layout.json"
    const val COPY_MOVING_FILE = "moving.json"
    const val ORIGINAL_CHECK_FILE = "original-check.json"
    const val INSTALLER_FILE = "Battle.net-Setup.exe"
    const val INSTALLER_WIN_PATH = "$SHARE_DRIVE:\\$INSTALLER_FILE"

    /** Offizielle Adresse des Battle.net-Installers; Blizzard tauscht die Datei aus, daher ohne feste Prüfsumme. */
    const val INSTALLER_URL = "https://us.battle.net/download/getInstallerForGame?os=win&version=LIVE&gameProgram=BATTLENET_APP"

    /** Vorgabe für die Schalter von Battle.net (Chromium ohne GPU, Netzwerkdienst im Hauptprozess). */
    const val DEFAULT_BNET_SWITCHES = "--disable-gpu --disable-gpu-compositing --enable-features=NetworkServiceInProcess2"

    /** Vorgabe der Fenstergröße: ein Anmeldefenster von 1280×800 passt hinein. */
    const val DEFAULT_SCREEN_SIZE = "1440x900"

    /** Wine-Kennung des gebündelten Pakets, falls der WoW-Container (noch) keine nennt. */
    const val FALLBACK_WINE = "proton-11.0-90624-arm64ec-1"

    /** Stufentest: Batch-Datei und Ergebnisdatei im Laufwerk `L:` (Ordner `files/bnetlab/share`). */
    const val STEP_TEST_BATCH = "labtest.cmd"
    const val STEP_TEST_RESULT = "labtest.txt"
}

/** Übersetzer für Intel-Programme (x86 auf ARM64EC). */
enum class LabTranslator(val key: String) {
    /** Der gebündelte `wowbox64` (nur 32 Bit). */
    BUNDLED("bundled"),

    /** Ein nachgeladenes FEX-Paket. */
    FEX("fex"),

    /** Kein Übersetzer: Wine startet ohne `HODLL`; Intel-Programme laufen dann nicht (nur als Gegenprobe gedacht). */
    NONE("none"),
    ;

    companion object {
        fun fromKey(key: String?): LabTranslator = entries.firstOrNull { it.key == key } ?: BUNDLED
    }
}

/** Wie das Labor das Programm startet. */
enum class LabStartWay(val key: String) {
    /** Wie das Spiel seinen ARM64-Start: `wine explorer /desktop=shell,<Größe> "<Pfad>" <Schalter>`, ohne `winhandler.exe`. */
    DIRECT("direct"),

    /** Über `winhandler.exe` (ein 64-Bit-Intel-Hilfsprogramm; braucht einen Übersetzer für 64 Bit, den die Schnellwahl 1 nicht hat). */
    WINHANDLER("winhandler"),
    ;

    companion object {
        fun fromKey(key: String?): LabStartWay = entries.firstOrNull { it.key == key } ?: DIRECT

        /** Ohne Angabe (kein Plan) gilt der direkte Weg. */
        fun isDirect(way: LabStartWay?): Boolean = way == null || way == DIRECT
    }
}

/** Wie ausführlich Wine meldet (Wert von `WINEDEBUG`). [CUSTOM] nimmt den Text aus `LabSettings.wineDebugCustom`. */
enum class LabWineDebug(val key: String, val value: String) {
    OFF("off", "-all"),
    ERRORS("errors", "fixme-all,err+all"),
    DIAGNOSE("diagnose", "fixme-all,err+all,+process,+loaddll,+timestamp"),
    VERBOSE("verbose", "fixme-all,err+all,+process,+loaddll,+timestamp,+module,+seh"),
    CUSTOM("custom", ""),
    ;

    companion object {
        /** Vorgabe ist die Diagnose; der alte Schlüssel `loaddll` (Stand 9.22) heißt jetzt ebenfalls Diagnose. */
        fun fromKey(key: String?): LabWineDebug = entries.firstOrNull { it.key == key } ?: DIAGNOSE
    }
}

/**
 * Alle Einstellungen des Labors, ohne neuen Bau änderbar. [wine] leer = das gebündelte Wine-Paket, sonst der Eintragsname eines
 * nachgeladenen Pakets (`ContentsManager.getEntryName`). [battleNetLine]: Schalter für Battle.net, oder mit `.exe` am ersten Wort
 * Programm samt Schaltern. [anyProgram]: Pfad im Prefix (mit Laufwerk) und Schalter für "Beliebiges Programm starten".
 */
data class LabSettings(
    val wine: String = "",
    val translator: LabTranslator = LabTranslator.BUNDLED,
    val fexVersion: String = "",
    val battleNetLine: String = LabIds.DEFAULT_BNET_SWITCHES,
    val anyProgram: String = "",
    val extraEnv: String = "",
    val dllOverrides: String = "",
    val wineDebug: LabWineDebug = LabWineDebug.DIAGNOSE,
    /** Eigener Wert für `WINEDEBUG` (nur bei [LabWineDebug.CUSTOM]), z. B. `err+all,+relay`. */
    val wineDebugCustom: String = "",
    val startWay: LabStartWay = LabStartWay.DIRECT,
    val screenSize: String = LabIds.DEFAULT_SCREEN_SIZE,
    /** Gleiche Windows-Kennung wie das Spiel (Vorgabe: an): die gespeicherte Kennung der App wird auch auf den Labor-Prefix angewendet. */
    val sameIdentity: Boolean = true,
    /** Schalter für Wines X11-Treiber im Labor-Prefix (Vorgabe: nichts ändern), siehe [LabX11Preset]. */
    val x11: LabX11Preset = LabX11Preset.DEFAULT,
    /** Stabilitäts-Schnellwahl (Vorgabe: Stufe 1), siehe [LabStabilities]. */
    val stability: LabStability = LabStability.DEFAULT,
    /** Übersetzer-Protokoll: `BOX64_LOG=1` und `BOX64_DYNAREC_MISSING=1` im Labor-Start (Vorgabe: aus). */
    val translatorLog: Boolean = false,
    /** Spielkopie als zusätzliches Laufwerk einbinden (Vorgabe: aus; wirkt nur mit fertiger Kopie, das Original wird nie eingebunden). */
    val useGameCopy: Boolean = false,
    /** Anmelde-Knöpfe „E-Mail“ / „Passwort“ in der Tastenleiste (Vorgabe: aus), siehe [LabSignInKeys]. */
    val signInKeys: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("wine", wine)
        put("translator", translator.key)
        put("fexVersion", fexVersion)
        put("battleNetLine", battleNetLine)
        put("anyProgram", anyProgram)
        put("extraEnv", extraEnv)
        put("dllOverrides", dllOverrides)
        put("v", VERSION)
        put("wineDebug", wineDebug.key)
        put("wineDebugCustom", wineDebugCustom)
        put("startWay", startWay.key)
        put("screenSize", screenSize)
        put("sameIdentity", sameIdentity)
        put("x11", x11.key)
        put("stability", stability.key)
        put("translatorLog", translatorLog)
        put("useGameCopy", useGameCopy)
        put("signInKeys", signInKeys)
    }.toString()

    companion object {
        /** Version des gespeicherten Formats: vor 2 galt `WINEDEBUG=-all` als Vorgabe, ein damals gespeicherter Wert wird nicht übernommen. */
        const val VERSION = 2

        /** Liest gespeicherte Einstellungen; fehlende oder kaputte Angaben werden zur Vorgabe, nie zu einem Fehler. */
        fun fromJson(text: String?): LabSettings {
            val d = LabSettings()
            if (text.isNullOrBlank()) return d
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return d
            return LabSettings(
                wine = o.optString("wine", d.wine),
                translator = LabTranslator.fromKey(o.optString("translator", d.translator.key)),
                fexVersion = o.optString("fexVersion", d.fexVersion),
                battleNetLine = o.optString("battleNetLine", d.battleNetLine),
                anyProgram = o.optString("anyProgram", d.anyProgram),
                extraEnv = o.optString("extraEnv", d.extraEnv),
                dllOverrides = o.optString("dllOverrides", d.dllOverrides),
                wineDebug = if (o.optInt("v", 1) < VERSION) d.wineDebug else LabWineDebug.fromKey(o.optString("wineDebug", d.wineDebug.key)),
                wineDebugCustom = o.optString("wineDebugCustom", d.wineDebugCustom),
                startWay = LabStartWay.fromKey(o.optString("startWay", d.startWay.key)),
                screenSize = o.optString("screenSize", d.screenSize),
                sameIdentity = o.optBoolean("sameIdentity", d.sameIdentity),
                x11 = LabX11Preset.fromKey(o.optString("x11", d.x11.key)),
                stability = LabStability.fromKey(o.optString("stability", d.stability.key)),
                translatorLog = o.optBoolean("translatorLog", d.translatorLog),
                useGameCopy = o.optBoolean("useGameCopy", d.useGameCopy),
                signInKeys = o.optBoolean("signInKeys", d.signInKeys),
            )
        }
    }
}

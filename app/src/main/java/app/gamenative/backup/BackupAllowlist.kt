package app.gamenative.backup

/** Typ eines gesicherten Einstellungswerts (so wie ihn SharedPreferences speichert). */
internal enum class SettingType { BOOL, INT, LONG, FLOAT, STRING }

/**
 * Eine Einstellung, die ins Backup darf: [file] ist die SharedPreferences-Datei ([DATASTORE] steht für den DataStore der App),
 * [key] der Schlüssel darin, [type] der erwartete Typ. Nur was hier steht, wird gesichert und beim Import übernommen.
 */
internal data class SettingSpec(val file: String, val key: String, val type: SettingType)

/**
 * Die Erlaubnisliste des Backups (rein, ohne Android, per JUnit geprüft). Bewusst eine Liste von Hand statt "alles außer": ein neuer
 * Schlüssel mit einem Geheimnis darf nie versehentlich mitgesichert werden.
 *
 * Nie dabei (und per Test abgesichert): Anmeldung (`battle_net_login`), `MachineIdentity`, Spielordner und seine Zugriffsrechte,
 * Update-Feed und Zeitstempel, "schon gefragt"-Merker, Protokolle und Zwischenspeicher, Zustand des laufenden Touch-Setups.
 */
internal object BackupAllowlist {
    /** Kennung für den DataStore der App (kein SharedPreferences-Dateiname). */
    const val DATASTORE = "datastore"

    const val FILE_MAIN = "wow_forever"
    const val FILE_AUTOSTART = "wow_forever_autostart"
    const val FILE_STARTCHECK = "wow_forever_startcheck"
    const val FILE_ART = "wow_forever_launcher_art"
    const val FILE_UPDATE = "wow_forever_update"
    const val FILE_COOLDOWN = "abklingzeit"
    const val FILE_VISIBILITY = "controls_visibility"

    /** Schlüssel der Betriebsart (Standard, Touch, Touch komplett, Mein Layout): sie bestimmt das aktive Profil. */
    const val KEY_INPUT_MODE = "input_mode"

    /** Schlüssel der App-Sprache im DataStore. */
    const val KEY_APP_LANGUAGE = "app_language"

    private fun spec(file: String, key: String, type: SettingType) = SettingSpec(file, key, type)

    val ALL: List<SettingSpec> = listOf(
        // Spiel: Region, Auflösung, Bildrate, Grafik
        spec(FILE_MAIN, "region", SettingType.STRING),
        spec(FILE_MAIN, "resolution", SettingType.STRING),
        spec(FILE_MAIN, "fps_limit", SettingType.STRING),
        spec(FILE_MAIN, "graphics_preset", SettingType.STRING),
        // Steuerung
        spec(FILE_MAIN, KEY_INPUT_MODE, SettingType.STRING),
        spec(FILE_MAIN, "stick_gain_walk", SettingType.FLOAT),
        spec(FILE_MAIN, "stick_gain_camera", SettingType.FLOAT),
        spec(FILE_MAIN, "touch_hide_in_login", SettingType.BOOL),
        spec(FILE_MAIN, "touch_hide_windows", SettingType.STRING),
        spec(FILE_MAIN, "touch_swipe_scroll", SettingType.BOOL),
        spec(FILE_MAIN, "touch_swipe_scroll_speed", SettingType.FLOAT),
        spec(FILE_MAIN, "touch_context_mode", SettingType.STRING),
        spec(FILE_MAIN, "touch_tap_view_items", SettingType.BOOL),
        spec(FILE_MAIN, "touch_two_finger_right_click", SettingType.BOOL),
        spec(FILE_MAIN, "touch_click_wheel", SettingType.BOOL),
        spec(FILE_MAIN, "touch_hold_ms", SettingType.INT),
        spec(FILE_MAIN, "touch_tap_window_ms", SettingType.INT),
        spec(FILE_MAIN, "touch_wheel_center", SettingType.STRING),
        spec(FILE_MAIN, "touch_wheel_up", SettingType.STRING),
        spec(FILE_MAIN, "touch_wheel_right", SettingType.STRING),
        spec(FILE_MAIN, "touch_wheel_down", SettingType.STRING),
        spec(FILE_MAIN, "touch_wheel_left", SettingType.STRING),
        spec(FILE_MAIN, "touch_style", SettingType.STRING),
        spec(FILE_MAIN, "touch_contrast", SettingType.FLOAT),
        // Sprachsteuerung
        spec(FILE_MAIN, "voice_language", SettingType.STRING),
        spec(FILE_MAIN, "voice_words", SettingType.STRING),
        spec(FILE_MAIN, "voice_umlauts", SettingType.STRING),
        // Symbole und Abklingzeiten
        spec(FILE_MAIN, "icons_from_wow", SettingType.BOOL),
        spec(FILE_COOLDOWN, "enabled", SettingType.BOOL),
        spec(FILE_COOLDOWN, "gcd", SettingType.BOOL),
        // Start: Autostart, Countdown, Startcheck, Updates automatisch
        spec(FILE_AUTOSTART, "enabled", SettingType.BOOL),
        spec(FILE_AUTOSTART, "seconds", SettingType.INT),
        spec(FILE_STARTCHECK, "enabled", SettingType.BOOL),
        spec(FILE_UPDATE, "auto_install", SettingType.BOOL),
        // App: Bilder im Launcher, Sprache, Symbole, Sichtbarkeit der Bedienelemente im Spiel
        spec(FILE_ART, "screenshots", SettingType.BOOL),
        spec(FILE_ART, "wow_art", SettingType.BOOL),
        spec(DATASTORE, KEY_APP_LANGUAGE, SettingType.STRING),
        spec(DATASTORE, "wow_emoji_symbols", SettingType.BOOL),
        spec(FILE_VISIBILITY, "visible_wow_forever", SettingType.BOOL),
    )

    private val byId: Map<Pair<String, String>, SettingSpec> = ALL.associateBy { it.file to it.key }

    fun find(file: String, key: String): SettingSpec? = byId[file to key]

    /** Dateien, aus denen gesichert wird (ohne Doppelte, in der Reihenfolge der Liste). */
    val FILES: List<String> = ALL.map { it.file }.distinct()

    /** Muster in Namen, die in einem Backup nie vorkommen dürfen; der Test hält die Liste [ALL] dagegen. */
    val FORBIDDEN_NAME_PARTS: List<String> =
        listOf("password", "passwort", "token", "secret", "email", "login_", "identity", "uri", "base_url", "feed", "game_path", "hash")

    /** Dateien, die nie gesichert werden (Anmeldung, Identität). */
    val FORBIDDEN_FILES: List<String> = listOf("battle_net_login", "wow_identity")

    /** Macht aus einem rohen Wert (so wie ihn JSON oder SharedPreferences liefert) den Wert des Typs von [spec], sonst null. */
    fun coerce(spec: SettingSpec, raw: Any?): Any? = when (spec.type) {
        SettingType.BOOL -> raw as? Boolean
        SettingType.STRING -> (raw as? String)?.takeIf { it.length <= MAX_STRING }
        SettingType.INT -> wholeNumber(raw)?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
        SettingType.LONG -> wholeNumber(raw)
        SettingType.FLOAT -> (raw as? Number)?.toDouble()?.takeIf { it.isFinite() && kotlin.math.abs(it) <= Float.MAX_VALUE }?.toFloat()
    }

    /** Längste Zeichenkette, die ein Wert haben darf (die eigenen Wörter der Sprachsteuerung sind die längste). */
    const val MAX_STRING = 20_000

    private fun wholeNumber(raw: Any?): Long? {
        if (raw !is Number) return null
        if (raw is Double || raw is Float) {
            val d = raw.toDouble()
            if (!d.isFinite() || d != Math.floor(d) || kotlin.math.abs(d) > 9.0e15) return null
            return d.toLong()
        }
        return raw.toLong()
    }
}

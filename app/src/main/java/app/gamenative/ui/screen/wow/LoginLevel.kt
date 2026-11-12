package app.gamenative.ui.screen.wow

import android.content.Context
import androidx.annotation.StringRes
import app.gamenative.R
import java.io.File

/**
 * Die drei Stufen der Anmeldung (Karte "Automatische Anmeldung" unter Spieldateien):
 *  - [OFF]: keine Daten gespeichert, alles von Hand.
 *  - [FILL]: Daten gespeichert, keine login.txt; die App tippt E-Mail und Passwort, der Spieler drückt selbst auf "Login".
 *  - [SELF]: Daten gespeichert, login.txt beim Start, das Spiel meldet sich selbst an (Battle.net kann dabei bei jeder Anmeldung
 *    den Authenticator verlangen).
 */
internal enum class LoginLevel(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    /** Kurzform für die Bereitschaftszeile des Launchers. */
    @StringRes val readyRes: Int,
) {
    OFF("off", R.string.launcher_login_level_off, R.string.launcher_login_level_off_desc, R.string.launcher_ready_login_off),
    FILL("fill", R.string.launcher_login_level_fill, R.string.launcher_login_level_fill_desc, R.string.launcher_ready_login_fill),
    SELF("self", R.string.launcher_login_level_self, R.string.launcher_login_level_self_desc, R.string.launcher_ready_login_self),
    ;

    companion object {
        fun fromKey(key: String?): LoginLevel? = entries.firstOrNull { it.key == key }
    }
}

/** Was die Stufe "Ausfüllen" eintippt: E-Mail und Passwort, oder nur das Passwort (für "Remember Account" im Anmeldebildschirm). */
internal enum class FillScope(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val action: LoginAction,
) {
    EMAIL_AND_PASSWORD("all", R.string.launcher_fill_scope_all, R.string.launcher_fill_scope_all_desc, LoginAction.FILL),
    PASSWORD_ONLY("password", R.string.launcher_fill_scope_password, R.string.launcher_fill_scope_password_desc, LoginAction.PASSWORD_ONLY),
    ;

    companion object {
        fun fromKey(key: String?): FillScope? = entries.firstOrNull { it.key == key }
    }
}

/** Reine Entscheidungen rund um die Stufen (ohne Android, per JUnit getestet). */
internal object LoginLevels {
    /**
     * Die geltende Stufe. Ohne gespeicherte Wahl: wer schon Daten gespeichert hat, behält "Selbst anmelden" (so lief die App bisher,
     * nichts wird still umgestellt); wer nichts gespeichert hat, steht auf "Aus".
     */
    fun resolve(stored: LoginLevel?, hasSavedData: Boolean): LoginLevel = stored ?: if (hasSavedData) LoginLevel.SELF else LoginLevel.OFF

    /** Die Stufe nach dem Speichern neuer Daten: aus "Aus" wird "Ausfüllen" (Standard), eine gewählte Stufe bleibt. */
    fun afterSave(current: LoginLevel): LoginLevel = if (current == LoginLevel.OFF) LoginLevel.FILL else current

    /** Nur "Selbst anmelden" legt beim Spielstart eine login.txt an. */
    fun writesLoginFile(level: LoginLevel): Boolean = level == LoginLevel.SELF

    /** Ob die Anmeldung für die Bereitschaft in Ordnung ist: "Aus" ist kein Mangel, sonst müssen Daten da sein. */
    fun ready(level: LoginLevel, hasSavedData: Boolean): Boolean = level == LoginLevel.OFF || hasSavedData

    /**
     * Der Knopf "Anmeldedaten ausfüllen" im Spielbild: nur in der Stufe "Ausfüllen", mit gespeicherten Daten, solange das Spiel nicht in der
     * Welt steht (dort wären Buchstaben und Tab Tastenbelegungen) und das Seitenmenü zu ist.
     */
    fun fillButtonVisible(level: LoginLevel, hasSavedData: Boolean, inWorld: Boolean, menuOpen: Boolean): Boolean =
        level == LoginLevel.FILL && hasSavedData && !inWorld && !menuOpen

    /** Ob der Lesedienst die Welt-Erkennung braucht, damit der Knopf in der Welt verschwindet. */
    fun needsWorldSignal(level: LoginLevel): Boolean = level == LoginLevel.FILL

    /** Ob vor dem Wechsel gefragt wird: nur "Aus" bei gespeicherten Daten löscht sie. */
    fun needsConfirm(target: LoginLevel, hasSaved: Boolean): Boolean = target == LoginLevel.OFF && hasSaved
}

/** Gespeicherte Wahl (Stufe und Eintippen-Umfang) und die Folgen eines Wechsels. */
internal object LoginLevelStore {
    private const val KEY_LEVEL = "login_level"
    private const val KEY_SCOPE = "login_fill_scope"

    /** Die gespeicherte Wahl; null = nie gewählt. */
    fun stored(context: Context): LoginLevel? = LoginLevel.fromKey(wowPrefs(context).getString(KEY_LEVEL, null))

    /** Die geltende Stufe (siehe [LoginLevels.resolve]). */
    fun current(context: Context): LoginLevel = LoginLevels.resolve(stored(context), BattleNetSignIn.hasSaved(context))

    fun scope(context: Context): FillScope =
        FillScope.fromKey(wowPrefs(context).getString(KEY_SCOPE, null)) ?: FillScope.EMAIL_AND_PASSWORD

    fun setScope(context: Context, scope: FillScope) {
        wowPrefs(context).edit().putString(KEY_SCOPE, scope.key).apply()
    }

    /**
     * Wechsel der Stufe. "Ausfüllen" und "Aus" entfernen eine noch liegende login.txt sofort (der Spielstart schreibt dann keine mehr);
     * "Aus" löscht außerdem die gespeicherten Daten, denn "Aus" heißt: nichts gespeichert.
     */
    fun change(context: Context, gameRoot: File?, level: LoginLevel) {
        wowPrefs(context).edit().putString(KEY_LEVEL, level.key).apply()
        if (!LoginLevels.writesLoginFile(level) && gameRoot != null) BattleNetSignIn.removeLoginFile(gameRoot)
        if (level == LoginLevel.OFF) BattleNetSignIn.forget(context)
        LoginTouchGate.setFillWanted(LoginLevels.needsWorldSignal(level))
    }

    /** Neue Daten speichern; die Stufe richtet sich nach [LoginLevels.afterSave], bewertet vor dem Speichern. */
    fun save(context: Context, login: BattleNetSignIn.Login) {
        val before = current(context)
        BattleNetSignIn.save(context, login)
        recordSaved(context, before)
    }

    /** Die Stufe nach dem Speichern; [before] ist die Stufe vor dem Speichern (danach zählen die neuen Daten schon als gespeichert). */
    fun recordSaved(context: Context, before: LoginLevel) {
        val after = LoginLevels.afterSave(before)
        wowPrefs(context).edit().putString(KEY_LEVEL, after.key).apply()
        LoginTouchGate.setFillWanted(LoginLevels.needsWorldSignal(after))
    }

    /** Gespeicherte Daten vergessen: Stufe "Aus", eine liegende login.txt verschwindet. */
    fun forget(context: Context, gameRoot: File?) {
        change(context, gameRoot, LoginLevel.OFF)
    }
}

/**
 * Vor dem Spielstart: nur "Selbst anmelden" schreibt die login.txt (und startet die Überwachung, die sie wieder löscht); in den
 * anderen Stufen wird eine noch liegende login.txt entfernt. [load] liefert die Daten erst, wenn sie gebraucht werden.
 */
internal fun prepareLoginFile(
    level: LoginLevel,
    gameRoot: File,
    load: () -> BattleNetSignIn.Login?,
    watch: (File) -> Unit = BattleNetSignIn::startLoginWatcher,
) {
    if (!LoginLevels.writesLoginFile(level)) {
        BattleNetSignIn.removeLoginFile(gameRoot)
        return
    }
    load()?.let { login ->
        BattleNetSignIn.writeLoginFile(gameRoot, login)
        watch(gameRoot)
    }
}

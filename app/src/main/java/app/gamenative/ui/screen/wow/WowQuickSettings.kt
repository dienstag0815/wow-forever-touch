package app.gamenative.ui.screen.wow

import app.gamenative.R
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.gamenative.PluviaApp
import app.gamenative.cooldown.CooldownService
import app.gamenative.report.ErrorReport
import app.gamenative.data.TouchGestureConfig
import app.gamenative.ui.component.QuickMenuAction
import app.gamenative.ui.screen.wow.context.ContextMode
import app.gamenative.ui.screen.wow.context.ContextSetting
import app.gamenative.ui.screen.wow.klick.TouchOptionStore
import app.gamenative.ui.screen.wow.klick.TouchOptions
import app.gamenative.ui.screen.wow.klick.WheelDirection
import app.gamenative.ui.screen.wow.voice.KeySink
import app.gamenative.ui.screen.wow.voice.VoiceLanguage
import app.gamenative.ui.screen.wow.voice.VoiceSettings
import app.gamenative.ui.touchstyle.TouchStyle
import app.gamenative.ui.touchstyle.TouchStyleSettings
import com.winlator.container.Container
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Einstellungen, die im Seitenmenü des laufenden Spiels sofort wirken: Stick-Empfindlichkeit (die Steuerelemente lesen den
 * Wert bei jeder Eingabe), Abklingzeiten, Wischen in Listen und die Touch-Gesten (2-Finger-Tipp, Klick-Rad, Haltezeit,
 * Doppeltipp-Fenster: Einstellung und Container-Gesten werden geschrieben und die laufende Berührungsfläche liest neu).
 * Alles andere (Region, Auflösung, Grafik, Steuerungsmodus) wird erst beim Start gelesen und bleibt im Launcher.
 *
 * Die Einstellung ist die Wahrheit: ein hier gesetzter Wert liegt in der Einstellung UND im Container, und der Merge beim
 * nächsten Start ([TouchSetupPolicy]) überschreibt ihn nicht. Andere Orte schreiben dieselben Einstellungen (Touch-Editor:
 * Stick-Empfindlichkeit; Zahnrad: Gesten), darum liest [reload] alles beim Öffnen des Seitenmenüs frisch.
 *
 * Dazu der Abschnitt "Anmeldung" (E-Mail und Passwort ins Spiel tippen, [LoginTypist]) und die Schalter für die
 * Touch-Steuerung im Login ([LoginTouchGate]).
 */
internal class WowQuickSettings(context: Context) {
    private val appContext = context.applicationContext

    var walkGain by mutableStateOf(StickSensitivity.walk(appContext))
        private set
    var cameraGain by mutableStateOf(StickSensitivity.camera(appContext))
        private set
    var cooldowns by mutableStateOf(initCooldowns(appContext))
        private set

    /** Auch die kurze globale Abklingzeit zeigen (nur sinnvoll bei eingeschalteten Abklingzeiten). */
    var globalCooldown by mutableStateOf(CooldownService.showGlobal)
        private set

    /** Touch-Optionen des Launchers, im Spiel änderbar: 2-Finger-Tipp, Klick-Rad, Haltezeit, Doppeltipp-Fenster, Rad-Belegung. */
    var touchOptions by mutableStateOf(TouchOptionStore.load(appContext))
        private set

    /** Wischen in Listen scrollt: Schalter und Geschwindigkeit (wirken sofort, siehe [SwipeScrollSetting]). */
    var swipeScroll by mutableStateOf(SwipeScrollSetting.isEnabled(appContext))
        private set
    var swipeSpeed by mutableStateOf(SwipeScrollSetting.speed(appContext))
        private set

    /** Der Container des laufenden Spiels (für die Container-Gesten); das Seitenmenü setzt ihn bei jedem Zeichnen. */
    var container: Container? = null

    /** Gespeicherte offene Gruppe im Reiter Steuerung: null = noch nie gewählt, leer = bewusst alle zu ([QuickMenuAccordion]). */
    var openGroupSaved by mutableStateOf<String?>(wowPrefs(appContext).getString(KEY_OPEN_GROUP, null))
        private set

    /** Das Klick-Rad hat eine Richtung gewählt, deren Auswahlliste offen ist; null = keine offen. */
    var wheelPicker by mutableStateOf<WheelDirection?>(null)

    /** Die Rückfrage "Layout auf Standard zurücksetzen?" ist offen. */
    var showResetConfirm by mutableStateOf(false)

    /** Ergebnis des letzten Zurücksetzens ("Layout zurückgesetzt" / "... ab dem nächsten Start"); null, solange keins lief. */
    var layoutNote by mutableStateOf<String?>(null)
        private set

    /**
     * Liest alle Werte frisch aus den Einstellungen. Das Seitenmenü ruft das bei jedem Öffnen auf: Der Touch-Editor schreibt
     * die Stick-Empfindlichkeit, das Zahnrad die Gesten, der Launcher die übrigen Werte, ohne dass diese Klasse es merkt.
     */
    fun reload() {
        walkGain = StickSensitivity.walk(appContext)
        cameraGain = StickSensitivity.camera(appContext)
        cooldowns = CooldownService.isEnabled
        globalCooldown = CooldownService.showGlobal
        touchOptions = TouchOptionStore.load(appContext)
        swipeScroll = SwipeScrollSetting.isEnabled(appContext)
        swipeSpeed = SwipeScrollSetting.speed(appContext)
        hideTouchInLogin = LoginTouchGate.hideEnabled(appContext)
        windowHideMode = WindowGate.savedMode(appContext)
        voiceLanguage = VoiceSettings.language(appContext)
        contextMode = ContextSetting.mode(appContext)
        tapViewItems = ContextSetting.tapViewOnly(appContext)
        TouchStyleSettings.init(appContext)
        touchStyle = TouchStyleSettings.style
        touchContrast = TouchStyleSettings.contrast
        hasLogin = BattleNetSignIn.hasSaved(appContext)
        openGroupSaved = wowPrefs(appContext).getString(KEY_OPEN_GROUP, null)
    }

    /** Eine Anmeldung ist gespeichert (der Eintrag zeigt sonst "Keine Anmeldedaten gespeichert"). */
    var hasLogin by mutableStateOf(BattleNetSignIn.hasSaved(appContext))
        private set

    /** Kurze Zeile zur letzten Anmeldeaktion ("Anmeldedaten eingetippt (14:32:05)" oder der Grund, warum nichts getippt wurde). */
    var loginNote by mutableStateOf<String?>(null)
        private set

    /** Kurze Zeile zum Fehlerbericht ("Bericht wird erstellt ..." / "Bericht fertig ..."); null, solange keiner angefordert wurde. */
    var reportNote by mutableStateOf<String?>(null)
        private set

    /** Der Battle.net-Dialog zum Speichern der Anmeldedaten ist offen (zeichnet das Seitenmenü). */
    var showCredentialDialog by mutableStateOf(false)

    var hideTouchInLogin by mutableStateOf(LoginTouchGate.hideEnabled(appContext))
        private set

    /** Touch-Elemente über offenen Spielfenstern: Überdeckte (Standard), Alle außer ESC/Tastatur, Aus. */
    var windowHideMode by mutableStateOf(WindowGate.savedMode(appContext))
        private set

    /** Die zuletzt ausgeführte Zeile soll das Seitenmenü schließen (das Spiel läuft erst danach weiter und kann die Tasten lesen). */
    var closeRequested: Boolean = false
        private set

    /** Erkennungssprache der Sprachnachricht (Standard Englisch, wie die Spielsprache); wirkt bei der nächsten Aufnahme. */
    var voiceLanguage by mutableStateOf(VoiceSettings.language(appContext))
        private set

    /** Meldung der Zeile "Addon-Einstellungen öffnen", wenn die Taste nicht gesendet werden konnte; sonst null. */
    var addonNote by mutableStateOf<String?>(null)
        private set

    /** Alle Werte für Zeilen und Kurzinfos; die Schalter der Modi (Shooter, Gyro, Maus) kennt nur das Seitenmenü. */
    fun values(shooterMode: Boolean, gyro: Boolean, mouseOff: Boolean): QuickMenuValues = QuickMenuValues(
        walkGain = walkGain,
        cameraGain = cameraGain,
        twoFingerRightClick = touchOptions.twoFingerRightClick,
        holdMs = touchOptions.holdMs,
        tapWindowMs = touchOptions.tapWindowMs,
        contextMode = contextMode,
        clickWheel = touchOptions.clickWheel,
        wheelAssignment = touchOptions.wheelAssignment,
        swipeScroll = swipeScroll,
        swipeSpeed = swipeSpeed,
        windowHide = windowHideMode,
        style = touchStyle,
        contrast = touchContrast,
        cooldowns = cooldowns,
        shooterMode = shooterMode,
        gyro = gyro,
        mouseOff = mouseOff,
        hasLogin = hasLogin,
        loginNote = loginNote,
        addonNote = addonNote,
        layoutNote = layoutNote,
        voiceLabel = voiceLanguage.label,
    )

    /** Ob der Schalter der Zeile [id] an ist (nur die Zeilen, die dieser Klasse gehören; sonst false). */
    fun isOn(id: Int): Boolean = when (id) {
        QuickMenuAction.COOLDOWNS -> cooldowns
        QuickMenuAction.COOLDOWN_GCD -> globalCooldown
        QuickMenuAction.TOUCH_HIDE_LOGIN -> hideTouchInLogin
        QuickMenuAction.TOUCH_HIDE_WINDOWS -> windowHideMode != WindowHideMode.OFF
        QuickMenuAction.TOUCH_STYLE -> touchStyle.ordinal != 0
        QuickMenuAction.CONTEXT_MENU -> contextMode != ContextMode.OFF
        QuickMenuAction.TAP_VIEW_ITEMS -> tapViewItems
        QuickMenuAction.TWO_FINGER_TAP -> touchOptions.twoFingerRightClick
        QuickMenuAction.CLICK_WHEEL -> touchOptions.clickWheel
        QuickMenuAction.SWIPE_SCROLL -> swipeScroll
        else -> false
    }

    /**
     * Das Spiel steht im Login oder in der Charakterauswahl (nicht in der Spielwelt): nur dann zeigt das Seitenmenü die Anmeldezeilen.
     * Ohne Ausblenden und Abklingzeiten wird der Streifen nicht gelesen, dann gilt vorsichtshalber "im Login" (die Zeilen bleiben da).
     */
    fun inLogin(): Boolean = !LoginTouchGate.inWorld()

    /** Aussehen der Touch-Elemente (Stil und Kontrast): die Zeilen im Seitenmenü schalten reihum, die Ansicht zeichnet sofort neu. */
    var touchStyle by mutableStateOf(initTouchStyle(appContext))
        private set
    var touchContrast by mutableStateOf(TouchStyleSettings.contrast) // nach initTouchStyle gelesen (Deklarationsreihenfolge)
        private set

    /** Kontextmenü beim Gedrückthalten auf Einheiten und Gegenständen: Aus, Automatisch (Zauberbuch) oder Aktionsleiste 1. */
    var contextMode by mutableStateOf(ContextSetting.mode(appContext))
        private set

    /** Ein Tipp auf einen Gegenstand in Tasche, Bank oder Charakterfenster zeigt ihn nur (Standard an). */
    var tapViewItems by mutableStateOf(ContextSetting.tapViewOnly(appContext))
        private set

    /** Nach "Speichern" im Dialog: die Daten liegen verschlüsselt in der App, das Seitenmenü zeigt die Anmeldeeinträge. */
    fun onCredentialsSaved(login: BattleNetSignIn.Login) {
        BattleNetSignIn.save(appContext, login)
        hasLogin = true
        showCredentialDialog = false
        loginNote = appContext.getString(R.string.game_note_login_saved)
    }

    /** Führt die Zeile mit [id] aus; false, wenn die Zeile nicht hierher gehört. */
    fun handle(id: Int): Boolean {
        closeRequested = false
        when (id) {
            QuickMenuAction.COOLDOWNS -> {
                val next = !cooldowns
                CooldownService.setEnabled(appContext, next)
                cooldowns = next
            }
            QuickMenuAction.COOLDOWN_GCD -> {
                val next = !globalCooldown
                CooldownService.setShowGlobal(appContext, next)
                globalCooldown = next
            }
            QuickMenuAction.TWO_FINGER_TAP -> {
                val next = !touchOptions.twoFingerRightClick
                TouchOptionStore.saveTwoFingerRightClick(appContext, next)
                applyTouchOptions(touchOptions.copy(twoFingerRightClick = next))
            }
            QuickMenuAction.CLICK_WHEEL -> {
                val next = !touchOptions.clickWheel
                TouchOptionStore.saveClickWheel(appContext, next)
                applyTouchOptions(touchOptions.copy(clickWheel = next))
            }
            QuickMenuAction.WHEEL_CENTER, QuickMenuAction.WHEEL_UP, QuickMenuAction.WHEEL_RIGHT,
            QuickMenuAction.WHEEL_DOWN, QuickMenuAction.WHEEL_LEFT,
            -> wheelPicker = wheelDirectionOf(id)
            QuickMenuAction.SWIPE_SCROLL -> {
                val next = !swipeScroll
                SwipeScrollSetting.setEnabled(appContext, next)
                swipeScroll = next
            }
            QuickMenuAction.RESET_LAYOUT -> showResetConfirm = true
            QuickMenuAction.VOICE_LANGUAGE -> {
                val next = VoiceLanguage.entries[(voiceLanguage.ordinal + 1) % VoiceLanguage.entries.size]
                VoiceSettings.saveLanguage(appContext, next)
                voiceLanguage = next
            }
            QuickMenuAction.ADDON_OPTIONS -> openAddonOptions()
            QuickMenuAction.LOGIN_FULL -> startLogin(LoginAction.FULL)
            QuickMenuAction.LOGIN_PASSWORD -> startLogin(LoginAction.PASSWORD_ONLY)
            QuickMenuAction.LOGIN_EMAIL -> startLogin(LoginAction.EMAIL_ONLY)
            QuickMenuAction.LOGIN_NOTE -> Unit
            QuickMenuAction.REPORT -> {
                ErrorReport.start(appContext) { reportNote = it }
                closeRequested = true
            }
            QuickMenuAction.REPORT_NOTE -> Unit
            QuickMenuAction.TOUCH_HIDE_LOGIN -> {
                val next = !hideTouchInLogin
                LoginTouchGate.setHideEnabled(appContext, next)
                hideTouchInLogin = next
                CooldownService.requestRedraw()
            }
            QuickMenuAction.TAP_VIEW_ITEMS -> {
                val next = !tapViewItems
                ContextSetting.setTapViewOnly(appContext, next)
                tapViewItems = next
            }
            QuickMenuAction.TOUCH_SHOW_NOW -> {
                LoginTouchGate.showNow()
                CooldownService.requestRedraw()
                closeRequested = true
            }
            else -> return false
        }
        return true
    }

    /** Die Gruppe [tapped] im Reiter Steuerung auf- oder zuklappen (Akkordeon) und die offene Gruppe merken. */
    fun toggleGroup(sections: List<QuickMenuSection>, tapped: String) {
        val open = QuickMenuAccordion.resolve(openGroupSaved, sections)
        val saved = QuickMenuAccordion.toSaved(QuickMenuAccordion.toggle(open, tapped))
        wowPrefs(appContext).edit().putString(KEY_OPEN_GROUP, saved).apply()
        openGroupSaved = saved
    }

    /** "- Wert +": ändert den Wert der Zeile [id] um [delta] Stufen (-1 oder +1); false, wenn die Zeile keine solche ist. */
    fun adjust(id: Int, delta: Int): Boolean {
        when (id) {
            QuickMenuAction.STICK_WALK -> {
                val next = QuickMenuSteps.gain(walkGain, delta)
                StickSensitivity.saveWalk(appContext, next)
                walkGain = next
            }
            QuickMenuAction.STICK_CAMERA -> {
                val next = QuickMenuSteps.gain(cameraGain, delta)
                StickSensitivity.saveCamera(appContext, next)
                cameraGain = next
            }
            QuickMenuAction.HOLD_TIME -> {
                val next = QuickMenuSteps.holdMs(touchOptions.holdMs, delta)
                TouchOptionStore.saveHoldMs(appContext, next)
                applyTouchOptions(touchOptions.copy(holdMs = next))
            }
            QuickMenuAction.TAP_WINDOW -> {
                val next = QuickMenuSteps.tapWindowMs(touchOptions.tapWindowMs, delta)
                TouchOptionStore.saveTapWindowMs(appContext, next)
                applyTouchOptions(touchOptions.copy(tapWindowMs = next))
            }
            QuickMenuAction.SWIPE_SPEED -> {
                val next = QuickMenuSteps.swipeSpeed(swipeSpeed, delta)
                SwipeScrollSetting.setSpeed(appContext, next)
                swipeSpeed = next
            }
            QuickMenuAction.TOUCH_CONTRAST -> {
                val next = QuickMenuSteps.contrast(touchContrast, delta)
                TouchStyleSettings.setContrast(appContext, next)
                touchContrast = next
            }
            else -> return false
        }
        return true
    }

    /** Auswahlknöpfe: wählt bei der Zeile [id] den Knopf [index]; false, wenn die Zeile keine solche ist oder [index] nicht passt. */
    fun choose(id: Int, index: Int): Boolean {
        when (id) {
            QuickMenuAction.CONTEXT_MENU -> {
                val next = ContextMode.entries.getOrNull(index) ?: return false
                ContextSetting.setMode(appContext, next)
                contextMode = next
            }
            QuickMenuAction.TOUCH_HIDE_WINDOWS -> {
                val next = WindowHideMode.entries.getOrNull(index) ?: return false
                WindowGate.setMode(appContext, next)
                windowHideMode = next
                CooldownService.requestRedraw()
            }
            QuickMenuAction.TOUCH_STYLE -> {
                val next = TouchStyle.entries.getOrNull(index) ?: return false
                TouchStyleSettings.setStyle(appContext, next)
                touchStyle = next
            }
            else -> return false
        }
        return true
    }

    /** Auswahlliste des Klick-Rads: [direction] bekommt die Aktion [actionId]. Wirkt beim nächsten Öffnen des Rads. */
    fun pickWheelAction(direction: WheelDirection, actionId: String) {
        TouchOptionStore.saveWheelAction(appContext, direction, actionId)
        touchOptions = touchOptions.copy(wheelAssignment = touchOptions.wheelAssignment + (direction to actionId))
        wheelPicker = null
    }

    /**
     * Ein Wert der Touch-Gesten wurde gesetzt (die Einstellung hat der Aufrufer schon geschrieben): Zustand merken, Container-Gesten
     * nachziehen, den Vergleichsstand für den nächsten Start mitführen und die laufende Berührungsfläche neu einlesen lassen.
     * Ohne Container-Gesten (Standard-Steuerung) bleibt es bei der Einstellung; sie gilt ab dem nächsten Start im Touch-Modus.
     */
    private fun applyTouchOptions(next: TouchOptions) {
        touchOptions = next
        try {
            val c = container
            val json = TouchSetupStore.applyInGame(appContext, c?.getGestureConfig().orEmpty(), next)
            if (c != null && json != null) {
                c.setGestureConfig(json)
                c.saveData()
                val touchpad = PluviaApp.touchpadView
                if (touchpad != null && touchpad.isTouchscreenMode()) {
                    touchpad.setGestureConfig(TouchGestureConfig.fromJson(json))
                }
            }
        } catch (e: Exception) {
            Timber.w("Touch-Gesten übernehmen fehlgeschlagen: ${e.javaClass.simpleName}")
        }
    }

    /**
     * "Layout auf Standard" nach der Rückfrage: schreibt die Vorlage des gewählten Touch-Modus über das Layout. Ist es das gerade
     * angezeigte Profil, tauscht die Ansicht es sofort aus; sonst (oder wenn die Ansicht nichts zeigt) gilt es ab dem nächsten Start.
     */
    fun confirmResetLayout() {
        showResetConfirm = false
        val mode = InputMode.current(appContext)
        val done = try {
            WowTouchProfiles.reset(appContext, mode)
        } catch (e: Exception) {
            Timber.w("Layout zurücksetzen fehlgeschlagen: ${e.javaClass.simpleName}")
            false
        }
        if (!done) {
            layoutNote = appContext.getString(R.string.game_layout_reset_failed)
            return
        }
        val swapped = try {
            val target = WowTouchProfiles.resetTarget(mode)
            val id = WowTouchProfiles.resolveProfileId(appContext, target)
            val fresh = id?.let { PluviaApp.inputControlsManager?.getProfile(it) }
            val view = PluviaApp.inputControlsView
            val active = view?.getProfile()
            if (fresh != null && view != null && active != null && active.id == fresh.id) {
                view.setProfilePreservingOverlayVisibility(fresh)
                PluviaApp.radialMenuCoordinator?.setProfile(fresh)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.w("Layout austauschen fehlgeschlagen: ${e.javaClass.simpleName}")
            false
        }
        layoutNote = appContext.getString(if (swapped) R.string.game_layout_reset_done else R.string.game_note_layout_reset_next)
    }

    /**
     * Öffnet die Einstellungen des Addons im Spiel: die Taste der Bindung WFB_OPTIONS (Strg und Umschalt mit einer Funktionstaste)
     * geht an das Spiel, sobald das Seitenmenü zu ist und das Spiel weiterläuft. Geht das nicht (keine Taste, Spiel nicht bereit),
     * sagt die Zeile warum und das Menü bleibt offen.
     */
    private fun openAddonOptions() {
        val codes = AddonOptionsKey.keyCodes(AddonOptionsKey.combo())
        if (codes.isEmpty()) {
            addonNote = appContext.getString(R.string.game_note_addon_nokey)
            return
        }
        val server = PluviaApp.xServerView?.getxServer()
        if (server == null) {
            addonNote = appContext.getString(R.string.game_note_addon_notready)
            return
        }
        addonNote = null
        closeRequested = true
        try {
            typing.execute {
                try {
                    Thread.sleep(RESUME_WAIT_MS)
                    AddonOptionsKey.send(
                        codes = codes,
                        press = { server.injectKeyPress(it) },
                        release = { server.injectKeyRelease(it) },
                        pause = { Thread.sleep(it) },
                    )
                } catch (e: Exception) {
                    Timber.w("Addon-Einstellungen öffnen fehlgeschlagen: ${e.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            closeRequested = false
            addonNote = appContext.getString(R.string.game_note_addon_nosend)
        }
    }

    /**
     * Tippt die gespeicherten Anmeldedaten in das Spiel. Erst wird geprüft, ob alles übertragbar ist (sonst geschieht nichts und
     * die Zeile sagt warum), dann schließt das Seitenmenü, das Spiel läuft weiter, und ein Arbeitsthread tippt. Das Passwort
     * existiert nur als CharArray und wird danach überschrieben; weder Zeichen noch Länge gehen ins Log.
     */
    private fun startLogin(action: LoginAction) {
        val secret = BattleNetSignIn.loadSecret(appContext)
        if (secret == null) {
            hasLogin = false
            loginNote = appContext.getString(R.string.game_note_nologin)
            showCredentialDialog = true
            return
        }
        val check = checkLogin(action, secret.email, secret.password)
        if (check != LoginCheck.OK) {
            secret.wipe()
            loginNote = check.messageRes?.let { appContext.getString(it) }
            return
        }
        if (LoginTouchGate.inWorld()) {
            secret.wipe()
            loginNote = appContext.getString(R.string.game_note_inworld)
            return
        }
        val server = PluviaApp.xServerView?.getxServer()
        if (server == null) {
            secret.wipe()
            loginNote = appContext.getString(R.string.game_note_notready)
            return
        }
        closeRequested = true
        loginNote = appContext.getString(R.string.game_note_typing)
        try {
            typing.execute {
                var done = false
                try {
                    Thread.sleep(RESUME_WAIT_MS)
                    done = LoginTypist(sinkOf(server)).run(action, secret.email, secret.password)
                } catch (e: Exception) {
                    Timber.w("Anmeldung eintippen fehlgeschlagen: ${e.javaClass.simpleName}")
                } finally {
                    secret.wipe()
                }
                loginNote = if (done) "${appContext.getString(action.doneRes)} (${clock()})" else appContext.getString(R.string.game_note_typing_cancelled)
            }
        } catch (e: Exception) {
            secret.wipe()
            loginNote = appContext.getString(R.string.game_note_typing_failed)
        }
    }

    private fun sinkOf(server: XServer) = object : KeySink {
        override fun press(key: XKeycode, keysym: Int) = server.injectKeyPress(key, keysym)

        override fun release(key: XKeycode) = server.injectKeyRelease(key)
    }

    private fun clock(): String = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())

    private companion object {
        /** Einstellung: Kennung der zuletzt offenen Gruppe im Reiter Steuerung ([QuickMenuAccordion]). */
        const val KEY_OPEN_GROUP = "quick_menu_open_group"

        /** Zeit, die das Spiel nach dem Schließen des Seitenmenüs braucht, um weiterzulaufen (es war angehalten). */
        const val RESUME_WAIT_MS = 700L

        // Ein Thread, damit nie zwei Anmeldeaktionen gleichzeitig Tasten senden.
        val typing = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "wow-login-typing").apply { isDaemon = true }
        }

        fun initTouchStyle(context: Context): TouchStyle {
            TouchStyleSettings.init(context)
            return TouchStyleSettings.style
        }

        fun initCooldowns(context: Context): Boolean {
            CooldownService.init(context)
            return CooldownService.isEnabled
        }
    }
}

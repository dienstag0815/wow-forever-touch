package app.gamenative.cooldown

import app.gamenative.R
import app.gamenative.ui.components.AppText

import android.content.Context
import android.view.View
import app.gamenative.ui.screen.wow.LoginTouchGate
import app.gamenative.ui.screen.wow.ScrollGate
import app.gamenative.ui.screen.wow.WindowGate
import app.gamenative.ui.screen.wow.chatbar.ChatBarGate
import app.gamenative.ui.screen.wow.context.ContextGate
import app.gamenative.wowicons.AddonSetup
import com.winlator.renderer.GPUImage
import com.winlator.xserver.Drawable
import java.nio.ByteBuffer
import java.util.Arrays
import timber.log.Timber

/**
 * Service of the cooldown display ("Abklingzeiten anzeigen"): reads the strip that the addon TouchForever draws in the
 * top left corner of the game picture (see [CooldownStrip]) and tells the touch buttons, ring segments and wheel fields
 * how long the action bar slot they stand for is on cooldown.
 *
 * Source: the pixmap the game presents (a CPU-mapped hardware buffer, BGRA, see GPUImage); the X server's Present handler
 * calls [onPresent] for every frame. A pass costs two rows of 940 pixels and a few thousand integer operations and runs at most
 * [READ_INTERVAL_NS] apart; it does nothing while the setting is off or no view is registered.
 *
 * Threads: [onPresent] runs on the X server thread. The views register on the main thread and read [stateFor] in onDraw.
 * The decoded cells are published as one immutable array; a view is only redrawn when a slot it asked for changed.
 */
object CooldownService {
    private const val PREFS = "abklingzeit"
    private const val PREF_ENABLED = "enabled"
    private const val PREF_GCD = "gcd"

    /** Minimum time between two reads while the strip is found (about 30 reads per second). */
    private const val READ_INTERVAL_NS = 33_000_000L

    /** Time between two searches while it is not found. */
    private const val SEARCH_INTERVAL_NS = 250_000_000L

    /** Failed reads in a row after which the display is cleared. */
    private const val LOSE_AFTER = 8

    /** The addon changes its counter at least every 0.1 s; without a change for this long the picture is stale. */
    private const val STALE_NS = 2_000_000_000L

    /** The chat state of the last read counts as current for this long (the strip is read about every 33 ms). */
    private const val CHAT_FRESH_NS = 400_000_000L

    /** How long [requestChatProbe] keeps the reading going. */
    private const val CHAT_PROBE_NS = 6_000_000_000L

    private const val RELOAD_TAG = "AddonReload"

    /** Log tag for changes of [statusLine] (one line per change, not per frame). */
    private const val STRIP_TAG = "CooldownStrip"

    private const val OUTCOME_NONE = 0
    private const val OUTCOME_READ = 1
    private const val OUTCOME_MISSED = 2

    private val lock = Any()

    @Volatile private var enabledFlag = true
    @Volatile private var gcdFlag = false
    @Volatile private var initialized = false

    /** Index (0..95) -> cell of a filled slot, null for an empty slot; the array itself is null while there is no valid strip. */
    @Volatile private var cells: Array<CooldownCell?>? = null

    // Slots whose cells were asked for since the last redraw request (benign races; see stateFor).
    private val interest = BooleanArray(CooldownStrip.SLOT_COUNT)

    // Main thread only.
    private val views = ArrayList<View>()

    @Volatile private var viewSnapshot: Array<View> = emptyArray()

    // Present thread only.
    private val rows = Array(CooldownStrip.ROW_COUNT) { IntArray(CooldownStrip.ROW_PIXELS) }
    private val scratch = IntArray(3 + 6 * CooldownStrip.SLOT_COUNT)
    private val work = CooldownStrip.Decoded()
    private val current = CooldownStrip.Decoded()
    private var hasCurrent = false
    private var lockOffset = -1
    private var nextReadNs = 0L
    private var failures = 0
    private var lastCounter = -1
    private var lastCounterChangeNs = 0L
    private var errors = 0

    // Build of the running addon against the bundled one (Present thread): once per game session it is logged and shown in the status
    // text when the game still runs an older addon. No automatic reload any more (decision of 09.11.2026). Log tag "AddonReload".
    @Volatile private var reloadRule = AddonReloadRule()

    // Ergebnis des letzten Durchgangs für die Welt-Erkennung (Present-Thread).
    private var outcome = OUTCOME_NONE

    // Chat edit box according to the last decoded strip (Present thread writes, anyone reads): info flags and read time.
    @Volatile private var chatInfoFlags = 0
    @Volatile private var chatReadNs = 0L
    @Volatile private var chatProbeUntilNs = 0L

    @Volatile private var statusLine = AppText.get(R.string.game_cd_nothing_read)
        set(value) {
            if (value != field) Timber.tag(STRIP_TAG).i("Streifen: %s", value)
            field = value
        }
    @Volatile private var activeCount = 0
    @Volatile private var secretSeen = false

    // ---- settings -----------------------------------------------------------------------------------------------

    private fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            enabledFlag = prefs.getBoolean(PREF_ENABLED, true)
            gcdFlag = prefs.getBoolean(PREF_GCD, false)
            initialized = true
        }
    }

    @JvmStatic
    fun init(context: Context) = ensureInit(context)

    /** "Abklingzeiten anzeigen", on by default. */
    val isEnabled: Boolean get() = enabledFlag

    /** Whether the short global cooldown after a cast is shown as well (off by default: it would flash on every button). */
    val showGlobal: Boolean get() = gcdFlag

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        ensureInit(context)
        enabledFlag = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, enabled).apply()
        if (!enabled) clear(AppText.get(R.string.game_cd_off))
        redraw()
    }

    @JvmStatic
    fun setShowGlobal(context: Context, show: Boolean) {
        ensureInit(context)
        gcdFlag = show
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF_GCD, show).apply()
        redraw()
    }

    // ---- views (main thread) -------------------------------------------------------------------------------------

    /** The view draws cooldowns: it is redrawn when a slot it asked for changes. Call from onAttachedToWindow / when shown. */
    @JvmStatic
    fun register(view: View) {
        ensureInit(view.context)
        if (views.contains(view)) return
        views.add(view)
        viewSnapshot = views.toTypedArray()
    }

    @JvmStatic
    fun unregister(view: View) {
        if (!views.remove(view)) return
        viewSnapshot = views.toTypedArray()
    }

    private fun redraw() {
        for (view in viewSnapshot) view.postInvalidate()
    }

    // ---- drawing side (main thread) ---------------------------------------------------------------------------

    /**
     * The state of the action bar [slot] (1..180) or null: no strip, setting off, slot not carried, or an empty slot. Asking
     * marks the slot as shown, so the service knows to redraw the views when it changes.
     */
    @JvmStatic
    fun stateFor(slot: Int): CooldownCell? {
        if (!enabledFlag) return null
        val index = CooldownStrip.indexOfSlot(slot)
        if (index < 0) return null
        interest[index] = true
        return cells?.get(index)
    }

    // ---- statistics -------------------------------------------------------------------------------------------------

    /** Whether the strip of the addon is found and read right now (the setting is on and the picture decodes). */
    val isStripReadable: Boolean get() = enabledFlag && cells != null

    /** Why the strip is read or not, in words (for the check dialog): "Streifen gelesen", "aus" or the reason it fails. */
    val reason: String get() = if (!enabledFlag) AppText.get(R.string.game_cd_reason_off) else statusLine

    /**
     * Whether a chat edit box of the game has the keyboard focus, as the addon last reported it: true = open, false = closed,
     * null = unknown (strip not read, last read older than [CHAT_FRESH_NS], or an addon that does not report the chat box).
     * Callers that type into the chat must treat null as "do not know", never as "open".
     */
    @JvmStatic
    fun chatBoxOpen(): Boolean? {
        val read = chatReadNs
        if (read == 0L || System.nanoTime() - read > CHAT_FRESH_NS) return null
        return CooldownStrip.chatOpenFrom(chatInfoFlags)
    }

    /**
     * Asks for strip reads for the next few seconds even if no view needs them (cooldown display off, no login rule), so
     * [chatBoxOpen] has an answer while a chat line is typed. Any thread.
     */
    @JvmStatic
    fun requestChatProbe() {
        chatProbeUntilNs = System.nanoTime() + CHAT_PROBE_NS
    }

    /** Number of action bar slots with a running cooldown in the last decoded strip. */
    val coolingCount: Int get() = activeCount

    /** Whether WoW hides cooldown values in combat (secret values); the strip is then incomplete there. */
    val valuesLockedInCombat: Boolean get() = secretSeen

    /** One line for the settings: what the service sees. */
    @JvmStatic
    fun statusText(): String {
        val state = if (!enabledFlag) AppText.get(R.string.game_cd_off) else statusLine
        val extra = if (enabledFlag && cells != null) {
            AppText.get(R.string.game_cd_extra_cooling, activeCount) + (if (secretSeen) AppText.get(R.string.game_cd_extra_locked) else "")
        } else {
            ""
        }
        return AppText.get(R.string.game_cd_status, state, extra, reloadRule.statusText())
    }

    // ---- the X server side (present thread) --------------------------------------------------------------------

    /** Called by the Present extension for every frame of the game; cheap unless a read is due. */
    @JvmStatic
    fun onPresent(pixmap: Drawable, windowWidth: Int, windowHeight: Int, screenWidth: Int, screenHeight: Int) {
        // Der Streifen dient auch dem Ausblenden der Touch-Steuerung im Login (er erscheint erst in der Welt), über offenen
        // Spielfenstern (Abschnitt "Fenster" des Streifens), dem Wischen in Listen (Abschnitt "Scroll") und dem Kontextmenü auf
        // Einheiten (Abschnitt "Kontext"): dafür wird er gelesen, auch wenn die Abklingzeit-Anzeige aus ist.
        val windowWork = WindowGate.needsSignal() || ScrollGate.needsSignal() || ContextGate.needsSignal() || ChatBarGate.needsSignal()
        val loginWork = LoginTouchGate.needsSignal()
        val reloadWork = !reloadRule.finished
        val worldWork = loginWork || windowWork || reloadWork
        val now = System.nanoTime()
        val chatProbe = now < chatProbeUntilNs
        if (!worldWork && !chatProbe && (!enabledFlag || viewSnapshot.isEmpty())) return
        if (now < nextReadNs) return
        nextReadNs = now + (if (lockOffset >= 0) READ_INTERVAL_NS else SEARCH_INTERVAL_NS)
        outcome = OUTCOME_NONE
        try {
            pass(pixmap, windowWidth, windowHeight, screenWidth, screenHeight, now)
        } catch (t: Throwable) {
            errors++
            statusLine = AppText.get(R.string.game_cd_error, t.javaClass.simpleName, t.message.orEmpty())
            if (errors <= 3) Timber.w(t, "Abklingzeit: Durchgang fehlgeschlagen")
            lose()
        }
        if (loginWork && outcome != OUTCOME_NONE) {
            if (LoginTouchGate.onPass(now / 1_000_000L, outcome == OUTCOME_READ)) redraw()
        }
    }

    /** Vom Seitenmenü: die Ansichten neu zeichnen (Touch-Steuerung ein-/ausgeblendet). */
    @JvmStatic
    fun requestRedraw() = redraw()

    /** The CPU-readable memory of the presented pixmap (BGRA), null if there is none. */
    private fun bufferOf(pixmap: Drawable): ByteBuffer? {
        val texture = pixmap.texture
        return if (texture is GPUImage) texture.virtualData else pixmap.data
    }

    /** Row length of [buffer] in pixels (the Drawable of a hardware-buffer pixmap reports it as its width). */
    private fun strideOf(pixmap: Drawable, buffer: ByteBuffer): Int {
        val texture = pixmap.texture
        if (texture is GPUImage) {
            val stride = texture.stride.toInt()
            return if (stride > 0) stride else pixmap.width.toInt()
        }
        val height = pixmap.height.toInt()
        return if (height > 0) buffer.capacity() / (height * 4) else pixmap.width.toInt()
    }

    private fun pass(pixmap: Drawable, windowWidth: Int, windowHeight: Int, screenWidth: Int, screenHeight: Int, now: Long) {
        synchronized(pixmap.renderLock) {
            val buffer = bufferOf(pixmap)
            if (buffer == null) {
                statusLine = AppText.get(R.string.game_cd_no_buffer)
                return
            }
            val stride = strideOf(pixmap, buffer)
            val frameWidth = FrameAccess.visibleWidth(windowWidth, stride)
            val pixmapHeight = pixmap.height.toInt()
            val frameHeight = if (windowHeight > 0) minOf(pixmapHeight, windowHeight) else pixmapHeight
            // Small windows (launcher, tooltips) are not the game picture; they neither show nor lose the strip.
            if (!FrameAccess.isMainFrame(frameWidth, frameHeight, screenWidth, screenHeight)) return
            if (frameWidth < CooldownStrip.ROW_PIXELS || frameHeight < CooldownStrip.ROW_COUNT || frameWidth > stride) {
                statusLine = AppText.get(R.string.game_cd_too_small)
                lose()
                return
            }
            val ints = FrameAccess.intView(buffer)
            // the strip has two pixel rows of cells and, right below, two pixel rows of the context section (second row)
            val lastRow = if (lockOffset >= 0) (lockOffset shr 8) + 2 * CooldownStrip.CELL_H - 1 else CooldownStrip.ROW_COUNT - 1
            if (lastRow.toLong() * stride + CooldownStrip.ROW_PIXELS > ints.limit()) {
                statusLine = AppText.get(R.string.game_cd_short)
                lose()
                return
            }
            val top: IntArray
            val bottom: IntArray
            val contextTop: IntArray
            val contextBottom: IntArray
            val ox: Int
            if (lockOffset >= 0) {
                ox = lockOffset and 255
                val oy = lockOffset shr 8
                CooldownStrip.readRow(ints, stride, oy, rows[0])
                CooldownStrip.readRow(ints, stride, oy + 1, rows[1])
                CooldownStrip.readRow(ints, stride, oy + CooldownStrip.CELL_H, rows[2])
                CooldownStrip.readRow(ints, stride, oy + CooldownStrip.CELL_H + 1, rows[3])
                top = rows[0]
                bottom = rows[1]
                contextTop = rows[2]
                contextBottom = rows[3]
            } else {
                for (y in 0 until CooldownStrip.ROW_COUNT) CooldownStrip.readRow(ints, stride, y, rows[y])
                val found = CooldownStrip.locate(rows)
                if (found < 0) {
                    statusLine = AppText.get(R.string.game_cd_not_found)
                    lose()
                    return
                }
                ox = found and 255
                val oy = found shr 8
                top = rows[oy]
                bottom = rows[oy + 1]
                contextTop = rows[oy + CooldownStrip.CELL_H]
                contextBottom = rows[oy + CooldownStrip.CELL_H + 1]
                lockOffset = found
                lastCounter = -1
            }
            val status = CooldownStrip.decode(top, bottom, ox, work, scratch, contextTop, contextBottom)
            if (status != StripStatus.OK) {
                statusLine = status.text
                if (++failures >= LOSE_AFTER) lose() else if (lockOffset >= 0 && failures >= 3) lockOffset = -1
                return
            }
            failures = 0
            if (work.counter != lastCounter) {
                lastCounter = work.counter
                lastCounterChangeNs = now
            } else if (now - lastCounterChangeNs > STALE_NS) {
                statusLine = AppText.get(R.string.game_cd_no_answer)
                lose()
                return
            }
            statusLine = AppText.get(R.string.game_cd_read)
            outcome = OUTCOME_READ
            chatInfoFlags = work.infoFlags
            chatReadNs = now
            // Offene Spielfenster (Abschnitt "Fenster"): die Rechtecke gelten für das Spielbild an der Ecke des X-Bildschirms.
            if (WindowGate.onStrip(work, frameWidth, frameHeight, screenWidth, screenHeight, now / 1_000_000L)) redraw()
            // Scrollbereiche (Abschnitt "Scroll", Addon-Stand 3): hier beginnt ein Wisch in der Touch-Ansicht als Mausrad.
            ScrollGate.onStrip(work, frameWidth, frameHeight, screenWidth, screenHeight, now / 1_000_000L)
            // Kontext (Abschnitt "Kontext", Addon-Stand 4): die Einheit unter dem Finger für das Menü beim Gedrückthalten.
            ContextGate.onStrip(work, now / 1_000_000L)
            // Chat (Abschnitt "Chat", Addon-Stand 5): wo das Chatfenster liegt und ob das Eingabefeld offen ist, für die Chat-Knöpfe (Sichtbarkeit, Anheften, Sprache).
            ChatBarGate.onStrip(work, frameWidth, frameHeight, screenWidth, screenHeight)
            watchAddon(work, now / 1_000_000L)
            publish()
        }
    }

    // ---- the build of the running addon ---------------------------------------------------------------------------------

    /** A new game session: the question "does the game run the current addon?" is asked again. Main thread. */
    @JvmStatic
    fun newSession() {
        reloadRule = AddonReloadRule()
    }

    /** One line about the addon build for the status text: "Addon: aktuell (Stand 2)" and so on. */
    @JvmStatic
    fun addonStatusText(): String = reloadRule.statusText()

    private fun watchAddon(decoded: CooldownStrip.Decoded, nowMs: Long) {
        val rule = reloadRule
        if (rule.finished) return
        val build = decoded.addonBuild
        val action = rule.onStrip(
            build,
            AddonReloadRule.inCombat(build, decoded.windowFlags, decoded.infoFlags),
            CooldownStrip.chatOpenFrom(decoded.infoFlags),
            AddonSetup.diskCurrent(),
            nowMs,
        )
        if (action == ReloadAction.NONE) {
            if (rule.finished) Timber.tag(RELOAD_TAG).i("addon build %d is current (bundled %d)", build, CooldownStrip.ADDON_BUILD)
            return
        }
        // Only reported, never acted on: the app does not reload the interface by itself (decision of 09.11.2026). The button
        // "Symbole aktualisieren" and /reload stay for the player.
        Timber.tag(RELOAD_TAG).w(
            "running addon build %d, bundled %d, disk current=%s: %s (no automatic reload)",
            build, CooldownStrip.ADDON_BUILD, AddonSetup.diskCurrent(), action,
        )
    }

    /** Publishes [work] if it differs from what the views have; asks the views that show a changed slot to redraw. */
    private fun publish() {
        val old = cells
        if (old != null && hasCurrent && work.sameAs(current)) {
            secretSeen = work.infoFlags and CooldownStrip.I_SECRET != 0
            return
        }
        val next = arrayOfNulls<CooldownCell>(CooldownStrip.SLOT_COUNT)
        var needRedraw = old == null
        var active = 0
        for (i in 0 until CooldownStrip.SLOT_COUNT) {
            val flags = work.flags[i]
            if (flags and CooldownStrip.F_FILLED == 0) {
                if (old != null && old[i] != null && interest[i]) needRedraw = true
                continue
            }
            val unchanged = old != null && hasCurrent && work.remainingCode[i] == current.remainingCode[i] &&
                work.fractionCode[i] == current.fractionCode[i] && flags == current.flags[i]
            val cell = if (unchanged) old!![i] else work.cell(i)
            next[i] = cell
            if (work.remainingCode[i] > 0) active++
            if (!unchanged && interest[i]) needRedraw = true
        }
        current.copyFrom(work)
        hasCurrent = true
        activeCount = active
        secretSeen = work.infoFlags and CooldownStrip.I_SECRET != 0
        cells = next
        if (needRedraw) {
            Arrays.fill(interest, false)
            redraw()
        }
    }

    /** Strip lost (not found, covered, addon stopped): the views show their plain symbols again. */
    private fun lose() {
        outcome = OUTCOME_MISSED
        if (WindowGate.clear()) redraw()
        ScrollGate.clear()
        ContextGate.clear()
        ChatBarGate.clear()
        lockOffset = -1
        failures = 0
        clearCells()
    }

    private fun clear(reason: String) {
        statusLine = reason
        if (WindowGate.clear()) redraw()
        ScrollGate.clear()
        ContextGate.clear()
        ChatBarGate.clear()
        lockOffset = -1
        failures = 0
        clearCells()
    }

    private fun clearCells() {
        val had = cells != null
        cells = null
        hasCurrent = false
        activeCount = 0
        secretSeen = false
        chatReadNs = 0L
        if (had) redraw()
    }
}

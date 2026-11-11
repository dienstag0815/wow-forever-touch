package app.gamenative.ui.screen.wow.context

import android.content.Context
import android.graphics.Bitmap
import app.gamenative.cooldown.CooldownService
import app.gamenative.wowicons.BridgeData
import app.gamenative.wowicons.WowIcons

/** Das offene Menü: Einträge, Anordnung, Stelle des Fingers beim Öffnen und der Eintrag unter dem Finger (-1 = keiner). */
class ContextMenuState(
    var entries: List<ContextEntry>,
    var layout: ContextMenuLayout.Result,
    val anchorX: Float,
    val anchorY: Float,
    val geometry: ContextMenuLayout.Geometry,
    val screenW: Float,
    val screenH: Float,
) {
    var selected: Int = -1

    /** Der Kontext, aus dem [entries] gebaut sind (Vergleich nach Identität: der Lesedienst legt nur bei Änderung einen neuen an). */
    var source: ContextData? = null

    /** Monotone Zeit (ms) des Öffnens. */
    var openedMs: Long = 0L

    /** Menü eines Gegenstands (Tasche, Bank, Charakterfenster): beim Öffnen wird nicht geklickt (ein Linksklick nähme ihn auf). */
    var itemMenu: Boolean = false

    /** Selbst-Menü (Halten auf der eigenen Figur): beim Öffnen wird nicht geklickt, die Einträge sind Fenster des Spiels. */
    var selfMenu: Boolean = false

    /** Ob beim Öffnen die Einheit per Linksklick als Ziel gewählt wird (nicht bei Gegenständen und beim Selbst-Menü). */
    val selectsTarget: Boolean get() = !itemMenu && !selfMenu

    /** Kontext des Öffnens: ob ein Gegenstand am Cursor hing, entscheidet die Schritte beim Loslassen. */
    var cursorHasItem: Boolean = false

    /** Ob das Menü gezeichnet wird und Loslassen etwas auslöst (siehe [ContextMenuController.revealed]). */
    var revealed: Boolean = false

    /** Mit dem Ziel-Klick beim Öffnen: das Menü wartet, bis das Ziel in den Zahlen des Addons steht. */
    var waitsForTarget: Boolean = false
}

/**
 * Verbindet die Touch-Ansicht mit dem Kontext: entscheidet beim Ablauf der Haltezeit, ob ein Menü kommt ([wantsMenu]), baut es
 * ([open]), verfolgt den Finger ([move]) und liefert beim Loslassen den Eintrag ([commit]). Eine Instanz je Touch-Ansicht, Hauptthread.
 * Die Auswahl der Einträge steht in [ContextMenus], die Anordnung in [ContextMenuLayout]; hier ist nur der Leim zu Android.
 */
class ContextMenuController(context: Context) {
    private val appContext = context.applicationContext

    /** Das offene Menü; null, solange keins offen ist. Die Ansicht zeichnet es. */
    var menu: ContextMenuState? = null
        private set

    private var pending: ContextData? = null
    private var lastCursorHadItem = false
    private var namesFor: BridgeData? = null
    private var names: Map<Int, String> = emptyMap()

    /**
     * Ob beim Ablauf der Haltezeit statt der linken Maustaste das Menü kommt. [downWallMs]: Aufsetzzeit der Berührung (Uhr der
     * Touch-Ansicht, `System.currentTimeMillis()`); der Streifenwert trägt die Uhr des Lesedienstes (`System.nanoTime()`).
     */
    fun wantsMenu(downWallMs: Long): Boolean {
        pending = null
        val snap = ContextGate.snapshot() ?: return false
        val sinceDownMs = System.currentTimeMillis() - downWallMs
        val nowMs = System.nanoTime() / 1_000_000L
        val ok = ContextDecision.opensMenu(ContextGate.mode(), snap.data, snap.receivedMs, nowMs - sinceDownMs, nowMs)
        if (!ok) return false
        val entries = buildEntries(snap.data)
        if (entries.isEmpty()) return false
        pending = snap.data
        return true
    }

    /**
     * Ob der Tipp, der bei [downWallMs] aufgesetzt hat, nur ansehen soll (Gegenstand in Tasche, Bank, Charakterfenster; siehe
     * [ContextDecision.tapIsViewOnly]). Die Berührung zeigt dann den Tooltip (der Zeiger liegt schon dort), ein Klick bleibt aus.
     */
    fun tapIsViewOnly(downWallMs: Long): Boolean {
        if (!ContextGate.tapViewOnly()) return false
        val snap = ContextGate.snapshot() ?: return false
        val sinceDownMs = System.currentTimeMillis() - downWallMs
        val nowMs = System.nanoTime() / 1_000_000L
        return ContextDecision.tapIsViewOnly(true, snap.data, snap.receivedMs, nowMs - sinceDownMs, nowMs)
    }

    private fun buildEntries(data: ContextData): List<ContextEntry> =
        ContextMenus.build(data, ContextGate.mode(), this::barState, this::spellName)

    /**
     * Öffnet das Menü um die Fingerstelle ([x], [y]) in einer Ansicht der Größe [screenW] x [screenH]: das Radialmenü der App
     * ([ContextOpen.RADIAL]); bei einem anderen Spieler das Mini-Menü (sein Eintrag "Mehr ..." drückt [ContextKeys.POPUP], dann geht das
     * Spielermenü des Spiels auf), bei der eigenen Figur das Selbst-Menü.
     */
    fun open(x: Float, y: Float, screenW: Float, screenH: Float, density: Float): ContextOpen {
        val data = pending ?: ContextGate.snapshot()?.data ?: return ContextOpen.NONE
        pending = null
        val entries = buildEntries(data)
        if (entries.isEmpty()) return ContextOpen.NONE
        val geometry = ContextMenuLayout.Geometry.forDensity(density)
        val layout = layoutFor(entries, data.focus.hasMenu, x, y, screenW, screenH, geometry)
        val state = ContextMenuState(entries, layout, x, y, geometry, screenW, screenH)
        state.source = data
        state.openedMs = System.nanoTime() / 1_000_000L
        state.cursorHasItem = data.focus.cursorHasItem
        state.itemMenu = data.focus.hasMenu
        state.selfMenu = ContextMenus.usesSelfMenu(data, ContextGate.mode())
        // Ohne Ziel-Klick (Gegenstand, eigene Figur) oder wenn die Einheit schon das Ziel ist, gibt es nichts abzuwarten.
        state.waitsForTarget = state.selectsTarget && !data.unit.isTarget
        state.revealed = !state.waitsForTarget
        menu = state
        return ContextOpen.RADIAL
    }

    /**
     * Anordnung der Segmente für [entries] um den Druckpunkt (Ringe nach [ContextEntry.ring]); Einheiten-Menüs halten die Zielfenster und
     * den Tooltip frei, Gegenstands-Menüs nicht.
     */
    private fun layoutFor(
        entries: List<ContextEntry>,
        item: Boolean,
        x: Float,
        y: Float,
        screenW: Float,
        screenH: Float,
        geometry: ContextMenuLayout.Geometry,
    ): ContextMenuLayout.Result {
        val keepClear = if (item) emptyList() else ContextMenuLayout.defaultKeepClear(screenW, screenH)
        return ContextMenuLayout.compute(
            entries.size, x, y, screenW, screenH, geometry, keepClear, ContextMenuLayout.ARC_DEG, ContextMenus.ringCounts(entries),
        )
    }

    /**
     * Ob das Menü sichtbar ist. Nach dem Ziel-Klick beim Öffnen stimmen Zauberliste und Reichweiten erst ein, zwei Streifenbilder später;
     * damit nichts erst erscheint und wieder springt, wartet das Menü, bis die Einheit das Ziel ist (höchstens [REVEAL_MAX_MS]).
     * Vorher löst Loslassen nichts aus.
     */
    fun revealed(): Boolean {
        val m = menu ?: return false
        if (m.revealed) return true
        val nowMs = System.nanoTime() / 1_000_000L
        if (m.source?.unit?.isTarget == true || nowMs - m.openedMs >= REVEAL_MAX_MS) m.revealed = true
        return m.revealed
    }

    /**
     * Gleicht das offene Menü mit dem neuesten Kontext ab (Abdunkeln, Abklingzeit, Reichweite). Das Ziel wird erst beim Öffnen per
     * Klick gewählt, die Reichweiten der Zauber gelten also erst ein, zwei Streifenbilder später: innerhalb von [SETTLE_MS] nach dem
     * Öffnen darf sich auch die Reihenfolge ändern (Sortierung nach Reichweite), danach bleibt sie stehen, damit unter dem Finger
     * nichts springt. Gibt true zurück, wenn neu gezeichnet werden muss.
     */
    fun refresh(): Boolean {
        val m = menu ?: return false
        val data = ContextGate.snapshot()?.data ?: return false
        if (data === m.source) return false
        val fresh = buildEntries(data)
        if (fresh.isEmpty()) return false
        m.source = data
        val nowMs = System.nanoTime() / 1_000_000L
        val entries = if (nowMs - m.openedMs <= SETTLE_MS) fresh else keepOrder(fresh, m.entries)
        if (entries.size != m.entries.size || ContextMenus.ringCounts(entries) != ContextMenus.ringCounts(m.entries)) {
            m.layout = layoutFor(entries, m.itemMenu, m.anchorX, m.anchorY, m.screenW, m.screenH, m.geometry)
            m.selected = -1
        }
        m.entries = entries
        return true
    }

    /** [fresh] in der Reihenfolge von [old] (nach Kennung); Neue hinten, Verschwundene fallen weg. */
    private fun keepOrder(fresh: List<ContextEntry>, old: List<ContextEntry>): List<ContextEntry> {
        val byId = fresh.associateBy { it.id }
        val out = ArrayList<ContextEntry>(fresh.size)
        for (entry in old) byId[entry.id]?.let { out.add(it) }
        for (entry in fresh) if (out.none { it.id == entry.id }) out.add(entry)
        return out
    }

    /** Der Finger ist bei ([x], [y]). Gibt true zurück, wenn sich der Eintrag unter dem Finger geändert hat (neu zeichnen). */
    fun move(x: Float, y: Float): Boolean {
        val m = menu ?: return false
        val index = if (m.revealed || revealed()) ContextMenuLayout.indexAt(m.layout, x, y, m.geometry) else -1
        if (index == m.selected) return false
        m.selected = index
        return true
    }

    /** Der Finger wurde bei ([x], [y]) losgelassen: der Eintrag darunter, wenn er möglich ist; sonst null (nichts tun). Schließt das Menü. */
    fun commit(x: Float, y: Float): ContextEntry? {
        val m = menu ?: return null
        menu = null
        // Der Stand zuletzt gelesen (nicht der beim Öffnen): hängt jetzt ein Gegenstand am Cursor?
        lastCursorHadItem = ContextGate.snapshot()?.data?.focus?.cursorHasItem ?: m.cursorHasItem
        // Vor dem Sichtbarwerden (Haltezeit gerade abgelaufen, Finger sofort gehoben) löst nichts aus.
        if (!m.revealed) return null
        val index = ContextMenuLayout.indexAt(m.layout, x, y, m.geometry)
        val entry = m.entries.getOrNull(index) ?: return null
        return if (entry.enabled) entry else null
    }

    /** Menü zu, ohne etwas auszulösen. */
    fun close() {
        menu = null
        pending = null
    }

    /** Ob beim Öffnen ein Gegenstand am Cursor hing (die Schritte beim Loslassen räumen ihn vorher weg). */
    fun cursorHadItem(): Boolean = lastCursorHadItem

    /**
     * Symbol des Spiels für die Kachel, zur Laufzeit aus dem Spiel geholt (nichts davon steckt in der App): Plätze der Aktionsleiste über
     * ihre Leiste, Zauber der Liste über ihre Zauber-Nummer. Feste Einträge haben ein gezeichnetes Symbol ([ContextEntry.glyph]); fehlt
     * jedes Bild, null: die Kachel zeigt den Namen.
     */
    fun iconFor(entry: ContextEntry): Bitmap? = when {
        entry.action == EntryAction.BAR_SLOT -> WowIcons.bitmapFor("ACTIONBUTTON${entry.barSlot}")
        entry.wowAction != null -> WowIcons.bitmapFor(entry.wowAction)
        entry.spellId > 0 -> WowIcons.bitmapForSpell(entry.spellId)
        else -> null
    }

    /** Symbol des Spiels für einen festen Eintrag (Angriff, Plündern, Kürschnern, Verkaufen), sonst null: dann zeichnet die Kachel ihr eigenes. */
    fun glyphIconFor(glyph: ContextGlyph): Bitmap? = WowIcons.bitmapForGlyph(glyph.name)

    private fun barState(slot: Int): BarSlotState? {
        val cell = CooldownService.stateFor(slot)
        val name = WowIcons.nameFor("ACTIONBUTTON$slot")
        if (cell == null && name == null) return null
        return BarSlotState(
            filled = true,
            name = name,
            notUsable = cell?.noResource ?: false,
            outOfRange = cell?.outOfRange ?: false,
            cooldown = cell?.remaining ?: 0f,
        )
    }

    private fun spellName(id: Int): String? {
        val data = WowIcons.bridgeData() ?: return null
        if (data !== namesFor) {
            val map = HashMap<Int, String>()
            for (spell in data.spells) {
                val sid = spell.spellId ?: continue
                val name = spell.name ?: continue
                map[sid.toInt()] = name
            }
            names = map
            namesFor = data
        }
        return names[id]
    }

    companion object {
        /** So lange nach dem Öffnen darf das Menü seine Einträge neu ordnen. */
        const val SETTLE_MS = 450L

        /** So lange wartet das Menü nach dem Öffnen höchstens auf das Ziel, bevor es sich zeigt. */
        const val REVEAL_MAX_MS = 350L
    }
}

package app.gamenative.lab

import app.gamenative.report.ReportRedactor
import com.winlator.xserver.Atom
import com.winlator.xserver.Window
import com.winlator.xserver.WindowManager
import com.winlator.xserver.XServer
import com.winlator.xserver.events.Event
import com.winlator.xserver.Property
import timber.log.Timber

/** Was "nach vorn holen" getan hat (die Seite übersetzt es in einen Text). */
data class LabRaiseResult(
    /** `false`: das Fenster gibt es nicht mehr oder ist nicht sichtbar. */
    val found: Boolean,
    /** Ein `FocusIn` ging an das Fenster (es hat `FocusChange` gewählt). */
    val focusEventSent: Boolean = false,
    /** Zusätzlich ging die Bitte über `winhandler.exe` (nur wenn der Versuch über diesen Weg gestartet wurde). */
    val viaWinHandler: Boolean = false,
)

/**
 * Das Fensterbild des Labors, am X-Server der App: Schnappschuss der Fenster der obersten Ebene für die Fensterliste der Tastenleiste,
 * Zeitverlauf der geöffneten und geschlossenen Fenster für den Abschnitt "Windows" des Protokolls, "nach vorn holen" und "schließen".
 * Ohne [begin] und ohne [attach] geschieht nichts; das Spiel wird nie berührt (Aufrufer sind nur `LabRun` und die Tastenleiste des Labors).
 * Wie das Ganze am X-Server hängt: `docs/reports/labfenster_2026-11-16.md`.
 */
object LabWindows {
    @Volatile private var timeline: LabWindowTimeline? = null
    @Volatile private var attached: Pair<XServer, WindowManager.OnWindowModificationListener>? = null

    /** Der letzte bekannte Schnappschuss (bei jedem Öffnen, Schließen und Umordnen erneuert): für das Protokoll, wenn der X-Server am Ende schon leer ist. */
    @Volatile private var last: Pair<Long, LabWinSnapshot>? = null

    /** Neuer Versuch (aus `LabRun.begin`). */
    fun begin(startedMs: Long) {
        timeline = LabWindowTimeline(startedMs)
        last = null
    }

    /** Die Fensterereignisse des laufenden Versuchs (für die Zusammenfassung am Ende, [LabRunWindows.summarize]); leer ohne Versuch. */
    fun events(): List<LabWinEvent> = timeline?.snapshot()?.first.orEmpty()

    /** Abschnitt "Windows" für das Protokoll; leer ohne Versuch. Holt die Liste am Ende frisch, sonst die letzte bekannte. */
    fun lines(): List<String> {
        val t = timeline ?: return emptyList()
        val (events, dropped) = t.snapshot()
        val server = attached?.first
        val fresh = server?.let { runCatching { snapshot(it) }.getOrNull() }
        val remembered = last
        val useLast = (fresh == null || fresh.count == 0) && remembered != null && remembered.second.count > 0
        val atEnd = if (useLast) remembered?.second else fresh
        val body = LabWindowsReport.lines(events, dropped, t.startedMs(), atEnd, ReportRedactor())
        return if (useLast) {
            body + "(the list above is the last one seen at +${"%.1f".format(java.util.Locale.ROOT, ((remembered?.first ?: 0L) - t.startedMs()) / 1000.0)}s; the X server had no open window at the end)"
        } else {
            body
        }
    }

    /** Ende des Versuchs (aus `LabRun`, nachdem [lines] geschrieben wurde). */
    fun end() {
        detach()
        timeline = null
        last = null
    }

    /** Hängt sich an [server] (einmal je Server); hält die schon offenen Fenster als "seen" fest. */
    fun attach(server: XServer) {
        if (attached?.first === server) return
        detach()
        val listener = object : WindowManager.OnWindowModificationListener {
            override fun onMapWindow(window: Window) = consider(server, window, alreadyOpen = false)

            override fun onUnmapWindow(window: Window) = closed(server, window)

            override fun onChangeWindowZOrder(window: Window) = remember(server)

            override fun onUpdateWindowGeometry(window: Window, resized: Boolean) {
                // Ein Fenster, das klein erschien und später größer wird, gehört ab da in den Zeitverlauf.
                if (resized && timeline?.isOpen(window.id) == false) consider(server, window, alreadyOpen = false)
            }

            override fun onModifyWindowProperty(window: Window, property: Property) {
                val t = timeline ?: return
                if (!isTitleOrClass(property.name)) return
                if (t.isOpen(window.id)) t.onChange(window.id, window.className.orEmpty(), titleOf(window), window.width.toInt(), window.height.toInt())
                else consider(server, window, alreadyOpen = false)
            }
        }
        server.windowManager.addOnWindowModificationListener(listener)
        attached = server to listener
        if (timeline != null) runCatching { snapshot(server) }.getOrNull()?.let { s ->
            s.entries.forEach { timeline?.onOpen(it.id, it.className, it.title, it.width, it.height, alreadyOpen = true) }
            last = System.currentTimeMillis() to s
        }
    }

    fun detach() {
        attached?.let { (server, listener) -> runCatching { server.windowManager.removeOnWindowModificationListener(listener) } }
        attached = null
    }

    private fun isTitleOrClass(atom: Int): Boolean = atom == Atom.getId("WM_NAME") || atom == Atom.getId("WM_CLASS") || (atom > 0 && atom == Atom.getId("_NET_WM_NAME"))

    private fun remember(server: XServer) {
        if (timeline == null) return
        runCatching { snapshot(server) }.getOrNull()?.let { last = System.currentTimeMillis() to it }
    }

    private fun consider(server: XServer, window: Window, alreadyOpen: Boolean) {
        val t = timeline ?: return
        val changed = runCatching {
            val wm = server.windowManager
            val node = nodeOf(window, window.parent?.id ?: -1)
            if (window.attributes.isMapped && LabWindowList.qualifiesForTimeline(node, wm.rootWindow.id, desktopWindow(wm)?.id) && !t.isOpen(node.id)) {
                t.onOpen(node.id, node.className, node.title, node.width, node.height, alreadyOpen)
                true
            } else {
                false
            }
        }.onFailure { Timber.w("Lab window event ignored: %s", it.javaClass.simpleName) }.getOrDefault(false)
        if (changed) remember(server)
    }

    private fun closed(server: XServer, window: Window) {
        val t = timeline ?: return
        if (t.isOpen(window.id)) t.onClose(window.id)
        remember(server)
    }

    /** Das Desktop-Fenster (siehe [LabWindowList.desktopId]); null, wenn es keines gibt. */
    private fun desktopWindow(wm: WindowManager): Window? =
        wm.rootWindow.children.firstOrNull { !it.attributes.isOverrideRedirect && it.className == LabWindowList.DESKTOP_CLASS }

    /** Der Titel: `_NET_WM_NAME` (UTF-8, wie Wine ihn setzt), sonst `WM_NAME`; leer, wenn keiner gesetzt ist. */
    fun titleOf(window: Window): String {
        val net = Atom.getId("_NET_WM_NAME")
        if (net > 0) {
            val text = window.getProperty(net)?.toString().orEmpty()
            if (text.isNotEmpty()) return text
        }
        return window.name.orEmpty()
    }

    /** Das Fenster nennt `WM_DELETE_WINDOW` in `WM_PROTOCOLS`. */
    private fun closableOf(window: Window): Boolean {
        val protocols = Atom.getId("WM_PROTOCOLS")
        val delete = Atom.getId("WM_DELETE_WINDOW")
        if (protocols <= 0 || delete <= 0) return false
        val p = window.getProperty(protocols) ?: return false
        if (p.format != Property.Format.INT_ARRAY) return false
        val data = p.data
        return LabWindowList.containsAtom((0 until data.capacity() / 4).map { data.getInt(it * 4) }, delete)
    }

    private fun nodeOf(w: Window, parentId: Int): LabWinNode = LabWinNode(
        id = w.id,
        parentId = parentId,
        className = w.className.orEmpty(),
        title = titleOf(w),
        x = w.rootX.toInt(),
        y = w.rootY.toInt(),
        width = w.width.toInt(),
        height = w.height.toInt(),
        mapped = w.attributes.isMapped,
        overrideRedirect = w.attributes.isOverrideRedirect,
        inputOutput = w.isInputOutput,
        closable = closableOf(w),
    )

    /** Schnappschuss der Fenster der obersten Ebene (nur lesen, kurz unter der Sperre des Fensterbaums). */
    fun snapshot(server: XServer): LabWinSnapshot = server.lock(XServer.Lockable.WINDOW_MANAGER).use {
        val wm = server.windowManager
        val root = wm.rootWindow
        val nodes = ArrayList<LabWinNode>()
        for (child in root.children) nodes += nodeOf(child, root.id)
        desktopWindow(wm)?.let { desktop -> for (child in desktop.children) nodes += nodeOf(child, desktop.id) }
        LabWindowList.build(nodes, root.id, wm.focusedWindow?.id)
    }

    /**
     * Holt das Fenster [id] nach vorn und gibt ihm den Fokus. Der kleinste Weg, den der X-Server der App hergibt:
     * 1. Stapelfolge: das Fenster wird ans Ende der Kinder seines Elternfensters gesetzt (`reparentWindow` auf dasselbe Elternfenster; das
     *    löst die Neuzeichnung aus, schickt dem Programm aber kein Ereignis),
     * 2. Fokus des X-Servers (`setFocus`, wie `DesktopHelper`): Tasten gehen von da an an dieses Fenster,
     * 3. `FocusIn` an das Fenster, wenn es `FocusChange` gewählt hat (Wine aktiviert daraufhin das Fenster),
     * 4. nur bei einem Versuch über `winhandler.exe`: dieselbe Bitte wie im Spiel (`WinHandler.bringToFront`).
     */
    fun bringToFront(server: XServer, id: Int, viaWinHandler: Boolean): LabRaiseResult {
        var sent = false
        var className = ""
        var handle = 0L
        val found = server.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.INPUT_DEVICE).use {
            val wm = server.windowManager
            val window = wm.getWindow(id)
            val parent = window?.parent
            if (window == null || parent == null || !window.attributes.isMapped || window === wm.rootWindow) return@use false
            wm.reparentWindow(window, parent, window.x, window.y)
            wm.setFocus(window, if (parent === wm.rootWindow) WindowManager.FocusRevertTo.POINTER_ROOT else WindowManager.FocusRevertTo.PARENT)
            if (window.hasEventListenerFor(Event.FOCUS_CHANGE)) {
                window.sendEvent(Event.FOCUS_CHANGE, LabFocusInEvent(window.id))
                sent = true
            }
            className = window.className.orEmpty()
            handle = window.handle
            timeline?.onAction(LabWinEventKind.RAISE, window.id, className, titleOf(window), window.width.toInt(), window.height.toInt())
            true
        }
        if (!found) return LabRaiseResult(false)
        remember(server)
        var handler = false
        if (viaWinHandler) {
            handler = runCatching { server.winHandler?.bringToFront(className, handle) != null }.getOrDefault(false)
        }
        return LabRaiseResult(true, sent, handler)
    }

    /** Schickt dem Fenster die Bitte, sich zu schließen (`WM_DELETE_WINDOW`). `false`, wenn es das Fenster nicht gibt oder es den Weg nicht anbietet. */
    fun requestClose(server: XServer, id: Int): Boolean = server.lock(XServer.Lockable.WINDOW_MANAGER).use {
        val window = server.windowManager.getWindow(id)
        val client = window?.originClient
        val protocols = Atom.getId("WM_PROTOCOLS")
        val delete = Atom.getId("WM_DELETE_WINDOW")
        if (window == null || client == null || !window.attributes.isMapped || !closableOf(window)) return@use false
        client.sendEvent(LabCloseRequestEvent(window.id, protocols, delete))
        timeline?.onAction(LabWinEventKind.CLOSE_REQUEST, window.id, window.className.orEmpty(), titleOf(window), window.width.toInt(), window.height.toInt())
        true
    }
}

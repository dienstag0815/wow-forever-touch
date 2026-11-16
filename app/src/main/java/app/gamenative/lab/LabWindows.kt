package app.gamenative.lab

import app.gamenative.report.ReportRedactor

/**
 * Ein X-Fenster im Schnappschuss des X-Servers: nur das, was die Fensterliste und das Protokoll brauchen. [x] und [y] sind Koordinaten
 * der Wurzel (nicht des Elternfensters). Die Kette der Knoten kommt flach und in Stapelfolge (unten zuerst), wie der X-Server sie führt.
 */
data class LabWinNode(
    val id: Int,
    val parentId: Int,
    val className: String,
    val title: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val mapped: Boolean,
    val overrideRedirect: Boolean = false,
    /** `false` bei reinen Eingabefenstern (ohne Bildinhalt): nie sichtbar. */
    val inputOutput: Boolean = true,
    /** Das Fenster nennt `WM_DELETE_WINDOW` in `WM_PROTOCOLS` (ein sauberes Schließen ist möglich). */
    val closable: Boolean = false,
)

/** Ein Eintrag der Fensterliste. [topmost]: das oberste sichtbare Fenster; [focused]: der Eingabefokus des X-Servers liegt auf ihm. */
data class LabWinEntry(
    val id: Int,
    val className: String,
    val title: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val topmost: Boolean,
    val focused: Boolean,
    val closable: Boolean,
)

/** Wohin der Eingabefokus zeigt, wenn nicht auf ein Fenster der Liste. */
enum class LabFocusKind { WINDOW, DESKTOP, ROOT, OTHER, NONE }

/** Die Liste samt Fokus-Angabe. [count] ist die Zahl der Einträge (die Zahl neben dem Knopf). */
data class LabWinSnapshot(val entries: List<LabWinEntry>, val focusKind: LabFocusKind, val focusId: Int?) {
    val count: Int get() = entries.size

    companion object {
        val EMPTY = LabWinSnapshot(emptyList(), LabFocusKind.NONE, null)
    }
}

/** Auswahl, Sortierung und Textform der Fensterliste; rein, ohne Android, JUnit-getestet. */
object LabWindowList {
    /** Hilfsfenster unter dieser Kantenlänge (in Punkten) zählen nicht (Wine legt viele 1×1-Fenster an). */
    const val MIN_SIZE = 8

    /** Klasse des Desktop-Fensters von `wine explorer /desktop=...`; der Renderer der App sucht es genauso (`ASurfaceRenderer.findDesktopWindow`). */
    const val DESKTOP_CLASS = "explorer.exe"

    /** Das Desktop-Fenster unter den Kindern der Wurzel: das erste Kind der Klasse [DESKTOP_CLASS] ohne `override-redirect`. */
    fun desktopId(nodes: List<LabWinNode>, rootId: Int): Int? =
        nodes.firstOrNull { it.parentId == rootId && !it.overrideRedirect && it.className == DESKTOP_CLASS }?.id

    /**
     * Die Fenster der obersten Ebene, oberstes zuerst. Das Desktop-Fenster selbst fehlt, seine Kinder stehen an seiner Stelle in der
     * Stapelfolge der Wurzel. Es zählen nur gemappte Fenster mit Bildinhalt, mindestens [MIN_SIZE]×[MIN_SIZE] Punkte groß.
     */
    fun build(nodes: List<LabWinNode>, rootId: Int, focusId: Int?): LabWinSnapshot {
        val desktop = desktopId(nodes, rootId)
        val bottomUp = ArrayList<LabWinNode>()
        for (node in nodes) {
            if (node.parentId != rootId) continue
            if (node.id != desktop) {
                bottomUp += node
            } else if (node.mapped) {
                bottomUp += nodes.filter { it.parentId == desktop }
            }
        }
        val visible = bottomUp.filter { it.mapped && it.inputOutput && it.width >= MIN_SIZE && it.height >= MIN_SIZE }.asReversed()
        val entries = visible.mapIndexed { i, n ->
            LabWinEntry(n.id, n.className, n.title, n.x, n.y, n.width, n.height, topmost = i == 0, focused = n.id == focusId, closable = n.closable)
        }
        val kind = when {
            focusId == null -> LabFocusKind.NONE
            entries.any { it.id == focusId } -> LabFocusKind.WINDOW
            focusId == desktop -> LabFocusKind.DESKTOP
            focusId == rootId -> LabFocusKind.ROOT
            else -> LabFocusKind.OTHER
        }
        return LabWinSnapshot(entries, kind, focusId)
    }

    /** Ob ein Fenster in den Zeitverlauf des Protokolls gehört: Fenster der obersten Ebene (Kind der Wurzel oder des Desktops), nicht der Desktop selbst, groß genug. */
    fun qualifiesForTimeline(node: LabWinNode, rootId: Int, desktopId: Int?): Boolean =
        node.id != desktopId && node.inputOutput && (node.parentId == rootId || (desktopId != null && node.parentId == desktopId)) &&
            node.width >= MIN_SIZE && node.height >= MIN_SIZE

    /** Druckbarer, gekürzter Text: Steuerzeichen und Zeilenumbrüche weg, höchstens [max] Zeichen. */
    fun clean(text: String, max: Int = 60): String = text.filter { !it.isISOControl() }.trim().take(max)

    /** Ob [atom] (gültig: größer 0) unter den Werten der Eigenschaft `WM_PROTOCOLS` steht. */
    fun containsAtom(values: List<Int>, atom: Int): Boolean = atom > 0 && atom in values

    /** Kennung wie in `LabFocus.text()`: `0x2800009`. */
    fun hex(id: Int): String = "0x" + Integer.toHexString(id)

    /** Kurzform für die Seite: `battle.net.exe  "Titel"`. */
    fun label(entry: LabWinEntry): String {
        val cls = entry.className.ifBlank { "?" }
        val title = clean(entry.title)
        return if (title.isEmpty()) cls else "$cls  \"$title\""
    }

    /** Größe und Lage: `1280×800 @ 80,40`. */
    fun geometry(entry: LabWinEntry): String = "${entry.width}×${entry.height} @ ${entry.x},${entry.y}"

    /** Zeile der Liste im Protokoll (englisch, wie die übrigen Protokollzeilen). */
    fun reportLine(entry: LabWinEntry, redactor: ReportRedactor = ReportRedactor()): String = buildString {
        append(hex(entry.id)).append("  class=").append(clean(entry.className).ifEmpty { "-" })
        append("  title=\"").append(redactor.redact(clean(entry.title))).append("\"")
        append("  ").append(entry.width).append('x').append(entry.height).append(" @ ").append(entry.x).append(',').append(entry.y)
        if (entry.topmost) append("  [topmost]")
        if (entry.focused) append("  [focus]")
        if (entry.closable) append("  [closable]")
    }

    /** Die Angabe zum Fokus am Ende der Liste (Protokoll). */
    fun focusLine(snapshot: LabWinSnapshot): String = when (snapshot.focusKind) {
        LabFocusKind.WINDOW -> "Input focus: window ${hex(snapshot.focusId ?: 0)} (listed above)"
        LabFocusKind.DESKTOP -> "Input focus: the desktop window ${hex(snapshot.focusId ?: 0)} (no application window has it)"
        LabFocusKind.ROOT -> "Input focus: the X root window (no application window has it)"
        LabFocusKind.OTHER -> "Input focus: window ${hex(snapshot.focusId ?: 0)} (not a top-level window of the list)"
        LabFocusKind.NONE -> "Input focus: none"
    }
}

/** Was der Zeitverlauf festhält. */
enum class LabWinEventKind(val label: String) {
    /** Das Fenster war schon offen, als die Aufzeichnung begann. */
    SEEN("seen"),
    OPEN("open"),
    CLOSE("close"),
    TITLE("title"),

    /** Der Nutzer hat das Fenster in der Liste nach vorn geholt. */
    RAISE("raise"),

    /** Der Nutzer hat dem Fenster in der Liste die Bitte zu schließen geschickt. */
    CLOSE_REQUEST("close-request"),
}

data class LabWinEvent(val atMs: Long, val kind: LabWinEventKind, val id: Int, val className: String, val title: String, val width: Int, val height: Int)

/**
 * Zeitverlauf der Fenster der obersten Ebene für den Abschnitt "Windows" des Protokolls: geöffnet, geschlossen, Titel geändert, mit
 * Zeit seit Start. Rein (Uhr ersetzbar), nicht an Android gebunden. Je Fenster merkt sie sich nur, ob es offen ist und welchen Titel
 * es zuletzt hatte; hält höchstens [MAX_EVENTS] Ereignisse im Speicher (die ältesten bleiben, die jüngsten ebenfalls: siehe [LabWindowsReport]).
 */
class LabWindowTimeline(private val startedMs: Long, private val nowMs: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val open = HashMap<Int, LabWinEvent>()
    private val events = ArrayList<LabWinEvent>()
    private var dropped = 0

    /** Das Fenster ist (jetzt) offen. Mehrfache Meldungen desselben Fensters sind wirkungslos; ein neuer Titel wird als Titeländerung festgehalten. */
    fun onOpen(id: Int, className: String, title: String, width: Int, height: Int, alreadyOpen: Boolean = false) = synchronized(lock) {
        val known = open[id]
        if (known == null) {
            add(LabWinEvent(nowMs(), if (alreadyOpen) LabWinEventKind.SEEN else LabWinEventKind.OPEN, id, className, title, width, height))
        } else if (title != known.title && title.isNotEmpty()) {
            add(LabWinEvent(nowMs(), LabWinEventKind.TITLE, id, className.ifEmpty { known.className }, title, width, height))
        }
    }

    /** Ein bekanntes Fenster ändert Klasse oder Titel (Eigenschaft gesetzt, nachdem es offen war). */
    fun onChange(id: Int, className: String, title: String, width: Int, height: Int) = synchronized(lock) {
        val known = open[id] ?: return@synchronized
        if (title != known.title || (className.isNotEmpty() && className != known.className)) {
            add(LabWinEvent(nowMs(), LabWinEventKind.TITLE, id, className.ifEmpty { known.className }, title, width, height))
        }
    }

    fun onClose(id: Int) = synchronized(lock) {
        val known = open.remove(id) ?: return@synchronized
        events += LabWinEvent(nowMs(), LabWinEventKind.CLOSE, id, known.className, known.title, known.width, known.height)
        trim()
    }

    /** Eine Aktion des Nutzers an einem Fenster ([LabWinEventKind.RAISE], [LabWinEventKind.CLOSE_REQUEST]); ändert nicht, welche Fenster offen sind. */
    fun onAction(kind: LabWinEventKind, id: Int, className: String, title: String, width: Int, height: Int) = synchronized(lock) {
        events += LabWinEvent(nowMs(), kind, id, className, title, width, height)
        trim()
    }

    fun isOpen(id: Int): Boolean = synchronized(lock) { id in open }

    fun openCount(): Int = synchronized(lock) { open.size }

    private fun add(e: LabWinEvent) {
        open[e.id] = e
        events += e
        trim()
    }

    private fun trim() {
        // Speicher begrenzen; die Kürzung fürs Protokoll macht LabWindowsReport.
        if (events.size > MAX_EVENTS) {
            events.removeAt(MAX_EVENTS / 2)
            dropped++
        }
    }

    /** Die Ereignisse in der Reihenfolge ihres Eintretens und wie viele aus dem Speicher fielen. */
    fun snapshot(): Pair<List<LabWinEvent>, Int> = synchronized(lock) { events.toList() to dropped }

    fun startedMs(): Long = startedMs

    companion object {
        const val MAX_EVENTS = 2000
    }
}

/** Der Abschnitt "Windows" des Protokolls: Zeitverlauf, dann die Liste der am Ende offenen Fenster in Stapelfolge; höchstens [MAX_LINES] Zeilen. */
object LabWindowsReport {
    const val MAX_LINES = 400

    /** Bis zu so viele Zeilen darf die Liste der am Ende offenen Fenster haben (der Rest des Platzes gehört dem Zeitverlauf). */
    private const val MAX_OPEN_LINES = 80

    /** Höchstens so viele Zeilen `error window`. */
    const val MAX_ERROR_LINES = 10

    /** Ein Titel, der "Fehler" oder "Error" enthält (Groß-/Kleinschreibung egal): `Battle.net-Fehler`, `Battle.net Error`. */
    fun isErrorTitle(title: String): Boolean = title.contains("Fehler", ignoreCase = true) || title.contains("Error", ignoreCase = true)

    /** Zeile `error window` zu einem Ereignis: Zeit, Art, Kennung, Klasse, Titel (geschwärzt), Größe. */
    fun errorLine(e: LabWinEvent, startedMs: Long, redactor: ReportRedactor = ReportRedactor()): String = "error window  " + eventLine(e, startedMs, redactor)

    /**
     * Die Ereignisse, bei denen ein Fenster mit einem Fehler-Titel erschien (gesehen, geöffnet oder Titel geändert), je Fenster und Titel einmal;
     * ohne Schließen und Aktionen des Nutzers. Höchstens [MAX_ERROR_LINES] Zeilen, ein Zähler für den Rest.
     */
    fun errorLines(events: List<LabWinEvent>, startedMs: Long, redactor: ReportRedactor = ReportRedactor()): List<String> {
        val seen = HashSet<Pair<Int, String>>()
        val hits = events.filter {
            (it.kind == LabWinEventKind.SEEN || it.kind == LabWinEventKind.OPEN || it.kind == LabWinEventKind.TITLE) &&
                isErrorTitle(it.title) && seen.add(it.id to it.title)
        }
        val shown = hits.take(MAX_ERROR_LINES).map { errorLine(it, startedMs, redactor) }
        return if (hits.size > shown.size) shown + "error window  (${hits.size - shown.size} more not listed)" else shown
    }

    /** Hinweis, wenn nichts aufgezeichnet wurde (kein X-Server, kein Fenster). */
    const val NOTHING = "(no window events recorded)"

    fun eventLine(e: LabWinEvent, startedMs: Long, redactor: ReportRedactor = ReportRedactor()): String {
        val t = "+" + "%.3f".format(java.util.Locale.ROOT, (e.atMs - startedMs) / 1000.0) + "s"
        val title = redactor.redact(LabWindowList.clean(e.title))
        return "$t  ${e.kind.label}  ${LabWindowList.hex(e.id)}  class=${LabWindowList.clean(e.className).ifEmpty { "-" }}  title=\"$title\"  ${e.width}x${e.height}"
    }

    /**
     * Die Zeilen. Der Zeitverlauf behält Anfang und Ende, wenn er nicht in den Platz passt; die Kürzung steht in einer Zeile dazwischen.
     * [droppedInMemory]: Ereignisse, die der Speicher schon verloren hat (nur bei sehr vielen Fenstern).
     */
    fun lines(
        events: List<LabWinEvent>,
        droppedInMemory: Int,
        startedMs: Long,
        atEnd: LabWinSnapshot?,
        redactor: ReportRedactor = ReportRedactor(),
        maxLines: Int = MAX_LINES,
    ): List<String> {
        val tail = buildList {
            add("Open top-level windows at the end (topmost first): ${atEnd?.count ?: "unknown"}")
            if (atEnd != null) {
                val shown = atEnd.entries.take(MAX_OPEN_LINES)
                shown.forEach { add("  " + LabWindowList.reportLine(it, redactor)) }
                if (atEnd.entries.size > shown.size) add("  (${atEnd.entries.size - shown.size} more not listed)")
                add(LabWindowList.focusLine(atEnd))
            }
        }
        val head = listOf("Top-level windows opened and closed (time since start, id, class, title, size; windows under ${LabWindowList.MIN_SIZE}x${LabWindowList.MIN_SIZE} are not listed):")
        val errors = errorLines(events, startedMs, redactor)
        val room = (maxLines - head.size - tail.size - errors.size - 1).coerceAtLeast(2)
        val all = events.map { eventLine(it, startedMs, redactor) }
        val timeline = if (all.isEmpty()) {
            listOf("  $NOTHING")
        } else if (all.size + (if (droppedInMemory > 0) 1 else 0) <= room) {
            val list = all.map { "  $it" }.toMutableList()
            if (droppedInMemory > 0) list.add(list.size / 2, "  ($droppedInMemory events in the middle were not kept)")
            list
        } else {
            val front = room / 2
            val back = room - front - 1
            val omitted = all.size - front - back + droppedInMemory
            all.take(front).map { "  $it" } + "  ($omitted events in the middle left out)" + all.takeLast(back).map { "  $it" }
        }
        return head + timeline + errors + tail
    }
}

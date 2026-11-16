package app.gamenative.lab

import app.gamenative.report.ReportRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fensterliste der Tastenleiste und Abschnitt "Windows" des Labor-Protokolls: Auswahl, Reihenfolge, Zeitverlauf, Kürzung, Schwärzung. */
class LabWindowsTest {
    private val root = 1
    private val desktop = 0x800007

    private fun node(
        id: Int,
        parent: Int,
        cls: String = "battle.net.exe",
        title: String = "",
        w: Int = 640,
        h: Int = 480,
        mapped: Boolean = true,
        io: Boolean = true,
        closable: Boolean = false,
        overrideRedirect: Boolean = false,
    ) = LabWinNode(id, parent, cls, title, 10, 20, w, h, mapped, overrideRedirect, io, closable)

    private val desktopNode = node(desktop, root, cls = "explorer.exe", w = 1440, h = 900)

    @Test
    fun desktopWindowIsLeftOutAndItsChildrenAreListedTopmostFirst() {
        val nodes = listOf(
            desktopNode,
            node(0x2800009, desktop, title = "Main"),
            node(0x280000b, desktop, title = "Login"),
            node(0x280000e, desktop, title = "Pop-up"),
        )
        val s = LabWindowList.build(nodes, root, focusId = 0x280000b)
        assertEquals(listOf(0x280000e, 0x280000b, 0x2800009), s.entries.map { it.id })
        assertEquals(3, s.count)
        assertEquals(listOf(true, false, false), s.entries.map { it.topmost })
        assertEquals(listOf(false, true, false), s.entries.map { it.focused })
        assertEquals(LabFocusKind.WINDOW, s.focusKind)
    }

    @Test
    fun windowsAboveTheDesktopStayAboveItsChildren() {
        val nodes = listOf(
            node(0x10, root, cls = "tray.exe"),
            desktopNode,
            node(0x2800009, desktop),
            node(0x20, root, cls = "popup.exe"),
        )
        val ids = LabWindowList.build(nodes, root, null).entries.map { it.id }
        // Stapelfolge der Wurzel unten nach oben: tray, Desktop (mit seinem Kind), popup.
        assertEquals(listOf(0x20, 0x2800009, 0x10), ids)
    }

    @Test
    fun tinyUnmappedAndInputOnlyWindowsAreFilteredOut() {
        val nodes = listOf(
            desktopNode,
            node(1001, desktop, w = 1, h = 1),
            node(1002, desktop, w = 7, h = 300),
            node(1003, desktop, w = 8, h = 8),
            node(1004, desktop, mapped = false),
            node(1005, desktop, io = false),
            node(1006, desktop, w = 300, h = 7),
        )
        assertEquals(listOf(1003), LabWindowList.build(nodes, root, null).entries.map { it.id })
    }

    @Test
    fun anUnmappedDesktopHidesItsChildren() {
        val nodes = listOf(desktopNode.copy(mapped = false), node(0x2800009, desktop))
        assertEquals(0, LabWindowList.build(nodes, root, null).count)
    }

    @Test
    fun withoutADesktopWindowTheRootChildrenAreTheTopLevelWindows() {
        val nodes = listOf(node(5, root), node(6, root))
        assertEquals(listOf(6, 5), LabWindowList.build(nodes, root, null).entries.map { it.id })
        assertNull(LabWindowList.desktopId(nodes, root))
    }

    @Test
    fun desktopIsTheFirstExplorerChildOfTheRootThatIsNotOverrideRedirect() {
        val nodes = listOf(
            node(7, root, cls = "explorer.exe", overrideRedirect = true),
            node(8, root, cls = "explorer.exe"),
            node(9, root, cls = "explorer.exe"),
            node(10, 8, cls = "explorer.exe"),
        )
        assertEquals(8, LabWindowList.desktopId(nodes, root))
    }

    @Test
    fun focusKindNamesWhereTheFocusIs() {
        val nodes = listOf(desktopNode, node(0x2800009, desktop))
        assertEquals(LabFocusKind.NONE, LabWindowList.build(nodes, root, null).focusKind)
        assertEquals(LabFocusKind.DESKTOP, LabWindowList.build(nodes, root, desktop).focusKind)
        assertEquals(LabFocusKind.ROOT, LabWindowList.build(nodes, root, root).focusKind)
        assertEquals(LabFocusKind.OTHER, LabWindowList.build(nodes, root, 12345).focusKind)
        assertEquals(LabFocusKind.WINDOW, LabWindowList.build(nodes, root, 0x2800009).focusKind)
        assertTrue(LabWindowList.focusLine(LabWindowList.build(nodes, root, desktop)).contains("desktop"))
    }

    @Test
    fun timelineAdmitsTopLevelWindowsOnly() {
        val top = node(0x2800009, desktop)
        assertTrue(LabWindowList.qualifiesForTimeline(top, root, desktop))
        assertTrue(LabWindowList.qualifiesForTimeline(node(3, root), root, null))
        assertFalse("the desktop itself", LabWindowList.qualifiesForTimeline(desktopNode, root, desktop))
        assertFalse("a child of a top-level window", LabWindowList.qualifiesForTimeline(node(4, 0x2800009), root, desktop))
        assertFalse("tiny", LabWindowList.qualifiesForTimeline(node(5, desktop, w = 4, h = 4), root, desktop))
        assertFalse("input only", LabWindowList.qualifiesForTimeline(node(6, desktop, io = false), root, desktop))
        assertFalse("child of the desktop without a desktop window", LabWindowList.qualifiesForTimeline(node(7, desktop), root, null))
    }

    @Test
    fun cleanStripsControlCharactersAndCuts() {
        assertEquals("ab c", LabWindowList.clean("ab\n\u0000 c\t"))
        assertEquals(60, LabWindowList.clean("x".repeat(100)).length)
        assertEquals("Fenster ä", LabWindowList.clean("  Fenster ä "))
    }

    @Test
    fun labelsAndGeometry() {
        val e = LabWinEntry(0x2800009, "battle.net.exe", "Battle.net", 80, 40, 1280, 800, topmost = true, focused = false, closable = false)
        assertEquals("battle.net.exe  \"Battle.net\"", LabWindowList.label(e))
        assertEquals("battle.net.exe", LabWindowList.label(e.copy(title = "")))
        assertEquals("?", LabWindowList.label(e.copy(title = "", className = "")))
        assertEquals("1280×800 @ 80,40", LabWindowList.geometry(e))
        val line = LabWindowList.reportLine(e.copy(focused = true, closable = true))
        assertTrue(line, line.startsWith("0x2800009  class=battle.net.exe  title=\"Battle.net\"  1280x800 @ 80,40"))
        assertTrue(line.endsWith("[topmost]  [focus]  [closable]"))
    }

    @Test
    fun deleteAtomIsFoundInTheProtocolList() {
        assertTrue(LabWindowList.containsAtom(listOf(300, 301, 302), 301))
        assertFalse(LabWindowList.containsAtom(listOf(300, 302), 301))
        assertFalse("an atom that does not exist yet (-1) never matches", LabWindowList.containsAtom(listOf(-1), -1))
    }

    @Test
    fun timelineRecordsOpenTitleAndClose() {
        var now = 10_000L
        val t = LabWindowTimeline(startedMs = 10_000L, nowMs = { now })
        t.onOpen(1, "battle.net.exe", "", 640, 480)
        t.onOpen(1, "battle.net.exe", "", 640, 480)
        now += 2_500
        t.onChange(1, "battle.net.exe", "Login", 640, 480)
        now += 1_000
        t.onClose(1)
        t.onClose(1)
        val (events, dropped) = t.snapshot()
        assertEquals(0, dropped)
        assertEquals(listOf(LabWinEventKind.OPEN, LabWinEventKind.TITLE, LabWinEventKind.CLOSE), events.map { it.kind })
        assertEquals("Login", events.last().title)
        assertEquals(0, t.openCount())
        assertFalse(t.isOpen(1))
    }

    @Test
    fun windowsAlreadyOpenAtTheStartAreSeenAndActionsDoNotChangeWhatIsOpen() {
        val t = LabWindowTimeline(0L, nowMs = { 0L })
        t.onOpen(5, "explorer.exe", "", 800, 600, alreadyOpen = true)
        t.onAction(LabWinEventKind.RAISE, 5, "explorer.exe", "", 800, 600)
        t.onAction(LabWinEventKind.CLOSE_REQUEST, 5, "explorer.exe", "", 800, 600)
        assertEquals(1, t.openCount())
        assertEquals(listOf(LabWinEventKind.SEEN, LabWinEventKind.RAISE, LabWinEventKind.CLOSE_REQUEST), t.snapshot().first.map { it.kind })
    }

    @Test
    fun anEmptyTitleDoesNotWriteATitleEvent() {
        val t = LabWindowTimeline(0L, nowMs = { 0L })
        t.onOpen(1, "a.exe", "Login", 100, 100)
        t.onOpen(1, "a.exe", "", 100, 100)
        assertEquals(1, t.snapshot().first.size)
    }

    @Test
    fun eventLineShowsTimeIdClassTitleAndSize() {
        val e = LabWinEvent(12_345L, LabWinEventKind.OPEN, 0x280000b, "battle.net.exe", "Einloggen.", 300, 120)
        assertEquals("+2.345s  open  0x280000b  class=battle.net.exe  title=\"Einloggen.\"  300x120", LabWindowsReport.eventLine(e, 10_000L))
    }

    @Test
    fun titlesGoThroughTheRedactor() {
        val redactor = ReportRedactor(extraSecrets = listOf("secret-token-value"))
        val e = LabWinEvent(0L, LabWinEventKind.TITLE, 7, "a.exe", "Welcome secret-token-value", 100, 100)
        val line = LabWindowsReport.eventLine(e, 0L, redactor)
        assertFalse(line, line.contains("secret-token-value"))
        assertTrue(line, line.contains(ReportRedactor.SECRET))
        val entry = LabWinEntry(7, "a.exe", "x secret-token-value", 0, 0, 100, 100, topmost = false, focused = false, closable = false)
        assertFalse(LabWindowList.reportLine(entry, redactor).contains("secret-token-value"))
    }

    private fun events(n: Int) = (1..n).map { LabWinEvent(it * 1000L, if (it % 2 == 1) LabWinEventKind.OPEN else LabWinEventKind.CLOSE, it, "a.exe", "t$it", 100, 100) }

    @Test
    fun theReportStaysUnderTheLineLimitAndKeepsBeginningAndEnd() {
        val end = LabWinSnapshot(
            (1..5).map { LabWinEntry(it, "a.exe", "w$it", 0, 0, 100, 100, topmost = it == 1, focused = it == 2, closable = false) },
            LabFocusKind.WINDOW,
            2,
        )
        val lines = LabWindowsReport.lines(events(1000), 0, 0L, end)
        assertTrue("${lines.size} lines", lines.size <= LabWindowsReport.MAX_LINES)
        assertTrue(lines.any { it.contains("+1.000s  open  0x1 ") })
        assertTrue(lines.any { it.contains("+1000.000s  close  0x3e8 ") })
        val cut = lines.single { it.contains("events in the middle left out") }
        val shown = lines.count { Regex("^\\s+\\+\\d").containsMatchIn(it) }
        assertEquals("1000 events, shown + omitted", 1000, shown + cut.trim().removePrefix("(").substringBefore(' ').toInt())
        assertTrue(lines.any { it.startsWith("Open top-level windows at the end (topmost first): 5") })
        assertTrue(lines.any { it.contains("[topmost]") })
        assertTrue(lines.any { it.contains("[focus]") })
    }

    @Test
    fun aShortRecordIsListedWithoutACutNote() {
        val lines = LabWindowsReport.lines(events(10), 0, 0L, null)
        assertFalse(lines.any { it.contains("left out") })
        assertEquals(10, lines.count { Regex("^\\s+\\+\\d").containsMatchIn(it) })
        assertTrue(lines.any { it.contains("unknown") })
    }

    @Test
    fun nothingRecordedIsSaidSo() {
        val lines = LabWindowsReport.lines(emptyList(), 0, 0L, LabWinSnapshot.EMPTY)
        assertTrue(lines.any { it.contains(LabWindowsReport.NOTHING) })
        assertTrue(lines.any { it.startsWith("Open top-level windows at the end (topmost first): 0") })
    }

    @Test
    fun memoryLossIsNamed() {
        val lines = LabWindowsReport.lines(events(4), 7, 0L, null)
        assertTrue(lines.any { it.contains("7 events in the middle were not kept") })
    }

    @Test
    fun memoryIsBoundedAndTheNewestEventIsKept() {
        val t = LabWindowTimeline(0L, nowMs = { 0L })
        for (i in 1..(LabWindowTimeline.MAX_EVENTS + 50)) t.onOpen(i, "a.exe", "t", 100, 100)
        val (events, dropped) = t.snapshot()
        assertEquals(LabWindowTimeline.MAX_EVENTS, events.size)
        assertEquals(50, dropped)
        assertEquals(LabWindowTimeline.MAX_EVENTS + 50, events.last().id)
        assertEquals(1, events.first().id)
    }

    @Test
    fun protocolBlockHasTheSectionTitle() {
        val text = LabProtocol.windowsBlock(listOf("a", "b"))
        assertTrue(text.contains("== Windows =="))
        assertTrue(text.contains("a\nb") || text.contains("a${System.lineSeparator()}b"))
        assertTrue(LabProtocol.windowsBlock(emptyList()).contains("(no window record)"))
    }
}

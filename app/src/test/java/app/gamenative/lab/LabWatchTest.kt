package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hänger-Hinweis (Auftrag labweg1, Punkt 3): Fenstersequenzen im Zeilenformat der echten Läufe (Abschnitt `== Windows ==` der Protokolle vom 15.11.),
 * mit der Abfrage der Tastenleiste (alle 500 ms) in [LabWatch.onSnapshot] gespielt.
 */
class LabWatchTest {
    private val eventLine = Regex("""^\s*\+([0-9.]+)s\s+(open|close)\s+(0x[0-9a-f]+)\s+class=(\S+)\s+title="([^"]*)"\s+(\d+)x(\d+)\s*$""")

    private data class Ev(val atMs: Long, val open: Boolean, val id: Int, val cls: String, val title: String, val w: Int, val h: Int)

    private fun parse(text: String): List<Ev> = text.trimIndent().lines().filter { it.isNotBlank() }.map { line ->
        val m = eventLine.matchEntire(line) ?: error("unparsable line: $line")
        Ev(
            atMs = (m.groupValues[1].toDouble() * 1000).toLong(),
            open = m.groupValues[2] == "open",
            id = m.groupValues[3].removePrefix("0x").toInt(16),
            cls = m.groupValues[4],
            title = m.groupValues[5],
            w = m.groupValues[6].toInt(),
            h = m.groupValues[7].toInt(),
        )
    }

    private data class Sample(val atMs: Long, val state: LabWatchState, val change: LabWatchChange?)

    /** Spielt die Ereignisse ab und fragt alle [stepMs] Millisekunden bis [endMs] die Fensterliste ab. */
    private fun replay(text: String, endMs: Long, stepMs: Long = 500L): List<Sample> {
        val events = parse(text)
        val open = LinkedHashMap<Int, Ev>()
        var status = LabWatchStatus()
        var next = 0
        val out = ArrayList<Sample>()
        var t = 0L
        while (t <= endMs) {
            while (next < events.size && events[next].atMs <= t) {
                val e = events[next++]
                if (e.open) open[e.id] = e else open.remove(e.id)
            }
            val entries = open.values.reversed().mapIndexed { i, e ->
                LabWinEntry(e.id, e.cls, e.title, 0, 0, e.w, e.h, topmost = i == 0, focused = false, closable = false)
            }
            val step = LabWatch.onSnapshot(status, LabWinSnapshot(entries, LabFocusKind.NONE, null), t)
            status = step.status
            out += Sample(t, status.state, step.change)
            t += stepMs
        }
        return out
    }

    private fun hintTimes(samples: List<Sample>): List<Long> = samples.filter { LabWatch.showsHint(it.state) }.map { it.atMs }

    /** Lauf `152227`: Anmeldefenster, 640x480, Hauptfenster, dann nichts mehr bis zum Ende. */
    private val normalStart = """
        +3.412s  open  0x800009  class=explorer.exe  title=""  1440x30
        +13.450s  open  0x2800009  class=battle.net.exe  title="Battle.net-Login"  362x693
        +16.026s  close  0x2800009  class=battle.net.exe  title="Battle.net-Login"  362x693
        +16.191s  open  0x280000b  class=battle.net.exe  title="Battle.net"  640x480
        +19.621s  close  0x280000b  class=battle.net.exe  title="Battle.net"  640x480
        +20.983s  open  0x280000e  class=battle.net.exe  title="Battle.net"  1440x641
    """

    @Test
    fun normalStartShowsNoHintAndLogsTheChanges() {
        val s = replay(normalStart, 70_000)
        assertTrue(hintTimes(s).isEmpty())
        // Davor: startet; ab dem ersten Fenster des Launchers (+13,45 s, erste Abfrage danach) ist alles in Ordnung.
        assertEquals(LabWatchState.STARTING, s.first { it.atMs == 13_000L }.state)
        assertEquals(LabWatchState.WINDOW_OK, s.first { it.atMs == 13_500L }.state)
        val changes = s.mapNotNull { it.change }
        assertEquals(listOf(LabWatchState.WINDOW_OK), changes.map { it.to })
        assertEquals(LabWatchState.STARTING, changes.single().from)
        assertEquals("-- Watch: window_ok after 13.5 s", LabWatch.changeLine(changes.single()))
    }

    @Test
    fun gapBetweenSignInAndMainWindowDoesNotFlicker() {
        // Zwischen „Close“ des Anmeldefensters und „Open“ des Hauptfensters liegen im Lauf `152336` Zehntelsekunden; auch ein Spalt von 5 s bleibt ohne Hinweis.
        val s = replay(
            """
            +20.479s  open  0x2800009  class=battle.net.exe  title="Battle.net-Login"  362x693
            +22.525s  close  0x2800009  class=battle.net.exe  title="Battle.net-Login"  362x693
            +27.800s  open  0x280000c  class=battle.net.exe  title="Battle.net"  1440x641
            """,
            60_000,
        )
        assertTrue(hintTimes(s).isEmpty())
        assertEquals(listOf(LabWatchState.WINDOW_OK), s.mapNotNull { it.change }.map { it.to })
    }

    @Test
    fun longReloadWithAnOpenWindowShowsNoHint() {
        // Lauf `152849`/`153707` (Nachladen, rund 7,5 Minuten ohne Fensterereignis): das Hauptfenster bleibt offen.
        val s = replay(normalStart, 8 * 60_000L, stepMs = 2_000L)
        assertTrue(hintTimes(s).isEmpty())
        assertEquals(LabWatchState.WINDOW_OK, s.last().state)
    }

    @Test
    fun startWithoutAnyWindowShowsTheHintAfterTheThreshold() {
        // Lauf `152712`: nur die Wine-Leiste (explorer.exe), nie ein Fenster des Launchers.
        val s = replay("+3.423s  open  0x800009  class=explorer.exe  title=\"\"  1440x30", 120_000)
        val first = hintTimes(s).first()
        assertEquals(LabWatch.NO_WINDOW_AFTER_MS, first)
        assertEquals(LabWatchState.STARTING, s.first { it.atMs == first - 500 }.state)
        assertEquals(LabWatchState.NO_WINDOW, s.first { it.atMs == first }.state)
        assertTrue(hintTimes(s).all { it >= LabWatch.NO_WINDOW_AFTER_MS })
        val change = s.mapNotNull { it.change }.single()
        assertEquals(LabWatchChange(LabWatchState.STARTING, LabWatchState.NO_WINDOW, 60_000), change)
        assertEquals("-- Watch: no_window after 60.0 s", LabWatch.changeLine(change))
    }

    @Test
    fun noWindowEventsAtAllStillCountsFromTheStart() {
        val s = replay("", 70_000)
        assertEquals(LabWatchState.NO_WINDOW, s.last().state)
        assertEquals(LabWatch.NO_WINDOW_AFTER_MS, hintTimes(s).first())
    }

    @Test
    fun errorWindowShowsTheHintAtOnceAndOnlyWhileItIsOpen() {
        // Lauf `144410`: Hauptfenster offen, „Battle.net-Fehler“ von +56,059 s bis +58,704 s.
        val s = replay(
            """
            +15.149s  open  0x280000c  class=battle.net.exe  title="Battle.net"  1440x641
            +47.012s  open  0x2800039  class=battle.net.exe  title="Battle.net-Einstellungen"  996x708
            +56.059s  open  0x2800042  class=battle.net.exe  title="Battle.net-Fehler"  400x210
            +58.704s  close  0x2800042  class=battle.net.exe  title="Battle.net-Fehler"  400x210
            +61.565s  open  0x2800046  class=battle.net.exe  title="Select Folder"  668x522
            """,
            90_000,
        )
        val hints = hintTimes(s)
        // Sofort: die erste Abfrage nach dem Öffnen (höchstens eine Abfrage = 500 ms später).
        assertEquals(56_500L, hints.first())
        assertEquals(58_500L, hints.last())
        assertEquals(LabWatchState.WINDOW_OK, s.first { it.atMs == 59_000L }.state)
        assertEquals(
            listOf(LabWatchState.WINDOW_OK, LabWatchState.ERROR_WINDOW, LabWatchState.WINDOW_OK),
            s.mapNotNull { it.change }.map { it.to },
        )
        assertEquals("-- Watch: error_window after 56.5 s", LabWatch.changeLine(s.mapNotNull { it.change }[1]))
    }

    @Test
    fun errorWindowOfEnglishTitleCountsToo() {
        val e = LabWinEntry(1, "battle.net.exe", "Battle.net Error", 0, 0, 400, 248, topmost = true, focused = false, closable = false)
        val step = LabWatch.onSnapshot(LabWatchStatus(), LabWinSnapshot(listOf(e), LabFocusKind.NONE, null), 2_000)
        assertEquals(LabWatchState.ERROR_WINDOW, step.status.state)
        assertTrue(LabWatch.showsHint(step.status.state))
    }

    @Test
    fun errorTitleOfAnotherProgramIsNotTheLaunchers() {
        val e = LabWinEntry(1, "other.exe", "Error", 0, 0, 400, 248, topmost = true, focused = false, closable = false)
        val step = LabWatch.onSnapshot(LabWatchStatus(), LabWinSnapshot(listOf(e), LabFocusKind.NONE, null), 2_000)
        assertEquals(LabWatchState.STARTING, step.status.state)
    }

    @Test
    fun noHintWhileAGameWindowIsOpen() {
        // Das Spielfenster ist offen, der Launcher hat kein Fenster mehr und ein Fehlerfenster steht daneben: kein Hinweis.
        val s = replay(
            """
            +13.376s  open  0x2800009  class=battle.net.exe  title="Battle.net-Login"  362x693
            +20.412s  open  0x280000e  class=battle.net.exe  title="Battle.net"  1440x696
            +100.000s  open  0x6c00005  class=wow-arm64.exe  title="World of Warcraft"  1440x900
            +110.000s  close  0x280000e  class=battle.net.exe  title="Battle.net"  1440x696
            +150.000s  open  0x2800090  class=battle.net.exe  title="Battle.net-Fehler"  400x210
            """,
            400_000,
            stepMs = 1_000L,
        )
        assertTrue(hintTimes(s).isEmpty())
        assertEquals(LabWatchState.WINDOW_OK, s.last().state)
    }

    @Test
    fun afterTheGameWindowClosesTheClockRunsFromThen() {
        val s = replay(
            """
            +20.412s  open  0x280000e  class=battle.net.exe  title="Battle.net"  1440x696
            +30.000s  open  0x6c00005  class=wow-arm64.exe  title="World of Warcraft"  1440x900
            +100.000s  close  0x280000e  class=battle.net.exe  title="Battle.net"  1440x696
            +200.000s  close  0x6c00005  class=wow-arm64.exe  title="World of Warcraft"  1440x900
            """,
            400_000,
            stepMs = 1_000L,
        )
        val first = hintTimes(s).first()
        // Das Spielfenster war bis +200 s offen (letzte Abfrage mit Fenster: +199 s); 60 s danach kommt der Hinweis.
        assertEquals(199_000L + LabWatch.NO_WINDOW_AFTER_MS, first)
    }

    @Test
    fun launcherWindowsClosedForAMinuteShowTheHint() {
        val s = replay(
            """
            +13.000s  open  0x2800009  class=battle.net.exe  title="Battle.net-Login"  362x693
            +20.000s  close  0x2800009  class=battle.net.exe  title="Battle.net-Login"  362x693
            """,
            120_000,
            stepMs = 1_000L,
        )
        assertEquals(19_000L + LabWatch.NO_WINDOW_AFTER_MS, hintTimes(s).first())
        // Kommt danach wieder ein Fenster, ist der Hinweis weg.
        val back = replay(
            """
            +13.000s  open  0x2800009  class=battle.net.exe  title="Battle.net-Login"  362x693
            +20.000s  close  0x2800009  class=battle.net.exe  title="Battle.net-Login"  362x693
            +90.000s  open  0x280000d  class=battle.net.exe  title="Battle.net"  1440x696
            """,
            120_000,
            stepMs = 1_000L,
        )
        assertEquals(LabWatchState.WINDOW_OK, back.last().state)
        assertEquals(
            listOf(LabWatchState.WINDOW_OK, LabWatchState.NO_WINDOW, LabWatchState.WINDOW_OK),
            back.mapNotNull { it.change }.map { it.to },
        )
    }

    @Test
    fun smallHelperWindowsAreNotListedSoTheyNeverCount() {
        // Fenster unter 8x8 Punkten kommen nicht in die Liste (LabWindowList.build); die Erkennung sieht nur die Liste.
        val nodes = listOf(
            LabWinNode(5, 1, "battle.net.exe", "Battle.net", 0, 0, 1, 1, mapped = true),
        )
        val snapshot = LabWindowList.build(nodes, rootId = 1, focusId = null)
        assertTrue(snapshot.entries.isEmpty())
        assertEquals(LabWatchState.STARTING, LabWatch.onSnapshot(LabWatchStatus(), snapshot, 5_000).status.state)
    }

    @Test
    fun restartLineFormat() {
        assertEquals("-- Restart requested by user after 75.3 s", LabWatch.restartLine(75_340))
        assertEquals("-- Restart requested by user after 0.0 s", LabWatch.restartLine(0))
    }

    @Test
    fun onlyTheTwoProblemStatesShowTheHint() {
        assertFalse(LabWatch.showsHint(LabWatchState.STARTING))
        assertFalse(LabWatch.showsHint(LabWatchState.WINDOW_OK))
        assertTrue(LabWatch.showsHint(LabWatchState.NO_WINDOW))
        assertTrue(LabWatch.showsHint(LabWatchState.ERROR_WINDOW))
        assertEquals(listOf("starting", "window_ok", "no_window", "error_window"), LabWatchState.entries.map { it.text })
    }

    @Test
    fun restartOfferLivesOnceAndExpires() {
        LabRestart.clear()
        assertNull(LabRestart.pending(0))
        LabRestart.offer(LabTarget.BATTLENET, nowMs = 1_000)
        assertEquals(LabTarget.BATTLENET, LabRestart.pending(2_000))
        assertEquals(LabTarget.BATTLENET, LabRestart.pending(1_000 + LabRestart.OFFER_TTL_MS))
        assertNull(LabRestart.pending(1_001 + LabRestart.OFFER_TTL_MS))
        // Nach dem Verfall ist der Wunsch weg, auch für eine frühere Zeit.
        assertNull(LabRestart.pending(2_000))
        LabRestart.offer(LabTarget.INSTALLER, nowMs = 5)
        LabRestart.clear()
        assertNull(LabRestart.pending(6))
    }
}

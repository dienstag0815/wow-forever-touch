package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LoginJournalTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(n: Long, endMs: Long? = null) = JournalEntry(
        startMs = 1_700_000_000_000L + n * 60_000,
        appVersion = "2.3.3-fold9.1",
        level = "fill",
        scope = "all",
        loginFileWritten = false,
        loginFileRemovedMs = null,
        fillTapMs = 1_700_000_000_000L + n * 60_000 + 12_000,
        fillResult = "done",
        worldMs = 1_700_000_000_000L + n * 60_000 + 83_000,
        endKind = "game_exit",
        endMs = endMs,
        network = "wifi",
        vpn = false,
        ipv6 = true,
        idFingerprint = "ab12cd34",
        systemFingerprint = "ef56ab78",
        sinceLastEndSec = 640,
    )

    @Test
    fun roundTripKeepsEveryField() {
        val e = entry(1, endMs = 1_700_000_000_000L + 60_000 + 3_600_000).copy(loginFileWritten = true, loginFileRemovedMs = 1_700_000_050_000L, worldSignal = false)
        val back = LoginJournal.parse(LoginJournal.serialize(listOf(e)))
        assertEquals(listOf(e), back)
    }

    @Test
    fun limitIsSixtyEntriesAndOldestFallAway() {
        var list = emptyList<JournalEntry>()
        for (i in 1..75) list = LoginJournal.append(list, entry(i.toLong()))
        assertEquals(60, list.size)
        assertEquals(entry(16).startMs, list.first().startMs)
        assertEquals(entry(75).startMs, list.last().startMs)
        val file = File(tmp.root, "anmeldejournal.txt")
        assertTrue(LoginJournal.save(file, list))
        assertEquals(60, LoginJournal.load(file).size)
        assertEquals(60, file.readLines().size)
        // Eine zu lange Eingabe wird beim Schreiben und beim Lesen auf 60 gekürzt.
        val tooMany = LoginJournal.serialize((1..80).map { entry(it.toLong()) })
        assertEquals(60, tooMany.lines().count { it.isNotEmpty() })
        assertEquals(60, LoginJournal.parse((1..80).joinToString("") { LoginJournal.serialize(listOf(entry(it.toLong()))) }).size)
    }

    @Test
    fun saveIsAtomicNoTempFileStaysAndTheOldFileIsReplacedWhole() {
        val file = File(tmp.root, "sub/anmeldejournal.txt")
        assertTrue(LoginJournal.save(file, listOf(entry(1))))
        assertTrue(LoginJournal.save(file, listOf(entry(1), entry(2))))
        assertEquals(listOf("anmeldejournal.txt"), file.parentFile!!.list()!!.toList())
        assertEquals(2, LoginJournal.load(file).size)
    }

    @Test
    fun saveFailureReturnsFalseAndLeavesTheOldFile() {
        val file = File(tmp.root, "anmeldejournal.txt")
        assertTrue(LoginJournal.save(file, listOf(entry(1))))
        val blocked = File(tmp.root, "blocker").apply { writeText("x") }
        // Das übergeordnete "Verzeichnis" ist eine Datei: Schreiben geht nicht.
        assertFalse(LoginJournal.save(File(blocked, "anmeldejournal.txt"), listOf(entry(2))))
        assertEquals(1, LoginJournal.load(file).size)
    }

    @Test
    fun brokenFileStartsOverAndIsReplacedOnNextSave() {
        val file = File(tmp.root, "anmeldejournal.txt")
        file.writeBytes(byteArrayOf(0, 1, 2, 0, -1, -2, 65, 0))
        assertTrue(LoginJournal.load(file).isEmpty())
        file.writeText("this is not a journal\nstart=abc v=x\n\u0000garbage")
        assertTrue(LoginJournal.load(file).isEmpty())
        val fresh = LoginJournal.append(LoginJournal.load(file), entry(1))
        assertTrue(LoginJournal.save(file, fresh))
        assertEquals(1, LoginJournal.load(file).size)
        assertFalse(file.readText().contains("garbage"))
    }

    @Test
    fun validLinesSurviveBetweenBrokenOnes() {
        val one = LoginJournal.serialize(listOf(entry(1))).trim()
        val two = LoginJournal.serialize(listOf(entry(2))).trim()
        val mixed = "$one\nrubbish line\n$two\nstart=12 v=\n"
        assertEquals(2, LoginJournal.parse(mixed).size)
    }

    @Test
    fun missingFileIsEmpty() {
        assertTrue(LoginJournal.load(File(tmp.root, "none.txt")).isEmpty())
    }

    @Test
    fun valuesCannotInjectFieldsOrCarryAnEmailLikeText() {
        val bad = entry(1).copy(appVersion = "2.3 level=self removed=5", idFingerprint = "a" + "@" + "b.de\nstart=1")
        val text = LoginJournal.serialize(listOf(bad))
        assertEquals(1, text.lines().count { it.isNotEmpty() })
        assertFalse(text.contains("@"))
        val back = LoginJournal.parse(text).single()
        assertEquals("fill", back.level)
        assertNull(back.loginFileRemovedMs)
    }

    @Test
    fun endKindsFollowTheExitReasons() {
        assertEquals("game_exit", LoginJournal.endKind("processes_exited"))
        assertEquals("game_exit", LoginJournal.endKind("guest_terminated"))
        assertEquals("hard_kill", LoginJournal.endKind("quick_menu"))
        assertEquals("hard_kill", LoginJournal.endKind("force_close"))
        assertEquals("app_background", LoginJournal.endKind("activity_destroyed"))
        assertEquals("other_new_reason", LoginJournal.endKind("new reason"))
    }

    @Test
    fun secondsSinceLastEnd() {
        assertEquals(640L, LoginJournal.sinceLastEnd(entry(1, endMs = 1_000_000L), 1_640_000L))
        assertNull(LoginJournal.sinceLastEnd(entry(1, endMs = null), 1_640_000L))
        assertNull(LoginJournal.sinceLastEnd(null, 1_640_000L))
        assertEquals(0L, LoginJournal.sinceLastEnd(entry(1, endMs = 2_000_000L), 1_000_000L))
    }

    @Test
    fun readableReportShowsTheShortIdsAndNothingAboutCredentials() {
        val text = LoginJournal.report(listOf(entry(1, endMs = 1_700_003_700_000L)))
        assertTrue(text.contains("login.txt not written"))
        assertTrue(text.contains("fill button tapped +12s (done)"))
        assertTrue(text.contains("world reached +83s"))
        assertTrue(text.contains("end game_exit"))
        assertTrue(text.contains("net wifi, vpn no, ipv6 yes"))
        assertTrue(text.contains("windows id ab12cd34, system view ef56ab78"))
        assertTrue(text.contains("since last end 640s"))
        assertNotNull(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""").find(text))
        assertTrue(LoginJournal.report(emptyList()).contains("no game start recorded yet"))
        val lines = text.lines().filter { it.startsWith("20") }
        assertEquals(1, lines.size)
        for (word in listOf("mail", "password", "ssid", "@")) assertFalse(word, lines.single().lowercase().contains(word))
    }

    @Test
    fun readableReportSaysWhenTheWorldWasNotWatched() {
        val e = entry(1).copy(worldMs = null, worldSignal = false, endKind = null)
        val line = LoginJournal.readableLine(e)
        assertTrue(line.contains("world not detected (detection off)"))
        assertTrue(line.contains("end unknown"))
    }

    @Test
    fun networkKindPrefersWifiThenCellular() {
        assertEquals("none", LoginJournalRecorder.networkKind(false, true, true))
        assertEquals("wifi", LoginJournalRecorder.networkKind(true, true, true))
        assertEquals("cellular", LoginJournalRecorder.networkKind(true, false, true))
        assertEquals("other", LoginJournalRecorder.networkKind(true, false, false))
    }
}

package app.gamenative.utils

import app.gamenative.backup.BackupAllowlist
import app.gamenative.utils.MachineIdentityLog.Entry
import app.gamenative.utils.MachineIdentityLog.Event
import app.gamenative.utils.MachineIdentityStore.Outcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/** Neue Kennung auf Wunsch, Ablage früherer Kennungen, Zurückholen, Sperrfrist (24 Stunden). */
class MachineIdentityRequestTest {
    private lateinit var dir: File
    private var clock = 1_800_000_000L
    private var seed = 100

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("machine-identity-request-test").toFile()
        File(dir, "game").mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val identityFile get() = File(dir, "files/" + MachineIdentity.FILE_NAME)
    private val logFile get() = File(dir, "files/" + MachineIdentityLog.LOG_FILE_NAME)
    private val archiveFile get() = File(dir, "files/" + MachineIdentityArchive.FILE_NAME)
    private val historyFile get() = File(dir, "game/" + MachineIdentityLog.HISTORY_FILE_NAME)

    private fun store() = MachineIdentityStore(
        identityFile = identityFile,
        logFile = logFile,
        historyFile = { historyFile },
        versionCode = 10,
        clock = { clock },
        random = { Random(seed++) },
    )

    private fun gen(n: Int) = MachineIdentity.generate(Random(n), 1_800_000_000L)

    private fun item(n: Int, from: Long = 10, until: Long = 20) = MachineIdentityArchive.Item(gen(n), from, until)

    private val hours = 3600L

    // --- Ablage (rein) ---

    @Test
    fun archiveKeepsAtMostFiveAndDropsTheOldest() {
        var items = emptyList<MachineIdentityArchive.Item>()
        for (n in 1..7) items = MachineIdentityArchive.push(items, item(n))
        assertEquals(5, items.size)
        assertEquals(listOf(7, 6, 5, 4, 3).map { gen(it) }, items.map { it.identity })
    }

    @Test
    fun archiveNeverHoldsTheSameIdentityTwice() {
        var items = emptyList<MachineIdentityArchive.Item>()
        for (n in 1..3) items = MachineIdentityArchive.push(items, item(n))
        items = MachineIdentityArchive.push(items, item(1, from = 50, until = 60))
        assertEquals(listOf(1, 3, 2).map { gen(it) }, items.map { it.identity })
        assertEquals(50L, items.first().from)
    }

    @Test
    fun swapExchangesActiveAndArchivedEntry() {
        val items = listOf(item(1), item(2), item(3))
        val swapped = MachineIdentityArchive.swap(items, 1, gen(9), activeFrom = 100, now = 200)!!
        assertEquals(gen(2), swapped.active)
        assertEquals(listOf(9, 1, 3).map { gen(it) }, swapped.items.map { it.identity })
        assertEquals(100L, swapped.items.first().from)
        assertEquals(200L, swapped.items.first().until)
        assertNull(MachineIdentityArchive.swap(items, 3, gen(9), 100, 200))
        assertNull(MachineIdentityArchive.swap(items, -1, gen(9), 100, 200))
    }

    @Test
    fun swapAtFullArchiveLosesNothing() {
        val items = (1..5).map { item(it) }
        val swapped = MachineIdentityArchive.swap(items, 4, gen(9), 100, 200)!!
        assertEquals(5, swapped.items.size)
        assertTrue(swapped.items.any { it.identity == gen(9) })
        assertFalse(swapped.items.any { it.identity == swapped.active })
    }

    @Test
    fun archiveTextRoundTripAndGarbageIsDropped() {
        val items = (1..3).map { item(it, from = it * 10L, until = it * 10L + 5) }
        assertEquals(items, MachineIdentityArchive.parse(MachineIdentityArchive.serialize(items)))
        val text = MachineIdentityArchive.serialize(items) + "\nmüll=1\n\nmachineGuid=kaputt\nusedFrom=1\nusedUntil=2\n"
        assertEquals(items, MachineIdentityArchive.parse(text))
        assertEquals(emptyList<MachineIdentityArchive.Item>(), MachineIdentityArchive.parse(null))
        assertEquals(emptyList<MachineIdentityArchive.Item>(), MachineIdentityArchive.parse(""))
    }

    @Test
    fun archiveInfosCarryNoIdentity() {
        val info = MachineIdentityArchive.infos(listOf(item(1, 5, 6))).single()
        assertEquals(MachineIdentityLog.fingerprint(gen(1)), info.fingerprint)
        assertEquals(5L, info.from)
        assertEquals(6L, info.until)
        assertFalse(info.toString().contains(gen(1).machineGuid))
    }

    // --- Sperrfrist (rein) ---

    private fun entry(time: Long, event: Event) = Entry(time, "0123abcd", 10, event, "")

    @Test
    fun lockLastsExactlyTwentyFourHoursAfterTheLastNewIdentity() {
        val log = listOf(entry(1000, Event.LOADED), entry(2000, Event.NEW_ON_REQUEST))
        assertEquals(2000 + 24 * hours, MachineIdentityLog.regenerateLockedUntil(log, 2000))
        assertEquals(2000 + 24 * hours, MachineIdentityLog.regenerateLockedUntil(log, 2000 + 24 * hours - 1))
        assertNull(MachineIdentityLog.regenerateLockedUntil(log, 2000 + 24 * hours))
    }

    @Test
    fun lockIsAbsentWithoutANewIdentityOnRequest() {
        assertNull(MachineIdentityLog.regenerateLockedUntil(emptyList(), 5000))
        assertNull(MachineIdentityLog.regenerateLockedUntil(listOf(entry(4000, Event.CREATED), entry(4001, Event.LOADED)), 5000))
    }

    @Test
    fun restoreNeitherLocksNorResetsTheLock() {
        val log = listOf(entry(2000, Event.NEW_ON_REQUEST), entry(3000, Event.RESTORED))
        assertEquals(2000 + 24 * hours, MachineIdentityLog.regenerateLockedUntil(log, 4000))
        assertNull(MachineIdentityLog.regenerateLockedUntil(listOf(entry(3000, Event.RESTORED)), 4000))
        // nach Ablauf der Frist hebt auch ein spaeteres "wiederhergestellt" nichts auf und sperrt nichts
        assertNull(MachineIdentityLog.regenerateLockedUntil(log, 2000 + 24 * hours + 5))
    }

    @Test
    fun lockIgnoresAnEntryFromTheFutureWhenTheClockWasSetBack() {
        assertNull(MachineIdentityLog.regenerateLockedUntil(listOf(entry(9000, Event.NEW_ON_REQUEST)), 1000))
    }

    @Test
    fun newEventsParseAndHistoryKeepsOnlyTheOldShortId() {
        val request = Entry(5, "11112222", 10, Event.NEW_ON_REQUEST, "alt:aaaabbbb")
        val restored = Entry(6, "33334444", 10, Event.RESTORED, "alt:11112222")
        val other = Entry(7, "33334444", 10, Event.PREFIX_MISMATCH, "vor_anwenden:machineGuid")
        val full = MachineIdentityLog.parse(MachineIdentityLog.serialize(listOf(request, restored, other)))
        assertEquals(listOf(request, restored, other), full)
        val history = MachineIdentityLog.parse(MachineIdentityLog.serialize(listOf(request, restored, other), withDetail = false))
        assertEquals(listOf("alt:aaaabbbb", "alt:11112222", ""), history.map { it.detail })
        // ein Detail, das keine Kurzkennung ist, kommt auch bei diesen Ereignissen nie in die Verlaufsdatei
        val odd = request.copy(detail = "irgendwas")
        assertEquals("", MachineIdentityLog.forHistory(odd).detail)
    }

    // --- Speicher ---

    @Test
    fun noIdentityYetNothingToChange() {
        val store = store()
        assertNull(store.peek())
        assertEquals(Outcome.NothingToChange, store.regenerate(gameRunning = false))
        assertFalse(archiveFile.exists())
    }

    @Test
    fun regenerateReplacesTheIdentityArchivesTheOldOneAndLogsBothFingerprints() {
        val store = store()
        val old = store.loadOrCreate()
        val oldFp = MachineIdentityLog.fingerprint(old)
        clock += 5 * hours
        val done = store.regenerate(gameRunning = false) as Outcome.Done

        val now = store.peek()!!
        assertNotEquals(old, now)
        assertEquals(now, store.loadOrCreate())
        assertEquals(MachineIdentityLog.fingerprint(now), done.fingerprint)
        // gespeichert
        assertEquals(now, MachineIdentity.load(identityFile))
        // alte Kennung in der Ablage, mit Zeitraum
        val infos = store.archiveInfos()
        assertEquals(listOf(oldFp), infos.map { it.fingerprint })
        assertEquals(1_800_000_000L, infos.single().from)
        assertEquals(clock, infos.single().until)
        assertEquals(old, MachineIdentityArchive.parse(archiveFile.readText()).single().identity)
        // Ereignis in beiden Verlaeufen: neue Kurzkennung, alte im Detail
        for (list in listOf(store.readLog(), store.readHistory())) {
            val last = list.last()
            assertEquals(Event.NEW_ON_REQUEST, last.event)
            assertEquals(done.fingerprint, last.fingerprint)
            assertEquals("alt:$oldFp", last.detail)
        }
    }

    @Test
    fun regenerateIsRefusedWhileTheGameRunsAndChangesNothing() {
        val store = store()
        val old = store.loadOrCreate()
        assertEquals(Outcome.GameRunning, store.regenerate(gameRunning = true))
        assertEquals(old, store.peek())
        assertFalse(archiveFile.exists())
        assertEquals(listOf(Event.CREATED), store.readLog().map { it.event })
    }

    @Test
    fun secondNewIdentityWithinTwentyFourHoursIsLockedUntilTheTimeFromTheLog() {
        val store = store()
        store.loadOrCreate()
        assertNull(store.regenerateLockedUntil())
        clock += hours
        assertTrue(store.regenerate(false) is Outcome.Done)
        val requestTime = clock
        assertEquals(requestTime + 24 * hours, store.regenerateLockedUntil())

        clock += 23 * hours
        val locked = store.regenerate(false) as Outcome.Locked
        assertEquals(requestTime + 24 * hours, locked.until)
        assertEquals(1, store.archiveInfos().size)

        clock = requestTime + 24 * hours
        assertNull(store.regenerateLockedUntil())
        assertTrue(store.regenerate(false) is Outcome.Done)
        assertEquals(2, store.archiveInfos().size)
    }

    @Test
    fun lockSurvivesAReinstallBecauseTheHistoryFileInTheGameFolderRemembersIt() {
        val store = store()
        store.loadOrCreate()
        clock += hours
        assertTrue(store.regenerate(false) is Outcome.Done)
        val until = clock + 24 * hours
        // Neuinstallation: App-Speicher weg, Spielordner bleibt
        File(dir, "files").deleteRecursively()
        clock += hours
        val fresh = store()
        fresh.loadOrCreate()
        assertEquals(until, fresh.regenerateLockedUntil())
    }

    @Test
    fun failedSaveKeepsTheOldIdentityAndTheArchiveUntouched() {
        val store = store()
        val old = store.loadOrCreate()
        // das temporaere Ziel von save() ist ein Ordner: Schreiben scheitert
        assertTrue(File(identityFile.parentFile, MachineIdentity.FILE_NAME + ".tmp").mkdirs())
        clock += hours
        assertEquals(Outcome.SaveFailed, store.regenerate(false))
        assertEquals(old, store.peek())
        assertEquals(old, MachineIdentity.load(identityFile))
        assertEquals(emptyList<MachineIdentityArchive.Info>(), store.archiveInfos())
        assertEquals(listOf(Event.CREATED), store.readLog().map { it.event })
        assertNull(store.regenerateLockedUntil())
    }

    @Test
    fun restoreSwapsActiveAndArchivedAndLogsBothFingerprints() {
        val store = store()
        val first = store.loadOrCreate()
        clock += hours
        assertTrue(store.regenerate(false) is Outcome.Done)
        val second = store.peek()!!
        clock += 2 * hours
        val done = store.restore(0, gameRunning = false) as Outcome.Done

        assertEquals(first, store.peek())
        assertEquals(first, MachineIdentity.load(identityFile))
        assertEquals(MachineIdentityLog.fingerprint(first), done.fingerprint)
        // die bis eben aktive Kennung steht jetzt in der Ablage, die zurueckgeholte nicht mehr
        assertEquals(listOf(MachineIdentityLog.fingerprint(second)), store.archiveInfos().map { it.fingerprint })
        assertEquals(second, MachineIdentityArchive.parse(archiveFile.readText()).single().identity)
        for (list in listOf(store.readLog(), store.readHistory())) {
            val last = list.last()
            assertEquals(Event.RESTORED, last.event)
            assertEquals(done.fingerprint, last.fingerprint)
            assertEquals("alt:" + MachineIdentityLog.fingerprint(second), last.detail)
        }
    }

    @Test
    fun restoreIsRefusedWhileTheGameRunsAndForAnUnknownEntry() {
        val store = store()
        val first = store.loadOrCreate()
        clock += hours
        assertTrue(store.regenerate(false) is Outcome.Done)
        val second = store.peek()!!
        assertEquals(Outcome.GameRunning, store.restore(0, gameRunning = true))
        assertEquals(Outcome.NothingToChange, store.restore(1, gameRunning = false))
        assertEquals(Outcome.NothingToChange, store.restore(-1, gameRunning = false))
        assertEquals(second, store.peek())
        assertEquals(listOf(MachineIdentityLog.fingerprint(first)), store.archiveInfos().map { it.fingerprint })
    }

    @Test
    fun restoreLeavesTheTwentyFourHourLockAlone() {
        val store = store()
        store.loadOrCreate()
        clock += hours
        assertTrue(store.regenerate(false) is Outcome.Done)
        val until = store.regenerateLockedUntil()
        assertEquals(clock + 24 * hours, until)

        // zurueckholen ist trotz Sperre erlaubt, auch mehrmals hintereinander, und aendert die Sperre nicht
        clock += hours
        assertTrue(store.restore(0, false) is Outcome.Done)
        assertEquals(until, store.regenerateLockedUntil())
        clock += hours
        assertTrue(store.restore(0, false) is Outcome.Done)
        assertEquals(until, store.regenerateLockedUntil())
        assertTrue(store.regenerate(false) is Outcome.Locked)
    }

    @Test
    fun archiveIsCappedAtFiveOverManyNewIdentitiesAndHoldsNoDuplicate() {
        val store = store()
        store.loadOrCreate()
        val fingerprints = ArrayList<String>()
        fingerprints += MachineIdentityLog.fingerprint(store.peek()!!)
        for (i in 1..7) {
            clock += 25 * hours
            assertTrue(store.regenerate(false) is Outcome.Done)
            if (i < 7) fingerprints += MachineIdentityLog.fingerprint(store.peek()!!)
        }
        val shown = store.archiveInfos().map { it.fingerprint }
        assertEquals(5, shown.size)
        assertEquals(shown.distinct(), shown)
        // neueste zuerst; die zwei aeltesten sind herausgefallen
        assertEquals(fingerprints.takeLast(5).reversed(), shown)
    }

    @Test
    fun theSameIdentityDoesNotLandTwiceInTheArchiveWhenSwappedBackAndForth() {
        val store = store()
        val first = store.loadOrCreate()
        clock += hours
        assertTrue(store.regenerate(false) is Outcome.Done)
        repeat(3) {
            clock += hours
            assertTrue(store.restore(0, false) is Outcome.Done)
        }
        // nach drei Tauschen ist die zweite Kennung aktiv, die erste in der Ablage: je genau einmal
        assertEquals(1, store.archiveInfos().size)
        assertNotEquals(store.peek(), MachineIdentityArchive.parse(archiveFile.readText()).single().identity)
        assertTrue(first == store.peek() || first == MachineIdentityArchive.parse(archiveFile.readText()).single().identity)
    }

    @Test
    fun thePeriodOfAnArchivedIdentityComesFromTheLog() {
        val store = store()
        store.loadOrCreate()
        clock += 10 * hours
        assertTrue(store.regenerate(false) is Outcome.Done)
        val secondSince = clock
        clock += 30 * hours
        assertTrue(store.regenerate(false) is Outcome.Done)
        val infos = store.archiveInfos()
        assertEquals(2, infos.size)
        // neueste zuerst: die zweite Kennung war von der Erzeugung (secondSince) bis jetzt in Benutzung
        assertEquals(secondSince, infos[0].from)
        assertEquals(clock, infos[0].until)
        assertEquals(1_800_000_000L, infos[1].from)
        assertEquals(secondSince, infos[1].until)
    }

    // --- Klartext ---

    @Test
    fun clearValuesAreOnlyInTheIdentityFileAndTheArchive() {
        val store = store()
        val first = store.loadOrCreate()
        clock += hours
        assertTrue(store.regenerate(false) is Outcome.Done)
        val second = store.peek()!!
        clock += hours
        assertTrue(store.restore(0, false) is Outcome.Done)
        store.recordPrefixMismatch(second, "vor_anwenden", listOf("machineGuid"))

        val summary = store.summary()
        val shown = listOf(
            logFile.readText(),
            historyFile.readText(),
            MachineIdentityLog.historyText(summary.trace),
            summary.toString(),
            store.archiveInfos().toString(),
            MachineIdentityArchive.infos(MachineIdentityArchive.parse(archiveFile.readText())).toString(),
        ).joinToString("\n")
        for (identity in listOf(first, second)) {
            for (secret in listOf(identity.machineGuid, identity.computerName, identity.volumeSerial, identity.productId)) {
                assertFalse("Klartext in einer Ausgabe: " + secret.take(4), shown.contains(secret))
            }
        }
        // die Ablage selbst enthaelt die Kennung (nur sie, nur im App-Speicher)
        assertTrue(archiveFile.readText().contains(second.machineGuid))
        assertEquals(MachineIdentityLog.HISTORY_FILE_NAME, historyFile.name)
    }

    @Test
    fun archiveIsNotPartOfTheBackup() {
        assertTrue(BackupAllowlist.FORBIDDEN_FILES.any { MachineIdentityArchive.FILE_NAME.startsWith(it) })
        for (spec in BackupAllowlist.ALL) {
            assertFalse(spec.toString(), spec.file.contains(MachineIdentityArchive.FILE_NAME) || spec.key.contains(MachineIdentityArchive.FILE_NAME))
        }
    }
}

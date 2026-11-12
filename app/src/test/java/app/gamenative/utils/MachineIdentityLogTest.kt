package app.gamenative.utils

import app.gamenative.backup.BackupAllowlist
import app.gamenative.utils.MachineIdentityLog.Entry
import app.gamenative.utils.MachineIdentityLog.Event
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

class MachineIdentityLogTest {
    private lateinit var dir: File
    private var now = 1_800_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("machine-identity-log-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun gen(seed: Int) = MachineIdentity.generate(Random(seed), 1_800_000_000L)

    private fun entry(fp: String, event: Event = Event.LOADED, version: Int = 10, time: Long = now++, detail: String = "") =
        Entry(time, fp, version, event, detail)

    private class Env(val dir: File, val version: Int = 10, val seed: Int = 1) {
        var clock = 1_800_000_000L
        val identityFile = File(dir, "files/" + MachineIdentity.FILE_NAME)
        val logFile = File(dir, "files/" + MachineIdentityLog.LOG_FILE_NAME)
        val historyDir = File(dir, "game")
        val historyFile = File(historyDir, MachineIdentityLog.HISTORY_FILE_NAME)
        var seedCounter = seed

        /** Without the second copy (kennungslog4) a damaged file can only be repaired or replaced; the copy has its own tests. */
        fun dropCopy() = File(identityFile.absoluteFile.parentFile, MachineIdentityStore.COPY_DIR_NAME).deleteRecursively()

        fun store(version: Int = this.version, history: Boolean = true) = MachineIdentityStore(
            identityFile = identityFile,
            logFile = logFile,
            historyFile = { if (history) historyFile else null },
            versionCode = version,
            clock = { clock++ },
            random = { Random(seedCounter++) },
        )
    }

    private fun env(version: Int = 10): Env = Env(dir, version).also { it.historyDir.mkdirs() }

    // --- Kurzkennung ---

    @Test
    fun fingerprintIsEightLowerCaseHexAndStable() {
        val id = gen(1)
        val fp = MachineIdentityLog.fingerprint(id)
        assertTrue(fp, Regex("^[0-9a-f]{8}$").matches(fp))
        assertEquals(fp, MachineIdentityLog.fingerprint(MachineIdentity.parse(id.serialize())!!))
        assertEquals(fp, MachineIdentityLog.fingerprint(gen(1)))
    }

    @Test
    fun fingerprintChangesWithAnyValueAndWithANewIdentity() {
        val id = gen(1)
        val fp = MachineIdentityLog.fingerprint(id)
        assertNotEquals(fp, MachineIdentityLog.fingerprint(gen(2)))
        assertNotEquals(fp, MachineIdentityLog.fingerprint(MachineIdentity(id.machineGuid, id.computerName, id.volumeSerial, id.productId, id.installDate + 1)))
        assertNotEquals(fp, MachineIdentityLog.fingerprint(MachineIdentity(id.machineGuid, "DESKTOP-ABCDEFG", id.volumeSerial, id.productId, id.installDate)))
        assertFalse(id.serialize().contains(fp))
    }

    // --- Protokolllogik ---

    @Test
    fun loadedIsOnlyLoggedWhenFingerprintOrVersionChanged() {
        assertTrue(MachineIdentityLog.shouldLogLoaded(emptyList(), "aaaaaaaa", 10))
        val log = listOf(entry("aaaaaaaa", Event.CREATED, 10))
        assertFalse(MachineIdentityLog.shouldLogLoaded(log, "aaaaaaaa", 10))
        assertTrue(MachineIdentityLog.shouldLogLoaded(log, "bbbbbbbb", 10))
        assertTrue(MachineIdentityLog.shouldLogLoaded(log, "aaaaaaaa", 11))
        // Zwischeneintrag mit gleicher Kurzkennung und Version aendert nichts
        val withMismatch = log + entry("aaaaaaaa", Event.PREFIX_MISMATCH, 10, detail = "vor_anwenden:hostname")
        assertFalse(MachineIdentityLog.shouldLogLoaded(withMismatch, "aaaaaaaa", 10))
    }

    @Test
    fun logIsCappedAtFiftyAndTheOldestFallAway() {
        var log = emptyList<Entry>()
        for (i in 0 until 60) log = MachineIdentityLog.append(log, entry("%08x".format(i), Event.LOADED, time = 1000L + i))
        assertEquals(MachineIdentityLog.MAX_ENTRIES, log.size)
        assertEquals(1010L, log.first().time)
        assertEquals(1059L, log.last().time)
        // auch beim Einlesen einer zu langen Datei
        val text = MachineIdentityLog.serialize((0 until 70).map { entry("%08x".format(it), time = 2000L + it) })
        assertEquals(50, MachineIdentityLog.parse(text).size)
        assertEquals(2020L, MachineIdentityLog.parse(text).first().time)
    }

    @Test
    fun repeatedMessagesDoNotFillTheLog() {
        val first = entry("aaaaaaaa", Event.PREFIX_MISMATCH, detail = "vor_anwenden:hostname")
        val log = MachineIdentityLog.append(emptyList(), first)
        assertSame(log, MachineIdentityLog.append(log, first.copy(time = first.time + 500)))
        assertEquals(2, MachineIdentityLog.append(log, first.copy(detail = "vor_anwenden:machineGuid")).size)
    }

    @Test
    fun lineFormatRoundTripsAndSkipsGarbage() {
        val entries = listOf(
            entry("0123abcd", Event.CREATED_INVALID, 7, 1_800_000_001L, "feld_ungueltig:machineGuid"),
            entry("0123abcd", Event.LOADED, 8, 1_800_000_002L),
        )
        val text = MachineIdentityLog.serialize(entries) + "kaputt\n\t\t\t\n1\tXYZ\t1\tgeladen\n1\t0123abcd\t1\tunbekannt\n"
        assertEquals(entries, MachineIdentityLog.parse(text))
        assertTrue(MachineIdentityLog.parse(null).isEmpty())
    }

    @Test
    fun detailKeepsOnlyNameCharacters() {
        val dirty = "vor\tanwenden:a b\nc=1234\\x"
        val clean = MachineIdentityLog.cleanDetail(dirty)
        assertFalse(clean.contains('\t') || clean.contains('\n') || clean.contains(' ') || clean.contains('=') || clean.contains('\\'))
        assertEquals(120, MachineIdentityLog.cleanDetail("x".repeat(200)).length)
    }

    @Test
    fun summaryTellsSinceChangesAndPrevious() {
        val log = listOf(
            entry("aaaaaaaa", Event.CREATED, time = 100),
            entry("aaaaaaaa", Event.PREFIX_MISMATCH, time = 150),
            entry("bbbbbbbb", Event.CREATED, time = 200),
            entry("bbbbbbbb", Event.LOADED, 11, time = 300),
        )
        val s = MachineIdentityLog.summarize(log, emptyList())
        assertEquals("bbbbbbbb", s.fingerprint)
        assertEquals(200L, s.since)
        assertEquals(1, s.changes)
        assertEquals("aaaaaaaa", s.previous)
        assertFalse(s.saveFailed)
        assertEquals(log, s.recent)

        val steady = MachineIdentityLog.summarize(log.take(2), emptyList())
        assertEquals(100L, steady.since)
        assertEquals(0, steady.changes)
        assertNull(steady.previous)

        val empty = MachineIdentityLog.summarize(emptyList(), emptyList())
        assertNull(empty.fingerprint)
        assertNull(empty.since)
        assertTrue(empty.recent.isEmpty())
    }

    @Test
    fun summaryShowsAtMostFiveEvents() {
        val log = (0 until 9).map { entry("aaaaaaaa", Event.PREFIX_MISMATCH, detail = "d$it") }
        assertEquals(5, MachineIdentityLog.summarize(log, emptyList()).recent.size)
    }

    @Test
    fun historyTextListsOnlyShortIdsAndEvents() {
        val text = MachineIdentityLog.historyText(listOf(entry("0123abcd", Event.CREATED, 9, 1_800_000_000L)))
        assertTrue(text, text.contains("0123abcd") && text.contains("v9") && text.contains("erzeugt") && text.contains("2027-01-15"))
        assertEquals("(no entries)\n", MachineIdentityLog.historyText(emptyList()))
    }

    // --- Ablauf mit Dateien ---

    @Test
    fun firstStartCreatesOnceAndLaterStartsLogNothingNew() {
        val env = env()
        val first = env.store().loadOrCreate()
        val second = env.store().loadOrCreate()
        assertEquals(first, second)
        val log = env.store().readLog()
        assertEquals(listOf(Event.CREATED), log.map { it.event })
        assertEquals(MachineIdentityLog.fingerprint(first), log.single().fingerprint)
        assertEquals(10, log.single().versionCode)
        assertEquals(log, env.store().readHistory())

        // neue App-Version: genau ein "geladen"
        env.store(version = 11).loadOrCreate()
        env.store(version = 11).loadOrCreate()
        assertEquals(listOf(Event.CREATED, Event.LOADED), env.store().readLog().map { it.event })
        assertEquals(11, env.store().readLog().last().versionCode)
    }

    @Test
    fun existingIdentityWithoutLogGetsOneLoadedBaseline() {
        val env = env()
        env.identityFile.parentFile.mkdirs()
        MachineIdentity.save(env.identityFile, gen(5))
        env.store().loadOrCreate()
        env.store().loadOrCreate()
        assertEquals(listOf(Event.LOADED), env.store().readLog().map { it.event })
    }

    @Test
    fun invalidFileIsReplacedAndLoggedWithTheReasonButNoValue() {
        val env = env()
        val original = env.store().loadOrCreate()
        env.dropCopy()
        val broken = original.serialize().replace(original.machineGuid, "not-a-guid-1234")
        env.identityFile.writeText(broken)
        // Only the damaged field is made new (kennungslog4): the other four stay.
        val replaced = env.store().loadOrCreate()
        assertNotEquals(original, replaced)
        assertEquals(original.computerName, replaced.computerName)
        val last = env.store().readLog().last()
        assertEquals(Event.REPAIRED, last.event)
        assertEquals("machineGuid", last.detail)
        assertEquals(MachineIdentityLog.fingerprint(replaced), last.fingerprint)
        assertFalse(env.logFile.readText().contains("not-a-guid"))

        // Nothing readable: only then a complete new identity, with the reason.
        env.dropCopy()
        env.identityFile.writeText("junk=1\n")
        env.store().loadOrCreate()
        val created = env.store().readLog().last()
        assertEquals(Event.CREATED_INVALID, created.event)
        assertEquals("feld_fehlt:machineGuid", created.detail)
    }

    @Test
    fun failedSaveIsLoggedAndTheSameIdentityIsRetriedWhileTheProcessLives() {
        val env = env()
        // Unter einer Datei laesst sich kein Ordner anlegen: save() scheitert
        val blocker = File(dir, "files")
        blocker.writeText("im weg")
        val store = env.store()
        val first = store.loadOrCreate()
        assertEquals(listOf(Event.CREATED, Event.SAVE_FAILED), store.readHistory().map { it.event })
        assertTrue(store.summary().saveFailed)

        // naechster Start im selben Prozess, Speichern geht weiter nicht: dieselbe Kennung, kein neuer "erzeugt"
        assertEquals(first, store.loadOrCreate())
        assertEquals(first, store.loadOrCreate())
        assertEquals(listOf(Event.CREATED, Event.SAVE_FAILED), store.readHistory().map { it.event })

        // Hindernis weg: dieselbe Kennung wird gespeichert
        assertTrue(blocker.delete())
        assertEquals(first, store.loadOrCreate())
        // das Protokoll im App-Speicher war bis hierher nicht schreibbar (derselbe Ordner), die Verlaufsdatei im Spielordner schon
        assertEquals(listOf(Event.CREATED, Event.SAVE_FAILED, Event.SAVED_LATER), store.readHistory().map { it.event })
        assertEquals(listOf(Event.SAVED_LATER), store.readLog().map { it.event })
        assertFalse(store.summary().saveFailed)
        assertEquals(first, MachineIdentity.load(env.identityFile))
        // und ein spaeterer Start (neuer Prozess) findet sie
        assertEquals(first, env.store().loadOrCreate())
        assertEquals(3, env.store().readHistory().size)
    }

    @Test
    fun withoutRetryStateEveryProcessStartWouldMakeANewIdentityAndThatIsVisible() {
        val env = env()
        File(dir, "files").writeText("im weg")
        val a = env.store().loadOrCreate()
        val b = env.store().loadOrCreate() // neuer Prozess = neues Store-Objekt
        assertNotEquals(a, b)
        val summary = env.store().summary()
        assertTrue(summary.saveFailed)
        assertEquals(1, summary.changes)
        assertEquals(MachineIdentityLog.fingerprint(b), summary.fingerprint)
        assertEquals(MachineIdentityLog.fingerprint(a), summary.previous)
    }

    @Test
    fun historySurvivesAReinstallAndShowsTheChange() {
        val env = env()
        val before = env.store().loadOrCreate()
        // Neuinstallation: App-Speicher leer, Spielordner bleibt
        File(dir, "files").deleteRecursively()
        val after = env.store().loadOrCreate()
        assertNotEquals(before, after)
        val summary = env.store().summary()
        assertEquals(MachineIdentityLog.fingerprint(after), summary.fingerprint)
        assertEquals(MachineIdentityLog.fingerprint(before), summary.previous)
        assertEquals(1, summary.changes)
        assertEquals(2, summary.trace.size)
        assertEquals(1, env.store().readLog().size)
    }

    @Test
    fun historyFileHoldsOnlyTimeFingerprintVersionAndEvent() {
        val env = env()
        val id = env.store().loadOrCreate()
        env.dropCopy()
        env.identityFile.writeText("machineGuid=kaputt\n")
        env.store().loadOrCreate()
        val lines = env.historyFile.readLines()
        assertEquals(2, lines.size)
        for (line in lines) assertEquals(line, 4, line.split('\t').size)
        assertTrue(lines.last().endsWith("erzeugt_ungueltig"))
        assertFalse(env.historyFile.readText().contains("feld_"))
        // das Protokoll im App-Speicher hat das Detail
        assertTrue(env.logFile.readText().contains("feld_fehlt:computerName"))
        assertTrue(id.machineGuid.isNotEmpty())
    }

    @Test
    fun missingGameFolderIsLeftOutSilently() {
        val env = env()
        env.historyDir.deleteRecursively()
        val id = env.store().loadOrCreate()
        assertEquals(id, env.store().loadOrCreate())
        assertFalse(env.historyDir.exists())
        assertEquals(1, env.store().readLog().size)
        val none = env.store(history = false)
        none.loadOrCreate()
        assertTrue(none.readHistory().isEmpty())
    }

    // --- Abgleich mit dem Prefix ---

    private fun prefix(): File {
        val wine = File(dir, "wine")
        wine.mkdirs()
        val text = checkNotNull(javaClass.getResourceAsStream("/machineidentity/system_excerpt.reg")) { "Testressource fehlt" }
            .use { String(it.readBytes(), Charsets.UTF_8) }
        File(wine, "system.reg").writeText(text)
        return wine
    }

    @Test
    fun prefixWithTheSharedDefaultsDeviatesInEveryField() {
        val wine = prefix()
        val id = gen(7)
        assertEquals(
            listOf("machineGuid", "computerName", "hostname", "installDate", "productId", "volumeSerial"),
            MachineIdentityApply.deviations(wine, id),
        )
    }

    @Test
    fun appliedPrefixHasNoDeviationAndEachChangeIsNamed() {
        val wine = prefix()
        val id = gen(8)
        MachineIdentityApply.applyToPrefix(wine, id)
        assertTrue(MachineIdentityApply.deviations(wine, id).isEmpty())

        val reg = File(wine, "system.reg")
        reg.writeText(reg.readText(Charsets.ISO_8859_1).replace(id.computerName, "DESKTOP-ZZZZZZZ"), Charsets.ISO_8859_1)
        assertEquals(listOf("computerName", "hostname"), MachineIdentityApply.deviations(wine, id))
        MachineIdentityApply.applyToPrefix(wine, id)
        File(wine, MachineIdentityApply.SERIAL_FILE).writeText("DEADBEEF\n")
        assertEquals(listOf("volumeSerial"), MachineIdentityApply.deviations(wine, id))
        // Gross-/Kleinschreibung der Seriennummer ist egal
        File(wine, MachineIdentityApply.SERIAL_FILE).writeText(id.volumeSerial.lowercase() + "\n")
        assertTrue(MachineIdentityApply.deviations(wine, id).isEmpty())
    }

    @Test
    fun missingKeysAndMissingRegistryAreNotCountedAsDeviation() {
        val wine = File(dir, "wine2")
        wine.mkdirs()
        val id = gen(9)
        File(wine, "system.reg").writeText("WINE REGISTRY Version 2\n\n[Software\\\\Other] 1\n\"a\"=\"b\"\n")
        File(wine, MachineIdentityApply.SERIAL_FILE).also { it.parentFile.mkdirs() }.writeText(id.volumeSerial)
        assertTrue(MachineIdentityApply.deviations(wine, id).isEmpty())
        File(wine, "system.reg").delete()
        assertTrue(MachineIdentityApply.deviations(wine, id).isEmpty())
    }

    @Test
    fun prefixMismatchIsLoggedWithFieldNamesOnly() {
        val env = env()
        val store = env.store()
        val id = store.loadOrCreate()
        store.recordPrefixMismatch(id, "vor_anwenden", emptyList())
        assertEquals(1, store.readLog().size)

        val wine = prefix()
        val fields = MachineIdentityApply.deviations(wine, id)
        store.recordPrefixMismatch(id, "vor_anwenden", fields)
        store.recordPrefixMismatch(id, "vor_anwenden", fields) // Wiederholung
        val last = store.readLog().last()
        assertEquals(2, store.readLog().size)
        assertEquals(Event.PREFIX_MISMATCH, last.event)
        assertEquals("vor_anwenden:machineGuid,computerName,hostname,installDate,productId,volumeSerial", last.detail)
        assertEquals(MachineIdentityLog.fingerprint(id), last.fingerprint)
        // Verlaufsdatei: Ereignis ohne Detail
        assertTrue(env.historyFile.readLines().last().endsWith("prefix_abweichung"))
    }

    // --- Klartext nie in Protokoll, Verlauf, Bericht ---

    @Test
    fun noIdentityValueEverReachesLogHistoryOrReportText() {
        val env = env()
        val store = env.store()
        val id = store.loadOrCreate()
        store.recordPrefixMismatch(id, "vor_anwenden", MachineIdentityApply.deviations(prefix(), id))
        env.dropCopy()
        env.identityFile.writeText("junk")
        val replaced = store.loadOrCreate()
        val summary = store.summary()
        val texts = listOf(
            env.logFile.readText(),
            env.historyFile.readText(),
            MachineIdentityLog.historyText(summary.trace),
            MachineIdentityLog.historyText(summary.recent),
            summary.toString(),
        )
        val values = listOf(id, replaced).flatMap {
            listOf(it.machineGuid, it.computerName, it.volumeSerial, it.productId, it.installDate.toString())
        }
        for (text in texts) for (value in values) assertFalse("$value steht im Text", text.contains(value, ignoreCase = true))
        assertEquals(2, store.readHistory().map { it.fingerprint }.distinct().size)
    }

    @Test
    fun logAndHistoryAreNotPartOfTheBackup() {
        val names = listOf(MachineIdentityLog.LOG_FILE_NAME, MachineIdentityLog.HISTORY_FILE_NAME, MachineIdentity.FILE_NAME)
        for (spec in BackupAllowlist.ALL) {
            for (name in names) {
                assertFalse(spec.toString(), spec.file.contains(name) || spec.key.contains(name))
            }
            assertFalse(spec.toString(), spec.file.contains("identity", ignoreCase = true) || spec.key.contains("identity", ignoreCase = true))
        }
    }
}

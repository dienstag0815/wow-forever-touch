package app.gamenative.utils

import app.gamenative.backup.BackupAllowlist
import app.gamenative.utils.MachineIdentityLog.Entry
import app.gamenative.utils.MachineIdentityLog.Event
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.random.Random

/**
 * Die Kennung übersteht jedes App-Update (Auftrag kennungslog4): eingefrorene Musterdateien, Reparatur einzelner Felder, Zweitkopie,
 * atomares Schreiben, Update-Zeile.
 *
 * Die Musterdateien unter `resources/machineidentity/frozen/` sind der Stand, der je ausgeliefert wurde (das Format hat sich seit der
 * ersten Fassung vom 08.11.2026 nicht geändert). WIRD EIN TEST HIER ROT, BEKÄMEN ALLE SPIELER BEIM UPDATE EINE NEUE KENNUNG:
 * dann nicht die Erwartung anpassen, sondern die Änderung am Dateiformat oder an der Prüfung (`isValid`) zurücknehmen.
 */
class MachineIdentityUpdateSafetyTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("machine-identity-update-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/machineidentity/frozen/$name")) { "Testressource fehlt: $name" }
            .use { String(it.readBytes(), Charsets.UTF_8) }

    private class Env(val dir: File) {
        var clock = 1_800_000_000L
        var seed = 1
        val identityFile = File(dir, "files/" + MachineIdentity.FILE_NAME)
        val copyFile = File(dir, "nobackup/" + MachineIdentity.FILE_NAME)
        val logFile = File(dir, "files/" + MachineIdentityLog.LOG_FILE_NAME)
        val damagedFile = File(dir, "files/" + MachineIdentityStore.DAMAGED_FILE_NAME)
        val historyDir = File(dir, "game")
        val historyFile = File(historyDir, MachineIdentityLog.HISTORY_FILE_NAME)

        init {
            historyDir.mkdirs()
        }

        fun store(version: Int = 10) = MachineIdentityStore(
            identityFile = identityFile,
            logFile = logFile,
            historyFile = { historyFile },
            versionCode = version,
            clock = { clock++ },
            random = { Random(seed++) },
            copyFile = copyFile,
        )
    }

    private fun env() = Env(dir)

    private fun events(store: MachineIdentityStore) = store.readLog().map { it.event }

    // --- 2a Eingefrorene Musterdateien ---------------------------------------------------------------------------------------

    private val first = MachineIdentity("5c2f9a1e-7b3d-4e6f-8a21-9d0c4b7e1f35", "DESKTOP-K7M3QX2", "A1B2C3D4", "00330-81234-56789-AAOEM", 1794096000L)
    private val firstFingerprint = "d7d55a00"
    private val logged = MachineIdentity("0e8d6b42-3a91-4c70-b5e8-12f4a96c3d07", "DESKTOP-9ZT4LW8", "0F1E2D3C", "00330-80042-00817-AAOEM", 1794441600L)
    private val loggedFingerprint = "2212c440"

    @Test
    fun frozenSampleOfTheFirstVersionLoadsWithAFixedFingerprint() {
        val file = File(dir, "first.properties").also { it.writeText(resource("v1_2026-11-08_first.properties")) }
        val inspection = MachineIdentity.inspect(file)
        assertTrue(inspection is MachineIdentity.Inspection.Valid)
        val identity = (inspection as MachineIdentity.Inspection.Valid).identity
        assertEquals(first, identity)
        assertEquals(firstFingerprint, MachineIdentityLog.fingerprint(identity))
    }

    @Test
    fun frozenSampleOfTheLoggedVersionLoadsWithAFixedFingerprint() {
        val file = File(dir, "logged.properties").also { it.writeText(resource("v1_2026-11-12_logged.properties")) }
        val identity = checkNotNull(MachineIdentity.load(file))
        assertEquals(logged, identity)
        assertEquals(loggedFingerprint, MachineIdentityLog.fingerprint(identity))
    }

    @Test
    fun theCodeStillWritesExactlyTheFrozenFormat() {
        assertEquals(resource("v1_2026-11-08_first.properties"), first.serialize())
        assertEquals(resource("v1_2026-11-12_logged.properties"), logged.serialize())
    }

    @Test
    fun frozenVariantWithCrlfSpacesOtherOrderAndUnknownKeysGivesTheSameFingerprint() {
        val text = resource("v1_hand_edited_variant.properties")
        assertTrue(text.contains("\r\n"))
        val identity = checkNotNull(MachineIdentity.parse(text))
        assertEquals(first, identity)
        assertEquals(firstFingerprint, MachineIdentityLog.fingerprint(identity))
    }

    @Test
    fun storeKeepsAFrozenFileUntouchedAndMakesNoNewIdentity() {
        val env = env()
        env.identityFile.parentFile.mkdirs()
        val text = resource("v1_2026-11-08_first.properties")
        env.identityFile.writeText(text)
        val store = env.store()
        val identity = store.loadOrCreate()
        assertEquals(first, identity)
        assertEquals(text, env.identityFile.readText())
        assertEquals(listOf(Event.LOADED), events(store))
        assertEquals(firstFingerprint, store.summary().fingerprint)
        // The second copy appears at this first start with the new version.
        assertEquals(first, MachineIdentity.load(env.copyFile))
    }

    // --- 2b Reparatur einzelner Felder ---------------------------------------------------------------------------------------

    private fun textWith(key: String, value: String?): String {
        val values = linkedMapOf(
            "machineGuid" to first.machineGuid,
            "computerName" to first.computerName,
            "volumeSerial" to first.volumeSerial,
            "productId" to first.productId,
            "installDate" to first.installDate.toString(),
        )
        if (value == null) values.remove(key) else values[key] = value
        return values.entries.joinToString("") { it.key + "=" + it.value + "\n" }
    }

    @Test
    fun repairKeepsEveryValidFieldAndRenewsOnlyTheDamagedOne() {
        val cases = mapOf(
            "machineGuid" to "not-a-guid",
            "computerName" to "desktop-k7m3qx2",
            "volumeSerial" to "00000000",
            "productId" to "00330-5-1-AAOEM",
            "installDate" to "12",
        )
        for ((key, bad) in cases) {
            val repair = checkNotNull(MachineIdentity.repair(textWith(key, bad), Random(5), 1_800_000_000L)) { key }
            assertEquals(key, listOf(key), repair.fields)
            assertTrue(key, repair.identity.isValid())
            val kept = MachineIdentity.FIELD_NAMES.filter { it != key }
            val old = MachineIdentity.readValues(first.serialize())
            val now = MachineIdentity.readValues(repair.identity.serialize())
            for (field in kept) assertEquals("$key/$field", old[field], now[field])
            assertNotEquals(key, old[key], now[key])
        }
    }

    @Test
    fun repairRenewsAMissingFieldAndSeveralDamagedOnes() {
        val missing = checkNotNull(MachineIdentity.repair(textWith("productId", null), Random(1), 1_800_000_000L))
        assertEquals(listOf("productId"), missing.fields)
        val severalText = textWith("machineGuid", "x").replace("installDate=${first.installDate}", "installDate=abc")
        val several = checkNotNull(MachineIdentity.repair(severalText, Random(2), 1_800_000_000L))
        assertEquals(listOf("machineGuid", "installDate"), several.fields)
        assertEquals(first.computerName, several.identity.computerName)
        assertEquals(1_800_000_000L, several.identity.installDate)
    }

    @Test
    fun repairGivesNothingWhenNothingIsReadableOrNothingNeedsRepair() {
        assertNull(MachineIdentity.repair(null))
        assertNull(MachineIdentity.repair("garbage"))
        assertNull(MachineIdentity.repair(""))
        assertNull(MachineIdentity.repair("machineGuid=1\ncomputerName=2\nvolumeSerial=3\nproductId=4\ninstallDate=5\n"))
        assertNull(MachineIdentity.repair(first.serialize()))
    }

    @Test
    fun storeRepairsAPartlyDamagedFileInsteadOfReplacingItAndKeepsTheOldText() {
        val env = env()
        env.identityFile.parentFile.mkdirs()
        val damaged = textWith("productId", "00330-8x-AAOEM")
        env.identityFile.writeText(damaged)
        val store = env.store()
        val identity = store.loadOrCreate()
        assertEquals(first.machineGuid, identity.machineGuid)
        assertEquals(first.computerName, identity.computerName)
        assertEquals(first.volumeSerial, identity.volumeSerial)
        assertEquals(first.installDate, identity.installDate)
        assertNotEquals(first.productId, identity.productId)
        assertTrue(identity.isValid())
        assertEquals(identity, MachineIdentity.load(env.identityFile))
        assertEquals(identity, MachineIdentity.load(env.copyFile))
        val log = store.readLog()
        assertEquals(listOf(Event.REPAIRED), log.map { it.event })
        assertEquals("productId", log.single().detail)
        assertTrue(env.damagedFile.readText().contains(damaged.trim()))
        // The next start has nothing to repair and logs nothing new.
        assertEquals(identity, env.store().loadOrCreate())
        assertEquals(listOf(Event.REPAIRED), env.store().readLog().map { it.event })
        // The history in the game folder carries the field names but no value.
        val history = env.historyFile.readText()
        assertTrue(history.contains("repariert\tproductId"))
        assertFalse(history.contains(first.machineGuid))
        assertFalse(env.logFile.readText().contains(first.machineGuid))
    }

    @Test
    fun storeCreatesANewIdentityOnlyWhenNothingIsReadableAndKeepsTheOldText() {
        val env = env()
        env.identityFile.parentFile.mkdirs()
        env.identityFile.writeText("garbage=1\n")
        val store = env.store()
        val identity = store.loadOrCreate()
        assertTrue(identity.isValid())
        val log = store.readLog()
        assertEquals(listOf(Event.CREATED_INVALID), log.map { it.event })
        assertTrue(env.damagedFile.readText().contains("garbage=1"))
    }

    @Test
    fun aStricterCheckInALaterVersionRepairsInsteadOfStartingOver() {
        val env = env()
        env.identityFile.parentFile.mkdirs()
        // A later version could refuse this value; here it is a name the check refuses today.
        env.identityFile.writeText(textWith("computerName", "DESKTOP-ab"))
        val identity = env.store().loadOrCreate()
        assertEquals(first.machineGuid, identity.machineGuid)
        assertEquals(first.volumeSerial, identity.volumeSerial)
        assertNotEquals(first.computerName, identity.computerName)
    }

    @Test
    fun theDamagedTextIsLimitedToTheLastThree() {
        val env = env()
        env.identityFile.parentFile.mkdirs()
        val store = env.store()
        repeat(5) { i ->
            env.identityFile.writeText("garbage=$i\n")
            env.copyFile.delete()
            store.loadOrCreate()
            // Break the file again on the next round.
        }
        val blocks = env.damagedFile.readText().split("\n---\n").filter { it.isNotBlank() }
        assertTrue(blocks.size <= 3)
        assertTrue(blocks.first().contains("garbage=4"))
    }

    // --- 2c Zweitkopie ---------------------------------------------------------------------------------------------------------

    @Test
    fun theCopyIsWrittenWithTheFirstSaveInAnotherFolder() {
        val env = env()
        val identity = env.store().loadOrCreate()
        assertEquals(identity, MachineIdentity.load(env.identityFile))
        assertEquals(identity, MachineIdentity.load(env.copyFile))
        assertNotEquals(env.identityFile.parentFile.canonicalPath, env.copyFile.parentFile.canonicalPath)
        assertFalse(env.copyFile.canonicalPath.startsWith(env.historyDir.canonicalPath))
    }

    @Test
    fun aMissingMainFileIsRestoredFromTheCopy() {
        val env = env()
        val identity = env.store().loadOrCreate()
        assertTrue(env.identityFile.delete())
        val store = env.store()
        assertEquals(identity, store.loadOrCreate())
        assertEquals(identity, MachineIdentity.load(env.identityFile))
        val log = store.readLog()
        assertEquals(listOf(Event.CREATED, Event.FROM_COPY), log.map { it.event })
        assertEquals("haupt_fehlt", log.last().detail)
        assertEquals(log.first().fingerprint, log.last().fingerprint)
        assertEquals(0, store.summary().changes)
    }

    @Test
    fun anUnusableMainFileIsReplacedByTheCopyAndItsTextIsKept() {
        val env = env()
        val identity = env.store().loadOrCreate()
        env.identityFile.writeText("zerbrochen\n")
        val store = env.store()
        assertEquals(identity, store.loadOrCreate())
        assertEquals(identity, MachineIdentity.load(env.identityFile))
        assertEquals("haupt_ungueltig", store.readLog().last().detail)
        assertEquals(Event.FROM_COPY, store.readLog().last().event)
        assertTrue(env.damagedFile.readText().contains("zerbrochen"))
    }

    @Test
    fun whenBothAreValidButDifferTheMainFileWinsAndTheCopyFollows() {
        val env = env()
        env.store().loadOrCreate()
        env.copyFile.writeText(logged.serialize())
        env.identityFile.writeText(first.serialize())
        val store = env.store()
        assertEquals(first, store.loadOrCreate())
        assertEquals(first, MachineIdentity.load(env.identityFile))
        assertEquals(first, MachineIdentity.load(env.copyFile))
        assertFalse(events(store).contains(Event.FROM_COPY))
    }

    @Test
    fun aBrokenOrMissingCopyIsRewrittenFromAValidMainFile() {
        val env = env()
        val identity = env.store().loadOrCreate()
        env.copyFile.writeText("kaputt")
        env.store().loadOrCreate()
        assertEquals(identity, MachineIdentity.load(env.copyFile))
        env.copyFile.delete()
        env.store().loadOrCreate()
        assertEquals(identity, MachineIdentity.load(env.copyFile))
    }

    @Test
    fun peekFindsTheIdentityInTheCopyWhenTheMainFileIsGone() {
        val env = env()
        val identity = env.store().loadOrCreate()
        env.identityFile.delete()
        assertEquals(identity, env.store().peek())
    }

    @Test
    fun regenerateAndRestoreKeepTheCopyInSync() {
        val env = env()
        val store = env.store()
        val old = store.loadOrCreate()
        assertTrue(store.regenerate(false) is MachineIdentityStore.Outcome.Done)
        val now = checkNotNull(MachineIdentity.load(env.identityFile))
        assertNotEquals(old, now)
        assertEquals(now, MachineIdentity.load(env.copyFile))
        assertTrue(store.restore(0, false) is MachineIdentityStore.Outcome.Done)
        assertEquals(old, MachineIdentity.load(env.identityFile))
        assertEquals(old, MachineIdentity.load(env.copyFile))
    }

    @Test
    fun aFailedSwapLeavesMainAndCopyAtTheActiveIdentity() {
        val env = env()
        val store = env.store()
        val old = store.loadOrCreate()
        // The main path cannot be replaced: a folder with content sits where the file would go.
        env.identityFile.delete()
        env.identityFile.mkdirs()
        File(env.identityFile, "blocker").writeText("x")
        assertTrue(store.regenerate(false) is MachineIdentityStore.Outcome.SaveFailed)
        assertEquals(old, MachineIdentity.load(env.copyFile))
        assertEquals(old, store.peek())
    }

    @Test
    fun whenNothingExistsANewIdentityIsMadeAndTheCopyWrittenEvenIfTheMainFileCannotBeSaved() {
        val env = env()
        env.identityFile.mkdirs()
        File(env.identityFile, "blocker").writeText("x")
        val store = env.store()
        val identity = store.loadOrCreate()
        assertEquals(identity, MachineIdentity.load(env.copyFile))
        assertEquals(identity, store.loadOrCreate())
        assertTrue(events(store).contains(Event.SAVE_FAILED))
    }

    @Test
    fun theCopyAndTheDamagedTextAreNeverInABackupALogOrAHistory() {
        val env = env()
        val store = env.store()
        val identity = store.loadOrCreate()
        env.identityFile.writeText("kaputt")
        store.loadOrCreate()
        for (file in listOf(env.logFile, env.historyFile)) {
            val text = file.readText()
            for (value in listOf(identity.machineGuid, identity.computerName, identity.volumeSerial, identity.productId)) {
                assertFalse(file.name + " " + value, text.contains(value))
            }
        }
        for (name in listOf(MachineIdentityStore.DAMAGED_FILE_NAME, PrefixTraits.FILE_NAME, MachineIdentity.FILE_NAME)) {
            val base = name.removeSuffix(".txt").removeSuffix(".properties")
            assertTrue(name, BackupAllowlist.FORBIDDEN_FILES.any { base == it || base.startsWith(it) })
        }
    }

    // --- 2d Atomar schreiben ----------------------------------------------------------------------------------------------------

    @Test
    fun anAbortBetweenTempFileAndRenameLeavesTheOldFile() {
        val file = File(dir, "x/" + MachineIdentity.FILE_NAME)
        assertTrue(MachineIdentity.save(file, first))
        try {
            MachineIdentity.writeAtomic(file, logged.serialize().toByteArray()) { throw IOException("abort") }
            fail("expected the abort")
        } catch (e: IOException) {
            assertEquals("abort", e.message)
        }
        assertEquals(first, MachineIdentity.load(file))
        assertFalse(File(file.parentFile, file.name + ".tmp").exists())
        // An abort by a runtime error behaves the same.
        try {
            MachineIdentity.writeAtomic(file, logged.serialize().toByteArray()) { throw IllegalStateException("kill") }
            fail("expected the abort")
        } catch (e: IllegalStateException) {
            assertEquals(first, MachineIdentity.load(file))
        }
    }

    @Test
    fun aHalfWrittenTempFileFromAKilledAppIsNeverReadAndIsReplacedByTheNextSave() {
        val env = env()
        val store = env.store()
        val identity = store.loadOrCreate()
        File(env.identityFile.parentFile, env.identityFile.name + ".tmp").writeText("machineGuid=halb")
        File(env.copyFile.parentFile, env.copyFile.name + ".tmp").writeText("computerName=halb")
        assertEquals(identity, env.store().loadOrCreate())
        assertEquals(identity, MachineIdentity.load(env.identityFile))
        assertTrue(MachineIdentity.save(env.identityFile, logged))
        assertEquals(logged, MachineIdentity.load(env.identityFile))
        assertFalse(File(env.identityFile.parentFile, env.identityFile.name + ".tmp").exists())
    }

    @Test
    fun saveWritesTheCompleteFileAndReplacesAnOldOneInOneStep() {
        val file = File(dir, "y/" + MachineIdentity.FILE_NAME)
        assertTrue(MachineIdentity.save(file, first))
        assertTrue(MachineIdentity.save(file, logged))
        assertEquals(logged.serialize(), file.readText())
        assertEquals(listOf(MachineIdentity.FILE_NAME), file.parentFile.list()!!.toList())
    }

    // --- 3 Update-Zeile --------------------------------------------------------------------------------------------------------

    private var t = 1_800_000_000L

    private fun entry(fp: String, version: Int, event: Event = Event.LOADED) = Entry(t++, fp, version, event)

    @Test
    fun noVersionChangeInTheHistoryGivesNoUpdateLine() {
        assertNull(MachineIdentityLog.updateChange(emptyList()))
        assertNull(MachineIdentityLog.updateChange(listOf(entry("aaaaaaaa", 299))))
        assertNull(MachineIdentityLog.updateChange(listOf(entry("aaaaaaaa", 299, Event.CREATED), entry("aaaaaaaa", 299, Event.PREFIX_MISMATCH))))
    }

    @Test
    fun theSameShortIdBeforeAndAfterTheUpdateIsUnchanged() {
        val trace = listOf(entry("aaaaaaaa", 299, Event.CREATED), entry("aaaaaaaa", 300))
        val update = checkNotNull(MachineIdentityLog.updateChange(trace))
        assertEquals(299, update.from)
        assertEquals(300, update.to)
        assertTrue(update.unchanged)
        assertEquals(trace[1].time, update.at)
        assertTrue(MachineIdentityLog.updateText(update).startsWith("299 -> 300: ID unchanged ("))
    }

    @Test
    fun aDifferentShortIdAfterTheUpdateIsReportedAsChanged() {
        val trace = listOf(entry("aaaaaaaa", 299, Event.CREATED), entry("bbbbbbbb", 300, Event.CREATED))
        val update = checkNotNull(MachineIdentityLog.updateChange(trace))
        assertFalse(update.unchanged)
        assertTrue(MachineIdentityLog.updateText(update).contains("ID CHANGED"))
    }

    @Test
    fun onlyTheLastVersionChangeCountsAndADowngradeIsReportedToo() {
        val trace = listOf(
            entry("aaaaaaaa", 298, Event.CREATED),
            entry("aaaaaaaa", 299),
            entry("bbbbbbbb", 299, Event.NEW_ON_REQUEST),
            entry("bbbbbbbb", 300),
            entry("bbbbbbbb", 300, Event.SYSTEM_CHANGED),
        )
        val update = checkNotNull(MachineIdentityLog.updateChange(trace))
        assertEquals(299, update.from)
        assertEquals(300, update.to)
        assertTrue(update.unchanged)
        val down = checkNotNull(MachineIdentityLog.updateChange(listOf(entry("aaaaaaaa", 300, Event.CREATED), entry("aaaaaaaa", 299))))
        assertEquals(300, down.from)
        assertEquals(299, down.to)
    }

    @Test
    fun aNewIdentityTheUserRequestedRightAfterTheUpdateIsNotBlamedOnTheUpdate() {
        // The first entry with the new version is the player's own "new ID": the update itself cannot be judged yet.
        val onlyRequest = listOf(entry("aaaaaaaa", 299, Event.CREATED), entry("bbbbbbbb", 300, Event.NEW_ON_REQUEST))
        assertNull(MachineIdentityLog.updateChange(onlyRequest))
        // Also later: the player's request sits between the last start of the old version and the first of the new one.
        assertNull(MachineIdentityLog.updateChange(onlyRequest + entry("bbbbbbbb", 300, Event.LOADED)))
    }

    @Test
    fun theSummaryCarriesTheUpdateAndWithoutHistoryNone() {
        val trace = listOf(entry("aaaaaaaa", 299, Event.CREATED), entry("aaaaaaaa", 300))
        assertEquals(299, MachineIdentityLog.summarize(trace, emptyList()).update!!.from)
        assertNull(MachineIdentityLog.summarize(emptyList(), emptyList()).update)
        assertEquals("-", MachineIdentityLog.updateText(null))
    }

    @Test
    fun anUpdateOverTheSameFilesKeepsTheIdentityAndShowsItInTheStore() {
        val env = env()
        val old = env.store(299).loadOrCreate()
        env.store(299).loadOrCreate()
        val store = env.store(300)
        assertEquals(old, store.loadOrCreate())
        val update = checkNotNull(store.summary().update)
        assertEquals(299, update.from)
        assertEquals(300, update.to)
        assertTrue(update.unchanged)
        // The same in the history file in the game folder after an app reinstall (no log, only the history).
        env.logFile.delete()
        val after = checkNotNull(env.store(300).summary().update)
        assertTrue(after.unchanged)
    }

    @Test
    fun anUpdateThatLosesBothFilesIsShownAsAChange() {
        val env = env()
        env.store(299).loadOrCreate()
        env.identityFile.delete()
        env.copyFile.delete()
        val store = env.store(300)
        store.loadOrCreate()
        val update = checkNotNull(store.summary().update)
        assertEquals(299, update.from)
        assertEquals(300, update.to)
        assertFalse(update.unchanged)
    }

    @Test
    fun theNewEventsHaveTheirOwnIdsAndSafeHistoryDetails() {
        assertEquals(Event.REPAIRED, Event.fromId("repariert"))
        assertEquals(Event.FROM_COPY, Event.fromId("aus_zweitkopie"))
        assertEquals(Event.PREFIX_TRAITS_CAPTURED, Event.fromId("prefix_merkmale_erfasst"))
        assertEquals(Event.PREFIX_TRAITS_CHANGED, Event.fromId("prefix_merkmale_geaendert"))
        fun hist(event: Event, detail: String) = MachineIdentityLog.forHistory(Entry(1, "aaaaaaaa", 1, event, detail)).detail
        assertEquals("productId,installDate", hist(Event.REPAIRED, "productId,installDate"))
        assertEquals("", hist(Event.REPAIRED, "productId,0123"))
        assertEquals("", hist(Event.REPAIRED, "5c2f9a1e-7b3d"))
        assertEquals("haupt_fehlt", hist(Event.FROM_COPY, "haupt_fehlt"))
        assertEquals("", hist(Event.FROM_COPY, "DESKTOP-K7M3QX2"))
        assertEquals("pm:0123abcd:hwprofil", hist(Event.PREFIX_TRAITS_CHANGED, "pm:0123abcd:hwprofil"))
        assertEquals("", hist(Event.PREFIX_TRAITS_CHANGED, "pm:{3b1f6a52}"))
        assertNotNull(Event.values().firstOrNull { it.isMeasurement })
    }
}

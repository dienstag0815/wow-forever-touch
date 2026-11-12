package app.gamenative.utils

import app.gamenative.backup.BackupAllowlist
import app.gamenative.report.ReportRedactor
import app.gamenative.utils.MachineIdentityLog.Event
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/**
 * Kennung übersteht eine Neuinstallation: Kopie im Spielordner (Format, Prüfsumme, Gerätemerkmal), Übernahme ohne App-Speicher-Kennung,
 * anderes Modell → Ablage, unbenutzte neue Kennung verfällt / benutzte bleibt, Schalter, Zugriff fehlt, Backup und Berichte ohne die Datei.
 */
class MachineIdentityKeepTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("machine-identity-keep-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val thisModel = MachineIdentityKeep.deviceTrait("Acme", "Phone 1")
    private val otherModel = MachineIdentityKeep.deviceTrait("Acme", "Phone 2")

    private class Env(val dir: File, val trait: String) {
        var clock = 1_800_000_000L
        var seed = 1
        var gameDir = File(dir, "game")
        val filesDir = File(dir, "files")
        val noBackupDir = File(dir, "nobackup")
        val identityFile get() = File(filesDir, MachineIdentity.FILE_NAME)
        val copyFile get() = File(noBackupDir, MachineIdentity.FILE_NAME)
        val logFile get() = File(filesDir, MachineIdentityLog.LOG_FILE_NAME)
        val archiveFile get() = File(filesDir, MachineIdentityArchive.FILE_NAME)
        val usedFile get() = File(filesDir, MachineIdentityKeep.USED_FILE_NAME)
        val offFile get() = File(filesDir, MachineIdentityKeep.OFF_FILE_NAME)
        val historyFile get() = File(gameDir, MachineIdentityLog.HISTORY_FILE_NAME)
        val keepFile get() = File(gameDir, MachineIdentityKeep.FILE_NAME)

        init {
            gameDir.mkdirs()
        }

        fun store(version: Int = 10, deviceTrait: String = trait) = MachineIdentityStore(
            identityFile = identityFile,
            logFile = logFile,
            historyFile = { historyFile },
            versionCode = version,
            clock = { clock++ },
            random = { Random(seed++) },
            copyFile = copyFile,
            keepFile = { keepFile },
            deviceTrait = deviceTrait,
        )

        /** Neuinstallation: der App-Speicher ist weg, der Spielordner bleibt. */
        fun reinstall() {
            filesDir.deleteRecursively()
            noBackupDir.deleteRecursively()
        }

        fun events(store: MachineIdentityStore) = store.readLog().map { it.event }
    }

    private fun env(trait: String = thisModel) = Env(dir, trait)

    private fun foreign(n: Int = 777) = MachineIdentity.generate(Random(n), 1_800_000_000L)

    private fun fp(identity: MachineIdentity) = MachineIdentityLog.fingerprint(identity)

    private fun writeCopy(env: Env, identity: MachineIdentity, trait: String) {
        env.gameDir.mkdirs()
        env.keepFile.writeText(MachineIdentityKeep.serialize(identity, trait))
    }

    private fun readCopy(env: Env): MachineIdentityKeep.Reading = MachineIdentityKeep.parse(env.keepFile.readText())

    private fun validCopy(env: Env) = readCopy(env) as MachineIdentityKeep.Reading.Valid

    // --- Format, Prüfsumme, Gerätemerkmal ------------------------------------------------------------------------------------

    @Test
    fun deviceTraitIsEightHexOfManufacturerAndModelOnly() {
        assertTrue(Regex("^[0-9a-f]{8}$").matches(thisModel))
        assertNotEquals(thisModel, otherModel)
        assertEquals(thisModel, MachineIdentityKeep.deviceTrait("  ACME ", "phone 1"))
        assertNotEquals(MachineIdentityKeep.deviceTrait("Acme", "Phone 1"), MachineIdentityKeep.deviceTrait("AcmePhone", " 1"))
    }

    @Test
    fun copyWritesAndReadsBack() {
        val id = foreign()
        val reading = MachineIdentityKeep.parse(MachineIdentityKeep.serialize(id, thisModel))
        assertTrue(reading is MachineIdentityKeep.Reading.Valid)
        reading as MachineIdentityKeep.Reading.Valid
        assertEquals(id, reading.identity)
        assertEquals(thisModel, reading.trait)
    }

    @Test
    fun copyHoldsOnlyTheIdentityTheTraitAndTheChecksum() {
        val text = MachineIdentityKeep.serialize(foreign(), thisModel)
        val keys = text.lines().filter { it.contains('=') }.map { it.substringBefore('=') }
        assertEquals(listOf("machineGuid", "computerName", "volumeSerial", "productId", "installDate", "device", "check"), keys)
        assertFalse(text.contains("Acme"))
        assertFalse(text.contains("Phone"))
    }

    @Test
    fun aWrongChecksumIsRejected() {
        val text = MachineIdentityKeep.serialize(foreign(), thisModel)
        val bad = text.replace(Regex("check=[0-9a-f]"), "check=" + if (text.contains("check=0")) "1" else "0")
        assertEquals("pruefsumme", (MachineIdentityKeep.parse(bad) as MachineIdentityKeep.Reading.Invalid).reason)
    }

    @Test
    fun anEditedValueOrTraitBreaksTheChecksum() {
        val id = foreign()
        val text = MachineIdentityKeep.serialize(id, thisModel)
        val otherName = text.replace(id.computerName, "DESKTOP-AAAAAAA")
        assertEquals("pruefsumme", (MachineIdentityKeep.parse(otherName) as MachineIdentityKeep.Reading.Invalid).reason)
        val otherTrait = text.replace("device=$thisModel", "device=$otherModel")
        assertEquals("pruefsumme", (MachineIdentityKeep.parse(otherTrait) as MachineIdentityKeep.Reading.Invalid).reason)
    }

    @Test
    fun brokenCopiesAreRejectedWithAReasonAndNoValue() {
        val good = MachineIdentityKeep.serialize(foreign(), thisModel)
        fun reason(text: String?) = (MachineIdentityKeep.parse(text) as MachineIdentityKeep.Reading.Invalid).reason
        assertEquals("lesefehler", reason(null))
        assertEquals("kopf", reason(""))
        assertEquals("kopf", reason(good.replace("wowforever-kennung-kopie 1", "etwas anderes")))
        assertEquals("kennung_ungueltig", reason(good.replace(Regex("machineGuid=.*"), "machineGuid=kaputt")))
        assertEquals("geraet", reason(good.replace(Regex("device=.*"), "device=xyz")))
        assertEquals("pruefsumme", reason(good.replace(Regex("check=.*"), "")))
        assertEquals("kopf", reason(good.take(10)))
        for (r in listOf("lesefehler", "kopf", "kennung_ungueltig", "geraet", "pruefsumme")) assertTrue(r in MachineIdentityKeep.REASONS)
    }

    @Test
    fun spacesAndLineEndingsDoNotMatterButTheContentDoes() {
        val id = foreign()
        val text = MachineIdentityKeep.serialize(id, thisModel).replace("\n", "\r\n").replace("=", " = ")
        assertTrue(MachineIdentityKeep.parse(text) is MachineIdentityKeep.Reading.Valid)
    }

    // --- Schreiben ---------------------------------------------------------------------------------------------------------------

    @Test
    fun theCopyIsWrittenWithEveryCreationAtomically() {
        val env = env()
        val store = env.store()
        val identity = store.loadOrCreate()
        assertEquals(identity, validCopy(env).identity)
        assertEquals(thisModel, validCopy(env).trait)
        assertEquals(listOf(MachineIdentityKeep.FILE_NAME, MachineIdentityLog.HISTORY_FILE_NAME).sorted(), env.gameDir.list()!!.sorted())
        assertEquals(MachineIdentityKeep.Status.MATCHES, store.keepStatus())
    }

    @Test
    fun theCopyFollowsNewOnRequestAndReuse() {
        val env = env()
        val store = env.store()
        val first = store.loadOrCreate()
        assertTrue(store.regenerate(false) is MachineIdentityStore.Outcome.Done)
        val second = validCopy(env).identity
        assertNotEquals(first, second)
        assertEquals(second, store.peek())
        assertTrue(store.restore(0, false) is MachineIdentityStore.Outcome.Done)
        assertEquals(first, validCopy(env).identity)
        assertEquals(first, store.peek())
    }

    @Test
    fun aLaterStartRepairsAMissingOrDamagedCopy() {
        val env = env()
        val identity = env.store().loadOrCreate()
        env.keepFile.delete()
        val again = env.store()
        assertEquals(identity, again.loadOrCreate())
        assertEquals(identity, validCopy(env).identity)
        env.keepFile.writeText("Mist")
        val third = env.store()
        assertEquals(identity, third.loadOrCreate())
        assertEquals(identity, validCopy(env).identity)
        assertTrue(Event.KEEP_IGNORED in env.events(third))
        assertEquals("kopf", env.store().readLog().last { it.event == Event.KEEP_IGNORED }.detail)
    }

    // --- Übernahme nach Neuinstallation ----------------------------------------------------------------------------------------

    @Test
    fun afterAReinstallTheCopyOfTheSameModelIsTakenOver() {
        val env = env()
        val identity = env.store().loadOrCreate()
        env.reinstall()
        assertFalse(env.identityFile.exists())
        val store = env.store(version = 11)
        val back = store.loadOrCreate()
        assertEquals(identity, back)
        assertEquals(identity, MachineIdentity.load(env.identityFile))
        assertEquals(identity, MachineIdentity.load(env.copyFile))
        val last = store.readLog().last()
        assertEquals(Event.FROM_FOLDER, last.event)
        assertEquals("keine_kennung", last.detail)
        assertFalse(Event.CREATED in env.events(store))
        // dieselbe Kurzkennung steht vor und nach der Neuinstallation in der Verlaufsdatei
        assertEquals(1, MachineIdentityLog.parse(env.historyFile.readText()).map { it.fingerprint }.distinct().size)
    }

    @Test
    fun theTakenOverIdentityIsUsedByTheNextStartsWithoutFurtherEvents() {
        val env = env()
        env.store().loadOrCreate()
        env.reinstall()
        env.store().loadOrCreate()
        val before = env.store().readLog().size
        val third = env.store()
        third.loadOrCreate()
        third.loadOrCreate()
        assertEquals(before, third.readLog().size)
    }

    @Test
    fun aCopyFromAnotherModelIsNotTakenOverButOffered() {
        val env = env()
        val other = foreign()
        writeCopy(env, other, otherModel)
        val store = env.store()
        val mine = store.loadOrCreate()
        assertNotEquals(other, mine)
        assertTrue(Event.CREATED in env.events(store))
        assertFalse(Event.FROM_FOLDER in env.events(store))
        val offered = store.archiveInfos()
        assertEquals(listOf(fp(other)), offered.map { it.fingerprint })
        val entry = store.readLog().last { it.event == Event.KEEP_ARCHIVED }
        assertEquals("anderes_modell", entry.detail)
        assertEquals(fp(mine), entry.fingerprint)
        // der eigene Stand ersetzt die fremde Kopie im Ordner
        assertEquals(mine, validCopy(env).identity)
    }

    @Test
    fun theOfferedForeignIdentityCanBeReusedWithTheExistingSwap() {
        val env = env()
        val other = foreign()
        writeCopy(env, other, otherModel)
        val store = env.store()
        val mine = store.loadOrCreate()
        assertTrue(store.restore(0, false) is MachineIdentityStore.Outcome.Done)
        assertEquals(other, store.peek())
        assertEquals(other, validCopy(env).identity)
        assertEquals(mine, MachineIdentityArchive.parse(env.archiveFile.readText()).single().identity)
    }

    @Test
    fun aDamagedCopyIsIgnoredWithAReasonAndANewIdentityIsMade() {
        val env = env()
        env.gameDir.mkdirs()
        env.keepFile.writeText(MachineIdentityKeep.serialize(foreign(), thisModel).replace(Regex("check=.*"), "check=0000000000000000"))
        val store = env.store()
        val mine = store.loadOrCreate()
        assertTrue(Event.CREATED in env.events(store))
        val ignored = store.readLog().last { it.event == Event.KEEP_IGNORED }
        assertEquals("pruefsumme", ignored.detail)
        assertEquals(fp(mine), ignored.fingerprint)
        assertEquals(mine, validCopy(env).identity)
    }

    @Test
    fun theCopyIsOnlyTakenWhenNothingIsInTheAppStorage() {
        val env = env()
        val mine = env.store().loadOrCreate()
        // gültige Hauptdatei: eine andere Kopie im Spielordner ersetzt sie nie, solange die Kennung benutzt ist
        env.store().markApplied(mine)
        val other = foreign()
        writeCopy(env, other, thisModel)
        val store = env.store()
        assertEquals(mine, store.loadOrCreate())
        assertEquals(mine, MachineIdentity.load(env.identityFile))
        assertEquals("neue_benutzt", store.readLog().last { it.event == Event.KEEP_ARCHIVED }.detail)
        assertEquals(listOf(fp(other)), store.archiveInfos().map { it.fingerprint })
        assertEquals(mine, validCopy(env).identity)
    }

    @Test
    fun theSecondCopyInTheAppStorageComesBeforeTheGameFolder() {
        val env = env()
        val first = env.store()
        val mine = first.loadOrCreate()
        first.markApplied(mine)
        env.identityFile.delete()
        writeCopy(env, foreign(), thisModel)
        val store = env.store()
        assertEquals(mine, store.loadOrCreate())
        assertTrue(Event.FROM_COPY in env.events(store))
        assertFalse(Event.FROM_FOLDER in env.events(store))
        assertEquals(mine, validCopy(env).identity)
    }

    @Test
    fun aPartlyDamagedFileIsRepairedBeforeTheFolderIsLookedAt() {
        val env = env()
        val mine = env.store().loadOrCreate()
        env.copyFile.delete()
        env.identityFile.writeText(mine.serialize().replace(Regex("computerName=.*"), "computerName=kaputt"))
        writeCopy(env, foreign(), thisModel)
        val store = env.store()
        val repaired = store.loadOrCreate()
        assertEquals(mine.machineGuid, repaired.machineGuid)
        assertTrue(Event.REPAIRED in env.events(store))
        assertFalse(Event.FROM_FOLDER in env.events(store))
    }

    // --- Reihenfolge: nach der Neuinstallation ist der Spielordner erst später lesbar ------------------------------------------

    @Test
    fun anUnusedNewIdentityLapsesWhenAValidCopyOfTheSameModelAppears() {
        val env = env()
        val original = foreign(5)
        // Neuinstallation, Spielordner noch nicht zugänglich
        env.gameDir = File(dir, "noch-nicht-da")
        val first = env.store().loadOrCreate()
        assertNotEquals(original, first)
        assertFalse(env.keepFile.exists())
        // Zugriff erteilt, die Kopie der früheren Installation wird sichtbar
        env.gameDir = File(dir, "game").also { it.mkdirs() }
        writeCopy(env, original, thisModel)
        val store = env.store()
        val now = store.loadOrCreate()
        assertEquals(original, now)
        assertEquals(original, MachineIdentity.load(env.identityFile))
        assertEquals(original, MachineIdentity.load(env.copyFile))
        val event = store.readLog().last { it.event == Event.FROM_FOLDER }
        assertEquals("verfallen:" + fp(first), event.detail)
        assertEquals(fp(original), event.fingerprint)
        // die unbenutzte neue Kennung kommt nicht in die Ablage
        assertTrue(store.archiveInfos().isEmpty())
        assertEquals(original, validCopy(env).identity)
    }

    @Test
    fun aNewIdentityThatWasAppliedStaysAndTheCopyIsOffered() {
        val env = env()
        val original = foreign(5)
        env.gameDir = File(dir, "noch-nicht-da")
        val first = env.store().loadOrCreate()
        env.store().markApplied(first)
        env.gameDir = File(dir, "game").also { it.mkdirs() }
        writeCopy(env, original, thisModel)
        val store = env.store()
        assertEquals(first, store.loadOrCreate())
        assertEquals(first, MachineIdentity.load(env.identityFile))
        assertFalse(Event.FROM_FOLDER in env.events(store))
        assertEquals(listOf(fp(original)), store.archiveInfos().map { it.fingerprint })
        assertEquals("neue_benutzt", store.readLog().last { it.event == Event.KEEP_ARCHIVED }.detail)
        assertEquals(first, validCopy(env).identity)
    }

    @Test
    fun anUnusedNewIdentityIsNotReplacedByACopyOfAnotherModel() {
        val env = env()
        env.gameDir = File(dir, "noch-nicht-da")
        val first = env.store().loadOrCreate()
        env.gameDir = File(dir, "game").also { it.mkdirs() }
        val other = foreign()
        writeCopy(env, other, otherModel)
        val store = env.store()
        assertEquals(first, store.loadOrCreate())
        assertEquals(listOf(fp(other)), store.archiveInfos().map { it.fingerprint })
        assertEquals("anderes_modell", store.readLog().last { it.event == Event.KEEP_ARCHIVED }.detail)
    }

    @Test
    fun anIdentityChosenByThePlayerNeverLapses() {
        val env = env()
        val store = env.store()
        store.loadOrCreate()
        assertTrue(store.regenerate(false) is MachineIdentityStore.Outcome.Done)
        val chosen = store.peek()!!
        writeCopy(env, foreign(), thisModel)
        assertEquals(chosen, env.store().loadOrCreate())
    }

    @Test
    fun anIdentityWithUnknownHistoryCountsAsUsed() {
        val env = env()
        val mine = env.store().loadOrCreate()
        // Stand vor diesem Update: es gibt weder die Liste der angewendeten Kennungen noch eine Kopie im Spielordner
        env.usedFile.delete()
        env.keepFile.delete()
        writeCopy(env, foreign(), thisModel)
        assertEquals(mine, env.store().loadOrCreate())
        assertTrue(env.usedFile.isFile)
    }

    @Test
    fun anAppliedIdentityIsRememberedAndTheListIsCapped() {
        val env = env()
        val store = env.store()
        val mine = store.loadOrCreate()
        assertFalse(MachineIdentityKeep.isUsed(MachineIdentityKeep.parseUsed(env.usedFile.readText()), fp(mine)))
        store.markApplied(mine)
        assertTrue(MachineIdentityKeep.isUsed(MachineIdentityKeep.parseUsed(env.usedFile.readText()), fp(mine)))
        for (n in 1..30) store.markApplied(foreign(1000 + n))
        assertEquals(20, MachineIdentityKeep.parseUsed(env.usedFile.readText())!!.size)
        assertTrue(MachineIdentityKeep.isUsed(null, "00000000"))
    }

    // --- Schalter ----------------------------------------------------------------------------------------------------------------

    @Test
    fun switchedOffDeletesTheCopyAndWritesNoMore() {
        val env = env()
        val store = env.store()
        val mine = store.loadOrCreate()
        assertTrue(env.keepFile.isFile)
        assertTrue(store.setKeepEnabled(false))
        assertFalse(env.keepFile.exists())
        assertFalse(store.keepEnabled())
        assertEquals(MachineIdentityKeep.Status.OFF, store.keepStatus())
        assertTrue(store.regenerate(false) is MachineIdentityStore.Outcome.Done)
        env.store().loadOrCreate()
        assertFalse(env.keepFile.exists())
        assertTrue(Event.KEEP_OFF in env.events(store))
        assertEquals(mine.machineGuid.length, 36)
    }

    @Test
    fun switchedOffNothingIsReadFromTheFolderEither() {
        val env = env()
        assertTrue(env.store().setKeepEnabled(false))
        writeCopy(env, foreign(), thisModel)
        val store = env.store()
        val mine = store.loadOrCreate()
        assertNotEquals(foreign(), mine)
        assertFalse(Event.FROM_FOLDER in env.events(store))
        assertEquals(foreign(), validCopy(env).identity)
    }

    @Test
    fun switchedOnWritesTheCopyAgain() {
        val env = env()
        val store = env.store()
        val mine = store.loadOrCreate()
        assertTrue(store.setKeepEnabled(false))
        assertTrue(store.setKeepEnabled(true))
        assertEquals(mine, validCopy(env).identity)
        assertEquals(MachineIdentityKeep.Status.MATCHES, store.keepStatus())
        assertTrue(Event.KEEP_ON in env.events(store))
    }

    // --- Zugriff fehlt -----------------------------------------------------------------------------------------------------------

    @Test
    fun withoutAccessTheCopyIsLeftOutSilentlyAndLoggedOnce() {
        val env = env()
        env.gameDir = File(dir, "nicht-da")
        val first = env.store()
        first.loadOrCreate()
        first.loadOrCreate()
        env.store().loadOrCreate()
        val store = env.store()
        store.loadOrCreate()
        assertEquals(1, store.readLog().count { it.event == Event.KEEP_FAILED })
        assertFalse(env.gameDir.exists())
        assertEquals(MachineIdentityKeep.Status.NO_ACCESS, store.keepStatus())
        // der Zugriff ist da: die Kopie wird geschrieben, einmal gemeldet
        env.gameDir = File(dir, "game").also { it.mkdirs() }
        store.loadOrCreate()
        store.loadOrCreate()
        assertEquals(1, store.readLog().count { it.event == Event.KEEP_WRITTEN })
        assertEquals(store.peek(), validCopy(env).identity)
        assertEquals(MachineIdentityKeep.Status.MATCHES, store.keepStatus())
    }

    @Test
    fun theFailureNoteDoesNotHideTheSaveWarning() {
        val entries = listOf(
            MachineIdentityLog.Entry(1, "aaaaaaaa", 1, Event.SAVE_FAILED),
            MachineIdentityLog.Entry(2, "aaaaaaaa", 1, Event.KEEP_FAILED),
        )
        assertTrue(MachineIdentityLog.summarize(entries, emptyList()).saveFailed)
    }

    @Test
    fun notWiredMeansNoCopyAndNoEvent() {
        val env = env()
        val store = MachineIdentityStore(env.identityFile, env.logFile, { null }, 10, clock = { env.clock++ }, random = { Random(env.seed++) }, copyFile = env.copyFile)
        store.loadOrCreate()
        assertEquals(listOf(Event.CREATED), store.readLog().map { it.event })
        assertEquals(MachineIdentityKeep.Status.NO_ACCESS, store.keepStatus())
    }

    // --- Anzeige-Wege erzeugen nie eine Kennung -------------------------------------------------------------------------------

    @Test
    fun displayPathsNeverCreateAnIdentity() {
        val env = env()
        val store = env.store()
        assertNull(store.peek())
        store.summary()
        store.archiveInfos()
        store.regenerateLockedUntil()
        store.keepStatus()
        store.keepEnabled()
        store.systemSummary()
        store.prefixSummary()
        assertEquals(MachineIdentityStore.Outcome.NothingToChange, store.regenerate(false))
        assertEquals(MachineIdentityStore.Outcome.NothingToChange, store.restore(0, false))
        assertTrue(store.setKeepEnabled(false))
        assertTrue(store.setKeepEnabled(true))
        assertFalse(env.identityFile.exists())
        assertFalse(env.copyFile.exists())
        assertFalse(env.keepFile.exists())
        assertFalse(env.usedFile.exists())
        assertFalse(env.logFile.exists())
        assertFalse(env.archiveFile.exists())
    }

    @Test
    fun peekDoesNotTakeOverTheFolderCopy() {
        val env = env()
        writeCopy(env, foreign(), thisModel)
        val store = env.store()
        assertNull(store.peek())
        assertEquals(MachineIdentityKeep.Status.OTHER_IDENTITY, store.keepStatus())
        assertFalse(env.identityFile.exists())
    }

    @Test
    fun onlyTheGameStartCreatesAnIdentity() {
        val root = File("src/main/java")
        assumeTrue("Quelltext nicht erreichbar", root.isDirectory)
        val callers = root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { f ->
                val text = f.readText()
                text.contains("MachineIdentityApply.identity(") || text.contains("MachineIdentityApply.shimHostname(") ||
                    text.contains("MachineIdentityApply.applyToContainer(") || text.contains(".loadOrCreate()")
            }
            .map { it.name }.toSet()
        assertEquals(
            setOf("BionicProgramLauncherComponent.java", "XServerScreen.kt", "MachineIdentityApply.kt"),
            callers,
        )
        // Karte und Fehlerbericht lesen nur: kein Aufruf, der eine Kennung erzeugen kann
        for (name in listOf("report/ErrorReport.kt", "ui/screen/wow/launcher/IdentityCard.kt")) {
            val text = File(root, "app/gamenative/$name").readText()
            assertFalse(name, text.contains(".identity(") && text.contains("MachineIdentityApply.identity("))
            assertFalse(name, text.contains("loadOrCreate"))
            assertFalse(name, text.contains("applyToContainer"))
        }
    }

    // --- Verboten: Backup, Log, Bericht, Verlauf --------------------------------------------------------------------------------

    @Test
    fun theCopyAndItsMarkersAreNeverInABackup() {
        val names = listOf(MachineIdentityKeep.FILE_NAME, MachineIdentityKeep.OFF_FILE_NAME, MachineIdentityKeep.USED_FILE_NAME)
        for (name in names) {
            val base = name.removeSuffix(".txt").removeSuffix(".key")
            assertTrue(name, BackupAllowlist.FORBIDDEN_FILES.any { base == it || base.startsWith(it) })
        }
        assertTrue(BackupAllowlist.FORBIDDEN_FILES.contains("wowforever-kennung-kopie"))
        for (spec in BackupAllowlist.ALL) {
            for (name in names) assertFalse(spec.file + "/" + spec.key, spec.file.contains(name) || spec.key.contains(name))
        }
    }

    @Test
    fun noValueOfAnyIdentityReachesTheLogTheHistoryOrTheReportText() {
        val env = env()
        val other = foreign()
        writeCopy(env, other, otherModel)
        val store = env.store()
        val mine = store.loadOrCreate()
        env.keepFile.writeText("Mist")
        store.loadOrCreate()
        env.reinstall()
        val back = env.store(version = 11)
        back.loadOrCreate()
        val unused = foreign(31)
        val texts = listOf(
            env.logFile.takeIf { it.exists() }?.readText().orEmpty(),
            env.historyFile.readText(),
            MachineIdentityLog.historyText(back.summary().trace),
            back.readLog().joinToString("\n") { MachineIdentityLog.format(it) },
        )
        for (text in texts) {
            for (id in listOf(mine, other, unused, back.peek()!!)) {
                for (value in listOf(id.machineGuid, id.computerName, id.volumeSerial, id.productId)) {
                    assertFalse(value, text.contains(value))
                }
            }
        }
        // Fehlerbericht: die Schwärzung der eigenen Werte greift auch auf den Text der Kopie
        val own = back.peek()!!
        val redactor = ReportRedactor(extraSecrets = listOf(own.machineGuid, own.computerName, own.volumeSerial, own.productId))
        val redacted = redactor.redact(env.keepFile.readText().let { MachineIdentityKeep.serialize(own, thisModel) })
        for (value in listOf(own.machineGuid, own.computerName, own.volumeSerial, own.productId)) assertFalse(value, redacted.contains(value))
        // Der Fehlerbericht liest die Kopie und ihren Namen nie
        val report = File("src/main/java/app/gamenative/report/ErrorReport.kt")
        assumeTrue(report.isFile)
        val source = report.readText()
        assertFalse(source.contains("MachineIdentityKeep"))
        assertFalse(source.contains("wowforever-kennung"))
    }

    @Test
    fun theHistoryFileKeepsOnlyFixedWordsAndShortIdsForTheNewEvents() {
        fun entry(event: Event, detail: String) = MachineIdentityLog.forHistory(MachineIdentityLog.Entry(1, "aaaaaaaa", 1, event, detail)).detail
        assertEquals("keine_kennung", entry(Event.FROM_FOLDER, "keine_kennung"))
        assertEquals("verfallen:0123abcd", entry(Event.FROM_FOLDER, "verfallen:0123abcd"))
        assertEquals("", entry(Event.FROM_FOLDER, "verfallen:" + "DESKTOP-ABCDEFG"))
        assertEquals("pruefsumme", entry(Event.KEEP_IGNORED, "pruefsumme"))
        assertEquals("", entry(Event.KEEP_IGNORED, "DESKTOP-ABCDEFG"))
        assertEquals("anderes_modell", entry(Event.KEEP_ARCHIVED, "anderes_modell"))
        assertEquals("", entry(Event.KEEP_ARCHIVED, "00330-81234-56789-AAOEM"))
        assertEquals("", entry(Event.KEEP_FAILED, "x"))
        for (event in listOf(Event.FROM_FOLDER, Event.KEEP_IGNORED, Event.KEEP_ARCHIVED, Event.KEEP_FAILED, Event.KEEP_WRITTEN, Event.KEEP_OFF, Event.KEEP_ON)) {
            assertEquals(event, Event.fromId(event.id))
        }
    }

    @Test
    fun theArchiveHoldsAtMostFiveAndStaysInTheAppStorage() {
        val env = env()
        for (n in 1..7) {
            writeCopy(env, foreign(200 + n), otherModel)
            env.store().loadOrCreate()
        }
        assertTrue(env.store().archiveInfos().size <= MachineIdentityArchive.MAX_ITEMS)
        assertTrue(env.archiveFile.exists())
        assertFalse(env.gameDir.listFiles()!!.any { it.name.contains("archive") })
    }
}

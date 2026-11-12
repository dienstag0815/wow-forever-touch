package app.gamenative.utils

import app.gamenative.utils.MachineIdentityLog.Event
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

/** Prefix-Merkmale (Auftrag kennungslog4, Punkt 4): messen vor dem Anwenden, vergleichen, nie ein Klartextwert, nichts im Prefix ändern. */
class MachineIdentityPrefixTraitsTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("prefix-traits-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val hwGuid = "3b1f6a52-9d44-4c0a-8e17-2a5d7c91e0b3"
    private val sqmGuid = "7A2C41F0-1B5D-4E83-9C06-D8E5F3A12B74"
    private val cardId = "AABBCCDD-0000-1111-2222-334455667788"
    private val cardMac = "0A1B2C3D4E5F"
    private val sid = "S-1-5-21-1111111111-2222222222-3333333333-1000"
    private val secrets = listOf(hwGuid, sqmGuid, cardId, cardMac, sid, "1111111111", "7A2C41F0", "3b1f6a52", "AABBCCDD")

    private fun excerpt(): String =
        checkNotNull(javaClass.getResourceAsStream("/machineidentity/system_excerpt.reg")) { "Testressource fehlt" }
            .use { String(it.readBytes(), Charsets.UTF_8) }.trimEnd() + "\n\n"

    /** Der echte Registry-Ausschnitt plus die Abschnitte, die Wine laut Auftrag selbst anlegen könnte. */
    private fun reg(hw: String? = hwGuid, sqm: String? = sqmGuid, card: String? = cardMac, user: String? = sid): String = buildString {
        append(excerpt())
        if (hw != null) append("[System\\\\ControlSet001\\\\Hardware Profiles\\\\0001] 1753725000\n\"HwProfileGuid\"=\"{$hw}\"\n\n")
        if (sqm != null) append("[Software\\\\Microsoft\\\\SQMClient] 1753725000\n\"MachineId\"=\"{$sqm}\"\n\n")
        if (user != null) append("[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\ProfileList\\\\$user] 1753725000\n\"ProfileImagePath\"=\"C:\\\\users\\\\xuser\"\n\n")
        if (card != null) {
            append("[System\\\\ControlSet001\\\\Control\\\\Class\\\\{4d36e972-e325-11ce-bfc1-08002be10318}\\\\0000] 1753725000\n")
            append("\"NetCfgInstanceId\"=\"{$cardId}\"\n\"NetworkAddress\"=\"$card\"\n\n")
            append("[System\\\\ControlSet001\\\\Services\\\\Tcpip\\\\Parameters\\\\Interfaces\\\\{$cardId}] 1753725000\n\"EnableDHCP\"=dword:00000001\n\n")
        }
    }

    private fun view(text: String? = reg(), stamp: String? = "00000000") = PrefixTraits.compute(PrefixTraits.Snapshot(text, stamp))

    private fun changed(a: PrefixTraits.View, b: PrefixTraits.View): List<String> =
        (PrefixTraits.compare(PrefixTraits.stored(a), b) as? PrefixTraits.Change.Changed)?.parts?.map { it.id }.orEmpty()

    private fun part(v: PrefixTraits.View, p: PrefixTraits.Part) = v.parts.first { it.part == p }

    // --- Messen ---

    @Test
    fun everyPartHasAnEightHexFingerprintAndCoarseFeatures() {
        val v = view()
        assertEquals(PrefixTraits.Part.values().toList(), v.parts.map { it.part })
        for (p in v.parts) {
            assertTrue(p.part.id, Regex("^[0-9a-f]{8}$").matches(p.fingerprint))
            assertTrue(p.part.id, p.features.isNotEmpty())
        }
        assertTrue(Regex("^[0-9a-f]{8}$").matches(v.total))
        assertEquals(v.total, view().total)
        assertTrue(part(v, PrefixTraits.Part.HW_PROFILE).features.containsAll(listOf("present=yes", "entries=1", "form=guid")))
        assertTrue(part(v, PrefixTraits.Part.SQM).features.contains("present=yes"))
        assertTrue(part(v, PrefixTraits.Part.USER).features.containsAll(listOf("profiles=1", "sid_form=nt")))
        assertTrue(part(v, PrefixTraits.Part.NETWORK_CARDS).features.containsAll(listOf("class_entries=1", "interfaces=1", "network_address=yes")))
        assertTrue(part(v, PrefixTraits.Part.UPDATE_STAMP).features.contains("present=yes"))
    }

    @Test
    fun partsThePrefixDoesNotHaveAreAbsentNotUnreadable() {
        val v = view(reg(hw = null, sqm = null, card = null, user = null), stamp = null)
        for (p in listOf(PrefixTraits.Part.HW_PROFILE, PrefixTraits.Part.SQM, PrefixTraits.Part.USER, PrefixTraits.Part.NETWORK_CARDS, PrefixTraits.Part.UPDATE_STAMP)) {
            assertEquals(p.id, listOf("present=no"), part(v, p).features)
        }
        // The real excerpt does have the product key blob: in the NT view as hex and a placeholder in the Wow6432 view.
        assertTrue(part(v, PrefixTraits.Part.PRODUCT_KEY).features.containsAll(listOf("present=yes", "entries=2", "product_id_inside=1")))
    }

    @Test
    fun anUnreadableRegistryMakesTheRegistryPartsUnreadableAndTheStampStillCounts() {
        val v = view(null, stamp = "5f2c")
        for (p in listOf(PrefixTraits.Part.HW_PROFILE, PrefixTraits.Part.SQM, PrefixTraits.Part.PRODUCT_KEY, PrefixTraits.Part.USER, PrefixTraits.Part.NETWORK_CARDS)) {
            assertEquals(p.id, listOf(PrefixTraits.UNREADABLE), part(v, p).features)
        }
        assertEquals("present=yes", part(v, PrefixTraits.Part.UPDATE_STAMP).features.first())
        assertNotEquals(view().total, v.total)
        assertEquals(PrefixTraits.Part.values().size, PrefixTraits.compute(PrefixTraits.Snapshot()).parts.size)
    }

    @Test
    fun theProductKeyBlobStillCarriesTheSharedProductIdAfterTheIdentityWasApplied() {
        val wine = File(dir, "wine").also { it.mkdirs() }
        File(wine, "system.reg").writeText(reg())
        val before = PrefixTraits.compute(PrefixTraits.capture(wine))
        assertTrue(part(before, PrefixTraits.Part.PRODUCT_KEY).features.contains("same_as_own_product_id=1"))
        MachineIdentityApply.applyToPrefix(wine, MachineIdentity.generate(Random(3), 1_800_000_000L))
        val after = PrefixTraits.compute(PrefixTraits.capture(wine))
        // Finding: the app renews ProductId, DigitalProductId keeps the shipped one (not touched, only measured).
        assertTrue(part(after, PrefixTraits.Part.PRODUCT_KEY).features.contains("same_as_own_product_id=0"))
        assertTrue(part(after, PrefixTraits.Part.PRODUCT_KEY).features.contains("product_id_inside=1"))
        assertEquals(part(before, PrefixTraits.Part.PRODUCT_KEY).fingerprint, part(after, PrefixTraits.Part.PRODUCT_KEY).fingerprint)
        assertEquals(listOf("hwprofil", "sqm", "benutzer", "netzwerkkarten"), listOf("hwprofil", "sqm", "benutzer", "netzwerkkarten").filter { id ->
            part(before, PrefixTraits.Part.fromId(id)!!).fingerprint == part(after, PrefixTraits.Part.fromId(id)!!).fingerprint
        })
    }

    @Test
    fun eachPartReactsOnlyToItsOwnInput() {
        val base = view()
        assertEquals(listOf("hwprofil"), changed(base, view(reg(hw = "99999999-9d44-4c0a-8e17-2a5d7c91e0b3"))))
        assertEquals(listOf("sqm"), changed(base, view(reg(sqm = "11111111-1B5D-4E83-9C06-D8E5F3A12B74"))))
        assertEquals(listOf("netzwerkkarten"), changed(base, view(reg(card = "0A1B2C3D4E60"))))
        assertEquals(listOf("benutzer"), changed(base, view(reg(user = "S-1-5-21-1111111111-2222222222-3333333333-1001"))))
        assertEquals(listOf("updatezeit"), changed(base, view(stamp = "00000001")))
        assertEquals(emptyList<String>(), changed(base, view()))
    }

    @Test
    fun theIdentityFieldsThemselvesDoNotCountAsPrefixTraits() {
        val a = view(reg())
        val withOtherIdentity = reg().replace("87447f37-0be9-4ca4-8034-20645c186c64", "00000000-0000-4000-8000-000000000000")
        assertEquals(emptyList<String>(), changed(a, view(withOtherIdentity)))
    }

    @Test
    fun sectionsJoinContinuationLinesAndSurviveCrlf() {
        val text = "[A\\\\B] 1\r\n\"x\"=hex:01,02,\\\r\n  03,04\r\n\"y\"=\"v\"\r\n\r\n[C] 2\r\n\"z\"=dword:00000001\r\n"
        val sections = PrefixTraits.sections(text)
        assertEquals(listOf("A\\B", "C"), sections.map { it.key })
        assertEquals("hex:01,02,03,04", sections[0].values["x"])
        assertEquals("\"v\"", sections[0].values["y"])
        assertEquals("dword:00000001", sections[1].values["z"])
        assertTrue(PrefixTraits.sections("").isEmpty())
        assertTrue(PrefixTraits.sections("\u0000\u0001garbage[[").isEmpty())
    }

    // --- Vergleich, Detail, Zustandsdatei ---

    @Test
    fun compareSaysFirstSameOrChangedWithPartsInFixedOrder() {
        val a = view()
        assertTrue(PrefixTraits.compare(null, a) is PrefixTraits.Change.First)
        assertTrue(PrefixTraits.compare(PrefixTraits.stored(a), a) is PrefixTraits.Change.Same)
        val b = view(reg(sqm = "11111111-1B5D-4E83-9C06-D8E5F3A12B74", hw = "99999999-9d44-4c0a-8e17-2a5d7c91e0b3"), stamp = "x")
        val change = PrefixTraits.compare(PrefixTraits.stored(a), b) as PrefixTraits.Change.Changed
        assertEquals(listOf("hwprofil", "sqm", "updatezeit"), change.parts.map { it.id })
        // A part that is missing in the stored state counts as changed.
        val partial = PrefixTraits.Stored(a.total, a.parts.filter { it.part != PrefixTraits.Part.USER })
        assertEquals(listOf("benutzer"), (PrefixTraits.compare(partial, a) as PrefixTraits.Change.Changed).parts.map { it.id })
    }

    @Test
    fun theEventDetailHasTheStrictFormAndNothingElse() {
        val a = view()
        val detail = PrefixTraits.eventDetail(a, PrefixTraits.Change.Changed(listOf(PrefixTraits.Part.HW_PROFILE, PrefixTraits.Part.UPDATE_STAMP)))
        assertEquals("pm:${a.total}:hwprofil,updatezeit", detail)
        assertEquals("pm:${a.total}", PrefixTraits.eventDetail(a, PrefixTraits.Change.First))
        assertTrue(PrefixTraits.isSafeDetail(detail))
        assertEquals(a.total to listOf("hwprofil", "updatezeit"), PrefixTraits.parseDetail(detail))
        assertFalse(PrefixTraits.isSafeDetail("pm:{$hwGuid}"))
        assertFalse(PrefixTraits.isSafeDetail("pm:$hwGuid"))
        assertFalse(PrefixTraits.isSafeDetail("pm:${a.total}:Hwprofil"))
        assertNull(PrefixTraits.parseDetail("sys:${a.total}"))
    }

    @Test
    fun theStateFileRoundTripsAndGarbageCountsAsNoState() {
        val a = view()
        val stored = PrefixTraits.stored(a)
        assertEquals(stored, PrefixTraits.parseStored(PrefixTraits.serialize(stored)))
        assertNull(PrefixTraits.parseStored(null))
        assertNull(PrefixTraits.parseStored(""))
        assertNull(PrefixTraits.parseStored("systemsicht1\ntotal\t0123abcd\n"))
        assertNull(PrefixTraits.parseStored("prefixmerkmale1\ntotal\tnot-hex\npart\thwprofil\t0123abcd\tx\n"))
        assertNull(PrefixTraits.parseStored("prefixmerkmale1\ntotal\t0123abcd\n"))
    }

    // --- Klartext ---

    @Test
    fun noPlainValueReachesTheStateTheDetailOrTheFeatures() {
        val v = view()
        val text = PrefixTraits.serialize(PrefixTraits.stored(v)) + PrefixTraits.eventDetail(v, PrefixTraits.Change.First) +
            v.parts.joinToString { it.features.joinToString() }
        for (s in secrets) assertFalse(s, text.contains(s, ignoreCase = true))
    }

    // --- Mit Dateien: Store ---

    private class Env(val dir: File) {
        var clock = 1_800_000_000L
        val identityFile = File(dir, "files/" + MachineIdentity.FILE_NAME)
        val logFile = File(dir, "files/" + MachineIdentityLog.LOG_FILE_NAME)
        val historyDir = File(dir, "game").also { it.mkdirs() }
        val historyFile = File(historyDir, MachineIdentityLog.HISTORY_FILE_NAME)
        val stateFile = File(dir, "files/" + PrefixTraits.FILE_NAME)
        fun store(version: Int = 10) = MachineIdentityStore(
            identityFile = identityFile,
            logFile = logFile,
            historyFile = { historyFile },
            versionCode = version,
            clock = { clock++ },
            random = { Random(1) },
            copyFile = File(dir, "nobackup/" + MachineIdentity.FILE_NAME),
        )
    }

    private fun snap(text: String? = reg(), stamp: String? = "00000000") = PrefixTraits.Snapshot(text, stamp)

    @Test
    fun theFirstStartRecordsCapturedThenNothingThenChangedWithTheNames() {
        val env = Env(dir)
        val store = env.store()
        val id = store.loadOrCreate()
        assertTrue(store.recordPrefixTraits(id, snap()) is PrefixTraits.Change.First)
        assertTrue(store.recordPrefixTraits(id, snap()) is PrefixTraits.Change.Same)
        val change = store.recordPrefixTraits(id, snap(reg(hw = "99999999-9d44-4c0a-8e17-2a5d7c91e0b3"), stamp = "00000002")) as PrefixTraits.Change.Changed
        assertEquals(listOf("hwprofil", "updatezeit"), change.parts.map { it.id })
        val traitEvents = store.readLog().filter { it.event.isPrefixTraits }
        assertEquals(listOf(Event.PREFIX_TRAITS_CAPTURED, Event.PREFIX_TRAITS_CHANGED), traitEvents.map { it.event })
        assertTrue(traitEvents[0].detail.startsWith("pm:"))
        assertTrue(traitEvents[1].detail.endsWith(":hwprofil,updatezeit"))
        // Same events with the same detail in the history file of the game folder.
        val history = store.readHistory().filter { it.event.isPrefixTraits }
        assertEquals(traitEvents.map { it.event to it.detail }, history.map { it.event to it.detail })
        assertEquals(PrefixTraits.parseStored(env.stateFile.readText())!!.total, store.prefixSummary().total)
    }

    @Test
    fun theSummaryCountsChangesAndNamesTheLastOne() {
        val env = Env(dir)
        val store = env.store()
        val id = store.loadOrCreate()
        store.recordPrefixTraits(id, snap())
        store.recordPrefixTraits(id, snap(stamp = "00000001"))
        store.recordPrefixTraits(id, snap(reg(sqm = "11111111-1B5D-4E83-9C06-D8E5F3A12B74"), stamp = "00000001"))
        val summary = store.prefixSummary()
        assertEquals(2, summary.changes)
        assertEquals(listOf("sqm"), summary.changedParts)
        assertEquals(PrefixTraits.Part.values().size, summary.parts.size)
        // After a reinstall only the history file is left: the summary still knows.
        env.stateFile.delete()
        env.logFile.delete()
        assertEquals(2, env.store().prefixSummary().changes)
    }

    @Test
    fun measurementEventsDoNotTouchTheIdentityHistory() {
        val env = Env(dir)
        val store = env.store()
        val id = store.loadOrCreate()
        val before = store.summary()
        store.recordPrefixTraits(id, snap())
        store.recordPrefixTraits(id, snap(stamp = "00000009"))
        val after = store.summary()
        assertEquals(before.fingerprint, after.fingerprint)
        assertEquals(before.since, after.since)
        assertEquals(before.changes, after.changes)
        assertEquals(before.recent, after.recent)
        assertFalse(after.saveFailed)
        assertTrue(after.trace.any { it.event.isPrefixTraits })
        assertNull(after.update)
    }

    @Test
    fun aFailedReadingIsAnEventNotACrashAndTheStateFileCanBeMissing() {
        val env = Env(dir)
        val store = env.store()
        val id = store.loadOrCreate()
        assertTrue(store.recordPrefixTraits(id, null) is PrefixTraits.Change.First)
        assertTrue(store.recordPrefixTraits(id, snap()) is PrefixTraits.Change.Changed)
        env.stateFile.delete()
        env.stateFile.mkdirs()
        File(env.stateFile, "blocker").writeText("x")
        // The state cannot be written: no crash, the event is still logged, a repeat is suppressed by the log.
        assertTrue(store.recordPrefixTraits(id, snap(stamp = "7")) is PrefixTraits.Change.First)
        assertTrue(store.recordPrefixTraits(id, snap(stamp = "7")) is PrefixTraits.Change.First)
        assertEquals(3, store.readLog().count { it.event.isPrefixTraits })
    }

    @Test
    fun noPlainValueReachesTheLogTheHistoryTheStateOrTheReportText() {
        val env = Env(dir)
        val store = env.store()
        val id = store.loadOrCreate()
        store.recordPrefixTraits(id, snap())
        store.recordPrefixTraits(id, snap(reg(hw = "99999999-9d44-4c0a-8e17-2a5d7c91e0b3", card = "0A1B2C3D4E60"), stamp = "5"))
        val summary = store.prefixSummary()
        val reportText = MachineIdentityLog.historyText(store.summary().trace) + MachineIdentityLog.updateText(store.summary().update) +
            summary.parts.joinToString { it.fingerprint + " " + it.features.joinToString(" ; ") }
        val files = listOf(env.logFile, env.historyFile, env.stateFile).map { it.readText() } + reportText
        for (text in files) for (s in secrets + "99999999-9d44" + "0A1B2C3D4E60") assertFalse(s, text.contains(s, ignoreCase = true))
    }

    // --- Lesen aus dem Prefix: nur lesen ---

    @Test
    fun captureReadsTheUpperPrefixThenTheBaseAndNeverThrows() {
        val upper = File(dir, "upper").also { it.mkdirs() }
        val lower = File(dir, "lower").also { it.mkdirs() }
        assertNull(PrefixTraits.capture(upper, lower).systemReg)
        assertNull(PrefixTraits.capture(File(dir, "nowhere"), null).updateStamp)
        File(lower, "system.reg").writeText("lower")
        File(lower, ".update-timestamp").writeText("00000abc\n")
        assertEquals("lower", PrefixTraits.capture(upper, lower).systemReg)
        File(upper, "system.reg").writeText("upper")
        assertEquals("upper", PrefixTraits.capture(upper, lower).systemReg)
        assertEquals("00000abc\n", PrefixTraits.capture(upper, lower).updateStamp)
        // A folder where the file should be is "not there", not an error.
        File(upper, ".update-timestamp").mkdirs()
        assertEquals("00000abc\n", PrefixTraits.capture(upper, lower).updateStamp)
    }

    @Test
    fun measuringLeavesThePrefixFilesAsTheyWere() {
        val env = Env(dir)
        val wine = File(dir, "wine").also { it.mkdirs() }
        val text = reg()
        File(wine, "system.reg").writeText(text)
        File(wine, ".update-timestamp").writeText("00000abc")
        val store = env.store()
        val id = store.loadOrCreate()
        store.recordPrefixTraits(id, PrefixTraits.capture(wine))
        assertEquals(text, File(wine, "system.reg").readText())
        assertEquals("00000abc", File(wine, ".update-timestamp").readText())
        assertEquals(setOf("system.reg", ".update-timestamp"), wine.list()!!.toSet())
    }
}

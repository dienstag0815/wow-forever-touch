package app.gamenative.utils

import app.gamenative.utils.MachineIdentityLog.Entry
import app.gamenative.utils.MachineIdentityLog.Event
import app.gamenative.utils.SystemView.Change
import app.gamenative.utils.SystemView.Iface
import app.gamenative.utils.SystemView.Network
import app.gamenative.utils.SystemView.Part
import app.gamenative.utils.SystemView.Snapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

class SystemViewTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("system-view-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    // Werte, die nirgends im Klartext auftauchen dürfen.
    private val secretHost = "testers-phone"
    private val secretMac = "a4:11:22:33:44:55"
    private val secretMacLocal = "da:a1:19:12:34:56"
    private val secretIp = "192.168.77.123"

    private fun snapshot(
        host: String = secretHost,
        shim: Boolean = true,
        interfaces: List<Iface>? = listOf(
            Iface("lo", true, null),
            Iface("wlan0", true, secretMac),
            Iface("rmnet_data1", false, null),
        ),
        network: Network? = Network(setOf("wlan"), ipv4 = true, ipv6 = true, privateDns = "automatic"),
        cpuOnline: Int = 8,
        folder: String = "intern",
        user: Int = 0,
        free: Long = 20L * 1024 * 1024 * 1024,
        sdk: Int = 35,
    ) = Snapshot(host, shim, interfaces, network, 8, cpuOnline, folder, user, free, sdk)

    private fun part(view: SystemView.View, p: Part) = view.parts.first { it.part == p }

    private val hex8 = Regex("^[0-9a-f]{8}$")

    // --- Fingerabdruck je Teil ---

    @Test
    fun everyPartHasEightHexFingerprintAndTotalToo() {
        val view = SystemView.compute(snapshot())
        assertEquals(Part.values().toList(), view.parts.map { it.part })
        for (p in view.parts) assertTrue(p.part.id + " " + p.fingerprint, hex8.matches(p.fingerprint))
        assertTrue(hex8.matches(view.total))
    }

    @Test
    fun sameInputGivesSameFingerprintsAndOrderOfInterfacesDoesNotMatter() {
        val a = SystemView.compute(snapshot())
        val b = SystemView.compute(snapshot(interfaces = listOf(Iface("rmnet_data1", false, null), Iface("wlan0", true, secretMac), Iface("lo", true, null))))
        assertEquals(a.total, b.total)
        assertEquals(a.parts.map { it.fingerprint }, b.parts.map { it.fingerprint })
    }

    @Test
    fun eachPartReactsToItsOwnInputOnly() {
        val base = SystemView.compute(snapshot())
        fun changedParts(other: Snapshot) = SystemView.compare(SystemView.stored(base), SystemView.compute(other)).let { (it as Change.Changed).parts }
        assertEquals(listOf(Part.HOSTNAME), changedParts(snapshot(host = "anderer-name")))
        assertEquals(listOf(Part.HOSTNAME), changedParts(snapshot(shim = false)))
        assertEquals(listOf(Part.NETWORK), changedParts(snapshot(network = Network(setOf("mobil"), true, true, "automatic"))))
        assertEquals(listOf(Part.NETWORK), changedParts(snapshot(network = Network(setOf("wlan", "vpn"), true, true, "automatic"))))
        assertEquals(listOf(Part.NETWORK), changedParts(snapshot(network = Network(setOf("wlan"), true, false, "automatic"))))
        assertEquals(listOf(Part.NETWORK), changedParts(snapshot(network = Network(setOf("wlan"), true, true, "provider"))))
        assertEquals(listOf(Part.CPU), changedParts(snapshot(cpuOnline = 6)))
        assertEquals(listOf(Part.GAME_FOLDER), changedParts(snapshot(folder = "sd")))
        assertEquals(listOf(Part.USER), changedParts(snapshot(user = 10)))
        assertEquals(listOf(Part.FREE_SPACE), changedParts(snapshot(free = 500L * 1024 * 1024)))
        assertEquals(listOf(Part.ANDROID), changedParts(snapshot(sdk = 36)))
    }

    @Test
    fun smallChangesOfFreeSpaceInsideALevelAreNoChange() {
        val gb = 1024L * 1024 * 1024
        val a = SystemView.compute(snapshot(free = 20 * gb))
        val b = SystemView.compute(snapshot(free = 31 * gb))
        assertEquals(a.total, b.total)
        assertEquals("lt1gb", SystemView.freeLevel(gb - 1))
        assertEquals("1to4gb", SystemView.freeLevel(gb))
        assertEquals("4to16gb", SystemView.freeLevel(4 * gb))
        assertEquals("ge16gb", SystemView.freeLevel(16 * gb))
    }

    @Test
    fun randomMacChangeChangesInterfacesButIpAddressDoesNot() {
        val base = SystemView.compute(snapshot())
        val newMac = SystemView.compute(snapshot(interfaces = listOf(Iface("wlan0", true, secretMacLocal), Iface("rmnet_data1", false, null))))
        assertNotEquals(part(base, Part.INTERFACES).fingerprint, part(newMac, Part.INTERFACES).fingerprint)
        // Die IP-Adresse geht gar nicht in die Erfassung ein (nur ipv4/ipv6 ja/nein).
        assertEquals(part(base, Part.NETWORK).fingerprint, part(SystemView.compute(snapshot()), Part.NETWORK).fingerprint)
    }

    // --- grobe Merkmale ---

    @Test
    fun interfaceFeaturesShowNameKindStateAndMacKindButNoMac() {
        val view = SystemView.compute(snapshot(interfaces = listOf(Iface("wlan0", true, secretMacLocal), Iface("rmnet_data1", false, null), Iface("tun0", true, secretMac), Iface("lo", true, null))))
        val f = part(view, Part.INTERFACES).features
        assertEquals("count=3", f[0])
        assertTrue(f.toString(), "rmnet_data1 rmnet down mac=none" in f)
        assertTrue(f.toString(), "tun0 tun up mac=global" in f)
        assertTrue(f.toString(), "wlan0 wlan up mac=local" in f)
        assertFalse("Loopback bleibt draußen", f.any { it.startsWith("lo ") })
    }

    @Test
    fun interfaceKinds() {
        assertEquals("wlan", SystemView.interfaceKind("wlan0"))
        assertEquals("rmnet", SystemView.interfaceKind("rmnet_data3"))
        assertEquals("clat", SystemView.interfaceKind("v4-rmnet_data3"))
        assertEquals("clat", SystemView.interfaceKind("clat4"))
        assertEquals("tun", SystemView.interfaceKind("tun0"))
        assertEquals("lo", SystemView.interfaceKind("lo"))
        assertEquals("p2p", SystemView.interfaceKind("p2p-wlan0-0"))
        assertEquals("other", SystemView.interfaceKind("xyz9"))
    }

    @Test
    fun macParsingLocalBitAndPlaceholders() {
        assertNull(SystemView.parseMac(null))
        assertNull(SystemView.parseMac(""))
        assertNull(SystemView.parseMac("00:00:00:00:00:00"))
        assertNull("Platzhalter von Android", SystemView.parseMac("02:00:00:00:00:00"))
        assertNull(SystemView.parseMac("a4:11:22:33:44"))
        assertNull(SystemView.parseMac("zz:11:22:33:44:55"))
        assertFalse(SystemView.isLocalMac(SystemView.parseMac(secretMac)!!))
        assertTrue(SystemView.isLocalMac(SystemView.parseMac(secretMacLocal)!!))
        assertTrue(SystemView.isLocalMac(SystemView.parseMac("02:11:22:33:44:55")!!))
    }

    @Test
    fun networkFeatures() {
        val f = part(SystemView.compute(snapshot(network = Network(setOf("wlan", "vpn"), true, false, "off"))), Part.NETWORK).features
        assertEquals(listOf("net=vpn+wlan", "ipv4=yes", "ipv6=no", "privatedns=off"), f)
        val none = part(SystemView.compute(snapshot(network = Network(emptySet(), false, false, "off"))), Part.NETWORK).features
        assertEquals("net=none", none[0])
    }

    @Test
    fun hostnameFeaturesNeverContainTheName() {
        val f = part(SystemView.compute(snapshot()), Part.HOSTNAME).features
        assertEquals(listOf("system=set", "shim=yes"), f)
        assertEquals("system=localhost", part(SystemView.compute(snapshot(host = "localhost", shim = false)), Part.HOSTNAME).features[0])
        assertEquals("system=empty", part(SystemView.compute(snapshot(host = "")), Part.HOSTNAME).features[0])
    }

    @Test
    fun cpuLists() {
        assertEquals(8, SystemView.countCpuList("0-7"))
        assertEquals(6, SystemView.countCpuList("0-3,5-6\n"))
        assertEquals(1, SystemView.countCpuList("0"))
        assertNull(SystemView.countCpuList(""))
        assertNull(SystemView.countCpuList(null))
        assertNull(SystemView.countCpuList("7-3"))
        assertNull(SystemView.countCpuList("a-b"))
    }

    @Test
    fun storageKinds() {
        val sd = listOf("/storage/1234-ABCD")
        assertEquals("intern", SystemView.storageKind("/storage/emulated/0/WoW Forever", "/storage/emulated/0", sd))
        assertEquals("sd", SystemView.storageKind("/storage/1234-ABCD/WoW Forever", "/storage/emulated/0", sd))
        assertEquals("anderes", SystemView.storageKind("/data/data/x/files/game", "/storage/emulated/0", sd))
        assertEquals("anderes", SystemView.storageKind("/storage/emulated/01/x", "/storage/emulated/0", sd))
    }

    @Test
    fun secondProfileIsAFlagOnly() {
        assertEquals(listOf("second_profile=no"), part(SystemView.compute(snapshot(user = 0)), Part.USER).features)
        assertEquals(listOf("second_profile=yes"), part(SystemView.compute(snapshot(user = 10)), Part.USER).features)
        // Die Nutzer-Id selbst geht nicht ein: Profil 10 und 11 sind für den Vergleich gleich.
        assertEquals(part(SystemView.compute(snapshot(user = 10)), Part.USER).fingerprint, part(SystemView.compute(snapshot(user = 11)), Part.USER).fingerprint)
    }

    // --- Vergleich alt/neu ---

    @Test
    fun compareFirstSameAndChangedWithNamesInFixedOrder() {
        val a = SystemView.compute(snapshot())
        assertTrue(SystemView.compare(null, a) is Change.First)
        assertTrue(SystemView.compare(SystemView.stored(a), SystemView.compute(snapshot())) is Change.Same)
        val b = SystemView.compute(snapshot(network = Network(setOf("mobil"), true, true, "off"), interfaces = listOf(Iface("rmnet_data1", true, null)), host = "x"))
        val change = SystemView.compare(SystemView.stored(a), b) as Change.Changed
        assertEquals(listOf(Part.HOSTNAME, Part.INTERFACES, Part.NETWORK), change.parts)
        assertEquals("sys:" + b.total + ":hostname,schnittstellen,netz", SystemView.eventDetail(b, change))
        assertEquals("sys:" + a.total, SystemView.eventDetail(a, Change.First))
    }

    @Test
    fun aMissingOrNewPartCountsAsChanged() {
        val a = SystemView.compute(snapshot())
        val older = SystemView.Stored(a.total, a.parts.filter { it.part != Part.CPU })
        val change = SystemView.compare(older, a) as Change.Changed
        assertEquals(listOf(Part.CPU), change.parts)
    }

    @Test
    fun unreadablePartsAreMarkedAndStillCompare() {
        val failed = SystemView.compute(Snapshot())
        for (p in failed.parts) assertEquals(listOf(SystemView.UNREADABLE), p.features)
        assertTrue(hex8.matches(failed.total))
        val change = SystemView.compare(SystemView.stored(SystemView.compute(snapshot())), failed) as Change.Changed
        assertEquals(Part.values().toList(), change.parts)
    }

    // --- Zustandsdatei und Detail ---

    @Test
    fun storedStateRoundTripsAndRejectsGarbage() {
        val view = SystemView.compute(snapshot())
        val stored = SystemView.parseStored(SystemView.serialize(SystemView.stored(view)))!!
        assertEquals(view.total, stored.total)
        assertEquals(view.parts, stored.parts)
        assertNull(SystemView.parseStored(null))
        assertNull(SystemView.parseStored(""))
        assertNull(SystemView.parseStored("müll"))
        assertNull(SystemView.parseStored("systemsicht1\ntotal\tnicht-hex\n"))
        assertNull(SystemView.parseStored("systemsicht1\ntotal\t0123abcd\n"))
    }

    @Test
    fun detailFormatIsStrict() {
        assertTrue(SystemView.isSafeDetail("sys:0123abcd"))
        assertTrue(SystemView.isSafeDetail("sys:0123abcd:netz,schnittstellen"))
        assertFalse(SystemView.isSafeDetail("sys:0123abcd:"))
        assertFalse(SystemView.isSafeDetail("sys:0123ABCD"))
        assertFalse(SystemView.isSafeDetail("sys:0123abcd:netz=192.168.1.1"))
        assertFalse(SystemView.isSafeDetail("vor_anwenden:hostname"))
        assertEquals("0123abcd" to listOf("netz", "schnittstellen"), SystemView.parseDetail("sys:0123abcd:netz,schnittstellen"))
        assertEquals("0123abcd" to emptyList<String>(), SystemView.parseDetail("sys:0123abcd"))
    }

    @Test
    fun forHistoryKeepsSystemDetailOnlyInSafeForm() {
        val ok = Entry(1, "aaaaaaaa", 1, Event.SYSTEM_CHANGED, "sys:0123abcd:netz")
        assertEquals("sys:0123abcd:netz", MachineIdentityLog.forHistory(ok).detail)
        val bad = Entry(1, "aaaaaaaa", 1, Event.SYSTEM_CHANGED, "sys:0123abcd:host_" + secretHost)
        assertEquals("", MachineIdentityLog.forHistory(bad).detail)
    }

    // --- Zusammenfassung ---

    private fun sysEntry(time: Long, event: Event, detail: String) = Entry(time, "aaaaaaaa", 1, event, detail)

    @Test
    fun summaryFromEventsSinceLastChangeAndParts() {
        val log = listOf(
            Entry(100, "aaaaaaaa", 1, Event.CREATED),
            sysEntry(101, Event.SYSTEM_CAPTURED, "sys:11111111"),
            sysEntry(200, Event.SYSTEM_CHANGED, "sys:22222222:netz"),
            sysEntry(300, Event.SYSTEM_CHANGED, "sys:33333333:netz,schnittstellen"),
        )
        val s = SystemView.summarize(log, emptyList(), null)
        assertEquals("33333333", s.total)
        assertEquals(300L, s.since)
        assertEquals(listOf("netz", "schnittstellen"), s.changedParts)
        assertEquals(300L, s.changedAt)
        assertEquals(2, s.changes)
        val empty = SystemView.summarize(emptyList(), emptyList(), null)
        assertNull(empty.total)
        assertNull(empty.since)
    }

    @Test
    fun summaryUsesTheHistoryAfterAReinstall() {
        val history = listOf(sysEntry(101, Event.SYSTEM_CAPTURED, "sys:11111111"), sysEntry(200, Event.SYSTEM_CHANGED, "sys:22222222:android"))
        val s = SystemView.summarize(emptyList(), history, null)
        assertEquals("22222222", s.total)
        assertEquals(listOf("android"), s.changedParts)
    }

    @Test
    fun systemEventsDoNotDisturbTheIdentitySummary() {
        val log = listOf(
            Entry(100, "aaaaaaaa", 1, Event.CREATED),
            Entry(110, "aaaaaaaa", 1, Event.SAVE_FAILED),
            sysEntry(120, Event.SYSTEM_CAPTURED, "sys:11111111"),
        )
        val s = MachineIdentityLog.summarize(log, emptyList())
        assertTrue("Speicherwarnung bleibt trotz Systemereignis", s.saveFailed)
        assertEquals(listOf(Event.CREATED, Event.SAVE_FAILED), s.recent.map { it.event })
        assertEquals(0, s.changes)
        assertEquals(100L, s.since)
        assertEquals(3, s.trace.size)
        assertTrue(MachineIdentityLog.historyText(s.trace).contains("systemsicht_erfasst sys:11111111"))
    }

    // --- Ablauf mit Dateien ---

    private class Env(val dir: File) {
        var clock = 1_800_000_000L
        val identityFile = File(dir, "files/" + MachineIdentity.FILE_NAME)
        val logFile = File(dir, "files/" + MachineIdentityLog.LOG_FILE_NAME)
        val systemFile = File(dir, "files/" + MachineIdentityStore.SYSTEM_FILE_NAME)
        val historyFile = File(dir, "game/" + MachineIdentityLog.HISTORY_FILE_NAME)

        fun store(history: Boolean = true) = MachineIdentityStore(
            identityFile = identityFile,
            logFile = logFile,
            historyFile = { if (history) historyFile else null },
            versionCode = 10,
            clock = { clock++ },
            random = { Random(5) },
        )
    }

    private fun env() = Env(dir).also { File(dir, "game").mkdirs() }

    @Test
    fun firstStartLogsCapturedThenNothingThenChanged() {
        val env = env()
        val store = env.store()
        val id = store.loadOrCreate()
        assertTrue(store.recordSystemView(id, snapshot()) is Change.First)
        assertTrue(store.recordSystemView(id, snapshot()) is Change.Same)
        val changed = store.recordSystemView(id, snapshot(network = Network(setOf("mobil"), true, true, "off"), interfaces = listOf(Iface("rmnet_data1", true, null))))
        assertEquals(listOf(Part.INTERFACES, Part.NETWORK), (changed as Change.Changed).parts)
        val events = store.readLog().map { it.event }
        assertEquals(listOf(Event.CREATED, Event.SYSTEM_CAPTURED, Event.SYSTEM_CHANGED), events)
        assertTrue(store.readLog().last().detail.endsWith(":schnittstellen,netz"))
        // Dieselben Ereignisse stehen in der Verlaufsdatei im Spielordner, mit Detail (nur Gesamt-Fingerabdruck und Teilnamen).
        assertEquals(events, store.readHistory().map { it.event })
        assertEquals(store.readLog().last().detail, store.readHistory().last().detail)
    }

    @Test
    fun systemEventCarriesTheIdentityFingerprintSoTheIdentityHistoryStaysIntact() {
        val env = env()
        val store = env.store()
        val id = store.loadOrCreate()
        store.recordSystemView(id, snapshot())
        store.recordSystemView(id, snapshot(sdk = 99))
        val s = store.summary()
        assertEquals(MachineIdentityLog.fingerprint(id), s.fingerprint)
        assertEquals(0, s.changes)
        assertNull(s.previous)
        assertEquals(2, store.readLog().count { it.event.isSystem })
    }

    @Test
    fun summaryOfTheStoreCombinesStateAndEvents() {
        val env = env()
        val store = env.store()
        val id = store.loadOrCreate()
        store.recordSystemView(id, snapshot())
        store.recordSystemView(id, snapshot(cpuOnline = 4))
        val s = store.systemSummary()
        assertNotNull(s.total)
        assertEquals(listOf("prozessor"), s.changedParts)
        assertEquals(1, s.changes)
        assertEquals(Part.values().size, s.parts.size)
    }

    @Test
    fun captureFailureIsLoggedWithoutCrash() {
        val env = env()
        val store = env.store()
        val id = store.loadOrCreate()
        assertTrue(store.recordSystemView(id, null) is Change.First)
        assertTrue(store.recordSystemView(id, null) is Change.Same)
        val s = store.systemSummary()
        assertTrue(s.parts.all { it.features == listOf(SystemView.UNREADABLE) })
        // Danach liest sich alles wieder: als Änderung aller Teile.
        val back = store.recordSystemView(id, snapshot()) as Change.Changed
        assertEquals(Part.values().toList(), back.parts)
    }

    @Test
    fun unwritableStateOrMissingGameFolderNeverThrows() {
        val env = env()
        // Zustandsdatei ist ein Ordner: Schreiben scheitert, Protokoll läuft weiter.
        env.systemFile.mkdirs()
        val store = env.store()
        val id = store.loadOrCreate()
        assertNotNull(store.recordSystemView(id, snapshot()))
        assertTrue(store.readLog().any { it.event == Event.SYSTEM_CAPTURED })
        // Ohne Spielordner nur das App-Protokoll.
        val noGame = env().store(history = false)
        assertNotNull(noGame.recordSystemView(id, snapshot()))
    }

    @Test
    fun repeatedFirstEventIsNotWrittenTwiceWhenStateCannotBeStored() {
        val env = env()
        env.systemFile.mkdirs()
        val store = env.store()
        val id = store.loadOrCreate()
        store.recordSystemView(id, snapshot())
        store.recordSystemView(id, snapshot())
        store.recordSystemView(id, snapshot())
        assertEquals(1, store.readLog().count { it.event == Event.SYSTEM_CAPTURED })
    }

    @Test
    fun brokenStateFileCountsAsFirstCapture() {
        val env = env()
        val store = env.store()
        val id = store.loadOrCreate()
        env.systemFile.parentFile.mkdirs()
        env.systemFile.writeText("kaputt")
        assertTrue(store.recordSystemView(id, snapshot()) is Change.First)
    }

    // --- Datenschutz ---

    @Test
    fun noPlainValueInLogHistoryStateOrReportText() {
        val env = env()
        val store = env.store()
        val id = store.loadOrCreate()
        store.recordSystemView(id, snapshot())
        store.recordSystemView(id, snapshot(host = "noch-ein-geheimer-name", interfaces = listOf(Iface("wlan0", true, secretMacLocal))))
        val texts = listOf(
            env.logFile.readText(),
            env.historyFile.readText(),
            env.systemFile.readText(),
            MachineIdentityLog.historyText(store.summary().trace),
            store.systemSummary().parts.joinToString("\n") { it.part.id + it.fingerprint + it.features },
        )
        val secrets = listOf(secretHost, "noch-ein-geheimer-name", secretMac, secretMacLocal, secretIp, "a4:11", "da:a1", "testers", "192.168")
        for (text in texts) for (secret in secrets) assertFalse("$secret in: $text", text.contains(secret, ignoreCase = true))
    }

    @Test
    fun historyFileHasNoDeviceModelAndNoAndroidVersionText() {
        val env = env()
        val store = env.store()
        val id = store.loadOrCreate()
        store.recordSystemView(id, snapshot())
        val text = env.historyFile.readText()
        // Nur Zeit, Kurzkennung, Version, Ereignis und sys:<Gesamt>[:Teile]; das Gerätemodell kommt in der App-Logik gar nicht vor.
        for (line in text.lines().filter { it.isNotEmpty() }) {
            val cols = line.split('\t')
            assertTrue(line, cols.size in 4..5)
            if (cols.size == 5) assertTrue(line, SystemView.isSafeDetail(cols[4]) || cols[3] == "neu_auf_wunsch" || cols[3] == "wiederhergestellt")
        }
    }

    @Test
    fun noBackupOfSystemFiles() {
        val name = MachineIdentityStore.SYSTEM_FILE_NAME.removeSuffix(".txt")
        assertTrue(app.gamenative.backup.BackupAllowlist.FORBIDDEN_FILES.contains(name))
        for (spec in app.gamenative.backup.BackupAllowlist.ALL) assertFalse(spec.toString(), spec.file.contains(name) || spec.key.contains(name))
    }
}

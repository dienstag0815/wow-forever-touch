package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Adressen in allen Dateien der Zip: IPv4 -> [IP], IPv6 -> [IP6], und was dabei unberührt bleiben muss. */
class AddressRedactorTest {
    private fun r(text: String) = AddressRedactor.redact(text)

    @Test
    fun ipv4IsReplacedAlsoWithPortAndInsideText() {
        assertEquals("connect to [IP]", r("connect to 203.0.113.9"))
        assertEquals("[IP]:1119 and [IP]", r("10.0.0.1:1119 and 255.255.255.255"))
        assertEquals("host=[IP], port=1119", r("host=192.168.1.20, port=1119"))
        assertEquals("(addr [IP]).", r("(addr 8.8.8.8)."))
        assertEquals("[IP].", r("0.0.0.0."))
        assertEquals("a [IP] b [IP]", r("a 1.2.3.4 b 5.6.7.8"))
    }

    @Test
    fun versionsTimestampsAndBuildNumbersAreNotTouched() {
        for (text in listOf(
            "client 1.60.1.70938", "Client-Build: 11.2.7.64978", "app 2.3.3", "2.3.3-touch9.15 (303)", "v1.2.3.4", "build1.2.3.4",
            "11/14 19:58:12.255  [N][Connection] Client connection initialized", "19:58:12", "2026-11-14T19:58:12Z", "14.11.2026",
            "10.0.19045.1234", "1.2.3.4.5", "256.1.1.1", "01.02.03.04", "x86_64", "Wine 9.0", "code 233091", "0024:fixme:d3d:wined3d_x",
            "0x7ffe12345678", "1:2", "12:34:56", "ratio 3:2:1", "std::vector", "a::b", "Foo::bar::baz", "key :: value", "::", "dead::beef",
        )) {
            assertEquals(text, r(text))
        }
    }

    @Test
    fun ipv6InAllFormsIsReplaced() {
        for ((text, expected) in listOf(
            "2001:db8:85a3:0:0:8a2e:370:7334" to "[IP6]",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334" to "[IP6]",
            "2001:db8::1" to "[IP6]",
            "fe80::1" to "[IP6]",
            "::1" to "[IP6]",
            "1::" to "[IP6]",
            "2001:db8::" to "[IP6]",
            "::ffff:192.168.0.1" to "[IP6]",
            "64:ff9b::192.0.2.33" to "[IP6]",
            "fe80::1%eth0" to "[IP6]",
            "[::1]:8080" to "[IP6]:8080",
            "[2001:db8::1]:443" to "[IP6]:443",
            "[2001:DB8::1]" to "[IP6]",
            "FE80::ABCD:1234" to "[IP6]",
        )) {
            assertEquals(text, expected, r(text))
        }
        assertEquals("connect failed to [IP6]:1119 (retry [IP6]).", r("connect failed to [2001:db8::5]:1119 (retry fe80::2)."))
        assertEquals("addr [IP6]: next", r("addr 1:2:3:4:5:6:7:8: next"))
    }

    @Test
    fun invalidIpv6LookalikesStay() {
        for (text in listOf("1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9", "2001:db8:::1", "1::2::3", "12345::1", "g::1", "ab:cd:ef", "aa:bb:cc:dd:ee:ff")) {
            assertEquals(text, r(text))
        }
    }

    @Test
    fun mixedTextWithTimesVersionsAndAddresses() {
        val text = "11/14 19:58:12.255  [N][Tact] Connecting to 203.0.113.9:1119 and [2001:db8::1]:443, client 1.60.1.70938, app 2.3.3"
        assertEquals("11/14 19:58:12.255  [N][Tact] Connecting to [IP]:1119 and [IP6]:443, client 1.60.1.70938, app 2.3.3", r(text))
    }

    @Test
    fun hostnamesOfTheGameServersStayReadable() {
        val text = "Battle.net logon requested host=\"EU.actual.battle.net\" port=\"1119\" cdn us.patch.battle.net level3.blizzard.com"
        assertEquals(text, r(text))
    }

    @Test
    fun manyLinesAreFastAndStayCorrect() {
        val line = "11/14 19:58:12.255  [N][Tact] fetch 203.0.113.9:80 /data/ab12 at 12:34:56 v1.60.1.70938 ::1 abcd:ef01:2345:6789:abcd:ef01:2345:6789"
        val text = (1..20000).joinToString("\n") { line }
        val start = System.nanoTime()
        val out = r(text)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("$ms ms", ms < 5000)
        assertEquals(20000, out.lines().size)
        assertTrue(out.lines().all { it == "11/14 19:58:12.255  [N][Tact] fetch [IP]:80 /data/ab12 at 12:34:56 v1.60.1.70938 [IP6] [IP6]" })
    }

    // --- Über den ReportRedactor: jede Art Datei ---

    private val redactor = ReportRedactor(listOf("player@example.com"), listOf("Tr0ub4dor&3xyz"))

    @Test
    fun everyKindOfFileAndTheLogcatGoThroughTheAddressRule() {
        val text = "server 203.0.113.9 and [2001:db8::7]:1119 at 11/14 19:58:12.255 build 1.60.1.70938"
        for (kind in LogKind.entries) {
            val out = redactor.redact(text, kind)
            assertEquals(kind.name, "server [IP] and [IP6]:1119 at 11/14 19:58:12.255 build 1.60.1.70938", out)
        }
    }

    @Test
    fun theZipHasNoAddressInAnyEntryNotEvenInTheSummaryOrDeviceFile() {
        val tmp = java.nio.file.Files.createTempDirectory("addr").toFile()
        try {
            val tact = java.io.File(tmp, "Tact.log").apply { writeText("fetch 203.0.113.9:80\nretry [2001:db8::1]:80\nversion 1.60.1.70938\n") }
            val sources = listOf(ReportSource("spiel/Logs/Tact.log", tact, LogKind.GENERAL, 1))
            val out = java.io.ByteArrayOutputStream()
            ReportBuilder.write(out, "device at 198.51.100.7", "logcat 192.0.2.44 fe80::1", sources, redactor, listOf("kurz.txt" to "ip 10.1.2.3"))
            val entries = mutableMapOf<String, String>()
            java.util.zip.ZipInputStream(out.toByteArray().inputStream()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entries[it.name] = String(zip.readBytes(), Charsets.UTF_8) }
            }
            assertEquals(4, entries.size)
            val all = entries.values.joinToString("\n")
            for (address in listOf("203.0.113.9", "2001:db8", "198.51.100.7", "192.0.2.44", "fe80::1", "10.1.2.3")) assertFalse(address, all.contains(address))
            assertTrue(entries.getValue("spiel/Logs/Tact.log").contains("version 1.60.1.70938"))
            assertTrue(entries.getValue("spiel/Logs/Tact.log").contains("[IP]:80"))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun theNamedExamplesFromTheOrderAreKeptOrBlackedOut() {
        for (kept in listOf("1.60.1.70938", "2.3.3-touch9.16", "11/14 19:58:12.255", "EU.actual.battle.net:1119")) {
            assertEquals(kept, r(kept))
            assertEquals("x $kept y", r("x $kept y"))
        }
        assertEquals("11/14 19:58:12.255  [N][Connection] listen on [IP6]:35991", r("11/14 19:58:12.255  [N][Connection] listen on [::1]:35991"))
        assertEquals(
            "Adding server node 0. Server 'level3.ssl.blizzard.com' | Address '[IP]'",
            r("Adding server node 0. Server 'level3.ssl.blizzard.com' | Address '203.0.113.7'"),
        )
    }

    @Test
    fun aFourPartVersionWithAllPartsBelow256IsAKnownFalseHit() {
        assertEquals("version [IP]", r("version 1.2.3.4"))
    }
}

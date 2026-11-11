package app.gamenative.backup

import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private fun manifest(version: Int = BackupFormat.FORMAT_VERSION, format: String = BackupFormat.FORMAT_NAME) =
        """{"format":"$format","formatVersion":$version,"appVersionName":"2.3.3","appVersionCode":300,"createdAt":"2026-11-10 12:00"}"""

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, text) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun failed(bytes: ByteArray): BackupError = (BackupFormat.unpack(bytes) as UnpackResult.Failed).error

    private fun ok(bytes: ByteArray): Backup = (BackupFormat.unpack(bytes) as UnpackResult.Ok).backup

    private fun setting(file: String, key: String, value: Any): BackupSetting =
        BackupSetting(BackupAllowlist.find(file, key)!!, value)

    @Test
    fun `round trip keeps profiles settings of every type and the manifest`() {
        val profiles = listOf(
            BackupProfile(7, "Touch (Mein Layout)", """{"id":7,"name":"Touch (Mein Layout)","elements":[{"x":0.1}]}"""),
            BackupProfile(3, "Eigenes", """{"id":3,"name":"Eigenes","elements":[]}"""),
        )
        val settings = listOf(
            setting(BackupAllowlist.FILE_MAIN, "region", "EU"),
            setting(BackupAllowlist.FILE_MAIN, "touch_hide_in_login", true),
            setting(BackupAllowlist.FILE_AUTOSTART, "seconds", 5),
            setting(BackupAllowlist.FILE_MAIN, "stick_gain_walk", 0.1f),
            setting(BackupAllowlist.FILE_MAIN, "touch_contrast", 1.25f),
        )
        val m = BackupManifest(1, "2.3.3-touch9.1", 301, "2026-11-10 12:00", 2, 5, "touch_full", "Touch (WoW komplett)")

        val back = ok(BackupFormat.pack(m, profiles, settings))

        assertEquals(m, back.manifest)
        assertEquals(profiles.sortedBy { it.id }, back.profiles)
        assertEquals(settings.toSet(), back.settings.toSet())
        assertEquals(0, back.skipped)
        // 0.1f muss als 0.1f zurückkommen, nicht als 0.10000000149011612.
        assertEquals(0.1f, back.settings.first { it.spec.key == "stick_gain_walk" }.value as Float, 0f)
    }

    @Test
    fun `manifest counts come from the content not from the manifest text`() {
        val bytes = zip(
            "manifest.json" to """{"format":"${BackupFormat.FORMAT_NAME}","formatVersion":1,"profileCount":99,"settingCount":99}""",
            "settings.json" to """{"wow_forever":{"region":"EU"}}""",
            "profiles/controls-1.icp" to """{"id":1,"name":"A"}""",
        )
        val back = ok(bytes)

        assertEquals(1, back.manifest.profileCount)
        assertEquals(1, back.manifest.settingCount)
    }

    @Test
    fun `unknown fields entries and settings are ignored and counted`() {
        val bytes = zip(
            "manifest.json" to """{"format":"${BackupFormat.FORMAT_NAME}","formatVersion":1,"zukunft":{"a":1}}""",
            "settings.json" to """{"wow_forever":{"region":"EU","erfunden":"x","touch_hold_ms":"viel"},"andere_datei":{"x":1},"kaputt":5}""",
            "profiles/controls-1.icp" to """{"id":1,"name":"A","neu":true}""",
            "profiles/notiz.txt" to "hallo",
            "readme.md" to "hallo",
        )
        val back = ok(bytes)

        assertEquals(listOf("region"), back.settings.map { it.spec.key })
        assertEquals(1, back.profiles.size)
        // erfunden, touch_hold_ms (falscher Typ), andere_datei.x, kaputt (kein Objekt)
        assertEquals(4, back.skipped)
    }

    @Test
    fun `secrets in settings json are dropped when reading`() {
        val bytes = zip(
            "manifest.json" to manifest(),
            "settings.json" to """{"battle_net_login":{"email":"a@b.c","password":"geheim"},"wow_forever":{"password":"x","region":"EU"}}""",
        )
        val back = ok(bytes)

        assertEquals(listOf("region"), back.settings.map { it.spec.key })
        assertEquals(3, back.skipped)
    }

    @Test
    fun `a file that is no zip is not a backup`() {
        assertEquals(BackupError.NOT_A_BACKUP, failed("das ist kein zip".toByteArray()))
        assertEquals(BackupError.NOT_A_BACKUP, failed(ByteArray(0)))
    }

    @Test
    fun `a truncated zip never throws`() {
        val good = BackupFormat.pack(
            BackupManifest(1, "x", 1, "t", 0, 0),
            listOf(BackupProfile(1, "A", """{"id":1,"name":"A"}""")),
            emptyList(),
        )
        val cut = good.copyOf(good.size / 2)
        val result = BackupFormat.unpack(cut)
        assertTrue(result is UnpackResult.Failed || result is UnpackResult.Ok)
    }

    @Test
    fun `a zip without manifest is rejected`() {
        assertEquals(BackupError.NO_MANIFEST, failed(zip("settings.json" to "{}")))
    }

    @Test
    fun `a manifest that is not json is broken`() {
        assertEquals(BackupError.BROKEN, failed(zip("manifest.json" to "{kaputt")))
    }

    @Test
    fun `a settings file that is not json is broken`() {
        assertEquals(BackupError.BROKEN, failed(zip("manifest.json" to manifest(), "settings.json" to "[[")))
    }

    @Test
    fun `another format name is rejected`() {
        assertEquals(BackupError.WRONG_FORMAT, failed(zip("manifest.json" to manifest(format = "etwas-anderes"))))
    }

    @Test
    fun `format version 0 is too old and 2 is too new`() {
        assertEquals(BackupError.TOO_OLD, failed(zip("manifest.json" to manifest(version = 0))))
        assertEquals(BackupError.TOO_NEW, failed(zip("manifest.json" to manifest(version = BackupFormat.FORMAT_VERSION + 1))))
    }

    @Test
    fun `a missing format version counts as too old`() {
        assertEquals(BackupError.TOO_OLD, failed(zip("manifest.json" to """{"format":"${BackupFormat.FORMAT_NAME}"}""")))
    }

    @Test
    fun `an entry over the size limit is too big`() {
        val big = "x".repeat(BackupFormat.MAX_ENTRY_BYTES + 1)
        assertEquals(BackupError.TOO_BIG, failed(zip("manifest.json" to manifest(), "profiles/controls-1.icp" to big)))
    }

    @Test
    fun `too many entries are too big`() {
        val entries = (0..BackupFormat.MAX_ENTRIES).map { "profiles/x$it.txt" to "a" }.toTypedArray()
        assertEquals(BackupError.TOO_BIG, failed(zip("manifest.json" to manifest(), *entries)))
    }

    @Test
    fun `profiles without id or name or with a negative id are skipped`() {
        val bytes = zip(
            "manifest.json" to manifest(),
            "profiles/controls-1.icp" to """{"name":"ohne id"}""",
            "profiles/controls-2.icp" to """{"id":2}""",
            "profiles/controls-3.icp" to """{"id":3,"name":"   "}""",
            "profiles/controls-4.icp" to """{"id":-4,"name":"negativ"}""",
            "profiles/controls-5.icp" to "kein json",
            "profiles/controls-6.icp" to """{"id":6,"name":"gut"}""",
        )
        val back = ok(bytes)

        assertEquals(listOf("gut"), back.profiles.map { it.name })
        assertEquals(5, back.skipped)
    }

    @Test
    fun `a profile name that is too long is skipped`() {
        val name = "n".repeat(BackupFormat.MAX_NAME_LENGTH + 1)
        val back = ok(zip("manifest.json" to manifest(), "profiles/controls-1.icp" to """{"id":1,"name":"$name"}"""))
        assertTrue(back.profiles.isEmpty())
        assertEquals(1, back.skipped)
    }

    @Test
    fun `a backup without settings or profiles is valid and empty`() {
        val back = ok(zip("manifest.json" to manifest()))
        assertTrue(back.profiles.isEmpty())
        assertTrue(back.settings.isEmpty())
    }

    @Test
    fun `file name carries date, time to the second and the device tag`() {
        assertEquals(
            "wow-forever-touch-backup-2026-11-11_14-05-09_a1b2c3.zip",
            BackupFormat.fileName("2026-11-11_14-05-09", "a1b2c3"),
        )
        val at = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 11, 14, 5, 9) }.time
        assertEquals("2026-11-11_14-05-09", BackupFormat.fileStamp(at))
        assertEquals("2026-11-11 14:05:09", BackupFormat.createdAtText(at))
        assertTrue(Regex("""wow-forever-touch-backup-\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}_[0-9a-f]{6}\.zip""")
            .matches(BackupFormat.fileName(BackupFormat.fileStamp(at), BackupFormat.deviceTag("x"))))
    }

    @Test
    fun `two backups never share a name - other second or other device`() {
        val a = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 11, 14, 5, 9) }.time
        val b = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 11, 14, 5, 10) }.time
        val phone = BackupFormat.deviceTag("seed one")
        val tablet = BackupFormat.deviceTag("seed two")
        assertNotEquals(BackupFormat.fileName(BackupFormat.fileStamp(a), phone), BackupFormat.fileName(BackupFormat.fileStamp(b), phone))
        assertNotEquals(BackupFormat.fileName(BackupFormat.fileStamp(a), phone), BackupFormat.fileName(BackupFormat.fileStamp(a), tablet))
    }

    @Test
    fun `device tag is six hex characters of the SHA-256 of the seed`() {
        // SHA-256("abc") = ba7816bf 8f01cfea ...
        assertEquals("ba7816", BackupFormat.deviceTag("abc"))
        assertEquals(BackupFormat.DEVICE_TAG_LENGTH, BackupFormat.deviceTag("anything").length)
        assertTrue(BackupFormat.isDeviceTag(BackupFormat.deviceTag("anything")))
        assertEquals(BackupFormat.deviceTag("same"), BackupFormat.deviceTag("same"))
        assertNotEquals(BackupFormat.deviceTag("same"), BackupFormat.deviceTag("other"))
        // the seed itself never shows up in the tag
        assertFalse(BackupFormat.deviceTag("0123456789abcdef").contains("012345"))
    }

    @Test
    fun `only a plain six character hex tag is a device tag`() {
        assertTrue(BackupFormat.isDeviceTag("0a9f3c"))
        assertFalse(BackupFormat.isDeviceTag(""))
        assertFalse(BackupFormat.isDeviceTag("0A9F3C"))
        assertFalse(BackupFormat.isDeviceTag("0a9f3"))
        assertFalse(BackupFormat.isDeviceTag("0a9f3cd"))
        assertFalse(BackupFormat.isDeviceTag("../../x"))
        assertFalse(BackupFormat.isDeviceTag("zzzzzz"))
    }

    @Test
    fun `manifest with seconds and device tag survives packing`() {
        val m = BackupManifest(1, "2.3.3-fold8.35", 290, "2026-11-11 14:05:09", 0, 0, "TOUCH_STANDARD", "Touch (Standard)", "a1b2c3")
        val back = ok(BackupFormat.pack(m, emptyList(), emptyList()))
        assertEquals(m, back.manifest)
        assertEquals("a1b2c3", back.manifest.device)
        assertEquals("2026-11-11 14:05:09", back.manifest.createdAt)
    }

    @Test
    fun `an old backup without device tag and seconds is still read`() {
        // manifest() is the old form: createdAt "2026-11-10 12:00", no device field
        val back = ok(zip("manifest.json" to manifest()))
        assertEquals("", back.manifest.device)
        assertEquals("2026-11-10 12:00", back.manifest.createdAt)
    }

    @Test
    fun `a device field that is not a tag is dropped`() {
        val text = """{"format":"${BackupFormat.FORMAT_NAME}","formatVersion":1,"createdAt":"2026-11-11 14:05:09","device":"../../etc"}"""
        assertEquals("", ok(zip("manifest.json" to text)).manifest.device)
    }
}

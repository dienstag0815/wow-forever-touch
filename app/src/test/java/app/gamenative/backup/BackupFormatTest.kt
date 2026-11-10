package app.gamenative.backup

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
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
    fun `file name carries the date`() {
        assertEquals("wow-forever-touch-backup-2026-11-10.zip", BackupFormat.fileName("2026-11-10"))
    }
}

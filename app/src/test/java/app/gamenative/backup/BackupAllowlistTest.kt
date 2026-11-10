package app.gamenative.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupAllowlistTest {
    private fun spec(type: SettingType) = SettingSpec("f", "k", type)

    @Test
    fun `no entry carries a forbidden name part`() {
        for (s in BackupAllowlist.ALL) {
            for (part in BackupAllowlist.FORBIDDEN_NAME_PARTS) {
                assertFalse("${s.file}/${s.key} enthaelt '$part'", s.key.lowercase().contains(part))
                assertFalse("${s.file}/${s.key} enthaelt '$part'", s.file.lowercase().contains(part))
            }
        }
    }

    @Test
    fun `no entry comes from a forbidden file`() {
        for (s in BackupAllowlist.ALL) {
            assertFalse(s.file, s.file in BackupAllowlist.FORBIDDEN_FILES)
        }
    }

    @Test
    fun `file and key pairs are unique`() {
        val pairs = BackupAllowlist.ALL.map { it.file to it.key }
        assertEquals(pairs.size, pairs.toSet().size)
    }

    @Test
    fun `find returns exactly the listed entries`() {
        assertNotNull(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, "region"))
        assertNull(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, "game_path"))
        assertNull(BackupAllowlist.find("battle_net_login", "email"))
        assertNull(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, "gibt_es_nicht"))
        // Gleicher Schluessel in einer anderen Datei ist ein anderer Eintrag.
        assertNotNull(BackupAllowlist.find(BackupAllowlist.FILE_AUTOSTART, "enabled"))
        assertNotNull(BackupAllowlist.find(BackupAllowlist.FILE_COOLDOWN, "enabled"))
        assertNull(BackupAllowlist.find(BackupAllowlist.FILE_ART, "enabled"))
    }

    @Test
    fun `input mode and app language are on the list`() {
        assertNotNull(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, BackupAllowlist.KEY_INPUT_MODE))
        assertNotNull(BackupAllowlist.find(BackupAllowlist.DATASTORE, BackupAllowlist.KEY_APP_LANGUAGE))
    }

    @Test
    fun `files lists each file once in order of appearance`() {
        assertEquals(BackupAllowlist.FILES.size, BackupAllowlist.FILES.toSet().size)
        assertEquals(BackupAllowlist.FILE_MAIN, BackupAllowlist.FILES.first())
        assertTrue(BackupAllowlist.DATASTORE in BackupAllowlist.FILES)
    }

    @Test
    fun `coerce bool accepts only booleans`() {
        val s = spec(SettingType.BOOL)
        assertEquals(true, BackupAllowlist.coerce(s, true))
        assertNull(BackupAllowlist.coerce(s, "true"))
        assertNull(BackupAllowlist.coerce(s, 1))
        assertNull(BackupAllowlist.coerce(s, null))
    }

    @Test
    fun `coerce string accepts only strings within the limit`() {
        val s = spec(SettingType.STRING)
        assertEquals("EU", BackupAllowlist.coerce(s, "EU"))
        assertEquals("", BackupAllowlist.coerce(s, ""))
        assertNull(BackupAllowlist.coerce(s, 5))
        assertNull(BackupAllowlist.coerce(s, "x".repeat(BackupAllowlist.MAX_STRING + 1)))
        assertNotNull(BackupAllowlist.coerce(s, "x".repeat(BackupAllowlist.MAX_STRING)))
    }

    @Test
    fun `coerce int accepts whole numbers in range`() {
        val s = spec(SettingType.INT)
        assertEquals(5, BackupAllowlist.coerce(s, 5))
        assertEquals(5, BackupAllowlist.coerce(s, 5L))
        assertEquals(5, BackupAllowlist.coerce(s, 5.0))
        assertNull(BackupAllowlist.coerce(s, 5.5))
        assertNull(BackupAllowlist.coerce(s, Long.MAX_VALUE))
        assertNull(BackupAllowlist.coerce(s, Double.NaN))
        assertNull(BackupAllowlist.coerce(s, "5"))
        assertNull(BackupAllowlist.coerce(s, true))
    }

    @Test
    fun `coerce long keeps large whole numbers`() {
        val s = spec(SettingType.LONG)
        assertEquals(Long.MAX_VALUE, BackupAllowlist.coerce(s, Long.MAX_VALUE))
        assertEquals(3_000_000_000L, BackupAllowlist.coerce(s, 3_000_000_000L))
        assertNull(BackupAllowlist.coerce(s, Double.POSITIVE_INFINITY))
        assertNull(BackupAllowlist.coerce(s, 1.0e20))
    }

    @Test
    fun `coerce float accepts finite numbers only`() {
        val s = spec(SettingType.FLOAT)
        assertEquals(1.25f, BackupAllowlist.coerce(s, 1.25))
        assertEquals(2f, BackupAllowlist.coerce(s, 2))
        assertNull(BackupAllowlist.coerce(s, Double.NaN))
        assertNull(BackupAllowlist.coerce(s, Double.POSITIVE_INFINITY))
        assertNull(BackupAllowlist.coerce(s, 1.0e300))
        assertNull(BackupAllowlist.coerce(s, "1.5"))
    }
}

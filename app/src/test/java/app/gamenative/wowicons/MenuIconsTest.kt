package app.gamenative.wowicons

import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowActionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuIconsTest {
    @Test
    fun `every menu action of the catalog has an icon name`() {
        val menus = WowActionCatalog.byGroup().getValue(WowActionGroup.MENUS)
        assertEquals(14, menus.size)
        for (action in menus) {
            assertNotNull("${action.command} has no icon", MenuIcons.nameFor(action.command))
        }
        assertEquals(menus.map { it.command }.toSet(), MenuIcons.commands.toSet())
    }

    @Test
    fun `the game menu template and all its fields have icons`() {
        for (command in WowActionCatalog.GAME_MENU_COMMANDS) {
            assertNotNull(command, MenuIcons.nameFor(command))
        }
    }

    @Test
    fun `icon names look like WoW icon names and are not used twice`() {
        val names = MenuIcons.commands.map { MenuIcons.nameFor(it)!! }
        assertEquals(names.size, names.toSet().size)
        for (name in names) {
            assertTrue(name, name.isNotEmpty() && name.all { it in 'a'..'z' || it in '0'..'9' || it == '_' })
        }
        assertEquals("inv_misc_map_01", MenuIcons.nameFor("TOGGLEWORLDMAP"))
        assertEquals("inv_misc_book_09", MenuIcons.nameFor("TOGGLESPELLBOOK"))
        assertEquals("inv_misc_bag_08", MenuIcons.nameFor("OPENALLBAGS"))
    }

    @Test
    fun `other actions have no menu icon`() {
        assertNull(MenuIcons.nameFor("ACTIONBUTTON1"))
        assertNull(MenuIcons.nameFor("EMOTE_DANCE"))
        assertNull(MenuIcons.nameFor(null))
    }

    @Test
    fun `refs are loaded by name and go through the render and wowhead sources`() {
        val refs = MenuIcons.refs()
        assertEquals(14, refs.size)
        assertTrue(refs.all { it.name != null && it.fileId == null && it.nameKey == null })
        assertEquals("https://render.worldofwarcraft.com/us/icons/56/inv_misc_map_01.jpg", IconUrls.render("inv_misc_map_01"))
        assertEquals("https://wow.zamimg.com/images/wow/icons/large/inv_misc_map_01.jpg", IconUrls.zamimg("inv_misc_map_01"))
    }

    @Test
    fun `an icon by name is loaded once, cached and found again`() {
        val dir = java.nio.file.Files.createTempDirectory("menuicons").toFile()
        try {
            val cache = IconCache(dir)
            val jpeg = ByteArray(64) { if (it == 0) 0xFF.toByte() else if (it == 1) 0xD8.toByte() else 1 }
            var calls = 0
            val http = IconHttp { url ->
                calls++
                assertTrue(url, url.startsWith("https://render.worldofwarcraft.com/us/icons/56/inv_misc_map_01"))
                IconResponse(200, jpeg)
            }
            val loader = IconLoader(cache, http)
            val ref = IconRef(name = "inv_misc_map_01")
            assertTrue(loader.load(ref) is IconResult.Loaded)
            assertTrue(loader.isCached(ref))
            assertNotNull(cache.lookup(ref))
            assertTrue(loader.load(ref) is IconResult.Loaded)
            assertEquals(1, calls)
        } finally {
            dir.deleteRecursively()
        }
    }
}

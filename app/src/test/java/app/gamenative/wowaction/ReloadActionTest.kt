package app.gamenative.wowaction

import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.PaletteKind
import app.gamenative.ui.screen.xserver.editor.PreviewKind
import com.winlator.inputcontrols.Binding
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReloadActionTest {
    @Before
    fun setUp() {
        ReloadGuard.reset()
    }

    private fun repoFile(path: String): File =
        File("src/main/$path").takeIf { it.isFile } ?: File("app/src/main/$path")

    private val reload get() = WowKeys.toCombo(ReloadGuard.RELOAD_KEY)!!

    // ---- catalog / palette ---------------------------------------------------------------------------------

    @Test
    fun theReloadActionIsInTheCatalogWithItsKeyAndSymbol() {
        val action = WowActionCatalog.find(ReloadGuard.ACTION_ID)
        assertNotNull(action)
        assertEquals(WowActionGroup.INTERFACE, action!!.group)
        assertEquals("CTRL-SHIFT-F4", action.defaultKey)
        assertEquals("reload", action.symbol)
        assertTrue(action.symbol in WowSymbols.KNOWN)
        assertEquals("🔄", action.emoji)
        // the key is nobody else's, and it can be sent (Ctrl+Shift+F4)
        val others = WowActionCatalog.all.filter { it.command != action.command }.mapNotNull { it.defaultKey }
        assertFalse("CTRL-SHIFT-F4" in others)
        assertEquals(listOf(Binding.KEY_CTRL_L, Binding.KEY_SHIFT_L, Binding.KEY_F4), reload.bindings)
        // it is not a Bindings.xml command (those start with WFB_ and are checked against the XML)
        assertFalse(action.command.startsWith("WFB_"))
    }

    @Test
    fun theReloadKeyIsResolvedLikeAnyOtherAction() {
        val resolved = WowActionResolver.resolve(WowActionCatalog.find(ReloadGuard.ACTION_ID)!!, null)!!
        assertEquals("CTRL-SHIFT-F4", resolved.wowKey)
        assertEquals(WowKeySource.DEFAULT, resolved.source)
    }

    @Test
    fun theGalleryOffersTheReloadButton() {
        assertTrue(PaletteKind.RELOAD_BUTTON in PaletteKind.available())
        assertEquals(PreviewKind.RELOAD, PaletteKind.RELOAD_BUTTON.preview)
        assertEquals(R.string.editor_kind_reload, PaletteKind.RELOAD_BUTTON.title)
    }

    @Test
    fun theAddonBindsTheSameKeyToItsSecureButton() {
        val lua = repoFile("assets/wow/addon/TouchForever/TouchForever.lua").readText()
        assertTrue(lua.contains("ns.RELOAD_KEY = \"${ReloadGuard.RELOAD_KEY}\""))
        assertTrue(lua.contains("\"TouchForeverReloadKey\""))
        assertTrue(lua.contains("SetOverrideBindingClick(reloadKeyButton, true, ns.RELOAD_KEY, \"TouchForeverReloadKey\", \"LeftButton\")"))
        assertTrue(lua.contains("SecureActionButtonTemplate"))
    }

    // ---- double tap lock -----------------------------------------------------------------------------------

    @Test
    fun aSecondPressWithinTwoSecondsIsDroppedWithItsRelease() {
        assertTrue(ReloadGuard.allow(reload, true, 10_000L, reload))
        assertTrue(ReloadGuard.allow(reload, false, 10_080L, reload))
        assertFalse(ReloadGuard.allow(reload, true, 10_500L, reload))   // double tap
        assertFalse(ReloadGuard.allow(reload, false, 10_560L, reload))  // its release as well
        assertFalse(ReloadGuard.allow(reload, true, 11_999L, reload))
        assertFalse(ReloadGuard.allow(reload, false, 12_000L, reload))
    }

    @Test
    fun afterTwoSecondsTheKeyWorksAgain() {
        assertTrue(ReloadGuard.allow(reload, true, 10_000L, reload))
        assertTrue(ReloadGuard.allow(reload, false, 10_080L, reload))
        assertTrue(ReloadGuard.allow(reload, true, 12_000L, reload))
        assertTrue(ReloadGuard.allow(reload, false, 12_080L, reload))
    }

    @Test
    fun otherKeysAreNeverLocked() {
        val other = WowKeys.toCombo("CTRL-SHIFT-F5")!!
        assertTrue(ReloadGuard.allow(reload, true, 10_000L, reload))
        assertTrue(ReloadGuard.allow(other, true, 10_010L, reload))
        assertTrue(ReloadGuard.allow(other, false, 10_020L, reload))
        // no reload key resolved: nothing is locked
        assertTrue(ReloadGuard.allow(reload, true, 10_030L, null))
    }

    // ---- order: the addon is installed before the game is prepared further ---------------------------------

    @Test
    fun prepareLaunchInstallsTheAddonBeforeContainerAndGameStart() {
        val text = repoFile("java/app/gamenative/ui/screen/wow/WoWForeverScreen.kt").readText()
        val body = text.substring(text.indexOf("private fun prepareLaunch("))
        val install = body.indexOf("AddonSetup.run(context, gameRoot)")
        val icons = body.indexOf("WowIcons.start(context, gameRoot)")
        val container = body.indexOf("ContainerManager(context)")
        val config = body.indexOf("ensureGameConfig(")
        assertTrue("addon install present", install > 0)
        assertTrue("after the game config (Config.wtf exists)", install > config)
        assertTrue("before the icons", install < icons)
        assertTrue("before the container is prepared", install < container)
        // the icon code does not install any more (it used to, and only when the icon setting was on)
        val icons2 = repoFile("java/app/gamenative/wowicons/WowIcons.kt").readText()
        assertFalse(icons2.contains("installAddon"))
        assertFalse(icons2.contains("BridgeInstaller.install"))
    }
}

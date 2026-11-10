package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonLinkResolverTest {

    private fun unsupported(input: String?): UiText {
        val r = resolveAddonLink(input)
        assertTrue("Erwartet Unsupported, war $r", r is AddonLink.Unsupported)
        return (r as AddonLink.Unsupported).reason
    }

    // ---- direkte Zip-Links -------------------------------------------------------------------------------------------

    @Test
    fun directZipOnAnyPublicHost() {
        assertEquals(
            AddonLink.DirectZip("https://example.org/files/Demo-1.0.zip"),
            resolveAddonLink("https://example.org/files/Demo-1.0.zip"),
        )
        assertEquals(
            AddonLink.DirectZip("https://edge.forgecdn.net/files/1/2/Demo.ZIP?x=1"),
            resolveAddonLink("  https://edge.forgecdn.net/files/1/2/Demo.ZIP?x=1#frag  "),
        )
    }

    @Test
    fun linkWithoutSchemeGetsHttps() {
        assertEquals(AddonLink.DirectZip("https://example.org/a.zip"), resolveAddonLink("example.org/a.zip"))
        assertEquals(AddonLink.GitHubRepo("Questie", "Questie", null), resolveAddonLink("github.com/Questie/Questie"))
    }

    @Test
    fun onlyHttps() {
        assertEquals(AddonLinkTexts.NOT_HTTPS, unsupported("http://example.org/a.zip"))
        assertEquals(AddonLinkTexts.NOT_HTTPS, unsupported("ftp://example.org/a.zip"))
        assertEquals(AddonLinkTexts.NOT_HTTPS, unsupported("file:///sdcard/a.zip"))
        assertEquals(UiText.res(R.string.launcher_addon_link_not_a_link, AddonLinkTexts.SUPPORTED), unsupported("javascript:alert(1)"))
    }

    @Test
    fun zipOnLocalOrNumericHostsIsRefused() {
        unsupported("https://localhost/a.zip")
        unsupported("https://192.168.0.5/a.zip")
        unsupported("https://printer.local/a.zip")
        unsupported("https://[::1]/a.zip")
    }

    @Test
    fun userInfoAndOddPortsAreRefused() {
        unsupported("https://user:pw@example.org/a.zip")
        unsupported("https://example.org:8443/a.zip")
        assertEquals(AddonLink.DirectZip("https://example.org/a.zip"), resolveAddonLink("https://example.org:443/a.zip"))
    }

    @Test
    fun emptyAndGarbageGiveFriendlyMessages() {
        assertEquals(AddonLinkTexts.EMPTY, unsupported(null))
        assertEquals(AddonLinkTexts.EMPTY, unsupported("   "))
        assertEquals(UiText.res(R.string.launcher_addon_link_has_spaces, AddonLinkTexts.SUPPORTED), unsupported("hallo welt"))
        assertEquals(UiText.res(R.string.launcher_addon_link_not_a_link, AddonLinkTexts.SUPPORTED), unsupported("hallo"))
    }

    @Test
    fun otherSitesNameTheSupportedKinds() {
        // Die Meldung hängt die Liste der unterstützten Arten ([AddonLinkTexts.SUPPORTED]) an.
        val reason = unsupported("https://example.org/download/page")
        assertEquals(UiText.res(R.string.launcher_addon_link_cannot_load, AddonLinkTexts.SUPPORTED), reason)
    }

    // ---- GitHub ------------------------------------------------------------------------------------------------------

    @Test
    fun gitHubRepoLinks() {
        val repo = AddonLink.GitHubRepo("Stanzilla", "AdvancedInterfaceOptions", null)
        assertEquals(repo, resolveAddonLink("https://github.com/Stanzilla/AdvancedInterfaceOptions"))
        assertEquals(repo, resolveAddonLink("https://github.com/Stanzilla/AdvancedInterfaceOptions/"))
        assertEquals(repo, resolveAddonLink("https://github.com/Stanzilla/AdvancedInterfaceOptions.git"))
        assertEquals(repo, resolveAddonLink("https://www.github.com/Stanzilla/AdvancedInterfaceOptions/releases"))
        assertEquals(repo, resolveAddonLink("https://github.com/Stanzilla/AdvancedInterfaceOptions/releases/latest"))
        assertEquals(repo, resolveAddonLink("https://github.com/Stanzilla/AdvancedInterfaceOptions/tree/master/Sub"))
        assertEquals("Stanzilla/AdvancedInterfaceOptions", repo.slug)
    }

    @Test
    fun gitHubReleaseTagLinkPinsTheTag() {
        assertEquals(
            AddonLink.GitHubRepo("Stanzilla", "AdvancedInterfaceOptions", "2.1.14"),
            resolveAddonLink("https://github.com/Stanzilla/AdvancedInterfaceOptions/releases/tag/2.1.14"),
        )
        assertEquals(
            AddonLink.GitHubRepo("BigWigsMods", "BigWigs", "bundle/v1"),
            resolveAddonLink("https://github.com/BigWigsMods/BigWigs/releases/tag/bundle/v1"),
        )
    }

    @Test
    fun gitHubZipLinksAreDirect() {
        val asset = "https://github.com/Stanzilla/AdvancedInterfaceOptions/releases/download/2.1.14/AIO-2.1.14.zip"
        assertEquals(AddonLink.DirectZip(asset), resolveAddonLink(asset))
        val archive = "https://github.com/Stanzilla/AdvancedInterfaceOptions/archive/refs/heads/master.zip"
        assertEquals(AddonLink.DirectZip(archive), resolveAddonLink(archive))
        val zipball = "https://api.github.com/repos/Stanzilla/AdvancedInterfaceOptions/zipball/master"
        assertEquals(AddonLink.DirectZip(zipball), resolveAddonLink(zipball))
    }

    @Test
    fun gitHubPagesWithoutRepoAreRefused() {
        assertEquals(UiText.res(R.string.launcher_addon_link_not_repo), unsupported("https://github.com"))
        assertEquals(UiText.res(R.string.launcher_addon_link_not_repo), unsupported("https://github.com/Stanzilla"))
        assertEquals(UiText.res(R.string.launcher_addon_link_not_repo), unsupported("https://github.com/settings/profile"))
        assertEquals(UiText.res(R.string.launcher_addon_link_not_repo), unsupported("https://github.com/topics/wow-addon"))
        unsupported("https://api.github.com/user")
    }

    // ---- WoWInterface ------------------------------------------------------------------------------------------------

    @Test
    fun wowInterfaceDownloadPages() {
        val expected = AddonLink.WowInterface("24910")
        assertEquals(expected, resolveAddonLink("https://www.wowinterface.com/downloads/info24910-WeakAuras2.html"))
        assertEquals(expected, resolveAddonLink("https://wowinterface.com/downloads/info24910"))
        assertEquals(expected, resolveAddonLink("https://www.wowinterface.com/downloads/info24910#comments"))
        assertEquals(expected, resolveAddonLink("https://www.wowinterface.com/downloads/download24910-WeakAuras2"))
        assertEquals(expected, resolveAddonLink("https://www.wowinterface.com/downloads/fileinfo.php?id=24910"))
        assertEquals(expected, resolveAddonLink("https://cdn.wowinterface.com/downloads/getfile.php?id=24910"))
        assertEquals(expected, resolveAddonLink("https://cdn.wowinterface.com/downloads/getfile.php?s=1&id=24910&d=1791652888"))
    }

    @Test
    fun wowInterfaceOtherPagesAreRefused() {
        assertEquals(UiText.res(R.string.launcher_addon_link_wowi_not_download), unsupported("https://www.wowinterface.com/"))
        assertEquals(UiText.res(R.string.launcher_addon_link_wowi_not_download), unsupported("https://www.wowinterface.com/downloads/"))
        assertEquals(UiText.res(R.string.launcher_addon_link_wowi_not_download), unsupported("https://www.wowinterface.com/downloads/fileinfo.php?id=abc"))
        assertEquals(UiText.res(R.string.launcher_addon_link_wowi_not_download), unsupported("https://www.wowinterface.com/forums/showthread.php?t=1"))
    }

    @Test
    fun wowInterfaceCdnZipIsDirect() {
        assertEquals(
            AddonLink.DirectZip("https://cdn.wowinterface.com/downloads/file24910/WeakAuras.zip"),
            resolveAddonLink("https://cdn.wowinterface.com/downloads/file24910/WeakAuras.zip"),
        )
    }

    // ---- CurseForge und Wago -----------------------------------------------------------------------------------------

    @Test
    fun curseForgeNeedsTheBrowser() {
        val r = resolveAddonLink("https://www.curseforge.com/wow/addons/deadly-boss-mods")
        assertTrue(r is AddonLink.NeedsBrowser)
        r as AddonLink.NeedsBrowser
        assertEquals("CurseForge", r.site)
        assertEquals("https://www.curseforge.com/wow/addons/deadly-boss-mods", r.pageUrl)
        assertEquals(AddonLinkTexts.needsBrowser("CurseForge"), r.message)
        assertTrue(resolveAddonLink("https://curseforge.com/wow/addons/x/files/123") is AddonLink.NeedsBrowser)
        assertTrue(resolveAddonLink("https://legacy.curseforge.com/wow/addons/x") is AddonLink.NeedsBrowser)
        // auch wenn der Link wie ein Zip aussieht: die Seite braucht den Browser
        assertTrue(resolveAddonLink("https://www.curseforge.com/wow/addons/x/files/1/download.zip") is AddonLink.NeedsBrowser)
    }

    @Test
    fun wagoNeedsTheBrowser() {
        val r = resolveAddonLink("https://addons.wago.io/addons/plater-nameplates")
        assertTrue(r is AddonLink.NeedsBrowser)
        assertEquals("Wago", (r as AddonLink.NeedsBrowser).site)
        assertTrue(resolveAddonLink("https://wago.io/xyz") is AddonLink.NeedsBrowser)
    }

    @Test
    fun lookAlikeHostsAreNotCurseForge() {
        // Ein Host, der nur so heißt, ist ein gewöhnlicher Host: ohne Zip nicht unterstützt, mit Zip direkt.
        unsupported("https://notcurseforge.com/wow/addons/x")
        assertTrue(resolveAddonLink("https://notcurseforge.com/x.zip") is AddonLink.DirectZip)
        // Die CurseForge-CDN ist ein normaler Zip-Host.
        assertTrue(resolveAddonLink("https://mediafilez.forgecdn.net/files/1/2/Demo.zip") is AddonLink.DirectZip)
    }

    @Test
    fun hostRuleForUserLinks() {
        assertTrue(AddonHosts.isAllowed("https://example.org/a.zip", AddonHosts.ANY_PUBLIC_HTTPS))
        assertTrue(!AddonHosts.isAllowed("http://example.org/a.zip", AddonHosts.ANY_PUBLIC_HTTPS))
        assertTrue(!AddonHosts.isAllowed("https://127.0.0.1/a.zip", AddonHosts.ANY_PUBLIC_HTTPS))
        assertTrue(!AddonHosts.isAllowed("https://localhost/a.zip", AddonHosts.ANY_PUBLIC_HTTPS))
        // die feste Liste bleibt streng
        assertTrue(!AddonHosts.isAllowed("https://example.org/a.zip", AddonHosts.GITHUB))
    }
}

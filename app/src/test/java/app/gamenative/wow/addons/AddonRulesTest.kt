package app.gamenative.wow.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonRulesTest {

    // ---- Hosts ----------------------------------------------------------------------------------------------

    @Test
    fun theFixedHostsAreAllowed() {
        for (host in AddonHosts.GITHUB) assertTrue(host, AddonHosts.isAllowed("https://$host/x/y"))
        for (host in AddonHosts.WOWINTERFACE) assertTrue(host, AddonHosts.isAllowed("https://$host/x/y"))
        assertTrue(AddonHosts.isAllowed("https://GitHub.com/Questie/Questie/releases/download/v1/a.zip"))
        assertTrue(AddonHosts.isAllowed("https://github.com:443/a"))
        assertTrue(AddonHosts.isAllowed("https://release-assets.githubusercontent.com/github-production-release-asset/1?sig=a%2Bb"))
    }

    @Test
    fun theHostListIsExactlyTheAgreedOne() {
        assertEquals(
            setOf("github.com", "api.github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"),
            AddonHosts.GITHUB,
        )
        assertEquals(setOf("wowinterface.com", "www.wowinterface.com", "cdn.wowinterface.com", "api.mmoui.com"), AddonHosts.WOWINTERFACE)
    }

    @Test
    fun otherHostsSchemesAndTrickeryAreRefused() {
        val bad = listOf(
            null, "", "   ", "http://github.com/a", "ftp://github.com/a", "file:///etc/passwd", "//github.com/a", "github.com/a",
            "https://evil.example/a", "https://github.com.evil.example/a", "https://evilgithub.com/a", "https://notgithub.com/a",
            "https://raw.githubusercontent.com/a/b/c", "https://gist.github.com/a", "https://curseforge.com/a",
            "https://user@github.com/a", "https://github.com@evil.example/a", "https://github.com:8443/a", "https://github.com:80/a",
            "https://github.com\\@evil.example/a", "https://127.0.0.1/a", "https://localhost/a", "javascript:alert(1)",
        )
        for (url in bad) assertFalse("sollte abgelehnt werden: $url", AddonHosts.isAllowed(url))
    }

    @Test
    fun sourcesCannotCrossOver() {
        val gh = AddonSource.GitHub("o", "r", "x")
        val wi = AddonSource.WowInterface("1")
        assertTrue(AddonHosts.isAllowed("https://github.com/a", AddonHosts.forSource(gh)))
        assertFalse(AddonHosts.isAllowed("https://cdn.wowinterface.com/a", AddonHosts.forSource(gh)))
        assertTrue(AddonHosts.isAllowed("https://cdn.wowinterface.com/a", AddonHosts.forSource(wi)))
        assertFalse(AddonHosts.isAllowed("https://github.com/a", AddonHosts.forSource(wi)))
    }

    // ---- Versionen ------------------------------------------------------------------------------------------

    @Test
    fun versionsCompareByNumbers() {
        assertEquals(0, AddonVersion.compare("v1.0", "1"))
        assertTrue(AddonVersion.compare("v1.9", "v1.10")!! < 0)
        assertTrue(AddonVersion.compare("v427.9", "v427.10")!! < 0)
        assertTrue(AddonVersion.compare("v2026.41", "v2026.9")!! > 0)
        assertTrue(AddonVersion.compare("bundle/v12.6.3+v1.0.12", "bundle/v12.6.4+v1.0.12")!! < 0)
        assertTrue(AddonVersion.compare("bundle/v12.6.4+v1.0.11", "bundle/v12.6.4+v1.0.12")!! < 0)
        assertTrue(AddonVersion.compare("bundle/v12.6.4+v1.0.12", "bundle/v12.6.4+v1.0.12")!! == 0)
        assertNull(AddonVersion.compare("alpha", "beta"))
    }

    @Test
    fun hugeNumbersDoNotCrash() {
        assertNull(AddonVersion.parts("abc"))
        assertEquals(listOf(Long.MAX_VALUE), AddonVersion.parts("v99999999999999999999999"))
    }

    @Test
    fun updateDecision() {
        assertTrue(AddonVersion.isUpdate(null, "v1"))
        assertTrue(AddonVersion.isUpdate("", "v1"))
        assertFalse(AddonVersion.isUpdate("v1.2", "v1.2"))
        assertTrue(AddonVersion.isUpdate("v1.2", "v1.3"))
        assertFalse("ältere Version ist kein Update", AddonVersion.isUpdate("v1.3", "v1.2"))
        assertFalse(AddonVersion.isUpdate("v1.0", "1"))
        assertTrue("ohne Zahlen zählt jeder Unterschied", AddonVersion.isUpdate("alpha", "beta"))
        assertFalse(AddonVersion.isUpdate("beta", "beta"))
    }

    @Test
    fun displayDropsThePathPart() {
        assertEquals("v12.6.4+v1.0.12", AddonVersion.display("bundle/v12.6.4+v1.0.12"))
        assertEquals("v1.2", AddonVersion.display("v1.2"))
    }
}

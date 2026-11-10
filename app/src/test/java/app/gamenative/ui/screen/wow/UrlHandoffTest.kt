package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlHandoffTest {
    private fun ok(url: String) = assertEquals(url, UrlHandoff.parseUrl(url))

    private fun bad(url: String?) = assertNull("sollte abgelehnt werden: $url", UrlHandoff.parseUrl(url))

    @Test
    fun acceptsHttpAndHttps() {
        ok("https://account.battle.net/")
        ok("http://example.com")
        ok("https://account.battle.net/login?ref=wow&lang=de#top")
        ok("https://eu.battle.net:8443/path/x.html")
        ok("https://sub-domain.example.co.uk/a%20b?q=%C3%A4")
    }

    @Test
    fun schemeIsCaseInsensitiveAndKeptAsGiven() {
        ok("HTTPS://Account.Battle.Net/")
        ok("Http://example.com/")
    }

    @Test
    fun stripsQuotesAndWhitespaceFromCmdEcho() {
        assertEquals("https://account.battle.net/", UrlHandoff.parseUrl("\"https://account.battle.net/\""))
        assertEquals("https://account.battle.net/", UrlHandoff.parseUrl("  \"https://account.battle.net/\" \r"))
        assertEquals("http://a.de", UrlHandoff.parseUrl("\t http://a.de  "))
    }

    @Test
    fun rejectsOtherSchemes() {
        bad("file:///sdcard/x")
        bad("javascript://example.com/%0aalert(1)")
        bad("intent://scan/#Intent;scheme=zxing;end")
        bad("market://details?id=com.blizzard.bma")
        bad("content://media/external/images")
        bad("ftp://example.com/")
        bad("tel:+49123456")
        bad("sms:123")
        bad("battlenet://wow")
        bad("httpx://example.com")
        bad("xhttp://example.com")
        bad("//example.com/x")
        bad("example.com/x")
        bad("C:\\windows\\system32\\cmd.exe")
    }

    @Test
    fun rejectsMissingOrBrokenHosts() {
        bad("https://")
        bad("https:///path")
        bad("https://?q=1")
        bad("https://.example.com/")
        bad("https://example..com/")
        bad("https://-example.com/")
        bad("https://example-.com/")
        bad("https://exa mple.com/")
        bad("https://exa_mple.com/")
        bad("https://[::1]/")
        bad("https://example.com:/")
        bad("https://example.com:0/")
        bad("https://example.com:99999/")
        bad("https://example.com:80a/")
        bad("https://a:1:2/")
    }

    @Test
    fun rejectsUserInfoInFrontOfTheHost() {
        bad("https://account.battle.net@evil.example/")
        bad("https://user:pw@example.com/")
        // ein @ im Pfad ist erlaubt
        ok("https://example.com/@user")
    }

    @Test
    fun rejectsUnsafeCharacters() {
        bad("https://example.com/a b")
        bad("https://example.com/a\"b")
        bad("https://example.com/<script>")
        bad("https://example.com/a\\b")
        bad("https://example.com/a`b")
        bad("https://example.com/a|b")
        bad("https://example.com/\u0001")
        bad("https://example.com/ä")
        bad("https://example.com/a\nb")
    }

    @Test
    fun rejectsEmptyNullAndTooLong() {
        bad(null)
        bad("")
        bad("   ")
        bad("\"\"")
        ok("https://example.com/" + "a".repeat(UrlHandoff.MAX_URL_LENGTH - "https://example.com/".length))
        bad("https://example.com/" + "a".repeat(UrlHandoff.MAX_URL_LENGTH))
    }

    @Test
    fun parseLinesTakesOneValidUrlPerLine() {
        val text = "\"https://account.battle.net/\" \r\n" +
            "garbage\r\n" +
            "\"file:///etc/passwd\" \r\n" +
            "\"http://example.com/x?a=1&b=2\" \r\n"
        assertEquals(listOf("https://account.battle.net/", "http://example.com/x?a=1&b=2"), UrlHandoff.parseLines(text))
    }

    @Test
    fun parseLinesDropsDuplicatesAndLimitsTheCount() {
        val dup = "\"https://a.de/\"\r\n\"https://a.de/\"\r\n"
        assertEquals(listOf("https://a.de/"), UrlHandoff.parseLines(dup))
        val many = (1..10).joinToString("\r\n") { "\"https://a$it.de/\"" }
        assertEquals(3, UrlHandoff.parseLines(many).size)
    }

    @Test
    fun parseLinesOfNothingIsEmpty() {
        assertTrue(UrlHandoff.parseLines("").isEmpty())
        assertTrue(UrlHandoff.parseLines("\r\n\r\n").isEmpty())
    }

    @Test
    fun hostOfGivesOnlyTheHost() {
        assertEquals("account.battle.net", UrlHandoff.hostOf("https://account.battle.net/login?x=1"))
        assertEquals("example.com", UrlHandoff.hostOf("http://example.com:8080/x"))
        assertEquals("example.com", UrlHandoff.hostOf("https://example.com"))
        assertEquals("example.com", UrlHandoff.hostOf("https://example.com?x=1"))
    }

    @Test
    fun handlerCommandRunsCmdAndAppendsTheQuotedUrl() {
        val command = UrlHandoff.HANDLER_COMMAND
        assertTrue(command.startsWith("C:\\windows\\system32\\cmd.exe /c echo "))
        assertTrue(command.contains("\"%1\">>"))
        assertTrue(command.endsWith("C:\\wowforever\\open_url.txt"))
    }
}

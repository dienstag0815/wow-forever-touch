package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserChoiceTest {
    private val own = "app.wowforever"

    @Test
    fun whenTheDefaultHandlerIsABrowserItIsTaken() {
        val target = BrowserChoice.pick(listOf("org.mozilla.firefox", "com.android.chrome"), "com.android.chrome", own)
        assertEquals(BrowserTarget.Browser("com.android.chrome"), target)
    }

    @Test
    fun whenTheDefaultIsAnotherAppTheFirstBrowserIsTaken() {
        // Die App, die github.com-Links abfängt, ist kein Kandidat für einen allgemeinen Link; sie ist der Standard-Handler.
        assertEquals(BrowserTarget.Browser("org.mozilla.firefox"), BrowserChoice.pick(listOf("org.mozilla.firefox", "com.android.chrome"), "com.github.android", own))
        // Kein Standard festgelegt: Android zeigt seine Auswahl.
        assertEquals(BrowserTarget.Browser("org.mozilla.firefox"), BrowserChoice.pick(listOf("org.mozilla.firefox", "com.android.chrome"), "android", own))
        assertEquals(BrowserTarget.Browser("org.mozilla.firefox"), BrowserChoice.pick(listOf("org.mozilla.firefox"), null, own))
    }

    @Test
    fun withoutAnyBrowserThereIsNoTargetAndTheCallerFallsBackToTheStandardWay() {
        assertEquals(BrowserTarget.None, BrowserChoice.pick(emptyList(), "com.github.android", own))
        assertEquals(BrowserTarget.None, BrowserChoice.pick(emptyList(), null, own))
        assertEquals(BrowserTarget.None, BrowserChoice.pick(listOf("", "  "), null, own))
    }

    @Test
    fun theOwnAppAndTheGithubAppNeverCount() {
        assertEquals(BrowserTarget.None, BrowserChoice.pick(listOf(own, "com.github.android"), own, own))
        assertEquals(BrowserTarget.Browser("org.mozilla.firefox"), BrowserChoice.pick(listOf("com.github.android", own, "org.mozilla.firefox"), "com.github.android", own))
    }

    @Test
    fun duplicatesAreHarmlessAndTheOrderOfTheCandidatesDecidesWithoutADefault() {
        assertEquals(BrowserTarget.Browser("a.b"), BrowserChoice.pick(listOf("a.b", "a.b", "c.d"), null, own))
        assertEquals(BrowserTarget.Browser("c.d"), BrowserChoice.pick(listOf("a.b", "a.b", "c.d"), "c.d", own))
    }
}

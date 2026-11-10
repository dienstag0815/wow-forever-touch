package app.gamenative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    @Test
    fun crashProcessIsRecognizedByItsSuffix() {
        assertTrue(CrashReport.isCrashProcess("app.wowforever:crash"))
        assertFalse(CrashReport.isCrashProcess("app.wowforever"))
        assertFalse(CrashReport.isCrashProcess(null))
        assertFalse(CrashReport.isCrashProcess(""))
    }

    @Test
    fun shouldShowHonoursTheMinimumGap() {
        assertTrue(CrashReport.shouldShow(0L, 1_000L))
        assertFalse(CrashReport.shouldShow(10_000L, 10_000L + CrashReport.MIN_GAP_MS - 1))
        assertTrue(CrashReport.shouldShow(10_000L, 10_000L + CrashReport.MIN_GAP_MS))
        // Uhr zurückgestellt: nicht für immer sperren
        assertTrue(CrashReport.shouldShow(10_000L, 5_000L))
    }

    @Test
    fun truncateLeavesShortTextAlone() {
        assertEquals("abc", CrashReport.truncate("abc", 10))
        assertEquals("", CrashReport.truncate("abc", 0))
    }

    @Test
    fun truncateKeepsHeadAndTailWithinLimit() {
        val text = "HEAD" + "x".repeat(5_000) + "Caused by: TAIL"
        val cut = CrashReport.truncate(text, 1_000)
        assertTrue(cut.length <= 1_000)
        assertTrue(cut.startsWith("HEAD"))
        assertTrue(cut.endsWith("Caused by: TAIL"))
        assertTrue(cut.contains("gekürzt"))
    }

    @Test
    fun truncateWithTinyLimitStillRespectsIt() {
        assertEquals(5, CrashReport.truncate("a".repeat(100), 5).length)
    }

    @Test
    fun buildHasHeaderAndTrace() {
        val text = CrashReport.build("2.3.3 (286)", "14", "Samsung - SM-F946B", "java.lang.IllegalStateException: boom\n\tat a.B.c(B.kt:1)")
        assertTrue(text.startsWith("App: 2.3.3 (286)\nAndroid: 14\nDevice: Samsung - SM-F946B\n"))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
        assertTrue(text.contains("at a.B.c(B.kt:1)"))
    }

    @Test
    fun buildRedactsEmailsAndTokens() {
        val trace = "java.io.IOException: login for someone@example.com failed, token=abcdef1234567890abcdef\n\tat a.B.c(B.kt:1)"
        val text = CrashReport.build("v", "14", "d", trace)
        assertFalse(text.contains("someone@example.com"))
        assertFalse(text.contains("abcdef1234567890abcdef"))
        assertTrue(text.contains("at a.B.c(B.kt:1)"))
    }

    @Test
    fun buildStaysWithinLimit() {
        val text = CrashReport.build("v", "14", "d", "Exception\n" + "\tat x.Y.z(Y.kt:1)\n".repeat(10_000), maxChars = 2_000)
        assertTrue(text.length <= 2_000)
    }
}

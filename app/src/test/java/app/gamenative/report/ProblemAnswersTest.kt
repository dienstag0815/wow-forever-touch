package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemAnswersTest {
    @Test
    fun unansweredQuestionsSayNotAnsweredAndAllFourLinesAreThere() {
        val lines = ProblemAnswers().lines()
        assertEquals(4, lines.size)
        assertTrue(lines.all { it.endsWith("not answered") })
    }

    @Test
    fun answersAppearAsWordsFromTheChoices() {
        val lines = ProblemAnswers(WorkedBefore.NO, OtherAppBefore.YES_FAILED, true, setOf(LoggedInElsewhere.BROWSER, LoggedInElsewhere.PC_APP)).lines()
        assertEquals("Login with this app on this device worked before: no", lines[0])
        assertEquals("Another app for this game used on this device before: yes, the login did not work there", lines[1])
        assertEquals("Account has an authenticator: yes", lines[2])
        // Reihenfolge der Auswahl ist fest, nicht die des Tippens.
        assertEquals("Logged in elsewhere shortly before: on a PC in the Battle.net app, in a browser", lines[3])
    }

    @Test
    fun onePartialAnswerLeavesTheOthersOpen() {
        val lines = ProblemAnswers(authenticator = false).lines()
        assertEquals("Account has an authenticator: no", lines[2])
        assertEquals(3, lines.count { it.endsWith("not answered") })
    }

    @Test
    fun nowhereElseExcludesTheOtherChoices() {
        val a = ProblemAnswers(elsewhere = setOf(LoggedInElsewhere.NONE, LoggedInElsewhere.BROWSER)).normalized()
        assertEquals(setOf(LoggedInElsewhere.BROWSER), a.elsewhere)
        assertEquals("Logged in elsewhere shortly before: nowhere else", ProblemAnswers(elsewhere = setOf(LoggedInElsewhere.NONE)).lines()[3])
    }

    @Test
    fun theAnswerTextsAreFixedChoicesNeverFreeText() {
        val all = WorkedBefore.values().map { it.text } + OtherAppBefore.values().map { it.text } + LoggedInElsewhere.values().map { it.text }
        assertFalse(all.any { it.contains("@") || it.contains("\n") })
    }
}

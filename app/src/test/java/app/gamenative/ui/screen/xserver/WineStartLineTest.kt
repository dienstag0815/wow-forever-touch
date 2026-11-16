package app.gamenative.ui.screen.xserver

import com.winlator.core.ProcessHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WineStartLineTest {
    @Test
    fun absolutePathWithoutArm64GetsWinhandlerExactlyOnce() {
        val line = WineStartLine.compose("L:\\Battle.net-Setup.exe", absolute = true)
        assertEquals("winhandler.exe \"L:\\Battle.net-Setup.exe\"", line)
        assertEquals(1, Regex("winhandler\\.exe").findAll(line).count())
    }

    @Test
    fun absolutePathWithArm64StaysAsBefore() {
        assertEquals("C:\\Wow\\Wow-ARM64.exe", WineStartLine.compose("C:\\Wow\\Wow-ARM64.exe", absolute = true))
        assertEquals(
            "\"C:\\World of Warcraft\\_retail_\\Wow-ARM64.exe\"",
            WineStartLine.compose("C:\\World of Warcraft\\_retail_\\Wow-ARM64.exe", absolute = true),
        )
        assertEquals("C:\\Wow\\wow-arm64.exe", WineStartLine.compose("C:\\Wow\\wow-arm64.exe", absolute = true))
    }

    @Test
    fun relativePathGetsWinhandlerAndDriveA() {
        assertEquals("winhandler.exe \"A:\\Game\\Sub\\game.exe\"", WineStartLine.compose("Game/Sub/game.exe", absolute = false))
    }

    @Test
    fun relativePathWithArm64HasNoWinhandler() {
        assertEquals("\"A:\\Game\\Wow-ARM64.exe\"", WineStartLine.compose("Game/Wow-ARM64.exe", absolute = false))
    }

    @Test
    fun directStartHasNoWinhandlerAndKeepsThePathWithoutSpaces() {
        assertEquals("L:\\Battle.net-Setup.exe", WineStartLine.compose("L:\\Battle.net-Setup.exe", absolute = true, direct = true))
        assertEquals("C:\\windows\\system32\\cmd.exe", WineStartLine.compose("C:\\windows\\system32\\cmd.exe", absolute = true, direct = true))
    }

    @Test
    fun directStartEscapesTheSpacesOfAPathWithSpacesInsteadOfQuotingIt() {
        val line = WineStartLine.compose("C:\\Program Files (x86)\\Battle.net\\Battle.net.exe", absolute = true, direct = true)
        assertEquals("C:\\Program\\ Files\\ (x86)\\Battle.net\\Battle.net.exe", line)
        assertFalse(line.contains("\""))
        assertFalse(line.contains("winhandler"))
    }

    @Test
    fun directStartLineWithSpacesIsOneArgumentThroughProcessHelperSplitCommand() {
        val line = "wine explorer /desktop=shell,1440x900 " +
            WineStartLine.compose("C:\\Program Files (x86)\\Battle.net\\Battle.net.exe", absolute = true, direct = true) +
            " --disable-gpu"
        val args = ProcessHelper.splitCommand(line).toList()
        assertEquals(
            listOf("wine", "explorer", "/desktop=shell,1440x900", "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe", "--disable-gpu"),
            args,
        )
    }

    @Test
    fun directStartLineWithoutSpacesIsOneArgumentThroughProcessHelperSplitCommand() {
        val line = "wine explorer /desktop=shell,1440x900 " +
            WineStartLine.compose("L:\\Battle.net-Setup.exe", absolute = true, direct = true) +
            " --disable-gpu"
        val args = ProcessHelper.splitCommand(line).toList()
        assertEquals(listOf("wine", "explorer", "/desktop=shell,1440x900", "L:\\Battle.net-Setup.exe", "--disable-gpu"), args)
    }

    @Test
    fun directStartIsTheSameFormAsTheArm64StartOfTheGame() {
        // Nur Pfade ohne Leerzeichen: mit Leerzeichen ist die direkte Form jetzt escaped, das Spiel-Format bleibt in Anführungszeichen.
        for (path in listOf("C:\\Wow\\Wow-ARM64.exe")) {
            assertEquals(WineStartLine.compose(path, absolute = true), WineStartLine.compose(path, absolute = true, direct = true))
        }
    }

    @Test
    fun theWholeLineWithSwitchesHasOnlyTheProgramThenTheSwitches() {
        // XServerScreen: "wine explorer /desktop=shell,<Größe> " + compose(...) + " " + execArgs
        val line = "wine explorer /desktop=shell,1440x900 " + WineStartLine.compose("L:\\Battle.net-Setup.exe", absolute = true, direct = true) + " --a \"b c\""
        assertEquals("wine explorer /desktop=shell,1440x900 L:\\Battle.net-Setup.exe --a \"b c\"", line)
    }

    @Test
    fun withoutTheSwitchTheGameStaysAsBefore() {
        // Das Spiel ruft compose nie mit direct = true auf; die Vorgabe ist false und ändert nichts.
        assertEquals("winhandler.exe \"C:\\Tools\\x.exe\"", WineStartLine.compose("C:\\Tools\\x.exe", absolute = true, direct = false))
        assertEquals(
            WineStartLine.compose("Game/Sub/game.exe", absolute = false),
            WineStartLine.compose("Game/Sub/game.exe", absolute = false, direct = false),
        )
    }

    @Test
    fun directStartAlsoWorksForRelativePaths() {
        assertEquals("\"A:\\Game\\game.exe\"", WineStartLine.compose("Game/game.exe", absolute = false, direct = true))
    }
}

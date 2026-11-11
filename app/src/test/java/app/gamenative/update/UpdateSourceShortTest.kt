package app.gamenative.update

import app.gamenative.ui.screen.wow.launcher.PillTone
import app.gamenative.update.SourceShort.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSourceShortTest {
    private fun form(url: String, active: Boolean, tester: Boolean, kind: SourceKind = SourceKind.Gitea) =
        UpdateSettings.SourceForm(kind, url, includePrereleases = false, active = active, tester = tester)

    @Test
    fun inactiveSourceIsOfficial() {
        assertEquals(SourceShort(Kind.Official, null), updateSourceShort(form("https://git.example.org/o/r", active = false, tester = false)))
    }

    @Test
    fun emptyAddressIsOfficialEvenWhenActive() {
        assertEquals(SourceShort(Kind.Official, null), updateSourceShort(form("", active = true, tester = false)))
    }

    @Test
    fun ownSourceShowsHost() {
        assertEquals(SourceShort(Kind.Own, "git.example.org"), updateSourceShort(form("https://git.example.org/o/r", active = true, tester = false)))
    }

    @Test
    fun testerShowsHost() {
        assertEquals(SourceShort(Kind.Tester, "git.example.org"), updateSourceShort(form("https://git.example.org/o/r", active = true, tester = true)))
    }

    @Test
    fun chipToneIsGreenForOfficialAndInfoForTheRest() {
        assertEquals(PillTone.Ok, sourceChipTone(Kind.Official))
        assertEquals(PillTone.Info, sourceChipTone(Kind.Tester))
        assertEquals(PillTone.Info, sourceChipTone(Kind.Own))
    }
}

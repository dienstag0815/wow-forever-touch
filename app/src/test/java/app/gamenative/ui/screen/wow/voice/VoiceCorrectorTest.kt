package app.gamenative.ui.screen.wow.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceCorrectorTest {
    private val list = """
        # Testliste
        Todesminen
        Kel'Thuzad
        Orgrimmar
        Sturmwind
        Eisenschmiede
        Schlingendorntal
        Naxxramas
        Ragnaros
        Hakkar
        Burning Steppes
        Zul'Gurub
        Un'Goro-Krater
        Dun Morogh
        Karazhan
        Scholomance
        Archimonde
        Stratholme
        LFG
        DPS
        Tank
        Healer
        Feuerball
        Portal
        Sartura
        Sarturi
        =Heiler
        =Gold
    """.trimIndent()

    private val vocabulary = VoiceVocabulary.parse(list)

    private fun fix(text: String, vocab: VoiceVocabulary = vocabulary) = VoiceCorrector.correct(text, vocab)

    @Test
    fun mergesSplitWords() {
        assertEquals("Wer will mit in die Todesminen?", fix("Wer will mit in die Todes Minen?"))
        assertEquals("Sturmwind und Eisenschmiede", fix("Sturm wind und Eisen schmiede"))
        assertEquals("nach Schlingendorntal", fix("nach Schlingen Dorn Tal"))
        assertEquals("Scholomance", fix("Scholo mance"))
        assertEquals("Naxxramas", fix("Nax ramas"))
    }

    @Test
    fun addsApostropheAndHyphen() {
        assertEquals("Zul'Gurub heute", fix("Zul Gurub heute"))
        assertEquals("Un'Goro-Krater", fix("Un Goro Krater"))
        assertEquals("Wir gehen nach Kel'Thuzad.", fix("Wir gehen nach Kel Thusad."))
    }

    @Test
    fun fixesSimpleMishearings() {
        assertEquals("lass uns nach Orgrimmar reiten", fix("lass uns nach Orgrimar reiten"))
        assertEquals("Ragnaros ist down", fix("Ragnaroos ist down"))
        assertEquals("Hakkar", fix("Hakar"))
        assertEquals("Burning Steppes", fix("Burning Steps"))
        assertEquals("Karazhan heute Abend", fix("Karazan heute Abend"))
        assertEquals("ich bin in Dun Morogh", fix("ich bin in Dun Morog"))
    }

    @Test
    fun fixesNamesThatLoseTheirLastLetter() {
        assertEquals("Archimonde", fix("Archimond"))
        assertEquals("Stratholme", fix("Stratholm"))
    }

    @Test
    fun abbreviationsTakeTheirSpelling() {
        assertEquals("LFG für Todesminen", fix("L F G für Todesminen"))
        assertEquals("suche DPS", fix("suche dps"))
    }

    @Test
    fun keepsPunctuationAroundTheName() {
        assertEquals("Todesminen, Orgrimmar!", fix("Todes Minen, Orgrimar!"))
        assertEquals("(Hakkar)", fix("(Hakar)"))
    }

    @Test
    fun doesNotJoinWordsAcrossPunctuation() {
        assertEquals("Todes, Minen", fix("Todes, Minen"))
    }

    @Test
    fun leavesOrdinarySentencesAlone() {
        val sentences = listOf(
            "Hallo zusammen wie geht es euch",
            "Dank dir fürs Heilen",
            "Feier am Abend",
            "Der Hunger ist groß",
            "Ich suche eine Gruppe für heute Abend",
            "we need a healer for deadmines",
        )
        for (sentence in sentences) assertEquals(sentence, fix(sentence))
    }

    @Test
    fun shortWordsAreOnlyMatchedExactly() {
        // "Dank" sounds like "Tank" but a four-letter word is never matched by sound.
        assertEquals("Dank fürs Tanken", fix("Dank fürs Tanken"))
        // Exact short words keep the spoken case (an everyday word), abbreviations get theirs.
        assertEquals("tank DPS", fix("tank dps"))
    }

    @Test
    fun commonWordsAreProtectedFromFuzzyMatching() {
        // "Heiler" is one letter away from "Healer" and sounds alike, but is a plain German word.
        assertEquals("Wir brauchen einen Heiler", fix("Wir brauchen einen Heiler"))
    }

    @Test
    fun grammaticalFormsAreNotMistakenForNames() {
        assertEquals("zwei Feuerbälle", fix("zwei Feuerbälle"))
        assertEquals("Portale", fix("Portale"))
    }

    @Test
    fun ambiguousMatchIsNotReplaced() {
        // "Sarturo" is one edit from both Sartura and Sarturi: no guess.
        assertEquals("Sarturo", fix("Sarturo"))
        assertEquals("Sartura", fix("Sartura"))
    }

    @Test
    fun emptyVocabularyKeepsTextButTrims() {
        assertEquals("Todes Minen", VoiceCorrector.correct("  Todes   Minen ", VoiceVocabulary.EMPTY))
    }

    @Test
    fun collapsesWhitespace() {
        assertEquals("Todesminen", fix("  Todes   Minen "))
        assertEquals("", fix("   "))
    }

    @Test
    fun customWordsJoinTheList() {
        val vocab = VoiceVocabulary.parse(list, listOf("Sturmwacht"))
        assertEquals("Gilde Sturmwacht", fix("Gilde Sturmwach", vocab))
    }

    @Test
    fun parseSkipsCommentsAndKeepsFlags() {
        val vocab = VoiceVocabulary.parse("# c\n\n=Gold\nOrgrimmar\n orgrimmar \n")
        assertEquals(2, vocab.terms.size)
        assertTrue(vocab.terms[0].protectedWord)
        assertEquals("Gold", vocab.terms[0].display)
    }

    @Test
    fun biasStringsPutOwnWordsFirstAndSkipCommonWords() {
        val bias = vocabulary.biasStrings(listOf("Sturmwacht"), max = 5)
        assertEquals(5, bias.size)
        assertEquals("Sturmwacht", bias[0])
        assertEquals("Todesminen", bias[1])
        assertTrue(!bias.contains("Heiler"))
        assertTrue(!bias.contains("Gold"))
    }

    @Test
    fun realWordListWorks() {
        val file = File("src/main/assets/wow/begriffe.txt")
        val vocab = VoiceVocabulary.parse(file.readText(Charsets.UTF_8))
        assertTrue("word list too small: ${vocab.terms.size}", vocab.terms.size >= 300)
        assertEquals("wir gehen in die Todesminen", VoiceCorrector.correct("wir gehen in die Todes Minen", vocab))
        assertEquals("Kel'Thuzad", VoiceCorrector.correct("Kel Thusad", vocab))
        assertEquals("Sturmwind", VoiceCorrector.correct("Sturm wind", vocab))
        assertEquals("Orgrimmar", VoiceCorrector.correct("Orgrimar", vocab))
        assertEquals("Kriegshymnenschlucht", VoiceCorrector.correct("Kriegs hymnen Schlucht", vocab))
        assertEquals("Pechschwingenhort", VoiceCorrector.correct("Pechschwingen Hort", vocab))
        assertEquals("suche DPS für Stratholme", VoiceCorrector.correct("suche dps für Stratholm", vocab))
    }

    @Test
    fun realWordListLeavesEverydayWordsAlone() {
        val file = File("src/main/assets/wow/begriffe.txt")
        val vocab = VoiceVocabulary.parse(file.readText(Charsets.UTF_8))
        val everyday = """
            Heiler Hunger Kämpfer Zauberer Gruppe Gilde Spieler Charakter Waffe Rüstung Schild Heilung Schaden Angriff
            Gegner Monster Drache Dämonen Untote Spinne Geld Preis kaufen verkaufen tauschen Händler Belohnung Gegenstand
            Leute Freunde Mitglied Anführer Verwandlung Furcht Auferstehung Zeit Minuten Stunde Server Verbindung
            Absturz Neustart Update Portal Portale Feuerbälle Arkane Thorium Mithril Tanken healer tank pull loot
            Reittier Pferd Raptor Greif Kristall Rubin Saphir Seele Trank Lehrer Meister Reise Instanz Schlachtzug
        """.trimIndent().split(Regex("\\s+"))
        for (word in everyday) assertEquals(word, VoiceCorrector.correct(word, vocab))
    }

    // --- fold5.2: Eigennamen, die als englische Wörter erkannt werden ("dead Miles iron Ford") ---

    private val realVocabulary: VoiceVocabulary by lazy {
        VoiceVocabulary.parse(File("src/main/assets/wow/begriffe.txt").readText(Charsets.UTF_8))
    }

    private fun real(text: String) = VoiceCorrector.correct(text, realVocabulary)

    @Test
    fun namesWithADifferentSoundKeyAreStillCorrected() {
        // The last letter changes the Cologne and Metaphone key ("Ford" / "Forge"), so no key matches; spelling and
        // sound similarity together carry the match.
        assertEquals("Deadmines", real("dead Miles"))
        assertEquals("Ironforge", real("iron Ford"))
        assertEquals("Stormwind", real("storm wind"))
        assertEquals("Coldridge Valley", real("Cold Ridge Valley"))
        assertEquals("Orgrimmar", real("Org rimmer"))
        assertEquals("Elwynn Forest", real("Elwin Forest"))
        assertEquals("Darkshore", real("Dark Shore"))
        assertEquals("Dun Morogh", real("Dun Moro"))
        assertEquals("Thunder Bluff", real("Thunder Bluff"))
    }

    @Test
    fun theReportedSentenceIsFixedAndTheRestStays() {
        assertEquals(
            "Deadmines Ironforge und sind alles d werden aber nicht erkannt oder",
            real("dead Miles iron Ford und sind alles d werden aber nicht erkannt oder"),
        )
        assertEquals("und sind alles", real("und sind alles"))
        assertEquals("aber nicht erkannt", real("aber nicht erkannt"))
        assertEquals("wir treffen uns in Ironforge", real("wir treffen uns in iron Ford"))
        assertEquals("lass uns in die Deadmines gehen", real("lass uns in die dead Miles gehen"))
    }

    @Test
    fun moreEnglishClientNamesAreFound() {
        assertEquals("Wailing Caverns", real("Wailing Cavern"))
        assertEquals("Shadowfang Keep", real("Shadow fang Keep"))
        assertEquals("Blackrock Depths", real("Blackrock Dept"))
        assertEquals("Blackrock Spire", real("Black rock Spire"))
        assertEquals("Goldshire", real("Gold shire"))
        assertEquals("Silvermoon", real("Silver moon"))
        assertEquals("Undercity", real("Under city"))
        assertEquals("Teldrassil", real("Tel drassil"))
        assertEquals("Shadowglen", real("Shadow Glen"))
        assertEquals("Camp Narache", real("Camp Naresh"))
        assertEquals("Blackwing Lair", real("Black Wing Lair"))
        assertEquals("Molten Core", real("Molten Corps"))
        assertEquals("Stranglethorn Vale", real("Stranglethorn Fail"))
        assertEquals("Deadmines", real("Deadmine"))
        assertEquals("Dalaran", real("Dallaran"))
        assertEquals("Mulgore", real("Mulgor"))
    }

    @Test
    fun aCommonWordAtTheEdgeIsNotSwallowed() {
        assertEquals("in Ironforge", real("in iron Ford"))
        assertEquals("und Ironforge", real("und iron Ford"))
        assertEquals("the Deadmines", real("the Deadmines"))
        // ... unless the name itself starts with that word.
        assertEquals("The Barrens", real("The Barens"))
        assertEquals("Die Exodar", real("Die Exodor"))
    }

    @Test
    fun theEnglishNamesOfTheListAreCompleteAndStayUnchanged() {
        val names = (
            "Stormwind|Ironforge|Darnassus|Orgrimmar|Thunder Bluff|Undercity|Exodar|Silvermoon|Shattrath|Dalaran|" +
                "Elwynn Forest|Dun Morogh|Teldrassil|Durotar|Mulgore|Tirisfal Glades|Eversong Woods|Azuremyst Isle|" +
                "Bloodmyst Isle|Northshire Valley|Coldridge Valley|Shadowglen|Valley of Trials|Camp Narache|Echo Isles|" +
                "Deathknell|Sunstrider Isle|Ammen Vale|Westfall|Darkshore|Ashenvale|The Barrens|Deadmines|" +
                "Wailing Caverns|Shadowfang Keep|Blackfathom Deeps|The Stockade|Gnomeregan|Razorfen Kraul|Razorfen Downs|" +
                "Scarlet Monastery|Uldaman|Zul'Farrak|Maraudon|Sunken Temple|Blackrock Depths|Blackrock Spire|" +
                "Stratholme|Scholomance|Dire Maul|Ragefire Chasm|Molten Core|Blackwing Lair|Naxxramas|Karazhan|" +
                "Warrior|Paladin|Hunter|Rogue|Priest|Shaman|Mage|Warlock|Druid|Death Knight|" +
                "Human|Dwarf|Night Elf|Gnome|Orc|Undead|Tauren|Troll|Blood Elf|Draenei"
            ).split("|")
        for (name in names) {
            assertTrue("missing in the word list: $name", realVocabulary.exact(Phonetics.fold(name)).isNotEmpty())
            assertEquals(name, real(name))
        }
    }

    @Test
    fun everydaySentencesStayAsTheyAreWithTheRealList() {
        val sentences = listOf(
            "Hallo zusammen wie geht es euch heute Abend",
            "Ich suche noch einen Heiler für die Gruppe",
            "Wir brauchen einen Tank und zwei Schadensverursacher",
            "Kann mir jemand beim Questen helfen ich komme nicht weiter",
            "Wo finde ich den Händler für die Rüstung",
            "Der Boss hat uns ganz schön auseinander genommen",
            "Ich habe gestern einen neuen Charakter angefangen",
            "Wie komme ich am schnellsten nach Hause",
            "Die Schmiede ist gleich neben der Bank",
            "we need a healer for the next run anyone interested",
            "lets meet at the bank in ten minutes",
            "Ich glaube der Server ist heute etwas langsam",
            "Mein Pferd ist weggelaufen und ich finde es nicht",
            "Das Tal ist nördlich von der Stadt am Fluss",
            "Die Burg im Westen ist verlassen und voller Untoter",
            "Der Turm ist hoch und hat eine schmale Treppe",
            "Er hat einen sehr starken Charakter",
        )
        for (sentence in sentences) assertEquals(sentence, real(sentence))
        // Single words one letter away from a name (Blenden, Priest, Hogger, Verschwinden) are not names.
        for (word in listOf("Beenden", "Ratschen", "schneller", "verwenden", "Preset", "higher", "Gesten")) {
            assertEquals(word, real(word))
        }
    }

    @Test
    fun detailedCorrectionReportsChangesAndHighlightSpans() {
        val result = VoiceCorrector.correctDetailed("Wir gehen nach Kel Thusad.", vocabulary)
        assertEquals("Wir gehen nach Kel'Thuzad.", result.text)
        assertTrue(result.changed)
        assertEquals(1, result.changes.size)
        assertEquals("Kel Thusad", result.changes[0].spoken)
        assertEquals("Kel'Thuzad", result.changes[0].corrected)
        assertEquals(1, result.spans.size)
        assertEquals("Kel'Thuzad", result.text.substring(result.spans[0].start, result.spans[0].end))

        val twice = VoiceCorrector.correctDetailed("(Hakar) und Todes Minen", vocabulary)
        assertEquals("(Hakkar) und Todesminen", twice.text)
        assertEquals(listOf("Hakkar", "Todesminen"), twice.spans.map { twice.text.substring(it.start, it.end) })

        val plain = VoiceCorrector.correctDetailed("Hallo  zusammen ", vocabulary)
        assertEquals("Hallo zusammen", plain.text)
        assertEquals("Hallo zusammen", plain.heard)
        assertTrue(!plain.changed)
        assertTrue(plain.spans.isEmpty())
    }

    @Test
    fun spansAreFoundAgainInACleanedText() {
        val result = VoiceCorrector.correctDetailed("(Hakar) ist da", vocabulary)
        assertEquals("(Hakkar) ist da", result.text)
        val cut = result.spansIn("Hakkar) ist da")
        assertEquals(1, cut.size)
        assertEquals("Hakkar", "Hakkar) ist da".substring(cut[0].start, cut[0].end))
        assertEquals(result.spans.size, result.spansIn(result.text).size)
    }

    @Test
    fun anAlternativeWithClearlyMoreNamesIsPreferred() {
        val first = VoiceCorrector.best(listOf("we go to the barren", "we go to Orgrimmar and Hakkar"), vocabulary)
        assertEquals("we go to Orgrimmar and Hakkar", first?.text)
        // One name more is needed, a slightly better score is not enough.
        val keep = VoiceCorrector.best(listOf("Orgrimar", "Hakkar"), vocabulary)
        assertEquals("Orgrimmar", keep?.text)
        assertEquals(null, VoiceCorrector.best(listOf("", "  "), vocabulary))
        assertEquals("Todesminen", VoiceCorrector.best(listOf("Todes Minen"), vocabulary)?.text)
    }

    @Test
    fun biasBlockOfTheListComesFirstAndFitsTheLimit() {
        val bias = realVocabulary.biasStrings(emptyList())
        assertTrue("too many: ${bias.size}", bias.size <= VoiceVocabulary.MAX_BIAS_STRINGS)
        assertEquals(bias.size, bias.distinct().size)
        for (name in listOf("Deadmines", "Ironforge", "Stormwind", "Coldridge Valley", "Orgrimmar", "Elwynn Forest")) {
            assertTrue("not in the first 160 hints: $name", bias.indexOf(name) in 0..159)
        }
        assertTrue(!bias.contains("Heiler"))
        val withOwn = realVocabulary.biasStrings(listOf("Sturmwacht", "Anna"))
        assertEquals("Sturmwacht", withOwn[0])
        assertEquals("Anna", withOwn[1])
        assertEquals("Stormwind", withOwn[2])
        assertTrue(withOwn.size <= VoiceVocabulary.MAX_BIAS_STRINGS)
    }

    @Test
    fun ownWordsAreCappedInTheBiasList() {
        val many = (1..80).map { "Eigenwort$it" }
        val bias = realVocabulary.biasStrings(many)
        assertEquals(VoiceVocabulary.MAX_BIAS_STRINGS, bias.size)
        assertEquals(VoiceVocabulary.MAX_CUSTOM_BIAS, bias.count { it.startsWith("Eigenwort") })
        assertTrue(bias.contains("Deadmines"))
    }

    @Test
    fun parseReadsTheBiasMarkers() {
        val vocab = VoiceVocabulary.parse("Alpha\n#!bias\nIronforge\n=Gold\n#!normal\nOrgrimmar", listOf("Mein"))
        assertEquals(listOf(false, true, false, false, false), vocab.terms.map { it.bias })
        assertEquals(listOf("Ironforge", "Alpha", "Orgrimmar", "Mein"), vocab.biasStrings(emptyList()))
    }
}

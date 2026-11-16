package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Zuordnung der Bedienelemente der Battle.net-Seite (Entwurf 3.2): jede Nummer genau einmal, keine leere Gruppe, „Einfach“ nur das Genannte. */
class LabItemsTest {
    @Test
    fun theDesignNamesSixtySevenNumbers() {
        assertEquals(67, LabItem.entries.size)
    }

    @Test
    fun everyNumberAppearsExactlyOnce() {
        val ids = LabItem.entries.map { it.id }
        assertEquals("doppelte Nummer", ids.size, ids.toSet().size)
        // Jedes Element hat genau einen Platz; die Gruppenlisten und die Listen „Einfach“/„zusammengelegt“ ergeben zusammen genau alle.
        val placed = LabItems.simple + LabGroup.entries.flatMap { LabItems.inGroup(it) } + LabItems.merged
        assertEquals(LabItem.entries.size, placed.size)
        assertEquals(LabItem.entries.toSet(), placed.toSet())
    }

    @Test
    fun theStepRowsS1ToS7SplitIntoPartAAndPartBWithoutLosingOne() {
        // Einmalanmelden: S1-S4 = Teil A, S5-S7 = Teil B (eingeklappt, aber in der Liste); alle sieben bleiben in "Einfach".
        val stepItems = LabItems.simple.filter { it.id.startsWith("S") }.map { it.id.drop(1).toInt() }.sorted()
        assertEquals((1..7).toList(), stepItems)
        assertEquals((1..4).toList(), LabGuide.PART_A.map { it.number })
        assertEquals((5..7).toList(), LabGuide.PART_B.map { it.number })
        assertEquals(GuideStep.entries.toSet(), (LabGuide.PART_A + LabGuide.PART_B).toSet())
    }

    @Test
    fun noGroupIsEmpty() {
        for (group in LabGroup.entries) assertTrue("Gruppe leer: $group", LabItems.inGroup(group).isNotEmpty())
    }

    @Test
    fun simpleHoldsOnlyWhatTheDesignNames() {
        val expected = setOf("B0", "G1", "G4", "G5", "S1", "S2", "S3", "S4", "S5", "S6", "S7")
        assertEquals(expected, LabItems.simple.map { it.id }.toSet())
    }

    @Test
    fun countsPerPlaceMatchTheDesignTable() {
        val perPlace = LabItem.entries.groupingBy { it.place }.eachCount()
        assertEquals(11, perPlace[LabPlace.SIMPLE])
        assertEquals(2, perPlace[LabPlace.SIGN_IN])
        assertEquals(6, perPlace[LabPlace.GAME_FOLDER])
        assertEquals(13, perPlace[LabPlace.TECH])
        assertEquals(7, perPlace[LabPlace.TROUBLESHOOTING])
        assertEquals(18, perPlace[LabPlace.DEVELOPMENT])
        assertEquals(10, perPlace[LabPlace.MERGED])
        assertEquals(46, LabItems.advanced.size)
    }

    @Test
    fun mergedItemsNameATargetThatStays() {
        for (item in LabItems.merged) {
            val targetId = item.mergedInto
            assertNotNull("Ziel fehlt: ${item.id}", targetId)
            val target = LabItem.entries.firstOrNull { it.id == targetId }
            assertNotNull("Ziel unbekannt: ${item.id} -> $targetId", target)
            assertFalse("Ziel selbst zusammengelegt: ${item.id} -> $targetId", target!!.place == LabPlace.MERGED)
        }
    }

    @Test
    fun onlyMergedItemsHaveATarget() {
        for (item in LabItem.entries) {
            if (item.place == LabPlace.MERGED) continue
            assertNull("Ziel ohne Zusammenlegung: ${item.id}", item.mergedInto)
        }
    }

    @Test
    fun groupMatchesPlace() {
        for (item in LabItem.entries) {
            assertEquals(item.place in setOf(LabPlace.SIMPLE, LabPlace.MERGED), item.group == null)
        }
    }
}

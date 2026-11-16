package app.gamenative.lab

import app.gamenative.utils.MachineIdentity
import app.gamenative.utils.MachineIdentityApply
import app.gamenative.utils.MachineIdentityLog
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LabIdentityTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val identity = MachineIdentity.generate(Random(42), 1_700_000_000L)

    @Test
    fun allFeaturesAreAdoptedOnlyWithTheSwitchAndAStoredIdentity() {
        assertEquals(listOf("machineGuid", "computerName", "hostname", "installDate", "productId", "volumeSerial"), LabIdentity.adopted(true, true))
        assertTrue(LabIdentity.adopted(false, true).isEmpty())
        assertTrue(LabIdentity.adopted(true, false).isEmpty())
        assertTrue(LabIdentity.adopted(false, false).isEmpty())
    }

    @Test
    fun theFeaturesAreTheOnesTheGameWritesIntoItsPrefix() {
        val written = MachineIdentityApply.registryEdits(identity).map { it.name }.toSet()
        // Registry-Werte der Kennung: MachineGuid, ComputerName, Hostname, ProductId, InstallDate; dazu die Seriennummer des Laufwerks.
        assertEquals(setOf("MachineGuid", "ComputerName", "Hostname", "ProductId", "InstallDate"), written)
        assertEquals(6, LabIdentity.FEATURES.size)
        assertTrue(LabIdentity.FEATURES.contains("volumeSerial"))
    }

    @Test
    fun theProtocolShowsOnlyShortIdsAndYesNoNeverAValue() {
        val wineDir = File(tmp.root, "lab/.wine")
        val outcome = LabIdentity.applyAndMeasure(wineDir, identity, enabled = true)
        val text = LabIdentity.protocolLines(outcome).joinToString("\n")
        val values = listOf(identity.machineGuid, identity.computerName, identity.volumeSerial, identity.productId, identity.installDate.toString())
        for (v in values) assertFalse("value in the protocol: $v", text.contains(v))
        assertTrue(text.contains("Game identity: " + MachineIdentityLog.fingerprint(identity)))
        assertTrue(Regex("Lab identity: [0-9a-f]{8}").containsMatchIn(text))
        assertTrue(text.contains("Same Windows identity as the game: on"))
        for (f in LabIdentity.FEATURES) assertTrue(text.contains("  $f: "))
    }

    @Test
    fun withTheSwitchTheLabPrefixGetsTheSerialAndWithoutItNot() {
        val on = File(tmp.root, "on/.wine")
        val outcomeOn = LabIdentity.applyAndMeasure(on, identity, enabled = true)
        assertEquals(identity.volumeSerial, File(on, MachineIdentityApply.SERIAL_FILE).readText().trim())
        assertFalse(outcomeOn.deviations.contains("volumeSerial"))
        val off = File(tmp.root, "off/.wine")
        val outcomeOff = LabIdentity.applyAndMeasure(off, identity, enabled = false)
        assertFalse(File(off, MachineIdentityApply.SERIAL_FILE).exists())
        assertTrue(outcomeOff.deviations.contains("volumeSerial"))
        assertNotEquals(outcomeOn.labFingerprint, outcomeOff.labFingerprint)
        assertTrue(LabIdentity.protocolLines(outcomeOff).contains("  volumeSerial: no"))
        assertTrue(LabIdentity.protocolLines(outcomeOn).contains("  volumeSerial: yes"))
    }

    @Test
    fun theGamePrefixIsNeverTouchedByTheLabLogic() {
        val game = File(tmp.root, "game/.wine").apply { mkdirs() }
        val before = game.walkTopDown().count()
        LabIdentity.applyAndMeasure(File(tmp.root, "lab/.wine"), identity, enabled = true)
        assertEquals(before, game.walkTopDown().count())
    }

    @Test
    fun noStoredIdentityMeansNothingIsAppliedAndSaid() {
        val wineDir = File(tmp.root, "lab/.wine")
        val outcome = LabIdentity.applyAndMeasure(wineDir, null, enabled = true)
        assertFalse(outcome.hasIdentity)
        assertFalse(wineDir.exists())
        assertTrue(LabIdentity.protocolLines(outcome).any { it.contains("none (nothing applied)") })
    }

    @Test
    fun labFingerprintEqualsTheGameOneOnlyWhenNothingDiffers() {
        assertEquals("abcd1234", LabIdentity.labFingerprint("abcd1234", emptyList()))
        val other = LabIdentity.labFingerprint("abcd1234", listOf("productId"))
        assertNotEquals("abcd1234", other)
        assertTrue(Regex("[0-9a-f]{8}").matches(other))
        assertEquals(other, LabIdentity.labFingerprint("abcd1234", listOf("productId")))
    }
}

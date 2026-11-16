package app.gamenative.lab

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Schlüssel, die das Labor vom WoW-Container übernimmt (Punkt 8 von labstart2): müssen die echten Namen aus `Container.java` sein. */
class LabContainerKeysTest {
    private val containerSource = File("src/main/java/com/winlator/container/Container.java").readText()

    @Test
    fun everyCopiedKeyIsWrittenByContainerSaveData() {
        // Nicht gegen die eigene Konstante prüfen, sondern gegen den Quelltext: der Name muss als `data.put("<name>"` stehen.
        val missing = LabContainer.COPY_KEYS.filterNot { containerSource.contains("data.put(\"$it\"") }
        assertTrue("not written by Container.java: $missing", missing.isEmpty())
    }

    @Test
    fun displayRendererIsCopiedUnderItsRealNameOnly() {
        assertFalse("displayRenderer ohne Mode", LabContainer.COPY_KEYS.contains("displayRenderer"))
        assertTrue(LabContainer.COPY_KEYS.contains("displayRendererMode"))
        assertTrue(containerSource.contains("data.put(\"displayRendererMode\""))
    }

    @Test
    fun containerSpecificKeysAreNotCopied() {
        val own = listOf("touchscreenMode", "shooterMode", "gestureConfig", "executablePath", "drives", "envVars", "name", "id")
        assertTrue("copied by mistake: ${own.filter { it in LabContainer.COPY_KEYS }}", own.none { it in LabContainer.COPY_KEYS })
    }

    @Test
    fun copiedKeysAreUnique() {
        assertTrue(LabContainer.COPY_KEYS.size == LabContainer.COPY_KEYS.toSet().size)
    }
}

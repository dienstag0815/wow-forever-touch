package app.gamenative.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibredirectChoiceTest {
    private val full = "libredirect-bionic-wx.so"
    private val minimal = "libredirect-bionic-wx-minimal.so"

    @Test
    fun belowApi37TheFullVariantIsUsed() {
        assertEquals(full, LibredirectChoice.pick(true, false, 29, full))
        assertEquals(full, LibredirectChoice.pick(true, false, 34, full))
        assertEquals(full, LibredirectChoice.pick(true, false, 36, full))
    }

    @Test
    fun fromApi37OnTheMinimalVariantIsUsed() {
        assertEquals(minimal, LibredirectChoice.pick(true, false, 37, full))
        assertEquals(minimal, LibredirectChoice.pick(true, false, 38, full))
        assertEquals(minimal, LibredirectChoice.pick(true, false, 99, full))
    }

    @Test
    fun aContainerThatDisablesLibredirectGetsTheMinimalVariantOnModernAndNothingElsewhere() {
        for (sdk in listOf(29, 36, 37, 40)) {
            assertEquals(minimal, LibredirectChoice.pick(true, true, sdk, full))
            assertNull(LibredirectChoice.pick(false, true, sdk, full))
        }
    }

    @Test
    fun theLegacyFlavorIsNeverSwitchedByApiLevel() {
        assertEquals(full, LibredirectChoice.pick(false, false, 37, full))
        assertEquals(full, LibredirectChoice.pick(false, false, 29, full))
    }

    @Test
    fun theMinimalNameMatchesTheAssetThatIsShipped() {
        assertEquals(minimal, LibredirectChoice.MINIMAL_SO)
        assertEquals(37, LibredirectChoice.MINIMAL_FROM_API)
    }

    @org.junit.Test
    fun withTheRuleSwitchedOffApi37KeepsTheFullVariant() {
        assertEquals(full, LibredirectChoice.pick(true, false, 37, full, false))
        assertEquals(minimal, LibredirectChoice.pick(true, true, 37, full, false))
    }
}

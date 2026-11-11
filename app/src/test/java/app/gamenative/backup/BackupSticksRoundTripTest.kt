package app.gamenative.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.winlator.inputcontrols.ControlElement
import com.winlator.widget.InputControlsView
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Rundreise mit einem echten Nutzerprofil "Touch (Mein Layout)" (Backup vom 10.11.2026, nur die beiden Sticks): Export → Zip → Import-Plan →
 * Laden. Beide Sticks, Ring (innen und außen) und Mitte müssen vollständig ankommen. Das Backup selbst ist unversehrt: die Sticks
 * fehlten dort nicht, sie wurden vom Shooter-Modus des frischen Containers ausgeblendet (InputControlsView.isStickHiddenByShooterMode).
 */
@RunWith(RobolectricTestRunner::class)
class BackupSticksRoundTripTest {
    private val json: String =
        javaClass.getResourceAsStream("/backup/sticks_profile.json")!!.readBytes().toString(Charsets.UTF_8)

    private fun mockView(): InputControlsView {
        val view = mockk<InputControlsView>(relaxed = true)
        every { view.snappingSize } returns 10
        every { view.maxWidth } returns 1000
        every { view.maxHeight } returns 1000
        return view
    }

    private fun load(profileJson: String): List<ControlElement> {
        val view = mockView()
        val array = JSONObject(profileJson).getJSONArray("elements")
        return (0 until array.length()).map { i ->
            ControlElement(view).apply {
                setType(ControlElement.Type.STICK)
                readExtrasFromJson(array.getJSONObject(i))
            }
        }
    }

    private fun assertSticks(sticks: List<ControlElement>) {
        assertEquals(2, sticks.size)
        val left = sticks[0]
        val right = sticks[1]
        assertEquals(0, left.ringSegments)
        assertEquals(8, right.ringSegments)
        assertEquals("KEY_1", right.getRingItem(0).binding.name)
        assertTrue(right.hasRingOuter(0))
        assertTrue(right.hasRingOuter(1))
        assertEquals("KEY_TAB", right.getRingOuterItem(0)!!.binding.name)
        assertEquals("MOUSE_RIGHT_BUTTON", right.getRingOuterItem(1)!!.binding.name)
        assertNotNull(right.centerItem)
        assertEquals("KEY_SPACE", right.centerItem!!.binding.name)
    }

    @Test
    fun `sticks with ring and center survive export, zip and import plan`() {
        val profile = BackupFormat.parseProfile(json.toByteArray(Charsets.UTF_8))!!
        val manifest = BackupManifest(1, "2.3.3-fold8.31", 285, "2026-11-10 21:53", 1, 0, "TOUCH_CUSTOM", profile.name)
        val unpacked = (BackupFormat.unpack(BackupFormat.pack(manifest, listOf(profile), emptyList())) as UnpackResult.Ok).backup

        // frisches Gerät mit einem anderen Profil auf Id 8: das Backup-Profil bekommt eine neue Id, der Inhalt bleibt
        val device = listOf(ExistingProfile(8, "Touch", """{"id":8,"name":"Touch","elements":[]}"""))
        val step = BackupImportPlan.plan(unpacked.profiles, device, ConflictPolicy.REPLACE).writes.single()

        assertEquals(9, JSONObject(step.json).getInt("id"))
        assertSticks(load(step.json))
    }

    @Test
    fun `sticks load from the original file`() {
        assertSticks(load(json))
    }
}

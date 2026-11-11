package com.winlator.inputcontrols

import com.winlator.inputcontrols.ProfileUpdatePolicy.Action
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUpdatePolicyTest {
    private val remembered = "aaaa"
    private val edited = "bbbb"
    private val newAsset = "cccc"

    @Test
    fun unchangedSinceLastWrite_isOverwritten() {
        assertEquals(Action.OVERWRITE, ProfileUpdatePolicy.decide(true, remembered, remembered, newAsset))
    }

    @Test
    fun editedByPlayer_isKept() {
        assertEquals(Action.KEEP, ProfileUpdatePolicy.decide(true, edited, remembered, newAsset))
    }

    @Test
    fun noRememberedHash_isKept() {
        assertEquals(Action.KEEP, ProfileUpdatePolicy.decide(true, edited, null, newAsset))
        assertEquals(Action.KEEP, ProfileUpdatePolicy.decide(true, edited, "", newAsset))
    }

    @Test
    fun noRememberedHash_butIdenticalToAsset_isOverwrittenSoTheHashGetsRecorded() {
        assertEquals(Action.OVERWRITE, ProfileUpdatePolicy.decide(true, newAsset, null, newAsset))
    }

    @Test
    fun unreadableLocalFile_isKept() {
        assertEquals(Action.KEEP, ProfileUpdatePolicy.decide(true, null, remembered, newAsset))
    }

    @Test
    fun newProfile_isCreated() {
        assertEquals(Action.CREATE, ProfileUpdatePolicy.decide(false, null, null, newAsset))
    }

    @Test
    fun hashKeyIsPerFileName() {
        assertEquals("inputcontrols_hash_controls-6.icp", ProfileUpdatePolicy.hashKey("controls-6.icp"))
        assertFalse(ProfileUpdatePolicy.hashKey("controls-6.icp") == ProfileUpdatePolicy.hashKey("controls-7.icp"))
    }

    @Test
    fun sha256_knownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ProfileUpdatePolicy.sha256Hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ProfileUpdatePolicy.sha256Hex("abc".toByteArray()))
        assertNull(ProfileUpdatePolicy.sha256Hex(null))
    }

    @Test
    fun copyJson_changesIdAndNameAndDropsTemplateFlag() {
        val source = """{"id":6,"name":"Touch (WoW)","cursorSpeed":1,"template":true,"elements":[{"type":"STICK"}]}"""
        val copy = JSONObject(ProfileUpdatePolicy.copyJson(source, 9, "Touch (Mein Layout)"))
        assertEquals(9, copy.getInt("id"))
        assertEquals("Touch (Mein Layout)", copy.getString("name"))
        assertFalse(copy.has("template"))
        assertEquals(1, copy.getJSONArray("elements").length())
    }

    @Test
    fun bundledTouchProfile_hashMatchesItself() {
        val file = java.io.File("src/main/assets/inputcontrols/profiles/controls-6.icp")
        val bytes = file.readBytes()
        val hash = ProfileUpdatePolicy.sha256Hex(bytes)
        assertEquals(64, hash.length)
        assertTrue(ProfileUpdatePolicy.decide(true, hash, hash, hash) == Action.OVERWRITE)
    }

    // ---- Id collision: bundled "Touch (Standard)" has id 8, players often own a profile "Touch (Mein Layout)" in controls-8.icp ----

    private class Local(val id: Int, val name: String, var bytes: String)

    /** Pure replay of InputControlsManager.copyAssetProfilesIfNeeded for one asset (files = file name -> profile). */
    private fun updateWith(files: MutableMap<String, Local>, assetFile: String, assetId: Int, assetName: String, assetBytes: String) {
        val match = files.values.firstOrNull { ProfileUpdatePolicy.sameProfile(assetId, assetName, it.id, it.name) }
        if (match != null) {
            val hash = ProfileUpdatePolicy.sha256Hex(match.bytes.toByteArray())
            val action = ProfileUpdatePolicy.decide(true, hash, "", ProfileUpdatePolicy.sha256Hex(assetBytes.toByteArray()))
            if (action == Action.OVERWRITE) match.bytes = assetBytes
            return
        }
        val taken = files.containsKey(assetFile)
        val sameName = files.values.any { it.name == assetName }
        when (ProfileUpdatePolicy.decideNewAsset(taken, taken && sameName)) {
            ProfileUpdatePolicy.NewAssetAction.WRITE_SAME_FILE -> files[assetFile] = Local(assetId, assetName, assetBytes)
            ProfileUpdatePolicy.NewAssetAction.WRITE_NEW_ID -> {
                val id = files.values.maxOf { it.id } + 1
                files["controls-$id.icp"] = Local(id, assetName, assetBytes)
            }
            ProfileUpdatePolicy.NewAssetAction.SKIP -> Unit
        }
    }

    @Test
    fun sameProfile_needsIdAndName() {
        assertTrue(ProfileUpdatePolicy.sameProfile(8, "Touch (Standard)", 8, "Touch (Standard)"))
        assertFalse(ProfileUpdatePolicy.sameProfile(8, "Touch (Standard)", 8, "Touch (Mein Layout)"))
        assertFalse(ProfileUpdatePolicy.sameProfile(8, "Touch (Standard)", 12, "Touch (Standard)"))
    }

    @Test
    fun decideNewAsset_freeFile_writesThere() {
        assertEquals(ProfileUpdatePolicy.NewAssetAction.WRITE_SAME_FILE, ProfileUpdatePolicy.decideNewAsset(false, false))
        assertEquals(ProfileUpdatePolicy.NewAssetAction.WRITE_NEW_ID, ProfileUpdatePolicy.decideNewAsset(true, false))
        assertEquals(ProfileUpdatePolicy.NewAssetAction.SKIP, ProfileUpdatePolicy.decideNewAsset(true, true))
    }

    @Test
    fun playerProfileOnId8_staysByteIdentical_andStandardGetsAFreshId() {
        val mine = Local(8, "Touch (Mein Layout)", """{"id":8,"name":"Touch (Mein Layout)","elements":[1,2,3]}""")
        val before = mine.bytes
        val files = mutableMapOf("controls-7.icp" to Local(7, "Touch (WoW komplett)", "full"), "controls-8.icp" to mine)
        updateWith(files, "controls-8.icp", 8, "Touch (Standard)", """{"id":8,"name":"Touch (Standard)"}""")
        assertEquals(before, files["controls-8.icp"]!!.bytes)
        assertEquals("Touch (Mein Layout)", files["controls-8.icp"]!!.name)
        val standard = files.values.single { it.name == "Touch (Standard)" }
        assertEquals(9, standard.id)
        // A second app update neither touches the player's file nor adds a second standard profile.
        updateWith(files, "controls-8.icp", 8, "Touch (Standard)", """{"id":8,"name":"Touch (Standard)"}""")
        assertEquals(before, files["controls-8.icp"]!!.bytes)
        assertEquals(1, files.values.count { it.name == "Touch (Standard)" })
    }

    @Test
    fun noProfileOnId8_standardIsCopiedToItsOwnFile() {
        val files = mutableMapOf("controls-7.icp" to Local(7, "Touch (WoW komplett)", "full"))
        updateWith(files, "controls-8.icp", 8, "Touch (Standard)", "std")
        assertEquals("std", files["controls-8.icp"]!!.bytes)
        assertEquals(8, files["controls-8.icp"]!!.id)
    }
}

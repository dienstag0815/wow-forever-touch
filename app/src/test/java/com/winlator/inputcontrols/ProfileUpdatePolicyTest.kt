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
}

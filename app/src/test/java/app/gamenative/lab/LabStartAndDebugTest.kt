package app.gamenative.lab

import com.winlator.core.envvars.EnvVars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Startweg und WINEDEBUG-Stufen des Labors (Punkte 1 und 2 von labstart2). */
class LabStartAndDebugTest {
    private fun ctx() = LabCommandContext(baseEnv = "MESA_GLTHREAD=true", battleNetExe = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe")

    private fun debugOf(settings: LabSettings): String {
        val plan = LabCommand.build(settings, LabTarget.INSTALLER, ctx())
        return EnvVars(plan.envString).get("WINEDEBUG")
    }

    @Test
    fun defaultsAreDirectStartAndDiagnosis() {
        val d = LabSettings()
        assertEquals(LabStartWay.DIRECT, d.startWay)
        assertEquals(LabWineDebug.DIAGNOSE, d.wineDebug)
        assertEquals("fixme-all,err+all,+process,+loaddll,+timestamp", d.wineDebug.value)
    }

    @Test
    fun everyLevelReachesTheEnvironmentOfTheContainer() {
        assertEquals("-all", debugOf(LabSettings(wineDebug = LabWineDebug.OFF)))
        assertEquals("fixme-all,err+all", debugOf(LabSettings(wineDebug = LabWineDebug.ERRORS)))
        assertEquals("fixme-all,err+all,+process,+loaddll,+timestamp", debugOf(LabSettings(wineDebug = LabWineDebug.DIAGNOSE)))
        assertEquals("fixme-all,err+all,+process,+loaddll,+timestamp,+module,+seh", debugOf(LabSettings(wineDebug = LabWineDebug.VERBOSE)))
        assertEquals("err+all,+relay", debugOf(LabSettings(wineDebug = LabWineDebug.CUSTOM, wineDebugCustom = " err+all,+relay ")))
    }

    @Test
    fun theWineDebugLineOfTheContainerWinsInTheFinalEnvironment() {
        // XServerScreen setzt WINEDEBUG=-all und danach putAll(container.envVars): derselbe Weg, hier nachgestellt.
        val plan = LabCommand.build(LabSettings(), LabTarget.BATTLENET, ctx())
        val envOfScreen = EnvVars().also { it.put("WINEDEBUG", "-all") }
        envOfScreen.putAll(EnvVars(plan.envString))
        assertEquals(LabWineDebug.DIAGNOSE.value, envOfScreen.get("WINEDEBUG"))
    }

    @Test
    fun customValueIsChecked() {
        for (bad in listOf("", "  ", "err+all, +relay", "err+all;+relay", "+", "all", "err+all,", "+rel ay", "a".repeat(400))) {
            val plan = LabCommand.build(LabSettings(wineDebug = LabWineDebug.CUSTOM, wineDebugCustom = bad), LabTarget.INSTALLER, ctx())
            assertEquals("bad: '$bad'", listOf(LabIssueCode.DEBUG_BAD_ENTRY), plan.issues.map { it.code })
            assertEquals(LabField.DEBUG, plan.issues.single().field)
        }
        for (good in listOf("-all", "+all", "err+all", "fixme-all,err+all,+relay,+timestamp", "warn+heap")) {
            val plan = LabCommand.build(LabSettings(wineDebug = LabWineDebug.CUSTOM, wineDebugCustom = good), LabTarget.INSTALLER, ctx())
            assertTrue("good: $good ${plan.issues}", plan.ok)
            assertEquals(good, EnvVars(plan.envString).get("WINEDEBUG"))
        }
    }

    @Test
    fun aCustomTextIsIgnoredForTheOtherLevels() {
        val plan = LabCommand.build(LabSettings(wineDebug = LabWineDebug.OFF, wineDebugCustom = "###"), LabTarget.INSTALLER, ctx())
        assertTrue(plan.ok)
    }

    @Test
    fun theNameWineDebugStillHasItsOwnField() {
        val (_, issues) = LabCommand.parseEnv("WINEDEBUG=+all")
        assertEquals(listOf(LabIssueCode.ENV_HAS_FIELD), issues.map { it.code })
    }

    @Test
    fun settingsRoundTrip() {
        val s = LabSettings(wineDebug = LabWineDebug.CUSTOM, wineDebugCustom = "err+all", startWay = LabStartWay.WINHANDLER)
        assertEquals(s, LabSettings.fromJson(s.toJson()))
        assertEquals(LabSettings(), LabSettings.fromJson(LabSettings().toJson()))
    }

    @Test
    fun anOldSavedOffValueDoesNotKeepTheLabBlind() {
        // Stand 9.22 speicherte "off" als Vorgabe (ohne Versionsangabe): der Wert wird nicht übernommen.
        assertEquals(LabWineDebug.DIAGNOSE, LabSettings.fromJson("{\"wineDebug\":\"off\",\"screenSize\":\"1280x800\"}").wineDebug)
        assertEquals(LabWineDebug.DIAGNOSE, LabSettings.fromJson("{\"wineDebug\":\"loaddll\"}").wineDebug)
        assertEquals("1280x800", LabSettings.fromJson("{\"wineDebug\":\"off\",\"screenSize\":\"1280x800\"}").screenSize)
        // Eine bewusste Wahl im neuen Format bleibt.
        assertEquals(LabWineDebug.OFF, LabSettings.fromJson("{\"v\":2,\"wineDebug\":\"off\"}").wineDebug)
    }

    @Test
    fun brokenSettingsFallBackToTheDefaults() {
        assertEquals(LabSettings(), LabSettings.fromJson(null))
        assertEquals(LabSettings(), LabSettings.fromJson("nonsense"))
        assertEquals(LabStartWay.DIRECT, LabSettings.fromJson("{\"v\":2,\"startWay\":\"x\"}").startWay)
    }

    @Test
    fun planCarriesTheStartWay() {
        assertEquals(LabStartWay.DIRECT, LabCommand.build(LabSettings(), LabTarget.INSTALLER, ctx()).startWay)
        assertEquals(LabStartWay.WINHANDLER, LabCommand.build(LabSettings(startWay = LabStartWay.WINHANDLER), LabTarget.INSTALLER, ctx()).startWay)
    }

    @Test
    fun theStepTestAlwaysStartsDirectly() {
        val plan = LabCommand.build(LabSettings(startWay = LabStartWay.WINHANDLER), LabTarget.STEP_TEST, ctx())
        assertTrue(plan.issues.toString(), plan.ok)
        assertEquals(LabStartWay.DIRECT, plan.startWay)
        assertEquals("C:\\windows\\system32\\cmd.exe", plan.program)
        assertEquals(listOf("/c", "L:\\labtest.cmd"), plan.args)
        assertEquals("/c L:\\labtest.cmd", plan.execArgs)
    }

    @Test
    fun withoutAPlanTheLabStartsDirectly() {
        assertTrue(LabStartWay.isDirect(null))
        assertTrue(LabStartWay.isDirect(LabStartWay.DIRECT))
        assertFalse(LabStartWay.isDirect(LabStartWay.WINHANDLER))
    }

    @Test
    fun protocolHeaderNamesTheStartWayAndTheRealDebugValue() {
        val settings = LabSettings(wineDebug = LabWineDebug.CUSTOM, wineDebugCustom = "err+all,+relay")
        val plan = LabCommand.build(settings, LabTarget.INSTALLER, ctx())
        val header = LabProtocol.header(LabProtocolInfo(0, "x", "d", "a", settings, plan, 1))
        assertTrue(header, header.contains("WINEDEBUG: custom (err+all,+relay)"))
        assertTrue(header, header.contains("Start way: direct"))
        val winhandler = LabCommand.build(LabSettings(startWay = LabStartWay.WINHANDLER), LabTarget.INSTALLER, ctx())
        assertTrue(LabProtocol.header(LabProtocolInfo(0, "x", "d", "a", LabSettings(), winhandler, 1)).contains("Start way: winhandler"))
    }
}

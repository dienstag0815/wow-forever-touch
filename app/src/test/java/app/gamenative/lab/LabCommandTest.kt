package app.gamenative.lab

import com.winlator.core.envvars.EnvVars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabCommandTest {
    private val bnet = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe"
    private fun ctx(exe: String? = bnet, host: String? = null, wine: Set<String> = emptySet(), fex: Set<String> = emptySet()) =
        LabCommandContext(baseEnv = "MESA_GLTHREAD=true PULSE_LATENCY_MSEC=144", battleNetExe = exe, installedWine = wine, installedFex = fex, identityHostname = host)

    @Test
    fun defaultStartLineIsTheBattleNetDefault() {
        val plan = LabCommand.build(LabSettings(), LabTarget.BATTLENET, ctx())
        assertTrue(plan.issues.toString(), plan.ok)
        assertEquals(bnet, plan.program)
        assertEquals(listOf("--disable-gpu", "--disable-gpu-compositing", "--enable-features=NetworkServiceInProcess2"), plan.args)
        assertEquals("--disable-gpu --disable-gpu-compositing --enable-features=NetworkServiceInProcess2", LabIds.DEFAULT_BNET_SWITCHES)
        assertEquals("Box64", plan.emulator)
        assertEquals(LabIds.FALLBACK_WINE, plan.wineVersion)
        assertEquals("1440x900", plan.screenSize)
    }

    @Test
    fun ownSwitchesAndAProgramInTheStartLine() {
        val own = LabCommand.build(LabSettings(battleNetLine = "--foo \"a b\" --bar=1"), LabTarget.BATTLENET, ctx())
        assertEquals(listOf("--foo", "a b", "--bar=1"), own.args)
        assertEquals("--foo \"a b\" --bar=1", own.execArgs)
        val program = LabCommand.build(LabSettings(battleNetLine = "C:\\Tools\\x.exe -a"), LabTarget.BATTLENET, ctx(exe = null))
        assertTrue(program.ok)
        assertEquals("C:\\Tools\\x.exe", program.program)
        assertEquals(listOf("-a"), program.args)
    }

    @Test
    fun battleNetNotFoundAndBadLinesAreRejected() {
        val missing = LabCommand.build(LabSettings(), LabTarget.BATTLENET, ctx(exe = null))
        assertEquals(listOf(LabIssueCode.BNET_NOT_FOUND), missing.issues.map { it.code })
        val quote = LabCommand.build(LabSettings(battleNetLine = "--a \"b"), LabTarget.BATTLENET, ctx())
        assertEquals(LabIssueCode.LINE_UNBALANCED_QUOTE, quote.issues.single().code)
        val notWin = LabCommand.build(LabSettings(anyProgram = "/usr/bin/x"), LabTarget.ANY, ctx())
        assertEquals(LabIssueCode.LINE_NOT_WINDOWS_PATH, notWin.issues.single().code)
        val empty = LabCommand.build(LabSettings(anyProgram = " "), LabTarget.ANY, ctx())
        assertEquals(LabIssueCode.LINE_EMPTY, empty.issues.single().code)
    }

    @Test
    fun invalidEnvironmentLinesAreRejectedWithTheirLineNumber() {
        val (pairs, issues) = LabCommand.parseEnv("GOOD=1\n# note\n\nNOEQUALS\n1BAD=x\nPATH=/x\nWINEDEBUG=+all\nOK_2=a b c")
        assertEquals(listOf("GOOD" to "1", "OK_2" to "a b c"), pairs)
        assertEquals(
            listOf(LabIssueCode.ENV_NO_EQUALS to 4, LabIssueCode.ENV_BAD_NAME to 5, LabIssueCode.ENV_RESERVED to 6, LabIssueCode.ENV_HAS_FIELD to 7),
            issues.map { it.code to it.line },
        )
    }

    @Test
    fun environmentRoundTripsThroughTheContainerFormat() {
        val plan = LabCommand.build(
            LabSettings(extraEnv = "WINE_SIMULATE_WRITECOPY=1\nMY_TEXT=a b", dllOverrides = "locationapi=d", wineDebug = LabWineDebug.DIAGNOSE),
            LabTarget.BATTLENET,
            ctx(),
        )
        assertTrue(plan.issues.toString(), plan.ok)
        val env = EnvVars(plan.envString)
        assertEquals("1", env.get("WINE_SIMULATE_WRITECOPY"))
        assertEquals("a b", env.get("MY_TEXT"))
        assertEquals("locationapi=d", env.get("WINEDLLOVERRIDES"))
        assertEquals("fixme-all,err+all,+process,+loaddll,+timestamp", env.get("WINEDEBUG"))
        assertEquals("true", env.get("MESA_GLTHREAD"))
        assertEquals(plan.env.toMap(), plan.env.map { (k, _) -> k to env.get(k) }.toMap())
    }

    @Test
    fun dllOverrides() {
        val (value, issues) = LabCommand.parseDllOverrides("locationapi=d\nfoo.dll=n,b; bar=b,n\nbad\nx=n,b,d\ny=q")
        assertEquals("locationapi=d;foo=n,b;bar=b,n", value)
        assertEquals(3, issues.size)
        assertEquals(listOf(3, 4, 5), issues.map { it.line })
        assertNull(LabCommand.parseDllOverrides("  \n# x").first)
    }

    @Test
    fun windowSize() {
        assertNull(LabCommand.checkScreenSize("1440x900"))
        assertNull(LabCommand.checkScreenSize(" 1280x800 "))
        assertNotNull(LabCommand.checkScreenSize("1440x901"))
        assertNotNull(LabCommand.checkScreenSize("100x100"))
        assertNotNull(LabCommand.checkScreenSize("axb"))
        assertNotNull(LabCommand.checkScreenSize("4000x3000"))
        val plan = LabCommand.build(LabSettings(screenSize = "abc"), LabTarget.INSTALLER, ctx())
        assertEquals(LabIssueCode.SIZE_INVALID, plan.issues.single().code)
    }

    @Test
    fun secretsAreRedactedInTheProtocolLines() {
        val lines = LabCommand.redactedEnvLines(
            listOf("MY_PASSWORD" to "hunter2", "api_token" to "abc", "SECRET_X" to "s", "SOME_KEY" to "k", "NORMAL" to "visible"),
        )
        assertEquals(listOf("MY_PASSWORD=[GEHEIM]", "api_token=[GEHEIM]", "SECRET_X=[GEHEIM]", "SOME_KEY=[GEHEIM]", "NORMAL=visible"), lines)
    }

    @Test
    fun theIdentityHostnameIsSetOnlyWithTheSwitchAndNeverShownAsAValue() {
        val host = "DESKTOP-ABC1234"
        val on = LabCommand.build(LabSettings(sameIdentity = true), LabTarget.BATTLENET, ctx(host = host))
        assertEquals(host, on.env.toMap()["WOWFOREVER_HOSTNAME"])
        val off = LabCommand.build(LabSettings(sameIdentity = false), LabTarget.BATTLENET, ctx(host = host))
        assertFalse(off.env.toMap().containsKey("WOWFOREVER_HOSTNAME"))
        val none = LabCommand.build(LabSettings(sameIdentity = true), LabTarget.BATTLENET, ctx(host = null))
        assertFalse(none.env.toMap().containsKey("WOWFOREVER_HOSTNAME"))
        assertFalse(LabCommand.redactedEnvLines(on.env).joinToString("\n").contains(host))
        val own = LabCommand.build(LabSettings(extraEnv = "WOWFOREVER_HOSTNAME=x"), LabTarget.BATTLENET, ctx())
        assertEquals(LabIssueCode.ENV_RESERVED, own.issues.single().code)
    }

    @Test
    fun wineAndFexMustBeInstalled() {
        val missingWine = LabCommand.build(LabSettings(wine = "proton-10.0-34-arm64ec-7"), LabTarget.BATTLENET, ctx())
        assertEquals(listOf(LabIssueCode.WINE_MISSING), missingWine.issues.map { it.code })
        val okWine = LabCommand.build(LabSettings(wine = "proton-10.0-34-arm64ec-7"), LabTarget.BATTLENET, ctx(wine = setOf("proton-10.0-34-arm64ec-7")))
        assertTrue(okWine.ok)
        assertEquals("proton-10.0-34-arm64ec-7", okWine.wineVersion)
        val noFex = LabCommand.build(LabSettings(translator = LabTranslator.FEX), LabTarget.BATTLENET, ctx())
        assertEquals(listOf(LabIssueCode.FEX_MISSING), noFex.issues.map { it.code })
    }

    @Test
    fun installerPathIsTheRealDriveLetterAndAnAbsoluteWindowsPath() {
        assertEquals("L:\\Battle.net-Setup.exe", LabIds.INSTALLER_WIN_PATH)
        val absolute = Regex("^[A-Za-z]:[\\\\/]")
        val installer = LabCommand.build(LabSettings(), LabTarget.INSTALLER, ctx())
        val battleNet = LabCommand.build(LabSettings(), LabTarget.BATTLENET, ctx())
        val any = LabCommand.build(LabSettings(anyProgram = "C:\\Windows\\notepad.exe"), LabTarget.ANY, ctx())
        assertEquals("L:\\Battle.net-Setup.exe", installer.program)
        assertEquals(bnet, battleNet.program)
        assertEquals("C:\\Windows\\notepad.exe", any.program)
        for (plan in listOf(installer, battleNet, any)) {
            assertTrue(plan.program, absolute.containsMatchIn(plan.program))
            assertFalse(plan.program, plan.program.contains('$'))
        }
    }

    @Test
    fun anyProgramWithoutAbsolutePathIsRejected() {
        for (line in listOf("notepad.exe", "L:datei.exe", "datei.exe")) {
            val plan = LabCommand.build(LabSettings(anyProgram = line), LabTarget.ANY, ctx())
            assertEquals(line, listOf(LabIssueCode.LINE_NOT_WINDOWS_PATH), plan.issues.map { it.code })
        }
    }
}

package app.gamenative.lab

import app.gamenative.report.ReportRedactor
import app.gamenative.utils.MachineIdentity
import com.winlator.core.envvars.EnvVars

/** Was gestartet wird. */
enum class LabTarget { INSTALLER, BATTLENET, ANY, STEP_TEST }

/** Das Eingabefeld, in dem ein Fehler steht (die Seite zeigt ihn dort an). */
enum class LabField { WINE, TRANSLATOR, START_LINE, ANY_PROGRAM, ENV, DLL, SCREEN, DEBUG }

enum class LabIssueCode {
    /** Zeile ohne `=`. */
    ENV_NO_EQUALS,

    /** Name nicht aus Buchstaben, Ziffern und `_` (oder zu lang). */
    ENV_BAD_NAME,

    /** Name, den das Labor nicht überschreiben lässt (ändert Wine selbst oder den Start). */
    ENV_RESERVED,

    /** Eigenes Feld vorhanden (`WINEDLLOVERRIDES`, `WINEDEBUG`). */
    ENV_HAS_FIELD,

    /** Zeichen im Wert, die nicht hineingehören (Steuerzeichen). */
    ENV_BAD_VALUE,
    DLL_BAD_ENTRY,

    /** Eigener `WINEDEBUG`-Wert ist leer oder hat ein Zeichen, das nicht hineingehört. */
    DEBUG_BAD_ENTRY,
    LINE_UNBALANCED_QUOTE,
    LINE_EMPTY,
    LINE_NOT_WINDOWS_PATH,
    BNET_NOT_FOUND,
    SIZE_INVALID,
    WINE_MISSING,
    FEX_MISSING,
}

/** Ein abgewiesener Eintrag: [line] zählt ab 1 (null bei Feldern ohne Zeilen), [text] ist der Wortlaut der Zeile. */
data class LabIssue(val code: LabIssueCode, val field: LabField, val line: Int? = null, val text: String = "")

/** Was die Befehlslogik von außen braucht (nichts davon liest sie selbst). */
data class LabCommandContext(
    /** Umgebungsvariablen des Containers als Text (Grafik, Audio ...), vom Labor aus dem WoW-Container übernommen. */
    val baseEnv: String,
    /** Windows-Pfad von `Battle.net.exe` im Prefix, null wenn nicht gefunden. */
    val battleNetExe: String?,
    val installerPath: String = LabIds.INSTALLER_WIN_PATH,
    val bundledWine: String = LabIds.FALLBACK_WINE,
    /** Eintragsnamen der nachgeladenen Wine-Pakete. */
    val installedWine: Set<String> = emptySet(),
    /** Versionsnamen der nachgeladenen FEX-Pakete. */
    val installedFex: Set<String> = emptySet(),
    /** Computername der gespeicherten Kennung der App (für den Namens-Dienst des Wine-Prozesses); null = keine Kennung oder Schalter aus. Nie im Protokoll. */
    val identityHostname: String? = null,
)

/**
 * Der fertige Startplan: [program] und [args] sind die Befehlszeile im Prefix, [env] die Umgebung des Containers (Reihenfolge wie
 * später im Container), der Rest die Container-Felder. [issues] nicht leer: nichts wird gestartet, die Seite zeigt die Fehler.
 */
data class LabPlan(
    val target: LabTarget,
    val program: String,
    val args: List<String>,
    val env: List<Pair<String, String>>,
    val wineVersion: String,
    val emulator: String,
    val fexVersion: String?,
    val screenSize: String,
    val issues: List<LabIssue>,
    /** Wie das Programm gestartet wird (beim Stufentest immer direkt). */
    val startWay: LabStartWay = LabStartWay.DIRECT,
    /** Die Stufe der Stabilitäts-Schnellwahl, die wirklich gilt (siehe [LabStabilities.effective]). */
    val stability: LabStability = LabStability.OFF,
    /** Schalter "Übersetzer-Protokoll" (`BOX64_LOG`, `BOX64_DYNAREC_MISSING`). */
    val translatorLog: Boolean = false,
) {
    val ok: Boolean get() = issues.isEmpty()

    /** Schalter als ein Text für `execArgs` des Containers (Leerzeichen und leere Teile in Anführungszeichen). */
    val execArgs: String get() = args.joinToString(" ") { LabCommand.quote(it) }

    /** Die Umgebung im Textformat des Containers (`NAME=wert`, Leerzeichen und `\` mit `\` geschützt). */
    val envString: String get() = EnvVars().also { e -> env.forEach { (k, v) -> e.put(k, v) } }.toString()
}

/** Befehlszeile und Umgebung des Labors aus den Einstellungen; rein, ohne Android, JUnit-getestet. */
object LabCommand {
    private val ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
    private val WINDOWS_PATH = Regex("^[A-Za-z]:[\\\\/].*")
    private val SIZE = Regex("(\\d{3,4})x(\\d{3,4})")
    private val DLL_NAME = Regex("[A-Za-z0-9_.\\-]{1,64}")
    private val DLL_MODE = setOf("n", "b", "d")

    /** Ein Teil von `WINEDEBUG`: `+kanal`, `-kanal`, `klasse+kanal`, `klasse-kanal` (`err+all`, `fixme-all`). */
    private val DEBUG_PART = Regex("[a-z]{0,8}[+-][A-Za-z0-9_]{1,32}")

    /** Namen, die das Labor nicht aus dem Textfeld setzen lässt: sie legen Wine, den Start oder die Bibliotheken fest. */
    val RESERVED = setOf(
        "PATH", "HOME", "USER", "DISPLAY", "TMPDIR", "LD_PRELOAD", "LD_LIBRARY_PATH", "WINEPREFIX", "WINELOADER", "WINESERVER",
        "HODLL", "GUEST_PROGRAM_LAUNCHER_COMMAND", "PREFIX", "ANDROID_SYSVSHM_SERVER", "GN_OVERLAY_UPPER", "GN_OVERLAY_LOWER",
        MachineIdentity.ENV_HOSTNAME,
    )

    /** Namen, deren Wert die Kennung der App trägt: im Protokoll nie als Wert, nur als gesetzt. */
    private val IDENTITY_ENV = setOf(MachineIdentity.ENV_HOSTNAME)

    /** Namen mit eigenem Feld auf der Seite. */
    private val HAS_FIELD = setOf("WINEDLLOVERRIDES", "WINEDEBUG")

    private val SECRET_NAME = Regex("PASS|TOKEN|SECRET|KEY", RegexOption.IGNORE_CASE)

    /** Wert in Anführungszeichen, wenn er Leerzeichen enthält oder leer ist. */
    fun quote(arg: String): String =
        if (arg.isEmpty() || arg.any { it.isWhitespace() }) "\"" + arg.replace("\"", "\\\"") + "\"" else arg

    /** Absoluter Windows-Pfad (`X:\` oder `X:/`), dieselbe Regel wie für Startzeilen. */
    fun isAbsoluteWindowsPath(path: String): Boolean = WINDOWS_PATH.matches(path)

    /**
     * Zerlegt eine Zeile in Wörter; Anführungszeichen fassen Wörter mit Leerzeichen zusammen (kein `\` als Fluchtzeichen, wegen der
     * Windows-Pfade). Null bei einem offenen Anführungszeichen.
     */
    fun tokenize(line: String): List<String>? {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuote = false
        var started = false
        for (c in line) {
            when {
                c == '"' -> {
                    inQuote = !inQuote
                    started = true
                }
                c.isWhitespace() && !inQuote -> {
                    if (started) out += cur.toString()
                    cur.setLength(0)
                    started = false
                }
                else -> {
                    cur.append(c)
                    started = true
                }
            }
        }
        if (inQuote) return null
        if (started) out += cur.toString()
        return out
    }

    private fun looksLikeProgram(token: String): Boolean {
        val t = token.lowercase()
        return t.endsWith(".exe") || t.endsWith(".bat") || t.endsWith(".cmd")
    }

    /** Eine Zeile `NAME=wert` je Zeile; leere Zeilen und `#`-Zeilen entfallen. Gibt die gültigen Paare und die abgewiesenen Zeilen zurück. */
    fun parseEnv(text: String): Pair<List<Pair<String, String>>, List<LabIssue>> {
        val pairs = linkedMapOf<String, String>()
        val issues = mutableListOf<LabIssue>()
        text.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val n = index + 1
            val eq = line.indexOf('=')
            if (eq < 0) {
                issues += LabIssue(LabIssueCode.ENV_NO_EQUALS, LabField.ENV, n, line)
                return@forEachIndexed
            }
            val name = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            when {
                !ENV_NAME.matches(name) -> issues += LabIssue(LabIssueCode.ENV_BAD_NAME, LabField.ENV, n, line)
                name.uppercase() in HAS_FIELD -> issues += LabIssue(LabIssueCode.ENV_HAS_FIELD, LabField.ENV, n, line)
                name.uppercase() in RESERVED -> issues += LabIssue(LabIssueCode.ENV_RESERVED, LabField.ENV, n, line)
                value.any { it.isISOControl() } -> issues += LabIssue(LabIssueCode.ENV_BAD_VALUE, LabField.ENV, n, line)
                else -> pairs[name] = value
            }
        }
        return pairs.entries.map { it.key to it.value } to issues
    }

    /**
     * DLL-Overrides: ein Eintrag je Zeile (oder durch `;` getrennt) `name=n`, `name=n,b`, `name=b,n`, `name=d`; `name=` (leer) schaltet die
     * DLL ab. Ergibt den Wert für `WINEDLLOVERRIDES` (`a=n,b;b=d`), null wenn nichts eingetragen ist.
     */
    fun parseDllOverrides(text: String): Pair<String?, List<LabIssue>> {
        val parts = mutableListOf<String>()
        val issues = mutableListOf<LabIssue>()
        var n = 0
        for (raw in text.lines()) {
            n++
            for (entry in raw.split(';')) {
                val e = entry.trim()
                if (e.isEmpty() || e.startsWith("#")) continue
                val eq = e.indexOf('=')
                if (eq < 0) {
                    issues += LabIssue(LabIssueCode.DLL_BAD_ENTRY, LabField.DLL, n, e)
                    continue
                }
                val name = e.substring(0, eq).trim().removeSuffix(".dll")
                val modes = e.substring(eq + 1).split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (!DLL_NAME.matches(name) || modes.any { it !in DLL_MODE } || modes.size > 2 || (modes.size == 2 && "d" in modes)) {
                    issues += LabIssue(LabIssueCode.DLL_BAD_ENTRY, LabField.DLL, n, e)
                    continue
                }
                parts += "$name=${modes.joinToString(",")}"
            }
        }
        return (if (parts.isEmpty()) null else parts.joinToString(";")) to issues
    }

    /** Der Wert von `WINEDEBUG` für die gewählte Stufe; bei [LabWineDebug.CUSTOM] der geprüfte eigene Text (Leerzeichen am Rand entfallen). */
    fun resolveWineDebug(settings: LabSettings): Pair<String, LabIssue?> {
        if (settings.wineDebug != LabWineDebug.CUSTOM) return settings.wineDebug.value to null
        val text = settings.wineDebugCustom.trim()
        val ok = text.isNotEmpty() && text.length <= 300 && text.split(',').all { DEBUG_PART.matches(it) }
        return if (ok) text to null else "-all" to LabIssue(LabIssueCode.DEBUG_BAD_ENTRY, LabField.DEBUG, null, settings.wineDebugCustom)
    }

    /** Prüft die Fenstergröße `BREITExHÖHE` (gerade Zahlen, 640x480 bis 3840x2160). */
    fun checkScreenSize(size: String): LabIssue? {
        val m = SIZE.matchEntire(size.trim()) ?: return LabIssue(LabIssueCode.SIZE_INVALID, LabField.SCREEN, null, size)
        val w = m.groupValues[1].toInt()
        val h = m.groupValues[2].toInt()
        val ok = w in 640..3840 && h in 480..2160 && w % 2 == 0 && h % 2 == 0
        return if (ok) null else LabIssue(LabIssueCode.SIZE_INVALID, LabField.SCREEN, null, size)
    }

    /** Baut den Startplan; bei Fehlern in den Einstellungen steht in [LabPlan.issues] jeder abgewiesene Eintrag. */
    fun build(settings: LabSettings, target: LabTarget, ctx: LabCommandContext): LabPlan {
        val issues = mutableListOf<LabIssue>()

        var program = ""
        var args = emptyList<String>()
        when (target) {
            LabTarget.INSTALLER -> program = ctx.installerPath
            LabTarget.STEP_TEST -> {
                program = LabStepTest.CMD_EXE
                args = LabStepTest.CMD_ARGS
            }
            LabTarget.BATTLENET -> {
                val tokens = tokenize(settings.battleNetLine)
                if (tokens == null) {
                    issues += LabIssue(LabIssueCode.LINE_UNBALANCED_QUOTE, LabField.START_LINE, null, settings.battleNetLine)
                } else if (tokens.isNotEmpty() && looksLikeProgram(tokens.first())) {
                    program = tokens.first()
                    args = tokens.drop(1)
                    if (!WINDOWS_PATH.matches(program)) {
                        issues += LabIssue(LabIssueCode.LINE_NOT_WINDOWS_PATH, LabField.START_LINE, null, program)
                    }
                } else if (ctx.battleNetExe == null) {
                    issues += LabIssue(LabIssueCode.BNET_NOT_FOUND, LabField.START_LINE)
                } else {
                    program = ctx.battleNetExe
                    args = tokens
                }
            }
            LabTarget.ANY -> {
                val tokens = tokenize(settings.anyProgram)
                when {
                    tokens == null -> issues += LabIssue(LabIssueCode.LINE_UNBALANCED_QUOTE, LabField.ANY_PROGRAM, null, settings.anyProgram)
                    tokens.isEmpty() -> issues += LabIssue(LabIssueCode.LINE_EMPTY, LabField.ANY_PROGRAM)
                    !WINDOWS_PATH.matches(tokens.first()) ->
                        issues += LabIssue(LabIssueCode.LINE_NOT_WINDOWS_PATH, LabField.ANY_PROGRAM, null, tokens.first())
                    else -> {
                        program = tokens.first()
                        args = tokens.drop(1)
                    }
                }
            }
        }

        // Wine-Paket
        val wine = settings.wine.ifBlank { ctx.bundledWine }
        if (settings.wine.isNotBlank() && settings.wine !in ctx.installedWine) {
            issues += LabIssue(LabIssueCode.WINE_MISSING, LabField.WINE, null, settings.wine)
        }

        // Übersetzer
        val translator = LabTranslatorChoice.resolve(settings, ctx.installedFex)
        translator.issue?.let { issues += it }

        // Fenstergröße
        checkScreenSize(settings.screenSize)?.let { issues += it }

        // Umgebung: Grundwerte des Containers, die Zeilen der Stabilitätsstufe (und des Übersetzer-Protokolls), dann die eigenen Zeilen
        // (sie gewinnen), DLL-Overrides und WINEDEBUG.
        val stage = LabStabilities.effective(settings)
        val env = EnvVars(ctx.baseEnv)
        for ((k, v) in LabStabilities.envLines(stage)) env.put(k, v)
        if (settings.translatorLog) for ((k, v) in LabStabilities.TRANSLATOR_LOG_LINES) env.put(k, v)
        if (target == LabTarget.BATTLENET && issues.none { it.field == LabField.START_LINE }) args = LabStabilities.withStartArg(args, stage)
        val (userEnv, envIssues) = parseEnv(settings.extraEnv)
        issues += envIssues
        val (overrides, dllIssues) = parseDllOverrides(settings.dllOverrides)
        issues += dllIssues
        for ((k, v) in userEnv) env.put(k, v)
        if (overrides != null) env.put("WINEDLLOVERRIDES", overrides)
        val (debugValue, debugIssue) = resolveWineDebug(settings)
        debugIssue?.let { issues += it }
        env.put("WINEDEBUG", debugValue)
        if (settings.sameIdentity && ctx.identityHostname != null) env.put(MachineIdentity.ENV_HOSTNAME, ctx.identityHostname)
        val pairs = env.map { it to env.get(it) }

        return LabPlan(
            target = target,
            program = program,
            args = args,
            env = pairs,
            wineVersion = wine,
            emulator = translator.emulator,
            fexVersion = translator.fexVersion,
            screenSize = settings.screenSize.trim(),
            issues = issues,
            startWay = if (target == LabTarget.STEP_TEST) LabStartWay.DIRECT else settings.startWay,
            stability = stage,
            translatorLog = settings.translatorLog,
        )
    }

    /** Die Umgebung für das Protokoll: Werte von Namen mit PASS, TOKEN, SECRET oder KEY (groß oder klein) und der Name der Kennung sind durch [ReportRedactor.SECRET] ersetzt. */
    fun redactedEnvLines(env: List<Pair<String, String>>): List<String> =
        env.map { (k, v) -> "$k=" + if (SECRET_NAME.containsMatchIn(k) || k in IDENTITY_ENV) ReportRedactor.SECRET else v }
}

/** Welcher Übersetzer in den Container kommt. */
data class LabTranslatorChoice(val emulator: String, val fexVersion: String?, val issue: LabIssue?) {
    companion object {
        /** `FEXCore` und `none` hat `BionicProgramLauncherComponent` im Blick; jeder andere Wert nimmt den gebündelten `wowbox64`. */
        const val EMULATOR_BUNDLED = "Box64"
        const val EMULATOR_FEX = "FEXCore"
        const val EMULATOR_NONE = "none"

        fun resolve(settings: LabSettings, installedFex: Set<String>): LabTranslatorChoice = when (settings.translator) {
            LabTranslator.BUNDLED -> LabTranslatorChoice(EMULATOR_BUNDLED, null, null)
            LabTranslator.NONE -> LabTranslatorChoice(EMULATOR_NONE, null, null)
            LabTranslator.FEX ->
                if (settings.fexVersion.isNotBlank() && settings.fexVersion in installedFex) {
                    LabTranslatorChoice(EMULATOR_FEX, settings.fexVersion, null)
                } else {
                    LabTranslatorChoice(EMULATOR_FEX, null, LabIssue(LabIssueCode.FEX_MISSING, LabField.TRANSLATOR, null, settings.fexVersion))
                }
        }
    }
}

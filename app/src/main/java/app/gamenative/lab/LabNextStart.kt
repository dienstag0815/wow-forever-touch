package app.gamenative.lab

/** Warum die Wahl auf der Labor-Seite nicht (so) greift, oder was dem Nutzer auffallen sollte. Die Seite übersetzt es in Text. */
enum class LabNextNote {
    /** Übersetzer "gebündelt", aber ein FEX-Paket ist installiert: es wird nicht benutzt, solange FEX nicht gewählt ist. */
    FEX_INSTALLED_NOT_CHOSEN,

    /** FEX gewählt, aber kein FEX-Paket installiert: der Start wird abgewiesen. */
    FEX_CHOSEN_NONE_INSTALLED,

    /** FEX gewählt, aber keine Version aus den installierten Paketen gewählt: der Start wird abgewiesen. */
    FEX_CHOSEN_NO_VERSION,

    /** FEX gewählt, die gespeicherte Version ist nicht (mehr) installiert: der Start wird abgewiesen. */
    FEX_VERSION_NOT_INSTALLED,

    /** Ein Wine-Paket gewählt, das nicht (mehr) installiert ist: der Start wird abgewiesen. */
    WINE_NOT_INSTALLED,

    /** Ohne Übersetzer: Intel-Programme laufen nicht (nur als Gegenprobe gedacht). */
    NO_TRANSLATOR,
}

/**
 * Womit der nächste Start des Labors läuft, aus den gespeicherten Einstellungen und den installierten Paketen (nicht aus dem, was zuletzt
 * lief): Wine, Übersetzer samt Version und die Werte, die der Start daraus macht. [startBlocked]: der Start würde abgewiesen
 * (`LabCommand.build` meldet dann [LabIssueCode.WINE_MISSING] oder [LabIssueCode.FEX_MISSING]).
 */
data class LabNextStart(
    /** Kennung des Wine-Pakets, das läuft (das gebündelte, wenn nichts gewählt ist). */
    val wine: String,
    val wineBundled: Boolean,
    val translator: LabTranslator,
    /** Wert von `emulator` im Container: `Box64`, `FEXCore` oder `none`. */
    val emulator: String,
    /** Wert von `fexcoreVersion`, nur wenn FEX läuft. */
    val fexVersion: String?,
    /** Wert von `HODLL` im Start; null ohne Übersetzer. */
    val hodll: String?,
    val notes: List<LabNextNote>,
    /** Zu [LabNextNote.FEX_INSTALLED_NOT_CHOSEN]: der Versionsname des installierten FEX-Pakets. */
    val installedFex: String? = null,
    /** Zu [LabNextNote.FEX_VERSION_NOT_INSTALLED] und [LabNextNote.WINE_NOT_INSTALLED]: der gewählte, aber fehlende Name. */
    val missingName: String? = null,
    /** Die Stufe der Stabilitäts-Schnellwahl, die wirklich gilt (Stufe 4 ohne FEX-Wahl ist Stufe 1). */
    val stability: LabStability = LabStability.OFF,
    /** Die Zeilen der Stufe (`NAME=wert`, am Ende der Startschalter), wie sie das Protokoll nennt. */
    val stabilityLines: List<String> = emptyList(),
    val translatorLog: Boolean = false,
) {
    val startBlocked: Boolean
        get() = notes.any { it in BLOCKING }

    /** FEX läuft wirklich: gewählt und das Paket ist da. */
    val fexActive: Boolean
        get() = translator == LabTranslator.FEX && fexVersion != null

    companion object {
        private val BLOCKING = setOf(
            LabNextNote.FEX_CHOSEN_NONE_INSTALLED,
            LabNextNote.FEX_CHOSEN_NO_VERSION,
            LabNextNote.FEX_VERSION_NOT_INSTALLED,
            LabNextNote.WINE_NOT_INSTALLED,
        )
    }
}

/** Beschreibt den nächsten Start; rein, JUnit-getestet. Dieselben Regeln wie `LabCommand.build` und `LabTranslatorChoice.resolve`. */
object LabNextStarts {
    /** Wert von `HODLL`, den `BionicProgramLauncherComponent.getFinalCommand` aus `emulator` macht (`fexcore` ohne Rücksicht auf Groß-/Kleinschreibung, `none` nichts, sonst `wowbox64.dll`). */
    fun hodll(emulator: String): String? = when {
        emulator.equals("fexcore", ignoreCase = true) -> "libwow64fex.dll"
        emulator.equals("none", ignoreCase = true) -> null
        else -> "wowbox64.dll"
    }

    fun describe(settings: LabSettings, installed: List<LabInstalledPackage>, bundledWine: String = LabIds.FALLBACK_WINE): LabNextStart {
        val notes = mutableListOf<LabNextNote>()
        var missing: String? = null

        val installedWine = installed.filter { it.kind == LabPackageKind.WINE }.mapNotNull { it.identifier }.toSet()
        val wineBundled = settings.wine.isBlank()
        val wine = if (wineBundled) bundledWine else settings.wine
        if (!wineBundled && settings.wine !in installedWine) {
            notes += LabNextNote.WINE_NOT_INSTALLED
            missing = settings.wine
        }

        val fexNames = installed.filter { it.kind == LabPackageKind.FEX }.map { it.verName }
        val choice = LabTranslatorChoice.resolve(settings, fexNames.toSet())
        var installedFex: String? = null
        when (settings.translator) {
            LabTranslator.BUNDLED -> if (fexNames.isNotEmpty()) {
                notes += LabNextNote.FEX_INSTALLED_NOT_CHOSEN
                installedFex = fexNames.first()
            }
            LabTranslator.NONE -> notes += LabNextNote.NO_TRANSLATOR
            LabTranslator.FEX -> if (choice.issue != null) {
                when {
                    fexNames.isEmpty() -> notes += LabNextNote.FEX_CHOSEN_NONE_INSTALLED
                    settings.fexVersion.isBlank() -> notes += LabNextNote.FEX_CHOSEN_NO_VERSION
                    else -> {
                        notes += LabNextNote.FEX_VERSION_NOT_INSTALLED
                        missing = missing ?: settings.fexVersion
                    }
                }
            }
        }
        return LabNextStart(
            wine = wine,
            wineBundled = wineBundled,
            translator = settings.translator,
            emulator = choice.emulator,
            fexVersion = if (choice.issue == null) choice.fexVersion else null,
            hodll = hodll(choice.emulator),
            notes = notes,
            installedFex = installedFex,
            missingName = missing,
            stability = LabStabilities.effective(settings),
            stabilityLines = LabStabilities.describeLines(LabStabilities.effective(settings)),
            translatorLog = settings.translatorLog,
        )
    }
}

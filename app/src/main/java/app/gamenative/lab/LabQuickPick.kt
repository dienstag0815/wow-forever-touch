package app.gamenative.lab

/** Die drei Schnellwahl-Knöpfe unter der Auswahl von Wine und Übersetzer (sie setzen nur die Einstellungen). */
enum class LabQuickPick {
    /** 1: Proton 11 und `wowbox64`, alles Gebündelte. */
    PROTON11_BUNDLED,

    /** 2: Proton 11 und FEX. */
    PROTON11_FEX,

    /** 3: Proton 10 (nachgeladen) und FEX. */
    PROTON10_FEX,
}

sealed class LabQuickPickResult {
    /** Die neuen Einstellungen (nur Wine, Übersetzer und FEX-Version sind anders als vorher). */
    data class Applied(val settings: LabSettings) : LabQuickPickResult()

    /** Es fehlt ein Paket: die Kennungen aus `bnetlab_pakete.json`, die zu laden sind; die Einstellungen bleiben unverändert. */
    data class Missing(val packageIds: List<String>) : LabQuickPickResult()
}

/** Auswahl der Schnellwahl aus den installierten Paketen; rein, JUnit-getestet. */
object LabQuickPicks {
    const val PROTON10_PACKAGE_ID = "wine-ge-proton-10.0-34-arm64ec"
    const val FEX_PACKAGE_ID = "fex-2508"
    const val FEX_SECOND_PACKAGE_ID = "fex-2605"

    private fun proton10(installed: List<LabInstalledPackage>): LabInstalledPackage? =
        installed.firstOrNull { it.kind == LabPackageKind.WINE && it.identifier != null && it.verName.lowercase().startsWith("proton-10") }

    /** Das FEX-Paket: bevorzugt der Eintrag [FEX_PACKAGE_ID] (`lab-2508`), dann [FEX_SECOND_PACKAGE_ID], dann irgendein FEX-Paket. */
    internal fun fex(installed: List<LabInstalledPackage>, packages: List<LabPackage>): LabInstalledPackage? {
        val fexes = installed.filter { it.kind == LabPackageKind.FEX }
        for (id in listOf(FEX_PACKAGE_ID, FEX_SECOND_PACKAGE_ID)) {
            val pkg = packages.firstOrNull { it.id == id } ?: continue
            fexes.firstOrNull { it.verName == LabPackages.fexVerName(pkg) }?.let { return it }
        }
        return fexes.firstOrNull()
    }

    fun resolve(pick: LabQuickPick, current: LabSettings, installed: List<LabInstalledPackage>, packages: List<LabPackage>): LabQuickPickResult {
        if (pick == LabQuickPick.PROTON11_BUNDLED) {
            return LabQuickPickResult.Applied(current.copy(wine = "", translator = LabTranslator.BUNDLED, fexVersion = ""))
        }
        val missing = mutableListOf<String>()
        val fexPackage = fex(installed, packages)
        if (fexPackage == null) missing += FEX_PACKAGE_ID
        var wine = ""
        if (pick == LabQuickPick.PROTON10_FEX) {
            val w = proton10(installed)
            if (w == null) missing += PROTON10_PACKAGE_ID else wine = w.identifier.orEmpty()
        }
        if (missing.isNotEmpty() || fexPackage == null) return LabQuickPickResult.Missing(missing)
        return LabQuickPickResult.Applied(current.copy(wine = wine, translator = LabTranslator.FEX, fexVersion = fexPackage.verName))
    }

    /** Die Vorschläge unter dem Feld für Umgebungsvariablen (nichts davon ist Vorgabe): der Text auf dem Knopf. */
    val ENV_SUGGESTIONS = listOf(
        "WINE_SIMULATE_WRITECOPY=1",
        "WINEDLLOVERRIDES=locationapi=d",
        "BOX64_DYNAREC_SAFEFLAGS=2",
        "BOX64_DYNAREC_STRONGMEM=1",
        "BOX64_DYNAREC_ALIGNED_ATOMICS=0",
        "MESA_SHADER_CACHE_DISABLE=true",
    )

    /** Die Zeile, die ein Vorschlag einfügt, und das Feld: `WINEDLLOVERRIDES` hat ein eigenes Feld, dort steht `locationapi=d`. */
    fun suggestionTarget(suggestion: String): Pair<LabField, String> =
        if (suggestion.startsWith("WINEDLLOVERRIDES=")) LabField.DLL to suggestion.removePrefix("WINEDLLOVERRIDES=") else LabField.ENV to suggestion

    /**
     * Fügt eine Zeile ans Ende des Feldes (eigene Zeile); steht sie schon darin, bleibt der Text.
     */
    fun addLine(text: String, line: String): String {
        if (text.lines().any { it.trim() == line }) return text
        return if (text.isBlank()) line else text.trimEnd('\n') + "\n" + line
    }
}

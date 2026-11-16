package app.gamenative.lab

/** Ergebnis von [LabLoginWay.apply]. */
sealed class LabLoginWayResult {
    /** Die neuen Einstellungen; gegenüber vorher sind nur die Felder aus [LabLoginWay.CHANGED_FIELDS] anders. */
    data class Applied(val settings: LabSettings) : LabLoginWayResult()

    /** Es fehlt das FEX-Paket: die Kennungen aus `bnetlab_pakete.json`, die zu laden sind; die Einstellungen bleiben unverändert. */
    data class FexMissing(val packageIds: List<String>) : LabLoginWayResult()
}

/**
 * Voreinstellung "Anmeldeweg": die Einstellung, mit der der Weg über den echten Launcher am Gerät bisher gelang (gebündeltes Wine, Übersetzer FEX
 * mit installiertem Paket, Stufe 4, ohne Spielkopie). Setzt nur diese Felder; nichts anderes an den Einstellungen ändert sich. Lädt nichts:
 * fehlt das FEX-Paket, bleibt alles wie es ist und der Aufrufer bietet das vorhandene Laden an. Die Spielsteuerung ("Spiel" in der Tastenleiste)
 * ist keine gespeicherte Einstellung: jeder Lauf beginnt mit ihr aus (`LabGcState()`), die Voreinstellung muss dafür nichts tun.
 * Rein, ohne Android, JUnit-getestet.
 */
object LabLoginWay {
    /** Die Felder von [LabSettings], die [apply] setzt (der Test vergleicht alle übrigen Felder auf Gleichheit). */
    val CHANGED_FIELDS = listOf("wine", "translator", "fexVersion", "stability", "useGameCopy")

    fun apply(current: LabSettings, installed: List<LabInstalledPackage>, packages: List<LabPackage>): LabLoginWayResult =
        when (val pick = LabQuickPicks.resolve(LabQuickPick.PROTON11_FEX, current, installed, packages)) {
            is LabQuickPickResult.Missing -> LabLoginWayResult.FexMissing(pick.packageIds)
            is LabQuickPickResult.Applied -> LabLoginWayResult.Applied(
                LabStabilities.normalize(pick.settings.copy(stability = LabStability.FEX, useGameCopy = false)),
            )
        }

    /**
     * Ob die Einstellungen der Voreinstellung entsprechen (für die Zeile `Sign-in setup:` im Protokoll): gebündeltes Wine, Übersetzer FEX mit
     * gewählter Version, Stufe 4, Schalter "Use game copy" aus. Ob das Paket wirklich installiert ist, prüft der Start (sonst gibt es keinen Lauf).
     */
    fun isApplied(settings: LabSettings): Boolean = isAppliedCore(settings) && !settings.useGameCopy

    /**
     * Wie [isApplied], aber ohne den Schalter „Use game copy“: für den Assistenten, in dem die Kopie später eingeschaltet wird, ohne dass die
     * Voreinstellung dadurch als „nicht gesetzt“ gilt.
     */
    fun isAppliedCore(settings: LabSettings): Boolean =
        settings.wine.isBlank() &&
            settings.translator == LabTranslator.FEX &&
            settings.fexVersion.isNotBlank() &&
            LabStabilities.effective(settings) == LabStability.FEX

    /** Wie [apply], lässt aber den Schalter „Use game copy“ so, wie er ist (der Assistent setzt die Voreinstellung auch nach dem Anlegen der Kopie). */
    fun applyKeepingCopy(current: LabSettings, installed: List<LabInstalledPackage>, packages: List<LabPackage>): LabLoginWayResult =
        when (val r = apply(current, installed, packages)) {
            is LabLoginWayResult.Applied -> LabLoginWayResult.Applied(r.settings.copy(useGameCopy = current.useGameCopy))
            is LabLoginWayResult.FexMissing -> r
        }

    /** Die Zeile im Kopf des Protokolls. */
    fun headerLine(settings: LabSettings): String = "Sign-in setup: ${if (isApplied(settings)) "applied" else "not applied"}"
}

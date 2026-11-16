package app.gamenative.lab

import android.content.Context

/**
 * Die Fragen der Startwahl an die App selbst: Dateien (nur Existenz), Pakete, Einstellungen, Selbstauskunft und Verlauf der Läufe, dieselben
 * Quellen wie der Assistent ([LabGuideSources]); nichts aus Dateien des Launchers. Läuft im Hintergrund (Dateizugriffe, Paketliste).
 */
object LabStartSources {
    /** Die Fakten der Startwahl; wirft nie (ein Fehler ergibt `null`, der Aufrufer nimmt dann den sicheren Weg). */
    fun facts(context: Context): LabStartFacts? = runCatching {
        val app = context.applicationContext
        val text = runCatching { app.assets.open("bnetlab_pakete.json").use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        val packages = LabPackages.parse(text).packages
        val inputs = LabGuideSources.guideInputs(
            LabGuideSources.fileFacts(app),
            LabStore.settings(app),
            LabLauncher.installedPackages(app),
            packages,
            LabStore.guideSelf(app),
            LabGuideCopy(),
            LabStore.runs(app),
        )
        LabStartMode.facts(LabStore.startMode(app), LabGuide.evaluate(inputs))
    }.getOrNull()

    /** Wirksame Startart für die Zeile im Protokoll; `null` = nicht ermittelbar. */
    fun effective(context: Context): EffectiveStart? = facts(context)?.let { LabStartMode.effective(it) }
}

package app.gamenative.lab

import com.winlator.core.WineRegistryEditor
import com.winlator.container.Container
import java.io.File
import timber.log.Timber

/**
 * Wenige feste Schalter für Wines X11-Treiber (`HKCU\Software\Wine\X11 Driver`), zum Ausprobieren der Tastatureingabe ohne neuen Bau.
 * [DEFAULT] ändert nichts. Die Werte gehören nur in den Prefix des Labors; der Spiel-Container wird nie angefasst.
 */
enum class LabX11Preset(val key: String, val values: List<Pair<String, String>>) {
    DEFAULT("default", emptyList()),
    TAKE_FOCUS_ON("take_focus_on", listOf("UseTakeFocus" to "Y")),
    TAKE_FOCUS_OFF("take_focus_off", listOf("UseTakeFocus" to "N")),
    NO_XIM("no_xim", listOf("UseXIM" to "N")),
    UNMANAGED("unmanaged", listOf("Managed" to "N")),
    ;

    companion object {
        fun fromKey(key: String?): LabX11Preset = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Was in die Registry des Labor-Prefix geschrieben (oder entfernt) wird. Rein; die Datei schreibt [LabX11Hook]. */
object LabX11Options {
    const val REG_KEY = "Software\\Wine\\X11 Driver"

    /** Alle Werte, die das Labor je setzt: bei jedem Start erst entfernt, dann die gewählten geschrieben (die Vorgabe lässt keine Reste). */
    val NAMES: List<String> = listOf("UseTakeFocus", "UseXIM", "Managed")

    /** Ein Schritt: [value] null = Wert entfernen. */
    data class Op(val name: String, val value: String?)

    fun plan(preset: LabX11Preset): List<Op> = NAMES.map { name -> Op(name, preset.values.firstOrNull { it.first == name }?.second) }

    fun protocolLines(preset: LabX11Preset, outcome: String): List<String> = buildList {
        add("X11 driver options: ${preset.key}")
        add("Key: HKCU\\$REG_KEY (Labor-Prefix only)")
        for (op in plan(preset)) add("  ${op.name} = ${op.value ?: "(not set)"}")
        add(outcome)
    }
}

/** Schreibt die gewählten X11-Werte vor dem Start in `user.reg` des Labor-Prefix (Einhängestelle: `LabIdentityHook.apply`, nur Container `bnetlab`). */
object LabX11Hook {
    fun apply(container: Container?, preset: LabX11Preset) {
        if (container == null || container.id != LabIds.CONTAINER_ID) return
        val userReg = File(File(container.rootDir, ".wine"), "user.reg")
        val outcome = if (!userReg.isFile) {
            "Result: user.reg does not exist yet, nothing written"
        } else {
            try {
                WineRegistryEditor(userReg).use { editor ->
                    editor.setCreateKeyIfNotExist(true)
                    for (op in LabX11Options.plan(preset)) {
                        if (op.value == null) editor.removeValue(LabX11Options.REG_KEY, op.name)
                        else editor.setStringValue(LabX11Options.REG_KEY, op.name, op.value)
                    }
                }
                "Result: written"
            } catch (e: Exception) {
                Timber.w("Lab X11 options not written: %s", e.javaClass.simpleName)
                "Result: failed (${e.javaClass.simpleName})"
            }
        }
        LabRun.appendSection(container.id, "X11 driver options", LabX11Options.protocolLines(preset, outcome))
    }
}

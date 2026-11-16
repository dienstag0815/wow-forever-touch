package app.gamenative.lab

import android.content.Context
import android.view.View
import app.gamenative.PluviaApp
import app.gamenative.data.TouchGestureConfig
import app.gamenative.ui.screen.xserver.ControlsVisibilityPref
import app.gamenative.ui.screen.xserver.showInputControls
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import org.json.JSONObject
import java.io.File
import timber.log.Timber

/** Was [LabGameControlsApply.prepare] vorbereitet hat: die Werte und der Labor-Container (nur im Speicher verändert, nie gespeichert). */
class LabGcPrepared(val values: LabControlValues, val labContainer: Container?)

/**
 * Schaltet die Spielsteuerung im laufenden Labor-Lauf um, ohne den Container zu speichern und ohne den Spielbildschirm anzufassen: die Werte
 * kommen aus der Datei `.container` des Spiel-Containers (nur gelesen), angewendet wird über die Ansichten, die `PluviaApp` hält
 * (`touchpadView`, `inputControlsView`), und die vorhandene Funktion `showInputControls` des Spielbildschirms. Beim nächsten Labor-Start schreibt
 * `LabContainer.prepare` wieder die Werte von [LabGameControls.OFF].
 */
object LabGameControlsApply {
    /** Die Werte des Spiel-Containers (nur lesen); null, wenn es ihn nicht gibt. */
    fun readGameValues(context: Context): LabControlValues? {
        val file = File(LabDirs.wowContainerRoot(context), ".container")
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
        return LabGameControls.fromGameContainer(json, ControlsVisibilityPref.load(context, LabIds.WOW_CONTAINER_ID))
    }

    /** Liest Werte und Labor-Container (Dateien, nicht auf dem Haupt-Thread). Für „an“ die Werte des Spiel-Containers, für „aus“ die von [LabGameControls.OFF]. */
    fun prepare(context: Context, on: Boolean): LabGcPrepared? {
        val values = if (on) readGameValues(context) else LabGameControls.OFF
        values ?: return null
        val lab = runCatching { ContainerManager(context).getContainerById(LabIds.CONTAINER_ID) }.getOrNull()
        // Nur im Speicher: das Spiel liest die Schussansicht aus dem Container, den es von `showInputControls` bekommt.
        lab?.setShooterMode(values.shooterMode)
        lab?.setShooterConfig(values.shooterConfig)
        return LabGcPrepared(values, lab)
    }

    /** Wendet [prepared] an (Haupt-Thread). Wahr, wenn die Ansichten da waren. */
    fun apply(prepared: LabGcPrepared, on: Boolean): Boolean {
        val touchpad = PluviaApp.touchpadView
        val icView = PluviaApp.inputControlsView
        val xServerView = PluviaApp.xServerView
        if (touchpad == null || icView == null || xServerView == null) return false
        val values = prepared.values
        val gestures = TouchGestureConfig.fromJson(if (on) values.gestureConfig else prepared.labContainer?.gestureConfig.orEmpty())
        touchpad.setTouchscreenMode(values.touchscreenMode)
        if (values.touchscreenMode) touchpad.setGestureConfig(gestures)
        // Der Zeiger wie im Spielbildschirm (`shouldShowMouseCursor`): bei Touch nur, wenn die Gesten ihn zeigen.
        xServerView.renderer?.setCursorVisible(!values.touchscreenMode || gestures.showCursorInTouchscreenMode)
        val winHandler = xServerView.getxServer()?.winHandler
        if (on && values.controlsVisible && winHandler != null) {
            val manager = PluviaApp.inputControlsManager
            val profile = values.profileNumber.takeIf { it != 0 }?.let { manager?.getProfile(it) }
                ?: manager?.getProfile(0)
                ?: manager?.getProfiles(false)?.let { it.getOrNull(2) ?: it.firstOrNull() }
            val container = prepared.labContainer
            if (profile != null && container != null) {
                showInputControls(profile, winHandler, container)
            } else {
                Timber.w("Labor: Spielsteuerung ohne Profil oder Container (profile=%s container=%s)", profile != null, container != null)
            }
        } else {
            hideControls(icView, touchpad, xServerView, winHandler, values.shooterMode)
        }
        return true
    }

    /** Wie `hideInputControls` im Spielbildschirm (privat dort): Bildschirmsteuerung weg, Zeiger und Tasten des Touchpads wieder frei. */
    private fun hideControls(
        icView: com.winlator.widget.InputControlsView,
        touchpad: com.winlator.widget.TouchpadView,
        xServerView: com.winlator.widget.XServerRendererView,
        winHandler: com.winlator.winhandler.WinHandler?,
        shooterMode: Boolean,
    ) {
        icView.setContainerShooterMode(shooterMode)
        icView.setShowTouchscreenControls(false)
        icView.setVisibility(View.GONE)
        icView.hideProfileForOverlay()
        winHandler?.refreshControllerMappingsForHotplug()
        touchpad.setSensitivity(1.0f)
        touchpad.setPointerButtonLeftEnabled(true)
        touchpad.setPointerButtonRightEnabled(true)
        if (!touchpad.isEnabled) {
            touchpad.setEnabled(true)
            xServerView.renderer?.setCursorVisible(true)
        }
        icView.invalidate()
    }
}

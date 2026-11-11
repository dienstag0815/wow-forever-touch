package app.gamenative.ui.screen.xserver.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.ui.screen.wow.LayoutNameDialog
import app.gamenative.ui.screen.wow.LayoutSwitcher
import app.gamenative.ui.screen.wow.TemplateEditedDialog
import app.gamenative.ui.screen.wow.TouchLayouts
import app.gamenative.ui.util.SnackbarManager
import com.winlator.container.Container
import com.winlator.inputcontrols.ControlsProfile

/**
 * "Fertig" im Editor mit Änderungen an einer Vorlage der App ([TouchLayouts.isTemplate]): nichts wird still behalten. Der Spieler
 * sichert die Änderungen als eigenes Layout (die Vorlage kommt unverändert zurück), ändert die Vorlage selbst oder verwirft.
 * Eigene Layouts und unveränderte Vorlagen gehen ohne Rückfrage durch.
 */
internal class TemplateEditGuard(private val session: ControlsEditorSession) {
    private var pending by mutableStateOf<ControlsProfile?>(null)
    private var askName by mutableStateOf(false)

    /** true = die Rückfrage ist offen und "Fertig" wartet auf die Antwort; false = ohne Rückfrage weiter. */
    fun intercept(profile: ControlsProfile?): Boolean {
        if (profile == null || !TouchLayouts.needsTemplatePrompt(profile.name, session.changedSinceStart())) return false
        pending = profile
        return true
    }

    @Composable
    fun Dialogs(onDone: () -> Unit, onCancel: () -> Unit, container: Container?) {
        val template = pending ?: return
        val context = LocalContext.current
        val close = {
            pending = null
            askName = false
        }
        if (askName) {
            val names = PluviaApp.inputControlsManager?.getProfiles(false)?.map { it.name }.orEmpty()
            LayoutNameDialog(
                existingProfileNames = names,
                onSave = { input ->
                    // Die Änderungen liegen schon in der Datei der Vorlage: erst kopieren, dann den Editor mit "Abbrechen" verlassen
                    // (das schreibt die Vorlage so zurück, wie sie vorher war), dann auf die Kopie umschalten.
                    val copy = LayoutSwitcher.copyAs(context, template.id, input)
                    if (copy == null) {
                        SnackbarManager.show(context.getString(R.string.game_layout_save_failed))
                        askName = false
                    } else {
                        close()
                        onCancel()
                        LayoutSwitcher.activate(context, container, copy.id, copy.name)
                        SnackbarManager.show(context.getString(R.string.game_layout_saved, TouchLayouts.displayName(copy.name)))
                    }
                },
                onDismiss = { askName = false },
            )
        } else {
            TemplateEditedDialog(
                templateDisplayName = TouchLayouts.displayName(template.name),
                onSaveOwn = { askName = true },
                onChangeTemplate = {
                    close()
                    onDone()
                },
                onDiscard = {
                    close()
                    onCancel()
                },
            )
        }
    }
}

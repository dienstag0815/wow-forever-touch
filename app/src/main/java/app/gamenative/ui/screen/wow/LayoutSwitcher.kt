package app.gamenative.ui.screen.wow

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.ui.screen.xserver.showInputControls
import com.winlator.container.Container
import com.winlator.inputcontrols.ControlsProfile
import timber.log.Timber

/**
 * Wechselt im laufenden Spiel auf ein anderes Touch-Layout und legt Kopien an. Gemeinsam für das Seitenmenü ("Layout",
 * "Neues Layout speichern") und den Editor (geänderte Vorlage als eigenes Layout sichern).
 */
internal object LayoutSwitcher {
    /**
     * Macht das Profil [profileId] zum aktiven Layout: gemerkt für den nächsten Start ([TouchLayouts.choose]), im Container
     * gesetzt und sofort angezeigt.
     */
    fun activate(context: Context, container: Container?, profileId: Int, profileName: String) {
        TouchLayouts.choose(context, profileName)
        container?.let {
            it.putExtra("profileId", profileId.toString())
            it.saveData()
        }
        showNow(context, container, profileId)
    }

    /**
     * Zeigt das Profil [profileId] sofort an, wenn die Touch-Steuerung gerade sichtbar ist (Ansicht und Ringe); sonst gilt es beim
     * nächsten Einblenden (der Container hat die ID). Die Controller-Belegung (physischer Controller) bleibt bis zum nächsten Start.
     */
    fun showNow(context: Context, container: Container?, profileId: Int) {
        try {
            val profile = WowTouchProfiles.manager(context).getProfile(profileId) ?: return
            val winHandler = PluviaApp.xServerView?.getxServer()?.winHandler
            if (container != null && PluviaApp.inputControlsView?.profile != null && winHandler != null) {
                showInputControls(profile, winHandler, container)
            } else {
                PluviaApp.radialMenuCoordinator?.setProfile(profile)
            }
        } catch (e: Exception) {
            Timber.w("Layout anzeigen fehlgeschlagen: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Kopie des Profils [sourceId] (Elemente, Ringe, Radials, Belegungen, so wie die Datei jetzt ist) unter dem Layoutnamen zu
     * [input]. Die Kopie ist keine Vorlage der App, ein Update fasst sie nie an. Null bei leerem oder vergebenem Namen oder wenn
     * die Datei nicht geschrieben werden kann.
     */
    fun copyAs(context: Context, sourceId: Int, input: String): ControlsProfile? {
        val manager = WowTouchProfiles.manager(context)
        val newName = TouchLayouts.profileNameFor(input) ?: return null
        val taken = manager.getProfiles(false).map { it.name }
        if (TouchLayouts.nameProblem(input, taken) != TouchLayouts.NameProblem.NONE) return null
        val source = manager.getProfile(sourceId) ?: return null
        return try {
            manager.copyProfileAs(source, newName)
        } catch (e: Exception) {
            Timber.w("Layout kopieren fehlgeschlagen: ${e.javaClass.simpleName}")
            null
        }
    }
}

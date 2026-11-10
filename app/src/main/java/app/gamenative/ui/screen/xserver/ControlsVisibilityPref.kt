package app.gamenative.ui.screen.xserver

import android.content.Context

/**
 * Merkt sich pro Container, ob die On-Screen-Controller sichtbar sein sollen.
 * Ohne gespeicherten Wert (null) gilt die automatische Entscheidung.
 */
object ControlsVisibilityPref {

    private const val PREFS_NAME = "controls_visibility"

    private fun key(containerId: String): String = "visible_$containerId"

    /** Gespeicherter Wert für den Container, oder null wenn nie gesetzt. */
    fun load(context: Context, containerId: String): Boolean? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val k = key(containerId)
        return if (prefs.contains(k)) prefs.getBoolean(k, false) else null
    }

    fun save(context: Context, containerId: String, visible: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key(containerId), visible)
            .apply()
    }

    /** Gespeicherter Wert gewinnt; ohne gespeicherten Wert gilt der automatische Default. */
    fun decide(stored: Boolean?, autoDefault: Boolean): Boolean = stored ?: autoDefault
}

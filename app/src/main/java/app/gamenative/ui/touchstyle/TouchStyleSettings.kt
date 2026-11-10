package app.gamenative.ui.touchstyle

import app.gamenative.R
import app.gamenative.ui.components.AppText
import android.content.Context
import app.gamenative.cooldown.CooldownService

/**
 * Gewählter Stil und Kontrast der Touch-Elemente. Ein Objekt für den Prozess: die Steuerungsansicht liest [style] und [contrast]
 * bei jedem Zeichnen (zwei Feldzugriffe), Seitenmenü und Einstellungsseite setzen sie, dann wirkt es sofort ohne Neustart.
 * Gespeichert in denselben Einstellungen wie die übrigen Touch-Schalter (`wow_forever`). Standard bleibt das heutige Aussehen.
 */
object TouchStyleSettings {
    private const val PREFS = "wow_forever"
    private const val KEY_STYLE = "touch_style"
    private const val KEY_CONTRAST = "touch_contrast"

    @Volatile var style: TouchStyle = TouchStyle.CURRENT
        private set

    @Volatile var contrast: Float = TouchStyleMath.CONTRAST_DEFAULT
        private set

    @Volatile private var loaded = false

    /** Liest die gespeicherten Werte einmal (jede Zeile, die ihn braucht, darf aufrufen). */
    @JvmStatic
    fun init(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        style = TouchStyle.fromId(prefs.getString(KEY_STYLE, null))
        contrast = TouchStyleMath.clampContrast(prefs.getFloat(KEY_CONTRAST, TouchStyleMath.CONTRAST_DEFAULT))
        loaded = true
    }

    /** Stil wählen: speichern und die Ansichten neu zeichnen. */
    fun setStyle(context: Context, next: TouchStyle) {
        init(context)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STYLE, next.id).apply()
        style = next
        TouchStyleDraw.clearCache()
        CooldownService.requestRedraw()
    }

    /** Kontrast wählen (70 bis 150 %): speichern und neu zeichnen. */
    fun setContrast(context: Context, value: Float) {
        init(context)
        val clamped = TouchStyleMath.clampContrast(value)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_CONTRAST, clamped).apply()
        contrast = clamped
        CooldownService.requestRedraw()
    }

    fun styleLabel(): String = AppText.get(R.string.game_style_line, style.label)

    fun contrastLabel(): String = AppText.get(R.string.game_contrast_line, TouchStyleMath.contrastLabel(contrast))
}

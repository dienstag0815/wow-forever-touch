package app.gamenative.ui.screen.wow.launcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import app.gamenative.PrefManager
import app.gamenative.utils.LocaleHelper

/**
 * Die gewählte Sprache der App (Android-Seite von [AppLanguage]). [currentTag] liest die Wahl bei jedem Aufruf frisch, nichts wird
 * zwischengespeichert; wer Texte außerhalb von Compose braucht (Addon-Sprachdatei, Meldungen), fragt hier. [set] speichert die Wahl,
 * stellt die Anwendungs-Ressourcen um und baut die Activity neu auf, die beim Start (MainActivity.attachBaseContext) die neue
 * Sprache übernimmt.
 */
internal object AppLanguageSetting {
    /** "en" oder "de": die Sprache, in der die App gerade läuft. */
    fun currentTag(): String = AppLanguage.tagFor(PrefManager.appLanguage)

    /** Speichert [tag] (nur "en" oder "de" zählen), stellt die Anwendung um und baut die Activity von [context] neu auf. */
    fun set(context: Context, tag: String) {
        val code = AppLanguage.tagFor(tag)
        // Sofort geschrieben, nicht im Hintergrund: die neue Activity liest die Wahl gleich beim Start.
        PrefManager.setAppLanguageNow(code)
        LocaleHelper.applyToApplication(context.applicationContext, code)
        findActivity(context)?.recreate()
    }

    private tailrec fun findActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}

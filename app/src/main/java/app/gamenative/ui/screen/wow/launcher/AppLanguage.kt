package app.gamenative.ui.screen.wow.launcher

/**
 * Die Sprache der App als reine Entscheidung (ohne Android, per JUnit getestet): Englisch ist der Standard und gilt für jede
 * Installation, auch auf einem deutschen Handy; Deutsch gibt es als Auswahl in den Einstellungen ("Language / Sprache"). Die
 * Systemsprache wird nicht gelesen. Gespeichert wird die Wahl in `PrefManager.appLanguage` (leer = nichts gewählt = Englisch).
 */
internal object AppLanguage {
    const val ENGLISH = "en"
    const val GERMAN = "de"

    /** Ohne Wahl gilt Englisch. */
    const val DEFAULT = ENGLISH

    /** Die wählbaren Sprachen in der Reihenfolge der Auswahl. */
    val CHOICES: List<String> = listOf(ENGLISH, GERMAN)

    /**
     * Sprach-Tag für die gespeicherte Wahl [stored]: "de" (auch "de-AT", "DE") ergibt Deutsch, alles andere (null, leer, ein
     * früher gewählter fremder Code) ergibt [DEFAULT].
     */
    fun tagFor(stored: String?): String = when (stored?.trim()?.lowercase()?.substringBefore('-')) {
        GERMAN -> GERMAN
        else -> DEFAULT
    }

    /** Name der Sprache in ihrer eigenen Sprache, wie ihn die Auswahl zeigt (nicht übersetzt, damit man ihn immer erkennt). */
    fun nativeName(tag: String): String = when (tagFor(tag)) {
        GERMAN -> "Deutsch"
        else -> "English"
    }
}

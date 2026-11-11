package app.gamenative.ui.screen.wow

import android.content.Context

/**
 * Die wählbaren Touch-Layouts als reine Logik. Ein Layout ist ein Steuerungsprofil, dessen Name "Touch (…)" lautet: die drei
 * Vorlagen der App ("Standard", "WoW", "WoW komplett"), "Mein Layout" und alles, was der Spieler mit "Als neues Layout speichern"
 * anlegt. Die Namen sind die Wahrheit (kein eigenes Verzeichnis), darum überlebt die Auswahl Datensicherung und Wiederherstellung.
 *
 * Gemerkt wird die Auswahl eines eigenen Layouts als Profilname ([chosenName]); der Modus ist dann [InputMode.TOUCH_CUSTOM].
 * Die Vorlagen wählen ihren eigenen Modus, damit der Start ihre Gesten und Zurücksetzen-Datei kennt.
 */
internal object TouchLayouts {
    private const val PREFIX = "Touch ("
    private const val SUFFIX = ")"
    private const val KEY_CHOSEN = "touch_layout_name"

    /** Längster Name, den der Spieler vergeben kann (die Zeile im Seitenmenü hat nur ~287 dp). */
    const val NAME_MAX = 24

    /** Die Vorlagen der App; sie werden beim Bearbeiten nicht still überschrieben (siehe [needsTemplatePrompt]). */
    val TEMPLATE_NAMES: List<String> = listOf(WowTouch.STANDARD_PROFILE_NAME, WowTouch.FULL_PROFILE_NAME, WowTouch.PROFILE_NAME)

    /** Ein wählbares Layout: Profil-ID, voller Profilname und der Name zum Anzeigen. */
    data class Entry(val id: Int, val profileName: String, val display: String, val template: Boolean)

    enum class NameProblem { NONE, EMPTY, TAKEN }

    fun isLayoutName(profileName: String?): Boolean =
        profileName != null && profileName.length > PREFIX.length + SUFFIX.length &&
            profileName.startsWith(PREFIX) && profileName.endsWith(SUFFIX)

    /** "Touch (Urlaub)" -> "Urlaub"; ein Name ohne die Klammern bleibt wie er ist. */
    fun displayName(profileName: String): String =
        if (isLayoutName(profileName)) profileName.substring(PREFIX.length, profileName.length - SUFFIX.length) else profileName

    fun isTemplate(profileName: String?): Boolean = profileName != null && profileName in TEMPLATE_NAMES

    /** Was der Spieler tippt, bereinigt: Leerraum außen weg, innen zusammengezogen, höchstens [NAME_MAX] Zeichen. */
    fun clean(input: String): String = input.trim().replace(Regex("\\s+"), " ").take(NAME_MAX).trim()

    /** Profilname zur Eingabe [input] ("Urlaub" -> "Touch (Urlaub)"); null bei leerer Eingabe. */
    fun profileNameFor(input: String): String? {
        val name = clean(input)
        return if (name.isEmpty()) null else PREFIX + name + SUFFIX
    }

    /** Prüft die Eingabe gegen die vorhandenen Profilnamen (Groß-/Kleinschreibung zählt nicht). */
    fun nameProblem(input: String, existingProfileNames: Collection<String>): NameProblem {
        val wanted = profileNameFor(input) ?: return NameProblem.EMPTY
        return if (existingProfileNames.any { it.equals(wanted, ignoreCase = true) }) NameProblem.TAKEN else NameProblem.NONE
    }

    /** Die wählbaren Layouts aus (ID, Profilname)-Paaren: erst die Vorlagen und "Mein Layout" in fester Reihenfolge, dann die übrigen nach Name. */
    fun list(profiles: List<Pair<Int, String>>): List<Entry> {
        val fixed = TEMPLATE_NAMES + WowTouch.CUSTOM_PROFILE_NAME
        val all = profiles.filter { isLayoutName(it.second) }
            .distinctBy { it.second }
            .map { Entry(it.first, it.second, displayName(it.second), isTemplate(it.second)) }
        val first = fixed.mapNotNull { name -> all.firstOrNull { it.profileName == name } }
        val rest = all.filter { it.profileName !in fixed }.sortedBy { it.display.lowercase() }
        return first + rest
    }

    /** Der Modus, den die Wahl des Layouts [profileName] setzt: Vorlagen ihren eigenen, alles andere "Mein Layout". */
    fun modeFor(profileName: String): InputMode = when (profileName) {
        WowTouch.PROFILE_NAME -> InputMode.TOUCH
        WowTouch.FULL_PROFILE_NAME -> InputMode.TOUCH_FULL
        WowTouch.STANDARD_PROFILE_NAME -> InputMode.TOUCH_STANDARD
        else -> InputMode.TOUCH_CUSTOM
    }

    /** Der Name, der im Modus [mode] gilt: bei "Mein Layout" das gewählte eigene Layout, sonst der feste Name des Modus. */
    fun effectiveName(mode: InputMode, chosen: String?): String? =
        if (mode == InputMode.TOUCH_CUSTOM && isLayoutName(chosen)) chosen else mode.profileName

    /**
     * Der Text für den Modus im Launcher, wenn ein eigenes Layout gewählt ist: "Touch · Raid". Null = der gewohnte Text des Modus
     * (Vorlagen, kein gewähltes Layout, "Mein Layout" selbst).
     */
    fun customLabel(mode: InputMode, chosen: String?): String? {
        if (mode != InputMode.TOUCH_CUSTOM || !isLayoutName(chosen) || chosen == WowTouch.CUSTOM_PROFILE_NAME) return null
        return "Touch · " + displayName(chosen!!)
    }

    /**
     * Welches Profil der Modus [mode] benutzt, wenn das gewählte Layout [chosen] gilt: das gewählte, falls es das Profil noch gibt,
     * sonst das feste des Modus. Null, wenn auch das fehlt.
     */
    fun pickProfileId(profiles: List<Pair<Int, String>>, mode: InputMode, chosen: String?): Int? {
        val wanted = effectiveName(mode, chosen) ?: return null
        return WowTouchProfiles.findProfileId(profiles, wanted) ?: mode.profileName?.let { WowTouchProfiles.findProfileId(profiles, it) }
    }

    /**
     * Beim Verlassen des Editors: eine geänderte Vorlage wird nicht still behalten, der Spieler wählt (eigenes Layout, Vorlage
     * ändern, verwerfen). Eigene Layouts speichern ohne Rückfrage.
     */
    fun needsTemplatePrompt(profileName: String?, changed: Boolean): Boolean = changed && isTemplate(profileName)

    fun chosenName(context: Context): String? = wowPrefs(context).getString(KEY_CHOSEN, null)

    fun saveChosen(context: Context, profileName: String?) {
        val edit = wowPrefs(context).edit()
        if (profileName == null) edit.remove(KEY_CHOSEN) else edit.putString(KEY_CHOSEN, profileName)
        edit.apply()
    }

    /** Merkt [profileName] als gewähltes Layout und setzt den dazu passenden Modus. */
    fun choose(context: Context, profileName: String) {
        val mode = modeFor(profileName)
        InputMode.save(context, mode)
        saveChosen(context, if (mode == InputMode.TOUCH_CUSTOM) profileName else null)
    }
}

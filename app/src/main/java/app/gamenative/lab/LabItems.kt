package app.gamenative.lab

/** Die fünf Gruppen von „Erweitert“, in der Reihenfolge auf der Seite (nach Häufigkeit, die Fehlersuche steht oben). */
enum class LabGroup { TROUBLESHOOTING, SIGN_IN, GAME_FOLDER, TECH, DEVELOPMENT }

/** Wohin ein Bedienelement der Battle.net-Seite gehört: in die Ansicht „Einfach“, in eine Gruppe von „Erweitert“ oder es ist mit einem anderen zusammengelegt. */
enum class LabPlace { SIMPLE, TROUBLESHOOTING, SIGN_IN, GAME_FOLDER, TECH, DEVELOPMENT, MERGED }

/**
 * Jedes Bedienelement der Battle.net-Seite (die 67 Nummern aus Abschnitt 3.2 des Entwurfs `einfachentwurf_2026-11-15.md`; C4 ist in C4a und C4b geteilt)
 * mit seinem Platz. Reine Zuordnung, keine Oberfläche: `LabItemsTest` prüft, dass jede Nummer genau einmal vorkommt, keine Gruppe leer ist und
 * [LabPlace.SIMPLE] nur die im Entwurf genannten Elemente trägt. [mergedInto] nennt bei [LabPlace.MERGED] die Nummer, in der das Element aufgeht.
 */
enum class LabItem(val id: String, val place: LabPlace, val mergedInto: String? = null) {
    // Kopf und Assistent
    B0("B0", LabPlace.SIMPLE),
    B1("B1", LabPlace.MERGED, "G4"),
    G1("G1", LabPlace.SIMPLE),
    G2("G2", LabPlace.MERGED, "G1"),
    G3("G3", LabPlace.TROUBLESHOOTING),
    G4("G4", LabPlace.SIMPLE),
    G5("G5", LabPlace.SIMPLE),
    S1("S1", LabPlace.SIMPLE),
    S2("S2", LabPlace.SIMPLE),
    S3("S3", LabPlace.SIMPLE),
    S4("S4", LabPlace.SIMPLE),
    S5("S5", LabPlace.SIMPLE),
    S6("S6", LabPlace.SIMPLE),
    S7("S7", LabPlace.SIMPLE),

    // Karten der Hauptseite
    C1("C1", LabPlace.TROUBLESHOOTING),
    C2("C2", LabPlace.MERGED, "S3"),
    C3("C3", LabPlace.TECH),
    C4A("C4a", LabPlace.TECH),
    C4B("C4b", LabPlace.TROUBLESHOOTING),
    C5A("C5a", LabPlace.MERGED, "S2"),
    C5B("C5b", LabPlace.MERGED, "S2"),
    C5C("C5c", LabPlace.MERGED, "S4"),
    C5D("C5d", LabPlace.SIGN_IN),
    C5E("C5e", LabPlace.SIGN_IN),
    C5F("C5f", LabPlace.DEVELOPMENT),
    C5G("C5g", LabPlace.TROUBLESHOOTING),
    C5H("C5h", LabPlace.TROUBLESHOOTING),
    C5I("C5i", LabPlace.MERGED, "G5"),
    C6A("C6a", LabPlace.GAME_FOLDER),
    C6B("C6b", LabPlace.MERGED, "S5"),
    C6C("C6c", LabPlace.GAME_FOLDER),
    C6D("C6d", LabPlace.GAME_FOLDER),
    C7("C7", LabPlace.GAME_FOLDER),
    C8("C8", LabPlace.TROUBLESHOOTING),
    C9("C9", LabPlace.MERGED, "D1"),
    C10("C10", LabPlace.MERGED, "P1"),
    C11A("C11a", LabPlace.TROUBLESHOOTING),
    C11B("C11b", LabPlace.GAME_FOLDER),
    C11C("C11c", LabPlace.GAME_FOLDER),
    C11D("C11d", LabPlace.DEVELOPMENT),

    // Detailansicht „Einstellungen“
    D1("D1", LabPlace.TECH),
    D2("D2", LabPlace.TECH),
    D3("D3", LabPlace.TECH),
    D4("D4", LabPlace.TECH),
    D5("D5", LabPlace.TECH),
    D6("D6", LabPlace.TECH),
    D7("D7", LabPlace.DEVELOPMENT),
    D8("D8", LabPlace.DEVELOPMENT),
    D9("D9", LabPlace.DEVELOPMENT),
    D10("D10", LabPlace.DEVELOPMENT),
    D11("D11", LabPlace.DEVELOPMENT),
    D12("D12", LabPlace.DEVELOPMENT),
    D13("D13", LabPlace.DEVELOPMENT),
    D14("D14", LabPlace.DEVELOPMENT),
    D15("D15", LabPlace.DEVELOPMENT),
    D16("D16", LabPlace.DEVELOPMENT),
    D17("D17", LabPlace.DEVELOPMENT),
    D18("D18", LabPlace.DEVELOPMENT),
    D19("D19", LabPlace.DEVELOPMENT),
    D20("D20", LabPlace.DEVELOPMENT),
    D21("D21", LabPlace.TECH),
    D22("D22", LabPlace.TECH),
    D23("D23", LabPlace.TECH),

    // Detailansicht „Pakete“
    P1("P1", LabPlace.TECH),
    P2("P2", LabPlace.DEVELOPMENT),
    P3("P3", LabPlace.TECH),
    P4("P4", LabPlace.DEVELOPMENT),
    ;

    /** Die Gruppe von „Erweitert“, in der das Element steht; null bei „Einfach“ und bei zusammengelegten. */
    val group: LabGroup?
        get() = when (place) {
            LabPlace.TROUBLESHOOTING -> LabGroup.TROUBLESHOOTING
            LabPlace.SIGN_IN -> LabGroup.SIGN_IN
            LabPlace.GAME_FOLDER -> LabGroup.GAME_FOLDER
            LabPlace.TECH -> LabGroup.TECH
            LabPlace.DEVELOPMENT -> LabGroup.DEVELOPMENT
            LabPlace.SIMPLE, LabPlace.MERGED -> null
        }
}

object LabItems {
    /** Die Elemente der Ansicht „Einfach“ (Entwurf 3.1: Zustandssatz mit Warnzeile, Hauptknopf, Schrittliste, Statuszeile). */
    val simple: List<LabItem> get() = LabItem.entries.filter { it.place == LabPlace.SIMPLE }

    fun inGroup(group: LabGroup): List<LabItem> = LabItem.entries.filter { it.group == group }

    val merged: List<LabItem> get() = LabItem.entries.filter { it.place == LabPlace.MERGED }

    /** Die Elemente, die in „Erweitert“ stehen (alle Gruppen zusammen). */
    val advanced: List<LabItem> get() = LabItem.entries.filter { it.group != null }
}

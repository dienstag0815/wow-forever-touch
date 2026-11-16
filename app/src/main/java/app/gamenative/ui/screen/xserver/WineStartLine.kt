package app.gamenative.ui.screen.xserver

/**
 * Setzt den Programmteil der Wine-Startzeile zusammen (reine Funktion, ohne Android).
 *
 * `winhandler.exe` steht höchstens einmal vor dem Programm. Programme mit "ARM64" im Namen (das Spiel) starten ohne `winhandler.exe`.
 * Mit [direct] (nur das Battle.net-Labor, Vorgabe dort) startet jedes Programm so wie das ARM64-Programm des Spiels: ohne `winhandler.exe`,
 * ein Pfad mit Leerzeichen in Anführungszeichen. Für das Spiel ist [direct] immer `false`.
 * Bei [direct] und [absolute] steht vor jedem Leerzeichen im Pfad ein Rückstrich statt Anführungszeichen, weil
 * `ProcessHelper.splitCommand` Anführungszeichen als echte Zeichen im Argument stehen lässt, der Rückstrich dagegen als Leerzeichen zählt.
 */
object WineStartLine {
    private const val WINHANDLER = "winhandler.exe"
    private const val BACKSLASH = '\\'

    /**
     * [executablePath] ist bei [absolute] ein Windows-Pfad mit Laufwerk (`L:\Programm.exe`), sonst ein Pfad unterhalb von `A:\`
     * (mit `/` oder `\`).
     */
    fun compose(executablePath: String, absolute: Boolean, direct: Boolean = false): String {
        val withoutHandler = direct || executablePath.contains("ARM64", ignoreCase = true)
        return if (absolute) {
            if (withoutHandler) {
                when {
                    !executablePath.contains(" ") -> executablePath
                    direct -> executablePath.replace(" ", "$BACKSLASH ")
                    else -> "\"$executablePath\""
                }
            } else {
                "$WINHANDLER \"$executablePath\""
            }
        } else {
            val quoted = "\"A:$BACKSLASH${executablePath.replace('/', BACKSLASH)}\""
            if (withoutHandler) quoted else "$WINHANDLER $quoted"
        }
    }
}

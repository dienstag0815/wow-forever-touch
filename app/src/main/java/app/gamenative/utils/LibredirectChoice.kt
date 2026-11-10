package app.gamenative.utils

/**
 * Welche libredirect-Datei Wine per LD_PRELOAD bekommt, als reine Funktion (ohne Android-Klassen, damit sie testbar ist).
 *
 * Unter Android 17 (API 37) hängt Wine beim Start, wenn die volle Variante geladen ist (Futex-Deadlock, laut
 * Fork-Issue #9 von WoW Foreverywhere). Dort wird deshalb immer die kleinere Variante `libredirect-bionic-wx-minimal.so`
 * geladen, die nur die W^X-Anpassung enthält. Beide Dateien liegen als Asset im Flavor "modern" und werden von
 * `ImageFsInstaller.ensureBionicLib` nach `usr/lib` kopiert.
 */
object LibredirectChoice {
    /** Ab dieser API-Stufe (Android 17) wird die Minimal-Variante erzwungen. */
    const val MINIMAL_FROM_API = 37

    const val MINIMAL_SO = "libredirect-bionic-wx-minimal.so"

    /**
     * Regel ab API 37 AUS (Stand 08.11.): ein Fold 8 läuft mit Android 17 (One UI 9.0, belegt 08.11.) und WoW startet dort
     * mit der vollen Variante – eine erzwungene Umstellung könnte einen funktionierenden Stand brechen. Einschalten, sobald
     * am Gerät ein Hänger beim Start mit der vollen Variante belegt ist (oder Android-Version geklärt).
     */
    const val FORCE_ON_NEW_ANDROID = false

    /**
     * Dateiname der zu ladenden Variante oder null (nichts laden).
     * - Flavor ohne Modern: nur die Standard-Datei, bei abgeschaltetem libredirect nichts.
     * - Modern, libredirect abgeschaltet: Minimal-Variante (ohne sie läuft Wine auf strengem W^X nicht).
     * - Modern, ab API 37: Minimal-Variante.
     * - sonst: [defaultSo].
     */
    @JvmStatic
    @JvmOverloads
    fun pick(
        modernAndroid: Boolean,
        disabledByContainer: Boolean,
        sdkInt: Int,
        defaultSo: String,
        forceOnNewAndroid: Boolean = true,
    ): String? = when {
        disabledByContainer -> if (modernAndroid) MINIMAL_SO else null
        forceOnNewAndroid && modernAndroid && sdkInt >= MINIMAL_FROM_API -> MINIMAL_SO
        else -> defaultSo
    }
}

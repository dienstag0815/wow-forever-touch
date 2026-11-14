package app.gamenative.report

import java.io.File
import java.io.OutputStream

/** Kopiert die Zip aus dem Cache in die Datei, die der Spieler mit "Speichern unter" gewählt hat. Ohne Android, damit der Test sie prüfen kann. */
object ReportZipCopy {
    /** Schreibt die Bytes von [zip] in [out] und schließt beide Ströme. Gibt die Zahl der Bytes zurück; fehlt die Zip, wirft es. */
    fun copy(zip: File, out: OutputStream): Long = out.use { sink -> zip.inputStream().use { source -> source.copyTo(sink) } }
}

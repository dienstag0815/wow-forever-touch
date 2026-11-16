# Änderungen an WoW Forever Touch

Pro Version ein Abschnitt, neueste oben. Format: `## <Version> – <Datum>`, darunter `### Neu`, `### Verbessert`, `### Behoben` mit
Aufzählungspunkten in Alltagssprache. Der Launcher liest diese Datei (Kopie in `app/src/main/assets/CHANGELOG.de.md`) und zeigt sie als
Änderungsliste; bei einem angebotenen Update kommt der Text der neuen Versionen aus dem Update-Feed.

## 2.3.3-touch9.40 – 16.11.2026

### Verbessert
- Battle.net-Seite: Die Downloadliste verweist jetzt bei jedem Paket auf die Stelle, an der der Urheber es anbietet, in einer festen Version; die kurzen Hinweise neben den Paketen sind kürzer und verständlicher
- Projektseite: README und Lizenzhinweise beschreiben jetzt den experimentellen Battle.net-Weg und die Pakete, die er auf Wunsch lädt

## 2.3.3-touch9.39 – 15.11.2026

### Verbessert
- Battle.net-Seite, Ansicht „Einfach“, Schritt „Anmelden“: neue Reihenfolge in fünf Schritten für den Fall, dass die Anmeldung im Spiel abgelehnt wird (Battle.net-App auf Android, Browser, Battle.net-Programm mit E-Mail-Code, danach einmal „Installieren“ falls nötig, dann das Spiel einmal direkt starten)

## 2.3.3-touch9.38 – 15.11.2026

### Verbessert
- Start: Das Spiel startet jetzt standardmäßig direkt; melde dich einmal im Battle.net-Programm an, danach startet das Spiel direkt (wie früher, schnell, mit Autostart)
- Start: Die Wahl unter „Spielen“ heißt jetzt „Direkt starten“ und „Über Battle.net starten“ (kleiner Hinweis „Ausweichweg“); die Marke „empfohlen“ und die Warnung beim Wechsel auf „Direkt starten“ entfallen
- Start: „Über Battle.net starten“ lässt sich erst wählen, wenn Teil B der Anleitung eingerichtet ist; sonst öffnet sich die Anleitung bei Teil B und sagt, was fehlt
- Startseite: Solange die einmalige Anmeldung nicht als erledigt vermerkt ist, steht eine ruhige Tipp-Zeile: einmal bei Battle.net anmelden, falls die Anmeldung im Spiel abgelehnt wird
- Battle.net-Seite, Ansicht „Einfach“: Die Anleitung hat jetzt zwei Teile – „Teil A: Einmal anmelden“ (Schritte 1 bis 4, danach „Spiel direkt starten“) und, eingeklappt, „Teil B: Wenn der direkte Start abgelehnt wird“ (Schritte 5 bis 7: Kopie des Spiels, Spiel in Battle.net installieren, über Battle.net spielen; braucht rund 50 GB zusätzlich)
- Startseite: Der Knopf neben „Spielen“ heißt jetzt „Battle.net einrichten“

## 2.3.3-touch9.37 – 15.11.2026

### Verbessert
- Einstellungen: Die Seite beginnt jetzt mit „Einfach“ – Spiel (Region, Auflösung, Bildrate, Grafik, Spielordner) sowie Start und Updates (Autostart, Updates) sind immer sofort zu sehen
- Einstellungen: Alles Übrige steht hinter der neuen Zeile „Erweitert“ (Steuerung, Sprachsteuerung, Symbole, App, Hilfe, Spiel-Einstellungen im Spiel); sie ist bei jedem Öffnen der Seite zu
- Sprachsteuerung: „Umlaute als echte Zeichen“ und „Erkennungsprotokoll“ sind jetzt Kippschalter statt Knopf; deine gespeicherte Wahl bleibt, wie sie war

## 2.3.3-touch9.36 – 15.11.2026

### Behoben
- Absturz „The app has crashed“ behoben, der auftreten konnte, während ein Windows-Programm ein Fenster schloss (gleichzeitiger Zugriff auf gemeinsamen Bildspeicher)

## 2.3.3-touch9.35 – 15.11.2026

### Neu
- Startseite: Ein kleiner Knopf „Battle.net-Launcher“ direkt neben „Spielen“ öffnet die Battle.net-Seite, auf der du den Launcher einrichtest und startest; er ersetzt den Eintrag „Einrichten“ unter der Startwahl
- Battle.net-Seite: Neue Ansicht „Einfach“ mit einem Satz, was als Nächstes zu tun ist, einem Knopf „Weiter“, den sieben Schritten mit Haken (auf einen Schritt tippen zeigt seinen Knopf) und „Hilfe, es klappt nicht“
- Battle.net-Seite: Neue Zeile „Erweitert“ mit fünf Gruppen (Fehlersuche, Anmeldung, Spielordner und Platz, Technik, Entwicklung); sie ist bei jedem Öffnen der Seite zu, und ein Schalter „Erweitert immer geöffnet“ hält sie offen

### Verbessert
- Battle.net-Seite: Was vorher eine lange Reihe von Karten war, steht jetzt in den fünf Gruppen von „Erweitert“; jede Aktion gibt es weiterhin, nur Knöpfe, die dasselbe doppelt taten, wurden zusammengelegt (Installationsprogramm, Einstellung für die Anmeldung, Kopie des Spiels, Statuszeile)
- Battle.net-Seite: Alle Ja/Nein-Entscheidungen sind Schalter; „Alle Einstellungen zurücksetzen“ fragt jetzt vorher nach
- Battle.net-Seite: Alltagssprache in der Ansicht „Einfach“; Fachwörter stehen nur unter „Erweitert“, jeweils mit einer kurzen Erklärung

## 2.3.3-touch9.34 – 15.11.2026

### Neu
- Startseite: Unter „Spielen“ kannst du wählen, ob das Spiel „Über Battle.net“ (empfohlen) oder „Direkt“ startet; der Knopf zeigt in einer Unterzeile die gewählte Startart
- Wer den Weg über Battle.net schon eingerichtet hat, bekommt ihn vorgewählt; alle anderen starten wie bisher direkt, bis sie selbst wählen
- Fehlt für den Start über Battle.net noch etwas, öffnet „Spielen“ die Anleitung und nennt in einem Satz, was fehlt, statt zu starten
- Beim Wechsel auf „Direkt starten“ kommt eine Rückfrage: Der Anbieter lehnt die direkte Anmeldung auf manchen Geräten ab (Fehler BLZ51914400); erst nutzen, wenn der Start über Battle.net mindestens einmal geklappt hat
- Ist „Über Battle.net“ gewählt, startet beim Öffnen der App nichts von selbst; der Countdown gilt nur für den Direktstart
- Anleitung, Schritt „Anmelden“: Erfahrungswert zu Authenticator und Anmeldung mit dem E-Mail-Code in den ersten Läufen
- Der Laborbericht nennt die Startart und von wo der Start kam
- Startseite: Neben der Startwahl gibt es den Eintrag „Einrichten“, der die Battle.net-Seite öffnet
- Hilfe-Seite: Eine neue Zeile „Battle.net: Anleitung und Einstellungen“ öffnet die Battle.net-Seite direkt, ohne siebenmal auf die Versionszeile zu tippen

## 2.3.3-touch9.33 – 15.11.2026

### Neu
- Versuchsbereich: Neuer Schalter „Anmelde-Knöpfe“ (Vorgabe aus, beim Einschalten kommt eine Rückfrage) zeigt beim Start von Battle.net die Knöpfe „E-Mail“ und „Passwort“ in der Tastenleiste
- Versuchsbereich: Ein Tipp auf einen dieser Knöpfe tippt genau einen Wert der im Spielbereich gespeicherten Anmeldung in das Feld, das du vorher angetippt hast; es wird nichts mit Enter oder Tab abgeschickt und nichts wiederholt
- Versuchsbereich: Die Knöpfe sind ausgegraut, wenn keine Anmeldung gespeichert ist; ein Wert mit Zeichen, die sich nicht tippen lassen, wird gar nicht gesendet
- Versuchsbereich: Der Laborbericht vermerkt, ob die Knöpfe an waren, und jeden Tipp mit der Zahl der Zeichen, nie den Wert selbst

## 2.3.3-touch9.32 – 15.11.2026

### Neu
- Versuchsbereich: Schritt-für-Schritt-Anleitung oben auf der Seite, mit dem Zustand jedes Schritts und woran er abgelesen wird
- Versuchsbereich: Ein Knopf „Weiter“ führt den nächsten Schritt aus
- Versuchsbereich: „Einrichtung prüfen“ testet die Installation, ohne etwas zu starten; das Ergebnis steht im nächsten Laborbericht
- Versuchsbereich: Eine Zeile zum letzten Lauf (Dauer, wie er endete, ob Launcher- oder Spielfenster gesehen wurden)

## 2.3.3-touch9.31 – 15.11.2026

### Neu
- Versuchsbereich: Neuer Knopf „Anmeldeweg einrichten“ oben auf der Versuchsseite stellt gebündeltes Wine, FEX, Stufe 4 und „Spielkopie aus“ in einem Schritt ein; er braucht das FEX-Paket und lädt nichts von selbst
- Versuchsbereich: Kurze Hinweise zum Anmelden: nicht immer wieder versuchen, die App tippt nie deine Zugangsdaten, und was zu tun ist, wenn das erste Anmeldefenster falsch gezeichnet wird (als Erfahrungswert gekennzeichnet)
- Versuchsbereich: Zeigt der Launcher über eine Minute lang kein Fenster oder ein Fehlerfenster, erscheint in der Tastenleiste ein Hinweis mit „Launcher neu starten“; der Knopf beendet den Lauf sauber, danach bietet die Versuchsseite „Erneut starten“ an (nichts startet von selbst neu)
- Versuchsbereich: Der Laborbericht nennt den Anmeldeweg und listet die Zustandswechsel des Launchers und einen von dir gewünschten Neustart

### Verbessert
- Versuchsbereich: Klarere Texte, wenn ein Paket abgewiesen wird, ein Download scheitert oder ein Start nicht vorbereitet werden kann

## 2.3.3-touch9.30 – 15.11.2026

### Neu
- Versuchsbereich: Neuer Knopf „Spiel“ in der Tastenleiste schaltet die Spielsteuerung ein (Touch-Gesten und Bildschirmtasten wie im Spielbereich); sie ist anfangs aus, der Launcher wird also wie bisher bedient
- Versuchsbereich: Die Spielsteuerung schaltet sich von selbst ein, wenn das Spielfenster den Fokus hat, und wieder aus, wenn es weg ist
- Versuchsbereich: Vor einem Start mit der Spielkopie werden die Spieleinstellungen und das Addon in der Kopie eingerichtet (nie im Original-Spielordner)
- Versuchsbereich: Der Laborbericht nennt, ob die Spielsteuerung an war und was in der Kopie eingerichtet wurde

## 2.3.3-touch9.29 – 15.11.2026

### Verbessert
- Versuchsbereich: Die Spielkopie liegt jetzt in einem Ordner „World of Warcraft“ auf Laufwerk K:, damit der Launcher darüber installieren kann; eine vorhandene Kopie wird verschoben, nicht neu kopiert
- Versuchsbereich: Beim Auffrischen der Kopie bleiben Dateien erhalten, die der Launcher angelegt hat
- Versuchsbereich: Der Laborbericht listet die Dateien der Kopie und was sich in einem Lauf geändert hat

## 2.3.3-touch9.28 – 15.11.2026

### Neu
- Versuchsbereich: Kopie des Spielordners für den Versuchsbereich anlegen; der Originalordner wird nie eingebunden
- Versuchsbereich: Schalter „Spielkopie verwenden“ bindet die fertige Kopie als Laufwerk K: ein
- Versuchsbereich: Der Original-Spielordner wird vor und nach jedem Lauf geprüft; der Laborbericht weist aus, ob er unverändert ist

### Verbessert
- Versuchsbereich: Ein Start wird verweigert, wenn ein Laufwerk des Versuchsbereichs den Original-Spielordner enthalten würde

## 2.3.3-touch9.27 – 15.11.2026

### Verbessert
- Versuchsbereich: Der Labor-Bericht hält jetzt den Zustand des Update-Helfers des Launchers während eines Laufs fest
- Versuchsbereich: Der Labor-Bericht enthält mehr der eigenen Protokolle des Helfers
- Versuchsbereich: Fehlerfenster stehen im Fenster-Abschnitt des Labor-Berichts

## 2.3.3-touch9.26 – 15.11.2026

### Neu
- Versuchsbereich: Knopf „Fenster“ zeigt die offenen Fenster und holt eines nach vorn
- Versuchsbereich: Die Seite zeigt, mit welchem Wine-Paket und Übersetzer der nächste Start läuft
- Versuchsbereich: Stabilitätsstufen für den Übersetzer; FEX ist die empfohlene Wahl, sobald das Paket installiert ist
- Versuchsbereich: Absturzzähler und Liste der letzten Läufe

### Verbessert
- Versuchsbereich: Ein schon installiertes Paket wird als installiert gemeldet statt als Fehler
- Versuchsbereich: Hinweis zum Anmelden, wenn das erste Anmeldefenster unvollständig gezeichnet wird

## 2.3.3-touch9.25 – 15.11.2026

### Neu
- Versuchsbereich: Tastenleiste mit Esc, Tab, Enter, Rücktaste und Pfeiltasten
- Versuchsbereich: Knopf „Text“ schickt getippten Text als Tastendrücke an das Fenster; gespeichert wird nichts
- Versuchsbereich: Der Labor-Bericht zeigt, ob Tastendrücke im Fenster angekommen sind
- Versuchsbereich: wählbare Fenster-Einstellungen für den Tastaturfokus

## 2.3.3-touch9.24 – 15.11.2026

### Behoben
- Versuchsbereich: „Start Battle.net“ funktioniert nach der Installation (Programme in Ordnern mit Leerzeichen)

## 2.3.3-touch9.23 – 15.11.2026

### Verbessert
- Versuchsbereich: Programme starten jetzt direkt, auf demselben Weg wie das Spiel
- Versuchsbereich: neuer Stufentest prüft Laufwerk, Übersetzer und Installer in einem Lauf und legt das Ergebnis in den Labor-Bericht
- Versuchsbereich: Wine-Meldungen zu Fehlern, Programmstarts und geladenen Bibliotheken werden standardmäßig aufgezeichnet
- Versuchsbereich: gespeicherte Anmeldedaten werden nie in ein Fenster des Versuchsbereichs getippt
- Versuchsbereich: Nach einem Lauf kehrt die App zur Seite des Versuchsbereichs zurück

## 2.3.3-touch9.22 – 15.11.2026

### Behoben
- Versuchsbereich: Der Installer startet jetzt

## 2.3.3-touch9.21 – 15.11.2026

### Neu
- Versuchsbereich für einen neuen Anmeldeweg (versteckt, für Tester)

## 2.3.3-touch9.20 – 15.11.2026

### Behoben
- Der Fehlerbericht zeigt jetzt, wenn die Anmeldung nach dem Authenticator-Code abgelehnt wird

## 2.3.3-touch9.19 – 14.11.2026

### Behoben
- Meldungen von „Problem melden“ bleiben nach dem Auf-, Zuklappen und Drehen des Geräts in der App-Sprache.

## 2.3.3-touch9.18 – 14.11.2026

### Neu
- Hilfe › Problem melden: zwei weitere Knöpfe für die Zip, „In Downloads zeigen“ und „Speichern unter …“.

### Verbessert
- Problem melden: Die Meldungen folgen der in der App gewählten Sprache, und die Dialoge haben die Farben der App.

## 2.3.3-touch9.17 – 14.11.2026

### Verbessert
- Hilfe › Problem melden: Die App springt nicht mehr von selbst weg. Nach „Bericht erstellen“ zeigt ein Dialog „Bericht bereit“ die drei Schritte (Zip in Downloads, Meldeseite öffnen, Kurzfassung einfügen und Zip anhängen) mit den Knöpfen Im Browser öffnen, In der App öffnen und Zip teilen. Die Karte auf der Hilfeseite bleibt.
- Der Titel einer Meldung ist immer englisch.

## 2.3.3-touch9.16 – 14.11.2026

### Neu
- Hilfe › Problem melden: Eine Karte „Bericht bereit“ bleibt auf der Hilfeseite stehen (Dateiname, „liegt in Downloads“, Uhrzeit), bis du sie schließt oder einen neuen Bericht machst. Knöpfe: Meldeseite öffnen, Im Browser öffnen, Zip teilen, Kurzfassung kopieren.
- Der Dialog sagt jetzt, was zu tun ist: Kurzfassung aus der Zwischenablage einfügen, falls das Textfeld leer ist, und die Zip aus Downloads anhängen, oder „Im Browser öffnen“ nehmen, wenn die App keine Datei anhängen kann.
- Bericht: `anmeldung.txt` zeigt mehr unkritische Anmeldefelder (Plattform, Sprache, Region, SSO), den Schritt, an dem eine Anmeldung scheiterte, und das Realm-Ergebnis.

### Verbessert
- Bericht: Netzwerkadressen (IPv4 und IPv6) sind in jeder Datei des Berichts geschwärzt.
- Bericht: Ein älterer Spielordner nach einer Neuinstallation zählt nicht mehr als „Spuren“; der Bericht nennt beide Daten und den Unterschied.

## 2.3.3-touch9.15 – 14.11.2026

### Neu
- Hilfe › Problem melden: Ein Tipp erstellt den Bericht, legt die Zip in Downloads, kopiert eine Kurzfassung und öffnet im Browser eine neue Meldung (es wird nichts automatisch gesendet; die Seite ist öffentlich, die Zip hängst du selbst an).
- Vier freiwillige Fragen in diesem Dialog (ging die Anmeldung schon einmal, andere App vorher genutzt, Authenticator, woanders angemeldet); die Antworten stehen in der Kurzfassung des Berichts.
- Der Fehlerbericht enthält jetzt eine Kurzfassung (`kurz.txt`), die letzten Anmeldeversuche aus der `Connection.log` des Spiels (`anmeldung.txt`) und ein kleines Journal der letzten Spielstarts (`anmeldejournal.txt`: Anmeldestufe, Netzart, Art des Endes, Versuche je Start; nie E-Mail, Passwort oder Adressen).
- Der Fehlerbericht zeigt außerdem eine Zeitleiste der Installation und ob andere Start-Apps für dieses Spiel installiert sind (nur ja/nein).

## 2.3.3-touch9.14 – 12.11.2026

### Neu
- Neue Anmeldestufe „Ausfüllen“ (Spieldateien › Automatische Anmeldung): Die App tippt E-Mail und Passwort ins Anmeldefenster, du drückst selbst auf Login. Dazu ein Knopf „Anmeldedaten ausfüllen“ im Anmeldebildschirm und die Zeile „Ausfüllen, nicht senden“ im Seitenmenü. Wahl „Was eintippen“: E-Mail und Passwort, oder nur das Passwort (für „Remember Account“).
- Die Stufen sind jetzt Aus, Ausfüllen und Selbst anmelden (wie bisher über `login.txt`; dabei kann Battle.net bei jeder Anmeldung den Authenticator verlangen). Wer schon Daten gespeichert hat, bleibt bei „Selbst anmelden“; neu gespeicherte Daten starten in „Ausfüllen“.
- Der Wechsel auf „Aus“ fragt nach, bevor die gespeicherte Anmeldung gelöscht wird.

## 2.3.3-touch9.13 – 12.11.2026

### Neu
- Die Windows-Kennung bleibt jetzt auch nach einer Neuinstallation der App erhalten: Sie wird als Kopie im Spielordner gesichert und beim ersten Start wieder übernommen (nur vom selben Gerätemodell; eine Kopie von einem anderen Modell steht unter „Frühere Kennungen“). In Hilfe und Diagnose lässt sich die Sicherung ausschalten; dann wird die Kopie gelöscht.

### Verbessert
- Die Windows-Kennung übersteht App-Updates abgesichert: Eine beschädigte Kennungsdatei wird feldweise repariert statt ersetzt, eine Zweitkopie stellt sie bei Verlust wieder her, und das Speichern ist gegen Abbrüche geschützt. Hilfe und Diagnose zeigt je Update „Kennung unverändert“ oder eine Warnung und misst zusätzlich Werte, die Wine selbst im Prefix erzeugt (nur Kurzkennungen, nie die Werte).

## 2.3.3-touch9.12 – 12.11.2026

### Neu
- Hilfe & Diagnose zeigt die Windows-Kennung, die das Spiel meldet, als kurzen Fingerabdruck, mit einem Verlauf jeder Änderung (Neuinstallation, Speichern fehlgeschlagen, abweichende Werte im Windows-Teil).
- Die Werte der Kennung lassen sich ansehen. Nach einer Rückfrage mit Erklärung der Folgen kann eine neue Kennung erzeugt werden (höchstens einmal in 24 Stunden), und bis zu fünf frühere Kennungen lassen sich wieder verwenden.
- Bei jedem Spielstart wird eine „Systemsicht“ festgehalten (Netzart, Netzwerkschnittstellen, Modus des privaten DNS, Prozessorkerne, Art des Spielordners), nur als Fingerabdrücke. So ist sichtbar, was sich zwischen zwei Starts ändert.

### Behoben
- Konnte die Kennung nicht gespeichert werden, entstand bei jedem Start unbemerkt eine neue. Jetzt bleibt sie gleich, solange die App läuft, und eine Warnung erscheint.

## 2.3.3-touch9.11 – 12.11.2026

### Neu
- Spieler-Menü: Ist der Spieler in deiner Gruppe, steht statt „Einladen“ der Eintrag „Gruppe verlassen“.

### Verbessert
- Spracheingabe: Ist der Spracherkennungsdienst nicht erreichbar, versucht es die App nach kurzer Pause bis zu zweimal neu, bevor sie die Meldung zeigt.

## 2.3.3-touch9.10 – 12.11.2026

### Verbessert
- Der Chat-Filter hat jetzt drei Stufen (Aus, Wenig, Viel). Der neue Standard „Wenig“ blendet nur Rauschen aus (Erfahrung, Ruf, Berufe, Geld, Handel, Dienste); System-, Beute- und Erfolgsmeldungen sowie Allgemein und Gruppensuche bleiben sichtbar. „Viel“ ist das bisherige Verhalten. Wer den Filter an hatte, steht nach dem Update auf „Wenig“.

## 2.3.3-touch9.9 – 12.11.2026

### Behoben
- „Einladen“ im Spieler-Menü beendete das Spiel: Die Taste dafür enthielt Alt+F4. Einladen und das Raid-Fenster nutzen jetzt andere Tasten, keine Taste der App enthält mehr Alt+F4.

## 2.3.3-touch9.8 – 11.11.2026

### Neu
- Finger auf einer Quest im Questlog oder im Quest-Tracker halten: Das Menü bietet „Im Chat verlinken“, „Mit Gruppe teilen“, „Verfolgen / Nicht verfolgen“ und „Mehr ...“ (das Quest-Menü des Spiels).

### Behoben
- Die Spracheingabe versucht es bei nicht erreichbarem Spracherkennungsdienst einmal selbst noch einmal; klappt es wieder nicht, steht die Fehlernummer am Ende der Meldung.

## 2.3.3-touch9.7 – 11.11.2026

### Neu
- Der Wechsel zwischen Touch-Layouts schaltet den Touchscreen-Schalter nicht mehr wieder ein und setzt deine Gesten nicht zurück.
- Der Launcher zeigt den Namen deines gewählten Layouts (z. B. „Touch · Raid“) statt „Touch (Mein Layout)“.
- Die deutsche README erklärt den Launcher jetzt oben auf Deutsch.

## 2.3.3-touch9.6 – 11.11.2026

### Neu
- Tester können eine ältere Version wählen: Einstellungen → Start und Updates → Update-Quelle → Tester → „Versionen anzeigen“ listet alle Releases (neueste oben, installierte markiert). Eine neuere Version ist ein normales Update. Bei einer älteren speichert die App ein Backup und die alte App-Datei in Downloads und bittet dann um das Deinstallieren; danach installierst du die Datei, und die App schlägt den Import des Backups vor.
- Update ohne Datei- und Installationsrechte für die App: „Im Browser herunterladen“ öffnet die APK (bei Tester- und privaten Quellen die Release-Seite, auf der du dich anmeldest). Die APK landet in Downloads; antippen zum Installieren, Android fragt dann selbst nach. Das Installationsrecht fragt die App erst beim Tippen auf „Installieren“ ab.

## 2.3.3-touch9.5 – 11.11.2026

### Verbessert
- Einstellungen: Jede Aktion ist jetzt ein echter Knopf (Gold = Hauptaktion, umrandet = weitere, rot umrandet = gefährlich); änderbare Werte sehen aus wie Eingabefelder, reine Werte wie die Version bleiben ruhiger Text.
- Einstellungen: Aufklappbare Bereiche haben einen Pfeil und stehen in einer helleren, eingerückten Fläche; jede Gruppe ist eine Karte mit eigener Überschrift und mehr Abstand.
- Update-Quelle: Offiziell, Tester (Code) oder Eigener Server auf drei Karten wählen; die aktive Quelle steht als Chip oben. Im Tester-Modus gibt es nur den Status mit „Verbindung prüfen“ und „Tester-Modus beenden“.

## 2.3.3-touch9.4 – 11.11.2026

### Neu
- Standardlayout mit allem an seinem Platz: obere Reihe ESC, Tastatur, Menü, Buch zentriert am Rand; Zielrahmen neben dem Spieler; Questtracker rechts direkt unter der Karte; Tooltip unten links.
- Tasten im WoW-Stil (dunkle Fläche, Bronzekante, Serifenschrift) sind jetzt Standard, auch nach Neuinstallation und Backup-Import.
- In den Einstellungen unter „Start und Updates“ steht die Zeile „Update-Quelle“ (Offiziell, Eigene oder Tester mit Server).

### Verbessert
- Die Battle.net-Anmeldung kommt beim Start nicht mehr. Sie ist nur noch freiwillig unter Einstellungen > Spieldateien > „Automatische Anmeldung (optional)“; die Daten bleiben verschlüsselt nur auf dem Gerät.

### Behoben
- Die Sprachtaste zeigte „Spracherkennung Fehler 13“. Fehlt das Sprachpaket, versucht die App es online, stößt das Laden an und zeigt eine verständliche Meldung.

## 2.3.3-touch9.3 – 11.11.2026

### Neu
- Standardlayout: Beim ersten Start ist „Touch (Standard)“ aktiv – zwei Sticks mit Tastenring, ESC, Tastatur und Menü oben. Du kannst stattdessen ein eigenes Layout erstellen.
- Das Addon kann die WoW-Oberfläche passend zum Standardlayout anordnen (Layout „Touch Forever“, umschaltbar mit /tf layout).
- Im Seitenmenü wählst du im Reiter Steuerung oben das Layout und speicherst das aktuelle als neues Layout. Ändert man im Editor eine Vorlage, fragt die App, ob die Änderungen als eigenes Layout gesichert werden sollen. Selten genutzte Einträge liegen unter „Mehr“.
- Das Backup sichert jetzt auch Touchscreen-Schalter, Gesten, Maus-aus, Gyro, Bildeffekte, FPS-Grenze, die Leistungsanzeige und das gewählte eigene Layout.
- Lehnt Android ein Update wegen einer anderen Signatur ab, erklärt die Update-Karte jetzt, dass die App neu installiert werden muss.
- Einstellungen → Updates → „Update-Quelle“. Tester können Updates aus einem eigenen Gitea- oder GitHub-Repo holen (auch privat, mit Zugangs-Token), Vorabversionen einschließen und die Verbindung prüfen; „Zurück zu offiziell“ stellt den Standard wieder her.

### Verbessert
- ESC, Tastatur und Menü stehen in „Touch (WoW komplett)“ jetzt oben statt unten links.
- Sicherungen heißen jetzt mit Datum, Uhrzeit auf die Sekunde und einer kurzen Gerätekennung, zum Beispiel `wow-forever-touch-backup-2026-11-11_14-05-09_a1b2c3.zip`. Alte Sicherungen lassen sich weiter einlesen.
- Das Standardlayout „Touch (Standard)“ ist jetzt das am Gerät gebaute Wunschlayout; die WoW-Fenster (Aktionsleisten, Chat, Rahmen, Questverfolgung, Mikromenü, Taschen) sitzen dazu passend.

### Behoben
- Editor: Joysticks und ihre Tastenringe werden auch bei eingeschaltetem Shooter-Modus angezeigt und lassen sich bearbeiten.
- Nach einer zweiten Installation über die erste bekommen neue Spieler trotzdem das Touch-Layout mit ESC, Tastatur und Menü.
- Die Tastatur-Taste schließt die Tastatur wieder, wenn sie offen ist. Ringfelder mit WoW-Aktion ohne Taste in WoW (z. B. Aktionsleisten 2 bis 8) erscheinen jetzt abgeblendet statt zu fehlen.
- Aktionsleisten 2 bis 5 funktionieren auf Touch-Knöpfen und Ringen auch ohne eigene WoW-Tastenbelegung: das Addon legt feste Ersatztasten darauf, solange du keine eigene Taste setzt.
- Nach Neuinstallation und Backup-Import sieht der Spielbildschirm wieder aus wie vorher; der Shooter-Modus ist für WoW standardmäßig aus (keine ausgeblendeten Sticks, kein Knopf „Maus aktivieren“ oben).
- Shooter-Knopf bei WoW ausgeblendet

## 2.3.3-touch9.2 – 10.11.2026

### Neu
- Absturzanzeige: Stürzt die App ab, zeigt sie den Fehlerbericht auf dem Bildschirm mit „Kopieren“ und „Teilen“, damit du ihn uns schicken kannst.

### Verbessert
- Neuer Name „WoW Forever Touch – Launcher“ und neues App-Icon: das WoW-Forever-Emblem mit Panzerhandschuh und „Touch“-Schriftzug.

### Behoben
- Nach einer Neuinstallation oder dem Wechsel von einem anderen Launcher schloss sich die App beim ersten Start sofort wieder, wenn der Spielordner schon da war.

## 2.3.3-touch9.1 – 10.11.2026

### Neu
- Backup: Unter Einstellungen › App › Backup sicherst du deine Layouts und Einstellungen in eine Datei und stellst sie wieder her – zum Beispiel vor einer Neuinstallation. Anmeldedaten und Gerätekennung kommen bewusst nicht mit.

## 2.3.3-touch9.0 – 10.11.2026

### Neu
- Neuer Name: WoW Forever Touch – läuft auf dem Galaxy Fold genauso wie auf anderen Tablets und Handys.
- Eigenes App-Icon (Panzerhandschuh, der tippt), passende Benachrichtigung, Banner und Hintergrundbild.
- Updates kommen jetzt aus den GitHub-Releases des Projekts.
- Die App ist mit einem neuen Schlüssel signiert. Einmal deinstallieren und neu installieren; danach laufen Updates wieder von selbst.

## 2.3.3-fold8.34 – 11.11.2026

### Neu
- Standardlayout: Beim ersten Start ist „Touch (Standard)“ aktiv – zwei Sticks mit Tastenring, ESC, Tastatur und Menü oben. Du kannst stattdessen ein eigenes Layout erstellen.
- Das Addon kann die WoW-Oberfläche passend zum Standardlayout anordnen (Layout „Touch Forever“, umschaltbar mit /tf layout).

### Verbessert
- ESC, Tastatur und Menü stehen in „Touch (WoW komplett)“ jetzt oben statt unten links.

### Behoben
- Editor: Joysticks und ihre Tastenringe werden auch bei eingeschaltetem Shooter-Modus angezeigt und lassen sich bearbeiten.
- Nach einer zweiten Installation über die erste bekommen neue Spieler trotzdem das Touch-Layout mit ESC, Tastatur und Menü.

## 2.3.3-fold8.33 – 10.11.2026

### Behoben
- Nach einer Neuinstallation schloss sich die App beim ersten Start sofort wieder, wenn der Spielordner der alten Installation noch da war.

## 2.3.3-fold8.32 – 10.11.2026

### Neu
- Absturzanzeige: Stürzt die App ab, zeigt sie den Fehlerbericht auf dem Bildschirm mit „Kopieren“ und „Teilen“, damit du ihn uns schicken kannst.

### Verbessert
- Neues App-Icon: das WoW-Forever-Emblem mit Panzerhandschuh und „Touch“-Schriftzug, heller und auch auf dem Startbildschirm gut erkennbar.

### Behoben
- Die Grafikkarten-Erkennung beim ersten Start kann die App nicht mehr beenden.

## 2.3.3-fold8.31 – 10.11.2026

### Neu
- Backup: Unter Einstellungen › App › Backup sicherst du deine Layouts und Einstellungen in eine Datei und stellst sie wieder her – zum Beispiel vor einer Neuinstallation. Anmeldedaten und Gerätekennung kommen bewusst nicht mit.

## 2.3.3-fold8.30 – 10.11.2026

### Verbessert
- Der Editor zeigt die Namen der WoW-Aktionen, Menüs und Standardknöpfe jetzt in der App-Sprache (Tastenauswahl, „Aktuell“, Kreismenü-Vorlagen, Knopftexte). Gespeicherte Layouts bleiben unverändert, selbst getippte Namen auch.
- Auf Englisch heißt der Knopf „Tastatur“ jetzt „Keys“ und der Knopf „WoW-Menü“ „Panels“, damit nichts abgeschnitten wird.

## 2.3.3-fold8.29 – 10.11.2026

### Verbessert
- Ruhigere Farben im ganzen Launcher: Altgold statt grellem Gelbgold für Titel, Linien und das Abzeichen „Neu“, gedämpftes Weinrot statt Signalrot für den Knopf „Spielen“, weniger Schein und weichere Schatten. Alle Texte bleiben gut lesbar.

## 2.3.3-fold8.28 – 10.11.2026

### Verbessert
- Einstellungen neu geordnet, nach Bedeutung statt nach Platz: Spiel, Start und Updates, Steuerung im Spiel, App, Hilfe. Zeilen, die auf eine andere Seite springen, heißen jetzt „Öffnen ›“; der Fehlerbericht startet direkt; Version und Client-Build stehen als leise Fußzeile unter „Start und Updates“. „Addons verwalten“ ist entfallen – dafür gibt es die Kachel „Addons“ auf der Startseite.

## 2.3.3-fold8.27 – 11.11.2026

### Neu
- Die App läuft jetzt auf Englisch – Standard auf jedem Handy. Deutsch wählst du in den Einstellungen unter „Weiteres › Language / Sprache“; die Wahl gilt sofort und bleibt gespeichert.
- Auch das Addon im Spiel (TouchForever) spricht Englisch oder Deutsch, passend zur Sprache der App – unabhängig von der Sprache des Spielclients. Addon-Stand 8.
- Launcher, Seitenmenü, Startcheck, Dialoge, Sprachsteuerung, Abklingzeiten und der Layout-Editor sind vollständig übersetzt.

### Verbessert
- Neuigkeiten: Die neueste Version nutzt die freie Höhe der Karte für ganze Punkte, statt sie nach drei Zeilen abzuschneiden; kleine Karten bleiben klein.
- Der Pfeil rechts oben an den Karten ist jetzt ein echtes Symbol.
- Einstellungen: Karten neu angeordnet, hochkant ohne Scrollen. Die bisherige Zeile „Sprache“ für die Spracheingabe heißt jetzt „Sprachsteuerung“.
- Datenschutz: Cloud-Backup und Geräteübertragung der App-Daten sind ausgeschlossen; der Fehlerbericht schwärzt zusätzlich den Kontoordner und die eigene Geräte-Kennung; die Spracheingabe bevorzugt die Erkennung auf dem Gerät.

### Behoben
- Bei arabischer, persischer, Hindi- oder Thai-Systemsprache wurde die Windows-Kennung des Spiels bei jedem Start neu erzeugt. Sie bleibt jetzt stabil.
- Der Untertitel der Kachel „Steuerung“ bricht auf dem Außendisplay nicht mehr in zwei Zeilen um.

## 2.3.3-fold8.26 – 11.11.2026

### Neu
- Auf der Startseite gibt es jetzt einen Knopf „Nach Updates suchen“. Du musst die App dafür nicht neu starten und kein Menü öffnen. Aufgeklappt sitzt er rechts oben neben der Statusanzeige, zugeklappt als Symbol unten rechts in der Fußzeile.
- Der Knopf zeigt, was los ist: „Suche ...“ (solange gesucht wird, nicht doppelt auslösbar), „Aktuell (Version ...)“, „Update ... installieren“ oder bei Problemen „Keine Verbindung“ mit einem Tipp zum erneuten Versuch. Ergebnisse bleiben ein paar Sekunden stehen, dann zeigt der Knopf wieder „Nach Updates suchen“. Liegt schon ein Update bereit, steht das sofort am Knopf, und ein Tipp installiert es.

### Verbessert
- Die Seiten hinter den Kacheln (Einstellungen, Steuerung, Addons, Spieldateien, Neuigkeiten, Hilfe und Über) sind jetzt Übersichten aus Karten, die auf dem aufgeklappten Fold den ganzen Bildschirm nutzen und ohne Scrollen auskommen: quer in drei Spalten, hochkant in zwei. Jede Karte zeigt nur das Wichtigste, zum Beispiel Region, Auflösung und Bildrate gleich zum Umschalten, oder bei den Addons Zustand, Installieren und den Schalter „Aktiv“.
- Alles Weitere öffnet sich mit einem Tipp auf die Karte (Pfeil rechts oben) in einer Detailansicht über die ganze Seite, in der man auch scrollen darf: Beschreibungen und Abzeichen der Einstellungen, die Liste der eigenen Addons, die ganzen Änderungslisten, die Lizenztexte. Die Zurück-Taste oder -Geste schließt zuerst die Detailansicht, dann die Seite. Es ist keine Funktion weggefallen.
- Auf dem Außendisplay stehen die Karten in einer Spalte untereinander, wie vorher mit Scrollen.
- Auf der Seite „Neuigkeiten“ steht die neueste Version groß mit Neu, Verbessert und Behoben nebeneinander, daneben die zwei Vorgänger kurz; „Alle Änderungen“ öffnet die ganze Liste.

### Behoben
- Die Minikarte sitzt jetzt wirklich in der oberen rechten Ecke. Bisher blieb sie auf dem Fold etwa 55 bis 65 Einheiten vom Rand weg, weil der automatische Schub bei so großem Abstand gar nicht erst ansetzte. Steht die Karte noch an der Standardposition des Spiels, wird sie immer in die Ecke gerückt (2 Einheiten Abstand), egal wie groß der Abstand vorher war. Eine eigene Position aus dem HUD-Bearbeitungsmodus bleibt unberührt.
- Die eckige Karte füllt den gelben Rahmen des HUD-Bearbeitungsmodus jetzt oben und rechts ohne Leerraum aus. Ziehst du den Rahmen in die Ecke, sitzt auch die Karte in der Ecke. Leerraum bleibt nur links und unten im Rahmen. Speichern und Neuladen ändern daran nichts.
- Unter „Minikarten-Aufbau anzeigen“ (oder im Chat mit „/tf minimap info“) steht jetzt, ob und warum die Karte geschoben wurde und wie weit sie danach vom Rand entfernt ist.

## 2.3.3-fold8.25 – 11.11.2026

### Neu
- Auf der Seite „Addons“ gibt es jetzt den Bereich „Eigene Addons“. Du kannst ein eigenes Addon per Link einbauen lassen (GitHub-Seite, WoWInterface-Seite oder ein direkter .zip-Link) oder eine heruntergeladene .zip-Datei wählen („Aus Datei“). Aus der Zwischenablage einfügen geht mit einem Tipp.
- Vor dem Einbau zeigt der Launcher, was im Paket steckt: die gefundenen Addon-Ordner mit Titel, Version und Interface-Nummer. Passt die Interface-Nummer nicht zu WoW Forever, steht dort „Evtl. für eine andere WoW-Version“; einbauen kannst du es trotzdem. Erst nach „Einbauen“ wird etwas geschrieben.
- Existiert der Ordner schon, fragt der Launcher, ob er ihn ersetzen soll, und hebt den alten Stand als „<Ordner>_bak“ auf. Die geprüften Addons (Questie & Co.) und unser TouchForever werden nie überschrieben.
- Jedes eigene Addon steht in einer Liste mit Version und Quelle, lässt sich ein- und ausschalten und entfernen. Bei GitHub-Quellen gibt es „Auf Update prüfen“ und „Aktualisieren“. Änderungen wirken ab dem nächsten Spielstart.
- CurseForge und Wago erlauben kein direktes Laden. Fügst du einen solchen Link ein, erklärt der Launcher das, öffnet auf Wunsch die Seite im Browser, und du wählst danach die heruntergeladene .zip über „Aus Datei“.

### Verbessert
- Die Startseite ist besser lesbar: Titel und Zeilen von Hero und Kacheln stehen jetzt auf einem dunklen, nach oben auslaufenden Balken am unteren Bildrand, so bleiben sie auf jedem Bild gut zu lesen. Die Titel sind deutlich größer und kräftiger (Hero 40 bis 48, Kacheln 20 bis 23), die Zeilen darunter heller und größer (15 bis 16), nichts ist mehr kleiner als 13.
- Das große Hauptbild ist zentriert: Es füllt den Platz und schneidet so zu, dass die Figur in der Mitte bleibt, statt am Rand abgeschnitten zu werden. Das gilt für die fünf WoW-Bilder und die mitgelieferten Bilder, auch zugeklappt.
- Die Startseite ist ruhiger: Statt verzierter Doppelrahmen hat jede Kachel eine dünne, dezente Goldlinie mit runden Ecken, die Kartenbilder sind etwas abgedunkelt und entsättigt, die Abstände zwischen den Kacheln sind etwas größer.
- Kopf und Fußzeile sind lesbarer (Unterzeile und Fußzeile mindestens 13 bis 14, heller). Auf den Seiten Einstellungen, Hilfe, Neuigkeiten, Über und Addons sind Zeilentitel, Beschreibungen und Abzeichen größer und heller, ohne dass sich das Layout ändert.

## 2.3.3-fold8.24 – 11.11.2026

### Neu
- Im Editor hat jeder Stick (und jedes Trackpad) auf der Seite „Verhalten“ jetzt einen Regler „Empfindlichkeit“. Er zeigt den Wert, der für genau diesen Stick gilt (Laufen oder Kamera, je nach Belegung), und wirkt sofort für alle Sticks dieser Art. Bei Tasten- oder Maus-Belegung ist er ausgegraut und sagt, dass er nur bei Pad-Stick-Belegung wirkt. Derselbe Wert steht auch im Seitenmenü › Steuerung › Sticks.
- Das Seitenmenü im Spiel ist aufgeräumt: Der Reiter „Steuerung“ hat jetzt klappbare Gruppen – Layout (immer offen), Sticks, Tippen und Halten, Klick-Rad, Wischen, Touch-Elemente und Weitere Eingabemodi. Es ist immer nur eine Gruppe offen, die zuletzt offene merkt sich das Menü; zugeklappt zeigt jede Gruppe in einer Zeile ihre Werte, zum Beispiel „Laufen 1,25 · Kamera 1,0“.
- Alles zur Steuerung lässt sich jetzt im Spiel einstellen und wirkt sofort: 2-Finger-Rechtsklick, Klick-Rad an/aus, Haltezeit, Doppeltipp-Fenster, die fünf Richtungen des Klick-Rads (Antippen öffnet eine Auswahlliste), „Wischen scrollt Listen“ samt Geschwindigkeit und „Layout auf Standard zurücksetzen“ (mit Rückfrage).
- Im Reiter „Spiel“ gibt es unter „Symbole und Abklingzeiten“ den Schalter „Auch globale Abklingzeit“.

### Verbessert
- Im Addon (/tf, Seite „Touch“) gibt es den Schalter „Wischen in Listen scrollt“ nicht mehr. Stattdessen steht dort ein Hinweis, dass du Wischen und Touch im Seitenmenü › Steuerung einstellst. Das Addon meldet scrollbare Listen jetzt immer; ob ein Wisch scrollt, bestimmt nur die App. Ein früher im Addon ausgeschalteter Schalter schaltet das Wischen nicht mehr heimlich ab.
- Der Launcher ist aufgeräumter: Was es auch im Spiel gibt, steht nicht mehr doppelt in den Einstellungen. Die Kachel heißt jetzt „Steuerung“ und zeigt den Steuerungsmodus plus einen Hinweis, wo du die Feinheiten im Spiel einstellst (Seitenmenü › Steuerung).
- Wer die Spiel-Einstellungen trotzdem im Launcher ändern will, findet ganz unten auf der Seite „Steuerung“ die Zeile „Spiel-Einstellungen hier ändern …“. Nach einer Rückfrage klappen Sticks, Ausblenden, Aussehen, Klick-Rad, Layout zurücksetzen, Erkennungssprache und Abklingzeiten auf.
- Die Zeilen im Seitenmenü sind kompakt: kurzer Titel links, Wert rechts, Werte mit „-“ und „+“ statt „Titel: Wert“ in zwei Zeilen. Auswahlen wie „Kontextmenü beim Halten“ oder „Aussehen“ sind Knöpfe nebeneinander.
- Ein im Spiel gesetzter Wert (Haltezeit, Doppeltipp, Klick-Rad, 2-Finger-Rechtsklick) bleibt beim nächsten Start erhalten; auch das Zahnrad „Gesten“ und der Launcher zeigen denselben Wert.
- Das Seitenmenü liest seine Werte bei jedem Öffnen frisch ein, zum Beispiel die Stick-Empfindlichkeit, wenn sie im Touch-Editor geändert wurde.

### Behoben
- Die offiziellen WoW-Bilder auf der Startseite zeigen keinen weißen, gezackten Rand mehr.

## 2.3.3-fold8.23 – 11.11.2026

### Neu
- Der Launcher bringt jetzt eigene Bilder mit (Fantasy-Malerei, Burgen, Gebirge, Höhlen aus freien Quellen): sechs große Bilder für die Startseite, die bei jedem Start wechseln, und je eins für die Kacheln. So sieht er auch ohne Netz gut aus. Wer sie gemalt oder fotografiert hat, steht unter „Über“ bei „Bilder“.

## 2.3.3-fold8.22 – 11.11.2026

### Neu
- Die Startseite des Launchers passt jetzt ohne Scrollen auf den Schirm, aufgeklappt wie zugeklappt. Kopf, „Spielen“, Kacheln und Fußzeile teilen sich die Höhe; nur auf sehr niedrigen Fenstern scrollt die Seite noch.
- Überschriften, Wortmarke, Kacheltitel und die „Spielen“-Taste stehen jetzt in der Schrift Cinzel (SIL Open Font License 1.1, der Lizenztext liegt unter „Über“).
- Der Launcher zeigt Bilder: der neueste eigene Screenshot aus dem Ordner Screenshots deines Spiels steht groß auf der Startseite. Gibt es keins, zeigt er offizielles WoW-Artwork, das er einmal aus dem Netz in den App-Speicher lädt (bei jedem Start ein anderes der fünf Bilder), und auf den Kacheln Zonenkarten. Ohne Netz bleibt die gezeichnete Kunst.
- Zwei neue Schalter unter Einstellungen › Start und Updates: „Eigene Screenshots im Launcher zeigen“ und „Echte WoW-Bilder im Launcher“ (beide an, beide „Nur hier“). Aus bedeutet: keine Bilder dieser Art, bei den WoW-Bildern wird auch nichts geladen und der Speicher nicht benutzt.
- In den Einstellungen unter „Start und Updates“ gibt es den Schalter „Updates automatisch installieren“ (zunächst aus). Ist er an, lädt der Launcher eine neue Version im Hintergrund und installiert sie, wenn er offen ist und das Spiel nicht startet oder läuft. Vorher erscheint 5 Sekunden lang der Hinweis „Update wird installiert“ mit „Abbrechen“.

### Verbessert
- Kopf, Statuspille, Fußzeile und Abstände sind kompakter; die Fußzeile ist eine Zeile.
- Die Unterseiten (Einstellungen, Hilfe, Spieldateien und andere) sind dichter gesetzt: kleinere Zeilenhöhen und Abstände, es passt deutlich mehr auf einen Schirm.
- Beim ersten Update nach einer Installation von Hand fragt Android noch einmal nach; der Hinweis sagt jetzt klar: ab dem nächsten Update ohne Rückfrage.

## 2.3.3-fold8.21 – 10.11.2026

### Neu
- Der Launcher sieht jetzt aus wie ein Fantasy-Spiel: warmer Stein und Leder statt Marineblau, Goldrahmen mit kleinen Verzierungen in den Ecken, goldene Überschriften in Versalien und eine rote „Spielen“-Taste mit Goldrand. Die Statuspille ist ein Edelstein, die Bilder sind in Gold und Violett gehalten. Alles ist selbst gezeichnet, es liegen keine fremden Bilder oder Schriften dabei.
- Auf der Einstellungsseite zeigt jede Zeile, wo man sie ändern kann: „Nur hier · ab nächstem Start“ wirkt erst beim nächsten Spielstart, „Auch im Spiel“ nennt dazu den Ort, zum Beispiel „Seitenmenü > Steuerung“. Eine Zeile oben erklärt die Abzeichen.
- Auf der Seite „Hilfe und Diagnose“ steht neu „Was kann ich im Spiel ändern?“ mit allem, was es nur im Spiel gibt (Seitenmenü, Touch-Editor, Addon-Optionen, WoW-eigene Optionen).

### Verbessert
- Antippbare Flächen auf der Startseite sind jetzt mindestens 48 dp hoch, die Schrift hat auf allen Flächen gut lesbaren Kontrast.
- Kacheln leuchten beim Drücken golden auf, die große Taste wird dabei heller.

## 2.3.3-fold8.20 – 10.11.2026

### Neu
- Neue Startseite als Kachelbild: links groß „Spielen“ (oder was vorher nötig ist: Dateizugriff erlauben, installieren, aktualisieren), rechts sechs Kacheln – Touch-Layout, Addons, Einstellungen, Spieldateien, Neuigkeiten und Hilfe und Diagnose. Jede Kachel nennt in der zweiten Zeile ihren Zustand und öffnet eine eigene Seite mit „Zurück“. Zugeklappt steht das Spielen oben und die Kacheln in zwei Spalten darunter.
- Oben rechts zeigt eine Statuspille, ob das Spiel bereit ist (grün), etwas fehlt oder ein Update nötig ist (gelb), etwas läuft (blau) oder ein Problem vorliegt (rot). Ein Tipp darauf öffnet „Hilfe und Diagnose“.
- Neue Seiten: „Spieldateien“ (Stand, Installieren oder Aktualisieren, Ordner wählen oder vorhandene Kopie suchen, Battle.net-Anmeldung ändern oder vergessen), „Neuigkeiten“ (die Änderungsliste), „Hilfe und Diagnose“ (Bereitschaftsliste, Fehlerbericht) und „Über“ (Version, Lizenz, Quellcode, Hinweis, dass die App inoffiziell ist und keine Spieldateien enthält).
- Fußzeile mit App-Version, Lizenzen, Quellcode und dem Hinweis „inoffiziell“. Namen und Bilder sind vorläufig und werden später ersetzt.

### Verbessert
- Ein Launcher-Update erscheint als schmaler Streifen mit „Details“ und „Installieren“ statt als große Karte; die Änderungen dazu stehen auf der Seite „Neuigkeiten“.
- Die lange Änderungsliste und die große Bereitschaftsliste füllen die Startseite nicht mehr. Der Spielen-Knopf ist immer sofort zu sehen.
- Die Kachel „Touch-Layout“ springt in die Steuerungs-Einstellungen.

## 2.3.3-fold8.19 – 10.11.2026

### Verbessert
- Tastenleiste: Wechselst du auf „Eigene“, startet die Leiste mit einer sichtbar anderen Vorlage – Enter, Leertaste, Tab, Escape und Rücktaste –, damit du sofort siehst, dass die Auswahl übernommen ist. Schon angelegte eigene Einträge bleiben unverändert.
- Unbelegte Knöpfe sind im Spiel ganz unsichtbar und lassen die Berührung durch. Im Editor bleiben sie mit einem blassen „+“ sichtbar.

## 2.3.3-fold8.18 – 10.11.2026

### Neu
- Im Editor steht oben im Blatt jedes Elements die „Knopfart“ mit Bild und Namen (Taste, Steuerkreuz, Tastenleiste, Stick, Trackpad, Dynamische Sticks, Scrollrad). Ein Tipp stellt das Element um, Position und Größe bleiben.
- Beim Hinzufügen gibt es die „Tastenleiste“ als eigenen Eintrag; im Blatt heißt sie jetzt auch so und nicht mehr „Längliche Taste“.
- Seite „Einträge“ der Tastenleiste: oben steht „Sichtbar in der Leiste“. Du siehst und änderst dort direkt, wie viele der Einträge die Leiste auf einmal zeigt. Wählst du „Eigene“, springt das Blatt gleich zu den Einträgen.

### Verbessert
- Minikarte am Rand ganz in der Ecke: Die Karte wird nicht mehr vergrößert (das hatte Taschenleiste, Koordinaten und Questverfolgung überdeckt), sondern nur einmal bis auf eine kleine Lücke in die obere rechte Ecke geschoben. Das Spiel hielt sie bisher mit seinem Randabstand zurück; dieser Abstand ist jetzt geöffnet.
- Bearbeitungsmodus und Minikarte arbeiten nicht mehr gegeneinander: Beim Öffnen springt die Karte nicht mehr, und du kannst sie dort selbst bis in die Ecke ziehen. Das Addon greift nur noch einmal nach dem Anmelden, nach dem Schließen des Bearbeitungsmodus und beim Umschalten ein, es schiebt nichts mehr dauernd zurück.
- Taschenleiste „Rucksack + Munition“: Die Leiste (Rahmen und Hintergrund) ist jetzt nur so lang wie die sichtbaren Knöpfe und sitzt rechtsbündig direkt unter der Minikarte (bei „Erfahrungsleiste unter der Minikarte“ darunter), ohne Koordinaten und Questverfolgung zu überdecken. Steht die Leiste im Bearbeitungsmodus woanders, bleibt sie dort.
- `/tf minimap info` zeigt zusätzlich Rechteck von Karte und Cluster, Abstand zum Bildrand, Klemm-Abstände, Anker, Breite der Taschenleiste und die im Bearbeitungsmodus gespeicherten Anker, damit sich am Gerät messen lässt.

### Behoben
- Tastenleiste mit eigenen Einträgen: Eine Leiste mit weniger als vier sichtbaren Tasten wächst jetzt mit, wenn du einen Eintrag hinzufügst, statt bei zwei Tasten stehen zu bleiben.
- Ein neuer, unbelegter Knopf zeigt nicht mehr den Text „NONE“. Im Editor trägt er ein blasses „+“, im Spiel bleibt er ohne Text.

## 2.3.3-fold8.17 – 10.11.2026

### Neu
- Minikarte am Rand: Die Karte rückt bis auf eine kleine Lücke in die obere rechte Ecke und wird dabei etwas größer, ihr unterer Rand bleibt, wo er war. So wirkt sie abgeschlossener, und darunter muss nichts verrutschen. Im Bearbeitungsmodus gilt wieder die Vorgabe des Spiels, eine eigene Position weit weg von der Ecke bleibt unberührt. Unter Addon-Optionen > Minikarte gibt es den Schalter „Minikarte am Rand“ (auch `/tf minimap corner on|off`).
- Chat-Knöpfe statt fester Chatleiste: Sprache, Senden (Enter), Tastatur, Kanal wechseln und Abbrechen sind jetzt gewöhnliche Knöpfe, die du im Editor wie alle anderen verschieben, vergrößern und gestalten kannst (Stil Aktuell, F, G). Du findest sie bei der Belegung unter „WoW-Aktion“ > „Chat-Knöpfe“. Die feste Leiste unter dem Chat und der Schalter „Chatleiste“ in den Spracheinstellungen entfallen.
- Im Editor hat jeder Knopf den Abschnitt „Sichtbarkeit“ und die Einstellung „Zeigen“: Immer, Bei sichtbarem Chat oder Bei offener Chatzeile. Im Editor siehst du jeden Knopf.
- „An Chatfenster heften“: Der Knopf behält seinen Abstand zu einer gewählten Ecke des Chatfensters und wandert mit, wenn du den Chat verschiebst oder vergrößerst. Der Abstand wird beim Verschieben im Editor neu gemessen; meldet das Spiel das Chatfenster nicht, bleibt der Knopf, wo du ihn hingelegt hast.
- Bei jedem Element gibt es „Bei offenen Fenstern“: Automatisch (wie bisher nach der Einstellung), Immer sichtbar (bleibt auch bei „Alle außer ESC/Tastatur“) oder Ausblenden (verschwindet, sobald ein größeres Fenster offen ist). Der letzte Escape- oder Tastaturknopf bleibt immer sichtbar, damit du dich nicht aussperrst; der Editor weist darauf hin. Die Chat-Knöpfe stehen auf „Immer sichtbar“.
- Das Standardprofil „Touch (WoW)“ bringt zwei Chat-Knöpfe mit (Sprache und Senden), an die obere rechte Ecke des Chatfensters geheftet. Eigene, schon bearbeitete Profile bleiben unverändert; die Knöpfe fügst du dort im Editor selbst hinzu.

### Verbessert
- Taschenleiste in drei Stufen: „Ganz“, „Rucksack + Munition“ (Vorgabe) und „Aus“. In der Vorgabe bleiben nur der Rucksack mit der Zahl freier Plätze und, wo vorhanden, die Köcher- oder Munitionstasche sichtbar; die übrigen Taschenplätze sind ausgeblendet. Unter Addon-Optionen > Minikarte wählbar (auch `/tf bags full|pack|off`). Wer in fold8.16 die Leiste aus- oder eingeblendet hatte, bekommt die neue Vorgabe.

## 2.3.3-fold8.16 – 10.11.2026

### Neu
- Kreismenüs öffnen sich zur Bildschirmmitte hin: Sitzt der Knopf oben am Rand, öffnet sich der Bogen nach unten, am linken Rand nach rechts, am rechten Rand nach links, in den Ecken schräg. Die Mitte bleibt auf dem Knopf, das Menü wird nur noch verschoben, wenn es in keiner Richtung auf den Bildschirm passt. Das gilt für alle Kreismenüs, auch das Kontextmenü.
- Im Editor hat jeder Kreismenü-Knopf die Einstellung „Öffnet nach“: Automatisch, Oben, Unten, Links oder Rechts. Eine feste Richtung gilt, außer sie liefe aus dem Bild: dann nimmt das Menü die nächste, die passt. Alte Profile bleiben auf Automatisch.
- Chatleiste: Direkt am Chatfenster sitzt jetzt eine Reihe großer Knöpfe. Sie braucht keine Tastatur: Mikrofon (Sprache), Enter, Tastatur, Kanal wechseln und Abbrechen. Ist das Eingabefeld zu, zeigt sie nur Mikrofon und Enter zum Öffnen; ist es offen, kommen die übrigen Knöpfe dazu. Hast du einen Gegenstand verlinkt, sprichst du den Rest ein und die Zeile geht mit einem Tipp ab. Unter Einstellungen > Sprache gibt es den Schalter „Chatleiste“.
- Das Chatfenster wird bündig an den linken Rand gesetzt, wenn es nahe daran liegt, auch nach dem Bearbeiten im HUD-Editor. Im Spiel gibt es dafür unter Chat mehr „Chat bündig links halten“ und den Knopf „Chat jetzt links andocken“.
- Die Taschenleiste unter der Minikarte ist jetzt ausgeblendet. Die Taschen öffnest du weiter mit der Taste, dem Selbst-Menü oder dem WoW-Menü. In den Addon-Optionen (Minikarte) lässt sie sich wieder einblenden.

### Verbessert
- Das Klickrad und die Vorschaubilder im Editor haben jetzt dasselbe Aussehen wie die anderen Kreismenüs (Ringsegmente im gewählten Stil). Belegung und Treffer des Klickrads bleiben, wie sie waren.
- Die Mitte „Abbrechen“ des Kreismenüs ist deckend: die Beschriftung des Knopfes darunter scheint nicht mehr durch.
- Update im Launcher: Der Balken zeigt echten Fortschritt und endet sichtbar mit „Update fertig“ und vollem Balken, statt bei „App startet neu …“ endlos weiterzulaufen.

### Behoben
- Die Weltkarte (auch maximiert) ragt nicht mehr rechts aus dem Bild: Sie wird so verkleinert, dass sie ganz auf den Bildschirm passt und das X sichtbar ist. Abschaltbar in den Addon-Optionen unter Touch.

## 2.3.3-fold8.15 – 10.11.2026

### Neu
- Alle Kreismenüs haben jetzt dasselbe Aussehen wie der Stick-Ring: echte Ringsegmente im gewählten Stil (Aktuell, F, G) statt einzelner Kacheln oder Tortenstücke. Das gilt für das Kontextmenü, das „WoW-Menü“ und die Kreismenüs deiner Knöpfe.
- Überall derselbe Bogen: 270 Grad nach oben, unten bleibt ein Stück frei. Die Belegung und die Reihenfolge der Kreismenüs bleiben, die Felder verteilen sich nur auf den Bogen, bei mehr als acht Feldern kommt ein zweiter Ring außen dazu.
- Hältst du deine eigene Figur gedrückt, öffnet sich das Selbst-Menü: Taschen, Charakter, Zauberbuch, Talente und Karte.
- Hältst du einen anderen Spieler gedrückt, kommt ein Mini-Menü: innen Flüstern per Sprache, Flüstern mit Tastatur, Einladen, Folgen, Handeln und Winken; außen Freund hinzufügen, Betrachten, hilfreiche Zauber in Reichweite und „Mehr …“. „Mehr …“ öffnet wie bisher das Spielermenü des Spiels.
- Leere Felder von Stick-Ring und Kreismenüs werden im Spiel ausgeblendet und lassen sich nicht antippen: Felder ohne Belegung und Felder, deren Aktionsleisten-Platz gerade leer ist. Im Editor bleiben alle Felder sichtbar (leere gestrichelt), damit du sie belegen kannst.

### Verbessert
- Die Symbole in den Kreismenüs sind etwas kleiner, der Name steht klein darunter. Das Feld unter dem Finger leuchtet und zeigt seinen vollen Namen daneben.

## 2.3.3-fold8.14 – 10.11.2026

### Neu
- Änderungsliste im Launcher: Wird ein Update angeboten, siehst du alle Änderungen seit deiner Version groß und nach Neu, Verbessert und Behoben sortiert.
- Die Liste bleibt sichtbar, während das Update lädt und installiert wird, damit du in Ruhe lesen kannst.
- Nach einem Update zeigt der Launcher einmal „Neu in dieser Version“ mit einem Knopf „Verstanden“.
- Unter Einstellungen > Updates gibt es den Eintrag „Änderungen“ mit der vollständigen Liste.
- Das Kontextmenü ist jetzt ein Kreis um deinen Finger: Die Einträge liegen als Bogen oben, unten bleibt ein Stück frei, damit die Hand nichts verdeckt. Wichtiges (Angriffe, Benutzen) liegt innen und oben, mehr als acht Einträge kommen auf einen zweiten Ring außen.
- Hältst du einen anderen Spieler gedrückt, öffnet sich das Menü des Spiels selbst (Flüstern, Einladen, Handeln, Betrachten, Folgen, Fokus und mehr), so wie beim Rechtsklick auf sein Bild.
- Die Einträge zeigen Symbole statt Text: Zauber mit ihrem Symbol aus dem Spiel, Angriff, Plündern, Kürschnern, Ansprechen, Ziel wählen, Verschieben, Wegwerfen und die anderen mit gezeichneten Symbolen und dem Namen klein darunter. Fehlt ein Bild, steht der Name.
- In den Taschen gibt es bei jedem Gegenstand „Benutzen“ (bei Ausrüstung „Anlegen“, beim Händler „Verkaufen“) als echten Rechtsklick.

### Verbessert
- Das Menü verdeckt Zielfenster und Tooltip nach Möglichkeit nicht und rückt am Bildrand nach innen. Nur wenn der Finger sonst auf einer Kachel läge, dreht sich der Bogen, so wenig wie nötig.
- Die Zauberlisten enthalten nur noch Zauber, die man auf eine andere Einheit wirken kann.
- Der Befehl /tf kontext im Spiel zeigt jetzt auch, ob eine Einheit angreifbar ist, wie das Anlegen erkannt wurde und was das Menü des Spiels getan hat.

### Behoben
- Gelbe (neutrale) Gegner, die man angreifen kann, zeigten „Ansprechen“. Alles Angreifbare bekommt jetzt das Angriffsmenü, „Ansprechen“ gibt es nur für Händler, Questgeber und Ähnliche.
- Bei Spielern standen Zauber wie Fährten, Schatz finden und Steinform im Menü.
- Bei einem Umhang in der Tasche fehlte „Anlegen“.

## 2.3.3-fold8.13 – 10.11.2026

### Neu
- Kontextmenü beim Gedrückthalten: Wer eine Einheit (Spieler, NPC, lebender oder toter Gegner) gedrückt hält, bekommt passende Aktionen angeboten, Angriffe nach Reichweite sortiert.
- Das Kontextmenü funktioniert auch auf Gegenständen in Taschen, Bank und Ausrüstung. „Wegwerfen“ fragt vorher mit Ja oder Nein nach.

### Verbessert
- Das Addon TouchForever erkennt jetzt, worauf du gerade drückst (Einheit, Tasche, Bank, Ausrüstung).

## 2.3.3-fold8.12 – 09.11.2026

### Neu
- Touch-Tasten zeigen Symbole statt großer Schrift (Leertaste, Tab, Umschalt, Enter, Pfeile, Maus). Die Schrift passt sich der Tastengröße an.

### Verbessert
- Der Touch-Editor hat ruhigere Farben (Schiefer und Bronze) und ist klarer gegliedert.

### Behoben
- Beschriftungen von Tasten im Editor werden zuverlässig angezeigt.
- Mehrere Fehler im Touch-Editor behoben.

## 2.3.3-fold8.11 – 09.11.2026

### Neu
- Tastenleiste „Eigene“: Du legst die Einträge selbst fest, belegst sie wie die Ringtasten und sortierst sie per Griff oder Pfeil.
- Zwei weitere Leistenstile (F und G) für die Tastenleiste.

## 2.3.3-fold8.10 – 09.11.2026

### Neu
- Stil-Umschalter für die Touch-Elemente: „Aktuell“, „F“ oder „G“ zum Vergleichen am Gerät.
- Kontrastregler und Ruhe-Timer: Nach einer Pause werden die Touch-Elemente dezenter.

## 2.3.3-fold8.9 – 09.11.2026

### Neu
- Wischen in Listen: Senkrechtes Wischen in Listen und Fenstern des Spiels scrollt wie das Mausrad.
- Fortschrittsbalken bei der Installation eines Launcher-Updates.

## 2.3.3-fold8.8 – 09.11.2026

### Neu
- Scrollrad: Die Geschwindigkeit lässt sich einstellen (mit Voreinstellungen).
- Am Stick gibt es einen zweiten, äußeren Ring mit Tasten.
- Im Seitenmenü öffnet „Addon-Einstellungen“ das Einstellungsfenster des Addons ohne Tippen.

### Verbessert
- Eckige Minikarte: Zonenname, Tag-und-Nacht-Anzeige und Suche sitzen sauber in den Ecken, die Zoom-Knöpfe sind größer.
- Das Einstellungsfenster des Addons ist in sieben übersichtliche Seiten gegliedert.
- Das Chatfenster schließt bündig am Bildrand ab.
- Touch-Elemente weichen einem offenen Fenster schon ab 10 Prozent Überdeckung aus.

### Behoben
- Die Scroll-Geschwindigkeit eines Mausrad-Elements ließ sich im Editor nicht ändern.

## 2.3.3-fold8.7 – 09.11.2026

### Behoben
- Das Addon war in den Versionen fold8.2 bis fold8.6 nicht auf dem neuesten Stand. Jetzt wird immer die aktuelle Fassung ausgeliefert und vor dem Veröffentlichen geprüft.
- Die Spieloberfläche wird nicht mehr von selbst neu geladen.
- Touch-Elemente werden hinter einer geöffneten Weltkarte ausgeblendet.

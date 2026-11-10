# Mitgelieferte freie Bilder für den Launcher

Hier liegen freie Bilder (Open-Source-Artwork, gemeinfreie Gemälde, freie Fotos), die der Launcher zeigt, wenn weder ein
eigener Screenshot noch WoW-Artwork aus dem Netz da ist. Reihenfolge Hero: eigene Screenshots, WoW-Artwork, diese Bilder,
gezeichnete Kunst. Kacheln: WoW-Karte, diese Bilder, gezeichnetes Motiv. Jedes Bild braucht eine Zeile in der Tabelle unten.
Die Liste auf der Seite "Über" in der App (`ART_CREDITS` in `ui/screen/wow/launcher/AboutPage.kt`) muss dieselben Dateien nennen;
ein Test (`LauncherArtCreditsTest`) prüft das.

## Namen der Dateien

- Hero-Bilder: Dateiname beginnt mit `hero` (zum Beispiel `hero_dawn.jpg`); jpg, jpeg, png oder webp. Mehrere sind erlaubt, der Launcher
  nimmt je App-Start ein anderes.
- Kachelbilder: `tile_<kachel>.<endung>` mit `touch`, `addons`, `settings`, `files`, `news`, `help` (zum Beispiel `tile_help.jpg`).
- Größe: Hero 1920 x 1080 (16:9), Kachel 800 x 500 (16:10), JPEG Qualität 82, progressiv, ohne EXIF, jeweils unter 400 KB. Der Launcher
  verkleinert beim Laden, größere Dateien blähen nur das APK auf.
- Keine Bilder von Blizzard oder aus Spielen. Nur Bilder, deren Lizenz Weitergabe in einer GPL-3.0-App erlaubt (gemeinfrei, CC0, CC BY,
  CC BY-SA, Unsplash-Lizenz). CC BY und CC BY-SA verlangen Namensnennung: sie steht auf der Seite "Über".
- Alle Bilder: verkleinert und zugeschnitten (Hero aus der Bildmitte auf 16:9, Kachel auf 16:10); sonst nicht verändert. Quelle der
  Dateien: `upload.wikimedia.org` (Commons), bei `hero_stars_over_mountains.jpg` `images.unsplash.com`. Geladen am 10.11.2026.

## Quellen

Lizenzlinks: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/),
[CC BY 2.0](https://creativecommons.org/licenses/by/2.0/), [Unsplash-Lizenz](https://unsplash.com/license). "gemeinfrei" = Schutzfrist
abgelaufen (Public domain laut Commons-Dateiseite).

| Datei | Titel | Urheber | Lizenz | Dateiseite (Quelle) | Änderung |
|---|---|---|---|---|---|
| hero_confront_the_dragon.jpg | Confront the dragon (2022) | David Revoy | CC BY 4.0 | https://commons.wikimedia.org/wiki/File:2022-10-22_Confront-the-dragon_by-David-Revoy.jpg | verkleinert, zugeschnitten |
| hero_twilight_in_the_wilderness.jpg | Twilight in the Wilderness (1860) | Frederic Edwin Church | gemeinfrei | https://commons.wikimedia.org/wiki/File:Frederic_Edwin_Church_-_Twilight_in_the_Wilderness.jpg | verkleinert, zugeschnitten (Rand abgeschnitten) |
| hero_landers_peak.jpg | The Rocky Mountains, Lander's Peak (1863) | Albert Bierstadt | gemeinfrei | https://commons.wikimedia.org/wiki/File:Albert_Bierstadt_-_The_Rocky_Mountains,_Lander%27s_Peak.jpg | verkleinert, zugeschnitten |
| hero_eilean_donan.jpg | Eilean Donan at Dusk | Syxaxis Photography | CC BY-SA 4.0 | https://commons.wikimedia.org/wiki/File:Eilean_Donan_at_Dusk.jpg | verkleinert (Original 2600 x 1462), zugeschnitten |
| hero_stars_over_mountains.jpg | snow mountain under stars | Benjamin Voros | Unsplash-Lizenz | https://unsplash.com/photos/snow-mountain-under-stars-phIFdC6lA4E (Bild: images.unsplash.com/photo-1519681393784-d120267933ba) | verkleinert, zugeschnitten |
| hero_great_day_of_his_wrath.jpg | The Great Day of His Wrath (um 1851) | John Martin | gemeinfrei | https://commons.wikimedia.org/wiki/File:John_Martin_-_The_Great_Day_of_His_Wrath_-_Google_Art_Project.jpg | verkleinert, zugeschnitten |
| tile_touch.jpg | Battle of Grunwald (1878), Ausschnitt Mitte | Jan Matejko | gemeinfrei | https://commons.wikimedia.org/wiki/File:Jan_Matejko,_Bitwa_pod_Grunwaldem.jpg | Ausschnitt, verkleinert |
| tile_addons.jpg | The Blue Crystal (2022) | David Revoy | CC BY 4.0 | https://commons.wikimedia.org/wiki/File:2022-11-30_The-Blue-Crystal_by-David-Revoy.jpg | verkleinert, zugeschnitten |
| tile_settings.jpg | Wieliczka, St Kingas Chapel (Kapelle der hl. Kinga, Salzbergwerk Wieliczka) | Arian Zwegers | CC BY 2.0 | https://commons.wikimedia.org/wiki/File:Wieliczka,_St_Kingas_Chapel_(6467348331).jpg | verkleinert, zugeschnitten |
| tile_files.jpg | The Course of Empire: Desolation (1836) | Thomas Cole | gemeinfrei | https://commons.wikimedia.org/wiki/File:Cole_Thomas_The_Course_of_Empire_Desolation_1836.jpg | verkleinert, zugeschnitten |
| tile_news.jpg | Abtei im Eichwald (1809/10) | Caspar David Friedrich | gemeinfrei | https://commons.wikimedia.org/wiki/File:Caspar_David_Friedrich_-_Abtei_im_Eichwald_-_Google_Art_Project.jpg | verkleinert, zugeschnitten |
| tile_help.jpg | Introduce the dragon to the outside world (2022) | David Revoy | CC BY 4.0 | https://commons.wikimedia.org/wiki/File:2022-10-28_Introduce-the-dragon-to-the-outside-world_by-David-Revoy.jpg | verkleinert, zugeschnitten (linker Bildteil) |

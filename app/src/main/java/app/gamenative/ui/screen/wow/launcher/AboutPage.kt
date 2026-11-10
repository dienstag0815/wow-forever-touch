package app.gamenative.ui.screen.wow.launcher

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.ui.screen.wow.SettingActionRow
import app.gamenative.ui.screen.wow.SettingGroupCard
import app.gamenative.ui.screen.wow.SettingRow

/** Dateiname der mitgelieferten Fremdhinweise (Kopie von THIRD_PARTY_NOTICES im Repo, siehe app/src/main/assets). */
internal const val NOTICES_ASSET = "THIRD_PARTY_NOTICES.txt"

/** Liest die Fremdhinweise aus den Assets; bei einem Fehler eine kurze Erklärung statt eines Absturzes. */
internal fun loadNotices(context: Context): String =
    runCatching { context.applicationContext.assets.open(NOTICES_ASSET).use { it.readBytes().toString(Charsets.UTF_8) } }
        .getOrElse { context.getString(R.string.launcher_about_notices_unreadable) }

/** Dateiname des Lizenztextes der Schrift Cinzel (SIL Open Font License 1.1) in den Assets. */
internal const val CINZEL_LICENSE_ASSET = "licenses/Cinzel-OFL.txt"

/**
 * Lizenz eines mitgelieferten Bildes: Anzeigename und Link auf den Lizenztext (null bei gemeinfrei). [labelRes] ist, wo der Name
 * übersetzt wird (gemeinfrei, Unsplash-Lizenz), sonst null; [label] ist dann die englische Fassung.
 */
internal enum class ArtLicense(val label: String, val url: String?, @StringRes val labelRes: Int? = null) {
    PublicDomain("public domain", null, R.string.launcher_license_public_domain),
    CcBy4("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
    CcBySa4("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"),
    CcBy2("CC BY 2.0", "https://creativecommons.org/licenses/by/2.0/"),
    Unsplash("Unsplash License", "https://unsplash.com/license", R.string.launcher_license_unsplash),
}

/** Namensnennung eines Bildes in assets/launcher_art ([file] ist der Dateiname dort). Die Quellen stehen in QUELLEN.md. */
internal data class ArtCredit(val file: String, val title: String, val author: String, val license: ArtLicense)

/** Alle mitgelieferten Bilder mit Titel, Urheber und Lizenz; muss zu den Dateien in assets/launcher_art passen (siehe Test). */
internal val ART_CREDITS: List<ArtCredit> = listOf(
    ArtCredit("hero_confront_the_dragon.jpg", "Confront the dragon", "David Revoy", ArtLicense.CcBy4),
    ArtCredit("hero_twilight_in_the_wilderness.jpg", "Twilight in the Wilderness", "Frederic Edwin Church", ArtLicense.PublicDomain),
    ArtCredit("hero_landers_peak.jpg", "The Rocky Mountains, Lander's Peak", "Albert Bierstadt", ArtLicense.PublicDomain),
    ArtCredit("hero_eilean_donan.jpg", "Eilean Donan at Dusk", "Syxaxis Photography", ArtLicense.CcBySa4),
    ArtCredit("hero_stars_over_mountains.jpg", "snow mountain under stars", "Benjamin Voros", ArtLicense.Unsplash),
    ArtCredit("hero_great_day_of_his_wrath.jpg", "The Great Day of His Wrath", "John Martin", ArtLicense.PublicDomain),
    ArtCredit("tile_touch.jpg", "Battle of Grunwald (detail)", "Jan Matejko", ArtLicense.PublicDomain),
    ArtCredit("tile_addons.jpg", "The Blue Crystal", "David Revoy", ArtLicense.CcBy4),
    ArtCredit("tile_settings.jpg", "Wieliczka, St Kingas Chapel", "Arian Zwegers", ArtLicense.CcBy2),
    ArtCredit("tile_files.jpg", "The Course of Empire: Desolation", "Thomas Cole", ArtLicense.PublicDomain),
    ArtCredit("tile_news.jpg", "Abtei im Eichwald", "Caspar David Friedrich", ArtLicense.PublicDomain),
    ArtCredit("tile_help.jpg", "Introduce the dragon to the outside world", "David Revoy", ArtLicense.CcBy4),
)

/** Zeile "Titel – Urheber – Lizenz" für die Seite "Über"; [licenseLabel] ist der Name der Lizenz in der App-Sprache (Standard: englisch). */
internal fun artCreditLine(credit: ArtCredit, licenseLabel: String = credit.license.label): String =
    "${credit.title} – ${credit.author} – $licenseLabel"

/** Der Name der Lizenz in der App-Sprache. */
@Composable
private fun ArtLicense.localLabel(): String = labelRes?.let { stringResource(it) } ?: label

/** Die Lizenzen mit Link, die in [credits] vorkommen, je einmal, in der Reihenfolge der Aufzählung. */
internal fun artLicensesWithLink(credits: List<ArtCredit>): List<ArtLicense> =
    ArtLicense.entries.filter { lic -> lic.url != null && credits.any { it.license == lic } }

/** Liest einen Text aus den Assets; bei einem Fehler [fallback] statt eines Absturzes. */
internal fun loadAssetText(context: Context, path: String, fallback: String): String =
    runCatching { context.applicationContext.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) } }.getOrElse { fallback }

/**
 * Seite "Über" als Übersicht: Name und Version mit den Hinweisen (inoffiziell, keine Spieldateien) über zwei Spalten, Lizenz und
 * Quellcode, Schrift, Bilder und Lizenzhinweise. Die volle Lizenzerklärung, die Herkunft, der Lizenztext der Schrift, die Liste aller
 * Bilder mit Urheber und Lizenz sowie die Texte der Fremdkomponenten stehen in der Detailansicht der Karte (Antippen).
 */
@Composable
internal fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var detailId by remember { mutableStateOf<String?>(null) }
    val plan = remember { aboutPlan() }
    val fontCredit = stringResource(R.string.launcher_about_font_credit)
    val fontLicenseMissing = stringResource(R.string.launcher_about_font_license_missing)

    val cards = listOf(
        plan.card("app", stringResource(R.string.launcher_about_app_title)) {
            CardValueRow(stringResource(R.string.launcher_about_app_version), BuildConfig.VERSION_NAME)
            CardText(LauncherTexts.UNOFFICIAL_NOTICE.asString(), 2, LauncherColors.Body)
            CardText(LauncherTexts.NO_GAME_FILES_NOTICE.asString(), 2)
        },
        plan.card("license", stringResource(R.string.launcher_about_license), onOpen = { detailId = "license" }) {
            CardActionRow(stringResource(R.string.launcher_footer_source), stringResource(R.string.launcher_open)) {
                runCatching { uriHandler.openUri(LauncherTexts.SOURCE_URL) }
            }
            CardText(stringResource(R.string.launcher_about_license_short, LauncherTexts.LICENSE_NAME), 2)
        },
        plan.card("font", stringResource(R.string.launcher_about_font), onOpen = { detailId = "font" }) {
            CardText(fontCredit, 2, LauncherColors.Body)
        },
        plan.card("images", stringResource(R.string.launcher_about_images), onOpen = { detailId = "images" }) {
            CardText(stringResource(R.string.launcher_about_images_short, ART_CREDITS.size), 3, LauncherColors.Body)
        },
        plan.card("notices", stringResource(R.string.launcher_about_notices), onOpen = { detailId = "notices" }) {
            CardText(stringResource(R.string.launcher_about_notices_short), 2, LauncherColors.Body)
        },
    )

    val close: () -> Unit = { detailId = null }
    val detail: PageDetail? = when (detailId) {
        "license" -> PageDetail(stringResource(R.string.launcher_about_license_source), close) {
            SettingGroupCard(stringResource(R.string.launcher_about_license_source)) {
                SettingRow(
                    title = stringResource(R.string.launcher_about_license),
                    description = stringResource(R.string.launcher_about_license_long, LauncherTexts.LICENSE_NAME),
                )
                SettingActionRow(
                    title = stringResource(R.string.launcher_footer_source),
                    description = LauncherTexts.SOURCE_URL,
                    actionText = stringResource(R.string.launcher_open),
                    onClick = { runCatching { uriHandler.openUri(LauncherTexts.SOURCE_URL) } },
                )
                SettingRow(title = stringResource(R.string.launcher_about_origin), description = LauncherTexts.ORIGIN_LINES.asString())
            }
            SettingGroupCard(stringResource(R.string.launcher_about_app_title)) {
                SettingRow(title = stringResource(R.string.launcher_about_app_version), description = BuildConfig.VERSION_NAME)
                SettingRow(title = LauncherTexts.UNOFFICIAL_TAG.asString(), description = LauncherTexts.UNOFFICIAL_NOTICE.asString())
                SettingRow(title = stringResource(R.string.launcher_about_no_game_files), description = LauncherTexts.NO_GAME_FILES_NOTICE.asString())
            }
        }
        "font" -> PageDetail(stringResource(R.string.launcher_about_font), close) {
            SettingGroupCard(stringResource(R.string.launcher_about_font_title)) {
                SettingRow(
                    title = stringResource(R.string.launcher_about_font_title),
                    description = stringResource(R.string.launcher_about_font_copyright, fontCredit),
                )
            }
            val text = remember { loadAssetText(context, CINZEL_LICENSE_ASSET, fontLicenseMissing) }
            MonoText(text)
        }
        "images" -> PageDetail(stringResource(R.string.launcher_about_images), close) {
            SettingGroupCard(stringResource(R.string.launcher_about_wow_images)) {
                SettingRow(
                    title = stringResource(R.string.launcher_about_wow_images),
                    description = stringResource(R.string.launcher_about_wow_images_desc),
                )
            }
            SettingGroupCard(stringResource(R.string.launcher_about_images)) {
                ART_CREDITS.forEach { credit ->
                    SettingRow(title = artCreditLine(credit, credit.license.localLabel()))
                }
                SettingRow(
                    title = stringResource(R.string.launcher_about_bundled_images),
                    description = stringResource(R.string.launcher_about_bundled_images_desc),
                )
                artLicensesWithLink(ART_CREDITS).forEach { license ->
                    val url = license.url.orEmpty()
                    SettingActionRow(
                        title = stringResource(R.string.launcher_about_license_text, license.localLabel()),
                        description = url,
                        actionText = stringResource(R.string.launcher_open),
                        onClick = { runCatching { uriHandler.openUri(url) } },
                    )
                }
            }
        }
        "notices" -> PageDetail(stringResource(R.string.launcher_about_notices), close) {
            SettingGroupCard(stringResource(R.string.launcher_about_notices)) {
                SettingRow(
                    title = stringResource(R.string.launcher_about_third_party),
                    description = stringResource(R.string.launcher_about_third_party_desc),
                )
            }
            val text = remember { loadNotices(context) }
            MonoText(text)
        }
        else -> null
    }

    LauncherOverviewPage(title = LauncherTexts.pageTitle(LauncherPage.About).asString(), onBack = onBack, cards = cards, detail = detail)
}

/** Langer Text in fester Schrift auf einer Karte (Lizenztext, Lizenzhinweise). */
@Composable
private fun MonoText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontFamily = FontFamily.Monospace,
        color = LauncherColors.Body,
        modifier = Modifier
            .fillMaxWidth()
            .ornateFrame(fill = LauncherColors.Surface, corners = false)
            .padding(16.dp),
    )
}

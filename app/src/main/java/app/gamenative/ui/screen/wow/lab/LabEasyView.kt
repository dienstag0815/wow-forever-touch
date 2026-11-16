package app.gamenative.ui.screen.wow.lab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.lab.EasyMark
import app.gamenative.lab.GuideStep
import app.gamenative.lab.LabGuide
import app.gamenative.lab.LabGroup
import app.gamenative.ui.screen.wow.launcher.ExpandRow
import app.gamenative.ui.screen.wow.launcher.InsetPanel
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import app.gamenative.ui.screen.wow.launcher.PagePrimaryButton
import app.gamenative.ui.screen.wow.launcher.PageSecondaryButton

/*
 * Die Ansicht „Einfach“ der Battle.net-Seite (Entwurf 3.1) und der Rahmen von „Erweitert“ (3.4). Reine Anordnung: was die Knöpfe tun, kommt von der Seite
 * (BnetLabPage.kt); hier stehen nur Zeilen, Haken und das Auf- und Zuklappen.
 */

/** Eine Schrittzeile der Liste: [step] mit Haken [mark] und dem Namen [name]. */
internal class EasyStepUi(val step: GuideStep, val mark: EasyMark, val name: String)

/** Was oben in der Einfach-Karte steht und was der Hauptknopf trägt. */
internal class EasyHeadUi(
    val sentence: String,
    val sentenceIsError: Boolean,
    /** Ein Zusatz unter dem Satz (zum Beispiel Hinweis zum Neustart); null = keiner. */
    val detail: String?,
    val risk: String,
    /** Beschriftung des Hauptknopfs; null = es gibt keinen. */
    val mainText: String?,
    val mainEnabled: Boolean,
    /** Ob die Zeile „Was macht Weiter?“ gilt (nur beim Hauptknopf „Weiter: …“). */
    val showWhat: Boolean = false,
)

/** Teil B der Anleitung („Wenn der direkte Start abgelehnt wird“): ein Knopf „Weiter: …“ (null = keiner) und Zustand des Abschnitts. */
internal class EasyPartBUi(
    val open: Boolean,
    val continueText: String?,
    val continueEnabled: Boolean,
)

@Composable
private fun markGlyph(mark: EasyMark): Pair<String, androidx.compose.ui.graphics.Color> = when (mark) {
    EasyMark.DONE -> "✓" to LauncherColors.Gold
    EasyMark.OPEN -> "○" to LauncherColors.Muted
    EasyMark.PROBLEM -> "!" to LauncherColors.ErrText
    EasyMark.SKIPPED -> "–" to LauncherColors.Muted
}

@Composable
private fun markDescription(mark: EasyMark): String = stringResource(
    when (mark) {
        EasyMark.DONE -> R.string.lab_easy_mark_done
        EasyMark.OPEN -> R.string.lab_easy_mark_open
        EasyMark.PROBLEM -> R.string.lab_easy_mark_problem
        EasyMark.SKIPPED -> R.string.lab_easy_mark_skipped
    },
)

/** Die Zeile eines Schritts: Haken, Nummer und Name, rechts der Pfeil. Tippen klappt sie auf oder zu (auch im Zustand „erledigt“). */
@Composable
private fun EasyStepHeader(index: Int, ui: EasyStepUi, expanded: Boolean, onToggle: () -> Unit) {
    val (glyph, color) = markGlyph(ui.mark)
    val markText = markDescription(ui.mark)
    val openText = stringResource(if (expanded) R.string.lab_easy_expanded else R.string.lab_easy_collapsed)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) { stateDescription = "$markText, $openText" }
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(26.dp))
        Text("$index  ${ui.name}", fontSize = 16.sp, color = LauncherColors.Body, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = LauncherColors.Gold,
            modifier = Modifier.padding(start = 4.dp).size(28.dp),
        )
    }
}

/**
 * Inhalt der Einfach-Karte: Zustandssatz mit Warnzeile, Hauptknopf, Statuszeile, „Was macht Weiter?“, Teil A „Einmal anmelden“ (Schritte 1–4),
 * der eingeklappte Teil B „Wenn der direkte Start abgelehnt wird“ (Schritte 5–7; höchstens eine Zeile offen) und „Hilfe, es klappt nicht“.
 * [stepBody] zeichnet den Inhalt der offenen Zeile.
 */
@Composable
internal fun ColumnScope.LabEasyContent(
    head: EasyHeadUi,
    status: String,
    whatText: String,
    whatOpen: Boolean,
    onToggleWhat: () -> Unit,
    steps: List<EasyStepUi>,
    openStep: GuideStep?,
    onToggleStep: (GuideStep) -> Unit,
    partB: EasyPartBUi,
    onTogglePartB: () -> Unit,
    onPartBContinue: () -> Unit,
    stepBody: @Composable ColumnScope.(GuideStep) -> Unit,
    onMain: () -> Unit,
    onHelp: () -> Unit,
) {
    Text(
        head.sentence,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (head.sentenceIsError) LauncherColors.ErrText else LauncherColors.Body,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
    head.detail?.let {
        Text(it, fontSize = 14.sp, lineHeight = 20.sp, color = LauncherColors.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp))
    }
    Text(head.risk, fontSize = 13.sp, lineHeight = 18.sp, color = LauncherColors.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp))
    head.mainText?.let {
        Column(modifier = Modifier.padding(top = 8.dp)) { PagePrimaryButton(it, enabled = head.mainEnabled, onClick = onMain) }
    }
    if (status.isNotEmpty()) {
        Text(status, fontSize = 14.sp, lineHeight = 20.sp, color = LauncherColors.Body, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp))
    }
    if (head.showWhat && head.mainText != null) {
        ExpandRow(title = stringResource(R.string.lab_easy_what), expanded = whatOpen, onToggle = onToggleWhat)
        if (whatOpen) InsetPanel { Text(whatText, fontSize = 14.sp, lineHeight = 20.sp, color = LauncherColors.Muted) }
    }
    val (partA, partBSteps) = steps.withIndex().partition { it.value.step in LabGuide.PART_A }
    GroupHeading(stringResource(R.string.lab_easy_part_a_title))
    Text(stringResource(R.string.lab_easy_part_a_intro), fontSize = 13.sp, lineHeight = 18.sp, color = LauncherColors.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp))
    partA.forEach { (index, ui) ->
        val expanded = openStep == ui.step
        EasyStepHeader(index + 1, ui, expanded) { onToggleStep(ui.step) }
        if (expanded) InsetPanel { stepBody(ui.step) }
    }
    Text(stringResource(R.string.lab_easy_part_a_end), fontSize = 13.sp, lineHeight = 18.sp, color = LauncherColors.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp))
    ExpandRow(
        title = stringResource(R.string.lab_easy_part_b_title),
        description = stringResource(R.string.lab_easy_part_b_desc),
        expanded = partB.open,
        onToggle = onTogglePartB,
    )
    if (partB.open) {
        partB.continueText?.let {
            Column(modifier = Modifier.padding(vertical = 4.dp)) { PageSecondaryButton(it, enabled = partB.continueEnabled, onClick = onPartBContinue) }
        }
        partBSteps.forEach { (index, ui) ->
            val expanded = openStep == ui.step
            EasyStepHeader(index + 1, ui, expanded) { onToggleStep(ui.step) }
            if (expanded) InsetPanel { stepBody(ui.step) }
        }
    }
    Column(modifier = Modifier.padding(top = 8.dp)) { PageSecondaryButton(stringResource(R.string.lab_easy_help), onClick = onHelp) }
}

/**
 * Die Zeile „Erweitert“ und, wenn sie offen ist, die fünf Gruppen (höchstens eine offen). [body] zeichnet den Inhalt der offenen Gruppe, [footer] steht
 * unter den Gruppen (der Schalter „Erweitert immer geöffnet“). [helpRequester] hängt an der Gruppe „Fehlersuche“: „Hilfe, es klappt nicht“ scrollt dorthin.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ColumnScope.LabAdvancedContent(
    open: Boolean,
    onToggle: () -> Unit,
    openGroup: LabGroup?,
    onToggleGroup: (LabGroup) -> Unit,
    helpRequester: BringIntoViewRequester,
    body: @Composable ColumnScope.(LabGroup) -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
) {
    ExpandRow(
        title = stringResource(R.string.lab_easy_advanced),
        description = stringResource(R.string.lab_easy_advanced_desc),
        expanded = open,
        onToggle = onToggle,
    )
    if (!open) return
    for (group in LabGroup.entries) {
        val expanded = openGroup == group
        Column(modifier = if (group == LabGroup.TROUBLESHOOTING) Modifier.bringIntoViewRequester(helpRequester) else Modifier) {
            ExpandRow(
                title = stringResource(groupTitleRes(group)),
                description = stringResource(groupDescRes(group)),
                expanded = expanded,
                onToggle = { onToggleGroup(group) },
            )
            if (expanded) InsetPanel { body(group) }
        }
    }
    footer()
}

internal fun groupTitleRes(group: LabGroup): Int = when (group) {
    LabGroup.TROUBLESHOOTING -> R.string.lab_group_troubleshooting
    LabGroup.SIGN_IN -> R.string.lab_group_sign_in
    LabGroup.GAME_FOLDER -> R.string.lab_group_game_folder
    LabGroup.TECH -> R.string.lab_group_tech
    LabGroup.DEVELOPMENT -> R.string.lab_group_development
}

internal fun groupDescRes(group: LabGroup): Int = when (group) {
    LabGroup.TROUBLESHOOTING -> R.string.lab_group_troubleshooting_desc
    LabGroup.SIGN_IN -> R.string.lab_group_sign_in_desc
    LabGroup.GAME_FOLDER -> R.string.lab_group_game_folder_desc
    LabGroup.TECH -> R.string.lab_group_tech_desc
    LabGroup.DEVELOPMENT -> R.string.lab_group_development_desc
}

/** Die kleine Überschrift über einem Block in einer offenen Gruppe. */
@Composable
internal fun GroupHeading(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LauncherColors.Gold, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
}

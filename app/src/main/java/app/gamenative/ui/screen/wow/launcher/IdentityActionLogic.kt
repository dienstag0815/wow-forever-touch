package app.gamenative.ui.screen.wow.launcher

import app.gamenative.utils.MachineIdentityArchive

/*
 * Reine Logik der Knöpfe "Neue Kennung erzeugen" und "Wieder verwenden" (ohne Compose, per JUnit geprüft): wann ein Knopf aus ist und
 * warum, und welche Rückfrage offen ist.
 */

/** Warum ein Knopf aus ist; [None] = der Knopf geht. */
internal enum class IdentityBlock { None, NoIdentity, GameRunning, Locked }

/** "Neue Kennung erzeugen": nur mit Kennung, nur wenn das Spiel nicht läuft, höchstens eine je 24 Stunden ([lockedUntil] aus dem Protokoll). */
internal fun regenerateBlock(hasIdentity: Boolean, gameBusy: Boolean, lockedUntil: Long?): IdentityBlock = when {
    !hasIdentity -> IdentityBlock.NoIdentity
    gameBusy -> IdentityBlock.GameRunning
    lockedUntil != null -> IdentityBlock.Locked
    else -> IdentityBlock.None
}

/** "Wieder verwenden": nur mit Kennung und wenn das Spiel nicht läuft; die 24-Stunden-Sperre gilt hier nicht. */
internal fun restoreBlock(hasIdentity: Boolean, gameBusy: Boolean): IdentityBlock = when {
    !hasIdentity -> IdentityBlock.NoIdentity
    gameBusy -> IdentityBlock.GameRunning
    else -> IdentityBlock.None
}

/** Die offene Rückfrage. Der Standardknopf jeder Rückfrage ist "Abbrechen". */
internal sealed class IdentityDialog {
    object None : IdentityDialog()
    object Regenerate : IdentityDialog()
    class Reuse(val index: Int, val fingerprint: String) : IdentityDialog()
}

/** Öffnet die Rückfrage nur, wenn der Knopf nicht gesperrt ist. */
internal fun openRegenerate(block: IdentityBlock): IdentityDialog =
    if (block == IdentityBlock.None) IdentityDialog.Regenerate else IdentityDialog.None

/** Öffnet die Rückfrage für Eintrag [index] der Ablage; ohne den Eintrag oder bei gesperrtem Knopf bleibt sie zu. */
internal fun openReuse(block: IdentityBlock, archive: List<MachineIdentityArchive.Info>, index: Int): IdentityDialog {
    val info = archive.getOrNull(index)
    return if (block == IdentityBlock.None && info != null) IdentityDialog.Reuse(index, info.fingerprint) else IdentityDialog.None
}

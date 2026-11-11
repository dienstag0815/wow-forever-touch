package app.gamenative.ui.touchstyle

import app.gamenative.cooldown.CooldownService
import app.gamenative.wowicons.ActionSlotMap
import app.gamenative.wowicons.WowIcons
import com.winlator.inputcontrols.RadialMenu

/**
 * Welche Felder eines Radialmenüs oder Tastenrings im Spiel verschwinden: leere. Leer heißt keine Belegung (weder Taste noch
 * Chat-Befehl) oder eine Belegung auf einen Platz der Aktionsleiste, der gerade leer ist (das Symbol fehlt). Ein ausgeblendetes Feld wird
 * nicht gezeichnet und ist nicht antippbar; seine Stelle bleibt frei, die übrigen Felder behalten ihren Platz. Der Editor zeigt immer
 * alle Felder (leere gestrichelt), damit man sie belegen kann: das entscheidet der Aufrufer, hier steht nur die Frage "leer?".
 */
object RadialSlotVisibility {
    /**
     * Rein: ob ein Feld leer ist. [hasAssignment]: Taste oder Chat-Befehl gesetzt. [barSlot]: Platz der Aktionsleiste (1..180), auf den
     * die Belegung zeigt, null bei allem anderen. [barSlotFilled]: ob der Platz belegt ist, null = unbekannt (dann bleibt das Feld
     * sichtbar: nie auf Verdacht ausblenden).
     */
    @JvmStatic
    fun isEmpty(hasAssignment: Boolean, barSlot: Int?, barSlotFilled: (Int) -> Boolean?): Boolean {
        if (!hasAssignment) return true
        if (barSlot == null) return false
        return barSlotFilled(barSlot) == false
    }

    /** Ob das Feld [slot] jetzt leer ist (Belegung und Stand der Aktionsleiste). */
    @JvmStatic
    fun isEmpty(slot: RadialMenu.Slot?): Boolean {
        if (slot == null) return true
        return isEmpty(slot.isEnabled, ActionSlotMap.slotFor(slot.wowAction)) { liveFilled(it) }
    }

    /**
     * Wie [isEmpty] für ein Ringfeld: ein Feld mit WoW-Aktion zählt auch ohne Taste als belegt. Die Taste kommt aus den Tastenbelegungen
     * von WoW; hat die Aktion dort keine (Aktionsleisten 2 bis 8 sind ab Werk ungebunden), ist die Belegung leer, das Feld aber nicht:
     * es bleibt sichtbar (abgeblendet, siehe [isInactive]), statt im Spiel zu fehlen, während der Editor es zeigt.
     */
    @JvmStatic
    fun isEmptyInRing(slot: RadialMenu.Slot?): Boolean {
        if (slot == null) return true
        return isEmpty(slot.isEnabled || slot.wowAction != null, ActionSlotMap.slotFor(slot.wowAction)) { liveFilled(it) }
    }

    /** Ob ein sichtbares Ringfeld ohne Taste und Chat-Befehl ist (WoW-Aktion ohne Taste in WoW): es tut beim Antippen nichts. */
    @JvmStatic
    fun isInactive(slot: RadialMenu.Slot?): Boolean = slot != null && !slot.isEnabled

    /**
     * Ob der Platz [slot] der Aktionsleiste belegt ist: zuerst der Abklingzeit-Streifen des Addons (live), sonst die Daten, die das Addon
     * zuletzt ins SavedVariables-File schrieb (nur bei /reload und Abmelden, kann veraltet sein); ohne beides null (unbekannt).
     */
    @JvmStatic
    fun liveFilled(slot: Int): Boolean? {
        CooldownService.stateFor(slot)?.let { return it.filled }
        val data = WowIcons.bridgeData() ?: return null
        val entry = data.slots[slot]
        return entry != null && entry.hasIconSource
    }
}

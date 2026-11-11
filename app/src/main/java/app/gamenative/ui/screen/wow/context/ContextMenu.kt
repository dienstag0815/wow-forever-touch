package app.gamenative.ui.screen.wow.context

import app.gamenative.ui.components.AppText
import app.gamenative.R

/** Was beim Ablauf der Haltezeit aufgeht: das Radialmenü der App oder nichts. (Das Spielermenü des Spiels öffnet der Eintrag "Mehr ...".) */
enum class ContextOpen { NONE, RADIAL }

/**
 * Gezeichnetes Symbol eines festen Eintrags (die Kachel zeigt es statt Text; Zauber zeigen ihr Symbol aus dem Spiel, fehlt es, den Namen).
 * Die Zeichnung steht in [ContextGlyphDraw]; hier nur die Zuordnung, damit sie ohne Android getestet werden kann.
 */
enum class ContextGlyph {
    ATTACK, TARGET, LOOT, SKIN, TALK, MOVE, SPLIT, DISCARD, LINK, COMPARE, USE, EQUIP, UNEQUIP, SELL, TRANSFER,

    /** Spielermenü: Flüstern per Sprache (Mikrofon), Einladen, Gruppe verlassen, Folgen, Winken, Freund hinzufügen, Betrachten, Mehr ... */
    MIC, INVITE, LEAVE, FOLLOW, WAVE, FRIEND, INSPECT, MORE,
}

/** Was ein Eintrag des Kontextmenüs beim Loslassen darauf tut. */
enum class EntryAction {
    /** Linksklick an der Stelle des Fingers (Ziel wählen). */
    LEFT_CLICK,

    /** Rechtsklick an der Stelle des Fingers (Ansprechen, Plündern, Kürschnern, Anlegen, Benutzen, Verkaufen). */
    RIGHT_CLICK,

    /** Umschalt-Linksklick an der Stelle des Fingers (Stapel teilen). */
    SHIFT_LEFT_CLICK,

    /** Eine Taste des Addons ([ContextEntry.key]): Zauber der Liste, Automatischer Angriff, Flüstern ... */
    KEY,

    /** Der Platz [ContextEntry.barSlot] der Aktionsleiste 1: die Taste, die das Spiel dafür kennt. */
    BAR_SLOT,

    /** Eine Aktion des Spiels ([ContextEntry.wowAction], z. B. OPENALLBAGS): die Taste, die das Spiel dafür kennt (Fenster umschalten). */
    WOW_ACTION,

    /** Flüstern per Spracheingabe: die Taste des Addons öffnet "/w Name", dann startet die Spracheingabe der App. */
    WHISPER_VOICE,

    /** Flüstern mit Tastatur: die Taste des Addons öffnet "/w Name", dann geht die Bildschirmtastatur auf. */
    WHISPER_KEYBOARD,

    /** Ein Chat-Befehl ([ContextEntry.text], z. B. "/wave"), in den Chat getippt (Enter, Text, Enter). */
    CHAT_COMMAND,
}

/**
 * Ein Eintrag des Menüs.
 *
 * @property id        stabile Kennung ("loot", "skin", "talk", "target", "attack", "whisper" ... "spell:3", "bar:7")
 * @property enabled   false = nicht möglich (zu weit weg, Taste nicht scharf): wird abgedunkelt und löst nichts aus
 * @property dimmed    möglich, aber im Moment kaum sinnvoll (außer Reichweite, kein Mana, Abklingzeit): abgedunkelt, löst aber aus
 * @property clearsCursor vorher einen am Cursor hängenden Gegenstand zurücklegen (Tasche: Rechtsklick, Stapel teilen ...)
 * @property outOfRange der Zauber ist gegen das Ziel außer Reichweite (nur Sortierung: in Reichweite zuerst)
 * @property glyph     gezeichnetes Symbol (feste Einträge), null bei Zaubern: dort das Symbol des Zaubers, sonst der Name
 * @property wowAction Aktion des Spiels ([EntryAction.WOW_ACTION]); ihr Symbol kommt aus dem Spiel, sonst das Symbol der Aktion
 * @property text      Chat-Text ([EntryAction.CHAT_COMMAND])
 * @property ring      Ring des Menüs, auf dem der Eintrag steht: 0 = innen, 1 = außen (die Einträge stehen nach Ringen geordnet)
 */
data class ContextEntry(
    val id: String,
    val label: String,
    val action: EntryAction,
    val key: String? = null,
    val barSlot: Int = 0,
    val spellId: Int = 0,
    val enabled: Boolean = true,
    val dimmed: Boolean = false,
    val cooldown: Float = 0f,
    val outOfRange: Boolean = false,
    val clearsCursor: Boolean = false,
    val glyph: ContextGlyph? = null,
    val wowAction: String? = null,
    val text: String? = null,
    val ring: Int = 0,
)

/** Stand eines Platzes der Aktionsleiste 1 (aus dem Abklingzeit-Streifen und den Symboldaten). */
data class BarSlotState(
    val filled: Boolean,
    val name: String? = null,
    val notUsable: Boolean = false,
    val outOfRange: Boolean = false,
    val cooldown: Float = 0f,
)

/**
 * Die Einträge des Kontextmenüs je Lage der Einheit unter dem Finger. Rein, ohne Android, getestet.
 *
 * - Angreifbare Einheit (feindlich oder neutral/gelb, siehe [UnitInfo.hostile]), lebend: Angriffe (Modus Automatisch: Feind-Liste des
 *   Addons; Modus Aktionsleiste: die Plätze 1 bis 12), Automatischer Angriff, Ziel wählen. In Reichweite zuerst, dann in der Reihenfolge
 *   der Liste.
 * - Tote Einheit (kein Spieler): Plündern (Rechtsklick), Kürschnern nur wenn kürschnerbar (Rechtsklick), Ziel wählen.
 * - Anderer Spieler (außer lebende feindliche): das Mini-Menü ([playerMenu]); innen Flüstern (Sprache, Tastatur), Einladen (in meiner Gruppe: Gruppe verlassen), Folgen, Handeln,
 *   Winken, außen Freund hinzufügen, Betrachten, hilfreiche Zauber und "Mehr ...", das Spielermenü des Spiels ([usesPlayerMenu]).
 * - Nicht angreifbarer NPC (Händler, Questgeber ...): Ansprechen (Rechtsklick), Ziel wählen.
 * - Die Einheit bin ich selbst (Halten auf der eigenen Figur): das Selbst-Menü ([selfMenu]): Taschen, Charakter, Zauberbuch, Talente, Karte.
 * - Keine Einheit: kein Menü (leere Liste).
 * - Gegenstand in Tasche, Bank, Charakterfenster: [itemMenu], immer mit Benutzen.
 * - Quest im Questlog oder Quest-Tracker unter dem Zeiger: [questMenu] (verlinken, teilen, verfolgen, Mehr ...); geht wie der Gegenstand vor jeder Einheit.
 */
object ContextMenus {
    /** Zeile "Ziel wählen" (Linksklick; die App hat das Ziel beim Öffnen schon gewählt, der Eintrag macht es erneut). */
    private fun target() = ContextEntry("target", AppText.get(R.string.game_cmenu_target), EntryAction.LEFT_CLICK, glyph = ContextGlyph.TARGET)

    /**
     * Ob das Mini-Menü für Spieler aufgehen soll: ein anderer Spieler, außer er ist lebend und angreifbar (dann Angriffe). Dessen Eintrag
     * "Mehr ..." öffnet das Spielermenü des Spiels (das Menü, das ein Rechtsklick auf das Zielbild öffnet; die App wählt den Spieler beim
     * Öffnen per Linksklick als Ziel und drückt [ContextKeys.POPUP]); ein Gegenstand unter dem Zeiger geht vor.
     */
    @JvmStatic
    fun usesPlayerMenu(data: ContextData, mode: ContextMode): Boolean {
        if (mode == ContextMode.OFF || data.focus.hasMenu) return false
        val unit = data.unit
        if (!unit.exists || unit.isSelf || !unit.isPlayer) return false
        return unit.dead || !unit.hostile
    }

    /** Ob das Selbst-Menü aufgehen soll: der Finger hält auf der eigenen Figur (mouseover ist der Spieler), kein Gegenstand darunter. */
    @JvmStatic
    fun usesSelfMenu(data: ContextData, mode: ContextMode): Boolean {
        if (mode == ContextMode.OFF || data.focus.hasMenu) return false
        return data.unit.exists && data.unit.isSelf
    }

    /** Menü für die eigene Figur: die Fenster, die man am häufigsten öffnet; ausgelöst über die Tasten, die WoW dafür belegt hat. */
    @JvmStatic
    fun selfMenu(): List<ContextEntry> = listOf(
        window("bags", AppText.get(R.string.game_cmenu_bags), "OPENALLBAGS"),
        window("character", AppText.get(R.string.game_cmenu_character), "TOGGLECHARACTER0"),
        window("spellbook", AppText.get(R.string.game_cmenu_spellbook), "TOGGLESPELLBOOK"),
        window("talents", AppText.get(R.string.game_cmenu_talents), "TOGGLETALENTS"),
        window("map", AppText.get(R.string.game_cmenu_map), "TOGGLEWORLDMAP"),
    )

    private fun window(id: String, label: String, command: String) =
        ContextEntry(id, label, EntryAction.WOW_ACTION, wowAction = command)

    /** Wie weit die Einheit mindestens weg sein darf, damit die Aktion geht: abgedunkelt, wenn die Entfernung bekannt und zu groß ist. */
    private fun tooFar(unit: UnitInfo, needed: Int): Boolean = unit.reach != Reach.UNKNOWN && unit.reach < needed

    /** Höchstens so viele hilfreiche Zauber stehen im Mini-Menü (der äußere Ring bleibt übersichtlich). */
    const val PLAYER_SPELLS_MAX = 4

    /**
     * Mini-Menü für einen anderen Spieler (Freund, Gruppe, Neutraler, toter Spieler): Ring 0 innen die häufigen Dinge, Ring 1 außen die
     * Zusätze. Alles wirkt auf das Ziel (die App hat den Spieler beim Öffnen als Ziel gewählt). "Mehr ..." ist das Spielermenü des Spiels.
     */
    @JvmStatic
    fun playerMenu(
        data: ContextData,
        mode: ContextMode,
        bar: (Int) -> BarSlotState?,
        spellName: (Int) -> String?,
    ): List<ContextEntry> {
        val unit = data.unit
        val keys = data.keysArmed
        val out = ArrayList<ContextEntry>()
        out.add(
            ContextEntry(
                "whisper_voice", AppText.get(R.string.game_cmenu_whisper_voice), EntryAction.WHISPER_VOICE, key = ContextKeys.WHISPER, enabled = keys,
                glyph = ContextGlyph.MIC,
            ),
        )
        out.add(
            ContextEntry(
                "whisper_keys", AppText.get(R.string.game_cmenu_whisper_keys), EntryAction.WHISPER_KEYBOARD, key = ContextKeys.WHISPER, enabled = keys,
                glyph = ContextGlyph.TALK,
            ),
        )
        // Steht der Spieler in meiner Gruppe, steht an der Stelle von "Einladen" "Gruppe verlassen" (dieselbe Taste, das Addon entscheidet
        // am Ziel; gemeldet wird "in Gruppe" nur, wenn das Addon es meldet, sonst bleibt es beim Einladen).
        if (unit.inGroup) {
            out.add(
                ContextEntry(
                    "leave_group", AppText.get(R.string.game_cmenu_leave_group), EntryAction.KEY, key = ContextKeys.INVITE, enabled = keys,
                    glyph = ContextGlyph.LEAVE,
                ),
            )
        } else {
            out.add(
                ContextEntry(
                    "invite", AppText.get(R.string.game_cmenu_invite), EntryAction.KEY, key = ContextKeys.INVITE, enabled = keys,
                    glyph = ContextGlyph.INVITE,
                ),
            )
        }
        out.add(
            ContextEntry(
                "follow", AppText.get(R.string.game_cmenu_follow), EntryAction.KEY, key = ContextKeys.FOLLOW, enabled = keys, dimmed = tooFar(unit, Reach.MID),
                glyph = ContextGlyph.FOLLOW,
            ),
        )
        out.add(
            ContextEntry(
                "trade", AppText.get(R.string.game_cmenu_trade), EntryAction.KEY, key = ContextKeys.TRADE, enabled = keys, dimmed = tooFar(unit, Reach.NEAR),
                glyph = ContextGlyph.TRANSFER,
            ),
        )
        out.add(ContextEntry("wave", AppText.get(R.string.game_cmenu_wave), EntryAction.CHAT_COMMAND, text = "/wave", glyph = ContextGlyph.WAVE))
        // Außen: Zusätze
        out.add(
            ContextEntry(
                "friend", AppText.get(R.string.game_cmenu_friend), EntryAction.KEY, key = ContextKeys.FRIEND, enabled = keys, glyph = ContextGlyph.FRIEND, ring = 1,
            ),
        )
        out.add(
            ContextEntry(
                "inspect", AppText.get(R.string.game_cmenu_inspect), EntryAction.KEY, key = ContextKeys.INSPECT, enabled = keys, dimmed = tooFar(unit, Reach.MID),
                glyph = ContextGlyph.INSPECT, ring = 1,
            ),
        )
        for (spell in spells(data, mode, harmful = false, bar = bar, spellName = spellName).take(PLAYER_SPELLS_MAX)) {
            out.add(spell.copy(ring = 1))
        }
        out.add(
            ContextEntry("more", AppText.get(R.string.game_cmenu_more), EntryAction.KEY, key = ContextKeys.POPUP, enabled = keys, glyph = ContextGlyph.MORE, ring = 1),
        )
        return out
    }

    /**
     * Einträge je Ring (von innen nach außen) für Einträge, die nach Ringen geordnet stehen ([ContextEntry.ring]); null, wenn alles
     * auf einem Ring steht oder die Ordnung nicht stimmt (dann teilt das Layout selbst auf, [ContextMenuLayout.ringSizes]).
     */
    @JvmStatic
    fun ringCounts(entries: List<ContextEntry>): List<Int>? {
        if (entries.isEmpty() || entries.all { it.ring == 0 }) return null
        val counts = ArrayList<Int>()
        var ring = 0
        var n = 0
        for (entry in entries) {
            if (entry.ring < ring) return null
            while (entry.ring > ring) {
                if (n == 0) return null
                counts.add(n)
                n = 0
                ring++
            }
            n++
        }
        counts.add(n)
        return counts
    }

    @JvmStatic
    fun build(
        data: ContextData,
        mode: ContextMode,
        bar: (Int) -> BarSlotState?,
        spellName: (Int) -> String?,
    ): List<ContextEntry> {
        val unit = data.unit
        if (mode == ContextMode.OFF) return emptyList()
        // Der Gegenstand unter dem Zeiger geht vor: über einem Fenster gibt es keine Einheit unter dem Zeiger. Ebenso die Quest.
        if (data.focus.isItem) return itemMenu(data.focus, data.keysArmed)
        if (data.focus.isQuest) return questMenu(data.focus, data.keysArmed)
        if (!unit.exists) return emptyList()
        if (unit.isSelf) return selfMenu()
        if (usesPlayerMenu(data, mode)) return playerMenu(data, mode, bar, spellName)
        val keys = data.keysArmed
        val out = ArrayList<ContextEntry>()
        when {
            unit.dead -> {
                // (ein toter Spieler hat das Mini-Menü für Spieler)
                out.add(ContextEntry("loot", AppText.get(R.string.game_cmenu_loot), EntryAction.RIGHT_CLICK, glyph = ContextGlyph.LOOT))
                if (unit.skinnable) out.add(ContextEntry("skin", AppText.get(R.string.game_cmenu_skin), EntryAction.RIGHT_CLICK, glyph = ContextGlyph.SKIN))
                out.add(target())
            }
            unit.hostile -> {
                out.addAll(spells(data, mode, harmful = true, bar = bar, spellName = spellName))
                out.add(
                    ContextEntry(
                        "attack", AppText.get(R.string.game_cmenu_attack), EntryAction.KEY, key = ContextKeys.ATTACK, enabled = keys,
                        glyph = ContextGlyph.ATTACK,
                    ),
                )
                out.add(target())
            }
            else -> {
                out.add(ContextEntry("talk", AppText.get(R.string.game_cmenu_talk), EntryAction.RIGHT_CLICK, glyph = ContextGlyph.TALK))
                out.add(target())
            }
        }
        return out
    }

    /**
     * Menü für eine Quest im Questlog oder im Quest-Tracker (Finger auf dem Eintrag), in der Reihenfolge der Wichtigkeit: Im Chat verlinken,
     * Mit Gruppe teilen (abgedunkelt, wenn die Quest nicht teilbar ist), Verfolgen an/aus, Mehr ... Letzteres ist ein Rechtsklick an der
     * Fingerstelle: das eigene Menü des Spiels (Quest abbrechen usw. bleibt dort, mit der Rückfrage des Spiels). Kein eigener "Abbrechen".
     */
    @JvmStatic
    fun questMenu(focus: FocusInfo, keys: Boolean): List<ContextEntry> = listOf(
        ContextEntry("quest_link", AppText.get(R.string.game_cmenu_link), EntryAction.KEY, key = ContextKeys.QUEST_LINK, enabled = keys, glyph = ContextGlyph.LINK),
        ContextEntry(
            "quest_share", AppText.get(R.string.game_cmenu_quest_share), EntryAction.KEY, key = ContextKeys.QUEST_SHARE,
            enabled = keys && focus.shareable, glyph = ContextGlyph.INVITE,
        ),
        ContextEntry(
            "quest_track",
            AppText.get(if (focus.tracked) R.string.game_cmenu_quest_untrack else R.string.game_cmenu_quest_track),
            EntryAction.KEY, key = ContextKeys.QUEST_TRACK, enabled = keys, glyph = ContextGlyph.TARGET,
        ),
        ContextEntry("quest_more", AppText.get(R.string.game_cmenu_more), EntryAction.RIGHT_CLICK, glyph = ContextGlyph.MORE),
    )

    /**
     * Menü für einen Gegenstand in Tasche, Bank oder Charakterfenster: immer Benutzen (bei Ausrüstung Anlegen, beim Händler Verkaufen,
     * am Körper Ausziehen, in der Bank Umlagern) als echter Rechtsklick - ein am Cursor hängender Gegenstand wird vorher zurückgelegt -,
     * Verschieben (aufnehmen), Stapel teilen, Im Chat verlinken, Vergleichen, Wegwerfen (nur über die Rückfrage des Addons).
     * Gegenstände am Cursor: "Hier ablegen" statt "Verschieben". Die Erkennung "anlegbar" kann in manchen Clients fehlen: der Eintrag
     * bleibt dann "Benutzen" und der Rechtsklick tut das Richtige.
     */
    @JvmStatic
    fun itemMenu(focus: FocusInfo, keys: Boolean): List<ContextEntry> {
        val out = ArrayList<ContextEntry>()
        val (label, glyph) = when {
            focus.merchantOpen && focus.kind != FocusKind.EQUIPMENT -> AppText.get(R.string.game_cmenu_sell) to ContextGlyph.SELL
            focus.kind == FocusKind.EQUIPMENT -> AppText.get(R.string.game_cmenu_unequip) to ContextGlyph.UNEQUIP
            focus.kind == FocusKind.BANK -> AppText.get(R.string.game_cmenu_transfer) to ContextGlyph.TRANSFER
            focus.equippable -> AppText.get(R.string.game_cmenu_equip) to ContextGlyph.EQUIP
            else -> AppText.get(R.string.game_cmenu_use) to ContextGlyph.USE
        }
        out.add(ContextEntry("use", label, EntryAction.RIGHT_CLICK, clearsCursor = true, glyph = glyph))
        out.add(
            ContextEntry(
                "move", if (focus.cursorHasItem) AppText.get(R.string.game_cmenu_place) else AppText.get(R.string.game_cmenu_move), EntryAction.LEFT_CLICK,
                glyph = ContextGlyph.MOVE,
            ),
        )
        if (focus.stacked) {
            out.add(ContextEntry("split", AppText.get(R.string.game_cmenu_split), EntryAction.SHIFT_LEFT_CLICK, clearsCursor = true, glyph = ContextGlyph.SPLIT))
        }
        out.add(ContextEntry("link", AppText.get(R.string.game_cmenu_link), EntryAction.KEY, key = ContextKeys.LINK_ITEM, enabled = keys, glyph = ContextGlyph.LINK))
        out.add(ContextEntry("compare", AppText.get(R.string.game_cmenu_compare), EntryAction.KEY, key = ContextKeys.COMPARE, enabled = keys, glyph = ContextGlyph.COMPARE))
        out.add(
            ContextEntry(
                "discard", AppText.get(R.string.game_cmenu_discard), EntryAction.KEY, key = ContextKeys.DISCARD, enabled = keys, clearsCursor = true,
                glyph = ContextGlyph.DISCARD,
            ),
        )
        return out
    }

    /**
     * Zauber gegen einen Feind ([harmful]) oder für einen Freund, in Reichweite zuerst. Das Addon meldet nur Zauber, die auf eine andere
     * Einheit wirkbar sind (kein Aufspüren, keine Selbstzauber): was es nicht meldet, ist nicht gefüllt und fällt hier weg. Im Modus
     * Automatisch gelten die Zahlen (Reichweite, wirkbar) für das Ziel; ist die Einheit unter dem Finger noch nicht das Ziel (der Klick
     * zum Wählen ist unterwegs), stammen sie von einem anderen Ziel und zählen nicht: dann keine Zauber, bis das Ziel stimmt.
     */
    private fun spells(
        data: ContextData,
        mode: ContextMode,
        harmful: Boolean,
        bar: (Int) -> BarSlotState?,
        spellName: (Int) -> String?,
    ): List<ContextEntry> {
        val list = ArrayList<ContextEntry>()
        if (mode == ContextMode.AUTO) {
            if (!data.unit.isTarget) return list
            val source = if (harmful) data.enemySpells else data.friendSpells
            for ((index, entry) in source.withIndex()) {
                if (entry == null || !entry.filled) continue
                val key = ContextKeys.spellKey(index) ?: continue
                list.add(
                    ContextEntry(
                        id = "spell:$index",
                        label = spellName(entry.spellId) ?: AppText.get(R.string.game_cmenu_spell, entry.spellId),
                        action = EntryAction.KEY,
                        key = key,
                        spellId = entry.spellId,
                        enabled = data.keysArmed,
                        dimmed = entry.outOfRange || entry.notUsable || entry.cooling,
                        cooldown = entry.cooldown,
                        outOfRange = entry.outOfRange,
                    ),
                )
            }
        } else {
            for (slot in 1..ContextData.BAR_SLOTS) {
                val state = bar(slot) ?: continue
                if (!state.filled) continue
                val cls = data.barClass.getOrElse(slot - 1) { BarClass.NONE }
                // Unbekannt (Makro, Gegenstand ...) erscheint in beiden Menüs; sicher falsche Seite nicht.
                val fits = cls == BarClass.NONE || cls == BarClass.BOTH || (if (harmful) cls == BarClass.HARMFUL else cls == BarClass.HELPFUL)
                if (!fits) continue
                list.add(
                    ContextEntry(
                        id = "bar:$slot",
                        label = state.name ?: AppText.get(R.string.game_tpl_bar, slot),
                        action = EntryAction.BAR_SLOT,
                        barSlot = slot,
                        dimmed = state.outOfRange || state.notUsable || state.cooldown > 0f,
                        cooldown = state.cooldown,
                        outOfRange = state.outOfRange,
                    ),
                )
            }
        }
        // Stabile Sortierung (sortedBy ist stabil): in Reichweite vor außer Reichweite, sonst die Reihenfolge der Liste.
        return list.sortedBy { if (it.outOfRange) 1 else 0 }
    }

    /** Der Abklingzeit-Text eines Eintrags ("" ohne Abklingzeit), wie die Abklingzeit-Anzeige der Knöpfe ihn schreibt. */
    @JvmStatic
    fun cooldownText(entry: ContextEntry): String = app.gamenative.cooldown.CooldownMath.format(entry.cooldown)
}

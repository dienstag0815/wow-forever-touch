package app.gamenative.ui.screen.wow.chatbar

import app.gamenative.ui.components.AppText
import app.gamenative.R
import androidx.annotation.StringRes
import app.gamenative.cooldown.CooldownStrip
import app.gamenative.ui.screen.wow.WindowGeometry
import app.gamenative.ui.screen.wow.WindowSnapshot
import com.winlator.xserver.XKeycode

/**
 * Chat-Knöpfe: frei belegbare Touch-Knöpfe des Editors für den Chat des Spiels (Sprache, Senden, Tastatur, Kanal, Abbrechen), damit man
 * nach dem Verlinken eines Gegenstands oder beim Antworten nicht die Bildschirmtastatur braucht. Das Spiel zeigt den Chattext weiter
 * selbst (die App kann ihn nicht lesen); das Addon TouchForever meldet nur, wo das Chatfenster liegt und ob das Eingabefeld offen ist
 * (Chat-Abschnitt des Streifens, Addon-Stand 5, siehe [CooldownStrip]).
 *
 * Seit fold8.17 gibt es keine feste Leiste mehr: die Knöpfe sind gewöhnliche Knöpfe des Profils ([com.winlator.inputcontrols.ControlElement],
 * Feld `chatAction`), dazu je Knopf die Sichtbarkeit nach dem Chatzustand ([ChatShow]) und das Anheften an eine Ecke des Chatfensters
 * ([ChatAnchor]). Dieser Teil ist rein (ohne Android-Ansicht, getestet): was ein Knopf tut ([ChatBarLogic.plan]), wann er zu sehen ist
 * ([ChatBarLogic.isVisible]) und wo er bei verschobenem Chatfenster sitzt ([ChatBarLogic.anchoredPosition]).
 */
enum class ChatButton(val id: String, @StringRes val labelRes: Int, @StringRes val hintRes: Int) {
    /** Spracheingabe der App; bei offenem Eingabefeld wird der Text an die offene Zeile angehängt und gesendet. */
    MIC("voice", R.string.game_chatbtn_mic, R.string.game_chatbtn_mic_hint),

    /** Enter: öffnet das Eingabefeld oder sendet die offene Zeile. */
    ENTER("send", R.string.game_chatbtn_enter, R.string.game_chatbtn_enter_hint),

    /** Bildschirmtastatur der App. */
    KEYBOARD("keyboard", R.string.game_chatbtn_keyboard, R.string.game_chatbtn_keyboard_hint),

    /** Tab im Eingabefeld: nächster Kanal (Sagen, Gruppe, Schlachtzug, Gilde ...). */
    CHANNEL("channel", R.string.game_chatbtn_channel, R.string.game_chatbtn_channel_hint),

    /** Escape: schließt das Eingabefeld ohne zu senden. */
    CANCEL("cancel", R.string.game_chatbtn_cancel, R.string.game_chatbtn_cancel_hint),
    ;

    /** The name in the current app language. */
    val label: String get() = AppText.get(labelRes)

    /** The explanation in the current app language. */
    val hint: String get() = AppText.get(hintRes)

    companion object {
        /** Der Knopf zu einer gespeicherten Kennung, null bei unbekannter oder leerer. */
        @JvmStatic
        fun fromId(id: String?): ChatButton? = entries.firstOrNull { it.id == id }
    }
}

/** Wann ein Knopf zu sehen ist (Option "Zeigen" im Editor). Im Editor ist jeder Knopf immer sichtbar. */
enum class ChatShow(val key: String?, @StringRes val labelRes: Int) {
    ALWAYS(null, R.string.game_chatshow_always),
    CHAT("chat", R.string.game_chatshow_chat),
    BOX("box", R.string.game_chatshow_box),
    ;

    /** The name in the current app language. */
    val label: String get() = AppText.get(labelRes)

    companion object {
        @JvmStatic
        fun fromKey(key: String?): ChatShow = entries.firstOrNull { it.key != null && it.key == key } ?: ALWAYS

        /** Die zu speichernde Kennung: null (immer) bei unbekanntem Wert. */
        @JvmStatic
        fun normalize(key: String?): String? = fromKey(key).key
    }
}

/** Ecke des Chatrechtecks, an der ein Knopf hängt. */
enum class ChatAnchor(val key: String, @StringRes val labelRes: Int) {
    TOP_LEFT("tl", R.string.game_anchor_tl),
    TOP_RIGHT("tr", R.string.game_anchor_tr),
    BOTTOM_LEFT("bl", R.string.game_anchor_bl),
    BOTTOM_RIGHT("br", R.string.game_anchor_br),
    ;


    /** The name in the current app language. */
    val label: String get() = AppText.get(labelRes)

    companion object {
        @JvmStatic
        fun fromKey(key: String?): ChatAnchor? = entries.firstOrNull { it.key == key }

        /** Die zu speichernde Kennung: null bei unbekanntem oder leerem Wert. */
        @JvmStatic
        fun normalize(key: String?): String? = fromKey(key)?.key
    }
}

/** Was ein Chat-Knopf beim Antippen ausführt. */
sealed interface ChatPlan {
    /** Eine Taste im Spiel, kurz gedrückt und losgelassen. */
    data class Key(val key: XKeycode) : ChatPlan

    /** Spracheingabe der App; [appendToOpenBox]: die Chatzeile ist offen, der Text wird angehängt und gesendet. */
    data class Voice(val appendToOpenBox: Boolean) : ChatPlan

    /** Bildschirmtastatur der App. */
    data object Keyboard : ChatPlan
}

/** Was das Addon zuletzt über den Chat meldete, mit den Bildgrößen zur Lesezeit (für die Abbildung auf die Ansicht). */
class ChatBarState(
    /** Das Hauptchatfenster ist sichtbar (nicht ausgeblendet). */
    val windowVisible: Boolean,
    /** Ein Eingabefeld hat den Tastaturfokus. */
    val boxOpen: Boolean,
    /** Das Rechteck (ein Eintrag, Codes 0..255 des Spielbilds) oder null, wenn das Addon es nicht lesen konnte. */
    val area: WindowSnapshot?,
) {
    fun sameAs(other: ChatBarState): Boolean {
        if (windowVisible != other.windowVisible || boxOpen != other.boxOpen) return false
        val a = area
        val b = other.area
        if (a == null || b == null) return a == null && b == null
        return a.sameAs(b)
    }

    companion object {
        /** Aus einem gelesenen Streifen; null, wenn er keinen Chat-Abschnitt hat (älteres Addon) oder dieser beschädigt ist. */
        @JvmStatic
        fun from(decoded: CooldownStrip.Decoded, pictureWidth: Int, pictureHeight: Int, screenWidth: Int, screenHeight: Int): ChatBarState? {
            if (!decoded.chatKnown) return null
            val area = if (decoded.chatRectKnown) {
                WindowSnapshot(1, decoded.chatRect.copyOf(), pictureWidth, pictureHeight, screenWidth, screenHeight)
            } else {
                null
            }
            return ChatBarState(
                windowVisible = decoded.chatFlags and CooldownStrip.CF_VISIBLE != 0,
                boxOpen = decoded.chatFlags and CooldownStrip.CF_BOX != 0,
                area = area,
            )
        }
    }
}

object ChatBarLogic {
    /** Die Taste, die ein Knopf im Spiel drückt (null: Sprache und Tastatur sind Aktionen der App). */
    @JvmStatic
    fun keyFor(button: ChatButton): XKeycode? = when (button) {
        ChatButton.ENTER -> XKeycode.KEY_ENTER
        ChatButton.CHANNEL -> XKeycode.KEY_TAB
        ChatButton.CANCEL -> XKeycode.KEY_ESC
        ChatButton.MIC, ChatButton.KEYBOARD -> null
    }

    /** Was [button] bei diesem Chatzustand tut; [boxOpen]: die Chatzeile ist offen (unbekannt zählt als zu). */
    @JvmStatic
    fun plan(button: ChatButton, boxOpen: Boolean): ChatPlan = when (button) {
        ChatButton.MIC -> ChatPlan.Voice(boxOpen)
        ChatButton.KEYBOARD -> ChatPlan.Keyboard
        ChatButton.ENTER -> ChatPlan.Key(XKeycode.KEY_ENTER)
        ChatButton.CHANNEL -> ChatPlan.Key(XKeycode.KEY_TAB)
        ChatButton.CANCEL -> ChatPlan.Key(XKeycode.KEY_ESC)
    }

    /**
     * Ob ein Knopf mit der Option [show] (Kennung von [ChatShow], null = immer) jetzt zu sehen ist. Im Editor immer; ohne Meldung des Addons
     * (älterer Stand, Streifen nicht gelesen) ebenfalls, damit ein Knopf nie wegen fehlender Daten verschwindet. Eine offene Chatzeile zählt
     * auch dann als sichtbarer Chat, wenn das Fenster selbst ausgeblendet ist (man tippt gerade).
     */
    @JvmStatic
    fun isVisible(show: String?, state: ChatBarState?, editMode: Boolean): Boolean {
        if (editMode) return true
        val mode = ChatShow.fromKey(show)
        if (mode == ChatShow.ALWAYS || state == null) return true
        return when (mode) {
            ChatShow.CHAT -> state.windowVisible || state.boxOpen
            ChatShow.BOX -> state.boxOpen
            ChatShow.ALWAYS -> true
        }
    }

    /** Die Ecke [corner] (Kennung von [ChatAnchor]) des Rechtecks [rect] (links, oben, rechts, unten) als x, y; null bei unbekannter Ecke. */
    @JvmStatic
    fun cornerPoint(corner: String?, rect: FloatArray?): FloatArray? {
        if (rect == null || rect.size < 4) return null
        return when (ChatAnchor.fromKey(corner)) {
            ChatAnchor.TOP_LEFT -> floatArrayOf(rect[0], rect[1])
            ChatAnchor.TOP_RIGHT -> floatArrayOf(rect[2], rect[1])
            ChatAnchor.BOTTOM_LEFT -> floatArrayOf(rect[0], rect[3])
            ChatAnchor.BOTTOM_RIGHT -> floatArrayOf(rect[2], rect[3])
            null -> null
        }
    }

    /**
     * Wo die Mitte eines angehefteten Knopfes sitzt: an der Ecke [corner] des Chatrechtecks [rect] plus dem Abstand ([dx], [dy] als Anteil der
     * Ansichtsbreite und -höhe), immer in der Ansicht. Null ohne Rechteck, bei unbekannter Ecke oder leerer Ansicht (der Knopf bleibt dann an
     * seiner gespeicherten Stelle).
     */
    @JvmStatic
    fun anchoredPosition(corner: String?, rect: FloatArray?, dx: Float, dy: Float, viewWidth: Int, viewHeight: Int): FloatArray? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val point = cornerPoint(corner, rect) ?: return null
        val x = (point[0] + dx * viewWidth).coerceIn(0f, viewWidth.toFloat())
        val y = (point[1] + dy * viewHeight).coerceIn(0f, viewHeight.toFloat())
        return floatArrayOf(x, y)
    }

    /** Der Abstand ([x], [y] = Mitte des Knopfes) zur Ecke [corner] von [rect] als Anteil der Ansicht (dx, dy); null wie bei [anchoredPosition]. */
    @JvmStatic
    fun anchorOffset(corner: String?, rect: FloatArray?, x: Float, y: Float, viewWidth: Int, viewHeight: Int): FloatArray? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val point = cornerPoint(corner, rect) ?: return null
        return floatArrayOf((x - point[0]) / viewWidth, (y - point[1]) / viewHeight)
    }

    /** Das Rechteck des Chats in Pixeln der Ansicht (wie die Fensterrechtecke der Touch-Elemente), null wenn nicht bekannt. */
    @JvmStatic
    fun viewRect(state: ChatBarState?, viewWidth: Int, viewHeight: Int, fullscreen: Boolean): FloatArray? {
        val area = state?.area ?: return null
        val rects = WindowGeometry.toView(area, viewWidth, viewHeight, fullscreen)
        if (rects.count < 1 || rects.rects.size < 4) return null
        return rects.rects.copyOf(4)
    }
}

/**
 * Zustand des Chats für die Chat-Knöpfe, ein Objekt für den Prozess. Der Lesedienst ([app.gamenative.cooldown.CooldownService]) füttert ihn mit
 * jedem gelesenen Streifen ([onStrip], Present-Thread); die Steuerungsansicht meldet sich mit [attach] an (sie zeichnet bei jeder Änderung neu)
 * und sagt mit [setDemand], ob ein Knopf des Profils den Chat braucht. Gelesen wird nur, solange beides gilt ([needsSignal]).
 */
object ChatBarGate {
    @Volatile private var owner: Any? = null
    @Volatile private var changeListener: Runnable? = null
    @Volatile private var demand = false
    @Volatile private var current: ChatBarState? = null

    /** Die Ansicht [viewOwner] hängt an einer Spielansicht ([onChange] wird bei jeder Änderung des Zustands aufgerufen, auf dem Present-Thread). */
    @JvmStatic
    fun attach(viewOwner: Any, onChange: Runnable) {
        owner = viewOwner
        changeListener = onChange
    }

    /** Die Ansicht wurde abgebaut; gilt nur, wenn sie noch die angemeldete ist (eine neue Ansicht kann schon da sein). */
    @JvmStatic
    fun detach(viewOwner: Any) {
        if (owner !== viewOwner) return
        owner = null
        changeListener = null
        demand = false
        current = null
    }

    /** Ein Knopf des Profils braucht den Chatzustand (Sichtbarkeit, Anheften, Sprache bei offener Zeile). Ohne Bedarf wird nicht gelesen. */
    @JvmStatic
    fun setDemand(on: Boolean) {
        if (demand == on) return
        demand = on
        if (!on) current = null
    }

    @JvmStatic
    fun demanded(): Boolean = demand

    @JvmStatic
    fun state(): ChatBarState? = current

    /** Ob der Lesedienst für dieses Signal arbeiten soll, auch wenn die Abklingzeit-Anzeige aus ist. */
    @JvmStatic
    fun needsSignal(): Boolean = demand && owner != null

    /** Vom Lesedienst (Present-Thread): ein gelesener Streifen. */
    @JvmStatic
    fun onStrip(decoded: CooldownStrip.Decoded, pictureWidth: Int, pictureHeight: Int, screenWidth: Int, screenHeight: Int) {
        if (!needsSignal()) return
        val next = ChatBarState.from(decoded, pictureWidth, pictureHeight, screenWidth, screenHeight)
        val old = current
        val changed = if (old == null || next == null) old !== next else !old.sameAs(next)
        if (changed) {
            current = next
            changeListener?.run()
        }
    }

    /** Streifen verloren oder Funktion aus: der Chat ist unbekannt. */
    @JvmStatic
    fun clear() {
        if (current != null) {
            current = null
            changeListener?.run()
        }
    }
}

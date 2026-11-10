package app.gamenative.ui.screen.wow.chatbar

import android.os.Handler
import android.os.Looper
import app.gamenative.cooldown.CooldownService
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import timber.log.Timber

/**
 * Führt die Chat-Aktionen der Knöpfe aus ([ChatBarLogic.plan]): Enter, Tab (Kanal) und Escape sind Tasten ins Spiel, kurz gehalten wie beim
 * Tippen der Sprache; Sprache und Tastatur sind Aktionen der App. Ob die Chatzeile offen ist, sagt der Streifen ([CooldownService.chatBoxOpen]);
 * unbekannt zählt als zu.
 */
internal class ChatActionRunner(
    private val xServer: XServer,
    /** Öffnet die Bildschirmtastatur der App. */
    private val openKeyboard: () -> Unit,
    /** Startet die Spracheingabe; das Argument sagt, ob die Chatzeile gerade offen ist (dann wird an die Zeile angehängt). */
    private val startVoice: (chatBoxOpen: Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    /** Führt den Knopf mit der Kennung [actionId] aus (Hauptthread); unbekannte Kennungen tun nichts. */
    fun perform(actionId: String?) {
        val button = ChatButton.fromId(actionId) ?: return
        val boxOpen = CooldownService.chatBoxOpen() == true
        val plan = ChatBarLogic.plan(button, boxOpen)
        Timber.d("Chat-Knopf: %s -> %s", button.label, plan)
        when (plan) {
            is ChatPlan.Key -> tapKey(plan.key)
            is ChatPlan.Voice -> startVoice(plan.appendToOpenBox)
            ChatPlan.Keyboard -> openKeyboard()
        }
    }

    /** Drückt eine Taste im Spiel und lässt sie kurz darauf los (länger als ein Buchstabe: ein langsames Bild darf sie nicht verpassen). */
    private fun tapKey(key: XKeycode) {
        xServer.injectKeyPress(key, 0)
        main.postDelayed({ xServer.injectKeyRelease(key) }, KEY_HOLD_MS)
    }

    private companion object {
        /** Wie lange eine Taste gehalten wird (wie bei [app.gamenative.ui.screen.wow.voice.VoiceTextSender.ENTER_HOLD_MS]). */
        const val KEY_HOLD_MS = 40L
    }
}

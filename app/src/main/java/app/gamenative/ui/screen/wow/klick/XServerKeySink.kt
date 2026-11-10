package app.gamenative.ui.screen.wow.klick

import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer

/** Presses and releases the modifier keys on the X server. */
object XServerKeySink {
    /** Hooks [StickyModifiers] up to [xServer]; calling it again for the same server changes nothing. */
    @JvmStatic
    fun attach(xServer: XServer?) {
        if (xServer == null) return
        StickyModifiers.attach(xServer) { key, down -> send(xServer, key, down) }
    }

    @JvmStatic
    fun keycode(key: ModKey): XKeycode = when (key) {
        ModKey.SHIFT -> XKeycode.KEY_SHIFT_L
        ModKey.CTRL -> XKeycode.KEY_CTRL_L
        ModKey.ALT -> XKeycode.KEY_ALT_L
    }

    /** One key press or release, used by the click wheel for its own (not latched) modifier. */
    @JvmStatic
    fun send(xServer: XServer, key: ModKey, down: Boolean) {
        if (down) xServer.injectKeyPress(keycode(key)) else xServer.injectKeyRelease(keycode(key))
    }
}

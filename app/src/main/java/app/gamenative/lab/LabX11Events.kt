package app.gamenative.lab

import com.winlator.xconnector.XOutputStream
import com.winlator.xserver.events.Event

/**
 * `FocusIn` (X-Ereignis 9) für ein Fenster: so meldet ein X-Server einem Fenster, dass es den Eingabefokus bekommen hat. Die App sendet es
 * sonst nie (`WindowManager.setFocus` setzt nur die Zeiger). Nur das Labor schickt es, wenn der Nutzer in der Fensterliste ein Fenster
 * nach vorn holt, damit Wine das Fenster aktivieren kann. [detail] 3 = `NotifyNonlinear`, [mode] 0 = `NotifyNormal`.
 */
class LabFocusInEvent(private val windowId: Int, private val detail: Int = NOTIFY_NONLINEAR, private val mode: Int = NOTIFY_NORMAL) : Event(CODE) {
    override fun send(sequenceNumber: Short, outputStream: XOutputStream) {
        outputStream.lock().use {
            outputStream.writeByte(code)
            outputStream.writeByte(detail.toByte())
            outputStream.writeShort(sequenceNumber)
            outputStream.writeInt(windowId)
            outputStream.writeByte(mode.toByte())
            outputStream.writePad(23)
        }
    }

    companion object {
        const val CODE = 9
        const val NOTIFY_NORMAL = 0
        const val NOTIFY_NONLINEAR = 3
    }
}

/**
 * `ClientMessage` (X-Ereignis 33, Format 32) `WM_PROTOCOLS`/`WM_DELETE_WINDOW`: die Bitte an das Programm, das Fenster zu schließen (ICCCM 4.2.8.1).
 * Das Programm entscheidet selbst, ob es schließt. Nur an Fenster, die `WM_DELETE_WINDOW` in `WM_PROTOCOLS` nennen.
 */
class LabCloseRequestEvent(private val windowId: Int, private val protocolsAtom: Int, private val deleteAtom: Int) : Event(CODE) {
    override fun send(sequenceNumber: Short, outputStream: XOutputStream) {
        outputStream.lock().use {
            outputStream.writeByte(code)
            outputStream.writeByte(32.toByte())
            outputStream.writeShort(sequenceNumber)
            outputStream.writeInt(windowId)
            outputStream.writeInt(protocolsAtom)
            outputStream.writeInt(deleteAtom)
            outputStream.writeInt(0) // Zeitstempel: CurrentTime
            outputStream.writePad(12)
        }
    }

    companion object {
        const val CODE = 33
    }
}

package app.gamenative.ui.screen.wow.voice

import app.gamenative.R
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import app.gamenative.PluviaApp
import app.gamenative.cooldown.CooldownService
import app.gamenative.ui.util.SnackbarManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The "Sprache" button (Binding.VOICE_CHAT): a press starts Android speech recognition at once and shows what is heard
 * in a small overlay. After the final result the text waits in a preview (corrected words highlighted) until the
 * player chooses with the round symbol buttons: check mark types it into the WoW chat (Enter, text, Enter), repeat sign
 * (or the voice button again) starts a new recognition, cross forgets it, pencil opens the text in an edit field
 * (on-screen keyboard; check mark sends the edited text, cross goes back to the preview). Nothing is sent without that
 * choice.
 *
 * States: IDLE -> LISTENING (button again = stop listening early, tap on the overlay = cancel) -> PREVIEW (waits for a
 * choice) <-> EDITING -> IDLE; MESSAGE shows a failure for two seconds. If speech recognition is not available the on-screen
 * keyboard is opened instead. All methods run on the main thread; the typing itself runs on a worker thread because
 * it paces the key events.
 */
internal class VoiceChatController(
    private val context: Context,
    private val host: ViewGroup,
    private val xServer: XServer,
    private val openKeyboard: () -> Unit,
) {
    private enum class State { IDLE, LISTENING, PREVIEW, EDITING, MESSAGE }

    private val main = Handler(Looper.getMainLooper())
    private val overlay = VoiceOverlayView(context)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wow-voice-typing").apply { isDaemon = true }
    }

    private var state = State.IDLE
    private var recognizer: SpeechRecognizer? = null
    private var lastIntent: Intent? = null
    private var lastPreferredOffline = true
    private var vocabulary: VoiceVocabulary = VoiceVocabulary.EMPTY
    private var pendingText = ""
    private var pendingSpans: List<VoiceSpan> = emptyList()
    private var flow = VoicePreviewFlow.open("")

    /** The view that had the focus before the edit field took it (and whether it held the pointer capture). */
    private var focusBeforeEdit: View? = null
    private var captureBeforeEdit = false

    /** The pending text has an entry in the recognition log whose outcome is still open. */
    private var pendingLogged = false

    @Volatile
    private var destroyed = false

    /** Pointer id of a touch that started on the overlay (-1 = none) and the button it started on, if any. */
    private var trackedPointerId = -1
    private var trackedButton: VoiceOverlayView.Action? = null
    private var trackedOnEditor = false

    private val cancelRunnable = Runnable { cancel() }

    init {
        overlay.onAction = { action -> handleAction(action) }
        overlay.setOnClickListener { onOverlayBodyTap() }
        host.addView(overlay, overlay.hostLayoutParams())
    }

    /** Called by the button (any thread). */
    fun onVoiceButton() {
        main.post { if (!destroyed) handlePress() }
    }

    /**
     * Whisper by voice (mini menu of the long-press context menu, any thread): the addon has just opened the chat box with "/w Name ".
     * Starts listening; the confirmed text is appended to that box and sent (never typed into a box opened here, see
     * [VoiceTextSender.send]). Only from rest: while a recognition or a preview is going on the press is ignored.
     */
    fun onWhisperVoice() {
        main.post {
            if (destroyed || (state != State.IDLE && state != State.MESSAGE)) return@post
            handlePress()
            whisperAppend = state == State.LISTENING
        }
    }

    /** The next confirmed text goes to the open whisper box (see [onWhisperVoice]). */
    private var whisperAppend = false

    /**
     * Microphone button of the chat bar (any thread). With the chat edit box open (a linked item, a started whisper) the confirmed text is
     * appended to that line and sent (like [onWhisperVoice]); with the box closed it is the ordinary voice button. While a recognition or
     * a preview runs the press is the ordinary one: stops listening early / starts again.
     */
    fun onChatBarVoice(chatBoxOpen: Boolean) {
        main.post {
            if (destroyed) return@post
            if (chatBoxOpen && (state == State.IDLE || state == State.MESSAGE)) {
                handlePress()
                whisperAppend = state == State.LISTENING
            } else {
                handlePress()
            }
        }
    }

    /**
     * Touch routing of the game screen: it forwards touches by hand, so the overlay never sees them as a view. A touch
     * that starts on the visible overlay is ours until the finger is lifted: on a button it runs the action when the
     * finger is lifted over the same button, on the rest of the panel it cancels listening (in the preview it does
     * nothing). Everything else is left to the game. A second finger counts, too, because the movement stick may be held.
     */
    fun onHostTouchEvent(event: MotionEvent): Boolean {
        if (trackedPointerId < 0 && overlay.visibility != View.VISIBLE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (trackedPointerId >= 0) return true
                val index = event.actionIndex
                val x = rawX(event, index)
                val y = rawY(event, index)
                if (!isOverOverlay(x, y)) return false
                trackedPointerId = event.getPointerId(index)
                trackedButton = overlay.buttonAt(x, y)
                trackedOnEditor = trackedButton == null && overlay.isOverEditor(x, y)
                overlay.setPressedAction(trackedButton)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (trackedPointerId < 0) return false
                val index = event.actionIndex
                if (event.getPointerId(index) != trackedPointerId) return true
                val x = rawX(event, index)
                val y = rawY(event, index)
                val pressed = trackedButton
                val onEditor = trackedOnEditor
                trackedPointerId = -1
                trackedButton = null
                trackedOnEditor = false
                overlay.setPressedAction(null)
                if (pressed == null) {
                    if (onEditor && overlay.isOverEditor(x, y)) {
                        onEditorTap(x, y)
                    } else if (isOverOverlay(x, y)) {
                        onOverlayBodyTap()
                    }
                } else if (overlay.buttonAt(x, y) == pressed) {
                    handleAction(pressed)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (trackedPointerId < 0) return false
                trackedPointerId = -1
                trackedButton = null
                trackedOnEditor = false
                overlay.setPressedAction(null)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (trackedPointerId < 0) return false
                val tracked = trackedButton
                val index = event.findPointerIndex(trackedPointerId)
                if (tracked != null && index >= 0) {
                    // The pressed look follows the finger: off the button = not pressed.
                    val over = overlay.buttonAt(rawX(event, index), rawY(event, index))
                    overlay.setPressedAction(if (over == tracked) tracked else null)
                }
                return true
            }
            else -> return trackedPointerId >= 0
        }
    }

    /** Screen position of pointer [index]: the raw position of the first pointer plus the offset inside the view. */
    private fun rawX(event: MotionEvent, index: Int): Float = event.rawX + (event.getX(index) - event.getX(0))

    private fun rawY(event: MotionEvent, index: Int): Float = event.rawY + (event.getY(index) - event.getY(0))

    private fun isOverOverlay(rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        overlay.getLocationOnScreen(location)
        return rawX >= location[0] && rawX <= location[0] + overlay.width &&
            rawY >= location[1] && rawY <= location[1] + overlay.height
    }

    /** A tap on the panel outside the buttons: aborts listening and failure messages, the preview stays. */
    private fun onOverlayBodyTap() {
        if (state == State.LISTENING || state == State.MESSAGE) cancel()
    }

    /** A tap on the edit field: the keyboard comes back (it may have been closed) and the cursor goes to the finger. */
    private fun onEditorTap(rawX: Float, rawY: Float) {
        if (state != State.EDITING) return
        overlay.focusEditor()
        overlay.placeCursorAt(rawX, rawY)
    }

    private fun handleAction(action: VoiceOverlayView.Action) {
        when (action) {
            VoiceOverlayView.Action.SEND -> sendPending()
            VoiceOverlayView.Action.AGAIN -> again()
            VoiceOverlayView.Action.DISCARD -> discard()
            VoiceOverlayView.Action.EDIT -> startEditing()
        }
    }

    fun destroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
        stopEditing()
        if (pendingLogged) VoiceLog.setLastOutcome(context, VoiceLog.OUTCOME_DISCARDED)
        pendingLogged = false
        releaseRecognizer()
        executor.shutdownNow()
        host.removeView(overlay)
    }

    private fun handlePress() {
        when (state) {
            State.IDLE -> startListening()
            State.LISTENING -> recognizer?.stopListening()
            State.PREVIEW -> again()
            // Typing is going on: the voice button must not throw the text away.
            State.EDITING -> Unit
            State.MESSAGE -> {
                cancel()
                startListening()
            }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            toast(context.getString(R.string.game_voice_unavailable))
            openKeyboard()
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val activity = findActivity(context)
            if (activity != null) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_AUDIO)
            }
            toast(context.getString(R.string.game_voice_grant_mic))
            return
        }

        vocabulary = VoiceSettings.loadVocabulary(context)
        beginRecognition(preferOffline = true)
    }

    /** Neuer Erkenner und Start; [preferOffline] false = zweiter Versuch, wenn das lokale Sprachpaket fehlt (siehe [VoiceErrors]). */
    private fun beginRecognition(preferOffline: Boolean) {
        val language = VoiceSettings.language(context)
        val intent = recognizerIntent(language, vocabulary.biasStrings(VoiceSettings.customWords(context)), preferOffline)
        lastIntent = intent
        lastPreferredOffline = preferOffline

        releaseRecognizer()
        val created = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Timber.w(e, "VoiceChat: SpeechRecognizer could not be created")
            null
        }
        if (created == null) {
            toast(context.getString(R.string.game_voice_unavailable))
            openKeyboard()
            return
        }
        created.setRecognitionListener(listener)
        recognizer = created
        state = State.LISTENING
        overlay.showStatus(context.getString(R.string.game_voice_listening), "")
        try {
            created.startListening(intent)
        } catch (e: Exception) {
            Timber.w(e, "VoiceChat: startListening failed")
            fail(context.getString(R.string.game_voice_no_start))
        }
    }

    private fun recognizerIntent(language: VoiceLanguage, bias: List<String>, preferOffline: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Recognition on the device when an offline model is installed; the second attempt after a missing language pack goes online.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // The corrector looks at all hypotheses and prefers the one that holds the most WoW names.
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // The recognizer should not give up after a short pause while the player thinks.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && bias.isNotEmpty()) {
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(bias))
            }
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (state != State.LISTENING) return
            val text = firstResult(partialResults) ?: return
            val correction = VoiceCorrector.correctDetailed(text, vocabulary)
            overlay.setText(VoiceOverlayView.highlighted(correction.text, correction.spans))
        }

        override fun onResults(results: Bundle?) {
            if (state != State.LISTENING) return
            val candidates: List<String> = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val correction = VoiceCorrector.best(candidates, vocabulary)
            if (correction == null) {
                fail(context.getString(R.string.game_voice_nothing))
                return
            }
            showFinal(correction, candidates)
        }

        override fun onError(error: Int) {
            if (state != State.LISTENING) return
            Timber.d("VoiceChat: recognizer error %d (offline preferred: %b)", error, lastPreferredOffline)
            when (VoiceErrors.recovery(error, lastPreferredOffline)) {
                VoiceErrors.Recovery.RETRY_ONLINE -> main.post { if (!destroyed && state == State.LISTENING) beginRecognition(preferOffline = false) }
                VoiceErrors.Recovery.DOWNLOAD_MODEL -> {
                    val started = triggerModelDownload()
                    fail(context.getString(if (started) VoiceErrors.downloadingRes() else VoiceErrors.messageRes(error)))
                }
                VoiceErrors.Recovery.MESSAGE -> fail(errorMessage(error))
            }
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }

    private fun errorMessage(error: Int): String {
        val res = VoiceErrors.messageRes(error)
        return if (res == R.string.game_voice_error) context.getString(res, error) else context.getString(res)
    }

    /** Stößt den Download des Sprachpakets beim aktuellen Erkenner an (ab Android 13); false, wenn das nicht geht. */
    private fun triggerModelDownload(): Boolean {
        val current = recognizer
        val intent = lastIntent
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || current == null || intent == null) return false
        return try {
            current.triggerModelDownload(intent)
            true
        } catch (e: Exception) {
            Timber.w(e, "VoiceChat: language pack download could not be started")
            false
        }
    }

    private fun showFinal(correction: VoiceCorrection, candidates: List<String>) {
        val cleaned = VoiceKeys.sanitize(correction.text)
        if (cleaned.isEmpty()) {
            fail(context.getString(R.string.game_voice_nothing))
            return
        }
        pendingText = cleaned
        pendingSpans = correction.spansIn(cleaned)
        flow = VoicePreviewFlow.open(cleaned)
        state = State.PREVIEW
        releaseRecognizer()
        overlay.showPreview(cleaned, pendingSpans)
        // The log gets every final result, also the unchanged ones: those are the misses worth looking at.
        val others = candidates.map { it.trim() }.filter { it.isNotEmpty() && it != correction.heard }.joinToString(" | ")
        VoiceLog.append(context, VoiceLogEntry(correction.heard, cleaned, "", others))
        pendingLogged = true
    }

    private fun fail(message: String) {
        releaseRecognizer()
        state = State.MESSAGE
        pendingText = ""
        pendingLogged = false
        overlay.showStatus(context.getString(R.string.game_voice_title), message)
        main.removeCallbacks(cancelRunnable)
        main.postDelayed(cancelRunnable, MESSAGE_MS)
    }

    /** Leaves the preview: remembers what the player chose in the log, clears the text, hides the panel. */
    private fun finishPreview(outcome: String): String {
        val text = pendingText
        main.removeCallbacks(cancelRunnable)
        if (pendingLogged) VoiceLog.setLastOutcome(context, outcome)
        pendingLogged = false
        pendingText = ""
        pendingSpans = emptyList()
        stopEditing()
        state = State.IDLE
        overlay.hide()
        return text
    }

    /** Check mark: types the pending text (or, while editing, the edited text) into the chat. */
    private fun sendPending() {
        if (state != State.PREVIEW && state != State.EDITING) return
        val wasEditing = state == State.EDITING
        val whisper = whisperAppend
        val step = VoicePreviewFlow.send(flow, overlay.editedText()).step as? VoicePreviewStep.Send ?: return
        whisperAppend = false
        finishPreview(if (step.edited) VoiceLog.OUTCOME_SENT_EDITED else VoiceLog.OUTCOME_SENT)
        val text = step.text
        if (text.isEmpty() || destroyed) return
        val mode = VoiceSettings.umlautMode(context)
        try {
            executor.execute {
                // Coming from the edit field the keyboard is still closing and the focus is moving back to the game view:
                // keys sent in that moment have been seen to arrive before the game is ready for them.
                if (wasEditing) pause(EDIT_SETTLE_MS)
                val result = VoiceTextSender(xKeySink, isCancelled = { destroyed }, gate = chatGate).send(text, mode, appendToOpenBox = whisper)
                Timber.d("VoiceChat: send result %s", result)
                if (result == SendResult.CHAT_NOT_OPEN) {
                    // A whisper is not offered again as a plain line (a retry would go out as /say); the player whispers anew.
                    if (whisper) main.post { toast(context.getString(R.string.game_voice_whisper_closed)) } else main.post { restoreAfterFailedSend(text) }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "VoiceChat: typing could not be scheduled")
        }
    }

    /**
     * The chat box did not open (or closed while typing): nothing, or only the start of the line, reached the game. The text
     * comes back into the preview so the player can send it again; no letter was typed as a key binding.
     */
    private fun restoreAfterFailedSend(text: String) {
        if (destroyed || state != State.IDLE) return
        pendingText = text
        pendingSpans = emptyList()
        flow = VoicePreviewFlow.open(text)
        state = State.PREVIEW
        overlay.showPreview(text, pendingSpans)
        VoiceLog.setLastOutcome(context, VoiceLog.OUTCOME_NOT_SENT)
        pendingLogged = true
        toast(context.getString(R.string.game_voice_chat_closed_kept))
    }

    private fun pause(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Chat command buttons and emote wheel fields: types [text] (a leading "/" is kept, so "/dance" runs the emote)
     * into the WoW chat: Enter, text, Enter. Any thread; the typing runs on the worker thread, one line at a time.
     */
    fun sendChatCommand(text: String) {
        if (destroyed || text.isBlank()) return
        val mode = VoiceSettings.umlautMode(context)
        try {
            executor.execute {
                val result = VoiceTextSender(xKeySink, isCancelled = { destroyed }, gate = chatGate).send(text, mode, keepSlash = true)
                Timber.d("VoiceChat: chat command result %s", result)
                if (result == SendResult.CHAT_NOT_OPEN) main.post { toast(context.getString(R.string.game_voice_chat_closed)) }
            }
        } catch (e: Exception) {
            Timber.w(e, "VoiceChat: chat command could not be scheduled")
        }
    }

    /** "Nochmal": drops the pending text and listens again at once. */
    private fun again() {
        if (state != State.PREVIEW) return
        finishPreview(VoiceLog.OUTCOME_AGAIN)
        startListening()
    }

    /** Cross: in the preview it drops the pending text (nothing is sent), in the edit field it goes back to the preview. */
    private fun discard() {
        val result = VoicePreviewFlow.cancel(flow)
        if (result.step === VoicePreviewStep.Discard) {
            if (state == State.PREVIEW) {
                whisperAppend = false
                finishPreview(VoiceLog.OUTCOME_DISCARDED)
            }
        } else if (state == State.EDITING) {
            flow = result.state
            stopEditing()
            state = State.PREVIEW
            overlay.showPreview(pendingText, pendingSpans)
        }
    }

    /** Pencil: the recognized text moves into an edit field with the on-screen keyboard. */
    private fun startEditing() {
        if (state != State.PREVIEW) return
        flow = VoicePreviewFlow.edit(flow).state
        state = State.EDITING
        overlay.showEditor(pendingText)
        beginEditing()
    }

    /**
     * The edit field needs the keyboard focus. The overlay lives in the activity window (no separate window with
     * FLAG_NOT_FOCUSABLE), so it only has to take the focus from the game view: that also ends the pointer capture,
     * which is given back in [stopEditing]. [isEditing] makes the key handler of the game screen let every key go to
     * the field instead of into the game.
     */
    private fun beginEditing() {
        isEditing = true
        val pad = PluviaApp.touchpadView
        captureBeforeEdit = pad?.hasPointerCapture() == true
        if (captureBeforeEdit) pad?.releasePointerCapture()
        focusBeforeEdit = pad ?: host.rootView.findFocus()
        overlay.focusEditor()
    }

    /** Gives the focus (and the pointer capture) back to the game view and closes the keyboard. No-op if not editing. */
    private fun stopEditing() {
        if (!isEditing) return
        isEditing = false
        overlay.releaseEditorFocus()
        val target = focusBeforeEdit
        val capture = captureBeforeEdit
        focusBeforeEdit = null
        captureBeforeEdit = false
        if (target != null && !destroyed) {
            target.post {
                if (target.isAttachedToWindow && !isEditing) {
                    target.requestFocus()
                    if (capture) target.requestPointerCapture()
                }
            }
        }
    }

    /** Overlay tap while listening, a failure message running out: forget everything, nothing is sent. */
    private fun cancel() {
        main.removeCallbacks(cancelRunnable)
        if (pendingLogged) VoiceLog.setLastOutcome(context, VoiceLog.OUTCOME_DISCARDED)
        pendingLogged = false
        pendingText = ""
        pendingSpans = emptyList()
        whisperAppend = false
        stopEditing()
        releaseRecognizer()
        state = State.IDLE
        overlay.hide()
    }

    private fun releaseRecognizer() {
        val current = recognizer ?: return
        recognizer = null
        try {
            current.setRecognitionListener(null)
            current.cancel()
            current.destroy()
        } catch (e: Exception) {
            Timber.w(e, "VoiceChat: recognizer cleanup failed")
        }
    }

    /** The addon tells whether a chat edit box has the focus (strip info flag); null while that is not known. */
    private val chatGate = object : ChatGate {
        override fun prepare() = CooldownService.requestChatProbe()

        override fun isChatOpen(): Boolean? = CooldownService.chatBoxOpen()
    }

    private val xKeySink = object : KeySink {
        override fun press(key: XKeycode, keysym: Int) = xServer.injectKeyPress(key, keysym)

        override fun release(key: XKeycode) = xServer.injectKeyRelease(key)
    }

    private fun toast(message: String) {
        SnackbarManager.show(message)
    }

    private fun findActivity(start: Context): Activity? {
        var current: Context? = start
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    companion object {
        /**
         * The edit field of the voice preview has the keyboard: the game screen must not forward key events (the
         * on-screen keyboard counts as a virtual keyboard device) into the game while this is true.
         */
        @Volatile
        var isEditing = false
            private set

        /** Pause before typing when the text came from the edit field (keyboard closing, focus returning to the game). */
        const val EDIT_SETTLE_MS = 300L

        const val MESSAGE_MS = 2000L
        const val SILENCE_MS = 1800L
        const val REQUEST_CODE_AUDIO = 4711
    }
}

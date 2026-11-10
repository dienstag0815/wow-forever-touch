package app.gamenative.ui.screen.wow.voice

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.gamenative.R

/**
 * Panel at the top of the game: a small caption ("Hört zu…", "Sprachnachricht"), below it the recognized text in big
 * letters, live. In the preview it also shows round symbol buttons in the WoW style: cross (discard), repeat sign
 * (again), pencil (edit) and, the largest one, the check mark (send). Words the word list corrected are highlighted.
 * The pencil swaps the text for an edit field ([showEditor]); then only cross (back) and check mark (send) remain.
 *
 * The game screen forwards touches by hand, so the buttons normally never see a touch as a view: the owner asks
 * [buttonAt] for the screen position of a tap and shows the pressed look with [setPressedAction]. The buttons also
 * have click listeners ([onAction]) for the case that a touch does reach the view; the owner ignores a second trigger
 * of the same action.
 */
internal class VoiceOverlayView(context: Context) : LinearLayout(context) {
    enum class Action { SEND, AGAIN, DISCARD, EDIT }

    /** Called when a button was clicked as a view. */
    var onAction: ((Action) -> Unit)? = null

    private val captionView = TextView(context)
    private val textView = TextView(context)
    private val editor = EditText(context)
    private val hintView = TextView(context)
    private val divider = View(context)
    private val buttonRow = LinearLayout(context)

    private val discardButton = SymbolButton(
        context, R.drawable.ic_voice_close, context.getString(R.string.game_voice_discard), 52,
        Color.rgb(0x6A, 0x24, 0x2B), Color.rgb(0x35, 0x10, 0x15), Color.rgb(0xD2, 0x6A, 0x6A), Color.rgb(0xFF, 0xD3, 0xD3),
    )
    private val againButton = SymbolButton(
        context, R.drawable.ic_voice_repeat, context.getString(R.string.game_voice_again), 52,
        Color.rgb(0x22, 0x36, 0x52), Color.rgb(0x0E, 0x18, 0x28), GOLD, Color.rgb(0x9C, 0xCB, 0xFF),
    )
    private val editButton = SymbolButton(
        context, R.drawable.ic_voice_edit, context.getString(R.string.game_voice_edit), 52,
        Color.rgb(0x4A, 0x37, 0x18), Color.rgb(0x24, 0x1A, 0x0A), GOLD, Color.rgb(0xFF, 0xDD, 0x8F),
    )
    private val sendButton = SymbolButton(
        context, R.drawable.ic_voice_check, context.getString(R.string.game_voice_send), 64,
        Color.rgb(0x3A, 0x94, 0x40), Color.rgb(0x16, 0x4A, 0x1B), Color.rgb(0x8F, 0xD8, 0x94), Color.rgb(0xEA, 0xFF, 0xEB),
    )
    private val buttons = listOf(
        Action.DISCARD to discardButton,
        Action.AGAIN to againButton,
        Action.EDIT to editButton,
        Action.SEND to sendButton,
    )

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(18), dp(10), dp(18), dp(14))
        background = panelBackground()
        isClickable = true
        isFocusable = false
        elevation = dp(8).toFloat()

        captionView.apply {
            setTextColor(GOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        textView.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            maxLines = 6
            maxWidth = dp(520)
            minWidth = dp(240)
            gravity = Gravity.CENTER_HORIZONTAL
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
        }
        styleEditor()
        hintView.apply {
            setTextColor(Color.argb(0xC0, 0xC7, 0x9C, 0x6E))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = GONE
        }
        divider.setBackgroundColor(Color.argb(0x55, 0xC7, 0x9C, 0x6E))
        divider.visibility = GONE

        addView(captionView, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(
            textView,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) },
        )
        addView(
            editor,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) },
        )
        addView(
            hintView,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) },
        )
        addView(divider, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(10) })

        for ((action, button) in buttons) {
            button.setOnClickListener { onAction?.invoke(action) }
        }
        buttonRow.orientation = HORIZONTAL
        buttonRow.gravity = Gravity.CENTER_VERTICAL
        for ((index, entry) in buttons.withIndex()) {
            val button = entry.second
            buttonRow.addView(
                button,
                LayoutParams(button.sizePx, button.sizePx).apply { leftMargin = if (index == 0) 0 else dp(16) },
            )
        }
        buttonRow.visibility = GONE
        addView(
            buttonRow,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            },
        )
        visibility = GONE
    }

    private fun styleEditor() {
        editor.apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(0x80, 0xFF, 0xFF, 0xFF))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            // "Send" key on the keyboard; no full-screen keyboard (it would cover the whole panel on the Fold).
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            // inputType makes the field single-line; wrap long text over up to four lines instead.
            setHorizontallyScrolling(false)
            maxLines = 4
            minWidth = dp(300)
            maxWidth = dp(520)
            filters = arrayOf(InputFilter.LengthFilter(VoiceKeys.MAX_CHAT_LENGTH))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            highlightColor = Color.argb(0x66, 0xC7, 0x9C, 0x6E)
            background = GradientDrawable().apply {
                setColor(Color.argb(0xFF, 0x08, 0x0D, 0x16))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), GOLD)
            }
            isFocusable = true
            isFocusableInTouchMode = true
            visibility = GONE
            setOnEditorActionListener { _, actionId, event ->
                val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                    onAction?.invoke(Action.SEND)
                    true
                } else {
                    false
                }
            }
        }
        // The cursor would take the theme's accent color, which can be dark on this dark panel.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                editor.textCursorDrawable = GradientDrawable().apply {
                    setColor(HIGHLIGHT_TEXT)
                    setSize(dp(2), -1)
                }
            } catch (e: Exception) {
                // Default cursor is fine.
            }
        }
    }

    /** Dark glass panel with a gold double border, like a WoW frame. */
    private fun panelBackground(): Drawable {
        val outer = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(0xF2, 0x1C, 0x26, 0x38), Color.argb(0xF2, 0x0A, 0x10, 0x1A))).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(2), Color.rgb(0x8A, 0x6A, 0x3F))
        }
        val inner = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.argb(0xAA, 0xC7, 0x9C, 0x6E))
        }
        return LayerDrawable(arrayOf(outer, inner)).apply {
            setLayerInset(1, dp(4), dp(4), dp(4), dp(4))
        }
    }

    /** Layout parameters for the owner's container: centered at the top. */
    fun hostLayoutParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(18)
            leftMargin = dp(8)
            rightMargin = dp(8)
        }

    /** Caption and text without buttons: listening, or a short message. */
    fun showStatus(title: String, text: CharSequence) {
        captionView.text = title.uppercase()
        textView.text = text
        textView.visibility = VISIBLE
        showEditorField(false)
        hintView.visibility = GONE
        setButtonsVisible(false, false, false, false)
        visibility = VISIBLE
        bringToFront()
    }

    /** The recognized text with its corrected words highlighted, and the four symbol buttons. */
    fun showPreview(text: String, spans: List<VoiceSpan>) {
        captionView.text = context.getString(R.string.game_voice_caption_message)
        textView.text = highlighted(text, spans)
        textView.visibility = VISIBLE
        showEditorField(false)
        hintView.text = context.getString(R.string.game_voice_hint_corrected)
        hintView.visibility = if (spans.isEmpty()) GONE else VISIBLE
        setButtonsVisible(discard = true, again = true, edit = true, send = true)
        visibility = VISIBLE
        bringToFront()
    }

    /**
     * The edit field with [text], cursor at the end, no highlighting. Only cross (back) and check mark (send) stay.
     * Keyboard focus is requested separately with [focusEditor].
     */
    fun showEditor(text: String) {
        captionView.text = context.getString(R.string.game_voice_caption_edit)
        textView.visibility = GONE
        showEditorField(true)
        editor.setText(text)
        editor.setSelection(editor.text.length)
        hintView.text = context.getString(R.string.game_voice_hint_edit)
        hintView.visibility = VISIBLE
        setButtonsVisible(discard = true, again = false, edit = false, send = true)
        visibility = VISIBLE
        bringToFront()
    }

    private fun showEditorField(show: Boolean) {
        editor.visibility = if (show) VISIBLE else GONE
        if (!show) editor.setText("")
    }

    private fun setButtonsVisible(discard: Boolean, again: Boolean, edit: Boolean, send: Boolean) {
        discardButton.visibility = if (discard) VISIBLE else GONE
        againButton.visibility = if (again) VISIBLE else GONE
        editButton.visibility = if (edit) VISIBLE else GONE
        sendButton.visibility = if (send) VISIBLE else GONE
        val any = discard || again || edit || send
        buttonRow.visibility = if (any) VISIBLE else GONE
        divider.visibility = if (any) VISIBLE else GONE
        if (!any) setPressedAction(null)
    }

    fun setText(text: CharSequence) {
        textView.text = text
    }

    fun hide() {
        setButtonsVisible(false, false, false, false)
        showEditorField(false)
        visibility = GONE
    }

    /** What is in the edit field (empty while it is not shown). */
    fun editedText(): String = if (editor.visibility == VISIBLE) editor.text.toString() else ""

    /** Gives the edit field the keyboard focus and opens the on-screen keyboard. */
    fun focusEditor() {
        if (editor.visibility != VISIBLE) return
        editor.requestFocus()
        val show = Runnable {
            if (editor.visibility == VISIBLE && editor.windowToken != null) inputMethodManager()?.showSoftInput(editor, 0)
        }
        editor.post(show)
        // Some keyboards ignore the first request while the focus is still moving.
        editor.postDelayed(show, 250)
    }

    /** Closes the keyboard and takes the focus away from the edit field. */
    fun releaseEditorFocus() {
        inputMethodManager()?.hideSoftInputFromWindow(editor.windowToken, 0)
        editor.clearFocus()
    }

    private fun inputMethodManager(): InputMethodManager? =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    /** The edit field is under a screen position. */
    fun isOverEditor(rawX: Float, rawY: Float): Boolean =
        visibility == VISIBLE && editor.visibility == VISIBLE && contains(editor, rawX, rawY)

    /** Puts the cursor where the finger touched the edit field (the touch never reaches the field as a view). */
    fun placeCursorAt(rawX: Float, rawY: Float) {
        val location = IntArray(2)
        editor.getLocationOnScreen(location)
        val offset = editor.getOffsetForPosition(rawX - location[0], rawY - location[1])
        if (offset >= 0 && offset <= editor.text.length) editor.setSelection(offset)
    }

    /** Shows [action]'s button as pressed (null = none); the owner drives this because the touches do not reach the views. */
    fun setPressedAction(action: Action?) {
        for ((buttonAction, button) in buttons) button.isPressed = buttonAction == action
    }

    /** The button under a screen position, or null (also while the buttons are hidden). */
    fun buttonAt(rawX: Float, rawY: Float): Action? {
        if (visibility != VISIBLE || buttonRow.visibility != VISIBLE) return null
        // The big check mark first, so it wins where hit areas could touch.
        for ((action, button) in buttons.asReversed()) {
            if (button.visibility == VISIBLE && contains(button, rawX, rawY)) return action
        }
        return null
    }

    private fun contains(view: View, rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return rawX >= location[0] && rawX <= location[0] + view.width &&
            rawY >= location[1] && rawY <= location[1] + view.height
    }

    private fun dp(value: Int): Int = dpToPx(context, value)

    /**
     * Round WoW-style button: dark gradient disc, golden or colored double ring, a symbol on top, lighter and a bit
     * smaller while pressed.
     */
    private class SymbolButton(
        context: Context,
        iconRes: Int,
        description: String,
        sizeDp: Int,
        top: Int,
        bottom: Int,
        ring: Int,
        iconColor: Int,
    ) : ImageView(context) {
        val sizePx: Int = dpToPx(context, sizeDp)

        init {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(iconColor)
            scaleType = ScaleType.FIT_CENTER
            val pad = (sizePx * 0.27f).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = description
            isClickable = true
            isFocusable = false
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), disc(context, lighten(top), lighten(bottom), lighten(ring)))
                addState(intArrayOf(), disc(context, top, bottom, ring))
            }
            elevation = dpToPx(context, 4).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }

        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            val scale = if (pressed) 0.92f else 1f
            scaleX = scale
            scaleY = scale
        }

        private fun lighten(color: Int): Int = ColorUtils.blendARGB(color, Color.WHITE, 0.28f)

        private fun disc(context: Context, top: Int, bottom: Int, ring: Int): Drawable {
            val outer = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
                shape = GradientDrawable.OVAL
                setStroke(dpToPx(context, 2), ring)
            }
            val inner = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dpToPx(context, 1), ColorUtils.setAlphaComponent(ring, 0x70))
            }
            val inset = dpToPx(context, 4)
            return LayerDrawable(arrayOf(outer, inner)).apply { setLayerInset(1, inset, inset, inset, inset) }
        }
    }

    companion object {
        private val GOLD = Color.rgb(0xC7, 0x9C, 0x6E)
        private val HIGHLIGHT_TEXT = Color.rgb(0xFF, 0xD9, 0x8A)
        private val HIGHLIGHT_BACKGROUND = Color.argb(0x66, 0xC7, 0x9C, 0x6E)

        private fun dpToPx(context: Context, value: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

        /** [text] with bold, gold marked words at [spans]; spans outside the text are skipped. */
        fun highlighted(text: String, spans: List<VoiceSpan>): CharSequence {
            if (spans.isEmpty()) return text
            val styled = SpannableString(text)
            for (span in spans) {
                if (span.start < 0 || span.end > text.length || span.start >= span.end) continue
                styled.setSpan(BackgroundColorSpan(HIGHLIGHT_BACKGROUND), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(ForegroundColorSpan(HIGHLIGHT_TEXT), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(StyleSpan(Typeface.BOLD), span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return styled
        }
    }
}

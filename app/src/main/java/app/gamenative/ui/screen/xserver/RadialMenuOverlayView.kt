package app.gamenative.ui.screen.xserver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import app.gamenative.R
import app.gamenative.cooldown.CooldownDraw
import app.gamenative.cooldown.CooldownService
import app.gamenative.ui.touchstyle.RadialArc
import app.gamenative.ui.touchstyle.RadialOpen
import app.gamenative.ui.touchstyle.RadialPlacement
import app.gamenative.ui.touchstyle.RadialSegmentDraw
import app.gamenative.ui.touchstyle.RadialSlotVisibility
import app.gamenative.ui.touchstyle.TouchRole
import app.gamenative.ui.touchstyle.TouchStyle
import app.gamenative.ui.touchstyle.TouchStyleMath
import app.gamenative.ui.touchstyle.TouchStyleSettings
import app.gamenative.wowaction.BindingCaption
import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowSymbols
import app.gamenative.wowicons.WowIconDraw
import app.gamenative.wowicons.WowIcons
import com.winlator.inputcontrols.RadialMenu
import kotlin.math.min

/**
 * The radial menus of the buttons ("WoW menu" and the others), drawn as ring segments on an arc of [RadialArc.ARC_DEG] degrees: the same
 * building blocks and the same look as the context menu and the stick ring ([RadialArc], [RadialSegmentDraw]). The middle stays on the
 * pressed point and the arc turns towards the middle of the screen (top edge: opens downwards, left edge: to the right, corners:
 * diagonal), or opens the way the button is set to ([RadialOpen], [RadialPlacement]); only if no direction fits on the screen the
 * menu is moved. The fields keep their order (field 0 at the left end of the arc, then clockwise); more than
 * [RadialArc.FIRST_RING_CAP] fields continue on a second ring outside. Fields without an assignment, or on an action bar slot that is empty right now, are not drawn and cannot be
 * chosen ([RadialSlotVisibility]); their place stays free.
 */
internal class RadialMenuOverlayView(context: Context) : View(context) {
    interface Listener {
        fun onPointerPosition(x: Float, y: Float)
        fun onPointerRelease(commit: Boolean)
    }

    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale
    private val arcGeometry = RadialArc.Geometry.forDensity(density)

    /** Radius of the empty middle (touch there chooses nothing; the cancel label sits in it, drawn opaque so the button label below does not show). */
    val innerRadiusPx: Float = 38f * density
    private val textSizePx: Float = 12f * scaledDensity

    private var shape: RadialArc.Shape = buildShape(DEFAULT_SLOTS, 0f)

    /** Outer radius of the menu now shown; follows the number of fields, see [show]. */
    var radiusPx: Float = shape.outerRadius
        private set

    private val frame = RadialSegmentDraw()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private companion object {
        const val EMPTY_SLOT_LABEL = "–"
        const val DEFAULT_SLOTS = 6
    }

    var listener: Listener? = null
    private var menu: RadialMenu? = null
    private var centerX = 0f
    private var centerY = 0f

    /** Middle of the menu now shown (view pixels): the pressed point, moved only if no direction fits on the screen. */
    val centerXPx: Float get() = centerX
    val centerYPx: Float get() = centerY
    private var selectedIndex = -1
    private var primaryColor = Color.argb(102, 255, 255, 255)
    private var secondaryColor = Color.argb(102, 2, 119, 189)
    private val centerLabel = context.getString(R.string.radial_menu_cancel)

    init {
        visibility = GONE
        isClickable = true
        isFocusable = false
        setWillNotDraw(false)
    }

    private fun buildShape(count: Int, rotationDeg: Float): RadialArc.Shape =
        RadialArc.build(count, arcGeometry, RadialArc.Order.SEQUENTIAL, RadialArc.WIDE_STEP_DEG, RadialArc.ARC_DEG, rotationDeg)

    /**
     * Shows [menu] for the pressed point ([x], [y]) on a screen of [screenW] x [screenH]: picks the direction ([direction], automatic by
     * the screen area) and the middle ([centerXPx], [centerYPx]); the middle stays on the pressed point unless no direction fits.
     */
    fun show(menu: RadialMenu, x: Float, y: Float, screenW: Float, screenH: Float, direction: RadialOpen = RadialOpen.AUTO) {
        val count = menu.slots.size
        val choice = RadialPlacement.choose(
            { rotation -> buildShape(count, rotation) },
            x, y, screenW, screenH, 8f * density, innerRadiusPx, direction,
        )
        shape = choice.shape
        radiusPx = shape.outerRadius
        this.menu = menu
        centerX = choice.centerX
        centerY = choice.centerY
        selectedIndex = -1
        visibility = VISIBLE
        bringToFront()
        // Cooldowns of the action bar slots on the fields are redrawn while the wheel is open.
        CooldownService.register(this)
        invalidate()
    }

    fun hide() {
        visibility = GONE
        CooldownService.unregister(this)
        menu = null
        selectedIndex = -1
        invalidate()
    }

    fun selectedIndex(): Int = selectedIndex

    /**
     * The field in the direction ([dx], [dy]) from the menu centre, -1 for none: inside the empty middle, in the free wedge at the bottom,
     * or on a field that is not shown because it is empty. Same hit rules as the context menu ([RadialArc.indexAt]).
     */
    fun slotIndexAt(dx: Double, dy: Double): Int {
        val slots = menu?.slots ?: return -1
        val index = RadialArc.indexAt(shape, dx.toFloat(), dy.toFloat(), innerRadiusPx)
        if (index !in slots.indices) return -1
        return if (RadialSlotVisibility.isEmpty(slots[index])) -1 else index
    }

    fun setControlsStyle(primaryColor: Int, secondaryColor: Int) {
        if (this.primaryColor == primaryColor && this.secondaryColor == secondaryColor) return
        this.primaryColor = primaryColor
        this.secondaryColor = secondaryColor
        invalidate()
    }

    fun setSelectedIndex(index: Int) {
        if (selectedIndex == index) return
        selectedIndex = index
        invalidate()
    }

    override fun onDetachedFromWindow() {
        CooldownService.unregister(this)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            -> listener?.onPointerPosition(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                listener?.onPointerPosition(event.x, event.y)
                listener?.onPointerRelease(true)
            }
            MotionEvent.ACTION_CANCEL -> listener?.onPointerRelease(false)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val slots = menu?.slots.orEmpty()
        if (visibility != VISIBLE || slots.isEmpty()) return

        val style = TouchStyleSettings.style
        val cx = centerX
        val cy = centerY

        for (index in slots.indices) {
            val seg = shape.segments.getOrNull(index) ?: continue
            // Empty fields are not shown (and cannot be chosen); the other fields keep their place.
            if (RadialSlotVisibility.isEmpty(slots[index])) continue
            val active = index == selectedIndex
            frame.drawFrame(canvas, cx, cy, seg, active, 1f, density, primaryColor and 0xFFFFFF)
            drawSlotLabel(canvas, slots[index], seg, cx, cy, active, style)
        }

        // The empty middle with the cancel label; opaque, so the label of the button below it does not shine through.
        val fill = if (style == TouchStyle.CURRENT) 0x0A101C else TouchStyleMath.fillRgb(style, TouchRole.ROUND, false)
        fillPaint.color = TouchStyleMath.withAlpha(fill, 255)
        canvas.drawCircle(cx, cy, innerRadiusPx, fillPaint)
        strokePaint.color = TouchStyleMath.withAlpha(edgeRgb(style), 200)
        strokePaint.strokeWidth = 1.4f * density
        canvas.drawCircle(cx, cy, innerRadiusPx, strokePaint)

        textPaint.color = RadialSegmentDraw.textColor(style, false)
        textPaint.textSize = min(textSizePx * 1.05f, innerRadiusPx * 0.42f)
        textPaint.isFakeBoldText = true
        canvas.drawText(centerLabel, cx, centeredTextBaseline(cy, textPaint), textPaint)
    }

    private fun edgeRgb(style: TouchStyle): Int = when (style) {
        TouchStyle.CURRENT -> primaryColor and 0xFFFFFF
        TouchStyle.F -> TouchStyleMath.F_EDGE_LIGHT
        TouchStyle.G -> TouchStyleMath.G_BRONZE
    }

    /** Symbol (smaller than the segment) with the name small below it; same layout as the context menu ([RadialArc.contentFor]). */
    private fun drawSlotLabel(canvas: Canvas, slot: RadialMenu.Slot, seg: RadialArc.Segment, cx: Float, cy: Float, active: Boolean, style: TouchStyle) {
        val content = RadialArc.contentFor(seg, arcGeometry)
        val iconX = cx + content.iconX
        val iconY = cy + content.iconY
        val half = content.iconSize / 2f
        val labelX = cx + content.labelX
        val labelBase = cy + content.labelBaseline
        val textColor = RadialSegmentDraw.textColor(style, active)
        textPaint.color = textColor
        textPaint.isFakeBoldText = active
        val captionSize = textSizePx * 0.8f
        // Real icon of the action bar slot (from the addon) instead of symbol/emoji; the label stays below it.
        val addonIcon = WowIcons.bitmapFor(slot.wowAction)
        if (addonIcon != null) {
            val drawn = WowIconDraw.draw(
                canvas, addonIcon, iconX - half, iconY - half, iconX + half, iconY + half,
                WowIconDraw.KIND_ROUND, 5f * density, 255,
            )
            if (drawn) {
                CooldownDraw.draw(canvas, slot.wowAction, iconX - half, iconY - half, iconX + half, iconY + half, CooldownDraw.KIND_ROUND, 5f * density)
                textPaint.textSize = captionSize
                val own = slot.label.isNotBlank()
                // The own label, else the name of the spell on the slot (as written by the addon), else the action name.
                val name = if (own) slot.label else WowIcons.nameFor(slot.wowAction)
                    ?: WowActionCatalog.displayLabel(slot.label, slot.wowAction, slot.bindingCombo)
                canvas.drawText(fitLabel(name.take(14), content.labelWidth), labelX, labelBase, textPaint)
                return
            }
        }
        val wowAction = WowActionCatalog.find(slot.wowAction)
        if (wowAction != null) {
            // WoW action: own vector symbol with the (short) name below it.
            val emoji = BindingCaption.emojiForAction(slot.wowAction)
            if (emoji != null) {
                // The system font draws the emoji in colour; it takes the place of the vector symbol.
                textPaint.textSize = half * 1.9f
                canvas.drawText(emoji, iconX, centeredTextBaseline(iconY, textPaint), textPaint)
            } else {
                WowSymbols.draw(canvas, wowAction.symbol, iconX, iconY, half, textColor)
            }
            CooldownDraw.draw(canvas, slot.wowAction, iconX - half, iconY - half, iconX + half, iconY + half, CooldownDraw.KIND_ROUND, 5f * density)
            textPaint.textSize = captionSize
            val name = WowActionCatalog.displayLabel(slot.label, slot.wowAction, slot.bindingCombo)
            canvas.drawText(fitLabel(name.take(14), content.labelWidth), labelX, labelBase, textPaint)
            return
        }
        // Own label, else the short name of the key or chat command (see BindingCaption).
        textPaint.textSize = textSizePx
        val caption = BindingCaption.textForSlot(slot.label, null, slot.chatCommand, slot.bindingCombo)
        val label = if (slot.isEnabled) fitLabel(BindingCaption.fit(caption, 14), content.labelWidth) else EMPTY_SLOT_LABEL
        canvas.drawText(label, seg.midX() + cx, centeredTextBaseline(seg.midY() + cy, textPaint), textPaint)
    }

    private fun fitLabel(label: String, maxWidth: Float): String {
        var adjusted = label
        while (textPaint.measureText(adjusted) > maxWidth && adjusted.length > 3) {
            // Never cut an emoji (surrogate pair) in half.
            val drop = if (Character.isLowSurrogate(adjusted.last())) 2 else 1
            adjusted = adjusted.dropLast(drop)
        }
        return if (adjusted.length < label.length && adjusted.length > 1) {
            adjusted.dropLast(1) + "."
        } else {
            adjusted
        }
    }

    private fun centeredTextBaseline(centerY: Float, paint: Paint): Float {
        return centerY - (paint.descent() + paint.ascent()) * 0.5f
    }
}

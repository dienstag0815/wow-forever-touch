package app.gamenative.ui.screen.xserver.editor

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.winlator.inputcontrols.ControlElement
import com.winlator.widget.InputControlsView
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Touch handling and overlay drawing of the controls editor on the game picture (used by [InputControlsView] in edit mode):
 * tap = select, drag = move with snapping to grid and to edges/centers of other elements (guide lines, vibration when a line
 * catches), corner handle or two-finger pinch = size, pencil handle or double tap = settings.
 */
class EditGestureController(private val view: InputControlsView) {
    private enum class Mode { IDLE, PENDING, MOVE, RESIZE, PINCH, EDIT_TAP, EMPTY_PENDING, IGNORE }
    private enum class Handle { NONE, RESIZE, EDIT }

    private val density = view.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
    private val handleRadius = HANDLE_RADIUS_DP * density
    private val handleHitRadius = HANDLE_HIT_DP * density
    private val minTarget = MIN_TARGET_DP * density
    private val snapThreshold = SNAP_THRESHOLD_DP * density

    private var mode = Mode.IDLE
    private var target: ControlElement? = null
    private var downX = 0f
    private var downY = 0f
    private var grabDx = 0f
    private var grabDy = 0f
    private var startX = 0
    private var startY = 0
    private var startScale = 1f
    private var startDistance = 1f
    private var lastScale = 1f
    private var guides: List<SnapGuide> = emptyList()
    private var anchors: Set<SnapAnchor> = emptySet()

    private var lastTapElement: ControlElement? = null
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    private val accent = 0xFF4FC3F7.toInt()
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = accent
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFFFF4081.toInt()
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accent
    }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFFFFFFFF.toInt()
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFF10202A.toInt()
    }
    private val frameRect = RectF()

    /** Forgets the running gesture (edit mode ends or the profile is replaced). */
    fun reset() {
        clearGesture()
        lastTapElement = null
    }

    private fun clearGesture() {
        mode = Mode.IDLE
        target = null
        guides = emptyList()
        anchors = emptySet()
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp()
            MotionEvent.ACTION_UP -> finish(event, true)
            MotionEvent.ACTION_CANCEL -> finish(event, false)
        }
        return true
    }

    private fun onDown(event: MotionEvent) {
        clearGesture()
        val x = event.x
        val y = event.y
        downX = x
        downY = y

        val selected = view.selectedElement
        if (selected != null) {
            when (hitHandle(selected, x, y)) {
                Handle.RESIZE -> {
                    begin(selected)
                    mode = Mode.RESIZE
                    val cx = selected.x.toFloat()
                    val cy = selected.y.toFloat()
                    startDistance = max(hypot(x - cx, y - cy), 1f)
                    return
                }
                Handle.EDIT -> {
                    begin(selected)
                    mode = Mode.EDIT_TAP
                    return
                }
                Handle.NONE -> Unit
            }
        }

        val hit = hitElement(x, y, selected)
        if (hit == null) {
            // Tap on empty space deselects on release; a second finger (pinch) or a drag keeps the selection.
            mode = if (selected != null) Mode.EMPTY_PENDING else Mode.IGNORE
            return
        }
        if (hit !== selected) view.editorSelect(hit)
        begin(hit)
        grabDx = x - hit.x
        grabDy = y - hit.y
        mode = Mode.PENDING
    }

    private fun begin(element: ControlElement) {
        target = element
        startX = element.x.toInt()
        startY = element.y.toInt()
        startScale = element.scale
        lastScale = element.scale
    }

    private fun onPointerDown(event: MotionEvent) {
        if (event.pointerCount != 2) return
        var element = target
        if (element == null && mode == Mode.EMPTY_PENDING) {
            element = view.selectedElement
            if (element != null) begin(element)
        }
        if (element == null) {
            mode = Mode.IGNORE
            return
        }
        if (mode == Mode.PENDING || mode == Mode.MOVE || mode == Mode.EMPTY_PENDING || mode == Mode.PINCH) {
            mode = Mode.PINCH
            startScale = element.scale
            lastScale = element.scale
            startDistance = max(pointerDistance(event), 1f)
        }
    }

    private fun onPointerUp() {
        // Back to one finger after a pinch: wait until all fingers are up.
        if (mode == Mode.PINCH) mode = Mode.IGNORE
    }

    private fun onMove(event: MotionEvent) {
        when (mode) {
            Mode.PENDING -> {
                if (hypot(event.x - downX, event.y - downY) > touchSlop) {
                    mode = Mode.MOVE
                    moveTo(event)
                }
            }
            Mode.MOVE -> moveTo(event)
            Mode.RESIZE -> resizeTo(event)
            Mode.PINCH -> pinchTo(event)
            Mode.EMPTY_PENDING -> {
                if (hypot(event.x - downX, event.y - downY) > touchSlop) mode = Mode.IGNORE
            }
            else -> Unit
        }
    }

    private fun moveTo(event: MotionEvent) {
        val element = target ?: return
        val curX = element.x.toInt()
        val curY = element.y.toInt()
        val box = element.boundingBox
        val dx = (event.x - grabDx) - curX
        val dy = (event.y - grabDy) - curY
        val proposed = SnapRect(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy)

        val areaW = view.maxWidth.toFloat()
        val areaH = view.maxHeight.toFloat()
        val engine = SnapEngine(view.snappingSize.toFloat(), snapThreshold)
        val result = engine.snap(proposed, otherRects(element), areaW, areaH)

        val newX = (curX + (result.rect.left - box.left)).roundToInt().coerceIn(0, view.maxWidth)
        val newY = (curY + (result.rect.top - box.top)).roundToInt().coerceIn(0, view.maxHeight)
        element.setX(newX)
        element.setY(newY)

        if (result.anchors.isNotEmpty() && result.anchors != anchors) tick()
        anchors = result.anchors
        guides = result.guides
        view.invalidate()
    }

    private fun resizeTo(event: MotionEvent) {
        val element = target ?: return
        val distance = hypot(event.x - element.x, event.y - element.y)
        applyScale(element, ResizeMath.scaleFromDistance(startScale, startDistance, distance))
    }

    private fun pinchTo(event: MotionEvent) {
        val element = target ?: return
        if (event.pointerCount < 2) return
        applyScale(element, ResizeMath.scaleFromDistance(startScale, startDistance, pointerDistance(event)))
    }

    private fun applyScale(element: ControlElement, scale: Float) {
        if (scale == lastScale) return
        if (scale == 1.0f) tick()
        lastScale = scale
        element.setScale(scale)
        view.invalidate()
    }

    private fun pointerDistance(event: MotionEvent): Float =
        hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))

    private fun finish(event: MotionEvent, released: Boolean) {
        val element = target
        val currentMode = mode
        val changed = element != null &&
            (element.x.toInt() != startX || element.y.toInt() != startY || element.scale != startScale)

        mode = Mode.IDLE
        target = null
        guides = emptyList()
        anchors = emptySet()

        if (released) {
            when (currentMode) {
                Mode.PENDING -> if (element != null) registerTap(element, event)
                Mode.EDIT_TAP -> if (element != null && hypot(event.x - editCenterX(element), event.y - editCenterY(element)) <= handleHitRadius) {
                    view.editorRequestEdit(element)
                }
                Mode.EMPTY_PENDING -> view.editorSelect(null)
                else -> Unit
            }
        }
        view.invalidate()
        view.editorGestureFinished(changed)
    }

    private fun registerTap(element: ControlElement, event: MotionEvent) {
        val now = event.eventTime
        val sameSpot = hypot(event.x - lastTapX, event.y - lastTapY) <= touchSlop * 3f
        if (lastTapElement === element && now - lastTapTime <= DOUBLE_TAP_MS && sameSpot) {
            lastTapElement = null
            view.editorRequestEdit(element)
        } else {
            lastTapElement = element
            lastTapTime = now
            lastTapX = event.x
            lastTapY = event.y
        }
    }

    private fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun otherRects(except: ControlElement): List<SnapRect> {
        val profile = view.profile ?: return emptyList()
        val rects = ArrayList<SnapRect>()
        for (other in profile.elements) {
            if (other === except) continue
            val b = other.boundingBox
            rects.add(SnapRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()))
        }
        return rects
    }

    private fun hitElement(x: Float, y: Float, selected: ControlElement?): ControlElement? {
        if (selected != null && hitBox(selected).contains(x, y)) return selected
        val profile = view.profile ?: return null
        val elements = profile.elements
        // The last element is drawn on top.
        for (i in elements.indices.reversed()) {
            val element = elements[i]
            if (hitBox(element).contains(x, y)) return element
        }
        return null
    }

    /** Bounding box, enlarged to at least 48 dp so small buttons stay easy to hit with a thumb. */
    private fun hitBox(element: ControlElement): RectF {
        val b = element.boundingBox
        val padX = max(0f, (minTarget - b.width()) / 2f)
        val padY = max(0f, (minTarget - b.height()) / 2f)
        return RectF(b.left - padX, b.top - padY, b.right + padX, b.bottom + padY)
    }

    private fun resizeCenterX(element: ControlElement): Float =
        min(element.boundingBox.right.toFloat(), view.width - handleRadius)

    private fun resizeCenterY(element: ControlElement): Float =
        min(element.boundingBox.bottom.toFloat(), view.height - handleRadius)

    private fun editCenterX(element: ControlElement): Float =
        min(element.boundingBox.right.toFloat(), view.width - handleRadius)

    private fun editCenterY(element: ControlElement): Float =
        max(element.boundingBox.top.toFloat(), handleRadius)

    private fun hitHandle(element: ControlElement, x: Float, y: Float): Handle {
        val dResize = hypot(x - resizeCenterX(element), y - resizeCenterY(element))
        val dEdit = hypot(x - editCenterX(element), y - editCenterY(element))
        if (dResize > handleHitRadius && dEdit > handleHitRadius) return Handle.NONE
        return if (dResize <= dEdit) Handle.RESIZE else Handle.EDIT
    }

    /** Draws guide lines, selection frame and handles on top of the elements. */
    fun draw(canvas: Canvas) {
        val extend = 16f * density
        for (guide in guides) {
            if (guide.vertical) {
                canvas.drawLine(guide.position, guide.start - extend, guide.position, guide.end + extend, guidePaint)
            } else {
                canvas.drawLine(guide.start - extend, guide.position, guide.end + extend, guide.position, guidePaint)
            }
        }

        val element = view.selectedElement ?: return
        val box = element.boundingBox
        val pad = 4f * density
        frameRect.set(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad)
        canvas.drawRoundRect(frameRect, 8f * density, 8f * density, framePaint)

        if (mode == Mode.MOVE) return
        drawResizeHandle(canvas, resizeCenterX(element), resizeCenterY(element))
        drawEditHandle(canvas, editCenterX(element), editCenterY(element))
    }

    private fun drawResizeHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, handleRadius, handleFill)
        canvas.drawCircle(cx, cy, handleRadius, handleRing)
        val a = 5f * density
        val h = 3.5f * density
        // diagonal double arrow
        canvas.drawLine(cx - a, cy - a, cx + a, cy + a, iconPaint)
        canvas.drawLine(cx + a, cy + a, cx + a - h, cy + a, iconPaint)
        canvas.drawLine(cx + a, cy + a, cx + a, cy + a - h, iconPaint)
        canvas.drawLine(cx - a, cy - a, cx - a + h, cy - a, iconPaint)
        canvas.drawLine(cx - a, cy - a, cx - a, cy - a + h, iconPaint)
    }

    private fun drawEditHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, handleRadius, handleFill)
        canvas.drawCircle(cx, cy, handleRadius, handleRing)
        val a = 4.5f * density
        // pencil: shaft and tip
        canvas.drawLine(cx - a, cy + a, cx + a, cy - a, iconPaint)
        canvas.drawLine(cx - a, cy + a, cx - a, cy + a - 3f * density, iconPaint)
        canvas.drawLine(cx - a, cy + a, cx - a + 3f * density, cy + a, iconPaint)
    }

    companion object {
        const val HANDLE_RADIUS_DP = 13f
        const val HANDLE_HIT_DP = 26f
        const val MIN_TARGET_DP = 48f
        const val SNAP_THRESHOLD_DP = 8f
        const val DOUBLE_TAP_MS = 350L
    }
}

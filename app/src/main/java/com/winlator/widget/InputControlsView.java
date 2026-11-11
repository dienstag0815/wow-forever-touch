package com.winlator.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import androidx.compose.ui.input.pointer.PointerIcon;
import androidx.core.graphics.ColorUtils;

import app.gamenative.R;
import app.gamenative.data.GyroSettings;
import app.gamenative.data.ShooterModeConfig;
import app.gamenative.cooldown.CooldownService;
import app.gamenative.ui.screen.wow.LoginTouchGate;
import app.gamenative.ui.screen.wow.WindowGate;
import app.gamenative.ui.screen.wow.WindowGeometry;
import app.gamenative.ui.screen.wow.WindowHideMode;
import app.gamenative.ui.screen.wow.WindowHideRule;
import app.gamenative.ui.screen.wow.WindowHideTracker;
import app.gamenative.ui.screen.wow.WindowViewRects;
import app.gamenative.ui.screen.wow.chatbar.ChatBarGate;
import app.gamenative.ui.screen.wow.chatbar.ChatBarLogic;
import app.gamenative.ui.screen.xserver.editor.EditGestureController;
import app.gamenative.ui.screen.xserver.editor.EditorListener;
import app.gamenative.ui.touchstyle.TouchStyleDraw;
import app.gamenative.ui.touchstyle.TouchStyleSettings;
import app.gamenative.wowaction.ReloadGuard;
import com.winlator.inputcontrols.Binding;
import com.winlator.inputcontrols.BindingCombo;
import com.winlator.inputcontrols.ControlElement;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.ExternalControllerBinding;
import com.winlator.inputcontrols.GamepadState;
import com.winlator.math.Mathf;
import com.winlator.winhandler.MouseEventFlags;
import com.winlator.winhandler.WinHandler;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class InputControlsView extends View {
    private static final long SHOOTER_SPRINT_TAP_DURATION_MS = 120;
    public static final float DEFAULT_OVERLAY_OPACITY = 0.4f;
    private static final int SEQUENCE_PRESS_MS = 80;
    private boolean editMode = false;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final ColorFilter colorFilter = new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN);
    private boolean readyToDraw = false;
    // Login, Warteschlange, Charakterauswahl: Touch-Steuerung unsichtbar, Berührungen wirken als Maus (LoginTouchGate).
    private boolean loginHidden = false;
    // Offene Spielfenster (Taschen, Karte, Menüs): Elemente darunter verschwinden (WindowGate), Einblenden mit Verzögerung.
    private final WindowHideTracker windowTracker = new WindowHideTracker(WindowHideTracker.SHOW_DELAY_MS);
    private final Set<BindingCombo> activeSequenceCombos = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Binding, Integer> activeSequenceBindings = new EnumMap<>(Binding.class);
    private final Map<Object, Binding> activeSequenceSources = new IdentityHashMap<>();
    private int sequenceGeneration = 0;
    private int snappingSize;
    private ControlElement selectedElement;
    // Direct editor on the game picture (touch handling and handles/guides drawing) and the editor UI behind it.
    private EditGestureController editController;
    private EditorListener editorListener;
    private ControlsProfile profile;
    // Retained while the overlay controls are hidden so gyro can keep targeting the active gamepad.
    private ControlsProfile gyroProfile;
    private float overlayOpacity = DEFAULT_OVERLAY_OPACITY;
    private TouchpadView touchpadView;
    private XServer xServer;
    private final Bitmap[] icons = new Bitmap[40];
    private Timer mouseMoveTimer;
    private final PointF mouseMoveOffset = new PointF();
    private boolean showTouchscreenControls = true;

    // Shooter mode state
    private boolean shooterModeActive = false;
    // Dynamic joystick (left side in shooter mode)
    private int joystickPointerId = -1;
    private float joystickCenterX, joystickCenterY;
    private float joystickCurrentX, joystickCurrentY;
    private final boolean[] joystickStates = new boolean[4];
    // Look-around (right side or fire button look-through)
    private int lookPointerId = -1;
    private float lookLastX, lookLastY;
    private float lookAccumX, lookAccumY;
    private float lookDeadzoneAccumX, lookDeadzoneAccumY;
    private float lookSmoothX, lookSmoothY;
    private ControlElement lookFireElement = null;
    // Delays button-originated look/move until the touch becomes an intentional drag.
    private final SparseArray<PendingButtonLook> pendingButtonLooks = new SparseArray<>();
    // General look-through owns one movement-only pointer and quarantines background pointers.
    private final LookThroughPointerState lookThroughPointerState = new LookThroughPointerState();
    // Pointers whose DOWN event was delivered to TouchpadView's normal gesture system.
    private final SparseBooleanArray touchpadPointers = new SparseBooleanArray();
    private final int lookThroughTouchSlop;
    // Right dynamic joystick (for gamepad_right_stick look type)
    private int rightJoystickPointerId = -1;
    private float rightJoystickCenterX, rightJoystickCenterY;
    private float rightJoystickCurrentX, rightJoystickCurrentY;
    private final boolean[] rightJoystickStates = new boolean[4];
    private boolean shooterSprintActive = false;
    private Binding shooterSprintTapBinding = Binding.NONE;
    private final Runnable shooterSprintTapReleaseRunnable = new Runnable() {
        @Override
        public void run() {
            releaseShooterSprintTap();
        }
    };
    private ShooterModeConfig shooterModeConfig = ShooterModeConfig.fromJson("");

    // Container-level shooter mode (auto-replaces STICK elements)
    private boolean containerShooterMode = false;
    private boolean containerShooterModeRuntime = false; // runtime toggle state
    private boolean wowContainer = false; // WoW-Forever-Container: runtime toggle never shown

    private final GyroController gyroController;
    private volatile boolean gyroEnabled;
    private volatile int gyroMode = GyroSettings.MODE_DISABLED;
    private volatile boolean gyroActive;
    private float baseThumbLX;
    private float baseThumbLY;
    private float baseThumbRX;
    private float baseThumbRY;
    private float gyroThumbLX;
    private float gyroThumbLY;
    private float gyroThumbRX;
    private float gyroThumbRY;

    // Callback invoked when the SHOW_KEYBOARD binding is triggered
    private Runnable showKeyboardCallback;
    // Tracks whether SHOW_KEYBOARD is currently held, so the callback fires once per press (rising edge only)
    private boolean showKeyboardPressed;
    // Callback for the VOICE_CHAT binding (speech to WoW chat), fires once per press
    private Runnable voiceChatCallback;
    private boolean voiceChatPressed;
    // Callback for chat command buttons and radial fields (e.g. emotes "/dance"): types the text into the WoW chat
    private ChatCommandCallback chatCommandCallback;
    private ChatActionCallback chatActionCallback;
    // Whether some button of the profile needs the chat state (reported to ChatBarGate so the strip is read only then)
    private boolean chatDemand = false;
    // Callback for the OPEN_NAVIGATION_MENU binding (opens the quick menu with settings/editor), fires once per press
    private Runnable openNavigationMenuCallback;
    private boolean openNavigationMenuPressed;
    // Callback for the OPEN_CONTROLS_EDITOR binding (starts the edit mode of the on-screen controls), fires once per press
    private Runnable openControlsEditorCallback;
    private boolean openControlsEditorPressed;
    private RadialMenuListener radialMenuListener;
    private boolean radialMenuTouchActive;
    private int radialMenuTouchPointerId = MotionEvent.INVALID_POINTER_ID;

    public interface ChatCommandCallback {
        void onChatCommand(String text);
    }

    /** A chat button was pressed ([actionId]: id of a ChatButton: voice, send, keyboard, channel, cancel). */
    public interface ChatActionCallback {
        void onChatAction(String actionId);
    }

    public interface RadialMenuListener {
        void onRadialMenuTouchStart(int pointerId, float x, float y);
        /** Touch start of a radial button with its own menu; a null/unknown menuId means the default menu. */
        default void onRadialMenuTouchStart(int pointerId, float x, float y, String menuId) {
            onRadialMenuTouchStart(pointerId, x, y);
        }
        /** As above, with the "opens to" setting of the button (null = automatic, else "up", "down", "left", "right"). */
        default void onRadialMenuTouchStart(int pointerId, float x, float y, String menuId, String direction) {
            onRadialMenuTouchStart(pointerId, x, y, menuId);
        }
        void onRadialMenuTouchMove(int pointerId, float x, float y);
        void onRadialMenuTouchEnd(int pointerId, boolean commit);
        void onRadialMenuButtonStateChanged(boolean isDown);
    }

    private static class PendingButtonLook {
        private final float startX;
        private final float startY;
        private final ControlElement element;
        private final boolean leftSide;

        private PendingButtonLook(float startX, float startY, ControlElement element, boolean leftSide) {
            this.startX = startX;
            this.startY = startY;
            this.element = element;
            this.leftSide = leftSide;
        }
    }

    @SuppressLint("ResourceType")
    public InputControlsView(Context context) {
        super(context);
        LoginTouchGate.reset(context); // new game session: hidden until the world is reached (if the setting is on)
        loginHidden = LoginTouchGate.isHidden();
        WindowGate.init(context);
        TouchStyleSettings.init(context); // look of the touch elements (style and contrast), read once
        TouchStyleDraw.resetSession(SystemClock.uptimeMillis()); // elements start active, rest after 4 s without touch (style G)
        CooldownService.newSession(); // a new game session: the check of the addon build starts again
        gyroController = new GyroController(context, new GyroController.Listener() {
            @Override
            public void onGyroMouseDelta(int x, int y) {
                updateGyroMouse(x, y);
            }

            @Override
            public void onGyroStick(float x, float y, boolean rightStick) {
                updateGyroStick(rightStick, x, y);
            }

            @Override
            public void onGyroActiveChanged(boolean active) {
                gyroActive = active;
                postInvalidateOnAnimation();
            }
        });
        lookThroughTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDefaultFocusHighlightEnabled(false);
        setBackgroundColor(0x00000000);
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        if (this.editMode != editMode) cancelTouchRouting();
        this.editMode = editMode;
        openControlsEditorPressed = false;
        if (!editMode) {
            // Leaving the editor: no element stays highlighted as selected.
            deselectAllElements();
            if (editController != null) editController.reset();
            if (editorListener != null) editorListener.onSelectionChanged(null);
        }
        gyroController.setEditMode(editMode);
        invalidate(); // Trigger redraw to show/hide grid background immediately
    }

    /** The touch controls are hidden by the login rule right now (never while the layout editor is open). */
    private boolean isLoginHiddenNow() {
        return !editMode && LoginTouchGate.isHidden();
    }

    /** Follows the gate on the main thread; when it flips, running touches are cancelled (no stuck button or stick). */
    private boolean updateLoginHidden() {
        boolean hidden = isLoginHiddenNow();
        if (hidden != loginHidden) {
            loginHidden = hidden;
            cancelTouchRouting();
        }
        return hidden;
    }

    /**
     * With the login rule active only the Escape and the keyboard button stay (LoginTouchGate.keepInLogin: decided by the
     * button's binding, so it holds for every profile); everything else is neither drawn nor touchable, and a touch that
     * hits nothing goes to the touchpad as a plain mouse touch, as always.
     */
    private boolean isHiddenInLogin(ControlElement element) {
        return isLoginHiddenNow() && !LoginTouchGate.keepInLogin(element.getType(), element.getBindingAt(0));
    }

    private boolean isRendererFullscreen() {
        return xServer != null && xServer.getRenderer() != null && xServer.getRenderer().isFullscreen();
    }

    /** The open game windows in view pixels, or null when none are reported (or the setting is off, or the editor is open). */
    private WindowViewRects currentWindowRects() {
        if (editMode || WindowGate.mode() == WindowHideMode.OFF) return null;
        return WindowGate.viewRects(getWidth(), getHeight(), isRendererFullscreen());
    }

    /**
     * An open game window hides this element (setting "Touch-Elemente über Spielfenstern ausblenden"): drawn no more and not
     * touchable, a touch there goes to the touchpad as a plain mouse touch. Escape and the keyboard button always stay.
     * Hiding is at once, showing again only after WindowHideTracker.SHOW_DELAY_MS without overlap; a finger that lies on
     * the element keeps it alive until it is lifted (no running touch is cancelled).
     */
    private boolean isHiddenByWindow(ControlElement element, WindowViewRects windows, long nowMs, int safeKeepers) {
        WindowHideMode mode = WindowGate.mode();
        if (editMode || mode == WindowHideMode.OFF) {
            windowTracker.reset();
            return false;
        }
        // Per button "Bei offenen Fenstern" (automatic = the setting; keep / hide win over it, see WindowHideRule.hides).
        boolean essential = WindowHideRule.alwaysKept(element.getType(), element.getBindingAt(0));
        boolean covered = false;
        if (windows != null) {
            Rect box = element.getBoundingBox();
            float share = mode == WindowHideMode.COVERED && !essential
                    ? WindowGeometry.coveredShare(box.left, box.top, box.right, box.bottom, windows)
                    : 0f;
            covered = WindowHideRule.hides(element.getWindowRule(), mode, share, windows.count, essential, safeKeepers);
        }
        if (!covered && essential) return false;
        return windowTracker.hidden(element, covered, element.isTouchHeld(), nowMs);
    }

    /**
     * How many Escape/keyboard buttons stay visible over open windows because they are not set to "Ausblenden": the last one may not be hidden
     * (the player must not lock himself out, see WindowHideRule.hides).
     */
    private int countWindowKeepers() {
        int count = 0;
        if (profile == null) return 0;
        for (ControlElement element : profile.getElements()) {
            if (WindowHideRule.alwaysKept(element.getType(), element.getBindingAt(0))
                    && !"hide".equals(element.getWindowRule())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Keeps the chat buttons in step with the game's chat: tells the gate whether any button needs the chat state (the strip is read only
     * then) and moves buttons that are pinned to the chat window with it. Not in the editor: there buttons stay where they are drawn and the
     * offset is measured when the profile is saved (ControlElement.toJSONObject).
     */
    private void syncChatLinks() {
        boolean demand = false;
        float[] rect = null;
        boolean rectRead = false;
        for (ControlElement element : profile.getElements()) {
            if (!element.dependsOnChat()) continue;
            demand = true;
            if (editMode || !element.followsChat()) continue;
            if (!rectRead) {
                rect = getChatRect();
                rectRead = true;
            }
            element.applyChatAnchor(rect);
        }
        if (demand != chatDemand) {
            chatDemand = demand;
            ChatBarGate.setDemand(demand);
        }
    }

    /** A button that is set to show only while the chat is visible / its line is open is hidden otherwise (never in the editor). */
    private boolean isHiddenByChat(ControlElement element) {
        return element.getChatShow() != null && !ChatBarLogic.isVisible(element.getChatShow(), ChatBarGate.state(), editMode);
    }

    private void cancelTouchRouting() {
        cancelBindingSequences();
        if (radialMenuTouchActive) handleRadialMenuTouchUp(radialMenuTouchPointerId, false);
        if (profile != null) {
            for (ControlElement element : profile.getElements()) element.cancelTouch();
        }
        releaseAllShooterInputs();
        commitGamepadState();
        if (touchpadView != null) touchpadView.cancelTouchInput();
        touchpadPointers.clear();
        lookThroughPointerState.clear();
    }

    public void setOverlayOpacity(float overlayOpacity) {
        this.overlayOpacity = overlayOpacity;
    }

    public float getOverlayOpacity() {
        return overlayOpacity;
    }

    public int getSnappingSize() {
        return snappingSize;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (profile != null && profile.isElementsLoaded() && oldw > 0 && w != oldw) {
            cancelTouchRouting();
            profile.loadElements(this);
        }
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0) {
            readyToDraw = false;
            return;
        }

        snappingSize = width / 100;
        readyToDraw = true;

        if (editMode) {
            drawGrid(canvas);
        }

        if (profile != null) {
            if (!profile.isElementsLoaded()) profile.loadElements(this);
            syncChatLinks();
            if (showTouchscreenControls) {
                WindowViewRects windows = currentWindowRects();
                long nowMs = SystemClock.uptimeMillis();
                windowTracker.beginPass();
                int safeKeepers = countWindowKeepers();
                boolean fingerHeld = false;
                for (ControlElement element : profile.getElements()) {
                    if (element.isTouchHeld()) {
                        fingerHeld = true;
                        break;
                    }
                }
                TouchStyleDraw.beginFrame(nowMs, fingerHeld, getResources().getDisplayMetrics().density);
                for (ControlElement element : profile.getElements()) {
                    // Hide STICK elements replaced by container shooter mode
                    if (isStickHiddenByShooterMode(element)) continue;
                    if (isHiddenInLogin(element)) continue;
                    if (isHiddenByWindow(element, windows, nowMs, safeKeepers)) continue;
                    if (isHiddenByChat(element)) continue;
                    element.draw(canvas);
                }
                windowTracker.endPass();
                // Draw time per frame (log) and the fade into the resting look (style G): draw again when it is due.
                long styleWait = TouchStyleDraw.endFrame(nowMs);
                if (styleWait == 0) postInvalidateOnAnimation();
                else if (styleWait > 0) postInvalidateDelayed(styleWait + 5);
                // An element that waits to come back: draw once more when its time is up (nothing else may change by then).
                long wait = windowTracker.nextChangeIn(nowMs);
                if (wait >= 0) postInvalidateDelayed(wait + 10);
            }
        }

        // Selection frame, handles and snapping guides of the editor
        if (editMode) editController().draw(canvas);

        // Draw dynamic joysticks when shooter mode is active
        boolean anyShooterActive = shooterModeActive || containerShooterModeRuntime;
        if (anyShooterActive && joystickPointerId != -1) {
            drawShooterJoystick(canvas);
        }
        if (anyShooterActive && rightJoystickPointerId != -1) {
            float sizeMultiplier = getResolvedLookJoystickSize();
            drawDynamicJoystick(canvas, rightJoystickCenterX, rightJoystickCenterY,
                                rightJoystickCurrentX, rightJoystickCurrentY, sizeMultiplier);
        }

        // Draw container shooter mode toggle button
        if (containerShooterMode && isRuntimeToggleVisible() && !editMode && !isLoginHiddenNow()) {
            drawContainerShooterToggle(canvas);
        }

        super.onDraw(canvas);
    }

    private void drawGrid(Canvas canvas) {
        canvas.drawColor(0x80000000);
        paint.setStrokeWidth(snappingSize * 0.0625f);

        paint.setAntiAlias(false);
        paint.setColor(0x80303030);

        int width = getMaxWidth();
        int height = getMaxHeight();

        for (int i = 0; i < width; i += snappingSize) {
            canvas.drawLine(i, 0, i, height, paint);
            canvas.drawLine(0, i, width, i, paint);
        }

        float cx = Mathf.roundTo(width * 0.5f, snappingSize);
        float cy = Mathf.roundTo(height * 0.5f, snappingSize);
        paint.setColor(0x99424242);

        for (int i = 0; i < width; i += snappingSize * 2) {
            canvas.drawLine(cx, i, cx, i + snappingSize, paint);
            canvas.drawLine(i, cy, i + snappingSize, cy, paint);
        }

        paint.setAntiAlias(true);
    }

    private EditGestureController editController() {
        if (editController == null) editController = new EditGestureController(this);
        return editController;
    }

    /** The editor UI (bottom sheet, toolbar) listens to selection and gesture results. */
    public void setEditorListener(EditorListener listener) {
        this.editorListener = listener;
    }

    /** Selects an element (null = none) from the editor and tells the editor UI. */
    public void editorSelect(ControlElement element) {
        selectElement(element);
        if (editorListener != null) editorListener.onSelectionChanged(element);
    }

    /** Double tap or pencil handle: the editor UI should open the settings of the element. */
    public void editorRequestEdit(ControlElement element) {
        if (editorListener != null) editorListener.onEditRequested(element);
    }

    public void editorGestureFinished(boolean changed) {
        if (editorListener != null) editorListener.onGestureFinished(changed);
    }

    public synchronized boolean addElement() {
        if (editMode && profile != null) {
            ControlElement element = new ControlElement(this);
            // Calculate center position, snapped to grid
            int centerX = (int)Mathf.roundTo(getMaxWidth() * 0.5f, snappingSize);
            int centerY = (int)Mathf.roundTo(getMaxHeight() * 0.5f, snappingSize);
            element.setX(centerX);
            element.setY(centerY);
            profile.addElement(element);
            profile.save();
            selectElement(element);
            return true;
        }
        else return false;
    }

    public synchronized boolean removeElement() {
        if (editMode && selectedElement != null && profile != null) {
            profile.removeElement(selectedElement);
            selectedElement = null;
            profile.save();
            invalidate();
            return true;
        }
        else return false;
    }

    public ControlElement getSelectedElement() {
        return selectedElement;
    }

    private synchronized void deselectAllElements() {
        selectedElement = null;
        if (profile != null) {
            for (ControlElement element : profile.getElements()) element.setSelected(false);
        }
    }

    private void selectElement(ControlElement element) {
        deselectAllElements();
        if (element != null) {
            selectedElement = element;
            selectedElement.setSelected(true);
        }
        invalidate();
    }

    public synchronized ControlsProfile getProfile() {
        return profile;
    }

    public void setProfile(ControlsProfile profile) {
        setProfileInternal(profile, false);
    }

    /** Replaces the active profile without making hidden on-screen controls visible again. */
    public void setProfilePreservingOverlayVisibility(ControlsProfile profile) {
        setProfileInternal(profile, true);
    }

    private void setProfileInternal(ControlsProfile profile, boolean preserveOverlayVisibility) {
        cancelTouchRouting();

        final boolean profileChanged;
        final boolean overlayProfileVisible;
        synchronized (this) {
            profileChanged = this.gyroProfile != profile;
            overlayProfileVisible = this.profile != null;
        }

        // Gyro callbacks acquire this view's monitor, so stop them before taking it below.
        if (profileChanged) {
            gyroController.setHasProfile(false);
            gyroController.clearModifierSources();
        }

        synchronized (this) {
            if (profileChanged) resetThumbContributions();
            if (profile != null) {
                this.profile = preserveOverlayVisibility && !overlayProfileVisible ? null : profile;
                this.gyroProfile = profile;
                GamepadState state = profile.getGamepadState();
                baseThumbLX = Mathf.clamp(state.thumbLX - gyroThumbLX, -1f, 1f);
                baseThumbLY = Mathf.clamp(state.thumbLY - gyroThumbLY, -1f, 1f);
                baseThumbRX = Mathf.clamp(state.thumbRX - gyroThumbRX, -1f, 1f);
                baseThumbRY = Mathf.clamp(state.thumbRY - gyroThumbRY, -1f, 1f);
                deselectAllElements();
            }
            else {
                this.profile = null;
                this.gyroProfile = null;
            }
        }

        onControlsProfileContentChanged(profileChanged);
        gyroController.setHasProfile(profile != null);
    }

    /** Hides the controls profile from drawing without disabling per-game gyro input. */
    public void hideProfileForOverlay() {
        cancelTouchRouting();
        synchronized (this) {
            this.profile = null;
        }
    }

    /** Re-evaluates latched gyro activation after the active profile is edited in place. */
    public void onControlsProfileContentChanged(boolean profileContentReplaced) {
        final ControlsProfile currentProfile;
        synchronized (this) {
            currentProfile = gyroProfile;
        }
        if (profileContentReplaced || !GyroBindingInspector.hasModifierBinding(currentProfile)) {
            gyroController.resetActivationState();
        }
    }

    public boolean isShowTouchscreenControls() {
        return showTouchscreenControls;
    }

    public void setShowTouchscreenControls(boolean showTouchscreenControls) {
        this.showTouchscreenControls = showTouchscreenControls;
    }

    public boolean isGyroAvailable() {
        return gyroController.isAvailable();
    }

    public boolean isGyroTiltAvailable() {
        return gyroController.isTiltAvailable();
    }

    public boolean isGyroActive() {
        return gyroActive;
    }

    public boolean isGyroEnabled() {
        return gyroEnabled;
    }

    public synchronized boolean hasGyroControlAssigned(int activationMode) {
        ControlsProfile profile = gyroProfile;
        if (profile != null && !profile.isElementsLoaded() && getWidth() > 0 && getHeight() > 0) {
            profile.loadElements(this);
        }
        return GyroBindingInspector.hasUsableBinding(profile, activationMode);
    }

    public void setGyroSettings(GyroSettings settings) {
        if (settings == null) return;
        GyroSettings normalized = settings.normalized();
        gyroMode = normalized.getMode();
        gyroEnabled = gyroMode != GyroSettings.MODE_DISABLED;
        gyroController.setSettings(normalized);
    }

    public void setGyroModifierPressed(Object source, boolean pressed) {
        if (source != null) gyroController.setModifierPressed(source, pressed);
    }

    public void setGyroOverlaySuppressed(boolean suppressed) {
        gyroController.setOverlaySuppressed(suppressed);
    }

    public void setGyroGameplayActive(boolean active) {
        gyroController.setGameplayActive(active);
    }

    public void setGyroForeground(boolean foreground) {
        gyroController.setForeground(foreground);
    }

    public int getPrimaryColor() {
        return Color.argb((int)(overlayOpacity * 255), 255, 255, 255);
    }

    public int getSecondaryColor() {
        return Color.argb((int)(overlayOpacity * 255), 2, 119, 189);
    }

    public Paint getPaint() {
        return paint;
    }

    public Path getPath() {
        return path;
    }

    public ColorFilter getColorFilter() {
        return colorFilter;
    }

    public TouchpadView getTouchpadView() {
        return touchpadView;
    }

    public void setTouchpadView(TouchpadView touchpadView) {
        this.touchpadView = touchpadView;
    }

    public XServer getXServer() {
        return xServer;
    }

    public void setXServer(XServer xServer) {
        this.xServer = xServer;
        applyShooterMouseInputMode();
        createMouseMoveTimer();
    }

    public int getMaxWidth() {
        return (int)Mathf.roundTo(getWidth(), snappingSize);
    }

    @Override
    protected void onDetachedFromWindow() {
        CooldownService.unregister(this);
        ChatBarGate.detach(this);
        cancelTouchRouting();
        gyroController.onDetachedFromWindow();
        if (mouseMoveTimer != null)
            mouseMoveTimer.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        CooldownService.register(this); // cooldowns of the action bar slots on the buttons (TouchForever strip)
        chatDemand = false;
        ChatBarGate.attach(this, this::postInvalidate); // chat state for chat buttons: shown while, pinned to the chat window
        gyroController.onAttachedToWindow();
    }

    public int getMaxHeight() {
        return (int)Mathf.roundTo(getHeight(), snappingSize);
    }

    private void createMouseMoveTimer() {
        if (profile != null && mouseMoveTimer == null) {
            final float cursorSpeed = profile.getCursorSpeed();
            mouseMoveTimer = new Timer();
            mouseMoveTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    xServer.injectPointerMoveDelta((int)(mouseMoveOffset.x * 10 * cursorSpeed), (int)(mouseMoveOffset.y * 10 * cursorSpeed));
                }
            }, 0, 1000 / 60);
        }
    }

    private void processJoystickInput(ExternalController controller) {
        ExternalControllerBinding controllerBinding;
        final int[] axes = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y};
        final float[] values = {controller.state.thumbLX, controller.state.thumbLY, controller.state.thumbRX, controller.state.thumbRY, controller.state.getDPadX(), controller.state.getDPadY()};

        for (byte i = 0; i < axes.length; i++) {
            if (Math.abs(values[i]) > ControlElement.STICK_DEAD_ZONE) {
                controllerBinding = controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axes[i], Mathf.sign(values[i])));
                if (controllerBinding != null) handleInputEvent(controllerBinding.getBindingCombo(), true, values[i]);
            }
            else {
                controllerBinding = controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axes[i], (byte) 1));
                if (controllerBinding != null) handleInputEvent(controllerBinding.getBindingCombo(), false, values[i]);
                controllerBinding = controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axes[i], (byte)-1));
                if (controllerBinding != null) handleInputEvent(controllerBinding.getBindingCombo(), false, values[i]);
            }
        }
    }

    // ========== Shooter Mode Methods ==========

    public boolean isShooterModeActive() {
        return shooterModeActive;
    }

    public void setShooterModeActive(boolean active) {
        if (shooterModeActive != active) cancelTouchRouting();
        if (!active) {
            releaseAllShooterInputs();
        }
        this.shooterModeActive = active;
        if (!active) commitGamepadState();
        applyShooterMouseInputMode();
        invalidate();
    }

    public void setContainerShooterMode(boolean enabled) {
        if (containerShooterMode != enabled || containerShooterModeRuntime != enabled) {
            cancelTouchRouting();
        }
        this.containerShooterMode = enabled;
        this.containerShooterModeRuntime = enabled;
        if (!enabled && !shooterModeActive) {
            releaseAllShooterInputs();
            commitGamepadState();
        }
        applyShooterMouseInputMode();
        invalidate();
    }

    public void setShooterModeConfig(ShooterModeConfig config) {
        releaseAllShooterInputs();
        commitGamepadState();
        this.shooterModeConfig = config != null ? config : ShooterModeConfig.fromJson("");
        applyShooterMouseInputMode();
        lookAccumX = 0;
        lookAccumY = 0;
        lookDeadzoneAccumX = 0;
        lookDeadzoneAccumY = 0;
        lookSmoothX = 0;
        lookSmoothY = 0;
        invalidate();
    }

    private void applyShooterMouseInputMode() {
        if (xServer == null || shooterModeConfig == null) return;
        boolean useWin32RelativeInput =
                (shooterModeActive || containerShooterModeRuntime)
                        && ShooterModeConfig.LOOK_MOUSE.equals(getResolvedShooterLookType())
                        && shooterModeConfig.getWin32RelativeMouseInput();
        xServer.setRelativeMouseMovement(useWin32RelativeInput);
    }

    public void setShooterModeConfigJson(String json) {
        setShooterModeConfig(ShooterModeConfig.fromJson(json));
    }

    public void setShowKeyboardCallback(Runnable callback) {
        this.showKeyboardCallback = callback;
    }

    public void setVoiceChatCallback(Runnable callback) {
        this.voiceChatCallback = callback;
    }

    public void setChatCommandCallback(ChatCommandCallback callback) {
        this.chatCommandCallback = callback;
    }

    /** Types [text] into the WoW chat (Enter, text, Enter); does nothing while no callback is wired. */
    public void runChatCommand(String text) {
        ChatCommandCallback callback = chatCommandCallback;
        if (callback != null && text != null && !text.isEmpty()) callback.onChatCommand(text);
    }

    public void setChatActionCallback(ChatActionCallback callback) {
        this.chatActionCallback = callback;
    }

    /** Runs the chat control [actionId] of a button; does nothing while no callback is wired. */
    public void runChatAction(String actionId) {
        ChatActionCallback callback = chatActionCallback;
        if (callback != null && actionId != null) callback.onChatAction(actionId);
    }

    /** The chat window of the game in view pixels (left, top, right, bottom), or null when the addon does not report it. */
    public float[] getChatRect() {
        return ChatBarLogic.viewRect(ChatBarGate.state(), getWidth(), getHeight(), isRendererFullscreen());
    }

    public void setOpenNavigationMenuCallback(Runnable callback) {
        this.openNavigationMenuCallback = callback;
    }

    public void setOpenControlsEditorCallback(Runnable callback) {
        this.openControlsEditorCallback = callback;
    }

    public void setRadialMenuListener(RadialMenuListener listener) {
        this.radialMenuListener = listener;
    }

    public void triggerShowKeyboard() {
        if (showKeyboardCallback != null) showKeyboardCallback.run();
    }

    public void handleRadialMenuTouchDown(int pointerId, float x, float y) {
        handleRadialMenuTouchDown(pointerId, x, y, null);
    }

    public void handleRadialMenuTouchDown(int pointerId, float x, float y, String menuId) {
        handleRadialMenuTouchDown(pointerId, x, y, menuId, null);
    }

    /** [direction]: the "opens to" setting of the radial button (null = automatic, by screen area). */
    public void handleRadialMenuTouchDown(int pointerId, float x, float y, String menuId, String direction) {
        radialMenuTouchActive = true;
        radialMenuTouchPointerId = pointerId;
        if (radialMenuListener != null) radialMenuListener.onRadialMenuTouchStart(pointerId, x, y, menuId, direction);
    }

    public void handleRadialMenuTouchMove(int pointerId, float x, float y) {
        if (radialMenuTouchActive && pointerId == radialMenuTouchPointerId && radialMenuListener != null) {
            radialMenuListener.onRadialMenuTouchMove(pointerId, x, y);
        }
    }

    public void handleRadialMenuTouchUp(int pointerId, boolean commit) {
        if (!radialMenuTouchActive) return;
        if (radialMenuTouchPointerId != MotionEvent.INVALID_POINTER_ID && pointerId != radialMenuTouchPointerId) return;
        int finishedPointerId = radialMenuTouchPointerId;
        radialMenuTouchActive = false;
        radialMenuTouchPointerId = MotionEvent.INVALID_POINTER_ID;
        if (radialMenuListener != null) radialMenuListener.onRadialMenuTouchEnd(finishedPointerId, commit);
    }

    /**
     * Check if a STICK element should be hidden because container shooter mode replaces it. Never in the editor: a stick (and its key
     * ring) that is hidden there still has its selection frame, so it looks missing and cannot be edited (Backup import, 10.11.2026).
     */
    private boolean isStickHiddenByShooterMode(ControlElement element) {
        if (editMode || !containerShooterModeRuntime) return false;
        if (element.getType() != ControlElement.Type.STICK) return false;
        Binding b0 = element.getBindingAt(0);
        return b0 == Binding.GAMEPAD_LEFT_THUMB_UP || b0 == Binding.GAMEPAD_RIGHT_THUMB_UP;
    }

    private ControlElement getShooterModeElement() {
        if (profile != null) {
            for (ControlElement element : profile.getElements()) {
                if (element.getType() == ControlElement.Type.SHOOTER_MODE) return element;
            }
        }
        return null;
    }

    private String getResolvedShooterMovementType() {
        if (containerShooterModeRuntime && shooterModeConfig != null) {
            return shooterModeConfig.getMovementType();
        }

        ControlElement smElement = getShooterModeElement();
        if (shooterModeActive && smElement != null) {
            return smElement.getShooterMovementType();
        }

        return shooterModeConfig != null ? shooterModeConfig.getMovementType() : "gamepad_left_stick";
    }

    private String getResolvedShooterLookType() {
        if (containerShooterModeRuntime && shooterModeConfig != null) {
            return shooterModeConfig.getLookType();
        }

        ControlElement smElement = getShooterModeElement();
        if (shooterModeActive && smElement != null) {
            return smElement.getShooterLookType();
        }

        return shooterModeConfig != null ? shooterModeConfig.getLookType() : "gamepad_right_stick";
    }

    private float getResolvedBaseLookSensitivity() {
        ControlElement smElement = getShooterModeElement();
        return smElement != null ? smElement.getShooterLookSensitivity() : 1.0f;
    }

    private float getProfileShooterJoystickSize() {
        ControlElement smElement = getShooterModeElement();
        return smElement != null ? smElement.getShooterJoystickSize() : 1.0f;
    }

    private float getResolvedMovementJoystickSize() {
        float configSize = shooterModeConfig != null ? shooterModeConfig.getMovementJoystickSize() : 1.0f;
        return getProfileShooterJoystickSize() * configSize;
    }

    private float getResolvedLookJoystickSize() {
        float configSize = shooterModeConfig != null ? shooterModeConfig.getLookJoystickSize() : 1.0f;
        return getProfileShooterJoystickSize() * configSize;
    }

    private float getMovementJoystickDeadzone() {
        return shooterModeConfig != null ? shooterModeConfig.getMovementJoystickDeadzone() : ShooterModeConfig.DEFAULT_ANALOG_STICK_DEADZONE;
    }

    private float getLookJoystickDeadzone() {
        return shooterModeConfig != null ? shooterModeConfig.getLookJoystickDeadzone() : ShooterModeConfig.DEFAULT_ANALOG_STICK_DEADZONE;
    }

    private float getMovementStickSensitivity() {
        return shooterModeConfig != null ? shooterModeConfig.getMovementStickSensitivity() : 1.0f;
    }

    private float getLookStickSensitivity() {
        return shooterModeConfig != null ? shooterModeConfig.getLookStickSensitivity() : 1.0f;
    }

    private boolean isMovementFloatingJoystickBehavior() {
        return shooterModeConfig != null &&
                ShooterModeConfig.JOYSTICK_FLOATING.equals(shooterModeConfig.getMovementJoystickBehavior());
    }

    private boolean isLookFloatingJoystickBehavior() {
        return shooterModeConfig != null &&
                ShooterModeConfig.JOYSTICK_FLOATING.equals(shooterModeConfig.getLookJoystickBehavior());
    }

    private boolean isRuntimeToggleVisible() {
        return ShooterModeConfig.runtimeToggleVisible(wowContainer, shooterModeConfig);
    }

    /** WoW-Forever-Container: der Shooter-Umschaltknopf erscheint dort nie. */
    public void setWowContainer(boolean wow) {
        this.wowContainer = wow;
        invalidate();
    }

    private float getJoystickOpacity() {
        if (shooterModeConfig != null && shooterModeConfig.getJoystickOpacity() > 0) {
            return shooterModeConfig.getJoystickOpacity();
        }
        return overlayOpacity;
    }

    private float applyJoystickDeadzone(float value, float deadzone) {
        float magnitude = Math.abs(value);
        if (magnitude <= deadzone) return 0;
        if (deadzone >= 1.0f) return 0;
        return ((magnitude - deadzone) / (1.0f - deadzone)) * Mathf.sign(value);
    }

    /** Fixed gain of the shooter-mode joysticks; they have their own sensitivity setting (unchanged behaviour). */
    private static final float SHOOTER_STICK_GAIN = 3.0f;

    private float getStickOutputValue(float value, float deadzone, float sensitivity) {
        float adjustedValue = applyJoystickDeadzone(value, deadzone);
        return Mathf.clamp(adjustedValue * SHOOTER_STICK_GAIN * sensitivity, -1, 1);
    }

    private int alphaForOpacity(float opacity, int maxAlpha) {
        return Mathf.clamp((int)(opacity * maxAlpha), 0, 255);
    }

    private boolean isFireButton(ControlElement element) {
        if (element.getType() != ControlElement.Type.BUTTON) return false;
        Binding binding = element.getBindingAt(0);
        return binding == Binding.MOUSE_LEFT_BUTTON || binding == Binding.MOUSE_RIGHT_BUTTON;
    }

    private Binding[] getJoystickBindings(String movementType) {
        switch (movementType) {
            case "arrow_keys":
                return new Binding[]{Binding.KEY_UP, Binding.KEY_RIGHT, Binding.KEY_DOWN, Binding.KEY_LEFT};
            case "gamepad_left_stick":
                return new Binding[]{Binding.GAMEPAD_LEFT_THUMB_UP, Binding.GAMEPAD_LEFT_THUMB_RIGHT,
                                     Binding.GAMEPAD_LEFT_THUMB_DOWN, Binding.GAMEPAD_LEFT_THUMB_LEFT};
            case "wasd":
            default:
                return new Binding[]{Binding.KEY_W, Binding.KEY_D, Binding.KEY_S, Binding.KEY_A};
        }
    }

    private void releaseShooterJoystick() {
        if (joystickPointerId != -1) {
            Binding[] bindings = getJoystickBindings(getResolvedShooterMovementType());
            for (int i = 0; i < 4; i++) {
                if (joystickStates[i]) {
                    handleInputEvent(bindings[i], false);
                }
            }
            joystickPointerId = -1;
            Arrays.fill(joystickStates, false);
            releaseShooterSprint();
            invalidate();
        }
    }

    private void releaseShooterLook() {
        if (lookPointerId != -1) {
            if (lookFireElement != null) {
                lookFireElement.handleTouchUp(lookPointerId);
                lookFireElement = null;
            }
            lookPointerId = -1;
            lookAccumX = 0;
            lookAccumY = 0;
            lookDeadzoneAccumX = 0;
            lookDeadzoneAccumY = 0;
            lookSmoothX = 0;
            lookSmoothY = 0;
        }
    }

    private void releasePendingButtonLook() {
        pendingButtonLooks.clear();
    }

    private void releasePendingButtonLook(int pointerId) {
        pendingButtonLooks.remove(pointerId);
    }

    private void releaseAllShooterInputs() {
        releaseShooterJoystick();
        releaseShooterLook();
        releaseRightJoystick();
        releasePendingButtonLook();
        releaseShooterSprint();
    }

    private Binding getShooterSprintBinding() {
        String bindingName = shooterModeConfig != null ? shooterModeConfig.getSprintBinding() : ShooterModeConfig.SPRINT_BINDING_SHIFT;
        Binding binding = Binding.fromString(bindingName);
        return binding != Binding.NONE ? binding : Binding.KEY_SHIFT_L;
    }

    private boolean isShooterSprintPressMode() {
        return shooterModeConfig != null && shooterModeConfig.getOuterRingSprintPressMode();
    }

    private void tapShooterSprintBinding() {
        Binding binding = getShooterSprintBinding();
        cancelPendingShooterSprintTap();
        handleInputEvent(binding, true);
        commitGamepadStateIfNeeded(binding);
        shooterSprintTapBinding = binding;
        postDelayed(shooterSprintTapReleaseRunnable, SHOOTER_SPRINT_TAP_DURATION_MS);
    }

    private void cancelPendingShooterSprintTap() {
        removeCallbacks(shooterSprintTapReleaseRunnable);
        releaseShooterSprintTap();
    }

    private void releaseShooterSprintTap() {
        Binding binding = shooterSprintTapBinding;
        if (binding == Binding.NONE) return;

        shooterSprintTapBinding = Binding.NONE;
        handleInputEvent(binding, false);
        commitGamepadStateIfNeeded(binding);
    }

    private void commitGamepadStateIfNeeded(Binding binding) {
        if (binding == null || !binding.isGamepad()) return;
        commitGamepadState();
    }

    private void commitGamepadState() {
        if (xServer == null || profile == null) return;
        WinHandler winHandler = xServer.getWinHandler();
        if (winHandler != null) {
            GamepadState state = profile.getGamepadState();
            winHandler.sendGamepadState();
            winHandler.sendVirtualGamepadState(state);
        }
    }

    private void releaseShooterSprint() {
        cancelPendingShooterSprintTap();
        if (shooterSprintActive) {
            if (!isShooterSprintPressMode()) {
                handleInputEvent(getShooterSprintBinding(), false);
            }
            shooterSprintActive = false;
        }
    }

    private void updateShooterSprint(float deltaX, float deltaY) {
        if (shooterModeConfig == null || !shooterModeConfig.getOuterRingSprintEnabled()) {
            releaseShooterSprint();
            return;
        }

        float magnitude = (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        boolean shouldSprint = magnitude >= shooterModeConfig.getOuterRingSprintThreshold();
        if (shooterModeConfig.getOuterRingSprintPressMode()) {
            if (shouldSprint && !shooterSprintActive) {
                tapShooterSprintBinding();
            }
            shooterSprintActive = shouldSprint;
            return;
        }

        if (shouldSprint != shooterSprintActive) {
            handleInputEvent(getShooterSprintBinding(), shouldSprint);
            shooterSprintActive = shouldSprint;
        }
    }

    private void releaseRightJoystick() {
        if (rightJoystickPointerId != -1) {
            Binding[] bindings = getRightJoystickBindings();
            for (int i = 0; i < 4; i++) {
                if (rightJoystickStates[i]) {
                    handleInputEvent(bindings[i], false);
                }
            }
            rightJoystickPointerId = -1;
            Arrays.fill(rightJoystickStates, false);
            invalidate();
        }
    }

    private Binding[] getRightJoystickBindings() {
        return new Binding[]{
            Binding.GAMEPAD_RIGHT_THUMB_UP, Binding.GAMEPAD_RIGHT_THUMB_RIGHT,
            Binding.GAMEPAD_RIGHT_THUMB_DOWN, Binding.GAMEPAD_RIGHT_THUMB_LEFT
        };
    }

    private void handleRightJoystickMove(float x, float y) {
        float sizeMultiplier = getResolvedLookJoystickSize();
        float radius = snappingSize * 6 * sizeMultiplier;
        if (radius <= 0) return;

        float localX = x - rightJoystickCenterX;
        float localY = y - rightJoystickCenterY;

        float distance = (float)Math.sqrt(localX * localX + localY * localY);
        if (distance > radius) {
            float angle = (float)Math.atan2(localY, localX);
            localX = (float)(Math.cos(angle) * radius);
            localY = (float)(Math.sin(angle) * radius);
            if (isLookFloatingJoystickBehavior()) {
                rightJoystickCenterX = x - localX;
                rightJoystickCenterY = y - localY;
            }
        }

        rightJoystickCurrentX = rightJoystickCenterX + localX;
        rightJoystickCurrentY = rightJoystickCenterY + localY;

        float deltaX = Mathf.clamp(localX / radius, -1, 1);
        float deltaY = Mathf.clamp(localY / radius, -1, 1);

        Binding[] bindings = getRightJoystickBindings();

        float deadzone = getLookJoystickDeadzone();
        float sensitivity = getLookStickSensitivity();

        for (int i = 0; i < 4; i++) {
            float value = (i == 1 || i == 3) ? deltaX : deltaY;
            value = getStickOutputValue(value, deadzone, sensitivity);
            handleInputEvent(bindings[i], true, value);
            rightJoystickStates[i] = true;
        }
        invalidate();
    }

    private void drawDynamicJoystick(Canvas canvas, float centerX, float centerY, float currentX, float currentY, float sizeMultiplier) {
        float radius = snappingSize * 6 * sizeMultiplier;
        float strokeWidth = snappingSize * 0.25f;
        int primaryColor = getPrimaryColor();
        float opacity = getJoystickOpacity();

        paint.setColor(ColorUtils.setAlphaComponent(primaryColor, alphaForOpacity(opacity, 255)));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        canvas.drawCircle(centerX, centerY, radius, paint);

        float thumbRadius = snappingSize * 3.5f * sizeMultiplier;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(primaryColor, alphaForOpacity(opacity, 50)));
        canvas.drawCircle(currentX, currentY, thumbRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(ColorUtils.setAlphaComponent(primaryColor, alphaForOpacity(opacity, 255)));
        canvas.drawCircle(currentX, currentY, thumbRadius + strokeWidth * 0.5f, paint);
    }

    /** Get the bounding rect for the container shooter mode toggle button at top center. */
    private android.graphics.RectF getToggleButtonRect() {
        float btnW = snappingSize * 12;
        float btnH = snappingSize * 4;
        float cx = getWidth() / 2f;
        return new android.graphics.RectF(cx - btnW / 2, snappingSize * 0.5f, cx + btnW / 2, snappingSize * 0.5f + btnH);
    }

    private void drawContainerShooterToggle(Canvas canvas) {
        android.graphics.RectF rect = getToggleButtonRect();
        float radius = snappingSize * 0.5f;
        int primaryColor = getPrimaryColor();

        // Background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(containerShooterModeRuntime ? 0xFF0277BD : 0xFF616161,
                        (int)(overlayOpacity * 200)));
        canvas.drawRoundRect(rect, radius, radius, paint);

        // Border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(snappingSize * 0.125f);
        paint.setColor(primaryColor);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // Text
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(primaryColor);
        paint.setTextSize(snappingSize * 1.8f);
        paint.setTextAlign(Paint.Align.CENTER);
        float textY = rect.centerY() - (paint.descent() + paint.ascent()) * 0.5f;
        String label = getContext().getString(containerShooterModeRuntime
                ? app.gamenative.R.string.shooter_mode_on
                : app.gamenative.R.string.shooter_mode_off);
        canvas.drawText(label, rect.centerX(), textY, paint);
    }

    private void handleShooterJoystickMove(float x, float y) {
        float joystickSizeMultiplier = getResolvedMovementJoystickSize();
        String movementType = getResolvedShooterMovementType();
        float radius = snappingSize * 6 * joystickSizeMultiplier;
        if (radius <= 0) return;

        float localX = x - joystickCenterX;
        float localY = y - joystickCenterY;

        float distance = (float)Math.sqrt(localX * localX + localY * localY);
        if (distance > radius) {
            float angle = (float)Math.atan2(localY, localX);
            localX = (float)(Math.cos(angle) * radius);
            localY = (float)(Math.sin(angle) * radius);
            if (isMovementFloatingJoystickBehavior()) {
                joystickCenterX = x - localX;
                joystickCenterY = y - localY;
            }
        }

        joystickCurrentX = joystickCenterX + localX;
        joystickCurrentY = joystickCenterY + localY;

        float deltaX = Mathf.clamp(localX / radius, -1, 1);
        float deltaY = Mathf.clamp(localY / radius, -1, 1);

        Binding[] bindings = getJoystickBindings(movementType);

        float analogDeadzone = getMovementJoystickDeadzone();
        float digitalDeadzone = ControlElement.STICK_DEAD_ZONE;
        float sensitivity = getMovementStickSensitivity();
        boolean[] newStates = {
            deltaY <= -digitalDeadzone,   // up
            deltaX >= digitalDeadzone,    // right
            deltaY >= digitalDeadzone,    // down
            deltaX <= -digitalDeadzone    // left
        };

        for (int i = 0; i < 4; i++) {
            float value = (i == 1 || i == 3) ? deltaX : deltaY;
            if (bindings[i].isGamepad()) {
                value = getStickOutputValue(value, analogDeadzone, sensitivity);
                handleInputEvent(bindings[i], true, value);
                joystickStates[i] = true;
            } else {
                if (newStates[i] != joystickStates[i]) {
                    handleInputEvent(bindings[i], newStates[i]);
                    joystickStates[i] = newStates[i];
                }
            }
        }

        updateShooterSprint(deltaX, deltaY);
        invalidate();
    }

    private void drawShooterJoystick(Canvas canvas) {
        float joystickSizeMultiplier = getResolvedMovementJoystickSize();
        drawDynamicJoystick(canvas, joystickCenterX, joystickCenterY, joystickCurrentX, joystickCurrentY, joystickSizeMultiplier);
    }

    private boolean isLeftSideTouch(float x) {
        float split = shooterModeConfig != null ? shooterModeConfig.getMovementZoneSplit() : 0.5f;
        return x < getWidth() * split;
    }

    private boolean shouldUseRightJoystickLook() {
        return "gamepad_right_stick".equals(getResolvedShooterLookType());
    }

    private boolean startShooterJoystickPointer(int pointerId, float x, float y) {
        if (joystickPointerId != -1) return false;

        joystickPointerId = pointerId;
        joystickCenterX = x;
        joystickCenterY = y;
        joystickCurrentX = x;
        joystickCurrentY = y;
        invalidate();
        return true;
    }

    private boolean startRightJoystickPointer(int pointerId, float x, float y) {
        if (rightJoystickPointerId != -1) return false;

        rightJoystickPointerId = pointerId;
        rightJoystickCenterX = x;
        rightJoystickCenterY = y;
        rightJoystickCurrentX = x;
        rightJoystickCurrentY = y;
        invalidate();
        return true;
    }

    private boolean startShooterLookPointer(int pointerId, float x, float y, ControlElement fireElement) {
        if (lookPointerId != -1) return false;

        lookPointerId = pointerId;
        lookLastX = x;
        lookLastY = y;
        lookAccumX = 0;
        lookAccumY = 0;
        lookDeadzoneAccumX = 0;
        lookDeadzoneAccumY = 0;
        lookSmoothX = 0;
        lookSmoothY = 0;
        lookFireElement = fireElement;
        return true;
    }

    private boolean startRightSideShooterPointer(int pointerId, float x, float y, ControlElement fireElement) {
        if (shouldUseRightJoystickLook()) {
            return startRightJoystickPointer(pointerId, x, y);
        }
        return startShooterLookPointer(pointerId, x, y, fireElement);
    }

    private void startPendingButtonLookPointer(int pointerId, float x, float y, ControlElement fireElement) {
        if (shooterModeConfig == null || !shooterModeConfig.getButtonLookThroughEnabled()) return;

        int threshold = shooterModeConfig.getButtonLookThroughDragThreshold();
        if (threshold <= 0) {
            if (isLeftSideTouch(x)) startShooterJoystickPointer(pointerId, x, y);
            else startRightSideShooterPointer(pointerId, x, y, fireElement);
            return;
        }

        if (pendingButtonLooks.get(pointerId) != null) return;
        pendingButtonLooks.put(pointerId, new PendingButtonLook(x, y, fireElement, isLeftSideTouch(x)));
    }

    private boolean handlePendingButtonLookMove(int pointerId, float x, float y) {
        PendingButtonLook pending = pendingButtonLooks.get(pointerId);
        if (pending == null) return false;

        float dx = x - pending.startX;
        float dy = y - pending.startY;
        float threshold = shooterModeConfig != null ? shooterModeConfig.getButtonLookThroughDragThreshold() : 0;
        if (dx * dx + dy * dy < threshold * threshold) {
            return true;
        }

        releasePendingButtonLook(pointerId);

        boolean started = pending.leftSide
                ? startShooterJoystickPointer(pointerId, pending.startX, pending.startY)
                : startRightSideShooterPointer(pointerId, pending.startX, pending.startY, pending.element);

        if (started) {
            return handleShooterTouchMovePointer(pointerId, x, y);
        }
        return true;
    }

    private boolean handleShooterTouchDown(int pointerId, float x, float y, boolean allowButtonLookThrough) {
        boolean handled = false;

        // Check container shooter mode toggle button first
        if (containerShooterMode && isRuntimeToggleVisible()) {
            android.graphics.RectF toggleRect = getToggleButtonRect();
            if (toggleRect.contains(x, y)) {
                cancelTouchRouting();
                containerShooterModeRuntime = !containerShooterModeRuntime;
                if (!containerShooterModeRuntime) {
                    releaseAllShooterInputs();
                    commitGamepadState();
                }
                applyShooterMouseInputMode();
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                return true;
            }
        }

        // If runtime is off (and no SHOOTER_MODE element active), only the toggle button above should handle
        if (!shooterModeActive && !containerShooterModeRuntime) {
            return false;
        }

        for (ControlElement element : profile.getElements()) {
            // Skip hidden sticks in container shooter mode
            if (isStickHiddenByShooterMode(element)) continue;
            if (element.handleTouchDown(pointerId, x, y)) {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                TouchStyleDraw.touched(SystemClock.uptimeMillis());
                handled = true;
                if (allowButtonLookThrough
                        && element.getType() == ControlElement.Type.BUTTON
                        && element.isShooterLookThrough()) {
                    startPendingButtonLookPointer(pointerId, x, y, element);
                }
                break;
            }
        }
        if (!handled) {
            if (isLeftSideTouch(x)) {
                handled = startShooterJoystickPointer(pointerId, x, y);
            } else {
                handled = startRightSideShooterPointer(pointerId, x, y, null);
            }
        }
        return handled;
    }

    private boolean handleShooterTouchMovePointer(int pid, float x, float y) {
        if (handlePendingButtonLookMove(pid, x, y)) {
            return true;
        }
        if (pid == joystickPointerId) {
            handleShooterJoystickMove(x, y);
            return true;
        }
        if (pid == rightJoystickPointerId) {
            handleRightJoystickMove(x, y);
            return true;
        }
        if (pid == lookPointerId) {
            float rawDx = x - lookLastX;
            float rawDy = y - lookLastY;
            lookLastX = x;
            lookLastY = y;

            float dx = rawDx;
            float dy = rawDy;
            float lookDeadzone = shooterModeConfig != null ? shooterModeConfig.getLookDeadzone() : 0;
            if (lookDeadzone > 0) {
                lookDeadzoneAccumX += rawDx;
                lookDeadzoneAccumY += rawDy;
                if (lookDeadzoneAccumX * lookDeadzoneAccumX + lookDeadzoneAccumY * lookDeadzoneAccumY <
                        lookDeadzone * lookDeadzone) {
                    return true;
                }
                dx = lookDeadzoneAccumX;
                dy = lookDeadzoneAccumY;
                lookDeadzoneAccumX = 0;
                lookDeadzoneAccumY = 0;
            }

            float baseSensitivity = getResolvedBaseLookSensitivity();
            float sensitivityX = shooterModeConfig != null ? shooterModeConfig.getLookSensitivityX() : 1.0f;
            float sensitivityY = shooterModeConfig != null ? shooterModeConfig.getLookSensitivityY() : 1.0f;
            float scaledDx = dx * baseSensitivity * sensitivityX;
            float scaledDy = dy * baseSensitivity * sensitivityY;
            float accelerationMultiplier = getMouseLookAccelerationMultiplier(dx, dy);
            scaledDx *= accelerationMultiplier;
            scaledDy *= accelerationMultiplier;
            if (shooterModeConfig != null && shooterModeConfig.getInvertLookY()) {
                scaledDy = -scaledDy;
            }

            float smoothing = shooterModeConfig != null ? shooterModeConfig.getLookSmoothing() : 0;
            if (smoothing > 0) {
                lookSmoothX = lookSmoothX * smoothing + scaledDx * (1.0f - smoothing);
                lookSmoothY = lookSmoothY * smoothing + scaledDy * (1.0f - smoothing);
                scaledDx = lookSmoothX;
                scaledDy = lookSmoothY;
            }

            lookAccumX += scaledDx;
            lookAccumY += scaledDy;
            int moveX = (int)lookAccumX;
            int moveY = (int)lookAccumY;
            lookAccumX -= moveX;
            lookAccumY -= moveY;
            if (moveX != 0 || moveY != 0) {
                if (xServer.isRelativeMouseMovement()) {
                    xServer.getWinHandler().mouseEvent(MouseEventFlags.MOVE, moveX, moveY, 0);
                } else {
                    xServer.injectPointerMoveDelta(moveX, moveY);
                }
            }
            return true;
        }
        return false;
    }

    private float getMouseLookAccelerationMultiplier(float dx, float dy) {
        if (shooterModeConfig == null || !shooterModeConfig.getMouseAccelerationEnabled()) {
            return 1.0f;
        }

        float speed = (float)Math.sqrt(dx * dx + dy * dy);
        float maxMultiplier = shooterModeConfig.getMouseAccelerationMaxMultiplier();
        if (maxMultiplier <= 1.0f) return 1.0f;

        float multiplier = 1.0f + (speed / 24.0f) * shooterModeConfig.getMouseAccelerationStrength();
        return Mathf.clamp(multiplier, 1.0f, maxMultiplier);
    }

    // ========== End Shooter Mode Methods ==========

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (!editMode && profile != null) {
            ExternalController controller = profile.getController(event.getDeviceId());
            if (controller != null && controller.updateStateFromMotionEvent(event)) {
                ExternalControllerBinding controllerBinding;
                controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_L2);
                if (controllerBinding != null) handleInputEvent(controllerBinding.getBindingCombo(), controller.state.isPressed(ExternalController.IDX_BUTTON_L2));

                controllerBinding = controller.getControllerBinding(KeyEvent.KEYCODE_BUTTON_R2);
                if (controllerBinding != null) handleInputEvent(controllerBinding.getBindingCombo(), controller.state.isPressed(ExternalController.IDX_BUTTON_R2));

                processJoystickInput(controller);
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editMode && readyToDraw) {
            // Select / move with snapping / resize / settings: see EditGestureController
            return editController().onTouchEvent(event);
        }

        if (!editMode && profile != null) {
            final boolean loginRule = updateLoginHidden();
            int actionIndex = event.getActionIndex();
            int pointerId = event.getPointerId(actionIndex);
            int actionMasked = event.getActionMasked();
            boolean handled = false;
            boolean touchscreenMode = touchpadView.isTouchscreenMode();
            if (touchscreenMode && lookThroughPointerState.isActive()) {
                cancelTouchRouting();
            }

            switch (actionMasked) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    float x = event.getX(actionIndex);
                    float y = event.getY(actionIndex);

                    // Shooter mode intercept (use containerShooterMode so toggle button is always reachable)
                    if (!loginRule && (shooterModeActive || containerShooterMode)
                            && handleShooterTouchDown(pointerId, x, y, !touchscreenMode)) {
                        break;
                    }

                    touchpadView.setPointerButtonLeftEnabled(true);
                    touchpadView.setPointerButtonRightEnabled(true);
                    boolean lookThroughCandidate = false;
                    WindowViewRects downWindows = currentWindowRects();
                    long downMs = SystemClock.uptimeMillis();
                    int downKeepers = countWindowKeepers();
                    for (ControlElement element : profile.getElements()) {
                        if (loginRule && !LoginTouchGate.keepInLogin(element.getType(), element.getBindingAt(0))) continue;
                        if (isHiddenByWindow(element, downWindows, downMs, downKeepers)) continue;
                        if (isHiddenByChat(element)) continue;
                        if (element.handleTouchDown(pointerId, x, y)) {
                            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                            TouchStyleDraw.touched(SystemClock.uptimeMillis());
                            handled = true;
                            if (event.getToolType(actionIndex) == MotionEvent.TOOL_TYPE_FINGER
                                    && element.getType() == ControlElement.Type.BUTTON
                                    && !touchscreenMode
                                    && element.isLookThrough()) {
                                lookThroughCandidate = true;
                            }
                        }
                        if (element.getBindingAt(0) == Binding.MOUSE_LEFT_BUTTON) {
                            touchpadView.setPointerButtonLeftEnabled(false);
                        }
                        if (element.getBindingAt(0) == Binding.MOUSE_RIGHT_BUTTON) {
                            touchpadView.setPointerButtonRightEnabled(false);
                        }
                    }
                    if (lookThroughCandidate) {
                        lookThroughPointerState.tryStart(
                                pointerId,
                                x,
                                y,
                                touchpadPointers.size() > 0
                        );
                    }
                    if (!handled) {
                        if (!lookThroughPointerState.isActive()) {
                            touchpadPointers.put(pointerId, true);
                            touchpadView.onTouchEvent(event);
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    for (byte i = 0, count = (byte)event.getPointerCount(); i < count; i++) {
                        float x = event.getX(i);
                        float y = event.getY(i);

                        // Shooter mode intercept per pointer
                        if (shooterModeActive || containerShooterModeRuntime) {
                            int pid = event.getPointerId(i);
                            if (handleShooterTouchMovePointer(pid, x, y)) continue;
                            // Non-intercepted pointer in shooter mode: try elements with correct ID
                            handled = false;
                            for (ControlElement element : profile.getElements()) {
                                if (element.handleTouchMove(pid, x, y)) handled = true;
                            }
                            continue;
                        }

                        handled = false;
                        int pid = event.getPointerId(i);
                        for (ControlElement element : profile.getElements()) {
                            if (element.handleTouchMove(pid, x, y)) handled = true;
                        }
                        if (lookThroughPointerState.owns(pid)) {
                            LookThroughPointerState.Delta delta = lookThroughPointerState.move(
                                    pid,
                                    x,
                                    y,
                                    lookThroughTouchSlop
                            );
                            if (delta != null) {
                                touchpadView.movePointerFromLookThrough(delta.x, delta.y);
                            }
                        }
                    }
                    if (!(shooterModeActive || containerShooterModeRuntime) && touchpadPointers.size() > 0) {
                        touchpadView.onTouchEvent(event);
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (actionMasked == MotionEvent.ACTION_CANCEL) {
                        cancelTouchRouting();
                        handled = true;
                        break;
                    }
                    // Shooter mode intercept
                    if (shooterModeActive || containerShooterModeRuntime) {
                        if (pendingButtonLooks.get(pointerId) != null) {
                            releasePendingButtonLook(pointerId);
                            handled = true;
                        }
                        if (pointerId == joystickPointerId) {
                            releaseShooterJoystick();
                            handled = true;
                        }
                        if (pointerId == rightJoystickPointerId) {
                            releaseRightJoystick();
                            handled = true;
                        }
                        if (pointerId == lookPointerId) {
                            releaseShooterLook();
                            handled = true;
                        }
                    }
                    for (ControlElement element : profile.getElements()) if (element.handleTouchUp(pointerId)) handled = true;
                    TouchStyleDraw.touched(SystemClock.uptimeMillis()); // the rest timer counts from the release
                    postInvalidateOnAnimation(); // an element kept visible by the finger may go (open game window over it)
                    if (touchpadPointers.get(pointerId)) touchpadView.onTouchEvent(event);
                    lookThroughPointerState.release(pointerId);
                    touchpadPointers.delete(pointerId);
                    break;
            }

            // commit on-screen joystick state
            WinHandler winHandler = xServer != null ? xServer.getWinHandler() : null;
            if (winHandler != null) {
                GamepadState state = profile.getGamepadState();
                winHandler.sendGamepadState();
                winHandler.sendVirtualGamepadState(state);
            }
        }
        return true;
    }

    public boolean onKeyEvent(KeyEvent event) {
        if (profile != null && event.getRepeatCount() == 0) {
            ExternalController controller = profile.getController(event.getDeviceId());
            if (controller != null) {
                ExternalControllerBinding controllerBinding = controller.getControllerBinding(event.getKeyCode());
                if (controllerBinding != null) {
                    int action = event.getAction();
                    if (action == KeyEvent.ACTION_DOWN) {
                        handleInputEvent(controllerBinding.getBindingCombo(), true);
                    }
                    else if (action == KeyEvent.ACTION_UP) {
                        handleInputEvent(controllerBinding.getBindingCombo(), false);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void handleInputEvent(Binding binding, boolean isActionDown) {
        handleInputEvent(binding, isActionDown, 0, binding);
    }

    public void handleInputEvent(BindingCombo bindingCombo, boolean isActionDown) {
        handleInputEvent(bindingCombo, isActionDown, 0, bindingCombo);
    }

    public void handleInputEvent(BindingCombo bindingCombo, boolean isActionDown, float offset) {
        handleInputEvent(bindingCombo, isActionDown, offset, bindingCombo);
    }

    public void handleInputEvent(BindingCombo bindingCombo, boolean isActionDown, float offset, Object source) {
        if (bindingCombo == null || bindingCombo.isEmpty()) return;
        // The reload key ("Oberfläche neu laden") is locked for 2 s after a press: no double tap, whatever element sent it.
        if (!ReloadGuard.allow(bindingCombo, isActionDown, SystemClock.uptimeMillis())) return;
        if (bindingCombo.isSequence()) {
            if (isActionDown) {
                if (activeSequenceCombos.add(bindingCombo)) performBindingSequence(bindingCombo, offset);
            }
            else {
                activeSequenceCombos.remove(bindingCombo);
            }
            return;
        }

        if (isActionDown) {
            for (Binding binding : bindingCombo.getBindings()) {
                handleInputEvent(binding, true, offset, source);
            }
        }
        else {
            java.util.List<Binding> bindings = bindingCombo.getBindings();
            for (int i = bindings.size() - 1; i >= 0; i--) {
                handleInputEvent(bindings.get(i), false, offset, source);
            }
        }
    }

    private void performBindingSequence(BindingCombo bindingCombo, float offset) {
        final int generation = sequenceGeneration;
        final int pressDurationMs = Math.min(
                SEQUENCE_PRESS_MS,
                Math.max(1, bindingCombo.getSequenceDelayMs() - 1));
        long delay = 0;
        for (Binding binding : bindingCombo.getBindings()) {
            final Object sequenceSource = new Object();
            postDelayed(() -> {
                if (generation != sequenceGeneration) return;
                handleInputEvent(binding, true, offset, sequenceSource);
                activeSequenceBindings.merge(binding, 1, Integer::sum);
                activeSequenceSources.put(sequenceSource, binding);
                commitGamepadStateIfNeeded(binding);
                postDelayed(() -> {
                    if (generation != sequenceGeneration) return;
                    releaseActiveSequenceBinding(binding, sequenceSource);
                }, pressDurationMs);
            }, delay);
            delay += bindingCombo.getSequenceDelayMs();
        }
    }

    private void releaseActiveSequenceBinding(Binding binding, Object source) {
        Integer count = activeSequenceBindings.get(binding);
        if (count == null) return;
        activeSequenceSources.remove(source);
        if (binding == Binding.GYRO_MODIFIER) {
            handleInputEvent(binding, false, 0, source);
        }
        if (count > 1) {
            activeSequenceBindings.put(binding, count - 1);
            return;
        }
        activeSequenceBindings.remove(binding);
        if (binding != Binding.GYRO_MODIFIER) handleInputEvent(binding, false, 0, source);
        commitGamepadStateIfNeeded(binding);
    }

    private void cancelBindingSequences() {
        sequenceGeneration++;
        activeSequenceCombos.clear();
        if (activeSequenceBindings.isEmpty()) {
            activeSequenceSources.clear();
            return;
        }

        boolean gamepadStateChanged = false;
        for (Map.Entry<Object, Binding> entry : activeSequenceSources.entrySet()) {
            if (entry.getValue() == Binding.GYRO_MODIFIER) {
                handleInputEvent(Binding.GYRO_MODIFIER, false, 0, entry.getKey());
            }
        }
        Binding[] bindings = activeSequenceBindings.keySet().toArray(new Binding[0]);
        for (int i = bindings.length - 1; i >= 0; i--) {
            Binding binding = bindings[i];
            if (binding != Binding.GYRO_MODIFIER) handleInputEvent(binding, false, 0, binding);
            gamepadStateChanged |= binding.isGamepad();
        }
        activeSequenceBindings.clear();
        activeSequenceSources.clear();
        if (gamepadStateChanged) commitGamepadState();
    }

    public void handleInputEvent(Binding binding, boolean isActionDown, float offset) {
        handleInputEvent(binding, isActionDown, offset, binding);
    }

    public void handleInputEvent(Binding binding, boolean isActionDown, float offset, Object source) {
        if (binding == null || binding == Binding.NONE) return;

        if (binding == Binding.GYRO_MODIFIER) {
            setGyroModifierPressed(source, isActionDown);
            return;
        }

        if (binding == Binding.OPEN_RADIAL_MENU) {
            if (radialMenuListener != null) radialMenuListener.onRadialMenuButtonStateChanged(isActionDown);
            return;
        }

        if (binding == Binding.OPEN_NAVIGATION_MENU) {
            if (isActionDown) {
                if (!openNavigationMenuPressed) {
                    openNavigationMenuPressed = true;
                    if (openNavigationMenuCallback != null) openNavigationMenuCallback.run();
                }
            } else {
                openNavigationMenuPressed = false;
            }
            return;
        }

        if (binding == Binding.OPEN_CONTROLS_EDITOR) {
            if (isActionDown) {
                // The edit mode is running already: the button must not trigger again while it is edited.
                if (!openControlsEditorPressed && !editMode) {
                    openControlsEditorPressed = true;
                    if (openControlsEditorCallback != null) openControlsEditorCallback.run();
                }
            } else {
                openControlsEditorPressed = false;
            }
            return;
        }

        if (binding.isGamepad()) {
            if (profile == null) return;
            WinHandler winHandler = xServer != null ? xServer.getWinHandler() : null;
            GamepadState state = profile.getGamepadState();

            int buttonIdx = binding.ordinal() - Binding.GAMEPAD_BUTTON_A.ordinal();
            if (buttonIdx <= ExternalController.IDX_BUTTON_R2) {
                if (buttonIdx == ExternalController.IDX_BUTTON_L2) {
                    state.triggerL = isActionDown ? 1.0f : 0f;
                    state.setPressed(ExternalController.IDX_BUTTON_L2, isActionDown);
                } else if (buttonIdx == ExternalController.IDX_BUTTON_R2) {
                    state.triggerR = isActionDown ? 1.0f : 0f;
                    state.setPressed(ExternalController.IDX_BUTTON_R2, isActionDown);
                } else
                    state.setPressed(buttonIdx, isActionDown);
            }
            else if (binding == Binding.GAMEPAD_LEFT_THUMB_UP || binding == Binding.GAMEPAD_LEFT_THUMB_DOWN) {
                state.thumbLY = updateBaseStickAndGetMixedValue(binding, isActionDown, offset);
            }
            else if (binding == Binding.GAMEPAD_LEFT_THUMB_LEFT || binding == Binding.GAMEPAD_LEFT_THUMB_RIGHT) {
                state.thumbLX = updateBaseStickAndGetMixedValue(binding, isActionDown, offset);
            }
            else if (binding == Binding.GAMEPAD_RIGHT_THUMB_UP || binding == Binding.GAMEPAD_RIGHT_THUMB_DOWN) {
                state.thumbRY = updateBaseStickAndGetMixedValue(binding, isActionDown, offset);
            }
            else if (binding == Binding.GAMEPAD_RIGHT_THUMB_LEFT || binding == Binding.GAMEPAD_RIGHT_THUMB_RIGHT) {
                state.thumbRX = updateBaseStickAndGetMixedValue(binding, isActionDown, offset);
            }
            else if (binding == Binding.GAMEPAD_DPAD_UP || binding == Binding.GAMEPAD_DPAD_RIGHT ||
                     binding == Binding.GAMEPAD_DPAD_DOWN || binding == Binding.GAMEPAD_DPAD_LEFT) {
                state.dpad[binding.ordinal() - Binding.GAMEPAD_DPAD_UP.ordinal()] = isActionDown;
            }

            if (winHandler != null) {
                ExternalController controller = winHandler.getCurrentController();
                if (controller != null) controller.state.copy(state);
            }
        }
        else {
            if (binding == Binding.VOICE_CHAT) {
                if (isActionDown) {
                    if (!voiceChatPressed) {
                        voiceChatPressed = true;
                        if (voiceChatCallback != null) voiceChatCallback.run();
                    }
                } else {
                    voiceChatPressed = false;
                }
                return;
            }
            if (binding == Binding.SHOW_KEYBOARD) {
                if (isActionDown) {
                    if (!showKeyboardPressed) {
                        showKeyboardPressed = true;
                        if (showKeyboardCallback != null) showKeyboardCallback.run();
                    }
                } else {
                    showKeyboardPressed = false;
                }
                return;
            }
            else if (binding == Binding.ALT_ENTER) {
                if (isActionDown) {
                    xServer.injectKeyPress(Binding.KEY_ALT_L.keycode);
                    xServer.injectKeyPress(Binding.KEY_ENTER.keycode);
                }
                else {
                    xServer.injectKeyRelease(Binding.KEY_ENTER.keycode);
                    xServer.injectKeyRelease(Binding.KEY_ALT_L.keycode);
                }
                return;
            }
            else if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
                mouseMoveOffset.x = isActionDown ? (offset != 0 ? offset : (binding == Binding.MOUSE_MOVE_LEFT ? -1 : 1)) : 0;
                if (isActionDown) createMouseMoveTimer();
            }
            else if (binding == Binding.MOUSE_MOVE_DOWN || binding == Binding.MOUSE_MOVE_UP) {
                mouseMoveOffset.y = isActionDown ? (offset != 0 ? offset : (binding == Binding.MOUSE_MOVE_UP ? -1 : 1)) : 0;
                if (isActionDown) createMouseMoveTimer();
            }
            else {
                Pointer.Button pointerButton = binding.getPointerButton();
                if (isActionDown) {
                    if (pointerButton != null) {
                        xServer.injectPointerButtonPress(pointerButton);
                    }
                    else binding.inject(xServer, true);
                }
                else {
                    if (pointerButton != null) {
                        xServer.injectPointerButtonRelease(pointerButton);
                    }
                    else binding.inject(xServer, false);
                }
            }
        }
    }

    /** Records a non-gyro stick contribution and returns it blended with the gyro value. */
    public synchronized float updateBaseStickAndGetMixedValue(Binding binding, boolean isActionDown, float offset) {
        float base;
        float gyro;
        switch (binding) {
            case GAMEPAD_LEFT_THUMB_UP:
            case GAMEPAD_LEFT_THUMB_DOWN:
                baseThumbLY = updateBaseAxis(baseThumbLY, binding == Binding.GAMEPAD_LEFT_THUMB_UP,
                        isActionDown, offset);
                base = baseThumbLY;
                gyro = gyroThumbLY;
                break;
            case GAMEPAD_LEFT_THUMB_LEFT:
            case GAMEPAD_LEFT_THUMB_RIGHT:
                baseThumbLX = updateBaseAxis(baseThumbLX, binding == Binding.GAMEPAD_LEFT_THUMB_LEFT,
                        isActionDown, offset);
                base = baseThumbLX;
                gyro = gyroThumbLX;
                break;
            case GAMEPAD_RIGHT_THUMB_UP:
            case GAMEPAD_RIGHT_THUMB_DOWN:
                baseThumbRY = updateBaseAxis(baseThumbRY, binding == Binding.GAMEPAD_RIGHT_THUMB_UP,
                        isActionDown, offset);
                base = baseThumbRY;
                gyro = gyroThumbRY;
                break;
            case GAMEPAD_RIGHT_THUMB_LEFT:
            case GAMEPAD_RIGHT_THUMB_RIGHT:
                baseThumbRX = updateBaseAxis(baseThumbRX, binding == Binding.GAMEPAD_RIGHT_THUMB_LEFT,
                        isActionDown, offset);
                base = baseThumbRX;
                gyro = gyroThumbRX;
                break;
            default:
                return 0f;
        }
        return isGyroStickTarget(binding)
                ? Mathf.clamp(base + gyro, -1f, 1f)
                : (isActionDown ? offset : 0f);
    }

    /** Records a physical stick contribution using the source axis' raw direction. */
    public synchronized float updatePhysicalStickAndGetMixedValue(
            Binding binding, boolean isActionDown, float offset, int sourceKeyCode) {
        float base;
        float gyro;
        switch (binding) {
            case GAMEPAD_LEFT_THUMB_UP:
            case GAMEPAD_LEFT_THUMB_DOWN:
                baseThumbLY = updatePhysicalBaseAxis(baseThumbLY, isActionDown, offset, sourceKeyCode);
                base = baseThumbLY;
                gyro = gyroThumbLY;
                break;
            case GAMEPAD_LEFT_THUMB_LEFT:
            case GAMEPAD_LEFT_THUMB_RIGHT:
                baseThumbLX = updatePhysicalBaseAxis(baseThumbLX, isActionDown, offset, sourceKeyCode);
                base = baseThumbLX;
                gyro = gyroThumbLX;
                break;
            case GAMEPAD_RIGHT_THUMB_UP:
            case GAMEPAD_RIGHT_THUMB_DOWN:
                baseThumbRY = updatePhysicalBaseAxis(baseThumbRY, isActionDown, offset, sourceKeyCode);
                base = baseThumbRY;
                gyro = gyroThumbRY;
                break;
            case GAMEPAD_RIGHT_THUMB_LEFT:
            case GAMEPAD_RIGHT_THUMB_RIGHT:
                baseThumbRX = updatePhysicalBaseAxis(baseThumbRX, isActionDown, offset, sourceKeyCode);
                base = baseThumbRX;
                gyro = gyroThumbRX;
                break;
            default:
                return 0f;
        }
        return isGyroStickTarget(binding)
                ? Mathf.clamp(base + gyro, -1f, 1f)
                : (isActionDown ? offset : 0f);
    }

    private static float updateBaseAxis(float current, boolean negativeDirection, boolean isActionDown, float offset) {
        if (isActionDown) return offset;
        if (current == 0f) return 0f;
        boolean currentIsNegative = current < 0f;
        return currentIsNegative == negativeDirection ? 0f : current;
    }

    public static float updatePhysicalBaseAxis(float current, boolean isActionDown, float offset, int sourceKeyCode) {
        if (isActionDown) return offset;
        if (current == 0f) return 0f;
        int sourceDirection = getPhysicalSourceDirection(sourceKeyCode);
        if (sourceDirection == 0) return 0f;
        return (current < 0f) == (sourceDirection < 0) ? 0f : current;
    }

    private static int getPhysicalSourceDirection(int sourceKeyCode) {
        switch (sourceKeyCode) {
            case ExternalControllerBinding.AXIS_X_NEGATIVE:
            case ExternalControllerBinding.AXIS_Y_POSITIVE:
            case ExternalControllerBinding.AXIS_Z_NEGATIVE:
            case ExternalControllerBinding.AXIS_RZ_POSITIVE:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
                return -1;
            case ExternalControllerBinding.AXIS_X_POSITIVE:
            case ExternalControllerBinding.AXIS_Y_NEGATIVE:
            case ExternalControllerBinding.AXIS_Z_POSITIVE:
            case ExternalControllerBinding.AXIS_RZ_NEGATIVE:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return 1;
            default:
                return 0;
        }
    }

    private boolean isGyroStickTarget(Binding binding) {
        if (gyroMode == GyroSettings.MODE_LEFT_STICK) {
            return binding == Binding.GAMEPAD_LEFT_THUMB_UP
                    || binding == Binding.GAMEPAD_LEFT_THUMB_RIGHT
                    || binding == Binding.GAMEPAD_LEFT_THUMB_DOWN
                    || binding == Binding.GAMEPAD_LEFT_THUMB_LEFT;
        }
        if (gyroMode == GyroSettings.MODE_RIGHT_STICK) {
            return binding == Binding.GAMEPAD_RIGHT_THUMB_UP
                    || binding == Binding.GAMEPAD_RIGHT_THUMB_RIGHT
                    || binding == Binding.GAMEPAD_RIGHT_THUMB_DOWN
                    || binding == Binding.GAMEPAD_RIGHT_THUMB_LEFT;
        }
        return false;
    }

    private void updateGyroMouse(int x, int y) {
        if (xServer == null || (x == 0 && y == 0)) return;
        if (xServer.isRelativeMouseMovement()) {
            xServer.getWinHandler().mouseEvent(MouseEventFlags.MOVE, x, y, 0);
        }
        else {
            xServer.injectPointerMoveDelta(x, y);
        }
    }

    private synchronized void updateGyroStick(boolean rightStick, float x, float y) {
        if (gyroProfile == null) return;
        GamepadState state = gyroProfile.getGamepadState();
        final float mixedX;
        final float mixedY;
        if (rightStick) {
            gyroThumbRX = x;
            gyroThumbRY = y;
            mixedX = Mathf.clamp(baseThumbRX + gyroThumbRX, -1f, 1f);
            mixedY = Mathf.clamp(baseThumbRY + gyroThumbRY, -1f, 1f);
        }
        else {
            gyroThumbLX = x;
            gyroThumbLY = y;
            mixedX = Mathf.clamp(baseThumbLX + gyroThumbLX, -1f, 1f);
            mixedY = Mathf.clamp(baseThumbLY + gyroThumbLY, -1f, 1f);
        }
        if (!state.updateThumbstick(rightStick, mixedX, mixedY)) return;

        WinHandler winHandler = xServer != null ? xServer.getWinHandler() : null;
        if (winHandler != null) {
            ExternalController controller = winHandler.getCurrentController();
            if (controller != null) controller.state.copyThumbstick(state, rightStick);
            winHandler.sendGamepadState();
            winHandler.sendVirtualGamepadState(state);
        }
    }

    private synchronized void resetThumbContributions() {
        ControlsProfile outgoingProfile = gyroProfile;
        baseThumbLX = 0f;
        baseThumbLY = 0f;
        baseThumbRX = 0f;
        baseThumbRY = 0f;
        gyroThumbLX = 0f;
        gyroThumbLY = 0f;
        gyroThumbRX = 0f;
        gyroThumbRY = 0f;

        if (outgoingProfile == null) return;
        GamepadState state = outgoingProfile.getGamepadState();
        state.thumbLX = 0f;
        state.thumbLY = 0f;
        state.thumbRX = 0f;
        state.thumbRY = 0f;

        WinHandler winHandler = xServer != null ? xServer.getWinHandler() : null;
        if (winHandler != null) {
            winHandler.sendGamepadState();
            winHandler.sendVirtualGamepadState(state);
        }
    }

    public Bitmap getIcon(byte id) {
        if (icons[id] == null) {
            Context context = getContext();
            try (InputStream is = context.getAssets().open("inputcontrols/icons/"+id+".png")) {
                icons[id] = BitmapFactory.decodeStream(is);
            }
            catch (IOException e) {}
        }
        return icons[id];
    }
}

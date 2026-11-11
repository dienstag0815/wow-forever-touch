package com.winlator.inputcontrols;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;

import androidx.core.graphics.ColorUtils;

import app.gamenative.ui.screen.wow.WindowVisibility;
import app.gamenative.ui.screen.wow.chatbar.ChatAnchor;
import app.gamenative.ui.screen.wow.chatbar.ChatBarLogic;
import app.gamenative.ui.screen.wow.chatbar.ChatButton;
import app.gamenative.ui.screen.wow.chatbar.ChatGlyphDraw;
import app.gamenative.ui.screen.wow.chatbar.ChatShow;
import app.gamenative.ui.screen.wow.klick.LatchMode;
import app.gamenative.ui.screen.wow.klick.ModKey;
import app.gamenative.ui.screen.wow.klick.StickyModifiers;
import app.gamenative.ui.screen.wow.klick.XServerKeySink;
import app.gamenative.wowaction.BindingCaption;
import app.gamenative.wowaction.StandardNames;
import app.gamenative.cooldown.CooldownDraw;
import app.gamenative.ui.touchstyle.KeyGlyphDraw;
import app.gamenative.ui.touchstyle.KeyGlyphs;
import app.gamenative.ui.touchstyle.KeyLabel;
import app.gamenative.ui.touchstyle.RadialSlotVisibility;
import app.gamenative.ui.touchstyle.TouchStyleDraw;
import app.gamenative.wowaction.WowSymbols;
import app.gamenative.wowicons.WowIconDraw;
import app.gamenative.wowicons.WowIcons;

import com.winlator.core.CubicBezierInterpolator;
import com.winlator.math.Mathf;
import com.winlator.widget.InputControlsView;
import com.winlator.widget.TouchpadView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ControlElement {
    public static final float STICK_DEAD_ZONE = 0.15f;
    /** Dead zone of the analog axis output, as before (the digital state above uses STICK_DEAD_ZONE). */
    public static final float STICK_AXIS_DEAD_ZONE = 0.01f;
    public static final float DPAD_DEAD_ZONE = 0.3f;
    /** Gain of the left (walk) and right (camera) on-screen stick, set from the launcher before the game starts. */
    public static volatile float stickSensitivityWalk = 1.25f;
    public static volatile float stickSensitivityCamera = 1.0f;
    public static final float TRACKPAD_MIN_SPEED = 0.8f;
    public static final float TRACKPAD_MAX_SPEED = 20.0f;
    public static final byte TRACKPAD_ACCELERATION_THRESHOLD = 4;
    public static final short BUTTON_MIN_TIME_TO_KEEP_PRESSED = 300;
    public static final int DEFAULT_BUTTON_COLOR = 0x00ffffff;
    public static final int DEFAULT_BUTTON_ACTIVE_COLOR = 0x00ffffff;
    public static final float INHERIT_BUTTON_OPACITY = -1.0f;
    public static final float DEFAULT_BUTTON_STROKE_SCALE = 1.0f;
    public static final boolean DEFAULT_LOOK_THROUGH = false;
    public static final float DEFAULT_SIZE_FACTOR = 1.0f;
    public static final float MIN_SIZE_FACTOR = 0.2f;
    public static final float MAX_SIZE_FACTOR = 5.0f;
    /** ROUND_RECT corner radius as a fraction of half the shorter side; 1 = fully round ends (the old look). */
    public static final float DEFAULT_CORNER_RADIUS = 1.0f;
    /** Most entries of a key strip with the free list ("Eigene"). */
    public static final int MAX_RANGE_ITEMS = 40;
    /** Entries a growing free list shows at once without the player raising "Sichtbare Tasten". */
    private static final int RANGE_AUTO_VISIBLE = 8;
    /** A free list shows at least this many entries (as far as it has them) when one is added: the strip is never a sliver of two keys. */
    private static final int RANGE_MIN_VISIBLE = 4;
    /** Start template of a new free list ("Eigene"): keys that look nothing like A-Z or 0-9, so the change shows at once. */
    private static final Binding[] START_RANGE_KEYS = {Binding.KEY_ENTER, Binding.KEY_SPACE, Binding.KEY_TAB, Binding.KEY_ESC, Binding.KEY_BKSP};
    /** Keys a free list shows at once after its start template was set. */
    private static final int START_RANGE_VISIBLE = 5;
    public static final int DEFAULT_ARC_SWEEP = 90;
    public static final int MIN_ARC_SWEEP = 20;
    public static final int MAX_ARC_SWEEP = 180;
    /** Thickness of an ARC button as a fraction of its outer radius. */
    public static final float ARC_THICKNESS = 0.4f;
    public enum Type {
        BUTTON, D_PAD, RANGE_BUTTON, STICK, TRACKPAD, SHOOTER_MODE, SCROLL_WHEEL;

        public static String[] names() {
            Type[] types = values();
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) names[i] = types[i].name().replace("_", "-");
            return names;
        }
    }
    public enum Shape {
        CIRCLE, RECT, ROUND_RECT, SQUARE, PILL, ARC;

        public static String[] names() {
            Shape[] shapes = values();
            String[] names = new String[shapes.length];
            for (int i = 0; i < shapes.length; i++) names[i] = shapes[i].name().replace("_", " ");
            return names;
        }
    }
    public enum Range {
        FROM_A_TO_Z(26), FROM_0_TO_9(10), FROM_F1_TO_F12(12), FROM_NP0_TO_NP9(10),
        /** Free list of entries ("Eigene"): count and assignments come from the element, see {@link ControlElement#getRangeItem}. */
        CUSTOM(0);
        public final byte max;

        Range(int max) {
            this.max = (byte)max;
        }

        /** Key of entry [index] of a template range; NONE for the free list and for indexes outside. */
        public Binding bindingAt(int index) {
            if (index < 0 || index >= max) return Binding.NONE;
            switch (this) {
                case FROM_A_TO_Z:
                    return Binding.valueOf("KEY_" + ((char)(65 + index)));
                case FROM_0_TO_9:
                    return Binding.valueOf("KEY_" + ((index + 1) % 10));
                case FROM_F1_TO_F12:
                    return Binding.valueOf("KEY_F" + (index + 1));
                case FROM_NP0_TO_NP9:
                    return Binding.valueOf("KEY_KP_" + ((index + 1) % 10));
                default:
                    return Binding.NONE;
            }
        }

        public static String[] names() {
            Range[] ranges = values();
            String[] names = new String[ranges.length];
            for (int i = 0; i < ranges.length; i++) names[i] = ranges[i].name().replace("_", " ");
            return names;
        }
    }
    private final InputControlsView inputControlsView;
    private Type type = Type.BUTTON;
    private Shape shape = Shape.CIRCLE;
    private BindingCombo[] bindings = {BindingCombo.none(), BindingCombo.none(), BindingCombo.none(), BindingCombo.none()};
    private Object[] bindingSources = createBindingSources(bindings.length);
    private float scale = 1.0f;
    private short x;
    private short y;
    private boolean selected = false;
    private boolean toggleSwitch = false;
    /** A Shift/Ctrl/Alt button latches: tap = next click or key, double tap = fixed, hold = while held. */
    private boolean stickyModifier = true;
    private boolean repaintTargetSet = false;
    private boolean scrollLocked = false;
    private int currentPointerId = -1;
    private boolean currentPointerActivatedButtonBindings = false;
    private final Rect boundingBox = new Rect();
    private boolean[] states = new boolean[4];
    private boolean[] gamepadAxisActive = new boolean[4];
    private boolean radialMenuTouchActive = false;
    private boolean boundingBoxNeedsUpdate = true;
    private String text = "";
    private byte iconId;
    private Range range;
    // Entries of a key strip with Range.CUSTOM (assigned like the fields of a key ring); kept in memory when switching to a template.
    private final ArrayList<RadialMenu.Slot> rangeItems = new ArrayList<>();
    private byte orientation;
    private PointF currentPosition;
    private RangeScroller scroller;
    private CubicBezierInterpolator interpolator;
    private Object touchTime;
    private String shooterMovementType = "wasd";
    private String shooterLookType = "mouse";
    private float shooterLookSensitivity = 1.0f;
    private float shooterJoystickSize = 1.0f;
    private int buttonColor = DEFAULT_BUTTON_COLOR;
    private int buttonActiveColor = DEFAULT_BUTTON_ACTIVE_COLOR;
    private boolean buttonActiveColorCustom = false;
    private float buttonOpacity = INHERIT_BUTTON_OPACITY;
    private float buttonStrokeScale = DEFAULT_BUTTON_STROKE_SCALE;
    // Null is reserved for buttons loaded from profiles that predate general
    // look-through. Those buttons retain their legacy shooter-only setting.
    private Boolean lookThrough = DEFAULT_LOOK_THROUGH;
    private boolean shooterLookThrough = true;
    // Id of this radial button's own menu inside the profile; null/unknown = the profile's default menu.
    private String radialMenuId;
    // Where this radial button's menu opens: "up", "down", "left", "right"; null = automatic, by screen area (RadialOpen).
    private String radialDirection;
    // Id of a WoW action (e.g. TOGGLEWORLDMAP) behind the primary binding of a button; null = plain key.
    private String wowAction;
    // Chat text (e.g. "/dance") a button types into the WoW chat when pressed instead of sending a key; null = plain key.
    private String chatCommand;
    // Chat control of a button (ChatButton id: voice, send, keyboard, channel, cancel); null = a normal key/command button.
    // Runs through InputControlsView.runChatAction instead of a key; the binding stays empty.
    private String chatAction;
    // When the button is shown (ChatShow key): null = always, "chat" = while the chat window is visible, "box" = while the chat line is open.
    private String chatShow;
    // Corner of the chat window the button is pinned to (ChatAnchor key: tl, tr, bl, br); null = free. The offset of the button's
    // middle from that corner is stored as a fraction of the view size and only known once it was measured in the editor.
    private String chatAnchor;
    private float chatAnchorDx;
    private float chatAnchorDy;
    private boolean chatAnchorKnown = false;
    // Behaviour while big game windows are open (WindowVisibility key): null = automatic (the setting of the launcher), "keep", "hide".
    private String windowRule;
    // Free shape of buttons and scroll wheels: on-screen width/height factors on top of scale, 1 = old behaviour.
    private float widthScale = DEFAULT_SIZE_FACTOR;
    private float heightScale = DEFAULT_SIZE_FACTOR;
    // Quarter turns in degrees (0/90/180/270): swaps width and height of buttons, turns an ARC.
    private int rotation = 0;
    private float cornerRadius = DEFAULT_CORNER_RADIUS;
    private int arcSweep = DEFAULT_ARC_SWEEP;
    // Scroll wheel: tick distance in grid units, direction, coasting after the finger leaves.
    private float scrollStep = ScrollWheelMath.DEFAULT_STEP;
    private boolean scrollReverse = false;
    private boolean scrollInertia = false;
    private float wheelPhase;
    private float wheelAccum;
    private float wheelLastPos;
    private float wheelStartX;
    private float wheelStartY;
    private float wheelLastX;
    private float wheelLastY;
    private float wheelVelocity;
    private long wheelDownTime;
    private long wheelLastMoveTime;
    private boolean wheelScrolled = false;
    private boolean wheelModifierHeld = false;
    private int wheelInertiaGeneration = 0;
    // Key ring around a stick: 0 = off, else 4/6/8 segments, each assigned like a radial menu field.
    private int ringSegments = 0;
    private float ringStartAngle = StickRing.DEFAULT_START_ANGLE;
    private float ringWidth = StickRing.DEFAULT_WIDTH;
    private float ringGap = StickRing.DEFAULT_GAP;
    private final ArrayList<RadialMenu.Slot> ringItems = new ArrayList<>();
    // Optional second, outer ring: entry i belongs to segment i, null = that segment has no outer field.
    private final ArrayList<RadialMenu.Slot> ringOuterItems = new ArrayList<>();
    private int stickRadiusPx;
    // Stick centre: a short tap on the middle without deflection triggers this assignment (null = none).
    private RadialMenu.Slot centerItem;
    private long centerDownTime;
    private float centerMaxOffset;
    // Sensitivity of the tap on the middle (see StickCenter): largest distance in dp and longest time in ms.
    private float centerTapDp = StickCenter.DEFAULT_TAP_DP;
    private long centerTapMillis = StickCenter.DEFAULT_TAP_MILLIS;
    // True from touch down until it is clear whether the finger taps or drags (stick with a middle assignment only).
    private boolean centerUndecided;
    private int centerGeneration;
    private float centerLastX;
    private float centerLastY;
    private int ringPointerId = -1;
    private int ringActiveSegment = -1;
    private boolean ringActiveOuter = false;
    private BindingCombo ringPressedCombo;
    private final RectF arcOuterRect = new RectF();
    private final RectF arcInnerRect = new RectF();
    // Square of the action symbol in a button (see computeIconBox), also used for the cooldown picture on top of it.
    private final RectF iconBox = new RectF();
    private int iconKind;
    private float iconRadius;

    public ControlElement(InputControlsView inputControlsView) {
        this.inputControlsView = inputControlsView;
    }

    private static Object[] createBindingSources(int count) {
        Object[] sources = new Object[count];
        for (int i = 0; i < count; i++) sources[i] = new Object();
        return sources;
    }

    private void reset() {
        setBinding(Binding.NONE);
        scroller = null;
        lookThrough = DEFAULT_LOOK_THROUGH;
        radialMenuId = null;
        radialDirection = null;
        wowAction = null;
        chatCommand = null;
        resetExtras();

        if (type == Type.STICK) {
            bindings[0] = BindingCombo.of(Binding.GAMEPAD_LEFT_THUMB_UP);
            bindings[1] = BindingCombo.of(Binding.GAMEPAD_LEFT_THUMB_RIGHT);
            bindings[2] = BindingCombo.of(Binding.GAMEPAD_LEFT_THUMB_DOWN);
            bindings[3] = BindingCombo.of(Binding.GAMEPAD_LEFT_THUMB_LEFT);
        }
        else if (type == Type.D_PAD) {
            bindings[0] = BindingCombo.of(Binding.GAMEPAD_DPAD_UP);
            bindings[1] = BindingCombo.of(Binding.GAMEPAD_DPAD_RIGHT);
            bindings[2] = BindingCombo.of(Binding.GAMEPAD_DPAD_DOWN);
            bindings[3] = BindingCombo.of(Binding.GAMEPAD_DPAD_LEFT);
        }
        else if (type == Type.TRACKPAD) {
            bindings[0] = BindingCombo.of(Binding.MOUSE_MOVE_UP);
            bindings[1] = BindingCombo.of(Binding.MOUSE_MOVE_RIGHT);
            bindings[2] = BindingCombo.of(Binding.MOUSE_MOVE_DOWN);
            bindings[3] = BindingCombo.of(Binding.MOUSE_MOVE_LEFT);
        }
        else if (type == Type.RANGE_BUTTON) {
            scroller = new RangeScroller(inputControlsView, this);
        }
        else if (type == Type.SCROLL_WHEEL) {
            // Tap without swiping = middle click, binding 1 is held while scrolling (none by default).
            bindings[0] = BindingCombo.of(Binding.MOUSE_MIDDLE_BUTTON);
            shape = Shape.ROUND_RECT;
        }
        else if (type == Type.SHOOTER_MODE) {
            shape = Shape.CIRCLE;
            iconId = 7;
            shooterMovementType = "wasd";
            shooterLookType = "mouse";
            shooterLookSensitivity = 1.0f;
            shooterJoystickSize = 1.0f;
        }

        text = "";
        if (type != Type.SHOOTER_MODE) iconId = 0;
        range = null;
        boundingBoxNeedsUpdate = true;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
        reset();
    }

    public void setTypeWithoutReset(Type type) {
        this.type = type;
        if (type == Type.RANGE_BUTTON && scroller == null) {
            scroller = new RangeScroller(inputControlsView, this);
        }
        boundingBoxNeedsUpdate = true;
    }

    public int getBindingCount() {
        return bindings.length;
    }

    public void setBindingCount(int bindingCount) {
        bindings = new BindingCombo[bindingCount];
        bindingSources = createBindingSources(bindingCount);
        setBinding(Binding.NONE);
        states = new boolean[bindingCount];
        gamepadAxisActive = new boolean[bindingCount];
        boundingBoxNeedsUpdate = true;
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
        boundingBoxNeedsUpdate = true;
    }

    public Range getRange() {
        return range != null ? range : Range.FROM_A_TO_Z;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    // ---- Key strip with free entries (Range.CUSTOM) -----------------------------------------------------------

    public boolean isCustomRange() {
        return range == Range.CUSTOM;
    }

    /** Entries stored for the free list (also while a template is chosen). */
    public int getRangeItemCount() {
        return rangeItems.size();
    }

    /** Number of entries the strip scrolls through: those of the free list, else those of the template. */
    public int getRangeCount() {
        return range == Range.CUSTOM ? rangeItems.size() : getRange().max;
    }

    /** Entry [index] of the free list, null outside of it. */
    public RadialMenu.Slot getRangeItem(int index) {
        return index >= 0 && index < rangeItems.size() ? rangeItems.get(index) : null;
    }

    public void setRangeItem(int index, RadialMenu.Slot item) {
        if (index < 0 || index >= rangeItems.size()) return;
        rangeItems.set(index, item != null ? item : new RadialMenu.Slot());
    }

    /**
     * Appends an empty entry and returns its index, -1 at {@link #MAX_RANGE_ITEMS}. When every entry was visible until now the
     * strip grows with it (up to eight entries), and a strip with fewer than four keys on show grows to four (as far as it has
     * entries), so a new entry shows up at once instead of hiding behind the scrolling.
     */
    public int addRangeItem() {
        if (rangeItems.size() >= MAX_RANGE_ITEMS) return -1;
        boolean allVisible = bindings.length >= rangeItems.size();
        rangeItems.add(new RadialMenu.Slot());
        int shown = bindings.length;
        if (allVisible && shown < RANGE_AUTO_VISIBLE) shown = Math.min(rangeItems.size(), RANGE_AUTO_VISIBLE);
        // A strip with fewer than four keys on show grows with its entries even when it hid some before (a new entry must be seen).
        shown = Math.max(shown, Math.min(rangeItems.size(), RANGE_MIN_VISIBLE));
        if (shown != bindings.length) setBindingCount(shown);
        boundingBoxNeedsUpdate = true;
        return rangeItems.size() - 1;
    }

    /** Removes entry [index]; false (nothing removed) for an unknown index and for the last entry. */
    public boolean removeRangeItem(int index) {
        if (index < 0 || index >= rangeItems.size() || rangeItems.size() <= 1) return false;
        rangeItems.remove(index);
        clampRangeVisible();
        return true;
    }

    /** Moves entry [from] to position [to] (the entries in between move up or down by one); false if nothing changed. */
    public boolean moveRangeItem(int from, int to) {
        if (from < 0 || from >= rangeItems.size() || to < 0 || to >= rangeItems.size() || from == to) return false;
        rangeItems.add(to, rangeItems.remove(from));
        return true;
    }

    /**
     * Switches the strip to the free list. From a template (A-Z, 1-0, F1-F12) the free list starts with its own start template
     * (Enter, Space, Tab, Escape, Backspace, five keys shown), so the change is visible; every entry can then be changed on its own.
     * An already free list stays as it is, except that an empty one gets one entry.
     */
    public void convertToCustomRange() {
        if (getRange() == Range.CUSTOM) {
            if (rangeItems.isEmpty()) rangeItems.add(new RadialMenu.Slot());
        }
        else {
            rangeItems.clear();
            for (Binding key : START_RANGE_KEYS) rangeItems.add(new RadialMenu.Slot("", BindingCombo.of(key)));
            range = Range.CUSTOM;
            setBindingCount(Math.min(START_RANGE_VISIBLE, rangeItems.size()));
        }
        clampRangeVisible();
        boundingBoxNeedsUpdate = true;
    }

    /** A free list with no entry (damaged file) falls back to the default template instead of an empty strip. */
    public void normalizeCustomRange() {
        if (range == Range.CUSTOM && rangeItems.isEmpty()) range = Range.FROM_A_TO_Z;
    }

    /** The free list never shows more entries at once than it has. */
    public void clampRangeVisible() {
        if (type != Type.RANGE_BUTTON || range != Range.CUSTOM || rangeItems.isEmpty()) return;
        if (bindings.length > rangeItems.size()) setBindingCount(rangeItems.size());
    }

    public byte getOrientation() {
        return orientation;
    }

    public void setOrientation(byte orientation) {
        this.orientation = orientation;
        boundingBoxNeedsUpdate = true;
    }

    public boolean isStickyModifier() {
        return stickyModifier;
    }

    public void setStickyModifier(boolean stickyModifier) {
        this.stickyModifier = stickyModifier;
    }

    /**
     * The modifier this button latches, or null when it is not a plain Shift/Ctrl/Alt button (other keys, combos,
     * toggle switch, chat command, radial menu, or the player switched the latching off for this button).
     */
    private ModKey stickyModKey() {
        if (type != Type.BUTTON || !stickyModifier || chatCommand != null || chatAction != null || usesToggleSwitch()) return null;
        BindingCombo first = getBindingComboAt(0);
        BindingCombo second = getBindingComboAt(1);
        if ((!first.isEmpty() && !first.isSingleBinding()) || (!second.isEmpty() && !second.isSingleBinding())) return null;
        return StickyModifiers.modifierOfButton(first.getPrimaryBinding().name(), second.getPrimaryBinding().name());
    }

    private void prepareStickyModifiers() {
        XServerKeySink.attach(inputControlsView.getXServer());
        if (!repaintTargetSet) {
            StickyModifiers.setRepaintTarget(inputControlsView);
            repaintTargetSet = true;
        }
    }

    public boolean isToggleSwitch() {
        return toggleSwitch;
    }

    public void setToggleSwitch(boolean toggleSwitch) {
        this.toggleSwitch = toggleSwitch;
    }

    public boolean isScrollLocked() {
        return scrollLocked;
    }

    public void setScrollLocked(boolean scrollLocked) {
        this.scrollLocked = scrollLocked;
    }

    public Binding getBindingAt(int index) {
        return getBindingComboAt(index).getPrimaryBinding();
    }

    public BindingCombo getBindingComboAt(int index) {
        return index < bindings.length ? bindings[index] : BindingCombo.none();
    }

    public void setBindingAt(int index, Binding binding) {
        setBindingComboAt(index, BindingCombo.of(binding));
    }

    public void setBindingComboAt(int index, BindingCombo binding) {
        if (index >= bindings.length) {
            int oldLength = bindings.length;
            bindings = Arrays.copyOf(bindings, index+1);
            Arrays.fill(bindings, oldLength, bindings.length, BindingCombo.none());
            bindingSources = Arrays.copyOf(bindingSources, bindings.length);
            for (int i = oldLength; i < bindingSources.length; i++) bindingSources[i] = new Object();
            states = new boolean[bindings.length];
            gamepadAxisActive = new boolean[bindings.length];
            boundingBoxNeedsUpdate = true;
        }
        bindings[index] = binding != null ? binding : BindingCombo.none();
    }

    public void setBinding(Binding binding) {
        Arrays.fill(bindings, BindingCombo.of(binding));
    }

    private void handleBindingInputEvent(int index, boolean isActionDown) {
        BindingCombo bindingCombo = getBindingComboAt(index);
        if (bindingCombo.isSingleBinding()) {
            Binding binding = bindingCombo.getPrimaryBinding();
            if (binding == Binding.GYRO_MODIFIER) {
                inputControlsView.handleInputEvent(binding, isActionDown, 0f, bindingSources[index]);
            }
            else inputControlsView.handleInputEvent(binding, isActionDown);
        }
        else if (bindingCombo.contains(Binding.GYRO_MODIFIER)) {
            inputControlsView.handleInputEvent(bindingCombo, isActionDown, 0f, bindingSources[index]);
        }
        else inputControlsView.handleInputEvent(bindingCombo, isActionDown);
    }

    private void handleBindingInputEvent(int index, boolean isActionDown, float offset) {
        BindingCombo bindingCombo = getBindingComboAt(index);
        if (bindingCombo.isSingleBinding()) {
            Binding binding = bindingCombo.getPrimaryBinding();
            if (binding == Binding.GYRO_MODIFIER) {
                inputControlsView.handleInputEvent(binding, isActionDown, offset, bindingSources[index]);
            }
            else inputControlsView.handleInputEvent(binding, isActionDown, offset);
        }
        else if (bindingCombo.contains(Binding.GYRO_MODIFIER)) {
            inputControlsView.handleInputEvent(bindingCombo, isActionDown, offset, bindingSources[index]);
        }
        else inputControlsView.handleInputEvent(bindingCombo, isActionDown, offset);
    }

    private void handleSimultaneousBindingMembers(
            int bindingIndex,
            Binding excludedBinding,
            boolean isActionDown,
            float offset) {
        BindingCombo bindingCombo = getBindingComboAt(bindingIndex);
        List<Binding> comboBindings = bindingCombo.getBindings();
        if (isActionDown) {
            for (Binding binding : comboBindings) {
                if (binding != excludedBinding) {
                    if (binding == Binding.GYRO_MODIFIER) {
                        inputControlsView.handleInputEvent(binding, true, offset, bindingSources[bindingIndex]);
                    }
                    else inputControlsView.handleInputEvent(binding, true, offset);
                }
            }
        }
        else {
            for (int i = comboBindings.size() - 1; i >= 0; i--) {
                Binding binding = comboBindings.get(i);
                if (binding != excludedBinding) {
                    if (binding == Binding.GYRO_MODIFIER) {
                        inputControlsView.handleInputEvent(binding, false, offset, bindingSources[bindingIndex]);
                    }
                    else inputControlsView.handleInputEvent(binding, false, offset);
                }
            }
        }
    }

    private Binding findMouseMoveBinding(BindingCombo bindingCombo) {
        for (Binding binding : bindingCombo.getBindings()) {
            if (binding.isMouseMove()) return binding;
        }
        return Binding.NONE;
    }

    /** True for the four right-stick axis bindings (the camera); the sheet of the stick uses it to tell which sensitivity applies. */
    public static boolean isRightThumb(Binding binding) {
        return binding.ordinal() >= Binding.GAMEPAD_RIGHT_THUMB_UP.ordinal() &&
                binding.ordinal() <= Binding.GAMEPAD_RIGHT_THUMB_LEFT.ordinal();
    }

    private Binding findGamepadAxisBinding(BindingCombo bindingCombo) {
        for (Binding binding : bindingCombo.getBindings()) {
            if (binding.isGamepadAxis()) return binding;
        }
        return Binding.NONE;
    }

    public String getShooterMovementType() {
        return shooterMovementType;
    }

    public void setShooterMovementType(String shooterMovementType) {
        this.shooterMovementType = shooterMovementType != null ? shooterMovementType : "wasd";
    }

    public String getShooterLookType() {
        return shooterLookType;
    }

    public void setShooterLookType(String shooterLookType) {
        this.shooterLookType = shooterLookType != null ? shooterLookType : "mouse";
    }

    public float getShooterLookSensitivity() {
        return shooterLookSensitivity;
    }

    public void setShooterLookSensitivity(float shooterLookSensitivity) {
        this.shooterLookSensitivity = shooterLookSensitivity;
    }

    public float getShooterJoystickSize() {
        return shooterJoystickSize;
    }

    public void setShooterJoystickSize(float shooterJoystickSize) {
        this.shooterJoystickSize = shooterJoystickSize;
    }

    public int getButtonColor() {
        return buttonColor;
    }

    public void setButtonColor(int buttonColor) {
        this.buttonColor = buttonColor & 0x00ffffff;
    }

    public int getButtonActiveColor() {
        return buttonActiveColor;
    }

    public void setButtonActiveColor(int buttonActiveColor) {
        setButtonActiveColor(buttonActiveColor, true);
    }

    public void setButtonActiveColor(int buttonActiveColor, boolean custom) {
        this.buttonActiveColor = buttonActiveColor & 0x00ffffff;
        this.buttonActiveColorCustom = custom;
    }

    public boolean hasCustomButtonActiveColor() {
        return buttonActiveColorCustom;
    }

    public float getButtonOpacity() {
        return buttonOpacity;
    }

    public void setButtonOpacity(float buttonOpacity) {
        if (buttonOpacity < 0) {
            this.buttonOpacity = INHERIT_BUTTON_OPACITY;
        }
        else {
            this.buttonOpacity = Mathf.clamp(buttonOpacity, 0.0f, 1.0f);
        }
    }

    public float getButtonStrokeScale() {
        return buttonStrokeScale;
    }

    public void setButtonStrokeScale(float buttonStrokeScale) {
        this.buttonStrokeScale = Mathf.clamp(buttonStrokeScale, 0.5f, 2.0f);
    }

    public boolean isLookThrough() {
        return Boolean.TRUE.equals(lookThrough) && !isRadialMenuButton();
    }

    public Boolean getLookThroughSetting() {
        return lookThrough;
    }

    public void setLookThroughSetting(Boolean lookThrough) {
        this.lookThrough = lookThrough;
    }

    public boolean isShooterLookThrough() {
        return !isRadialMenuButton() && (lookThrough != null ? lookThrough : shooterLookThrough);
    }

    public void setShooterLookThrough(boolean shooterLookThrough) {
        this.shooterLookThrough = shooterLookThrough;
    }

    public boolean getShooterLookThroughSetting() {
        return shooterLookThrough;
    }

    public void copyButtonAppearanceFrom(ControlElement element) {
        buttonColor = element.buttonColor;
        buttonActiveColor = element.buttonActiveColor;
        buttonActiveColorCustom = element.buttonActiveColorCustom;
        buttonOpacity = element.buttonOpacity;
        buttonStrokeScale = element.buttonStrokeScale;
        lookThrough = element.lookThrough;
        shooterLookThrough = element.shooterLookThrough;
    }

    public String getRadialMenuId() {
        return radialMenuId;
    }

    public void setRadialMenuId(String radialMenuId) {
        this.radialMenuId = radialMenuId != null && !radialMenuId.isEmpty() ? radialMenuId : null;
    }

    /** Where the radial menu of this button opens ("up", "down", "left", "right"); null = automatic, by screen area. */
    public String getRadialDirection() {
        return radialDirection;
    }

    /** Unknown or empty values mean automatic (old profiles). */
    public void setRadialDirection(String direction) {
        boolean known = "up".equals(direction) || "down".equals(direction) || "left".equals(direction) || "right".equals(direction);
        this.radialDirection = known ? direction : null;
    }

    public String getWowAction() {
        return wowAction;
    }

    public void setWowAction(String wowAction) {
        this.wowAction = wowAction != null && !wowAction.isEmpty() ? wowAction : null;
    }

    public String getChatCommand() {
        return chatCommand;
    }

    public void setChatCommand(String chatCommand) {
        String trimmed = chatCommand != null ? chatCommand.trim() : "";
        this.chatCommand = trimmed.isEmpty() ? null : trimmed;
    }

    /** Id of the chat control this button runs (ChatButton id), or null. */
    public String getChatAction() {
        return chatAction;
    }

    /** Unknown or empty ids mean none. */
    public void setChatAction(String chatAction) {
        this.chatAction = ChatButton.fromId(chatAction) != null ? chatAction : null;
    }

    /** When the button is shown (ChatShow key); null = always. */
    public String getChatShow() {
        return chatShow;
    }

    public void setChatShow(String chatShow) {
        this.chatShow = ChatShow.normalize(chatShow);
    }

    /** Corner of the chat window the button is pinned to (ChatAnchor key), or null = free. */
    public String getChatAnchor() {
        return chatAnchor;
    }

    /** A new corner (or none) forgets the measured offset: it is measured again against the chat window the next time the editor saves. */
    public void setChatAnchor(String corner) {
        String normalized = ChatAnchor.normalize(corner);
        if (normalized == null ? chatAnchor == null : normalized.equals(chatAnchor)) return;
        chatAnchor = normalized;
        chatAnchorKnown = false;
        chatAnchorDx = 0f;
        chatAnchorDy = 0f;
    }

    /** True once the offset from the chat corner is known (the button then follows the chat window). */
    public boolean isChatAnchorKnown() {
        return chatAnchor != null && chatAnchorKnown;
    }

    public float getChatAnchorDx() {
        return chatAnchorDx;
    }

    public float getChatAnchorDy() {
        return chatAnchorDy;
    }

    /** Sets the offset of the middle from the chat corner as fractions of the view size. */
    public void setChatAnchorOffset(float dx, float dy) {
        if (chatAnchor == null) return;
        chatAnchorDx = dx;
        chatAnchorDy = dy;
        chatAnchorKnown = true;
    }

    /** Behaviour while game windows are open (WindowVisibility key); null = automatic. */
    public String getWindowRule() {
        return windowRule;
    }

    public void setWindowRule(String windowRule) {
        this.windowRule = WindowVisibility.normalize(windowRule);
    }

    /** True when the button's look or place depends on what the game's chat does, so the chat state must be read. */
    public boolean dependsOnChat() {
        return chatShow != null || chatAnchor != null || ChatButton.MIC.getId().equals(chatAction);
    }

    /** True when the button follows the chat window (corner and offset known). */
    public boolean followsChat() {
        return chatAnchor != null && chatAnchorKnown;
    }

    /**
     * Puts the button where its pinned corner of the chat window [rect] (left, top, right, bottom in view pixels) puts it. Without a rect or
     * without a measured offset the button stays where it is.
     */
    public void applyChatAnchor(float[] rect) {
        if (!followsChat() || rect == null) return;
        float[] position = ChatBarLogic.anchoredPosition(chatAnchor, rect, chatAnchorDx, chatAnchorDy,
                inputControlsView.getMaxWidth(), inputControlsView.getMaxHeight());
        if (position == null) return;
        int newX = Math.round(position[0]);
        int newY = Math.round(position[1]);
        if (newX != x || newY != y) {
            setX(newX);
            setY(newY);
        }
    }

    /** Measures the offset from the pinned corner of [rect] at the button's current place (editor: what you see is what is saved). */
    public void measureChatAnchor(float[] rect) {
        if (chatAnchor == null || rect == null) return;
        float[] offset = ChatBarLogic.anchorOffset(chatAnchor, rect, x, y, inputControlsView.getMaxWidth(), inputControlsView.getMaxHeight());
        if (offset != null) setChatAnchorOffset(offset[0], offset[1]);
    }

    /** Takes the key of the WoW action as currently set in WoW (see WowActionBindings) as primary binding. */
    public void applyWowActionBinding() {
        if (wowAction == null) return;
        BindingCombo stored = getBindingComboAt(0);
        BindingCombo current = WowActionBindings.resolve(wowAction, stored);
        if (!current.equals(stored)) setBindingComboAt(0, current);
    }

    public float getWidthScale() {
        return widthScale;
    }

    public void setWidthScale(float widthScale) {
        this.widthScale = clampSizeFactor(widthScale);
        boundingBoxNeedsUpdate = true;
    }

    public float getHeightScale() {
        return heightScale;
    }

    public void setHeightScale(float heightScale) {
        this.heightScale = clampSizeFactor(heightScale);
        boundingBoxNeedsUpdate = true;
    }

    private static float clampSizeFactor(float value) {
        if (Float.isNaN(value)) return DEFAULT_SIZE_FACTOR;
        return Mathf.clamp(value, MIN_SIZE_FACTOR, MAX_SIZE_FACTOR);
    }

    public int getRotation() {
        return rotation;
    }

    /** Rotation in quarter turns; any value is rounded to 0, 90, 180 or 270. */
    public void setRotation(int degrees) {
        int quarter = Math.round(degrees / 90.0f);
        this.rotation = (((quarter * 90) % 360) + 360) % 360;
        boundingBoxNeedsUpdate = true;
    }

    public float getCornerRadius() {
        return cornerRadius;
    }

    public void setCornerRadius(float cornerRadius) {
        this.cornerRadius = Float.isNaN(cornerRadius) ? DEFAULT_CORNER_RADIUS : Mathf.clamp(cornerRadius, 0.0f, 1.0f);
    }

    public int getArcSweep() {
        return arcSweep;
    }

    public void setArcSweep(int arcSweep) {
        this.arcSweep = Math.max(MIN_ARC_SWEEP, Math.min(MAX_ARC_SWEEP, arcSweep));
    }

    public float getScrollStep() {
        return scrollStep;
    }

    public void setScrollStep(float scrollStep) {
        this.scrollStep = ScrollWheelMath.clampStep(scrollStep);
    }

    public boolean isScrollReverse() {
        return scrollReverse;
    }

    public void setScrollReverse(boolean scrollReverse) {
        this.scrollReverse = scrollReverse;
    }

    public boolean isScrollInertia() {
        return scrollInertia;
    }

    public void setScrollInertia(boolean scrollInertia) {
        this.scrollInertia = scrollInertia;
    }

    public int getRingSegments() {
        return ringSegments;
    }

    /**
     * Sets the number of key ring segments (0 = no ring, otherwise rounded to 4, 6 or 8). New segments get the number
     * keys 1 to 8 so a fresh ring is usable at once, with an empty label (the ring then names them after their key, see
     * {@link BindingCaption}); segments that were set earlier stay as they are, in both directions of a change.
     */
    public void setRingSegments(int segments) {
        ringSegments = StickRing.normalizeSegments(segments);
        while (ringItems.size() < ringSegments) {
            int index = ringItems.size();
            ringItems.add(new RadialMenu.Slot("", defaultRingBinding(index)));
        }
        boundingBoxNeedsUpdate = true;
    }

    private static Binding defaultRingBinding(int index) {
        Binding[] keys = {Binding.KEY_1, Binding.KEY_2, Binding.KEY_3, Binding.KEY_4, Binding.KEY_5, Binding.KEY_6,
                Binding.KEY_7, Binding.KEY_8};
        return index >= 0 && index < keys.length ? keys[index] : Binding.NONE;
    }

    public float getRingStartAngle() {
        return ringStartAngle;
    }

    public void setRingStartAngle(float degrees) {
        this.ringStartAngle = Float.isNaN(degrees) ? StickRing.DEFAULT_START_ANGLE : degrees;
    }

    public float getRingWidth() {
        return ringWidth;
    }

    public void setRingWidth(float width) {
        this.ringWidth = Float.isNaN(width) ? StickRing.DEFAULT_WIDTH : StickRing.clampWidth(width);
        boundingBoxNeedsUpdate = true;
    }

    public float getRingGap() {
        return ringGap;
    }

    public void setRingGap(float gap) {
        this.ringGap = Float.isNaN(gap) ? StickRing.DEFAULT_GAP : StickRing.clampGap(gap);
        boundingBoxNeedsUpdate = true;
    }

    /** Assignment of ring segment [index]; empty fields are created on demand. Never null. */
    public RadialMenu.Slot getRingItem(int index) {
        if (index < 0) index = 0;
        while (ringItems.size() <= index) ringItems.add(new RadialMenu.Slot());
        return ringItems.get(index);
    }

    public void setRingItem(int index, RadialMenu.Slot item) {
        if (index < 0) return;
        while (ringItems.size() <= index) ringItems.add(new RadialMenu.Slot());
        ringItems.set(index, item != null ? item : new RadialMenu.Slot());
    }

    /** True when ring segment [index] has an outer field (second ring). Only sticks with a ring have outer fields. */
    public boolean hasRingOuter(int index) {
        return type == Type.STICK && index >= 0 && index < ringSegments
                && index < ringOuterItems.size() && ringOuterItems.get(index) != null;
    }

    /** True when at least one visible ring segment has an outer field; the second ring is only drawn and sized then. */
    public boolean hasAnyRingOuter() {
        for (int i = 0; i < ringSegments; i++) {
            if (hasRingOuter(i)) return true;
        }
        return false;
    }

    /** Assignment of the outer field of ring segment [index], or null if that segment has none. */
    public RadialMenu.Slot getRingOuterItem(int index) {
        return hasRingOuter(index) ? ringOuterItems.get(index) : null;
    }

    /** Gives segment [index] an outer field (an empty one if it had none) and returns it; null outside the ring. */
    public RadialMenu.Slot addRingOuter(int index) {
        if (type != Type.STICK || index < 0 || index >= ringSegments) return null;
        while (ringOuterItems.size() <= index) ringOuterItems.add(null);
        if (ringOuterItems.get(index) == null) ringOuterItems.set(index, new RadialMenu.Slot());
        boundingBoxNeedsUpdate = true;
        return ringOuterItems.get(index);
    }

    /** Takes the outer field of segment [index] away. */
    public void removeRingOuter(int index) {
        if (index >= 0 && index < ringOuterItems.size()) ringOuterItems.set(index, null);
        boundingBoxNeedsUpdate = true;
    }

    /** Sets (or with null removes) the outer field of segment [index]. */
    public void setRingOuterItem(int index, RadialMenu.Slot item) {
        if (item == null) {
            removeRingOuter(index);
            return;
        }
        if (index < 0) return;
        while (ringOuterItems.size() <= index) ringOuterItems.add(null);
        ringOuterItems.set(index, item);
        boundingBoxNeedsUpdate = true;
    }

    /** Assignment of the stick's middle ("Mitte drücken"); created on demand, never null. */
    public RadialMenu.Slot getCenterItem() {
        if (centerItem == null) centerItem = new RadialMenu.Slot();
        return centerItem;
    }

    public void setCenterItem(RadialMenu.Slot item) {
        centerItem = item;
    }

    /** True when the middle of this stick has something assigned (a key, a WoW action or a chat command). */
    public boolean hasCenterAction() {
        return type == Type.STICK && centerItem != null && centerItem.isEnabled();
    }

    /** Largest distance from the middle (dp) a finger may have during a tap on the middle. */
    public float getCenterTapDp() {
        return centerTapDp;
    }

    public void setCenterTapDp(float dp) {
        centerTapDp = StickCenter.clampDp(dp);
    }

    /** Longest time (ms) a tap on the middle may take. */
    public long getCenterTapMillis() {
        return centerTapMillis;
    }

    public void setCenterTapMillis(long millis) {
        centerTapMillis = StickCenter.clampMillis(millis);
    }

    private boolean centerHasContent() {
        return centerItem != null && (centerItem.getWowAction() != null || centerItem.hasChatCommand()
                || !centerItem.getBindingCombo().isEmpty() || !centerItem.getLabel().isEmpty());
    }

    /** True when a visible ring segment uses a gamepad binding (the profile then acts as a virtual gamepad). */
    public boolean hasRingGamepadBinding() {
        if (type != Type.STICK) return false;
        for (int i = 0; i < ringSegments && i < ringItems.size(); i++) {
            if (ringItems.get(i).getBindingCombo().containsGamepadBinding()) return true;
            if (hasRingOuter(i) && ringOuterItems.get(i).getBindingCombo().containsGamepadBinding()) return true;
        }
        return false;
    }

    /** Back to the old behaviour for everything the free shape, scroll wheel and key ring added. */
    public void resetExtras() {
        widthScale = DEFAULT_SIZE_FACTOR;
        heightScale = DEFAULT_SIZE_FACTOR;
        rotation = 0;
        cornerRadius = DEFAULT_CORNER_RADIUS;
        arcSweep = DEFAULT_ARC_SWEEP;
        scrollStep = ScrollWheelMath.DEFAULT_STEP;
        scrollReverse = false;
        scrollInertia = false;
        ringSegments = 0;
        ringStartAngle = StickRing.DEFAULT_START_ANGLE;
        ringWidth = StickRing.DEFAULT_WIDTH;
        ringGap = StickRing.DEFAULT_GAP;
        ringItems.clear();
        ringOuterItems.clear();
        rangeItems.clear();
        centerItem = null;
        centerTapDp = StickCenter.DEFAULT_TAP_DP;
        centerTapMillis = StickCenter.DEFAULT_TAP_MILLIS;
        stickyModifier = true;
        radialDirection = null;
        chatAction = null;
        chatShow = null;
        chatAnchor = null;
        chatAnchorDx = 0f;
        chatAnchorDy = 0f;
        chatAnchorKnown = false;
        windowRule = null;
        boundingBoxNeedsUpdate = true;
    }

    private boolean usesSizeFactors() {
        return type == Type.BUTTON || type == Type.SCROLL_WHEEL;
    }

    /**
     * Writes the optional fields of the free shape, scroll wheel and key ring. Only values that differ from the old
     * behaviour are written, so profiles without these features stay byte-identical.
     */
    public void writeExtrasToJson(JSONObject json) throws JSONException {
        if (usesSizeFactors()) {
            if (widthScale != DEFAULT_SIZE_FACTOR) json.put("widthScale", (double)widthScale);
            if (heightScale != DEFAULT_SIZE_FACTOR) json.put("heightScale", (double)heightScale);
        }
        if (type == Type.BUTTON) {
            if (rotation != 0) json.put("rotation", rotation);
            if (cornerRadius != DEFAULT_CORNER_RADIUS) json.put("cornerRadius", (double)cornerRadius);
            if (arcSweep != DEFAULT_ARC_SWEEP) json.put("arcSweep", arcSweep);
            if (!stickyModifier) json.put("stickyModifier", false);
            if (radialDirection != null) json.put("radialDirection", radialDirection);
            if (chatAction != null) json.put("chatAction", chatAction);
            if (chatShow != null) json.put("chatShow", chatShow);
            if (chatAnchor != null) {
                json.put("chatAnchor", chatAnchor);
                if (chatAnchorKnown) {
                    json.put("chatAnchorDx", (double)chatAnchorDx);
                    json.put("chatAnchorDy", (double)chatAnchorDy);
                }
            }
        }
        if (windowRule != null) json.put("windowRule", windowRule);
        if (type == Type.SCROLL_WHEEL) {
            if (scrollStep != ScrollWheelMath.DEFAULT_STEP) json.put("scrollStep", (double)scrollStep);
            if (scrollReverse) json.put("scrollReverse", true);
            if (scrollInertia) json.put("scrollInertia", true);
        }
        if (type == Type.STICK && ringSegments > 0) {
            JSONObject ring = new JSONObject();
            ring.put("segments", ringSegments);
            ring.put("startAngle", (double)ringStartAngle);
            ring.put("width", (double)ringWidth);
            ring.put("gap", (double)ringGap);
            JSONArray items = new JSONArray();
            for (int i = 0; i < ringSegments; i++) items.put(getRingItem(i).toJSONObject());
            ring.put("items", items);
            // Outer second ring (optional): one entry per segment, null = no outer field. Missing in old files and omitted
            // when no segment has one, so rings without it stay byte-identical.
            if (hasAnyRingOuter()) {
                JSONArray outer = new JSONArray();
                for (int i = 0; i < ringSegments; i++) {
                    outer.put(hasRingOuter(i) ? ringOuterItems.get(i).toJSONObject() : JSONObject.NULL);
                }
                ring.put("outer", outer);
            }
            json.put("ring", ring);
        }
        if (type == Type.RANGE_BUTTON && range == Range.CUSTOM && !rangeItems.isEmpty()) {
            // Free entries of the key strip; templates (A-Z, ...) store nothing, so old profiles stay byte-identical.
            JSONArray items = new JSONArray();
            for (RadialMenu.Slot item : rangeItems) items.put(item.toJSONObject());
            json.put("rangeItems", items);
        }
        if (type == Type.STICK && centerHasContent()) {
            json.put("center", centerItem.toJSONObject());
            if (centerTapDp != StickCenter.DEFAULT_TAP_DP) json.put("centerTapDp", (double)centerTapDp);
            if (centerTapMillis != StickCenter.DEFAULT_TAP_MILLIS) json.put("centerTapMillis", centerTapMillis);
        }
    }

    /** Reads what {@link #writeExtrasToJson} wrote; missing fields mean the old behaviour. */
    public void readExtrasFromJson(JSONObject json) {
        resetExtras();
        if (json == null) return;
        setWidthScale((float)json.optDouble("widthScale", DEFAULT_SIZE_FACTOR));
        setHeightScale((float)json.optDouble("heightScale", DEFAULT_SIZE_FACTOR));
        setRotation(json.optInt("rotation", 0));
        setCornerRadius((float)json.optDouble("cornerRadius", DEFAULT_CORNER_RADIUS));
        setArcSweep(json.optInt("arcSweep", DEFAULT_ARC_SWEEP));
        setScrollStep((float)json.optDouble("scrollStep", ScrollWheelMath.DEFAULT_STEP));
        setScrollReverse(json.optBoolean("scrollReverse", false));
        setScrollInertia(json.optBoolean("scrollInertia", false));
        setStickyModifier(json.optBoolean("stickyModifier", true));
        setRadialDirection(json.optString("radialDirection", null));
        setChatAction(json.optString("chatAction", null));
        setChatShow(json.optString("chatShow", null));
        setChatAnchor(json.optString("chatAnchor", null));
        if (chatAnchor != null && json.has("chatAnchorDx") && json.has("chatAnchorDy")) {
            setChatAnchorOffset((float)json.optDouble("chatAnchorDx", 0.0), (float)json.optDouble("chatAnchorDy", 0.0));
        }
        setWindowRule(json.optString("windowRule", null));

        JSONObject ring = json.optJSONObject("ring");
        if (ring != null) {
            setRingStartAngle((float)ring.optDouble("startAngle", StickRing.DEFAULT_START_ANGLE));
            setRingWidth((float)ring.optDouble("width", StickRing.DEFAULT_WIDTH));
            setRingGap((float)ring.optDouble("gap", StickRing.DEFAULT_GAP));
            JSONArray items = ring.optJSONArray("items");
            ringItems.clear();
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    ringItems.add(item != null ? RadialMenu.Slot.fromJSONObject(item) : new RadialMenu.Slot());
                }
            }
            // Segments not stored in the file stay empty instead of getting the number keys.
            int segments = StickRing.normalizeSegments(ring.optInt("segments", 0));
            while (ringItems.size() < segments) ringItems.add(new RadialMenu.Slot());
            ringSegments = segments;
            ringOuterItems.clear();
            JSONArray outer = ring.optJSONArray("outer");
            if (outer != null) {
                for (int i = 0; i < outer.length() && i < segments; i++) {
                    JSONObject item = outer.isNull(i) ? null : outer.optJSONObject(i);
                    ringOuterItems.add(item != null ? RadialMenu.Slot.fromJSONObject(item) : null);
                }
            }
            boundingBoxNeedsUpdate = true;
        }
        JSONArray rangeEntries = json.optJSONArray("rangeItems");
        if (rangeEntries != null) {
            // A damaged entry becomes an empty one (keeps the positions of the others); too many are cut off.
            for (int i = 0; i < rangeEntries.length() && i < MAX_RANGE_ITEMS; i++) {
                RadialMenu.Slot item = null;
                try {
                    JSONObject object = rangeEntries.optJSONObject(i);
                    if (object != null) item = RadialMenu.Slot.fromJSONObject(object);
                }
                catch (RuntimeException ignored) {
                }
                rangeItems.add(item != null ? item : new RadialMenu.Slot());
            }
        }
        JSONObject center = json.optJSONObject("center");
        if (center != null) centerItem = RadialMenu.Slot.fromJSONObject(center);
        setCenterTapDp((float)json.optDouble("centerTapDp", StickCenter.DEFAULT_TAP_DP));
        setCenterTapMillis(json.optLong("centerTapMillis", StickCenter.DEFAULT_TAP_MILLIS));
    }

    /** Takes over the free shape, scroll wheel and key ring settings of [other] (profile copy). */
    public void copyExtrasFrom(ControlElement other) {
        JSONObject json = new JSONObject();
        try {
            other.writeExtrasToJson(json);
        }
        catch (JSONException e) {
            return;
        }
        readExtrasFromJson(json);
    }

    public float getEffectiveButtonOpacity(float fallbackOpacity) {
        return buttonOpacity >= 0 ? buttonOpacity : fallbackOpacity;
    }

    public static int parseRgbColor(Object value, int fallbackColor) {
        if (value instanceof Number) return ((Number)value).intValue() & 0x00ffffff;
        if (value instanceof String) {
            String hex = ((String)value).trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 8) hex = hex.substring(2);
            if (hex.length() == 6) {
                try {
                    return (int)Long.parseLong(hex, 16) & 0x00ffffff;
                }
                catch (NumberFormatException ignored) {
                }
            }
        }
        return fallbackColor;
    }

    public static String formatRgbColor(int color) {
        return String.format(Locale.US, "#%06X", color & 0x00ffffff);
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        boundingBoxNeedsUpdate = true;
    }

    public short getX() {
        return x;
    }

    public void setX(int x) {
        this.x = (short)x;
        boundingBoxNeedsUpdate = true;
    }

    public short getY() {
        return y;
    }

    public void setY(int y) {
        this.y = (short)y;
        boundingBoxNeedsUpdate = true;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public byte getIconId() {
        return iconId;
    }

    public void setIconId(int iconId) {
        this.iconId = (byte)iconId;
    }

    public Rect getBoundingBox() {
        if (boundingBoxNeedsUpdate) computeBoundingBox();
        return boundingBox;
    }

    /** A finger lies on this element right now (also on a key ring segment of a stick). */
    public boolean isTouchHeld() {
        return currentPointerId != -1 || ringPointerId != -1;
    }

    private Rect computeBoundingBox() {
        int snappingSize = inputControlsView.getSnappingSize();
        int halfWidth = 0;
        int halfHeight = 0;

        switch (type) {
            case BUTTON:
                switch (shape) {
                    case RECT:
                    case ROUND_RECT:
                        halfWidth = snappingSize * 4;
                        halfHeight = snappingSize * 2;
                        break;
                    case PILL:
                        halfWidth = snappingSize * 5;
                        halfHeight = (int)(snappingSize * 1.5f);
                        break;
                    case SQUARE:
                        halfWidth = (int)(snappingSize * 2.5f);
                        halfHeight = (int)(snappingSize * 2.5f);
                        break;
                    case CIRCLE:
                    case ARC:
                        halfWidth = snappingSize * 3;
                        halfHeight = snappingSize * 3;
                        break;
                }
                // A quarter turn swaps width and height (an ARC is turned when drawn instead).
                if (shape != Shape.ARC && (rotation == 90 || rotation == 270)) {
                    int tmp = halfWidth;
                    halfWidth = halfHeight;
                    halfHeight = tmp;
                }
                break;
            case D_PAD: {
                halfWidth = snappingSize * 7;
                halfHeight = snappingSize * 7;
                break;
            }
            case TRACKPAD:
            case STICK: {
                halfWidth = snappingSize * 6;
                halfHeight = snappingSize * 6;
                break;
            }
            case RANGE_BUTTON: {
                halfWidth = snappingSize * ((bindings.length * 4) / 2);
                halfHeight = snappingSize * 2;

                if (orientation == 1) {
                    int tmp = halfWidth;
                    halfWidth = halfHeight;
                    halfHeight = tmp;
                }
                break;
            }
            case SHOOTER_MODE: {
                halfWidth = snappingSize * 3;
                halfHeight = snappingSize * 3;
                break;
            }
            case SCROLL_WHEEL: {
                // Vertical wheel (swipe up/down) is tall and narrow, orientation 1 lies flat.
                halfWidth = snappingSize * 2;
                halfHeight = snappingSize * 5;
                if (orientation == 1) {
                    int tmp = halfWidth;
                    halfWidth = halfHeight;
                    halfHeight = tmp;
                }
                break;
            }
        }

        // widthScale/heightScale refer to the width and height as seen on the screen (after turning).
        float horizontalScale = usesSizeFactors() ? scale * widthScale : scale;
        float verticalScale = usesSizeFactors() ? scale * heightScale : scale;
        halfWidth *= horizontalScale;
        halfHeight *= verticalScale;
        if (type == Type.STICK) {
            stickRadiusPx = halfWidth;
            if (ringSegments > 0) {
                // The optional outer second ring (same width) lies outside the first one, after a small gap.
                float outerExtent = hasAnyRingOuter() ? StickRing.OUTER_GAP + ringWidth : 0.0f;
                int extent = (int)Math.ceil((ringGap + ringWidth + outerExtent) * snappingSize * scale);
                halfWidth += extent;
                halfHeight += extent;
            }
        }
        boundingBox.set(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight);
        boundingBoxNeedsUpdate = false;
        return boundingBox;
    }

    private String getDisplayText() {
        if (text != null && !text.isEmpty()) {
            return StandardNames.displayLabel(text);
        }
        else {
            BindingCombo bindingCombo = getBindingComboAt(0);
            // No key: no text at all (the combo would print "NONE").
            if (bindingCombo.isEmpty()) return "";
            Binding binding = bindingCombo.getPrimaryBinding();
            String text = bindingCombo.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "").replace("SHOW KEYBOARD", "KEY").replace("MENÜ (Einstellungen)", "MENÜ");
            if (text.length() > 7) {
                String[] parts = text.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) {
                    if (part.isEmpty() || part.equals("+") || part.equals("->")) continue;
                    sb.append(part.charAt(0));
                }
                return (binding.isMouse() ? "M" : "")+ sb;
            }
            else return text;
        }
    }

    private static float getTextSizeForWidth(Paint paint, String text, float desiredWidth) {
        final byte testTextSize = 48;
        paint.setTextSize(testTextSize);
        return testTextSize * desiredWidth / paint.measureText(text);
    }

    /**
     * Text of a button without own picture: the symbol of its key when it has one (KeyGlyphs, only without own text), else the
     * text. Font size by TextFit (45 % of the diameter at most, shrinks to the width, cut at the smallest size).
     */
    private void drawButtonLabel(Canvas canvas, Paint paint, float cx, float cy, float diameter, float fitWidth, float maxSize,
                                 int color, boolean serif, boolean pressed) {
        if (isUnassignedButton()) {
            // A button without any assignment shows nothing in the game; the editor marks it with a dimmed "+".
            if (inputControlsView.isEditMode()) {
                int hint = ColorUtils.setAlphaComponent(color, Math.min(color >>> 24, 150));
                drawSlotLabel(canvas, paint, null, "+", cx, cy, diameter, fitWidth, maxSize, false, hint, serif, false);
            }
            return;
        }
        KeyLabel keyLabel = (text == null || text.isEmpty()) ? KeyGlyphs.labelFor(getBindingComboAt(0)) : null;
        drawSlotLabel(canvas, paint, keyLabel, keyLabel != null ? null : getDisplayText(), cx, cy, diameter, fitWidth, maxSize, false,
                color, serif, pressed);
    }

    /**
     * A button with no assignment at all, like a freshly added one: no key, WoW action, chat command or chat control, no own
     * text, no picture and no menu. It has nothing to show (the game draws it without text, the editor with a "+").
     */
    public boolean isUnassignedButton() {
        if (type != Type.BUTTON) return false;
        if (text != null && !text.isEmpty()) return false;
        if (wowAction != null || chatCommand != null || chatAction != null || radialMenuId != null || iconId > 0) return false;
        for (int i = 0; i < bindings.length; i++) {
            BindingCombo combo = bindings[i];
            if (combo != null && !combo.isEmpty()) return false;
        }
        return true;
    }

    /** True for an unassigned button outside the editor: nothing is drawn and no touch is taken (see {@link #isUnassignedButton()}). */
    private boolean isHiddenUnassignedButton() {
        return !inputControlsView.isEditMode() && isUnassignedButton();
    }

    /** [keyLabel] (symbols) when not null, else [label] as text; [ring] for ring and outer ring fields (smaller text). */
    private static void drawSlotLabel(Canvas canvas, Paint paint, KeyLabel keyLabel, String label, float cx, float cy, float diameter,
                                      float fitWidth, float maxSize, boolean ring, int color, boolean serif, boolean pressed) {
        if (keyLabel != null) {
            KeyGlyphDraw.drawLabel(canvas, paint, keyLabel, cx, cy, diameter, fitWidth, maxSize, ring, color, serif, pressed);
        }
        else {
            KeyGlyphDraw.drawText(canvas, paint, label, cx, cy, diameter, fitWidth, maxSize, ring, color, serif, pressed);
        }
    }

    private static String getRangeTextForIndex(Range range, int index) {
        String text = "";
        switch (range) {
            case FROM_A_TO_Z:
                text = String.valueOf((char)(65 + index));
                break;
            case FROM_0_TO_9:
                text = String.valueOf((index + 1) % 10);
                break;
            case FROM_F1_TO_F12:
                text = "F"+(index + 1);
                break;
            case FROM_NP0_TO_NP9:
                text = "NP"+((index + 1) % 10);
                break;
        }
        return text;
    }

    private int getAppearanceDrawColor(boolean active) {
        int rgb = active ? buttonActiveColor : buttonColor;
        int alpha = (int)(getEffectiveButtonOpacity(inputControlsView.getOverlayOpacity()) * 255);
        return ColorUtils.setAlphaComponent(0xff000000 | rgb, alpha);
    }

    private int getEditorSelectionDrawColor() {
        int rgb = buttonActiveColorCustom ? buttonActiveColor : inputControlsView.getSecondaryColor();
        int alpha = (int)(getEffectiveButtonOpacity(inputControlsView.getOverlayOpacity()) * 255);
        return ColorUtils.setAlphaComponent(0xff000000 | (rgb & 0x00ffffff), alpha);
    }

    private int getRuntimeSelectedDrawColor() {
        if (buttonActiveColorCustom) return getAppearanceDrawColor(true);
        int alpha = (int)(getEffectiveButtonOpacity(inputControlsView.getOverlayOpacity()) * 255);
        return ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), alpha);
    }

    private static void setDPadDirectionPath(Path path, byte direction, Rect boundingBox, float cx, float cy, float offsetX, float offsetY, float start) {
        path.reset();
        switch (direction) {
            case 0:
                path.moveTo(cx, cy - start);
                path.lineTo(cx - offsetX, cy - offsetY);
                path.lineTo(cx - offsetX, boundingBox.top);
                path.lineTo(cx + offsetX, boundingBox.top);
                path.lineTo(cx + offsetX, cy - offsetY);
                break;
            case 1:
                path.moveTo(cx + start, cy);
                path.lineTo(cx + offsetY, cy - offsetX);
                path.lineTo(boundingBox.right, cy - offsetX);
                path.lineTo(boundingBox.right, cy + offsetX);
                path.lineTo(cx + offsetY, cy + offsetX);
                break;
            case 2:
                path.moveTo(cx, cy + start);
                path.lineTo(cx - offsetX, cy + offsetY);
                path.lineTo(cx - offsetX, boundingBox.bottom);
                path.lineTo(cx + offsetX, boundingBox.bottom);
                path.lineTo(cx + offsetX, cy + offsetY);
                break;
            case 3:
                path.moveTo(cx - start, cy);
                path.lineTo(cx - offsetY, cy - offsetX);
                path.lineTo(boundingBox.left, cy - offsetX);
                path.lineTo(boundingBox.left, cy + offsetX);
                path.lineTo(cx - offsetY, cy + offsetX);
                break;
        }
        path.close();
    }

    private static void setHorizontalRangeSegmentPath(Path path, Rect boundingBox, float segmentLeft, float segmentRight, float radius) {
        float left = Math.max(segmentLeft, boundingBox.left);
        float right = Math.min(segmentRight, boundingBox.right);
        float top = boundingBox.top;
        float bottom = boundingBox.bottom;
        float width = Math.max(0.0f, right - left);
        float leftRadius = segmentLeft <= boundingBox.left ? Math.min(radius, width * 0.5f) : 0.0f;
        float rightRadius = segmentRight >= boundingBox.right ? Math.min(radius, width * 0.5f) : 0.0f;

        path.reset();
        path.moveTo(left + leftRadius, top);
        path.lineTo(right - rightRadius, top);
        if (rightRadius > 0.0f) {
            path.quadTo(right, top, right, top + rightRadius);
            path.lineTo(right, bottom - rightRadius);
            path.quadTo(right, bottom, right - rightRadius, bottom);
        }
        else {
            path.lineTo(right, bottom);
        }
        path.lineTo(left + leftRadius, bottom);
        if (leftRadius > 0.0f) {
            path.quadTo(left, bottom, left, bottom - leftRadius);
            path.lineTo(left, top + leftRadius);
            path.quadTo(left, top, left + leftRadius, top);
        }
        else {
            path.lineTo(left, top);
        }
        path.close();
    }

    private static void setVerticalRangeSegmentPath(Path path, Rect boundingBox, float segmentTop, float segmentBottom, float radius) {
        float left = boundingBox.left;
        float right = boundingBox.right;
        float top = Math.max(segmentTop, boundingBox.top);
        float bottom = Math.min(segmentBottom, boundingBox.bottom);
        float height = Math.max(0.0f, bottom - top);
        float topRadius = segmentTop <= boundingBox.top ? Math.min(radius, height * 0.5f) : 0.0f;
        float bottomRadius = segmentBottom >= boundingBox.bottom ? Math.min(radius, height * 0.5f) : 0.0f;

        path.reset();
        path.moveTo(left + topRadius, top);
        path.lineTo(right - topRadius, top);
        if (topRadius > 0.0f) {
            path.quadTo(right, top, right, top + topRadius);
        }
        else {
            path.lineTo(right, top);
        }
        path.lineTo(right, bottom - bottomRadius);
        if (bottomRadius > 0.0f) {
            path.quadTo(right, bottom, right - bottomRadius, bottom);
            path.lineTo(left + bottomRadius, bottom);
            path.quadTo(left, bottom, left, bottom - bottomRadius);
        }
        else {
            path.lineTo(right, bottom);
            path.lineTo(left, bottom);
        }
        path.lineTo(left, top + topRadius);
        if (topRadius > 0.0f) {
            path.quadTo(left, top, left + topRadius, top);
        }
        else {
            path.lineTo(left, top);
        }
        path.close();
    }

    private static void setHorizontalRangeOutlinePath(Path path, Rect boundingBox, float radius, float skipLeft, float skipRight) {
        float left = boundingBox.left;
        float top = boundingBox.top;
        float right = boundingBox.right;
        float bottom = boundingBox.bottom;

        path.reset();
        if (skipLeft > left) {
            float stop = Math.min(skipLeft, right);
            path.moveTo(left + radius, top);
            path.lineTo(Math.max(left + radius, stop), top);
            path.moveTo(left + radius, bottom);
            path.lineTo(Math.max(left + radius, stop), bottom);
            path.moveTo(left + radius, top);
            path.quadTo(left, top, left, top + radius);
            path.lineTo(left, bottom - radius);
            path.quadTo(left, bottom, left + radius, bottom);
        }

        if (skipRight < right) {
            float start = Math.max(skipRight, left);
            path.moveTo(Math.min(right - radius, start), top);
            path.lineTo(right - radius, top);
            path.quadTo(right, top, right, top + radius);
            path.lineTo(right, bottom - radius);
            path.quadTo(right, bottom, right - radius, bottom);
            path.moveTo(Math.min(right - radius, start), bottom);
            path.lineTo(right - radius, bottom);
        }
    }

    private static void setVerticalRangeOutlinePath(Path path, Rect boundingBox, float radius, float skipTop, float skipBottom) {
        float left = boundingBox.left;
        float top = boundingBox.top;
        float right = boundingBox.right;
        float bottom = boundingBox.bottom;

        path.reset();
        if (skipTop > top) {
            float stop = Math.min(skipTop, bottom);
            path.moveTo(left + radius, top);
            path.lineTo(right - radius, top);
            path.quadTo(right, top, right, top + radius);
            path.moveTo(left + radius, top);
            path.quadTo(left, top, left, top + radius);
            path.lineTo(left, Math.max(top + radius, stop));
            path.moveTo(right, top + radius);
            path.lineTo(right, Math.max(top + radius, stop));
        }

        if (skipBottom < bottom) {
            float start = Math.max(skipBottom, top);
            path.moveTo(left, Math.min(bottom - radius, start));
            path.lineTo(left, bottom - radius);
            path.quadTo(left, bottom, left + radius, bottom);
            path.lineTo(right - radius, bottom);
            path.quadTo(right, bottom, right, bottom - radius);
            path.moveTo(right, Math.min(bottom - radius, start));
            path.lineTo(right, bottom - radius);
        }
    }

    public void draw(Canvas canvas) {
        // An unassigned button is invisible in the game (the editor shows it with a "+"), like an empty ring field.
        if (isHiddenUnassignedButton()) return;
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        boolean showGyroState = !inputControlsView.isEditMode()
                && inputControlsView.isGyroEnabled() && isGyroModifierControl();
        boolean gyroActive = showGyroState && inputControlsView.isGyroActive();
        // Latching Shift/Ctrl/Alt: lit while the key is down (once = active colour, fixed = permanent selected colour)
        LatchMode latchMode = LatchMode.OFF;
        if (type == Type.BUTTON && !inputControlsView.isEditMode()) {
            ModKey latchKey = stickyModKey();
            if (latchKey != null) {
                latchMode = StickyModifiers.mode(latchKey);
                if (!repaintTargetSet) prepareStickyModifiers();
            }
        }
        boolean active = selected || latchMode != LatchMode.OFF
                || (showGyroState ? (gyroActive || currentPointerId != -1) : currentPointerId != -1);
        boolean editSelected = selected && inputControlsView.isEditMode();
        int normalColor = getAppearanceDrawColor(false);
        int activeColor = editSelected ? getEditorSelectionDrawColor() : getAppearanceDrawColor(true);
        boolean runtimeSelected = (selected || gyroActive || latchMode == LatchMode.LOCKED) && !editSelected;
        if (runtimeSelected) activeColor = getRuntimeSelectedDrawColor();
        int primaryColor = active ? activeColor : normalColor;
        int contentColor = runtimeSelected && !buttonActiveColorCustom ? normalColor : primaryColor;

        paint.setColor(primaryColor);
        paint.setStyle(Paint.Style.STROKE);
        float strokeWidth = snappingSize * 0.25f * buttonStrokeScale;
        paint.setStrokeWidth(strokeWidth);
        Rect boundingBox = getBoundingBox();

        // Styles F and G (see TouchStyleDraw): buttons, scroll wheels, sticks and key strips are drawn there; the rest keeps the old look.
        if (TouchStyleDraw.isStyled() && drawStyled(canvas, boundingBox, active, editSelected, activeColor)) {
            return;
        }

        switch (type) {
            case BUTTON: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();

                // Content (icon, symbol, text) sits in the middle; on an ARC it sits on the middle of the arc.
                float contentX = cx;
                float contentY = cy;
                float contentWidth = boundingBox.width();
                float contentHeight = boundingBox.height();

                switch (shape) {
                    case CIRCLE:
                        if (boundingBox.width() == boundingBox.height()) {
                            canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
                        }
                        else {
                            canvas.drawOval(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, paint);
                        }
                        break;
                    case RECT:
                        canvas.drawRect(boundingBox, paint);
                        break;
                    case ROUND_RECT: {
                        float radius = Math.min(boundingBox.width(), boundingBox.height()) * 0.5f * cornerRadius;
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                        break;
                    }
                    case PILL: {
                        float radius = Math.min(boundingBox.width(), boundingBox.height()) * 0.5f;
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                        break;
                    }
                    case SQUARE: {
                        float radius = snappingSize * 0.75f * scale;
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                        break;
                    }
                    case ARC: {
                        float outer = Math.min(boundingBox.width(), boundingBox.height()) * 0.5f;
                        float inner = outer * (1.0f - ARC_THICKNESS);
                        float centerAngle = getArcCenterAngle();
                        Path arcPath = inputControlsView.getPath();
                        buildSectorPath(arcPath, cx, cy, inner, outer, centerAngle - arcSweep * 0.5f, arcSweep);
                        canvas.drawPath(arcPath, paint);
                        double middleAngle = Math.toRadians(centerAngle);
                        float middleRadius = (outer + inner) * 0.5f;
                        contentX = cx + (float)Math.cos(middleAngle) * middleRadius;
                        contentY = cy + (float)Math.sin(middleAngle) * middleRadius;
                        contentWidth = (outer - inner) + snappingSize * 2 * scale;
                        contentHeight = contentWidth;
                        break;
                    }
                }

                boolean noOwnText = text == null || text.isEmpty();
                String wowSymbol = noOwnText && wowAction != null ? WowSymbols.symbolFor(wowAction) : null;
                String wowEmoji = noOwnText ? BindingCaption.emojiForAction(wowAction) : null;
                if (iconId > 0) {
                    drawIcon(canvas, contentX, contentY, contentWidth, contentHeight, iconId, contentColor);
                }
                else if (noOwnText && chatAction != null
                        && ChatGlyphDraw.draw(canvas, chatAction, contentX, contentY, Math.min(contentWidth, contentHeight), contentColor, false)) {
                    // Symbol of the chat control (drawn by the condition).
                }
                else if (drawAddonIcon(canvas, boundingBox, contentX, contentY, strokeWidth, contentColor)) {
                    // Real icon of the action bar slot (from the addon); the own text (if any) stays as a small label below.
                    if (!noOwnText) {
                        float captionSize = Math.min(snappingSize * 1.4f * scale, boundingBox.height() * 0.26f);
                        WowIconDraw.drawCaption(canvas, getDisplayText(), cx, boundingBox.bottom - strokeWidth - captionSize * 0.25f, captionSize);
                    }
                }
                else if (wowEmoji != null) {
                    float emojiSize = Math.min(getTextSizeForWidth(paint, wowEmoji, contentWidth * 0.8f), contentHeight * 0.65f);
                    paint.setTextSize(Math.min(emojiSize, snappingSize * 3 * scale));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(contentColor);
                    canvas.drawText(wowEmoji, contentX, (contentY - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                }
                else if (wowSymbol != null) {
                    int margin = (int)(inputControlsView.getSnappingSize() * (shape == Shape.CIRCLE || shape == Shape.SQUARE || shape == Shape.ARC ? 2.0f : 1.0f) * scale);
                    float halfSize = (Math.min(contentWidth, contentHeight) - margin) * 0.5f;
                    WowSymbols.draw(canvas, wowSymbol, contentX, contentY, halfSize, contentColor);
                }
                else {
                    // Symbol for keys that have one (space, tab, ...), else text; at most 45 % of the element high (TextFit).
                    drawButtonLabel(canvas, paint, contentX, contentY, Math.min(contentWidth, contentHeight), contentWidth - strokeWidth * 2,
                            snappingSize * 2 * scale, contentColor, false, false);
                }
                // Cooldown of the action bar slot this button stands for (sector and remaining time) over whatever was drawn.
                drawCooldown(canvas, boundingBox, contentX, contentY, strokeWidth);
                break;
            }
            case D_PAD: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                float offsetX = snappingSize * 2 * scale;
                float offsetY = snappingSize * 3 * scale;
                float start = snappingSize * scale;
                Path path = inputControlsView.getPath();

                for (byte i = 0; i < 4; i++) {
                    setDPadDirectionPath(path, i, boundingBox, cx, cy, offsetX, offsetY, start);
                    boolean directionActive = editSelected || states[i];

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(strokeWidth);
                    paint.setColor(directionActive ? activeColor : normalColor);
                    canvas.drawPath(path, paint);
                }
                break;
            }
            case RANGE_BUTTON: {
                int rangeCount = getRangeCount();
                int oldColor = editSelected ? activeColor : normalColor;
                int activeRangeIndex = editSelected ? -1 : scroller.getActiveIndex();
                float radius = snappingSize * 0.75f * scale;
                float activeStrokeWidth = strokeWidth * 1.5f;
                float activeRadius = radius + (activeStrokeWidth - strokeWidth) * 0.5f;
                float elementSize = scroller.getElementSize();
                float minTextSize = snappingSize * 2 * scale;
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                Path path = inputControlsView.getPath();
                path.reset();
                paint.setColor(oldColor);
                paint.setStrokeWidth(strokeWidth);

                if (orientation == 0) {
                    float lineTop = boundingBox.top + strokeWidth * 0.5f;
                    float lineBottom = boundingBox.bottom - strokeWidth * 0.5f;
                    float startX = boundingBox.left;
                    boolean drawActiveSegmentOutline = false;
                    float activeSegmentLeft = 0;
                    float activeSegmentRight = 0;

                    canvas.save();
                    path.addRoundRect(startX, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(path);
                    startX -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % rangeCount;
                        float segmentLeft = startX;
                        float segmentRight = startX + elementSize;
                        boolean segmentActive = index == activeRangeIndex;
                        boolean segmentVisible = segmentLeft < boundingBox.right && segmentRight > boundingBox.left;

                        paint.setStyle(Paint.Style.STROKE);
                        int previousIndex = (index - 1 + rangeCount) % rangeCount;
                        boolean activeBoundary = index == activeRangeIndex || previousIndex == activeRangeIndex;
                        paint.setColor(activeBoundary ? activeColor : oldColor);
                        paint.setStrokeWidth(activeBoundary ? activeStrokeWidth : strokeWidth);

                        if (!activeBoundary && startX > boundingBox.left && startX < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        if (segmentActive && segmentVisible) {
                            drawActiveSegmentOutline = true;
                            activeSegmentLeft = segmentLeft;
                            activeSegmentRight = segmentRight;
                        }
                        paint.setStrokeWidth(strokeWidth);

                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            drawRangeCellContent(canvas, index, startX, boundingBox.top, startX + elementSize, boundingBox.bottom,
                                    segmentActive, segmentActive ? activeColor : oldColor, elementSize - strokeWidth * 2, minTextSize, false);
                        }
                        startX += elementSize;
                    }

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.restore();

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    paint.setStrokeWidth(strokeWidth);
                    if (drawActiveSegmentOutline) {
                        setHorizontalRangeOutlinePath(path, boundingBox, radius, activeSegmentLeft, activeSegmentRight);
                        canvas.drawPath(path, paint);
                    }
                    else {
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                    }

                    if (drawActiveSegmentOutline) {
                        setHorizontalRangeSegmentPath(path, boundingBox, activeSegmentLeft, activeSegmentRight, activeRadius);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(activeColor);
                        paint.setStrokeWidth(activeStrokeWidth);
                        canvas.drawPath(path, paint);
                        paint.setStrokeWidth(strokeWidth);
                    }
                }
                else {
                    float lineLeft = boundingBox.left + strokeWidth * 0.5f;
                    float lineRight = boundingBox.right - strokeWidth * 0.5f;
                    float startY = boundingBox.top;
                    boolean drawActiveSegmentOutline = false;
                    float activeSegmentTop = 0;
                    float activeSegmentBottom = 0;

                    canvas.save();
                    path.addRoundRect(boundingBox.left, startY, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(path);
                    startY -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % rangeCount;
                        float segmentTop = startY;
                        float segmentBottom = startY + elementSize;
                        boolean segmentActive = index == activeRangeIndex;
                        boolean segmentVisible = segmentTop < boundingBox.bottom && segmentBottom > boundingBox.top;

                        paint.setStyle(Paint.Style.STROKE);
                        int previousIndex = (index - 1 + rangeCount) % rangeCount;
                        boolean activeBoundary = index == activeRangeIndex || previousIndex == activeRangeIndex;
                        paint.setColor(activeBoundary ? activeColor : oldColor);
                        paint.setStrokeWidth(activeBoundary ? activeStrokeWidth : strokeWidth);

                        if (!activeBoundary && startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        if (segmentActive && segmentVisible) {
                            drawActiveSegmentOutline = true;
                            activeSegmentTop = segmentTop;
                            activeSegmentBottom = segmentBottom;
                        }
                        paint.setStrokeWidth(strokeWidth);

                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            drawRangeCellContent(canvas, index, boundingBox.left, startY, boundingBox.right, startY + elementSize,
                                    segmentActive, segmentActive ? activeColor : oldColor, boundingBox.width() - strokeWidth * 2, minTextSize, false);
                        }
                        startY += elementSize;
                    }

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.restore();

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    paint.setStrokeWidth(strokeWidth);
                    if (drawActiveSegmentOutline) {
                        setVerticalRangeOutlinePath(path, boundingBox, radius, activeSegmentTop, activeSegmentBottom);
                        canvas.drawPath(path, paint);
                    }
                    else {
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                    }

                    if (drawActiveSegmentOutline) {
                        setVerticalRangeSegmentPath(path, boundingBox, activeSegmentTop, activeSegmentBottom, activeRadius);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(activeColor);
                        paint.setStrokeWidth(activeStrokeWidth);
                        canvas.drawPath(path, paint);
                        paint.setStrokeWidth(strokeWidth);
                    }
                }

                if (editSelected) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(activeColor);
                    paint.setStrokeWidth(activeStrokeWidth);
                    canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, activeRadius, activeRadius, paint);
                    paint.setStrokeWidth(strokeWidth);
                }
                break;
            }
            case STICK: {
                int cx = boundingBox.centerX();
                int cy = boundingBox.centerY();
                int oldColor = paint.getColor();
                float stickRadius = getStickRadius();
                canvas.drawCircle(cx, cy, stickRadius, paint);

                float thumbstickX = currentPosition != null ? currentPosition.x : cx;
                float thumbstickY = currentPosition != null ? currentPosition.y : cy;

                short thumbRadius = (short) (snappingSize * 3.5f * scale);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(contentColor, Math.min(50, contentColor >>> 24)));
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(oldColor);
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius + strokeWidth * 0.5f, paint);
                drawStickCenterCaption(canvas, thumbstickX, thumbstickY, thumbRadius, contentColor);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(oldColor);

                if (ringSegments > 0) {
                    drawStickRing(canvas, cx, cy, stickRadius, normalColor, activeColor, editSelected, strokeWidth);
                }
                break;
            }
            case SCROLL_WHEEL: {
                drawScrollWheel(canvas, boundingBox, primaryColor, activeColor, editSelected, strokeWidth);
                break;
            }
            case TRACKPAD: {
                float radius = boundingBox.height() * 0.15f;
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                float offset = strokeWidth * 2.5f;
                float innerStrokeWidth = strokeWidth * 2;
                float innerHeight = boundingBox.height() - offset * 2;
                radius = (innerHeight / boundingBox.height()) * radius - (innerStrokeWidth * 0.5f + strokeWidth * 0.5f);
                paint.setStrokeWidth(innerStrokeWidth);
                canvas.drawRoundRect(boundingBox.left + offset, boundingBox.top + offset, boundingBox.right - offset, boundingBox.bottom - offset, radius, radius, paint);
                break;
            }
            case SHOOTER_MODE: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                float halfW = boundingBox.width() * 0.5f;

                if (selected) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(ColorUtils.setAlphaComponent(primaryColor, Math.min(80, primaryColor >>> 24)));
                    canvas.drawCircle(cx, cy, halfW, paint);
                }

                // Draw outline
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(primaryColor);
                paint.setStrokeWidth(strokeWidth);
                canvas.drawCircle(cx, cy, halfW, paint);

                // Draw icon or fallback text
                if (iconId > 0) {
                    drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId, contentColor);
                } else {
                    String displayText = (text != null && !text.isEmpty()) ? text : "DJ";
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, displayText, boundingBox.width() - strokeWidth * 2), snappingSize * 2 * scale));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(contentColor);
                    canvas.drawText(displayText, cx, (cy - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                }
                break;
            }
        }
    }

    // ---- Styles F and G (TouchStyleDraw) ---------------------------------------------------------------------------

    /** Draws this element in the chosen style; false for the types that keep the old look (D-pad, trackpad, shooter). */
    private boolean drawStyled(Canvas canvas, Rect box, boolean active, boolean editSelected, int activeColor) {
        // An element selected in the editor is not "pressed": it only gets the highlighted edge, in the editor colour.
        boolean pressed = active && !editSelected;
        int ringColor = editSelected ? (activeColor | 0xff000000) : 0;
        switch (type) {
            case BUTTON:
                drawStyledButton(canvas, box, pressed, editSelected, ringColor);
                return true;
            case SCROLL_WHEEL:
                drawStyledWheel(canvas, box, pressed, editSelected, ringColor);
                return true;
            case STICK:
                drawStyledStick(canvas, box, pressed, editSelected, ringColor);
                return true;
            case RANGE_BUTTON:
                drawStyledRange(canvas, box, editSelected, ringColor);
                return true;
            default:
                return false;
        }
    }

    /**
     * Key strip in styles F and G: every visible cell is a slot like the buttons of a row (cut corners, gap between the cells),
     * the pressed cell gets the pressed look. The cells scroll under the clip of the strip; the shadows stay free across the strip.
     */
    private void drawStyledRange(Canvas canvas, Rect box, boolean editSelected, int ringColor) {
        int snappingSize = inputControlsView.getSnappingSize();
        boolean horizontal = orientation == 0;
        int rangeCount = getRangeCount();
        float edge = TouchStyleDraw.edgePx();
        if (rangeCount <= 0) {
            TouchStyleDraw.drawFrame(canvas, TouchStyleDraw.SHAPE_CHAMFER, box.left, box.top, box.right, box.bottom,
                    TouchStyleDraw.ROLE_PANEL, false, editSelected, ringColor);
            return;
        }
        float elementSize = scroller.getElementSize();
        float scrollOffset = scroller.getScrollOffset();
        byte[] rangeIndex = scroller.getRangeIndex();
        int activeIndex = editSelected ? -1 : scroller.getActiveIndex();
        float minTextSize = snappingSize * 2 * scale;
        float gap = Math.max(1.0f, edge);
        float shadowMargin = edge * 6.0f;

        canvas.save();
        if (horizontal) canvas.clipRect(box.left, box.top - shadowMargin, box.right, box.bottom + shadowMargin);
        else canvas.clipRect(box.left - shadowMargin, box.top, box.right + shadowMargin, box.bottom);
        float start = (horizontal ? box.left : box.top) - scrollOffset % elementSize;
        for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
            int index = i % rangeCount;
            float left = horizontal ? start + gap * 0.5f : box.left;
            float right = horizontal ? start + elementSize - gap * 0.5f : box.right;
            float top = horizontal ? box.top : start + gap * 0.5f;
            float bottom = horizontal ? box.bottom : start + elementSize - gap * 0.5f;
            start += elementSize;
            if (right <= box.left || left >= box.right || bottom <= box.top || top >= box.bottom) continue;

            boolean pressed = index == activeIndex;
            float longSide = Math.max(right - left, bottom - top);
            float shortSide = Math.max(1.0f, Math.min(right - left, bottom - top));
            int role = longSide / shortSide <= 1.25f ? TouchStyleDraw.ROLE_SLOT : TouchStyleDraw.ROLE_TEXT;
            TouchStyleDraw.drawFrame(canvas, TouchStyleDraw.SHAPE_CHAMFER, left, top, right, bottom, role, pressed,
                    pressed || editSelected, ringColor);
            drawRangeCellContent(canvas, index, left, top, right, bottom, pressed, 0, (right - left) - edge * 2, minTextSize, true);
        }
        canvas.restore();
        inputControlsView.getPaint().setStyle(Paint.Style.STROKE);
    }

    /**
     * Content of cell [index] of the key strip inside [left, top, right, bottom]: a template shows the name of its key, the free
     * list the real icon or the symbol of the WoW action, the own label or the name of the key. [styled]: styles F and G
     * (serif label, symbols smaller while pressed, colours from the style instead of [color]).
     */
    private void drawRangeCellContent(Canvas canvas, int index, float left, float top, float right, float bottom, boolean pressed,
                                      int color, float fitWidth, float maxTextSize, boolean styled) {
        float cx = (left + right) * 0.5f;
        float cy = (top + bottom) * 0.5f;
        Range current = getRange();
        float cellDiameter = Math.min(right - left, bottom - top);
        if (current != Range.CUSTOM) {
            drawRangeCellText(canvas, getRangeTextForIndex(current, index), cx, cy, cellDiameter, fitWidth, maxTextSize, pressed,
                    styled ? TouchStyleDraw.textColor(pressed) : color, styled, false);
            return;
        }
        RadialMenu.Slot item = getRangeItem(index);
        if (item == null) return;
        BindingCaption.Caption caption = BindingCaption.forSlot(item.getLabel(), item.getWowAction(), item.getChatCommand(),
                item.getBindingCombo(), 0);
        float thickness = Math.min(right - left, bottom - top);
        float symbolThickness = styled ? thickness * TouchStyleDraw.symbolScale(pressed) : thickness;
        int contentColor = styled ? TouchStyleDraw.textColor(pressed) : color;
        String symbol = caption.getSymbol();
        if (drawRangeCellIcon(canvas, item.getWowAction(), caption, cx, cy, symbolThickness, contentColor, styled, pressed)) {
            // real icon of the action bar slot; drawn
        }
        else if (symbol != null) {
            WowSymbols.draw(canvas, symbol, cx, cy, symbolThickness * 0.35f, contentColor);
        }
        else if (!caption.getText().isEmpty()) {
            String label = BindingCaption.fit(caption.getText());
            boolean emoji = BindingCaption.containsEmoji(label);
            if (emoji) {
                drawRangeCellText(canvas, label, cx, cy, thickness, fitWidth, Math.min(maxTextSize, thickness * 0.8f), pressed,
                        contentColor, false, true);
            }
            else {
                KeyLabel keyLabel = KeyGlyphs.forCaption(caption, item.getWowAction(), item.getChatCommand(), item.getBindingCombo());
                drawSlotLabel(canvas, inputControlsView.getPaint(), keyLabel, label, cx, cy, thickness, fitWidth, maxTextSize, false,
                        contentColor, styled, pressed);
            }
        }
        drawCooldownInSegment(canvas, item.getWowAction(), caption, cx, cy, symbolThickness);
    }

    /** Text of a strip cell. [emoji]: old size rule (colour emoji of the system font); else TextFit (45 % of [diameter] at most). */
    private void drawRangeCellText(Canvas canvas, String text, float cx, float cy, float diameter, float fitWidth, float maxSize,
                                   boolean pressed, int color, boolean serif, boolean emoji) {
        if (text == null || text.isEmpty()) return;
        Paint paint = inputControlsView.getPaint();
        if (!emoji) {
            KeyGlyphDraw.drawText(canvas, paint, text, cx, cy, diameter, fitWidth, maxSize, false, color, serif, pressed);
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, fitWidth), maxSize));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, cx, cy - ((paint.descent() + paint.ascent()) * 0.5f), paint);
    }

    /** Real icon of the action bar slot in a cell of the key strip (round, like in a ring segment); an own label stays below it. */
    private boolean drawRangeCellIcon(Canvas canvas, String action, BindingCaption.Caption caption, float px, float py, float thickness,
                                      int color, boolean styled, boolean pressed) {
        if (action == null) return false;
        Bitmap icon = WowIcons.bitmapFor(action);
        if (icon == null) return false;
        boolean ownLabel = !caption.getAutomatic() && !caption.getText().isEmpty();
        float half = thickness * (ownLabel ? 0.30f : 0.38f);
        float centerY = ownLabel ? py - thickness * 0.12f : py;
        int alpha = styled ? TouchStyleDraw.iconAlpha(pressed) : Math.max(0xD0, color >>> 24);
        if (!WowIconDraw.draw(canvas, icon, px - half, centerY - half, px + half, centerY + half, WowIconDraw.KIND_OVAL, 0f, alpha)) {
            return false;
        }
        if (styled) TouchStyleDraw.tintIcon(canvas, px - half, centerY - half, px + half, centerY + half, WowIconDraw.KIND_OVAL, 0f, pressed);
        if (ownLabel) {
            WowIconDraw.drawCaption(canvas, BindingCaption.fit(caption.getText()), px, py + thickness * 0.38f, thickness * 0.3f);
        }
        return true;
    }

    private void drawStyledButton(Canvas canvas, Rect box, boolean pressed, boolean editSelected, int ringColor) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        float strokeWidth = TouchStyleDraw.edgePx();
        boolean highlight = pressed || editSelected;
        float cx = box.centerX();
        float cy = box.centerY();
        float contentX = cx;
        float contentY = cy;
        float contentWidth = box.width();
        float contentHeight = box.height();
        boolean noOwnText = text == null || text.isEmpty();
        boolean hasPicture = iconId > 0 || (wowAction != null && WowIcons.bitmapFor(wowAction) != null);

        switch (shape) {
            case CIRCLE:
                TouchStyleDraw.drawFrame(canvas, TouchStyleDraw.SHAPE_OVAL, box.left, box.top, box.right, box.bottom,
                        TouchStyleDraw.ROLE_ROUND, pressed, highlight, ringColor);
                break;
            case ARC: {
                float outer = Math.min(box.width(), box.height()) * 0.5f;
                float inner = outer * (1.0f - ARC_THICKNESS);
                float centerAngle = getArcCenterAngle();
                TouchStyleDraw.drawSegment(canvas, cx, cy, inner, outer, centerAngle - arcSweep * 0.5f, arcSweep, pressed, highlight, ringColor);
                double middleAngle = Math.toRadians(centerAngle);
                float middleRadius = (outer + inner) * 0.5f;
                contentX = cx + (float)Math.cos(middleAngle) * middleRadius;
                contentY = cy + (float)Math.sin(middleAngle) * middleRadius;
                contentWidth = (outer - inner) + snappingSize * 2 * scale;
                contentHeight = contentWidth;
                break;
            }
            default: {
                // Square-ish buttons and buttons with a symbol are slots (cut corners 9 %), flat ones text buttons.
                float longSide = Math.max(box.width(), box.height());
                float shortSide = Math.max(1, Math.min(box.width(), box.height()));
                int role = (hasPicture || longSide / shortSide <= 1.25f) ? TouchStyleDraw.ROLE_SLOT : TouchStyleDraw.ROLE_TEXT;
                TouchStyleDraw.drawFrame(canvas, TouchStyleDraw.SHAPE_CHAMFER, box.left, box.top, box.right, box.bottom,
                        role, pressed, highlight, ringColor);
                break;
            }
        }

        String wowSymbol = noOwnText && wowAction != null ? WowSymbols.symbolFor(wowAction) : null;
        String wowEmoji = noOwnText ? BindingCaption.emojiForAction(wowAction) : null;
        int textColor = TouchStyleDraw.textColor(pressed);
        float k = TouchStyleDraw.symbolScale(pressed);
        if (iconId > 0) {
            drawIcon(canvas, contentX, contentY, contentWidth * k, contentHeight * k, iconId, textColor);
        }
        else if (noOwnText && chatAction != null
                && ChatGlyphDraw.draw(canvas, chatAction, contentX, contentY, Math.min(contentWidth, contentHeight) * k, textColor, true)) {
            // Symbol of the chat control (drawn by the condition).
        }
        else if (drawStyledAddonIcon(canvas, box, contentX, contentY, strokeWidth, pressed)) {
            if (!noOwnText) {
                float captionSize = Math.min(snappingSize * 1.4f * scale, box.height() * 0.26f);
                WowIconDraw.drawCaption(canvas, getDisplayText(), cx, box.bottom - strokeWidth - captionSize * 0.25f, captionSize);
            }
        }
        else if (wowEmoji != null) {
            float emojiSize = Math.min(getTextSizeForWidth(paint, wowEmoji, contentWidth * 0.8f), contentHeight * 0.65f);
            paint.setTextSize(Math.min(emojiSize, snappingSize * 3 * scale) * k);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(textColor);
            canvas.drawText(wowEmoji, contentX, (contentY - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
        }
        else if (wowSymbol != null) {
            int margin = (int)(snappingSize * (shape == Shape.CIRCLE || shape == Shape.SQUARE || shape == Shape.ARC ? 2.0f : 1.0f) * scale);
            float halfSize = (Math.min(contentWidth, contentHeight) - margin) * 0.5f;
            WowSymbols.draw(canvas, wowSymbol, contentX, contentY, halfSize * k, textColor);
        }
        else {
            drawButtonLabel(canvas, paint, contentX, contentY, Math.min(contentWidth, contentHeight), contentWidth - strokeWidth * 2,
                    snappingSize * 2 * scale, textColor, true, pressed);
        }
        // Cooldown of the action bar slot this button stands for (sector and remaining time) over whatever was drawn.
        drawCooldown(canvas, box, contentX, contentY, strokeWidth);
        paint.setStyle(Paint.Style.STROKE);
    }

    /** Like drawAddonIcon, but 25 % smaller while pressed (style G), muted by a tint and with the resting opacity of the style. */
    private boolean drawStyledAddonIcon(Canvas canvas, Rect box, float contentX, float contentY, float strokeWidth, boolean pressed) {
        if (wowAction == null) return false;
        Bitmap icon = WowIcons.bitmapFor(wowAction);
        if (icon == null) return false;
        computeIconBox(box, contentX, contentY, strokeWidth);
        float k = TouchStyleDraw.symbolScale(pressed);
        float midX = iconBox.centerX();
        float midY = iconBox.centerY();
        float halfW = iconBox.width() * 0.5f * k;
        float halfH = iconBox.height() * 0.5f * k;
        boolean drawn = WowIconDraw.draw(canvas, icon, midX - halfW, midY - halfH, midX + halfW, midY + halfH, iconKind, iconRadius * k,
                TouchStyleDraw.iconAlpha(pressed));
        if (drawn) {
            TouchStyleDraw.tintIcon(canvas, midX - halfW, midY - halfH, midX + halfW, midY + halfH, iconKind, iconRadius * k, pressed);
        }
        return drawn;
    }

    private void drawStyledWheel(Canvas canvas, Rect box, boolean pressed, boolean editSelected, int ringColor) {
        Paint paint = inputControlsView.getPaint();
        int snappingSize = inputControlsView.getSnappingSize();
        boolean vertical = orientation == 0;
        float side = Math.min(box.width(), box.height());
        float edge = TouchStyleDraw.edgePx();
        TouchStyleDraw.drawFrame(canvas, TouchStyleDraw.SHAPE_CHAMFER, box.left, box.top, box.right, box.bottom,
                TouchStyleDraw.ROLE_PANEL, pressed, pressed || editSelected, ringColor);

        // Fine grooves that run with the finger, so the wheel looks like it turns (clear of the cut corners).
        float spacing = Math.max(4.0f, snappingSize * 1.0f);
        float phase = wheelPhase % spacing;
        if (phase < 0) phase += spacing;
        float corner = side * 0.17f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.0f, edge * 0.6f));
        paint.setColor(TouchStyleDraw.ribColor());
        if (vertical) {
            for (float gy = box.top + corner + phase; gy < box.bottom - corner; gy += spacing) {
                canvas.drawLine(box.left + edge * 2, gy, box.right - edge * 2, gy, paint);
            }
        }
        else {
            for (float gx = box.left + corner + phase; gx < box.right - corner; gx += spacing) {
                canvas.drawLine(gx, box.top + edge * 2, gx, box.bottom - edge * 2, paint);
            }
        }

        // Small arrows near both ends show the swipe direction.
        float cx = box.exactCenterX();
        float cy = box.exactCenterY();
        float off = side * 0.29f;
        float size = side * 0.11f;
        if (vertical) {
            TouchStyleDraw.drawArrow(canvas, cx, box.top + off, TouchStyleDraw.DIR_UP, size);
            TouchStyleDraw.drawArrow(canvas, cx, box.bottom - off, TouchStyleDraw.DIR_DOWN, size);
        }
        else {
            TouchStyleDraw.drawArrow(canvas, box.left + off, cy, TouchStyleDraw.DIR_LEFT, size);
            TouchStyleDraw.drawArrow(canvas, box.right - off, cy, TouchStyleDraw.DIR_RIGHT, size);
        }
        paint.setStyle(Paint.Style.STROKE);
    }

    private void drawStyledStick(Canvas canvas, Rect box, boolean pressed, boolean editSelected, int ringColor) {
        int snappingSize = inputControlsView.getSnappingSize();
        int cx = box.centerX();
        int cy = box.centerY();
        float stickRadius = getStickRadius();
        float thumbX = currentPosition != null ? currentPosition.x : cx;
        float thumbY = currentPosition != null ? currentPosition.y : cy;
        float thumbRadius = snappingSize * 3.5f * scale;

        if (ringSegments <= 0 && TouchStyleDraw.softStick()) {
            // Style G: joystick as a soft surface. In the editor its size would not show, so a faint base circle is drawn too.
            if (inputControlsView.isEditMode()) {
                TouchStyleDraw.drawFrame(canvas, TouchStyleDraw.SHAPE_OVAL, cx - stickRadius, cy - stickRadius, cx + stickRadius,
                        cy + stickRadius, TouchStyleDraw.ROLE_BASE, false, editSelected, ringColor);
            }
            TouchStyleDraw.drawSoftStick(canvas, cx, cy, stickRadius, thumbX, thumbY, pressed);
            return;
        }

        TouchStyleDraw.drawFrame(canvas, TouchStyleDraw.SHAPE_OVAL, cx - stickRadius, cy - stickRadius, cx + stickRadius,
                cy + stickRadius, TouchStyleDraw.ROLE_BASE, false, editSelected, ringColor);
        if (ringSegments > 0) {
            drawStickRing(canvas, cx, cy, stickRadius, 0, ringColor, editSelected, TouchStyleDraw.edgePx());
        }
        TouchStyleDraw.drawFrame(canvas, TouchStyleDraw.SHAPE_OVAL, thumbX - thumbRadius, thumbY - thumbRadius, thumbX + thumbRadius,
                thumbY + thumbRadius, TouchStyleDraw.ROLE_THUMB, pressed, TouchStyleDraw.thumbHighlight(pressed), 0);
        drawStickCenterCaption(canvas, thumbX, thumbY, thumbRadius, TouchStyleDraw.textColor(pressed));
        inputControlsView.getPaint().setStyle(Paint.Style.STROKE);
    }

    /**
     * Draws the real icon of the action bar slot this button stands for (see WowIcons) into the shape of the button.
     * Returns false while there is none (no addon data, not downloaded yet): the symbol, emoji or text is drawn then.
     */
    private boolean drawAddonIcon(Canvas canvas, Rect box, float contentX, float contentY, float strokeWidth, int contentColor) {
        if (wowAction == null) return false;
        Bitmap icon = WowIcons.bitmapFor(wowAction);
        if (icon == null) return false;
        computeIconBox(box, contentX, contentY, strokeWidth);
        int alpha = Math.max(0xD0, contentColor >>> 24);
        return WowIconDraw.draw(canvas, icon, iconBox.left, iconBox.top, iconBox.right, iconBox.bottom, iconKind, iconRadius, alpha);
    }

    /** Cooldown picture (CooldownDraw) over the square of the action symbol; nothing for other actions or an idle slot. */
    private void drawCooldown(Canvas canvas, Rect box, float contentX, float contentY, float strokeWidth) {
        if (wowAction == null) return;
        computeIconBox(box, contentX, contentY, strokeWidth);
        CooldownDraw.draw(canvas, wowAction, iconBox.left, iconBox.top, iconBox.right, iconBox.bottom, iconKind, iconRadius);
    }

    /** Square (iconBox), shape kind (iconKind) and corner radius (iconRadius) of the action symbol inside the button. */
    private void computeIconBox(Rect box, float contentX, float contentY, float strokeWidth) {
        float inset = strokeWidth * 1.5f;
        float left = box.left + inset;
        float top = box.top + inset;
        float right = box.right - inset;
        float bottom = box.bottom - inset;
        float side = Math.min(right - left, bottom - top);
        int kind = WowIconDraw.KIND_ROUND;
        float radius = 0f;
        switch (shape) {
            case CIRCLE:
                kind = WowIconDraw.KIND_OVAL;
                break;
            case RECT:
                kind = WowIconDraw.KIND_RECT;
                break;
            case ROUND_RECT:
                radius = side * 0.5f * cornerRadius;
                break;
            case PILL:
                radius = side * 0.5f;
                break;
            case SQUARE:
                radius = inputControlsView.getSnappingSize() * 0.75f * scale;
                break;
            case ARC: {
                float outer = Math.min(box.width(), box.height()) * 0.5f;
                float half = outer * ARC_THICKNESS * 0.5f * 0.9f;
                left = contentX - half;
                right = contentX + half;
                top = contentY - half;
                bottom = contentY + half;
                kind = WowIconDraw.KIND_OVAL;
                break;
            }
        }
        // The icon is square: keep it square and centred inside wide or tall shapes.
        if (shape != Shape.ARC) {
            float cx = (left + right) * 0.5f;
            float cy = (top + bottom) * 0.5f;
            float half = Math.min(right - left, bottom - top) * 0.5f;
            left = cx - half;
            right = cx + half;
            top = cy - half;
            bottom = cy + half;
        }
        iconBox.set(left, top, right, bottom);
        iconKind = kind;
        iconRadius = radius;
    }

    /** Annular sector from [startDegrees] over [sweepDegrees] (clockwise, 0 = 3 oclock) as a closed path. */
    private void buildSectorPath(Path path, float cx, float cy, float innerRadius, float outerRadius, float startDegrees, float sweepDegrees) {
        arcOuterRect.set(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius);
        arcInnerRect.set(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius);
        path.reset();
        path.arcTo(arcOuterRect, startDegrees, sweepDegrees);
        path.arcTo(arcInnerRect, startDegrees + sweepDegrees, -sweepDegrees);
        path.close();
    }

    private void drawStickRing(Canvas canvas, float cx, float cy, float stickRadius, int normalColor, int activeColor, boolean editSelected, float strokeWidth) {
        Paint paint = inputControlsView.getPaint();
        float inner = stickRadius + getRingGapPixels();
        float outer = inner + getRingWidthPixels();
        drawRingBand(canvas, cx, cy, inner, outer, false, normalColor, activeColor, editSelected, strokeWidth);
        if (hasAnyRingOuter()) {
            float outerInner = outer + getRingOuterGapPixels();
            drawRingBand(canvas, cx, cy, outerInner, outerInner + getRingWidthPixels(), true, normalColor, activeColor, editSelected, strokeWidth);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
    }

    /** One band of key segments: the inner ring ([outerBand] false) or the optional outer second ring. */
    private void drawRingBand(Canvas canvas, float cx, float cy, float inner, float outer, boolean outerBand, int normalColor, int activeColor, boolean editSelected, float strokeWidth) {
        Paint paint = inputControlsView.getPaint();
        Path path = inputControlsView.getPath();
        int snappingSize = inputControlsView.getSnappingSize();
        float step = 360.0f / ringSegments;
        float sweep = Math.max(1.0f, step - StickRing.SEGMENT_SEPARATION_DEGREES);
        float middleRadius = (inner + outer) * 0.5f;
        float thickness = outer - inner;
        boolean styled = TouchStyleDraw.isStyled();

        boolean editing = inputControlsView.isEditMode();
        for (int i = 0; i < ringSegments; i++) {
            if (outerBand && !hasRingOuter(i)) continue;
            // An empty field (no assignment, or an action bar slot without a spell) is not shown in the game; the editor shows it dashed.
            RadialMenu.Slot fieldItem = outerBand ? getRingOuterItem(i) : getRingItem(i);
            boolean emptyField = RadialSlotVisibility.isEmptyInRing(fieldItem);
            if (emptyField && !editing) continue;
            boolean pressed = i == ringActiveSegment && ringActiveOuter == outerBand;
            boolean segmentActive = editSelected || pressed;
            int color = segmentActive ? activeColor : normalColor;
            // A WoW action without a key in WoW is shown dimmed: it is there, but a tap does nothing until the key is set in WoW.
            if (!emptyField && RadialSlotVisibility.isInactive(fieldItem)) {
                color = ColorUtils.setAlphaComponent(color, (color >>> 24) * 2 / 5);
            }
            float centerAngle = StickRing.segmentCenterAngle(i, ringSegments, ringStartAngle);
            // Styles F and G: the segment frame comes from TouchStyleDraw; symbol and text follow its colours and the pressed size.
            float contentThickness = thickness;
            if (emptyField) {
                buildSectorPath(path, cx, cy, inner, outer, centerAngle - sweep * 0.5f, sweep);
                float dash = Math.max(3.0f, strokeWidth * 3.0f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setColor(color);
                paint.setPathEffect(new DashPathEffect(new float[] {dash, dash}, 0.0f));
                canvas.drawPath(path, paint);
                paint.setPathEffect(null);
                if (styled) color = TouchStyleDraw.textColor(false);
            }
            else if (styled) {
                TouchStyleDraw.drawSegment(canvas, cx, cy, inner, outer, centerAngle - sweep * 0.5f, sweep, pressed, segmentActive,
                        editSelected ? (activeColor | 0xff000000) : 0);
                color = TouchStyleDraw.textColor(pressed);
                contentThickness = thickness * TouchStyleDraw.symbolScale(pressed);
            }
            else {
                buildSectorPath(path, cx, cy, inner, outer, centerAngle - sweep * 0.5f, sweep);

                if (pressed) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(ColorUtils.setAlphaComponent(color, Math.min(80, color >>> 24)));
                    canvas.drawPath(path, paint);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setColor(color);
                canvas.drawPath(path, paint);
            }

            RadialMenu.Slot item = outerBand ? getRingOuterItem(i) : getRingItem(i);
            double middleAngle = Math.toRadians(centerAngle);
            float px = cx + (float)Math.cos(middleAngle) * middleRadius;
            float py = cy + (float)Math.sin(middleAngle) * middleRadius;
            BindingCaption.Caption caption = BindingCaption.forSlot(item.getLabel(), item.getWowAction(), item.getChatCommand(),
                    item.getBindingCombo(), i + 1);
            String symbol = caption.getSymbol();
            if (drawAddonIconInSegment(canvas, item.getWowAction(), caption, px, py, contentThickness, color)) {
                // real icon of the action bar slot; drawn
            }
            else if (symbol != null) {
                WowSymbols.draw(canvas, symbol, px, py, contentThickness * 0.35f, color);
            }
            else if (!caption.getText().isEmpty()) {
                String label = BindingCaption.fit(caption.getText());
                if (BindingCaption.containsEmoji(label)) {
                    // Emojis may be a little bigger than letters; the system font draws them in colour.
                    float maxSize = contentThickness * 0.8f;
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, label, contentThickness * 1.3f), Math.min(maxSize, snappingSize * 2 * scale)));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(color);
                    canvas.drawText(label, px, py - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                }
                else {
                    // Symbol for keys that have one, else text; ring fields are smaller than the middle (TextFit). The size
                    // comes from the unpressed thickness; a pressed label is shrunk by the drawing itself.
                    KeyLabel keyLabel = KeyGlyphs.forCaption(caption, item.getWowAction(), item.getChatCommand(), item.getBindingCombo());
                    drawSlotLabel(canvas, paint, keyLabel, label, px, py, thickness, thickness * 1.3f, snappingSize * 2 * scale, true,
                            color, styled, pressed);
                }
            }
            // Cooldown of the action bar slot of this segment over its symbol (same circle as the real icon).
            drawCooldownInSegment(canvas, item.getWowAction(), caption, px, py, contentThickness);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
    }

    /** Cooldown picture over the symbol of a ring segment or stick knob (same circle as the real icon, see drawAddonIconInSegment). */
    private void drawCooldownInSegment(Canvas canvas, String action, BindingCaption.Caption caption, float px, float py, float thickness) {
        if (action == null) return;
        boolean ownLabel = !caption.getAutomatic() && !caption.getText().isEmpty();
        float half = thickness * (ownLabel ? 0.30f : 0.38f);
        float centerY = ownLabel ? py - thickness * 0.12f : py;
        CooldownDraw.draw(canvas, action, px - half, centerY - half, px + half, centerY + half, CooldownDraw.KIND_OVAL, 0f);
    }

    /** Real icon of the action bar slot in a ring segment; an own label stays as small text below it. */
    private boolean drawAddonIconInSegment(Canvas canvas, String action, BindingCaption.Caption caption, float px, float py, float thickness, int color) {
        if (action == null) return false;
        Bitmap icon = WowIcons.bitmapFor(action);
        if (icon == null) return false;
        boolean ownLabel = !caption.getAutomatic() && !caption.getText().isEmpty();
        float half = thickness * (ownLabel ? 0.30f : 0.38f);
        float centerY = ownLabel ? py - thickness * 0.12f : py;
        if (!WowIconDraw.draw(canvas, icon, px - half, centerY - half, px + half, centerY + half, WowIconDraw.KIND_OVAL, 0f,
                Math.max(0xD0, color >>> 24))) {
            return false;
        }
        if (ownLabel) {
            float captionSize = thickness * 0.3f;
            WowIconDraw.drawCaption(canvas, BindingCaption.fit(caption.getText()), px, py + thickness * 0.38f, captionSize);
        }
        return true;
    }

    private void drawScrollWheel(Canvas canvas, Rect box, int primaryColor, int activeColor, boolean editSelected, float strokeWidth) {
        Paint paint = inputControlsView.getPaint();
        Path path = inputControlsView.getPath();
        int snappingSize = inputControlsView.getSnappingSize();
        boolean vertical = orientation == 0;
        float radius = Math.min(box.width(), box.height()) * 0.5f;
        int outlineColor = editSelected ? activeColor : primaryColor;

        path.reset();
        path.addRoundRect(box.left, box.top, box.right, box.bottom, radius, radius, Path.Direction.CW);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(outlineColor);
        canvas.drawPath(path, paint);

        // Grooves run with the finger, so the wheel looks like it turns.
        float spacing = Math.max(4.0f, snappingSize * 1.0f);
        float phase = wheelPhase % spacing;
        if (phase < 0) phase += spacing;
        canvas.save();
        canvas.clipPath(path);
        paint.setStrokeWidth(Math.max(1.0f, strokeWidth * 0.6f));
        paint.setColor(ColorUtils.setAlphaComponent(outlineColor, (outlineColor >>> 24) / 2));
        if (vertical) {
            for (float gy = box.top + phase; gy < box.bottom; gy += spacing) {
                canvas.drawLine(box.left + strokeWidth, gy, box.right - strokeWidth, gy, paint);
            }
        }
        else {
            for (float gx = box.left + phase; gx < box.right; gx += spacing) {
                canvas.drawLine(gx, box.top + strokeWidth, gx, box.bottom - strokeWidth, paint);
            }
        }
        canvas.restore();

        // Small chevrons at both ends show the swipe direction.
        float size = Math.min(box.width(), box.height()) * 0.18f;
        float cx = box.exactCenterX();
        float cy = box.exactCenterY();
        float inset = Math.min(box.width(), box.height()) * 0.5f;
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(outlineColor);
        if (vertical) {
            float top = box.top + inset;
            float bottom = box.bottom - inset;
            canvas.drawLine(cx - size, top + size * 0.5f, cx, top - size * 0.5f, paint);
            canvas.drawLine(cx, top - size * 0.5f, cx + size, top + size * 0.5f, paint);
            canvas.drawLine(cx - size, bottom - size * 0.5f, cx, bottom + size * 0.5f, paint);
            canvas.drawLine(cx, bottom + size * 0.5f, cx + size, bottom - size * 0.5f, paint);
        }
        else {
            float left = box.left + inset;
            float right = box.right - inset;
            canvas.drawLine(left + size * 0.5f, cy - size, left - size * 0.5f, cy, paint);
            canvas.drawLine(left - size * 0.5f, cy, left + size * 0.5f, cy + size, paint);
            canvas.drawLine(right - size * 0.5f, cy - size, right + size * 0.5f, cy, paint);
            canvas.drawLine(right + size * 0.5f, cy, right - size * 0.5f, cy + size, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
    }

    private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId, int tintColor) {
        Paint paint = inputControlsView.getPaint();
        Bitmap icon = inputControlsView.getIcon((byte)iconId);
        paint.setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN));
        int margin = (int)(inputControlsView.getSnappingSize() * (shape == Shape.CIRCLE || shape == Shape.SQUARE || shape == Shape.ARC ? 2.0f : 1.0f) * scale);
        int halfSize = (int)((Math.min(width, height) - margin) * 0.5f);

        Rect srcRect = new Rect(0, 0, icon.getWidth(), icon.getHeight());
        Rect dstRect = new Rect((int)(cx - halfSize), (int)(cy - halfSize), (int)(cx + halfSize), (int)(cy + halfSize));
        canvas.drawBitmap(icon, srcRect, dstRect, paint);
        paint.setColorFilter(null);
    }

    public JSONObject toJSONObject() {
        try {
            JSONObject elementJSONObject = new JSONObject();
            elementJSONObject.put("type", type.name());
            elementJSONObject.put("shape", shape.name());

            JSONArray bindingsJSONArray = new JSONArray();
            for (BindingCombo binding : bindings) bindingsJSONArray.put(binding.toJsonValue());

            elementJSONObject.put("bindings", bindingsJSONArray);
            elementJSONObject.put("scale", Float.valueOf(scale));
            elementJSONObject.put("x", (float)x / inputControlsView.getMaxWidth());
            elementJSONObject.put("y", (float)y / inputControlsView.getMaxHeight());
            elementJSONObject.put("toggleSwitch", toggleSwitch);
            elementJSONObject.put("text", text);
            elementJSONObject.put("iconId", iconId);

            if (type == Type.RANGE_BUTTON && range != null) {
                elementJSONObject.put("range", range.name());
                if (orientation != 0) elementJSONObject.put("orientation", orientation);
                if (scrollLocked) elementJSONObject.put("scrollLocked", true);
            }

            if (type == Type.SHOOTER_MODE) {
                elementJSONObject.put("shooterMovementType", shooterMovementType);
                elementJSONObject.put("shooterLookType", shooterLookType);
                elementJSONObject.put("shooterLookSensitivity", (double) shooterLookSensitivity);
                elementJSONObject.put("shooterJoystickSize", (double) shooterJoystickSize);
            }

            if (buttonColor != DEFAULT_BUTTON_COLOR) elementJSONObject.put("buttonColor", formatRgbColor(buttonColor));
            if (buttonActiveColorCustom || buttonActiveColor != DEFAULT_BUTTON_ACTIVE_COLOR) elementJSONObject.put("buttonActiveColor", formatRgbColor(buttonActiveColor));
            if (buttonOpacity >= 0) elementJSONObject.put("buttonOpacity", (double)buttonOpacity);
            if (buttonStrokeScale != DEFAULT_BUTTON_STROKE_SCALE) elementJSONObject.put("buttonStrokeScale", (double)buttonStrokeScale);
            if (type == Type.BUTTON && lookThrough != null) elementJSONObject.put("lookThrough", lookThrough);
            if (type == Type.BUTTON && !shooterLookThrough) elementJSONObject.put("shooterLookThrough", false);
            if (radialMenuId != null) elementJSONObject.put("radialMenuId", radialMenuId);
            if (wowAction != null) elementJSONObject.put("wowAction", wowAction);
            if (chatCommand != null) elementJSONObject.put("chatCommand", chatCommand);
            if (type == Type.SCROLL_WHEEL && orientation != 0) elementJSONObject.put("orientation", orientation);
            // Pinned to the chat window: in the editor what is on screen is what is saved, so the offset from the corner is measured now
            // (while playing the button was moved by the offset, which would only measure the same value again).
            if (type == Type.BUTTON && chatAnchor != null && inputControlsView.isEditMode()) {
                measureChatAnchor(inputControlsView.getChatRect());
            }
            writeExtrasToJson(elementJSONObject);

            return elementJSONObject;
        }
        catch (JSONException e) {
            return null;
        }
    }

    public boolean containsPoint(float x, float y) {
        return getBoundingBox().contains((int)(x + 0.5f), (int)(y + 0.5f));
    }

    /** Radius of the stick circle itself; a key ring lies outside of it. */
    public float getStickRadius() {
        getBoundingBox();
        return type == Type.STICK ? stickRadiusPx : getBoundingBox().width() * 0.5f;
    }

    private float getRingGapPixels() {
        return ringGap * inputControlsView.getSnappingSize() * scale;
    }

    private float getRingWidthPixels() {
        return ringWidth * inputControlsView.getSnappingSize() * scale;
    }

    private float getRingOuterGapPixels() {
        return StickRing.OUTER_GAP * inputControlsView.getSnappingSize() * scale;
    }

    /**
     * What a touch at (x, y) hits on a stick with key ring, see {@link StickRing#zoneAt}. A touch in the outer band at a
     * segment without outer field hits nothing (it passes on to whatever lies below, like a touch outside the ring).
     */
    private int ringZoneAt(float x, float y) {
        Rect box = getBoundingBox();
        float dx = x - box.centerX();
        float dy = y - box.centerY();
        float outerWidth = hasAnyRingOuter() ? getRingWidthPixels() : 0.0f;
        int zone = StickRing.zoneAt(dx, dy, getStickRadius(), getRingGapPixels(), getRingWidthPixels(), getRingOuterGapPixels(), outerWidth);
        if (zone == StickRing.ZONE_OUTER || zone == StickRing.ZONE_RING) {
            int segment = StickRing.segmentIndex(StickRing.angleOf(dx, dy), ringSegments, ringStartAngle);
            if (zone == StickRing.ZONE_OUTER && !hasRingOuter(segment)) return StickRing.ZONE_NONE;
            // A hidden (empty) field cannot be tapped; the touch passes on like one outside the ring. The editor hits every field.
            if (isRingSegmentHidden(segment, zone == StickRing.ZONE_OUTER)) return StickRing.ZONE_NONE;
        }
        return zone;
    }

    /** True when ring field [index] (inner or [outerBand]) is empty and therefore not shown and not tappable in the game; never in the editor. */
    private boolean isRingSegmentHidden(int index, boolean outerBand) {
        if (index < 0 || inputControlsView.isEditMode()) return false;
        return RadialSlotVisibility.isEmptyInRing(outerBand ? getRingOuterItem(index) : getRingItem(index));
    }

    private float getArcCenterAngle() {
        return -90.0f + rotation;
    }

    private boolean isInsideArc(float x, float y) {
        Rect box = getBoundingBox();
        float outer = Math.min(box.width(), box.height()) * 0.5f;
        return StickRing.inSector(x - box.centerX(), y - box.centerY(), outer * (1.0f - ARC_THICKNESS), outer,
                getArcCenterAngle(), arcSweep);
    }

    /** Hit test of a game touch: the bounding box, except ARC buttons (their sector) and sticks with key ring (the stick circle). */
    private boolean hitTest(float x, float y) {
        if (!containsPoint(x, y)) return false;
        if (type == Type.STICK && ringSegments > 0) return ringZoneAt(x, y) == StickRing.ZONE_STICK;
        if (type == Type.BUTTON && shape == Shape.ARC) return isInsideArc(x, y);
        return true;
    }

    private boolean isKeepButtonPressedAfterMinTime() {
        BindingCombo bindingCombo = getBindingComboAt(0);
        return !usesToggleSwitch() &&
                (bindingCombo.contains(Binding.GAMEPAD_BUTTON_L3) || bindingCombo.contains(Binding.GAMEPAD_BUTTON_R3));
    }

    private boolean isGyroModifierControl() {
        int dispatchableBindingCount;
        switch (type) {
            case BUTTON:
                dispatchableBindingCount = Math.min(2, bindings.length);
                break;
            case D_PAD:
            case STICK:
            case TRACKPAD:
                dispatchableBindingCount = bindings.length;
                break;
            default:
                dispatchableBindingCount = 0;
                break;
        }
        for (int i = 0; i < dispatchableBindingCount; i++) {
            if (bindings[i].contains(Binding.GYRO_MODIFIER)) return true;
        }
        return false;
    }

    private boolean usesToggleSwitch() {
        return toggleSwitch && !isGyroModifierControl();
    }

    public boolean isRadialMenuButton() {
        return type == Type.BUTTON &&
                (getBindingComboAt(0).contains(Binding.OPEN_RADIAL_MENU) ||
                        getBindingComboAt(1).contains(Binding.OPEN_RADIAL_MENU));
    }

    private boolean handleRadialMenuDirectionalMove(int pointerId, boolean[] directionalStates, float x, float y) {
        if (radialMenuTouchActive) {
            inputControlsView.handleRadialMenuTouchMove(pointerId, x, y);
            inputControlsView.invalidate();
            return true;
        }

        for (byte i = 0; i < directionalStates.length && i < bindings.length; i++) {
            if (getBindingComboAt(i).contains(Binding.OPEN_RADIAL_MENU) && directionalStates[i]) {
                releaseActiveDirectionalStates();
                radialMenuTouchActive = true;
                inputControlsView.handleRadialMenuTouchDown(pointerId, x, y, radialMenuId, radialDirection);
                inputControlsView.invalidate();
                return true;
            }
        }

        return false;
    }

    private void releaseActiveDirectionalStates() {
        for (byte i = 0; i < states.length && i < bindings.length; i++) {
            if (states[i]) handleBindingInputEvent(i, false);
            else if (gamepadAxisActive[i]) {
                Binding gamepadAxisBinding = findGamepadAxisBinding(getBindingComboAt(i));
                if (gamepadAxisBinding != Binding.NONE) {
                    inputControlsView.handleInputEvent(gamepadAxisBinding, false, 0);
                }
            }
            states[i] = false;
            gamepadAxisActive[i] = false;
        }
    }

    public boolean handleTouchDown(int pointerId, float x, float y) {
        // A hidden unassigned button is not tappable in the game: the touch passes on to whatever lies below.
        if (isHiddenUnassignedButton()) return false;
        // Stick with key ring: a touch in the ring (not in the stick circle or the gap) taps a segment with its own pointer.
        if (type == Type.STICK && ringSegments > 0) {
            int zone = ringZoneAt(x, y);
            if (zone == StickRing.ZONE_RING || zone == StickRing.ZONE_OUTER) {
                return ringTouchDown(pointerId, x, y, zone == StickRing.ZONE_OUTER);
            }
        }
        if (currentPointerId == -1 && hitTest(x, y)) {
            currentPointerId = pointerId;
            currentPointerActivatedButtonBindings = false;
            inputControlsView.invalidate();
            if (type == Type.BUTTON) {
                if (isRadialMenuButton()) {
                    inputControlsView.handleRadialMenuTouchDown(pointerId, x, y, radialMenuId, radialDirection);
                    inputControlsView.invalidate();
                    return true;
                }
                if (chatCommand != null) {
                    // Chat command button: types the text into the WoW chat once per press, no key is held.
                    inputControlsView.runChatCommand(chatCommand);
                    inputControlsView.invalidate();
                    return true;
                }
                if (chatAction != null) {
                    // Chat control (voice, send, keyboard, channel, cancel): runs once per press, no key is held.
                    inputControlsView.runChatAction(chatAction);
                    inputControlsView.invalidate();
                    return true;
                }
                ModKey stickyKey = stickyModKey();
                if (stickyKey != null) {
                    // Latching modifier: the key goes down now, what happens on lift depends on how long the finger stayed
                    prepareStickyModifiers();
                    StickyModifiers.press(stickyKey, SystemClock.uptimeMillis());
                    inputControlsView.invalidate();
                    return true;
                }
                if (isKeepButtonPressedAfterMinTime()) touchTime = System.currentTimeMillis();
                if (!usesToggleSwitch() || !selected) {
                    currentPointerActivatedButtonBindings = true;
                    handleBindingInputEvent(0, true);
                    handleBindingInputEvent(1, true);
                }
                inputControlsView.invalidate();
                return true;
            }
            else if (type == Type.SHOOTER_MODE) {
                // Toggle handled on touch up
                return true;
            }
            else if (type == Type.RANGE_BUTTON) {
                scroller.handleTouchDown(x, y);
                return true;
            }
            else if (type == Type.SCROLL_WHEEL) {
                wheelTouchDown(x, y);
                return true;
            }
            else {
                if (type == Type.TRACKPAD) {
                    if (currentPosition == null) currentPosition = new PointF();
                    currentPosition.set(x, y);
                }
                if (type == Type.STICK) {
                    centerDownTime = currentTimeMillis();
                    centerMaxOffset = 0.0f;
                    centerGeneration++;
                    // With a middle assignment the stick waits to see whether this is a tap (see StickCenter).
                    centerUndecided = hasCenterAction();
                    boolean handled = handleTouchMove(pointerId, x, y);
                    if (centerUndecided) scheduleCenterDecision();
                    return handled;
                }
                return handleTouchMove(pointerId, x, y);
            }
        }
        else return false;
    }

    public boolean handleTouchMove(int pointerId, float x, float y) {
        // The finger on a ring segment stays on that segment, moving it around fires nothing else.
        if (ringPointerId != -1 && pointerId == ringPointerId) return true;

        if (pointerId == currentPointerId && type == Type.SCROLL_WHEEL) {
            wheelTouchMove(x, y);
            return true;
        }

        if (pointerId == currentPointerId && isRadialMenuButton()) {
            inputControlsView.handleRadialMenuTouchMove(pointerId, x, y);
            return true;
        }

        if (pointerId == currentPointerId && (type == Type.BUTTON || type == Type.SHOOTER_MODE)) {
            return true;
        }

        if (pointerId == currentPointerId && (type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD)) {
            float deltaX, deltaY;
            Rect boundingBox = getBoundingBox();
            // A stick with key ring has a bigger bounding box than its circle.
            float radius = type == Type.STICK ? getStickRadius() : boundingBox.width() * 0.5f;
            float originX = boundingBox.centerX() - radius;
            float originY = boundingBox.centerY() - radius;
            TouchpadView touchpadView =  inputControlsView.getTouchpadView();

            if (type == Type.TRACKPAD) {
                if (currentPosition == null) currentPosition = new PointF();
                float[] deltaPoint = touchpadView.computeDeltaPoint(currentPosition.x, currentPosition.y, x, y);
                deltaX = deltaPoint[0];
                deltaY = deltaPoint[1];
                currentPosition.set(x, y);
            }
            else {
                float localX = x - originX;
                float localY = y - originY;
                float offsetX = localX - radius;
                float offsetY = localY - radius;

                float distance = Mathf.lengthSq(radius - localX, radius - localY);
                if (distance > radius * radius) {
                    float angle = (float)Math.atan2(offsetY, offsetX);
                    offsetX = (float)(Math.cos(angle) * radius);
                    offsetY = (float)(Math.sin(angle) * radius);
                }

                deltaX = Mathf.clamp(offsetX / radius, -1, 1);
                deltaY = Mathf.clamp(offsetY / radius, -1, 1);
            }

            if (type == Type.STICK) {
                if (currentPosition == null) currentPosition = new PointF();
                currentPosition.x = originX + deltaX * radius + radius;
                currentPosition.y = originY + deltaY * radius + radius;
                // Farthest the finger got from the middle (for the tap on the middle, see StickCenter).
                centerMaxOffset = Math.max(centerMaxOffset, (float)Math.hypot(x - boundingBox.centerX(), y - boundingBox.centerY()));
                centerLastX = x;
                centerLastY = y;
                if (centerUndecided) {
                    if (centerStillTap()) {
                        // Still a possible tap: no output yet, so small movements never reach the camera.
                        inputControlsView.invalidate();
                        return true;
                    }
                    // A drag: from here on the stick works as usual, starting at the current point of the finger.
                    centerUndecided = false;
                }
                final boolean[] states = {deltaY <= -STICK_DEAD_ZONE, deltaX >= STICK_DEAD_ZONE, deltaY >= STICK_DEAD_ZONE, deltaX <= -STICK_DEAD_ZONE};
                if (handleRadialMenuDirectionalMove(pointerId, states, x, y)) return true;

                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    BindingCombo bindingCombo = getBindingComboAt(i);
                    Binding binding = bindingCombo.getPrimaryBinding();
                    Binding gamepadAxisBinding = findGamepadAxisBinding(bindingCombo);
                    if (gamepadAxisBinding != Binding.NONE && !bindingCombo.isSequence()) {
                        value = StickResponse.stickResponse(value, isRightThumb(gamepadAxisBinding) ? stickSensitivityCamera : stickSensitivityWalk, STICK_AXIS_DEAD_ZONE, StickResponse.CURVE);
                        inputControlsView.handleInputEvent(gamepadAxisBinding, true, value);
                        gamepadAxisActive[i] = value != 0;
                        boolean nextState = states[i];
                        if (!bindingCombo.isSingleBinding() && this.states[i] != nextState) {
                            handleSimultaneousBindingMembers(
                                    i,
                                    gamepadAxisBinding,
                                    nextState,
                                    value);
                        }
                        this.states[i] = bindingCombo.isSingleBinding() || nextState;
                    }
                    else {
                        boolean state = binding.isMouseMove() ? (states[i] || states[(i+2)%4]) : states[i];
                        if (binding.isMouseMove() || this.states[i] != state) {
                            handleBindingInputEvent(i, state, value);
                        }
                        this.states[i] = state;
                    }
                }

                inputControlsView.invalidate();
            }
            else if (type == Type.TRACKPAD) {
                final boolean[] states = {deltaY <= -TRACKPAD_MIN_SPEED, deltaX >= TRACKPAD_MIN_SPEED, deltaY >= TRACKPAD_MIN_SPEED, deltaX <= -TRACKPAD_MIN_SPEED};
                if (handleRadialMenuDirectionalMove(pointerId, states, x, y)) return true;
                int cursorDx = 0;
                int cursorDy = 0;

                for (byte i = 0; i < 4; i++) {
                    float value = (i == 1 || i == 3 ? deltaX : deltaY);
                    BindingCombo bindingCombo = getBindingComboAt(i);
                    Binding binding = bindingCombo.getPrimaryBinding();
                    Binding mouseMoveBinding = findMouseMoveBinding(bindingCombo);
                    if (mouseMoveBinding != Binding.NONE && !bindingCombo.isSequence()) {
                        if (Math.abs(value) > TouchpadView.CURSOR_ACCELERATION_THRESHOLD) {
                            value *= TouchpadView.CURSOR_ACCELERATION;
                        }
                        if (mouseMoveBinding == Binding.MOUSE_MOVE_LEFT || mouseMoveBinding == Binding.MOUSE_MOVE_RIGHT) {
                            cursorDx = Mathf.roundPoint(value);
                        }
                        else {
                            cursorDy = Mathf.roundPoint(value);
                        }
                        boolean nextState = states[i];
                        if (!bindingCombo.isSingleBinding() && this.states[i] != nextState) {
                            handleSimultaneousBindingMembers(
                                    i,
                                    mouseMoveBinding,
                                    nextState,
                                    Mathf.clamp(value, -1, 1));
                        }
                        this.states[i] = nextState;
                    }
                    else {
                        Binding gamepadAxisBinding = findGamepadAxisBinding(bindingCombo);
                        if (gamepadAxisBinding != Binding.NONE && !bindingCombo.isSequence()) {
                            if (interpolator == null) interpolator = new CubicBezierInterpolator();
                            value *= stickSensitivityCamera;
                            interpolator.set(0.075f, 0.95f, 0.45f, 0.95f);
                            float interpolatedValue = interpolator.getInterpolation(Math.min(1.0f, Math.abs(value / TRACKPAD_MAX_SPEED)));
                            float gamepadOffset = Mathf.clamp(interpolatedValue * Mathf.sign(value), -1, 1);
                            inputControlsView.handleInputEvent(gamepadAxisBinding, true, gamepadOffset);
                            gamepadAxisActive[i] = gamepadOffset != 0;
                            boolean nextState = states[i];
                            if (!bindingCombo.isSingleBinding() && this.states[i] != nextState) {
                                handleSimultaneousBindingMembers(
                                        i,
                                        gamepadAxisBinding,
                                        nextState,
                                        gamepadOffset);
                            }
                            this.states[i] = bindingCombo.isSingleBinding() || nextState;
                        }
                        else if (bindingCombo.isSingleBinding() &&
                                (binding == Binding.MOUSE_SCROLL_UP || binding == Binding.MOUSE_SCROLL_DOWN)) {
                            // Wheel ticks are pulses, not held buttons. Repeat for each active movement sample.
                            if (states[i]) {
                                handleBindingInputEvent(i, true, value);
                                handleBindingInputEvent(i, false, value);
                            }
                            this.states[i] = false;
                        }
                        else if (this.states[i] != states[i]) {
                            handleBindingInputEvent(i, states[i], value);
                            this.states[i] = states[i];
                        }
                    }
                }

                if (cursorDx != 0 || cursorDy != 0) inputControlsView.getXServer().injectPointerMoveDelta(cursorDx, cursorDy);
            }
            else {
                final boolean[] states = {deltaY <= -DPAD_DEAD_ZONE, deltaX >= DPAD_DEAD_ZONE, deltaY >= DPAD_DEAD_ZONE, deltaX <= -DPAD_DEAD_ZONE};
                if (handleRadialMenuDirectionalMove(pointerId, states, x, y)) return true;

                // Release transitions first because opposing stick and mouse-move
                // bindings share an axis. An inactive direction must not clear an
                // active direction later in the same update.
                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    if (this.states[i] && !states[i]) {
                        handleBindingInputEvent(i, false, value);
                    }
                }

                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    if (states[i]) handleBindingInputEvent(i, true, value);
                    this.states[i] = states[i];
                }

                inputControlsView.invalidate();
            }

            return true;
        }
        else if (pointerId == currentPointerId && type == Type.RANGE_BUTTON) {
            scroller.handleTouchMove(x, y);
            return true;
        }
        else return false;
    }

    public boolean handleTouchUp(int pointerId) {
        if (ringPointerId != -1 && pointerId == ringPointerId) {
            releaseRing();
            return true;
        }
        if (pointerId == currentPointerId) {
            if (radialMenuTouchActive) {
                radialMenuTouchActive = false;
                releaseActiveDirectionalStates();
                inputControlsView.handleRadialMenuTouchUp(pointerId, true);
                StickyModifiers.consume();
                if (currentPosition != null) currentPosition = null;
                currentPointerId = -1;
                inputControlsView.invalidate();
                return true;
            }

            if (type == Type.BUTTON) {
                ModKey stickyKey = stickyModKey();
                if (stickyKey != null) {
                    StickyModifiers.release(stickyKey, SystemClock.uptimeMillis(), false);
                    currentPointerId = -1;
                    inputControlsView.invalidate();
                    return true;
                }
                if (isRadialMenuButton()) {
                    inputControlsView.handleRadialMenuTouchUp(pointerId, true);
                    StickyModifiers.consume();
                    currentPointerId = -1;
                    inputControlsView.invalidate();
                    return true;
                }
                else if (isKeepButtonPressedAfterMinTime() && touchTime != null) {
                    selected = (System.currentTimeMillis() - (long)touchTime) > BUTTON_MIN_TIME_TO_KEEP_PRESSED;
                    if (!selected) {
                        handleBindingInputEvent(0, false);
                        handleBindingInputEvent(1, false);
                    }
                    touchTime = null;
                    inputControlsView.invalidate();
                }
                else if (chatCommand == null && chatAction == null && (!usesToggleSwitch() || selected)) {
                    handleBindingInputEvent(0, false);
                    handleBindingInputEvent(1, false);
                }

                if (chatCommand == null && chatAction == null && usesToggleSwitch()) {
                    selected = !selected;
                    inputControlsView.invalidate();
                }
                else {
                    inputControlsView.invalidate();
                }
                // The key or click of this button is done: a single-use Shift/Ctrl/Alt is let go
                if (currentPointerActivatedButtonBindings) StickyModifiers.consume();
                currentPointerActivatedButtonBindings = false;
            }
            else if (type == Type.RANGE_BUTTON || type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD) {
                releaseActiveDirectionalStates();

                if (type == Type.RANGE_BUTTON) {
                    scroller.handleTouchUp();
                }
                else if (type == Type.STICK) {
                    inputControlsView.invalidate();
                    fireStickCenterIfTap();
                    centerUndecided = false;
                    centerGeneration++;
                }

                if (currentPosition != null) currentPosition = null;
            }
            else if (type == Type.SHOOTER_MODE) {
                selected = !selected;
                inputControlsView.setShooterModeActive(selected);
                inputControlsView.invalidate();
            }
            else if (type == Type.SCROLL_WHEEL) {
                wheelTouchUp();
            }
            currentPointerId = -1;
            inputControlsView.invalidate();
            return true;
        }
        return false;
    }

    // ---- Middle of a stick ---------------------------------------------------------------------------------------

    /** True while the current touch on the stick can still be a tap on the middle (near enough, short enough). */
    private boolean centerStillTap() {
        float density = 1.0f;
        try {
            density = inputControlsView.getResources().getDisplayMetrics().density;
        }
        catch (RuntimeException e) {
            // Without display metrics the tap area is the plain pixel size.
        }
        long duration = currentTimeMillis() - centerDownTime;
        float radius = StickCenter.tapRadius(getStickRadius(), density, centerTapDp);
        return StickCenter.isTap(centerMaxOffset, duration, radius, centerTapMillis);
    }

    /**
     * A finger that rests inside the tap area sends no more move events; when the tap time is over the stick has to
     * start working at the place where the finger lies, so this looks again at that moment.
     */
    private void scheduleCenterDecision() {
        final int generation = centerGeneration;
        inputControlsView.postDelayed(() -> {
            if (generation != centerGeneration || !centerUndecided || currentPointerId == -1) return;
            handleTouchMove(currentPointerId, centerLastX, centerLastY);
        }, centerTapMillis + 1);
    }

    /** A short tap on the middle (no deflection, see {@link StickCenter}) presses the assignment of the middle once. */
    private void fireStickCenterIfTap() {
        if (!hasCenterAction()) return;
        if (!centerStillTap()) return;

        String command = centerItem.getChatCommand();
        if (command != null) {
            inputControlsView.runChatCommand(command);
        }
        else {
            final BindingCombo combo = centerItem.getBindingCombo();
            inputControlsView.handleInputEvent(combo, true);
            inputControlsView.postDelayed(() -> inputControlsView.handleInputEvent(combo, false), StickCenter.HOLD_MILLIS);
        }
        inputControlsView.invalidate();
    }

    /** Symbol, real icon or text of the middle assignment, drawn in the knob of the stick. */
    private void drawStickCenterCaption(Canvas canvas, float x, float y, float knobRadius, int color) {
        if (!centerHasContent()) return;
        RadialMenu.Slot item = centerItem;
        BindingCaption.Caption caption = BindingCaption.forSlot(item.getLabel(), item.getWowAction(), item.getChatCommand(), item.getBindingCombo(), 0);
        float thickness = knobRadius * 1.6f;
        if (drawAddonIconInSegment(canvas, item.getWowAction(), caption, x, y, thickness, color)) {
            drawCooldownInSegment(canvas, item.getWowAction(), caption, x, y, thickness);
            return;
        }
        Paint paint = inputControlsView.getPaint();
        if (caption.getSymbol() != null) {
            WowSymbols.draw(canvas, caption.getSymbol(), x, y, thickness * 0.35f, color);
        }
        else if (!caption.getText().isEmpty()) {
            String label = BindingCaption.fit(caption.getText());
            if (BindingCaption.containsEmoji(label)) {
                paint.setTextSize(Math.min(getTextSizeForWidth(paint, label, thickness * 1.3f), thickness * 0.6f));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(color);
                canvas.drawText(label, x, y - ((paint.descent() + paint.ascent()) * 0.5f), paint);
            }
            else {
                KeyLabel keyLabel = KeyGlyphs.forCaption(caption, item.getWowAction(), item.getChatCommand(), item.getBindingCombo());
                drawSlotLabel(canvas, paint, keyLabel, label, x, y, knobRadius * 2f, thickness * 1.3f, thickness * 0.6f, false,
                        color, TouchStyleDraw.isStyled(), false);
            }
        }
        drawCooldownInSegment(canvas, item.getWowAction(), caption, x, y, thickness);
    }

    // ---- Key ring around a stick -------------------------------------------------------------------------------

    private boolean ringTouchDown(int pointerId, float x, float y, boolean outer) {
        if (ringPointerId != -1) return false;
        Rect box = getBoundingBox();
        int segment = StickRing.segmentIndex(StickRing.angleOf(x - box.centerX(), y - box.centerY()), ringSegments, ringStartAngle);
        if (segment < 0) return false;
        RadialMenu.Slot item = outer ? getRingOuterItem(segment) : getRingItem(segment);
        if (item == null) return false;

        ringPointerId = pointerId;
        ringActiveSegment = segment;
        ringActiveOuter = outer;
        String command = item.getChatCommand();
        if (command != null) {
            // Chat command: typed once, no key is held.
            inputControlsView.runChatCommand(command);
        }
        else {
            BindingCombo combo = item.getBindingCombo();
            if (!combo.isEmpty()) {
                ringPressedCombo = combo;
                inputControlsView.handleInputEvent(combo, true);
            }
        }
        inputControlsView.invalidate();
        return true;
    }

    private void releaseRing() {
        if (ringPointerId == -1) return;
        BindingCombo combo = ringPressedCombo;
        ringPointerId = -1;
        ringActiveSegment = -1;
        ringActiveOuter = false;
        ringPressedCombo = null;
        if (combo != null) inputControlsView.handleInputEvent(combo, false);
        inputControlsView.invalidate();
    }

    // ---- Scroll wheel ------------------------------------------------------------------------------------------

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private float wheelAxisPosition(float x, float y) {
        return orientation == 0 ? y : x;
    }

    private void wheelTouchDown(float x, float y) {
        wheelInertiaGeneration++;
        releaseWheelModifier();
        wheelAccum = 0.0f;
        wheelVelocity = 0.0f;
        wheelScrolled = false;
        wheelStartX = x;
        wheelStartY = y;
        wheelLastX = x;
        wheelLastY = y;
        wheelLastPos = wheelAxisPosition(x, y);
        wheelDownTime = currentTimeMillis();
        wheelLastMoveTime = wheelDownTime;
        inputControlsView.invalidate();
    }

    private void wheelTouchMove(float x, float y) {
        float position = wheelAxisPosition(x, y);
        float delta = position - wheelLastPos;
        wheelLastPos = position;
        wheelLastX = x;
        wheelLastY = y;
        if (delta == 0.0f) return;

        boolean vertical = orientation == 0;
        float signed = ScrollWheelMath.signedDelta(delta, vertical, scrollReverse);
        long now = currentTimeMillis();
        wheelVelocity = ScrollWheelMath.blendVelocity(wheelVelocity, signed, now - wheelLastMoveTime);
        wheelLastMoveTime = now;
        wheelPhase += delta;

        wheelAccum += signed;
        float stepPixels = ScrollWheelMath.stepPixels(scrollStep, inputControlsView.getSnappingSize());
        int steps = ScrollWheelMath.wholeSteps(wheelAccum, stepPixels);
        if (steps != 0) {
            wheelAccum -= steps * stepPixels;
            wheelScrolled = true;
            fireScrollTicks(ScrollWheelMath.limitTicks(steps));
        }
        inputControlsView.invalidate();
    }

    private void wheelTouchUp() {
        long now = currentTimeMillis();
        if (!wheelScrolled) {
            float travel = (float)Math.hypot(wheelLastX - wheelStartX, wheelLastY - wheelStartY);
            if (ScrollWheelMath.isTap(travel, now - wheelDownTime, TouchpadView.MAX_TAP_TRAVEL_DISTANCE, TouchpadView.MAX_TAP_MILLISECONDS)) {
                // A tap without swiping: pulse of the tap binding (middle click by default).
                if (!getBindingComboAt(0).isEmpty()) {
                    handleBindingInputEvent(0, true);
                    handleBindingInputEvent(0, false);
                }
            }
            releaseWheelModifier();
            return;
        }

        if (scrollInertia && now - wheelLastMoveTime <= ScrollWheelMath.MAX_FLING_IDLE_MILLIS) {
            float stepPixels = ScrollWheelMath.stepPixels(scrollStep, inputControlsView.getSnappingSize());
            int ticks = ScrollWheelMath.inertiaTicks(wheelVelocity, stepPixels);
            if (ticks > 0) {
                int generation = ++wheelInertiaGeneration;
                scheduleWheelInertia(0, ticks, wheelVelocity > 0, generation);
                return;
            }
        }
        releaseWheelModifier();
    }

    private void wheelCancel() {
        wheelInertiaGeneration++;
        wheelScrolled = false;
        releaseWheelModifier();
    }

    private void scheduleWheelInertia(int index, int total, boolean up, int generation) {
        inputControlsView.postDelayed(() -> {
            if (generation != wheelInertiaGeneration) return;
            fireScrollTicks(up ? 1 : -1);
            float stepPixels = ScrollWheelMath.stepPixels(scrollStep, inputControlsView.getSnappingSize());
            wheelPhase += ScrollWheelMath.signedDelta(up ? stepPixels : -stepPixels, orientation == 0, scrollReverse);
            inputControlsView.invalidate();
            if (index + 1 < total) scheduleWheelInertia(index + 1, total, up, generation);
            else releaseWheelModifier();
        }, ScrollWheelMath.inertiaDelayMillis(index));
    }

    /** Sends |steps| wheel ticks (positive = up) and keeps the modifier binding held while the wheel is used. */
    private void fireScrollTicks(int steps) {
        if (steps == 0) return;
        if (!wheelModifierHeld && !getBindingComboAt(1).isEmpty()) {
            wheelModifierHeld = true;
            handleBindingInputEvent(1, true);
        }
        Binding tick = steps > 0 ? Binding.MOUSE_SCROLL_UP : Binding.MOUSE_SCROLL_DOWN;
        for (int i = 0, count = Math.abs(steps); i < count; i++) {
            inputControlsView.handleInputEvent(tick, true);
            inputControlsView.handleInputEvent(tick, false);
        }
    }

    private void releaseWheelModifier() {
        if (!wheelModifierHeld) return;
        wheelModifierHeld = false;
        handleBindingInputEvent(1, false);
    }

    public boolean cancelTouch() {
        boolean ringWasHeld = ringPointerId != -1;
        releaseRing();
        if (type == Type.SCROLL_WHEEL) wheelCancel();
        if (currentPointerId == -1) return ringWasHeld;

        if (radialMenuTouchActive || isRadialMenuButton()) {
            if (radialMenuTouchActive) releaseActiveDirectionalStates();
            radialMenuTouchActive = false;
            inputControlsView.handleRadialMenuTouchUp(currentPointerId, false);
            if (currentPosition != null) currentPosition = null;
        }
        else if (type == Type.BUTTON) {
            ModKey stickyKey = stickyModKey();
            if (stickyKey != null) {
                StickyModifiers.release(stickyKey, SystemClock.uptimeMillis(), true);
            }
            else if (currentPointerActivatedButtonBindings) {
                handleBindingInputEvent(0, false);
                handleBindingInputEvent(1, false);
            }
            currentPointerActivatedButtonBindings = false;
            touchTime = null;
        }
        else if (type == Type.RANGE_BUTTON || type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD) {
            releaseActiveDirectionalStates();
            if (type == Type.RANGE_BUTTON) scroller.cancelTouch();
            currentPosition = null;
            centerUndecided = false;
            centerGeneration++;
        }

        currentPointerId = -1;
        inputControlsView.invalidate();
        return true;
    }
}

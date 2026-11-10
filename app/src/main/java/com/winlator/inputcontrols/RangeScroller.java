package com.winlator.inputcontrols;

import android.graphics.Rect;

import com.winlator.widget.InputControlsView;
import com.winlator.widget.TouchpadView;

import java.util.Timer;
import java.util.TimerTask;

public class RangeScroller {
    private final InputControlsView inputControlsView;
    private final ControlElement element;
    private float scrollOffset;
    private float currentOffset;
    private float lastPosition;
    private long touchTime;
    // Template strips (A-Z, ...) press one key per cell; the free list ("Eigene") presses the key combination of its entry
    // or types its chat command once.
    private Binding binding = Binding.NONE;
    private BindingCombo customCombo;
    private String customChat;
    private boolean customChatDone;
    private int activeIndex = -1;
    private boolean isActionDown = false;
    private boolean scrolling = false;
    private Timer timer;
    private int touchGeneration;

    public RangeScroller(InputControlsView inputControlsView, ControlElement element) {
        this.inputControlsView = inputControlsView;
        this.element = element;
    }

    /** Number of entries the strip scrolls through (the free list has its own count, a template a fixed one). */
    private int count() {
        ControlElement.Range range = element.getRange();
        return range == ControlElement.Range.CUSTOM ? element.getRangeItemCount() : range.max;
    }

    public float getElementSize() {
        Rect boundingBox = element.getBoundingBox();
        return (float)Math.max(boundingBox.width(), boundingBox.height()) / element.getBindingCount();
    }

    public float getScrollSize() {
        return getElementSize() * count();
    }

    public float getScrollOffset() {
        return scrollOffset;
    }

    public byte[] getRangeIndex() {
        int count = count();
        if (count <= 0) return new byte[]{0, 0};
        byte from = (byte)Math.floor((scrollOffset / getElementSize()) % count);
        if (from < 0) from = (byte)(count + from);
        byte to = (byte)(from + element.getBindingCount() + 1);
        return new byte[]{from, to};
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    /**
     * Index of the entry under [position] (x of a horizontal strip, y of a vertical one) when the strip starts at [start] and is
     * shifted by [currentOffset] (negative = pulled to the left/top), cells of [cellSize], [count] entries in a ring. -1 for an
     * empty strip. This is the same cell the drawing shows there.
     */
    public static int indexAt(float position, float start, float currentOffset, float cellSize, int count) {
        if (count <= 0 || cellSize <= 0.0f) return -1;
        float offset = position - start - currentOffset;
        int index = (int)Math.floor((offset / cellSize) % count);
        if (index < 0) index = count + index;
        return index;
    }

    private int getIndexByPosition(float x, float y) {
        Rect boundingBox = element.getBoundingBox();
        boolean horizontal = element.getOrientation() == 0;
        return indexAt(horizontal ? x : y, horizontal ? boundingBox.left : boundingBox.top, currentOffset, getElementSize(), count());
    }

    private Binding getBindingByIndex(int index) {
        return element.getRange().bindingAt(index);
    }

    /** Takes over what the cell [index] presses: its key (template) or the assignment of its entry (free list). */
    private void pickAction(int index) {
        binding = Binding.NONE;
        customCombo = null;
        customChat = null;
        customChatDone = false;
        if (index < 0) return;
        if (element.getRange() == ControlElement.Range.CUSTOM) {
            RadialMenu.Slot item = element.getRangeItem(index);
            if (item == null) return;
            String command = item.getChatCommand();
            if (command != null) customChat = command;
            else {
                BindingCombo combo = item.getBindingCombo();
                if (combo != null && !combo.isEmpty()) customCombo = combo;
            }
        }
        else binding = getBindingByIndex(index);
    }

    /** A finger that rests on a cell: the key goes down, a chat command is typed (once). */
    private void pressAction(Binding scheduledBinding, BindingCombo scheduledCombo, String scheduledChat) {
        if (scheduledChat != null) {
            if (!customChatDone) {
                customChatDone = true;
                inputControlsView.runChatCommand(scheduledChat);
            }
        }
        else if (scheduledCombo != null) inputControlsView.handleInputEvent(scheduledCombo, true);
        else if (scheduledBinding != Binding.NONE) inputControlsView.handleInputEvent(scheduledBinding, true);
    }

    private void releaseAction(Binding releasedBinding, BindingCombo releasedCombo) {
        if (releasedCombo != null) inputControlsView.handleInputEvent(releasedCombo, false);
        else if (customChat == null && releasedBinding != Binding.NONE) inputControlsView.handleInputEvent(releasedBinding, false);
    }

    /** False when the whole free list is visible: there is nothing to scroll to then. */
    private boolean canScroll() {
        return element.getRange() != ControlElement.Range.CUSTOM || count() > element.getBindingCount();
    }

    private boolean isTap() {
        return (currentTimeMillis() - touchTime) < TouchpadView.MAX_TAP_MILLISECONDS;
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private void destroyTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    protected void scheduleLongPress(Runnable callback) {
        timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                callback.run();
            }
        }, TouchpadView.MAX_TAP_MILLISECONDS);
    }

    public void handleTouchDown(float x, float y) {
        final int generation = ++touchGeneration;
        destroyTimer();

        scrolling = false;
        isActionDown = true;
        activeIndex = getIndexByPosition(x, y);
        pickAction(activeIndex);
        touchTime = currentTimeMillis();
        lastPosition = element.getOrientation() == 0 ? x : y;
        element.setBinding(Binding.NONE);
        inputControlsView.invalidate();

        final Binding scheduledBinding = binding;
        final BindingCombo scheduledCombo = customCombo;
        final String scheduledChat = customChat;
        scheduleLongPress(() -> inputControlsView.post(() -> {
            if (generation != touchGeneration || !isActionDown || scrolling) return;
            pressAction(scheduledBinding, scheduledCombo, scheduledChat);
        }));
    }

    public void handleTouchMove(float x, float y) {
        if (isActionDown) {
            if (element.isScrollLocked() || !canScroll()) return;

            float position = element.getOrientation() == 0 ? x : y;
            float deltaPosition = position - lastPosition;

            if (Math.abs(deltaPosition) >= TouchpadView.MAX_TAP_TRAVEL_DISTANCE) {
                scrolling = true;
                activeIndex = -1;
                destroyTimer();
                inputControlsView.invalidate();
            }

            if (scrolling) {
                currentOffset += deltaPosition;

                float scrollSize = getScrollSize();
                scrollOffset = -currentOffset % scrollSize;
                if (scrollOffset < 0) scrollOffset = scrollSize + scrollOffset;

                lastPosition = position;
                inputControlsView.invalidate();
            }
        }
    }

    public void handleTouchUp() {
        touchGeneration++;
        if (isActionDown) {
            destroyTimer();
            if (isTap() && !scrolling) {
                final Binding finalBinding = binding;
                final BindingCombo finalCombo = customCombo;
                pressAction(finalBinding, finalCombo, customChat);
                if (customChat == null && (finalCombo != null || finalBinding != Binding.NONE)) {
                    inputControlsView.postDelayed(() -> {
                        if (finalCombo != null) inputControlsView.handleInputEvent(finalCombo, false);
                        else inputControlsView.handleInputEvent(finalBinding, false);
                    }, 30);
                }
            }
            else releaseAction(binding, customCombo);
        }
        isActionDown = false;
        activeIndex = -1;
        inputControlsView.invalidate();
    }

    public void cancelTouch() {
        touchGeneration++;
        if (isActionDown) {
            destroyTimer();
            releaseAction(binding, customCombo);
        }
        isActionDown = false;
        activeIndex = -1;
        scrolling = false;
        inputControlsView.invalidate();
    }
}

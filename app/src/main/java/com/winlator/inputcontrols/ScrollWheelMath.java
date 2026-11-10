package com.winlator.inputcontrols;

/**
 * Pure arithmetic of the on-screen scroll wheel (swipe up/down emulates a mouse wheel): finger distance to wheel
 * ticks, direction, tap detection and coasting. No Android classes, unit tested.
 */
public final class ScrollWheelMath {
    /** Default distance of one wheel tick in grid units (multiplied by the snapping size). */
    public static final float DEFAULT_STEP = 0.6f;
    public static final float MIN_STEP = 0.1f;
    public static final float MAX_STEP = 4.0f;
    /** Scroll speed as shown to the player: a factor of the default (1 = default, 2 = twice the ticks per finger movement). */
    public static final float MIN_SPEED = 0.25f;
    public static final float MAX_SPEED = 6.0f;
    /** More ticks than this from one move event are dropped (a very fast swipe must not flood the game). */
    public static final int MAX_TICKS_PER_EVENT = 12;
    public static final int MAX_INERTIA_TICKS = 12;
    /** Below this speed (pixels per millisecond) a lifted finger does not coast. */
    public static final float MIN_FLING_VELOCITY = 0.4f;
    /** A finger that rested longer than this before lifting does not coast. */
    public static final long MAX_FLING_IDLE_MILLIS = 80;

    private ScrollWheelMath() {}

    public static float clampStep(float stepUnits) {
        if (Float.isNaN(stepUnits)) return DEFAULT_STEP;
        return Math.max(MIN_STEP, Math.min(MAX_STEP, stepUnits));
    }

    /** Speed factor of a tick distance: the default is 1, a smaller distance is faster. */
    public static float speedOf(float stepUnits) {
        return DEFAULT_STEP / clampStep(stepUnits);
    }

    /** Tick distance (grid units) for a speed factor; the factor is limited to MIN_SPEED..MAX_SPEED, bad input gives the default. */
    public static float stepForSpeed(float speed) {
        if (Float.isNaN(speed) || speed <= 0.0f) return DEFAULT_STEP;
        float limited = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
        return clampStep(Math.round(DEFAULT_STEP / limited * 1000.0f) / 1000.0f);
    }

    /** Position 0..1 of a speed on a logarithmic slider (equal distances for equal ratios, so slow and fast get room). */
    public static float positionForSpeed(float speed) {
        float limited = Float.isNaN(speed) ? 1.0f : Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
        return (float)(Math.log(limited / MIN_SPEED) / Math.log(MAX_SPEED / MIN_SPEED));
    }

    /** Inverse of {@link #positionForSpeed}, rounded to steps the player can read (0.05 below 2x, 0.1 above). */
    public static float speedForPosition(float position) {
        float clamped = Float.isNaN(position) ? 0.0f : Math.max(0.0f, Math.min(1.0f, position));
        float speed = (float)(MIN_SPEED * Math.pow(MAX_SPEED / MIN_SPEED, clamped));
        float rounded = speed < 2.0f ? Math.round(speed * 20.0f) / 20.0f : Math.round(speed * 10.0f) / 10.0f;
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, rounded));
    }

    /** Distance of one tick in pixels. */
    public static float stepPixels(float stepUnits, int snappingSize) {
        return Math.max(1.0f, clampStep(stepUnits) * snappingSize);
    }

    /**
     * Finger movement along the swipe axis as a signed scroll amount, positive = scroll up. Vertical wheel: swiping up
     * (y decreases) scrolls up; horizontal wheel: swiping right scrolls up. [reverse] flips the direction.
     */
    public static float signedDelta(float delta, boolean vertical, boolean reverse) {
        float value = vertical ? -delta : delta;
        return reverse ? -value : value;
    }

    /** Whole ticks in [accumulated] (truncated towards zero), positive = up. */
    public static int wholeSteps(float accumulated, float stepPixels) {
        if (stepPixels <= 0.0f) return 0;
        return (int)(accumulated / stepPixels);
    }

    /** Ticks sent for one move event: the sign is kept, the amount is limited to MAX_TICKS_PER_EVENT. */
    public static int limitTicks(int steps) {
        if (steps > MAX_TICKS_PER_EVENT) return MAX_TICKS_PER_EVENT;
        if (steps < -MAX_TICKS_PER_EVENT) return -MAX_TICKS_PER_EVENT;
        return steps;
    }

    /** True when a touch counts as a tap: short and without noticeable movement. */
    public static boolean isTap(float travelPixels, long durationMillis, float maxTravelPixels, long maxMillis) {
        return durationMillis < maxMillis && travelPixels < maxTravelPixels;
    }

    /** Smoothed signed velocity (pixels per millisecond) from the previous value and the latest move. */
    public static float blendVelocity(float previous, float signedDelta, long elapsedMillis) {
        if (elapsedMillis <= 0) return previous;
        float instant = signedDelta / elapsedMillis;
        return previous * 0.4f + instant * 0.6f;
    }

    /** Number of coasting ticks after the finger left at [velocityPixelsPerMs] (signed); 0 = no coasting. */
    public static int inertiaTicks(float velocityPixelsPerMs, float stepPixels) {
        float speed = Math.abs(velocityPixelsPerMs);
        if (speed < MIN_FLING_VELOCITY || stepPixels <= 0.0f) return 0;
        int ticks = (int)(speed * 80.0f / stepPixels);
        return Math.max(1, Math.min(MAX_INERTIA_TICKS, ticks));
    }

    /** Delay before coasting tick [index] (0-based); grows, so the wheel slows down. */
    public static long inertiaDelayMillis(int index) {
        return 35L + 12L * Math.max(0, index);
    }
}

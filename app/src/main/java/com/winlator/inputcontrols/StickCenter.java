package com.winlator.inputcontrols;

/**
 * "Press the middle of a stick": a short tap on the stick without any deflection triggers an own assignment (for example
 * jump on the right stick). Pure helper for the tap test; the assignment itself lives in {@link ControlElement}.
 *
 * A stick with a middle assignment does not know at touch down whether the finger taps or drags. As long as the touch is
 * still a possible tap (not farther than the tap radius from the middle, shorter than the tap time) the stick outputs
 * nothing, so small finger movements never reach the camera; once it is clear that it is a drag, the stick works as usual
 * from the current point of the finger.
 */
public final class StickCenter {
    /** Default time limit of a tap (ms). */
    public static final long DEFAULT_TAP_MILLIS = 350;
    public static final long MIN_TAP_MILLIS = 150;
    public static final long MAX_TAP_MILLIS = 600;
    /** Default largest distance from the middle (dp) a finger may have during a tap. */
    public static final float DEFAULT_TAP_DP = 24.0f;
    public static final float MIN_TAP_DP = 8.0f;
    public static final float MAX_TAP_DP = 48.0f;
    /** The tap area never gets bigger than this share of the stick's radius. */
    private static final float MAX_RADIUS_SHARE = 0.9f;
    /** How long the key of the middle assignment is held (a press needs a moment to be seen by the game). */
    public static final long HOLD_MILLIS = 60;

    private StickCenter() {
    }

    public static float clampDp(float dp) {
        if (Float.isNaN(dp)) return DEFAULT_TAP_DP;
        return Math.max(MIN_TAP_DP, Math.min(MAX_TAP_DP, dp));
    }

    public static long clampMillis(long millis) {
        return Math.max(MIN_TAP_MILLIS, Math.min(MAX_TAP_MILLIS, millis));
    }

    /** Largest distance from the middle, in pixels, that still counts as "in the middle" ([tapDp] at [density]). */
    public static float tapRadius(float stickRadiusPx, float density, float tapDp) {
        float d = density > 0.0f ? density : 1.0f;
        return Math.min(clampDp(tapDp) * d, MAX_RADIUS_SHARE * stickRadiusPx);
    }

    /**
     * True while the touch can still be a tap: the finger was down for less than [tapMillis] and never farther than
     * [tapRadiusPx] from the middle (the largest distance during the whole touch, the start included).
     */
    public static boolean isTap(float maxOffsetPx, long downMillis, float tapRadiusPx, long tapMillis) {
        return downMillis >= 0 && downMillis < tapMillis && maxOffsetPx <= tapRadiusPx;
    }
}

package com.winlator.inputcontrols;

/**
 * Pure geometry of the key ring around an on-screen stick and of ring-shaped arc buttons. No Android classes, so it is
 * unit tested without a device.
 *
 * Angles are in degrees, 0 = right (3 o'clock) and growing clockwise on the screen (y points down), the same
 * convention as {@code Path.arcTo}. Segment 0 is centred on the start angle (default -90 = straight up), the following
 * segments continue clockwise.
 */
public final class StickRing {
    public static final int ZONE_NONE = 0;
    /** Inside the stick circle or in the gap between stick and ring: the stick finger lives here. */
    public static final int ZONE_STICK = 1;
    /** In the ring of key segments. */
    public static final int ZONE_RING = 2;
    /** In the optional second, outer band of key segments (same angles as the inner ring). */
    public static final int ZONE_OUTER = 3;
    /** Gap between the inner ring and the outer band in grid units (multiplied by the snapping size and the scale). */
    public static final float OUTER_GAP = 0.5f;

    public static final int MAX_SEGMENTS = 8;
    public static final float DEFAULT_START_ANGLE = -90.0f;
    /** Ring width and gap to the stick in grid units (multiplied by the snapping size and the element scale). */
    public static final float DEFAULT_WIDTH = 2.5f;
    public static final float DEFAULT_GAP = 0.75f;
    public static final float MIN_WIDTH = 1.0f;
    public static final float MAX_WIDTH = 5.0f;
    public static final float MIN_GAP = 0.0f;
    public static final float MAX_GAP = 3.0f;
    /** Visible separation between two neighbouring segments, in degrees. */
    public static final float SEGMENT_SEPARATION_DEGREES = 4.0f;

    private StickRing() {}

    /** 0 = no ring, otherwise the next allowed count 4, 6 or 8. */
    public static int normalizeSegments(int segments) {
        if (segments <= 0) return 0;
        if (segments <= 4) return 4;
        if (segments <= 6) return 6;
        return 8;
    }

    public static float clampWidth(float width) {
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
    }

    public static float clampGap(float gap) {
        return Math.max(MIN_GAP, Math.min(MAX_GAP, gap));
    }

    /** Angle normalised to [0, 360). */
    public static float normalizeAngle(float degrees) {
        float angle = degrees % 360.0f;
        if (angle < 0) angle += 360.0f;
        return angle;
    }

    /** Smallest signed difference a - b in (-180, 180]. */
    public static float angleDifference(float a, float b) {
        float diff = normalizeAngle(a - b);
        if (diff > 180.0f) diff -= 360.0f;
        return diff;
    }

    /** Angle of the vector (dx, dy) in screen coordinates (y down), i.e. clockwise from 3 o'clock. */
    public static float angleOf(float dx, float dy) {
        return (float)Math.toDegrees(Math.atan2(dy, dx));
    }

    /** Centre angle of segment [index] of [segments]. */
    public static float segmentCenterAngle(int index, int segments, float startAngleDegrees) {
        if (segments <= 0) return startAngleDegrees;
        return startAngleDegrees + index * (360.0f / segments);
    }

    /**
     * Index of the segment a touch at [angleDegrees] belongs to, or -1 without segments. Every angle belongs to exactly
     * one segment (the small visual separation between segments does not leave dead angles, a tap on it takes the nearer
     * segment).
     */
    public static int segmentIndex(float angleDegrees, int segments, float startAngleDegrees) {
        if (segments <= 0) return -1;
        float step = 360.0f / segments;
        float relative = normalizeAngle(angleDegrees - startAngleDegrees + step * 0.5f);
        int index = (int)(relative / step);
        return Math.min(index, segments - 1);
    }

    /**
     * Which part of a stick with ring a touch at the offset (dx, dy) from the stick centre hits. [gap] and [width] are in
     * pixels. The gap counts to the stick on purpose: a thumb that rests slightly off centre must never fire a key.
     */
    public static int zoneAt(float dx, float dy, float stickRadius, float gap, float width) {
        double distance = Math.sqrt((double)dx * dx + (double)dy * dy);
        if (distance <= stickRadius + gap) return ZONE_STICK;
        if (distance <= stickRadius + gap + width) return ZONE_RING;
        return ZONE_NONE;
    }

    /**
     * Like {@link #zoneAt(float, float, float, float, float)} for a stick whose ring has an outer second band of width
     * [outerWidth] (pixels) lying [outerGap] (pixels) outside the first one. The gap between the two bands counts to
     * the inner ring, so a thumb that slips between them still taps the segment it came from. [outerWidth] of 0 or less
     * means there is no outer band: the result is the one of the plain function.
     */
    public static int zoneAt(float dx, float dy, float stickRadius, float gap, float width, float outerGap, float outerWidth) {
        int zone = zoneAt(dx, dy, stickRadius, gap, width);
        if (zone != ZONE_NONE || outerWidth <= 0.0f) return zone;
        double distance = Math.sqrt((double)dx * dx + (double)dy * dy);
        double innerRingEnd = (double)stickRadius + gap + width;
        if (distance <= innerRingEnd + Math.max(0.0f, outerGap)) return ZONE_RING;
        if (distance <= innerRingEnd + Math.max(0.0f, outerGap) + outerWidth) return ZONE_OUTER;
        return ZONE_NONE;
    }

    /** Hit test of an annular sector (ring segment or ARC button): radius in [inner, outer] and angle within the sweep. */
    public static boolean inSector(float dx, float dy, float innerRadius, float outerRadius,
                                   float centerAngleDegrees, float sweepDegrees) {
        double distance = Math.sqrt((double)dx * dx + (double)dy * dy);
        if (distance < innerRadius || distance > outerRadius) return false;
        float angle = angleOf(dx, dy);
        return Math.abs(angleDifference(angle, centerAngleDegrees)) <= sweepDegrees * 0.5f;
    }
}

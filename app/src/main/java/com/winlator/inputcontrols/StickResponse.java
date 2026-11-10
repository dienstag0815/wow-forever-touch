package com.winlator.inputcontrols;

/**
 * Response curve of the on-screen sticks: dead zone, normalisation, power curve and sensitivity.
 * Pure functions, unit tested. The old behaviour was a fixed factor of 3 (full deflection at a third of the travel).
 */
public final class StickResponse {
    /** Power applied to the normalised deflection: above 1 gives fine control near the centre. */
    public static final float CURVE = 1.5f;

    private StickResponse() {
    }

    /**
     * @param raw         stick deflection, -1..1
     * @param sensitivity gain after the curve; 1 reaches full output at full deflection, above 1 earlier, below 1 never
     * @param deadZone    deflection (0..1) that is ignored around the centre
     * @param curve       exponent of the response curve (1 = linear)
     * @return output axis value, -1..1
     */
    public static float stickResponse(float raw, float sensitivity, float deadZone, float curve) {
        float magnitude = Math.abs(raw);
        if (Float.isNaN(raw) || magnitude <= deadZone || deadZone >= 1f) return 0f;
        float normalized = Math.min(1f, (magnitude - deadZone) / (1f - deadZone));
        float shaped = (float) Math.pow(normalized, curve);
        float out = Math.min(1f, shaped * sensitivity);
        return raw < 0f ? -out : out;
    }
}

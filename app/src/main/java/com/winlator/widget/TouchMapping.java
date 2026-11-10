package com.winlator.widget;

import com.winlator.math.XForm;
import com.winlator.renderer.ViewTransformation;

/**
 * Touch position in the view -> position in the game picture (X screen). Pure math, kept apart from TouchpadView so it can be
 * tested without Android. The picture keeps the size chosen at start; when the view size changes (fold/unfold) only this
 * mapping is recomputed: the picture is scaled to fit and centered (letterbox).
 */
public final class TouchMapping {
    private TouchMapping() {}

    /**
     * Fills {@code xform} so that {@link XForm#transformPoint} maps view pixels to picture pixels.
     *
     * @param outerWidth  view width in pixels
     * @param outerHeight view height in pixels
     * @param innerWidth  picture (screen) width
     * @param innerHeight picture (screen) height
     * @param fullscreen  picture without letterbox offset (scale only)
     */
    public static void update(float[] xform, int outerWidth, int outerHeight, int innerWidth, int innerHeight, boolean fullscreen) {
        // A view that is not laid out yet (0 px) would give an infinite scale: keep the previous mapping.
        if (outerWidth <= 0 || outerHeight <= 0 || innerWidth <= 0 || innerHeight <= 0) return;
        ViewTransformation viewTransformation = new ViewTransformation();
        viewTransformation.update(outerWidth, outerHeight, innerWidth, innerHeight);
        float invAspect = 1.0f / viewTransformation.aspect;
        if (!fullscreen) {
            XForm.makeTranslation(xform, -viewTransformation.viewOffsetX, -viewTransformation.viewOffsetY);
            XForm.scale(xform, invAspect, invAspect);
        } else {
            XForm.makeScale(xform, invAspect, invAspect);
        }
    }
}

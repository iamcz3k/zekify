package com.v3x.zekify;

import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** Material Motion timing/easing shared by native Android screens. */
public final class MaterialMotion {
    private MaterialMotion() {}
    public static final long FADE_THROUGH_MS = 260L;
    public static final long SHARED_AXIS_MS = 300L;
    public static final long CONTAINER_TRANSFORM_MS = 340L;
    public static Interpolator standard() { return new PathInterpolator(.2f, 0f, 0f, 1f); }
    public static void fade(View view, boolean reduceMotion) {
        if (view == null) return;
        view.animate().cancel();
        if (reduceMotion) { view.setAlpha(1f); view.setScaleX(1f); view.setScaleY(1f); return; }
        view.setAlpha(0f); view.setScaleX(.96f); view.setScaleY(.96f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(FADE_THROUGH_MS).setInterpolator(standard()).start();
    }
}

package com.screentint.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.view.View;

/**
 * The tint layer. Draws its colour in high precision (16-bit floating point, together with a
 * high-precision window), so changes of a tenth of a percent show instead of being rounded to
 * the usual 256 steps.
 */
@SuppressLint("ViewConstructor")
final class TintView extends View {
    private static final ColorSpace SPACE = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB);
    private long color;

    TintView(Context context) {
        super(context);
    }

    /** {alpha, red, green, blue}, each 0..1. */
    void setTint(float[] argb) {
        color = Color.pack(argb[1], argb[2], argb[3], argb[0], SPACE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(color);
    }
}

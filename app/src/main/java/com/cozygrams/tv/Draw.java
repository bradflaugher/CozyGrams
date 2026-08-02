package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Small drawing primitives shared by every scene.
 *
 * <p>One {@link Paint} and one {@link RectF} are reused for the whole frame so that
 * drawing never allocates. Each helper leaves the paint in a known state (fill style,
 * butt caps) so callers can chain without surprises.
 */
public final class Draw {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF rect = new RectF();

    /** The shared paint, for the rare caller that needs an effect we do not wrap. */
    public Paint paint() {
        return paint;
    }

    /** The shared scratch rectangle. Contents are only valid until the next helper call. */
    public RectF rect() {
        return rect;
    }

    // ---- Text ----------------------------------------------------------------------

    public void text(Canvas canvas, String value, float x, float y, float size, int color,
                     Paint.Align align, boolean strong) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(strong ? Theme.bold() : Theme.regular());
        canvas.drawText(value, x, y, paint);
    }

    /**
     * Draws text with a soft dark drop shadow behind it. Illustrated backdrops vary in
     * brightness, and the shadow keeps light copy readable wherever it lands.
     */
    public void shadowedText(Canvas canvas, String value, float x, float y, float size,
                             int color, Paint.Align align, boolean strong) {
        float offset = Math.max(1.5f, size * .06f);
        text(canvas, value, x + offset, y + offset, size,
                Color.argb(120, 8, 5, 16), align, strong);
        text(canvas, value, x, y, size, color, align, strong);
    }

    public float measure(String value, float size, boolean strong) {
        paint.setTextSize(size);
        paint.setTypeface(strong ? Theme.bold() : Theme.regular());
        return paint.measureText(value);
    }

    /** Distance from a text baseline to the visual centre of upper-case glyphs. */
    public float capCentreOffset(float size) {
        return size * .35f;
    }

    // ---- Surfaces ------------------------------------------------------------------

    /** A soft dark shadow beneath a rounded surface. */
    public void shadow(Canvas canvas, float left, float top, float right, float bottom,
                       float radius, float depth) {
        paint.setStyle(Paint.Style.FILL);
        for (int layer = 3; layer >= 1; layer--) {
            float spread = depth * layer * .55f;
            paint.setColor(Color.argb(26, 10, 6, 18));
            rect.set(left - spread * .3f, top + spread * .35f,
                    right + spread * .3f, bottom + spread);
            canvas.drawRoundRect(rect, radius + spread, radius + spread, paint);
        }
    }

    /** The frosted plum panel used for menus, the HUD and the win card. */
    public void panel(Canvas canvas, float left, float top, float right, float bottom,
                      int alpha) {
        float radius = Theme.scale(Theme.PANEL_RADIUS);
        shadow(canvas, left, top, right, bottom, radius, Theme.scale(10));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(Theme.PANEL, alpha));
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // A hairline highlight along the top edge reads as lamplight catching the panel.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, Theme.scale(2)));
        paint.setColor(Color.argb(Math.min(alpha, 150), 255, 245, 227));
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    public void roundRect(Canvas canvas, float left, float top, float right, float bottom,
                          float radius, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    public void roundRectStroke(Canvas canvas, float left, float top, float right,
                                float bottom, float radius, float width, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    public void circle(Canvas canvas, float cx, float cy, float radius, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    public void circleStroke(Canvas canvas, float cx, float cy, float radius, float width,
                             int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    /**
     * A filled heart, centred on {@code cx, cy} and about {@code size} across.
     *
     * <p>Four cubics, mirrored about the vertical axis: two for the shoulders, two for
     * the flanks that meet at the point. The old version was two circles and a triangle,
     * which at particle sizes read as a lumpy blob with no notch and a blunt tail — this
     * one keeps a notch {@code .32} deep relative to the shoulders and closes at the tip
     * with an included angle near 88°, so the silhouette survives being 12 px wide.
     *
     * <p>One {@link android.graphics.Path} is reused for the life of this {@code Draw},
     * so a screenful of heart particles still allocates nothing.
     */
    public void heart(Canvas canvas, float cx, float cy, float size, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(heartPath(cx, cy, size), paint);
    }

    /**
     * The heart outline in screen coordinates. The shape spans y from {@code -.745} to
     * {@code 1.0} in its own units, so it is shifted to hang its bounding box on
     * {@code cy} — callers position hearts by their centre.
     */
    private android.graphics.Path heartPath(float cx, float cy, float size) {
        float k = size * .58f;
        float y = cy - .1275f * k;
        android.graphics.Path path = heartPath;
        path.reset();
        path.moveTo(cx, y - .42f * k);
        path.cubicTo(cx + .28f * k, y - .92f * k, cx + .92f * k, y - .82f * k,
                cx + 1.00f * k, y - .28f * k);
        path.cubicTo(cx + 1.06f * k, y + .16f * k, cx + .46f * k, y + .52f * k,
                cx, y + 1.00f * k);
        path.cubicTo(cx - .46f * k, y + .52f * k, cx - 1.06f * k, y + .16f * k,
                cx - 1.00f * k, y - .28f * k);
        path.cubicTo(cx - .92f * k, y - .82f * k, cx - .28f * k, y - .92f * k,
                cx, y - .42f * k);
        path.close();
        return path;
    }

    private final android.graphics.Path heartPath = new android.graphics.Path();

    // ---- Colour maths --------------------------------------------------------------

    /**
     * Mixes two colours and returns an opaque result.
     *
     * <p>The channel maths is done with shifts rather than through
     * {@code android.graphics.Color}, so it produces real numbers in a plain JVM unit
     * test as well as on a device — the test source set runs with
     * {@code returnDefaultValues}, where every {@code Color} static answers zero.
     */
    public static int blend(int first, int second, float amount) {
        float safe = clamp01(amount);
        int red = (int) (((first >> 16) & 0xff) * (1 - safe) + ((second >> 16) & 0xff) * safe);
        int green = (int) (((first >> 8) & 0xff) * (1 - safe) + ((second >> 8) & 0xff) * safe);
        int blue = (int) ((first & 0xff) * (1 - safe) + (second & 0xff) * safe);
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00ffffff);
    }

    public static int withAlpha(int color, float alpha) {
        return withAlpha(color, (int) (clamp01(alpha) * 255));
    }

    // ---- Easing --------------------------------------------------------------------

    public static float clamp01(float value) {
        return value < 0 ? 0 : (value > 1 ? 1 : value);
    }

    /** Decelerating cubic — the default for anything entering the screen. */
    public static float easeOut(float value) {
        float inverse = 1 - clamp01(value);
        return 1 - inverse * inverse * inverse;
    }

    /** Symmetric ease, used for pulses that grow and settle. */
    public static float easeInOut(float value) {
        float safe = clamp01(value);
        return safe < .5f ? 4 * safe * safe * safe : 1 - (float) Math.pow(-2 * safe + 2, 3) / 2;
    }

    /**
     * Overshooting ease that settles back — the "pop" used when a square is filled.
     * Peaks around 1.12 near t=0.4 before resolving to exactly 1.
     */
    public static float easeOutBack(float value) {
        float safe = clamp01(value) - 1;
        float tension = 1.70158f;
        return 1 + (tension + 1) * safe * safe * safe + tension * safe * safe;
    }

    /** A damped spring wobble that starts at 0, overshoots, and settles at 1. */
    public static float springy(float value) {
        float safe = clamp01(value);
        if (safe >= 1) return 1;
        return 1 - (float) (Math.pow(2, -9 * safe) * Math.cos(safe * 14));
    }

    public static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}

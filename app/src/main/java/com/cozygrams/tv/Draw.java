package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Small drawing primitives shared by every scene.
 *
 * <p>One {@link Paint}, one {@link RectF} and one {@link Path} are reused for the whole
 * frame so that drawing never allocates. Each helper leaves the paint in a known state
 * (fill style, butt caps) so callers can chain without surprises.
 *
 * <h2>Stacked alpha, and the rule that keeps it smooth</h2>
 *
 * <p>There is no shader here and there never will be: the desktop screenshot harness runs
 * on Java2D-backed stubs with no gradients and no clipping, and keeping the renderer inside
 * that budget is what lets every frame of this game be inspected without a device. Every
 * soft edge is therefore a stack of translucent shapes — a halo, a panel's lamplight, a
 * shadow, the backdrop's pool.
 *
 * <p>A stack like that bands the moment one layer carries too much alpha, because each
 * layer's outline <em>is</em> a step. {@link Theme#GLOW_STEP_ALPHA_MAX} is the ceiling
 * that stops it, and {@link #glowRings(float, float)} / {@link #glowRingAlpha(int, int,
 * float)} are the ladder that spends a requested peak across enough layers to respect it.
 * Both are pure functions so {@code ThemeTest} can prove the property rather than a
 * screenshot suggesting it.
 */
public final class Draw {

    /**
     * Rings closer together than this land on the same pixel, and two rings on one pixel
     * is a step of twice {@link Theme#GLOW_STEP_ALPHA_MAX} however careful the ladder is.
     * It also sets the honest ceiling on a halo's brightness: two levels per pixel, so a
     * glow can never be brighter than twice its own reach.
     */
    private static final float GLOW_MIN_PITCH = 2f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    /**
     * Scratch for {@link #tracked}, which has to place glyphs one at a time. Sized for the
     * longest string anyone would letterspace — a wordmark, not a sentence.
     */
    private final char[] glyphs = new char[64];

    /** The shared paint, for the rare caller that needs an effect we do not wrap. */
    public Paint paint() {
        return paint;
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
                withAlpha(Theme.SHADOW_INK, 120), align, strong);
        text(canvas, value, x, y, size, color, align, strong);
    }

    /**
     * The same string with {@code tracking} of extra space between glyphs, centred on
     * {@code centreX}. For the wordmark, which is the first thing anyone sees: no typeface
     * ships with the app and none is going to, so letterspacing is the whole of what makes
     * COZYGRAMS read as a wordmark rather than as the system font. {@code HomeScene}
     * draws it through here.
     *
     * <p>Falls back to ordinary centred text for anything longer than the scratch buffer:
     * letterspacing a sentence is a mistake anyway, and silently drawing nothing would be
     * a worse one.
     */
    public void tracked(Canvas canvas, String value, float centreX, float baseline,
                        float size, int color, float tracking, boolean strong) {
        int count = value.length();
        if (count == 0) {
            return;
        }
        if (count > glyphs.length) {
            text(canvas, value, centreX, baseline, size, color, Paint.Align.CENTER, strong);
            return;
        }
        float extra = size * tracking;
        float total = measure(value, size, strong) + extra * (count - 1);

        value.getChars(0, count, glyphs, 0);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.LEFT);
        float x = centreX - total / 2;
        for (int i = 0; i < count; i++) {
            canvas.drawText(glyphs, i, 1, x, baseline, paint);
            x += paint.measureText(value, i, i + 1) + extra;
        }
    }

    public float measure(String value, float size, boolean strong) {
        paint.setTextSize(size);
        paint.setTypeface(strong ? Theme.bold() : Theme.regular());
        return paint.measureText(value);
    }

    /**
     * The largest size at or below {@code size} at which {@code value} fits in
     * {@code maxWidth}, never going below {@code floor}.
     *
     * <p>Three scenes carried a private copy of this loop and two more needed one and did
     * not have it, which is why the title screen's greeting overflowed its panel and a long
     * message hung 260 px off each end of its own pill. Shrinking by 6% a step converges in
     * a handful of iterations and never overshoots enough to be seen next to the size beside
     * it.
     *
     * <p>{@code HomeScene.drawRow} and {@code SettingsScene.fit} come through here now. The
     * two that still do not are deliberate: {@code HudScene.fit} and {@code WinScene.fit}
     * solve for the size in a single {@code preferred * room / measured} rather than
     * stepping down to it, because {@code HudScene.wrapToFit} has to re-break its lines at
     * whatever size comes back and a stepped answer would move the breaks twice.
     *
     * <p>Under {@code returnDefaultValues} every measurement is zero, so in a unit test
     * this returns {@code size} unchanged — which is correct, and is why the guard has to
     * be checked by rendering rather than by asserting.
     */
    public float fit(String value, float size, float maxWidth, boolean strong, float floor) {
        float smallest = Math.max(1f, floor);
        if (maxWidth <= 0) {
            return smallest;
        }
        float chosen = size;
        while (chosen > smallest && measure(value, chosen, strong) > maxWidth) {
            chosen *= .94f;
        }
        return Math.max(smallest, chosen);
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
            paint.setColor(withAlpha(Theme.SHADOW_INK, 26));
            rect.set(left - spread * .3f, top + spread * .35f,
                    right + spread * .3f, bottom + spread);
            canvas.drawRoundRect(rect, radius + spread, radius + spread, paint);
        }
    }

    /**
     * The frosted plum panel used for menus, the HUD and the win card.
     *
     * <p>It used to be a flat fill inside a single bright ring, and the ring's
     * <em>bottom</em> edge was the brightest line on the title screen — light arriving from
     * underneath a lamp-lit room. The panel is now lit the way everything else in the two
     * illustrations is: a wash of lamplight down from the top, a bright top edge with its
     * two corners, a shaded bottom edge, and a quiet hairline the whole way round to keep
     * the outline crisp.
     *
     * <p>The wash is drawn <em>over</em> the fill rather than being part of it, which
     * costs a second composite of at most {@link Theme#PANEL_LIFT_ALPHA} — the panel's
     * effective opacity moves by well under one percent, so the worst-case readability
     * {@code ThemeTest} pins against a 228-alpha panel over lamplight still holds. Building
     * the gradient into the fill instead would need either a shader or overlapping fills at
     * the panel's own alpha, and the second of those doubles the opacity along every seam.
     */
    public void panel(Canvas canvas, float left, float top, float right, float bottom,
                      int alpha) {
        float radius = Theme.scale(Theme.PANEL_RADIUS);
        shadow(canvas, left, top, right, bottom, radius, Theme.scale(10));

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(Theme.PANEL, alpha));
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);

        lamplight(canvas, left, top, right, bottom, radius);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Theme.hairline());
        paint.setColor(cappedAlpha(Theme.PANEL_EDGE_QUIET, alpha));
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);

        edge(canvas, left, top, right, bottom, radius, true,
                cappedAlpha(Theme.PANEL_EDGE_LIGHT, alpha));
        edge(canvas, left, top, right, bottom, radius, false,
                cappedAlpha(Theme.PANEL_EDGE_DARK, alpha));
    }

    /**
     * Lamplight falling down the panel, as nested rounded rectangles that all share its
     * top edge: a point near the top is under every one of them and a point near the
     * bottom is under only the longest, so the alpha ramps linearly without a shader.
     *
     * <p>No band is allowed to be shorter than two radii, because {@code drawRoundRect}
     * clamps a corner radius to half the box — a shorter band would come out squarer than
     * the panel and paint outside its corners.
     */
    private void lamplight(Canvas canvas, float left, float top, float right, float bottom,
                           float radius) {
        float shortest = top + 2 * radius;
        float span = bottom - shortest;
        int rings = glowRings(span, Theme.PANEL_LIFT_ALPHA);
        if (rings <= 0) {
            return;
        }
        float peak = glowPeak(span, Theme.PANEL_LIFT_ALPHA);
        paint.setStyle(Paint.Style.FILL);
        for (int band = 0; band < rings; band++) {
            int step = glowRingAlpha(band, rings, peak);
            if (step <= 0) {
                continue;
            }
            float reach = rings == 1 ? 1 : band / (float) (rings - 1);
            rect.set(left, top, right, lerp(bottom, shortest, reach));
            paint.setColor(withAlpha(Theme.CREAM, step));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }

    /**
     * A stroke along the top edge and its two corners, or the bottom edge and its two.
     *
     * <p>Stroking a rounded rect that stops half way down the panel would put a bright
     * horizontal line across the middle of it, so the lit edge is an open path: two
     * quarter-circle cubics either side of a straight run, ending where the all-round
     * hairline carries on. The cubic control points use the usual .5523 of the radius, so
     * the highlight sits on the fill's own corner rather than beside it.
     */
    private void edge(Canvas canvas, float left, float top, float right, float bottom,
                      float radius, boolean lit, int color) {
        float k = radius * .5523f;
        float y = lit ? top : bottom;
        float inward = lit ? radius : -radius;
        path.reset();
        path.moveTo(left, y + inward);
        path.cubicTo(left, y + inward - k, left + radius - k, y, left + radius, y);
        path.lineTo(right - radius, y);
        path.cubicTo(right - radius + k, y, right, y + inward - k, right, y + inward);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Theme.hairline());
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    /** An ARGB token dimmed to whatever alpha the surface it belongs to is drawn at. */
    private static int cappedAlpha(int color, int alpha) {
        return withAlpha(color, Math.min((color >>> 24) & 0xff, Math.max(0, alpha)));
    }

    /**
     * A soft halo around a rounded rectangle, reaching {@code reach} beyond it and
     * arriving at {@code peak} alpha against its own edge.
     *
     * <p>The win card's halo was four rings at alpha 13 — an eight-level step at every
     * ring boundary, which is four countable contour lines around the picture the whole
     * game is building toward. This spreads the same brightness across as many rings as
     * {@link Theme#GLOW_STEP_ALPHA_MAX} demands, and quietly refuses to be brighter than
     * its reach can carry (see {@link #glowPeak}).
     */
    public void glow(Canvas canvas, float left, float top, float right, float bottom,
                     float radius, float reach, int color, float peak) {
        int rings = glowRings(reach, peak);
        float safe = glowPeak(reach, peak);
        if (rings <= 0 || safe < 1) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        for (int ring = rings - 1; ring >= 0; ring--) {
            int step = glowRingAlpha(ring, rings, safe);
            if (step <= 0) {
                continue;
            }
            float spread = reach * (ring + 1) / rings;
            rect.set(left - spread, top - spread, right + spread, bottom + spread);
            paint.setColor(withAlpha(color, step));
            canvas.drawRoundRect(rect, radius + spread, radius + spread, paint);
        }
    }

    /**
     * The most alpha a stack spread over {@code reach} pixels may carry.
     *
     * <p>{@link Theme#GLOW_STEP_ALPHA_MAX} levels every {@link #GLOW_MIN_PITCH} pixels is
     * two levels per pixel; asking for more than that has to produce a visible step
     * somewhere, so the request is honoured as far as the geometry allows and no further.
     */
    static float glowPeak(float reach, float peak) {
        if (reach <= 0 || peak <= 0) {
            return 0;
        }
        return Math.min(peak, (int) (reach / GLOW_MIN_PITCH) * Theme.GLOW_STEP_ALPHA_MAX);
    }

    /**
     * How many rings to spend a halo over: never fewer than {@link Theme#GLOW_STEPS}
     * (which is what makes a shallow glow smooth), never more than the reach has room for,
     * and always enough that no single ring exceeds
     * {@link Theme#GLOW_STEP_ALPHA_MAX}.
     */
    static int glowRings(float reach, float peak) {
        float safe = glowPeak(reach, peak);
        int byPitch = (int) (reach / GLOW_MIN_PITCH);
        if (safe < 1 || byPitch <= 0) {
            return 0;
        }
        int byAlpha = (int) Math.ceil(safe / (double) Theme.GLOW_STEP_ALPHA_MAX);
        return Math.max(1, Math.min(byPitch, Math.max(Theme.GLOW_STEPS, byAlpha)));
    }

    /**
     * The alpha one ring of a {@code rings}-deep stack carries.
     *
     * <p>Error diffusion along the stack: each ring is handed the difference between the
     * running total it should have reached and the total already laid down. A ladder of
     * plain {@code peak / rings} would round every ring the same way and either overshoot
     * the peak or lose it entirely — at a peak of 12 over 16 rings, rounding gives sixteen
     * zeroes.
     */
    static int glowRingAlpha(int ring, int rings, float peak) {
        return Math.round(peak * (ring + 1) / rings) - Math.round(peak * ring / rings);
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

    /**
     * One menu row, focused or resting — the recipe the title screen and the cozy corner
     * share.
     *
     * <p>They were two designs for one flow: the same focused pill, but a rose-tinted
     * resting card on the title screen and a flat cream wash in settings, which the title
     * screen's own comment had already rejected as "the colour of a filing cabinet".
     *
     * <p>{@code beat} is the caller's pulse, 0 to 1; hand it a constant when
     * {@link Comfort#calmMotion} is on so the focus glows steadily instead of breathing.
     */
    public void menuRow(Canvas canvas, float left, float top, float right, float bottom,
                        float radius, boolean focused, float beat) {
        if (focused) {
            float halo = Math.max(Theme.scale(4), (bottom - top) * .10f);
            roundRect(canvas, left - halo, top - halo, right + halo, bottom + halo,
                    radius + halo,
                    withAlpha(Theme.PINK, (int) (55 + clamp01(beat) * 45)));
            roundRect(canvas, left, top, right, bottom, radius, Theme.PINK);
            roundRectStroke(canvas, left, top, right, bottom, radius,
                    Math.max(1.5f, Theme.scale(2.4f)), withAlpha(Theme.CREAM, 215));
        } else {
            roundRect(canvas, left, top, right, bottom, radius, Theme.ROW_REST);
            roundRectStroke(canvas, left, top, right, bottom, radius,
                    Math.max(1f, Theme.scale(1.4f)), Theme.ROW_REST_STROKE);
        }
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
     * <p>One {@link Path} is reused for the life of this {@code Draw}, so a screenful of
     * heart particles still allocates nothing.
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
    private Path heartPath(float cx, float cy, float size) {
        float k = size * .58f;
        float y = cy - .1275f * k;
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

    /**
     * Closes {@code amount} of the gap between two values, worked out from elapsed
     * milliseconds rather than from frames.
     *
     * <p>Easing "10% of the way there each frame" makes an animation's speed a property of
     * the display: the same code moves at half speed on a 30 Hz panel and races on a
     * 120 Hz one, and the screenshot harness — which steps at whatever rate it likes — can
     * never show the motion the sofa will see. An exponential in real time is the same
     * curve everywhere.
     *
     * <p>{@code tau} is the time constant: after one tau the gap is 63% closed, after
     * three it is 95% closed.
     */
    public static float approachRate(float elapsedMs, float tau) {
        if (elapsedMs <= 0 || tau <= 0) {
            return elapsedMs <= 0 ? 0 : 1;
        }
        return 1 - (float) Math.exp(-elapsedMs / tau);
    }

    public static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}

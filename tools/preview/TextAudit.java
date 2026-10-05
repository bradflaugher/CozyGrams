import android.graphics.Canvas;
import android.graphics.Paint;

import com.cozygrams.tv.Draw;
import com.cozygrams.tv.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The text-fit guard: told about every line of words and every surface a frame draws, it
 * says whether any of the words have left the place they were set in.
 *
 * <p>It exists because no unit test can see this. The app's tests run with
 * {@code returnDefaultValues}, where {@code Paint.measureText} answers zero and every fit
 * routine in the game passes trivially, so the only place a label running off its pill can
 * be caught is a rendered frame. This measures the real glyph outlines through the same
 * Java2D stubs the preview draws with, and checks, per frame:
 *
 * <ul>
 *   <li><b>containment</b> — the ink stays inside its container, with a little air: the
 *       lane declared with {@code Draw.beginBox}, or else the most recent surface drawn
 *       before it whose rounded outline contains the point the text was anchored to;</li>
 *   <li><b>the safe area</b> — no ink in the overscan margin or under a system bar;</li>
 *   <li><b>collisions</b> — no two visible lines' ink overlaps, and no line runs into a
 *       button cap it does not belong to;</li>
 *   <li><b>clipping</b> — no line is cut by a clip while the row it sits in is whole.</li>
 * </ul>
 *
 * <p>A line under a surface drawn later that covers it entirely (a tip bubble, the tour's
 * card) is hidden and takes no part, and so is a line faded below {@link #VISIBLE_ALPHA}.
 */
final class TextAudit implements Draw.Probe {

    /** Below this alpha a line has faded out: nobody can read it, so nothing can clash. */
    static final int VISIBLE_ALPHA = 40;
    /** A surface at least this opaque hides a line it covers completely. */
    private static final int COVER_ALPHA = 160;
    /** Surfaces fainter than this are washes, not containers. */
    private static final int SURFACE_ALPHA = 12;
    /** Overlaps smaller than this, in pixels, are antialiasing, not a collision. */
    private static final float SLOP = .75f;

    private static final class Text {
        String value;
        float[] ink;
        float[] visible;
        float anchorX;
        float anchorY;
        float size;
        int alpha;
        float[] box;
        float[] clip;
        int order;
        boolean hidden;
        Surface container;
    }

    private static final class Surface {
        float[] rect;
        float radius;
        int alpha;
        int kind;
        int order;
        float[] clip;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Text> texts = new ArrayList<>();
    private final List<Surface> surfaces = new ArrayList<>();
    private int order;
    private boolean active;
    private float width;
    private float height;

    /** Starts listening for one shot; {@link #finish} says what it found. */
    void begin(int w, int h) {
        width = w;
        height = h;
        active = true;
        reset();
    }

    private void reset() {
        texts.clear();
        surfaces.clear();
        order = 0;
    }

    @Override
    public void frame(Canvas canvas) {
        // A shot may draw several frames to settle (a tip finding its place, a page turn);
        // only the last one is the picture.
        reset();
    }

    @Override
    public void text(Canvas canvas, String value, float x, float y, float size, int color,
                     Paint.Align align, boolean strong, float spacing, float[] box) {
        if (!active || value == null || value.trim().isEmpty()) {
            return;
        }
        paint.setTypeface(strong ? Theme.bold() : Theme.regular());
        paint.setTextSize(size);
        float advance = paint.measureText(value) + spacing;
        float start = align == Paint.Align.LEFT ? x
                : align == Paint.Align.CENTER ? x - advance / 2 : x - advance;
        float[] glyphs = paint.harnessInk(value);
        Text t = new Text();
        t.value = value;
        t.ink = canvas.harnessMap(new float[]{start + glyphs[0], y + glyphs[1],
                start + glyphs[2] + spacing, y + glyphs[3]});
        // Where the caller put it: the end it is aligned by, half way up the capitals.
        float nudge = Math.min(size * .25f, advance / 2);
        float ax = align == Paint.Align.LEFT ? x + nudge
                : align == Paint.Align.RIGHT ? x - nudge : x;
        float[] anchor = canvas.harnessMap(new float[]{ax, y - size * .35f, ax,
                y - size * .35f});
        t.anchorX = anchor[0];
        t.anchorY = anchor[1];
        t.size = size;
        t.alpha = color >>> 24;
        t.box = box == null ? null : canvas.harnessMap(box.clone());
        t.clip = canvas.harnessClip();
        t.order = order++;
        texts.add(t);
    }

    @Override
    public void surface(Canvas canvas, float left, float top, float right, float bottom,
                        float radius, int color, int kind) {
        if (!active || right - left <= 0 || bottom - top <= 0) {
            return;
        }
        float[] rect = canvas.harnessMap(new float[]{left, top, right, bottom});
        float scale = (rect[2] - rect[0]) / (right - left);
        int alpha = color >>> 24;
        if (kind == Draw.KEYCAP && !surfaces.isEmpty()) {
            // A keycap is drawn as a rounded rect and then named: upgrade that one.
            Surface last = surfaces.get(surfaces.size() - 1);
            if (same(last.rect, rect)) {
                last.kind = kind;
                return;
            }
        }
        Surface s = new Surface();
        s.rect = rect;
        s.radius = radius * scale;
        s.alpha = alpha;
        s.kind = kind;
        s.order = order++;
        s.clip = canvas.harnessClip();
        surfaces.add(s);
        if (alpha >= COVER_ALPHA) {
            // Only what the surface really paints over: inside its rounded outline, and
            // inside whatever clip it was drawn through — a row scrolled half out of a
            // list hides nothing beyond the list's edge.
            float[] painted = s.clip == null ? rect : intersect(rect, s.clip);
            for (Text t : texts) {
                if (!t.hidden && inside(t.ink, painted, 0)
                        && outsideRounded(t.ink, rect, s.radius, 0) <= 0) {
                    t.hidden = true;
                }
            }
            for (Surface under : surfaces) {
                if (under != s && under.kind == Draw.KEYCAP && inside(under.rect, painted, 0)
                        && outsideRounded(under.rect, rect, s.radius, 0) <= 0) {
                    under.alpha = 0;
                }
            }
        }
    }

    /**
     * Every problem in the last frame drawn since {@link #begin}, one line each; empty when
     * every word is where it belongs.
     */
    List<String> finish() {
        active = false;
        List<String> problems = new ArrayList<>();
        float[] safe = {Theme.safeLeft(width), Theme.safeTop(height), Theme.safeRight(width),
                Theme.safeBottom(height)};
        List<Text> seen = new ArrayList<>();
        for (Text t : texts) {
            if (t.hidden || t.alpha < VISIBLE_ALPHA || clippedAway(t)) {
                continue;
            }
            seen.add(t);
            checkContainer(t, safe, problems);
            if (Boolean.getBoolean("cozy.audit.small")
                    && (t.container == null || t.container.kind != Draw.KEYCAP)
                    && t.value.replaceAll("[^A-Za-z]", "").length() >= 4
                    && t.size < Theme.scale(Theme.MIN_PROSE_SP) * .98f) {
                problems.add("SMALL " + describe(t) + " floor "
                        + px(Theme.scale(Theme.MIN_PROSE_SP)));
            }
            // What can collide is what can be seen: the part of the line inside its clip.
            t.visible = t.clip == null ? t.ink : intersect(t.ink, t.clip);
            if (!inside(t.ink, safe, 0)) {
                problems.add(describe(t) + " leaves the safe area by "
                        + px(outside(t.ink, safe)));
            }
            if (t.clip != null && !inside(t.ink, t.clip, 0)
                    && (t.container == null || inside(t.container.rect, t.clip, 0))) {
                problems.add(describe(t) + " is cut off by a clip");
            }
        }
        for (int i = 0; i < seen.size(); i++) {
            Text a = seen.get(i);
            for (int j = i + 1; j < seen.size(); j++) {
                Text b = seen.get(j);
                float over = overlap(a.visible, b.visible);
                // Glyph boxes are a little larger than glyphs: two digits set tight in a
                // narrow clue column can touch boxes without touching ink.
                if (over > Math.max(1.5f, Math.min(a.size, b.size) * .03f)) {
                    problems.add(describe(a) + " collides with " + describe(b) + " by "
                            + px(over));
                }
            }
            for (Surface s : surfaces) {
                if (s.kind != Draw.KEYCAP || s == a.container || s.alpha < VISIBLE_ALPHA) {
                    continue;
                }
                float over = overlap(a.visible, s.clip == null ? s.rect
                        : intersect(s.rect, s.clip));
                if (over > SLOP) {
                    problems.add(describe(a) + " runs into a button cap by " + px(over));
                }
            }
        }
        reset();
        return problems;
    }

    private void checkContainer(Text t, float[] safe, List<String> problems) {
        if (t.box != null) {
            float over = outside(t.ink, t.box);
            if (over > SLOP) {
                problems.add(describe(t) + " runs out of its lane by " + px(over));
            }
            return;
        }
        // The most recent surface under the anchor: a row is drawn after its panel, a cap
        // after its row, and a card laid over the screen after everything beneath it.
        Surface best = null;
        for (Surface s : surfaces) {
            if (s.order > t.order || s.alpha < SURFACE_ALPHA) {
                continue;
            }
            if (!containsPoint(s, t.anchorX, t.anchorY)) {
                continue;
            }
            // A surface clipped away where the words are is not under them at all.
            if (s.clip != null && (t.anchorX < s.clip[0] || t.anchorX > s.clip[2]
                    || t.anchorY < s.clip[1] || t.anchorY > s.clip[3])) {
                continue;
            }
            // A surface shorter than the capitals is a rule or a track, not a container.
            if (s.rect[3] - s.rect[1] < t.size * .7f) {
                continue;
            }
            best = s;
        }
        t.container = best;
        // Words half on and half off a card read as having fallen out of it, whichever
        // surface their anchor happens to land in: no line may straddle the edge of a
        // surface laid between its container and itself — a card inside the panel the
        // words are in. What lies under the container is hidden by it.
        int floor = best == null ? -1 : best.order;
        if (t.clip != null && !inside(t.ink, t.clip, 0)) {
            // Scrolling under a clip, part of the way out of view: whatever it is half on
            // is half out of view too.
            return;
        }
        for (Surface s : surfaces) {
            if (s.order > t.order || s.order <= floor || s.alpha < SURFACE_ALPHA
                    || s.kind == Draw.KEYCAP) {
                continue;
            }
            float[] visible = s.clip == null ? s.rect : intersect(s.rect, s.clip);
            float over = overlap(t.ink, visible);
            if (over > SLOP && !inside(t.ink, visible, 0)
                    && s.rect[3] - s.rect[1] >= t.size * .7f) {
                problems.add(describe(t) + " straddles the edge of a surface "
                        + rect(s.rect) + " by " + px(over));
            }
        }
        if (best == null) {
            return;
        }
        float air = Math.max(1f, Math.min(t.size * .06f, 4f));
        float over = outsideRounded(t.ink, best.rect, best.radius, air);
        if (over > SLOP) {
            problems.add(describe(t) + " runs past its "
                    + (best.kind == Draw.KEYCAP ? "button cap" : "surface "
                    + rect(best.rect)) + " by " + px(over));
        }
    }

    private boolean clippedAway(Text t) {
        return t.clip != null && overlap(t.ink, t.clip) <= 0;
    }

    // ---- Geometry ----------------------------------------------------------------------

    private static float[] intersect(float[] a, float[] b) {
        return new float[]{Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.min(a[2], b[2]),
                Math.min(a[3], b[3])};
    }

    private static boolean same(float[] a, float[] b) {
        for (int i = 0; i < 4; i++) {
            if (Math.abs(a[i] - b[i]) > .01f) {
                return false;
            }
        }
        return true;
    }

    private static boolean inside(float[] inner, float[] outer, float air) {
        return inner[0] >= outer[0] + air - SLOP && inner[1] >= outer[1] + air - SLOP
                && inner[2] <= outer[2] - air + SLOP && inner[3] <= outer[3] - air + SLOP;
    }

    /** How far {@code inner} reaches past {@code outer} on its worst side; 0 if inside. */
    private static float outside(float[] inner, float[] outer) {
        return Math.max(0, Math.max(Math.max(outer[0] - inner[0], outer[1] - inner[1]),
                Math.max(inner[2] - outer[2], inner[3] - outer[3])));
    }

    /** The smaller of the two extents of the intersection; 0 when apart. */
    private static float overlap(float[] a, float[] b) {
        float w = Math.min(a[2], b[2]) - Math.max(a[0], b[0]);
        float h = Math.min(a[3], b[3]) - Math.max(a[1], b[1]);
        return w > 0 && h > 0 ? Math.min(w, h) : 0;
    }

    private static boolean containsPoint(Surface s, float x, float y) {
        return roundedDistance(s.rect, s.radius, x, y) <= 0;
    }

    /**
     * Signed distance from a point to a rounded rectangle's outline: negative inside.
     */
    private static float roundedDistance(float[] r, float radius, float x, float y) {
        float rad = Math.max(0, Math.min(radius,
                Math.min(r[2] - r[0], r[3] - r[1]) / 2));
        float cx = (r[0] + r[2]) / 2;
        float cy = (r[1] + r[3]) / 2;
        float hx = (r[2] - r[0]) / 2 - rad;
        float hy = (r[3] - r[1]) / 2 - rad;
        float qx = Math.abs(x - cx) - hx;
        float qy = Math.abs(y - cy) - hy;
        float ox = Math.max(qx, 0);
        float oy = Math.max(qy, 0);
        return (float) Math.hypot(ox, oy) + Math.min(Math.max(qx, qy), 0) - rad;
    }

    /**
     * How far the ink's corners and edges reach past a rounded rectangle shrunk by
     * {@code air}; 0 when the whole of the ink is inside.
     */
    private static float outsideRounded(float[] ink, float[] rect, float radius, float air) {
        float worst = 0;
        float midX = (ink[0] + ink[2]) / 2;
        float midY = (ink[1] + ink[3]) / 2;
        float[][] points = {{ink[0], ink[1]}, {ink[2], ink[1]}, {ink[0], ink[3]},
                {ink[2], ink[3]}, {midX, ink[1]}, {midX, ink[3]}, {ink[0], midY},
                {ink[2], midY}};
        for (float[] p : points) {
            worst = Math.max(worst, roundedDistance(rect, radius, p[0], p[1]) + air);
        }
        return worst;
    }

    private static String describe(Text t) {
        return "\"" + t.value + "\" (" + px(t.size) + " type)";
    }

    private static String rect(float[] r) {
        return String.format(Locale.ROOT, "[%.0f,%.0f %.0fx%.0f]", r[0], r[1], r[2] - r[0],
                r[3] - r[1]);
    }

    private static String px(float value) {
        return String.format(Locale.ROOT, "%.1f px", value);
    }
}

package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * One-time tips: a small speech bubble beside a control, the first time a player meets it.
 *
 * <p>Each tip is shown once in the life of a save, one at a time, a moment after the screen
 * it belongs to has settled. It never takes a press for itself: the press that comes next
 * does what it always does, and also puts the tip away. A tip nobody answers fades out on
 * its own after {@value #SHOW_MS} ms. A player who was here before tips existed has met
 * all of these already and sees none.
 *
 * <p>The state is plain numbers so the rules can be tested without a device; the bubble
 * is drawn here too, because it is one rounded rectangle and a pointer.
 */
public final class Tips {

    public static final int STORY = 0;
    public static final int CORNER = 1;
    public static final int CLUES = 2;
    public static final int PEN = 3;
    public static final int LEGEND = 4;
    public static final int HINT = 5;
    public static final int JOIN = 6;
    public static final int COUNT = 7;

    /** Every tip, as a bit mask: what a returning player has already "seen". */
    public static final int ALL = (1 << COUNT) - 1;

    static final long SHOW_MS = 7000;
    /** A press this soon after a tip appears was already on its way; it does not count. */
    static final long GRACE_MS = 600;
    /** Breathing room between one tip going and the next arriving, and after a new screen. */
    static final long GAP_MS = 1400;

    /** Tips already shown, as bits. */
    public int seen;
    /** The tip on screen, or -1. */
    public int showing = -1;
    public long shownAt;
    public long hiddenAt;

    /** The words, in the names of whatever is in the room. */
    static String text(int tip, int hands) {
        boolean touch = hands == Tutorial.HANDS_TOUCH;
        switch (tip) {
            case STORY:
                return touch ? "Tap ‹ › to choose a chapter, then the row to open it"
                        : "Left and right choose a chapter; " + confirm(hands) + " opens it";
            case CORNER:
                return "Sound, comfort and How to play live in the Cozy Corner";
            case CLUES:
                return "These numbers are the clues: runs of filled squares, in order";
            case PEN:
                return "The pen: choose whether a tap fills or crosses out";
            case LEGEND:
                return hands == Tutorial.HANDS_REMOTE
                        ? "Your buttons are listed here: hold OK when you're stuck"
                        : "Your buttons are listed here: Y lights up a square";
            case HINT:
                return "Stuck? HINT lights up one square";
            default:
                return "A second controller joins as Sky: just press any button on it";
        }
    }

    private static String confirm(int hands) {
        switch (hands) {
            case Tutorial.HANDS_REMOTE:
                return "OK";
            case Tutorial.HANDS_UNKNOWN:
                return "A or OK";
            default:
                return "A";
        }
    }

    /** Whether a tip belongs on what is showing now. */
    static boolean applies(int tip, UiState ui, boolean touch) {
        boolean home = ui.screen == UiState.HOME;
        boolean playing = ui.screen == UiState.GAME && !ui.won;
        switch (tip) {
            case STORY:
            case CORNER:
                return home;
            case CLUES:
                return playing;
            case PEN:
            case HINT:
                return playing && touch;
            case LEGEND:
                return playing && !touch;
            default:
                return playing && !touch && ui.twoPlayers && !ui.joined[1];
        }
    }

    /** The first tip not yet seen that belongs here, or -1. */
    int next(UiState ui, boolean touch) {
        for (int tip = 0; tip < COUNT; tip++) {
            if ((seen & (1 << tip)) == 0 && applies(tip, ui, touch)) {
                return tip;
            }
        }
        return -1;
    }

    /** True when a new tip may appear: none showing and the last one long enough gone. */
    boolean ready(long now, long screenSince) {
        return showing < 0 && now - hiddenAt >= GAP_MS && now - screenSince >= GAP_MS;
    }

    public void show(int tip, long now) {
        showing = tip;
        shownAt = now;
        seen |= 1 << tip;
    }

    /** Any press: puts the tip away, unless it has only just appeared. */
    boolean dismiss(long now) {
        if (showing < 0 || now - shownAt < GRACE_MS) {
            return false;
        }
        hide(now);
        return true;
    }

    public void hide(long now) {
        showing = -1;
        hiddenAt = now;
    }

    /** Lets a tip fade on its own; and a tip whose screen has gone goes with it. */
    void tick(long now, UiState ui, boolean touch) {
        if (showing >= 0 && (now - shownAt >= SHOW_MS || !applies(showing, ui, touch))) {
            hide(now);
        }
    }

    // ---- The bubble ------------------------------------------------------------------

    private final Path pointer = new Path();

    /**
     * Draws the bubble next to {@code anchor} (left, top, right, bottom): below it if it
     * fits, else above, else to its left. Kept inside the safe area whatever happens.
     */
    void draw(Canvas canvas, Draw draw, float width, float height, float[] anchor, String text,
              long age, boolean bold) {
        float appear = Comfort.get().calmMotion ? 1f : Draw.clamp01(age / 220f);
        float fade = Draw.clamp01((SHOW_MS - age) / 400f);
        float alpha = Math.min(appear, fade);
        if (alpha <= 0) {
            return;
        }
        float size = Theme.textSize(Theme.CAPTION);
        float maxWidth = Math.min(width * .34f, Theme.scale(560));
        String[] lines = wrap(draw, text, size, maxWidth);
        // Balance the lines, so a two-line tip never leaves one word alone on the second.
        if (lines.length > 1) {
            float narrow = maxWidth;
            while (narrow > maxWidth * .4f) {
                String[] tighter = wrap(draw, text, size, narrow * .95f);
                if (tighter.length != lines.length) {
                    break;
                }
                narrow *= .95f;
                lines = tighter;
            }
        }
        float textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, draw.measure(line, size, false));
        }
        float padX = size * .8f;
        float padY = size * .55f;
        float w = textWidth + padX * 2 + size * .9f;
        float h = lines.length * size * 1.3f + padY * 2 - size * .3f;
        float arrow = size * .55f;
        float safeL = Theme.safeLeft(width);
        float safeR = Theme.safeRight(width);
        float safeT = Theme.safeTop(height);
        float safeB = Theme.safeBottom(height);

        float l;
        float t;
        int side; // 0 below, 1 above, 2 left
        // Something on the right-hand rail is pointed at from its left, so the bubble
        // lies over the backdrop rather than over the rail's other buttons.
        boolean railSide = (anchor[0] + anchor[2]) / 2 > width * .62f
                && anchor[0] - arrow - w >= safeL;
        if (railSide) {
            side = 2;
            l = anchor[0] - arrow - w;
            t = (anchor[1] + anchor[3]) / 2 - h / 2;
        } else if (anchor[3] + arrow + h <= safeB) {
            side = 0;
            t = anchor[3] + arrow;
            l = (anchor[0] + anchor[2]) / 2 - w / 2;
        } else if (anchor[1] - arrow - h >= safeT) {
            side = 1;
            t = anchor[1] - arrow - h;
            l = (anchor[0] + anchor[2]) / 2 - w / 2;
        } else {
            side = 2;
            l = anchor[0] - arrow - w;
            t = (anchor[1] + anchor[3]) / 2 - h / 2;
        }
        l = Math.max(safeL, Math.min(safeR - w, l));
        t = Math.max(safeT, Math.min(safeB - h, t));
        float rise = (1 - appear) * Theme.scale(8);
        t += side == 1 ? -rise : rise;

        int fill = Draw.withAlpha(Theme.INK, (int) (245 * alpha));
        int edge = Draw.withAlpha(Theme.PINK, (int) (255 * alpha));
        float radius = Math.min(h / 2, size * .9f);
        draw.shadow(canvas, l, t, l + w, t + h, radius, Theme.scale(5));
        draw.roundRect(canvas, l, t, l + w, t + h, radius, fill);
        draw.roundRectStroke(canvas, l, t, l + w, t + h, radius, Theme.hairline(), edge);

        // The pointer, toward the anchor's middle.
        float ax = Math.max(l + radius, Math.min(l + w - radius, (anchor[0] + anchor[2]) / 2));
        float ay = Math.max(t + radius, Math.min(t + h - radius, (anchor[1] + anchor[3]) / 2));
        pointer.reset();
        if (side == 0) {
            pointer.moveTo(ax - arrow, t + 1);
            pointer.lineTo(ax, t - arrow);
            pointer.lineTo(ax + arrow, t + 1);
        } else if (side == 1) {
            pointer.moveTo(ax - arrow, t + h - 1);
            pointer.lineTo(ax, t + h + arrow);
            pointer.lineTo(ax + arrow, t + h - 1);
        } else {
            pointer.moveTo(l + w - 1, ay - arrow);
            pointer.lineTo(l + w + arrow, ay);
            pointer.lineTo(l + w - 1, ay + arrow);
        }
        pointer.close();
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(edge);
        canvas.drawPath(pointer, paint);

        draw.heart(canvas, l + padX * .75f + size * .2f, t + padY + size * .32f, size * .5f,
                Draw.withAlpha(Theme.PINK, (int) (255 * alpha)));
        int ink = Draw.withAlpha(Theme.CREAM, (int) ((bold ? 255 : 240) * alpha));
        float y = t + padY + size * .72f;
        for (String line : lines) {
            draw.text(canvas, line, l + padX + size * .9f, y, size, ink, Paint.Align.LEFT, false);
            y += size * 1.3f;
        }
    }

    private static String[] wrap(Draw draw, String text, float size, float width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String tried = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && draw.measure(tried, size, false) > width) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(tried);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.toArray(new String[0]);
    }
}

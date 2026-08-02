package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * The pause and options screen, framed as tidying the room rather than configuration.
 *
 * <p>The room is read from a sofa, with a remote that has a D-pad, a centre button and
 * Back — nothing else is assumed. So every row is a single line with a switch, the whole
 * list moves under up and down, and the explanation for whichever row is highlighted is
 * given once, in full size, along the bottom. Eleven small captions all shouting at once
 * is a control panel; one calm sentence is a cozy corner.
 *
 * <p>{@link #ITEM_COUNT} and the {@code ITEM_} constants are the single source of truth
 * for the row order. {@link #toggle} is the matching source of truth for what each row
 * does, so a caller only has to route the centre button here.
 */
public final class SettingsScene {

    public static final int ITEM_MUSIC = 0;
    public static final int ITEM_SFX = 1;
    public static final int ITEM_GENTLE = 2;
    public static final int ITEM_HINTS = 3;
    public static final int ITEM_BIG_TEXT = 4;
    public static final int ITEM_CONTRAST = 5;
    public static final int ITEM_DISTINCT_PLAYERS = 6;
    public static final int ITEM_BOLD_CURSOR = 7;
    public static final int ITEM_CALM_MOTION = 8;
    public static final int ITEM_DEFAULTS = 9;
    public static final int ITEM_BACK = 10;
    public static final int ITEM_COUNT = 11;

    /** The last row that carries a switch; everything after it is an action. */
    private static final int LAST_SWITCH = ITEM_CALM_MOTION;

    /** Rows after which a little extra air separates one group of options from the next. */
    private static final int[] BREAK_AFTER = {ITEM_SFX, ITEM_HINTS, ITEM_CALM_MOTION};

    /** A group break is worth this much of a row. */
    private static final float GROUP_GAP = .34f;

    /** The band of the screen the rows live in, as a fraction of its height. */
    static final float ROWS_TOP = .215f;
    static final float ROWS_BOTTOM = .820f;

    private final Draw draw;

    public SettingsScene(Draw draw) {
        this.draw = draw;
    }

    public static String[] labels() {
        return new String[]{
                "MUSIC",
                "SOUND EFFECTS",
                "GENTLE MISTAKE CHECK",
                "HINT BUTTON",
                "LARGER TEXT",
                "EXTRA CONTRAST",
                "TELL ROSE & SKY APART",
                "BOLDER CURSORS",
                "CALMER ANIMATION",
                "PUT EVERYTHING BACK",
                "BACK TO THE PUZZLE"
        };
    }

    /**
     * One line about the highlighted row. Kept short so it still fits on one line at the
     * largest text size, on the narrowest panel we draw.
     */
    public static String[] descriptions() {
        return new String[]{
                "a soft music box in the background",
                "little chimes for every square",
                "we'll gently say when a square isn't right",
                "the hint button lights up one square",
                "bigger type for the far side of the room",
                "deepen the backdrop so the clues pop",
                "Sky takes a deeper teal and a dashed ring",
                "thicker cursor rings that are easy to find",
                "fewer sparkles, and nothing that pulses",
                "every option back the way it started",
                "we'll keep your place"
        };
    }

    public static boolean[] states(UiState ui) {
        Comfort comfort = Comfort.get();
        return new boolean[]{
                ui.musicOn,
                ui.sfxOn,
                ui.gentleCheck,
                ui.hintsOn,
                ui.bigTextOn,
                ui.highContrastOn,
                comfort.distinctPlayers,
                comfort.boldCursor,
                comfort.calmMotion,
                false,
                false
        };
    }

    /** True when the row shows a switch, rather than being something you simply do. */
    public static boolean hasSwitch(int item) {
        return item >= 0 && item <= LAST_SWITCH;
    }

    /**
     * Acts on a row, exactly as pressing the centre button should.
     *
     * @return true when the row was handled here, false for {@link #ITEM_BACK} and
     *         anything out of range — which the caller should treat as "close the corner".
     */
    public static boolean toggle(UiState ui, int item) {
        Comfort comfort = Comfort.get();
        switch (item) {
            case ITEM_MUSIC:
                ui.musicOn = !ui.musicOn;
                return true;
            case ITEM_SFX:
                ui.sfxOn = !ui.sfxOn;
                return true;
            case ITEM_GENTLE:
                ui.gentleCheck = !ui.gentleCheck;
                return true;
            case ITEM_HINTS:
                ui.hintsOn = !ui.hintsOn;
                return true;
            case ITEM_BIG_TEXT:
                ui.bigTextOn = !ui.bigTextOn;
                return true;
            case ITEM_CONTRAST:
                ui.highContrastOn = !ui.highContrastOn;
                return true;
            case ITEM_DISTINCT_PLAYERS:
                comfort.distinctPlayers = !comfort.distinctPlayers;
                return true;
            case ITEM_BOLD_CURSOR:
                comfort.boldCursor = !comfort.boldCursor;
                return true;
            case ITEM_CALM_MOTION:
                comfort.calmMotion = !comfort.calmMotion;
                return true;
            case ITEM_DEFAULTS:
                ui.restoreDefaults();
                return true;
            default:
                return false;
        }
    }

    // ---- Layout ----------------------------------------------------------------------

    /**
     * The vertical centre of every row, spread evenly through the band between
     * {@code top} and {@code bottom} with a breath of air between groups.
     *
     * <p>Deliberately worked out from the space available rather than from fixed design
     * pixels: larger text scales the type, and the rows have to keep fitting.
     */
    static float[] rowCentres(float top, float bottom) {
        float step = rowStep(top, bottom);
        float[] centres = new float[ITEM_COUNT];
        float extra = 0;
        for (int item = 0; item < ITEM_COUNT; item++) {
            centres[item] = top + step * (item + .5f) + extra;
            if (breaksAfter(item)) {
                extra += step * GROUP_GAP;
            }
        }
        return centres;
    }

    /** Half the height of a row's pill, leaving a clear gap between neighbours. */
    static float rowHalfHeight(float top, float bottom) {
        return rowStep(top, bottom) * .43f;
    }

    private static float rowStep(float top, float bottom) {
        return (bottom - top) / (ITEM_COUNT + GROUP_GAP * BREAK_AFTER.length);
    }

    private static boolean breaksAfter(int item) {
        for (int at : BREAK_AFTER) {
            if (at == item) {
                return true;
            }
        }
        return false;
    }

    // ---- Drawing ---------------------------------------------------------------------

    public void draw(Canvas canvas, float width, float height, UiState ui, long now) {
        draw.panel(canvas, width * .255f, height * Theme.SAFE_AREA, width * .745f,
                height * (1 - Theme.SAFE_AREA), 235);

        draw.shadowedText(canvas, "COZY CORNER", width / 2, height * .128f,
                Theme.textSize(Theme.HEADING), Theme.CREAM, Paint.Align.CENTER, true);
        draw.text(canvas, "Make the room feel just right", width / 2, height * .175f,
                Theme.textSize(Theme.CAPTION), Theme.BLUE, Paint.Align.CENTER, false);

        // The menu index belongs to the caller, so never trust it to be in range.
        int focus = Math.floorMod(ui.menu, ITEM_COUNT);
        String[] labels = labels();
        boolean[] states = states(ui);
        float[] centres = rowCentres(height * ROWS_TOP, height * ROWS_BOTTOM);
        float half = rowHalfHeight(height * ROWS_TOP, height * ROWS_BOTTOM);
        for (int item = 0; item < ITEM_COUNT; item++) {
            drawRow(canvas, width, centres[item], half, labels[item], states[item],
                    item == focus, item, now);
        }

        drawDescription(canvas, width, height, descriptions()[focus]);
        draw.text(canvas, "D-pad to move   ·   OK to change   ·   Back to close",
                width / 2, height * .916f, Theme.textSize(Theme.CAPTION), Theme.SOFT_TEXT,
                Paint.Align.CENTER, false);
    }

    private void drawRow(Canvas canvas, float width, float centreY, float half,
                         String label, boolean on, boolean focused, int item, long now) {
        float left = width * .285f;
        float right = width * .715f;
        float radius = half * .82f;

        if (focused) {
            // A steady glow rather than a heartbeat when movement is unwelcome.
            float beat = Comfort.get().calmMotion ? .55f
                    : (float) Math.abs(Math.sin(now / 900f));
            float glow = half * .2f;
            draw.roundRect(canvas, left - glow, centreY - half - glow, right + glow,
                    centreY + half + glow, radius + glow,
                    Draw.withAlpha(Theme.PINK, (int) (55 + beat * 45)));
            draw.roundRect(canvas, left, centreY - half, right, centreY + half, radius,
                    Theme.PINK);
            draw.roundRectStroke(canvas, left, centreY - half, right, centreY + half,
                    radius, Math.max(1.5f, Theme.scale(2.4f)),
                    Draw.withAlpha(Theme.CREAM, 215));
        } else {
            draw.roundRect(canvas, left, centreY - half, right, centreY + half, radius,
                    Color.argb(46, 255, 245, 227));
        }

        float pad = Theme.scale(24);
        float switchWidth = half * 2.4f;
        float switchLeft = right - pad - switchWidth;
        float chips = item == ITEM_DISTINCT_PLAYERS ? half * 3.1f + Theme.scale(16) : 0;
        float reserved = hasSwitch(item) ? switchWidth + pad * 2 + chips : pad;
        float labelSize = fit(label, right - left - pad - reserved, Theme.textSize(17), true);
        draw.text(canvas, label, left + pad, centreY + draw.capCentreOffset(labelSize),
                labelSize, focused ? Theme.INK : Theme.CREAM, Paint.Align.LEFT, true);

        if (item == ITEM_DISTINCT_PLAYERS) {
            drawIdentityChips(canvas, switchLeft - Theme.scale(16), centreY, half, focused);
        }
        if (hasSwitch(item)) {
            drawSwitch(canvas, switchLeft, centreY, switchWidth, half * 1.24f, on, focused);
        }
    }

    /**
     * Two little chips showing Rose's and Sky's current colours, so the effect of the
     * colour row can be seen from the row itself rather than taken on trust.
     */
    private void drawIdentityChips(Canvas canvas, float rightEdge, float centreY,
                                   float half, boolean focused) {
        float radius = half * .58f;
        float gap = radius * 2.4f;
        float skyX = rightEdge - radius * 1.34f;
        float roseX = skyX - gap;
        drawChip(canvas, roseX, centreY, radius, Theme.PINK, focused);
        drawChip(canvas, skyX, centreY, radius, Comfort.skyColor(), focused);
    }

    /** A colour chip sunk into a dark well, so Rose's pink still shows on a pink pill. */
    private void drawChip(Canvas canvas, float cx, float centreY, float radius, int color,
                          boolean focused) {
        draw.circle(canvas, cx, centreY, radius * 1.34f,
                Draw.withAlpha(Theme.INK, focused ? 240 : 130));
        draw.circle(canvas, cx, centreY, radius, color);
        draw.circleStroke(canvas, cx, centreY, radius * 1.34f, Math.max(1f, radius * .12f),
                Draw.withAlpha(Theme.CREAM, focused ? 150 : 110));
    }

    /** A pill switch: the knob's position carries the state, so colour is never alone. */
    private void drawSwitch(Canvas canvas, float left, float centreY, float width,
                            float height, boolean on, boolean focused) {
        float radius = height / 2;
        int trackOn = focused ? Theme.INK : Theme.PINK;
        int trackOff = focused ? Draw.withAlpha(Theme.INK, 90)
                : Color.argb(70, 255, 245, 227);
        draw.roundRect(canvas, left, centreY - radius, left + width, centreY + radius,
                radius, on ? trackOn : trackOff);
        float knobX = on ? left + width - radius : left + radius;
        draw.circle(canvas, knobX, centreY, radius * .76f,
                on ? Theme.CREAM : (focused ? Draw.withAlpha(Theme.INK, 150)
                        : Theme.SOFT_TEXT));
        if (on) {
            draw.heart(canvas, left + radius * .95f, centreY, radius * .8f,
                    focused ? Draw.withAlpha(Theme.PINK_LIGHT, 220)
                            : Draw.withAlpha(Theme.CREAM, 200));
        }
    }

    /** The one explanation on screen, given properly rather than whispered. */
    private void drawDescription(Canvas canvas, float width, float height,
                                 String description) {
        if (description.isEmpty()) {
            return;
        }
        float room = width * .49f - Theme.scale(56);
        float size = fit(description, room, Theme.textSize(Theme.BODY), false);
        draw.text(canvas, description, width / 2, height * .868f, size,
                Draw.withAlpha(Theme.CREAM, 225), Paint.Align.CENTER, false);
    }

    /**
     * The largest size at or below {@code preferred} that fits the width — but never
     * below the ten-foot legibility floor, because unreadable text is worse than tight
     * text.
     */
    private float fit(String text, float maxWidth, float preferred, boolean strong) {
        float floor = Theme.textSize(0);
        float size = preferred;
        float stepDown = Math.max(.5f, Theme.scale(1));
        while (size > floor && draw.measure(text, size, strong) > maxWidth) {
            size -= stepDown;
        }
        return Math.max(floor, size);
    }
}

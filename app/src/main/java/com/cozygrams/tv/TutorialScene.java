package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the first-evening tour over whatever screen is behind it.
 *
 * <p>One card: the little puzzle on the left, drawn the way the real board is — paper,
 * clues in the gutters, Rose's ring and letter — and the words on the right. Along the
 * bottom are three buttons, Skip tour, Back and Next, and the one with the focus is lit
 * pink with a cream ring, which is how every menu in the game says "this one". Progress is
 * a row of dots and a "step 3 of 8" in words, so neither is colour alone.
 *
 * <p>Motion is the board's own: squares arrive one after another and the glowing square
 * breathes. With Reduce motion on, the squares are simply there and nothing breathes.
 */
public final class TutorialScene {

    private final Draw draw;

    private final float[][] buttonRects = new float[3][4];
    private boolean buttonsDrawn;
    private final float[] targetRect = new float[4];
    private boolean targetDrawn;

    public TutorialScene(Draw draw) {
        this.draw = draw;
    }

    /** The button under a point, or -1. */
    public int buttonAt(float x, float y) {
        if (!buttonsDrawn) {
            return -1;
        }
        float slop = Theme.scale(6);
        for (int b = 0; b < 3; b++) {
            float[] r = buttonRects[b];
            if (x >= r[0] - slop && x <= r[2] + slop && y >= r[1] - slop && y <= r[3] + slop) {
                return b;
            }
        }
        return -1;
    }

    /** True when a point lands on the glowing square, with a thumb's slack around it. */
    public boolean targetAt(float x, float y) {
        if (!targetDrawn) {
            return false;
        }
        float slop = Math.max(Theme.scale(10), (HudScene.touchMinPx()
                - (targetRect[2] - targetRect[0])) / 2);
        return x >= targetRect[0] - slop && x <= targetRect[2] + slop
                && y >= targetRect[1] - slop && y <= targetRect[3] + slop;
    }

    /** Forgets every hit rectangle, for a frame without the tour. */
    public void forget() {
        buttonsDrawn = false;
        targetDrawn = false;
    }

    /** Which hands the words should name, from what the room has shown so far. */
    static int hands() {
        if (HudScene.touch()) {
            return Tutorial.HANDS_TOUCH;
        }
        if (HudScene.keyboard()) {
            return Tutorial.HANDS_KEYBOARD;
        }
        if (HudScene.remoteOnly()) {
            return Tutorial.HANDS_REMOTE;
        }
        return HudScene.padSeen() ? Tutorial.HANDS_PAD : Tutorial.HANDS_UNKNOWN;
    }

    public void draw(Canvas canvas, float width, float height, Tutorial tour, UiState ui,
                     long now) {
        boolean bold = ui.highContrastOn;
        boolean calm = Comfort.get().calmMotion;
        int hands = hands();
        draw.roundRect(canvas, 0, 0, width, height, 0,
                Draw.withAlpha(Theme.NIGHT, bold ? 205 : Theme.SCRIM_PEAK));
        float left = Math.max(width * .06f, Theme.safeLeft(width));
        float right = Math.min(width * .94f, Theme.safeRight(width));
        float top = Math.max(Theme.stageTop(height) + Theme.unitHeight() * Theme.SAFE_AREA,
                Theme.safeTop(height));
        float bottom = Math.min(Theme.stageBottom(height) - Theme.unitHeight() * Theme.SAFE_AREA,
                Theme.safeBottom(height));
        draw.roundRect(canvas, left, top, right, bottom, Theme.scale(Theme.PANEL_RADIUS),
                Theme.PANEL);
        draw.panel(canvas, left, top, right, bottom, bold ? 250 : 242);

        float pad = Theme.scale(30);
        float innerLeft = left + pad;
        float innerRight = right - pad;

        // Eyebrow and progress.
        float capSize = Theme.textSize(Theme.CAPTION);
        float eyebrowBase = top + Theme.scale(22) + capSize * .8f;
        draw.text(canvas, "A LITTLE TOUR  ·  STEP " + (tour.step + 1) + " OF "
                        + Tutorial.STEP_COUNT, innerLeft, eyebrowBase, capSize, Theme.GOLD,
                Paint.Align.LEFT, true);
        drawDots(canvas, innerRight, eyebrowBase - capSize * .35f, tour.step, bold);

        // Buttons, from the bottom up.
        float buttonHeight = Math.max(capSize * 2.0f, HudScene.touchMinPx());
        float buttonsBottom = bottom - Theme.scale(20);
        float buttonsTop = buttonsBottom - buttonHeight;
        drawButtons(canvas, innerLeft, innerRight, buttonsTop, buttonsBottom, capSize, tour,
                bold, calm, now);

        float contentTop = eyebrowBase + Theme.scale(20);
        float contentBottom = buttonsTop - Theme.scale(20);
        float split = innerLeft + (innerRight - innerLeft) * .42f;
        if (tour.step == Tutorial.STEP_NEXT) {
            targetDrawn = false;
            drawWhereNext(canvas, innerLeft, contentTop, split - Theme.scale(24), contentBottom,
                    bold);
        } else {
            drawBoard(canvas, innerLeft, contentTop, split - Theme.scale(24), contentBottom,
                    tour, bold, calm, now);
        }
        drawWords(canvas, split + Theme.scale(12), contentTop, innerRight, contentBottom,
                tour, hands, bold, calm, now);
    }

    // ---- The progress dots and the buttons ---------------------------------------------

    private void drawDots(Canvas canvas, float right, float centreY, int step, boolean bold) {
        float r = Theme.scale(6);
        float gap = Theme.scale(10);
        float x = right - r;
        for (int s = Tutorial.STEP_COUNT - 1; s >= 0; s--) {
            if (s == step) {
                draw.roundRect(canvas, x - r * 2.2f, centreY - r, x + r, centreY + r, r,
                        Theme.PINK);
                x -= r * 3.2f + gap;
                continue;
            }
            draw.circle(canvas, x, centreY, r * .8f, s < step
                    ? Draw.withAlpha(Theme.PINK_LIGHT, 200)
                    : Draw.withAlpha(Theme.CREAM, bold ? 120 : 70));
            x -= r * 2 + gap;
        }
    }

    private void drawButtons(Canvas canvas, float left, float right, float top, float bottom,
                             float size, Tutorial tour, boolean bold, boolean calm, long now) {
        float half = (bottom - top) / 2;
        float gap = Theme.scale(16);
        String[] labels = {
                Tutorial.buttonName(Tutorial.FOCUS_SKIP, false),
                "‹  " + Tutorial.buttonName(Tutorial.FOCUS_BACK, false),
                tour.nextLabel() + "  ›"};
        float[] widths = new float[3];
        for (int b = 0; b < 3; b++) {
            widths[b] = draw.measure(labels[b], size, true) + half * 2.2f;
        }
        float[] lefts = {left, right - widths[2] - gap - widths[1], right - widths[2]};
        float beat = calm ? .5f : (float) Math.abs(Math.sin(now / 900.0));
        for (int b = 0; b < 3; b++) {
            float l = lefts[b];
            float r = l + widths[b];
            boolean enabled = tour.enabled(b);
            boolean focused = enabled && tour.focus == b;
            if (focused) {
                float glow = half * .22f;
                draw.roundRect(canvas, l - glow, top - glow, r + glow, bottom + glow,
                        half + glow, Draw.withAlpha(Theme.PINK, (int) (50 + beat * 50)));
                draw.roundRect(canvas, l, top, r, bottom, half, Theme.PINK);
                draw.roundRectStroke(canvas, l, top, r, bottom, half, Theme.hairline() * 1.5f,
                        Draw.withAlpha(Theme.CREAM, 235));
            } else {
                draw.roundRect(canvas, l, top, r, bottom, half,
                        Draw.withAlpha(Theme.ROW_REST, bold ? 90 : 56));
                draw.roundRectStroke(canvas, l, top, r, bottom, half, Theme.keyline(),
                        Draw.withAlpha(Theme.ROW_REST_STROKE, bold ? 140 : 80));
            }
            int ink = focused ? Theme.textOn(Theme.PINK)
                    : Draw.withAlpha(Theme.CREAM, enabled ? 235 : 90);
            draw.text(canvas, labels[b], (l + r) / 2,
                    (top + bottom) / 2 + draw.capCentreOffset(size), size, ink,
                    Paint.Align.CENTER, true);
            buttonRects[b][0] = l;
            buttonRects[b][1] = top;
            buttonRects[b][2] = r;
            buttonRects[b][3] = bottom;
        }
        buttonsDrawn = true;
        if (!HudScene.touch()) {
            // How to move between them, small, between Skip and Back.
            boolean typing = HudScene.keyboard();
            String how = "◂ ▸ choose  ·  " + (typing ? "Enter" : HomeScene.confirmName())
                    + " press  ·  " + (typing ? "Esc" : HudScene.remoteOnly() ? "Back" : "⧉")
                    + " skip";
            float hint = Theme.textSize(Theme.MIN_PROSE_SP) * .8f;
            float room = lefts[1] - (left + widths[0]) - gap * 2;
            hint = draw.fit(how, hint, room, false, Theme.scale(14));
            if (draw.measure(how, hint, false) <= room) {
                draw.text(canvas, how, (left + widths[0] + lefts[1]) / 2,
                        (top + bottom) / 2 + draw.capCentreOffset(hint), hint,
                        Theme.secondaryText(bold), Paint.Align.CENTER, false);
            }
        }
    }

    // ---- The words -------------------------------------------------------------------

    private void drawWords(Canvas canvas, float left, float top, float right, float bottom,
                           Tutorial tour, int hands, boolean bold, boolean calm, long now) {
        float width = right - left;
        float titleSize = draw.fit(tour.title(), Theme.textSize(Theme.HEADING), width, true,
                Theme.textSize(Theme.MIN_PROSE_SP));
        float bodySize = Theme.textSize(Theme.BODY);
        String body = tour.body(hands);
        String prompt = tour.prompt(hands);
        String[] controls = tour.step == Tutorial.STEP_HANDS ? Tutorial.controls(hands) : null;

        // Fit everything in the column: shrink the body a step at a time if it must.
        String[] lines;
        String[] promptLines;
        float promptSize;
        float total;
        do {
            promptSize = bodySize;
            lines = body.isEmpty() ? new String[0] : wrap(body, bodySize, width, false);
            promptLines = prompt.isEmpty() ? new String[0]
                    : wrap(prompt, promptSize, width, true);
            total = titleSize * 1.25f + lines.length * bodySize * 1.3f
                    + (promptLines.length > 0 ? bodySize * .6f + promptLines.length * promptSize * 1.3f : 0);
            if (controls != null) {
                for (String line : controls) {
                    total += wrap(line, bodySize, width - bodySize, false).length * bodySize * 1.3f
                            + bodySize * .35f;
                }
            }
            if (total <= bottom - top || bodySize <= Theme.scale(15)) {
                break;
            }
            bodySize *= .94f;
        } while (true);

        float y = top + Math.max(0, (bottom - top - total) / 2) + titleSize * .85f;
        draw.shadowedText(canvas, tour.title(), left, y, titleSize, Theme.CREAM,
                Paint.Align.LEFT, true);
        y += titleSize * .40f;
        int ink = Draw.withAlpha(Theme.CREAM, bold ? 255 : 232);
        for (String line : lines) {
            y += bodySize * 1.3f;
            draw.text(canvas, line, left, y - bodySize * .3f, bodySize, ink, Paint.Align.LEFT,
                    false);
        }
        if (controls != null) {
            for (String line : controls) {
                String[] wrapped = wrap(line, bodySize, width - bodySize, false);
                y += bodySize * .35f;
                draw.heart(canvas, left + bodySize * .3f, y + bodySize * .62f, bodySize * .42f,
                        Theme.PINK);
                for (String part : wrapped) {
                    y += bodySize * 1.3f;
                    draw.text(canvas, part, left + bodySize, y - bodySize * .3f, bodySize, ink,
                            Paint.Align.LEFT, false);
                }
            }
        }
        if (promptLines.length > 0) {
            y += bodySize * .6f;
            boolean waiting = tour.awaitingAction();
            float beat = calm || !waiting ? 1f : .75f + .25f * (float) Math.abs(Math.sin(now / 700.0));
            int color = waiting ? Draw.withAlpha(Theme.GOLD, (int) (255 * beat))
                    : Theme.PINK_LIGHT;
            for (String line : promptLines) {
                y += promptSize * 1.3f;
                draw.text(canvas, line, left, y - promptSize * .3f, promptSize, color,
                        Paint.Align.LEFT, true);
            }
        }
    }

    // ---- The little board ------------------------------------------------------------

    private void drawBoard(Canvas canvas, float left, float top, float right, float bottom,
                           Tutorial tour, boolean bold, boolean calm, long now) {
        float w = right - left;
        float h = bottom - top;
        float cell = Math.min(w / 7.6f, h / 7.0f);
        float clueW = cell * 2.1f;
        float clueH = cell * 1.7f;
        float boardW = clueW + cell * Tutorial.SIZE;
        float boardH = clueH + cell * Tutorial.SIZE;
        float cardPad = cell * .35f;
        float gx = left + (w - boardW) / 2 + clueW;
        float gy = top + (h - boardH) / 2 + clueH;
        float cardL = gx - clueW - cardPad;
        float cardT = gy - clueH - cardPad;
        float cardR = gx + cell * Tutorial.SIZE + cardPad;
        float cardB = gy + cell * Tutorial.SIZE + cardPad;

        byte[][] marks = tour.board(now, calm);
        boolean solved = true;
        for (int r = 0; r < Tutorial.SIZE; r++) {
            solved &= Tutorial.rowDone(marks, r);
        }
        if (solved && tour.step == Tutorial.STEP_SOLVED) {
            float beat = calm ? .6f : (float) Math.abs(Math.sin(now / 800.0));
            float glow = cell * (.25f + .15f * beat);
            draw.roundRect(canvas, cardL - glow, cardT - glow, cardR + glow, cardB + glow,
                    Theme.scale(Theme.RADIUS_CARD) + glow,
                    Draw.withAlpha(Theme.PINK, (int) (60 + 50 * beat)));
        }
        draw.shadow(canvas, cardL, cardT, cardR, cardB, Theme.scale(Theme.RADIUS_CARD),
                Theme.scale(6));
        draw.roundRect(canvas, cardL, cardT, cardR, cardB, Theme.scale(Theme.RADIUS_CARD),
                Theme.PAPER);

        // The row the words are about, washed pink across its clue and its squares.
        int focusRow = tour.focusRow();
        if (focusRow >= 0) {
            draw.roundRect(canvas, gx - clueW, gy + focusRow * cell, gx + cell * Tutorial.SIZE,
                    gy + (focusRow + 1) * cell, cell * .2f, Draw.withAlpha(Theme.PINK, 70));
        }

        // Clues.
        float clueSize = cell * .50f;
        for (int r = 0; r < Tutorial.SIZE; r++) {
            boolean done = Tutorial.rowDone(marks, r);
            String clue = join(Tutorial.rowClues(r));
            float cy = gy + r * cell + cell / 2;
            draw.text(canvas, clue, gx - cell * .48f, cy + draw.capCentreOffset(clueSize),
                    clueSize, done ? Theme.RULE_MINOR : Theme.INK, Paint.Align.RIGHT, true);
            if (done) {
                drawTick(canvas, gx - cell * .24f, cy, cell * .11f);
            }
        }
        for (int c = 0; c < Tutorial.SIZE; c++) {
            boolean done = Tutorial.colDone(marks, c);
            String clue = join(Tutorial.colClues(c));
            float cx = gx + c * cell + cell / 2;
            draw.text(canvas, clue, cx, gy - cell * .35f, clueSize,
                    done ? Theme.RULE_MINOR : Theme.INK, Paint.Align.CENTER, true);
        }

        // Squares.
        for (int r = 0; r < Tutorial.SIZE; r++) {
            for (int c = 0; c < Tutorial.SIZE; c++) {
                float l = gx + c * cell;
                float t = gy + r * cell;
                draw.roundRect(canvas, l, t, l + cell, t + cell, 0, Theme.RULE_MINOR);
                draw.roundRect(canvas, l + Theme.keyline(), t + Theme.keyline(),
                        l + cell - Theme.keyline(), t + cell - Theme.keyline(), 0,
                        r == focusRow ? Draw.blend(Theme.PAPER, Theme.PINK_LIGHT, .35f)
                                : Theme.PAPER);
                drawMark(canvas, l, t, cell, marks[r][c], bold);
            }
        }
        draw.roundRectStroke(canvas, gx, gy, gx + cell * Tutorial.SIZE,
                gy + cell * Tutorial.SIZE, 0, Theme.hairline(), Theme.RULE_MAJOR);

        // The glowing square, with Rose's ring around it.
        int[] target = tour.target();
        if (target != null && !tour.acted) {
            float l = gx + target[1] * cell;
            float t = gy + target[0] * cell;
            float beat = calm ? .6f : (float) Math.abs(Math.sin(now / 600.0));
            float glow = cell * (.10f + .14f * beat);
            draw.roundRect(canvas, l - glow, t - glow, l + cell + glow, t + cell + glow,
                    cell * .3f, Draw.withAlpha(Theme.GOLD, (int) (90 + 90 * beat)));
            draw.roundRect(canvas, l + Theme.keyline(), t + Theme.keyline(),
                    l + cell - Theme.keyline(), t + cell - Theme.keyline(), 0,
                    Draw.blend(Theme.PAPER, Theme.GOLD, .35f));
            drawMark(canvas, l, t, cell, marks[target[0]][target[1]], bold);
            drawCursor(canvas, l, t, cell, 0, bold);
            targetRect[0] = l;
            targetRect[1] = t;
            targetRect[2] = l + cell;
            targetRect[3] = t + cell;
            targetDrawn = true;
        } else {
            targetDrawn = false;
        }

        // Cursors walking the finished picture: Rose on the controls page, both of them
        // when the page is about playing together.
        if (tour.step == Tutorial.STEP_HANDS || tour.step == Tutorial.STEP_TOGETHER) {
            long t = calm ? 0 : Math.max(0, now - tour.stepAt);
            int[][] rosePath = {{2, 1}, {2, 2}, {1, 2}, {1, 1}};
            int[][] skyPath = {{3, 3}, {3, 2}, {2, 3}, {2, 4}};
            int[] rose = rosePath[(int) ((t / 900) % rosePath.length)];
            drawCursor(canvas, gx + rose[1] * cell, gy + rose[0] * cell, cell, 0, bold);
            if (tour.step == Tutorial.STEP_TOGETHER) {
                int[] sky = skyPath[(int) (((t + 450) / 900) % skyPath.length)];
                drawCursor(canvas, gx + sky[1] * cell, gy + sky[0] * cell, cell, 1, bold);
            }
        }
    }

    private void drawMark(Canvas canvas, float l, float t, float cell, byte mark,
                          boolean bold) {
        float inset = Math.max(1f, cell * .07f);
        if (mark == Puzzle.FILLED) {
            draw.roundRect(canvas, l + inset, t + inset, l + cell - inset, t + cell - inset,
                    cell * .16f, Theme.tile(bold));
        } else if (mark == Puzzle.CROSSED) {
            Paint paint = draw.paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(2f, cell * .08f));
            paint.setColor(bold ? Theme.INK : Theme.CROSS_TINT);
            float reach = cell * .22f;
            float cx = l + cell / 2;
            float cy = t + cell / 2;
            canvas.drawLine(cx - reach, cy - reach, cx + reach, cy + reach, paint);
            canvas.drawLine(cx + reach, cy - reach, cx - reach, cy + reach, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawTick(Canvas canvas, float cx, float cy, float size) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(1.5f, size * .35f));
        paint.setColor(Theme.PINK_DARK);
        canvas.drawLine(cx - size, cy, cx - size * .3f, cy + size * .7f, paint);
        canvas.drawLine(cx - size * .3f, cy + size * .7f, cx + size, cy - size * .8f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    /** A player's ring around a square and the letter chip at its corner. */
    private void drawCursor(Canvas canvas, float l, float t, float cell, int player,
                            boolean bold) {
        int color = player == 0 ? Theme.PINK : Comfort.skyColor();
        float ring = Math.max(2.5f, cell * (Comfort.get().boldCursor ? .14f : .09f));
        draw.roundRectStroke(canvas, l - ring / 2, t - ring / 2, l + cell + ring / 2,
                t + cell + ring / 2, cell * .18f, ring, color);
        float r = cell * .24f;
        float cx = player == 0 ? l : l + cell;
        float cy = t;
        draw.circle(canvas, cx, cy, r, color);
        draw.circleStroke(canvas, cx, cy, r, Math.max(1f, r * .14f), Theme.CREAM);
        float size = r * 1.15f;
        draw.text(canvas, player == 0 ? "R" : "S", cx, cy + draw.capCentreOffset(size), size,
                Theme.textOn(color), Paint.Align.CENTER, true);
    }

    /** The last step's picture: the two rows of the title screen it is pointing at. */
    private void drawWhereNext(Canvas canvas, float left, float top, float right, float bottom,
                               boolean bold) {
        float h = bottom - top;
        float rowH = Math.min(h / 3.2f, Theme.scale(110));
        float gap = rowH * .35f;
        float y = top + (h - rowH * 2 - gap) / 2;
        String[][] rows = {{"Story Book", "24 handmade chapters"},
                {"Cozy Corner", "Comfort, sound, and how to play"}};
        for (int i = 0; i < 2; i++) {
            boolean lit = i == 0;
            float radius = rowH * .3f;
            if (lit) {
                draw.roundRect(canvas, left, y, right, y + rowH, radius, Theme.GOLD);
            } else {
                draw.roundRect(canvas, left, y, right, y + rowH, radius,
                        Draw.withAlpha(Theme.ROW_REST, bold ? 110 : 70));
                draw.roundRectStroke(canvas, left, y, right, y + rowH, radius,
                        Theme.keyline(), Draw.withAlpha(Theme.ROW_REST_STROKE, 110));
            }
            float pad = rowH * .28f;
            float head = draw.fit(rows[i][0], rowH * .36f, right - left - pad * 2 - rowH * .5f,
                    true, Theme.scale(14));
            float sub = draw.fit(rows[i][1], rowH * .24f, right - left - pad * 2, false,
                    Theme.scale(12));
            int ink = lit ? Theme.textOn(Theme.GOLD) : Theme.CREAM;
            draw.text(canvas, rows[i][0], left + pad, y + rowH * .47f, head, ink,
                    Paint.Align.LEFT, true);
            draw.text(canvas, rows[i][1], left + pad, y + rowH * .80f, sub,
                    lit ? Draw.withAlpha(ink, 200) : Theme.secondaryText(bold),
                    Paint.Align.LEFT, false);
            draw.heart(canvas, right - pad - rowH * .1f, y + rowH * .42f, rowH * .22f,
                    lit ? Theme.PINK_DARK : Theme.PINK);
            y += rowH + gap;
        }
    }

    private static String join(int[] clues) {
        StringBuilder out = new StringBuilder();
        for (int clue : clues) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(clue);
        }
        return out.toString();
    }

    private String[] wrap(String text, float size, float width, boolean strong) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String tried = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && draw.measure(tried, size, strong) > width) {
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

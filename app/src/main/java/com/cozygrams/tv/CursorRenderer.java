package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * The two player cursors, Rose and Sky.
 *
 * <p>Everything here serves one measurement, and it is a measurement about <em>both</em>
 * players: <em>can each of them find their own square in well under a second, from the
 * sofa, without moving it — and equally?</em> The second half of that used to fail badly.
 * A filled tile was Rose's own pink, so her cursor core sat at 1.30:1 on the field she had
 * to find it against, the same hue at the same value, rescued only by two pixels of cream
 * casing; Sky's cyan was hue-opposite and popped instantly. The fix was not in this file:
 * it was {@link Theme#TILE}, which took the board off the identity palette so that
 * saturated pink exists on it only as Rose.
 *
 * <p>What is here is the other half of that fix. The cursor is no longer a stack of
 * outlines that differ from the board by degree; it is a square that differs in kind:
 *
 * <ul>
 *   <li>A hard-edged <b>plate</b> — cream, with a dark outer edge, reaching just past the
 *       square — which replaces whatever the cell was and holds the cell's own mark,
 *       redrawn smaller inside it. Drawn by {@link BoardRenderer#plateHalf} and friends,
 *       because it lives underneath the mark. This is the cue that carries findability, and
 *       it is <em>identical</em> for both players: same shape, same size, same contrast,
 *       9.5:1 on a field of tiles and a hard dark edge on clean paper.</li>
 *   <li>A solid <b>player-colour border</b> stroked around that plate, between two dark
 *       keylines so it holds its edge whatever it is over. This says whose cursor it is —
 *       which is all an identity colour should ever have to do.</li>
 *   <li>A <b>crosshair rail</b> along the four boundaries of the cursor's own row and
 *       column, in the player's colour. {@code BoardRenderer} washes the row and the column
 *       underneath the marks, which is the only order that leaves a tile its own colour and
 *       therefore the order in which a filled tile paints the wash out completely. A cell
 *       boundary carries no mark ink, so the rail is the part of the crosshair the picture
 *       cannot erase. See {@link #drawRails}.</li>
 *   <li><b>Margin tabs</b> just outside the paper card, on the cursor's row and its column.
 *       Always {@link #TAB_WIDTH} design pixels wide, always against the dark backdrop,
 *       never on top of anything. Two of them cross at your square, and they are the cue
 *       that owes nothing at all to the size of the board — which is why they are also
 *       where the arrival feedback goes.</li>
 *   <li>A <b>named badge</b>, R or S, sitting just <em>outside</em> the corner facing away
 *       from the nearest board edge — outside, so the border stays an unbroken rectangle.
 *       It used to be drawn on top of the frame's top-left bracket, which turned a centred
 *       reticle into an asymmetric L pointing at the wrong thing.</li>
 * </ul>
 *
 * <p>The oversized finder reticle this class used to draw is gone. Its half-width was
 * floored in screen pixels, so at 20x20 it enclosed about 2.3 by 2.3 cells: it answered
 * "roughly where" when the question is always "which square". Reaching across the room is
 * the margin tabs' job, and they were already doing it.
 *
 * <p>So is the breathing halo, and for a harder reason: it was three stroked rings at alpha
 * 8, 12 and 16, which lifted a plum tile from (88,54,78) to at most (100,59,82) — 1.05:1,
 * well under the threshold at which a wash is a thing anybody sees. It was also the only
 * part of a cursor that was never still, so {@code Renderer.animating} repainted the whole
 * 1920x1080 canvas sixty times a second all evening to animate it. Lifting the plate off
 * the paper is now the plate's own drop shadow, which is a real one.
 *
 * <p>Every identity cue above is exactly as loud standing still as it is in flight. The one
 * thing that moves is arrival — the landing squash and the tab flash timed by
 * {@link Theme#CURSOR_LAND_MS} — and under {@link Comfort#calmMotion} that is pinned off
 * too, leaving a cursor that is complete without a single animated pixel.
 */
public final class CursorRenderer {

    /** How long the fade takes once a player has gone quiet, so it is never a blink. */
    private static final long REST_FADE_MS = 1400;

    /** Margin tabs: fixed width, and never longer than the line they are naming. */
    private static final float TAB_WIDTH = 12f;
    private static final float TAB_MAX_LENGTH = 34f;
    /** The contrast a tab's fill must reach against the backdrop it floats on. */
    private static final double TAB_RATIO = 4.0;
    /**
     * How far a tab leads its cursor into the direction of travel, in design pixels — 12 px
     * at 1080p. Flat rather than a fraction of a cell, for the same reason the tab's own
     * length has a ceiling in design pixels: this is the one cue whose size owes nothing to
     * the board, and an anticipation that shrank with the squares would be least visible on
     * the board with the most travelling to do.
     */
    private static final float TAB_LEAN = 8f;

    /** How much over-size the player-colour border arrives at before it settles. */
    private static final float LAND_SQUASH = .06f;

    /** Opacity of the crosshair rail. Saturated, because it is a line and lines are thin. */
    private static final int RAIL_ALPHA = 210;

    /**
     * Cubic control distance for a quarter turn, as a fraction of the corner radius — the
     * usual .5523 — and the two numbers that split one of those quarters in half: the
     * control point sits {@code radius * tan(22.5°)} back along the edge, and the arc's own
     * midpoint {@code radius * (1 - 1/√2)} in from the corner on both axes. A quadratic
     * through that control bulges 0.3% of the radius past the circle, which is 0.07 px on
     * the largest corner this file draws.
     */
    private static final float ARC_K = .5523f;
    private static final float HALF_ARC_IN = .41421f;
    private static final float HALF_ARC_MID = .29289f;

    /**
     * The band of clear screen the column tabs need immediately above the paper card.
     *
     * <p>{@link BoardLayout#headerHeight()} adds this to the title strip so the band
     * belongs to the tabs alone. Without it the tab is anchored to the card's top edge
     * with no knowledge of the header's rect, so a saturated pill and its shadow paint
     * straight through the title whenever the active column happens to sit under it — a
     * moving bar scribbling across "ENDLESS #7 · 20 × 20" for the whole game.
     *
     * <p>Covers the widest the tab can be (bold cursors), the second lane a doubled-up
     * tab uses, and the gap to the card.
     *
     * <p>It used to reserve {@code Theme.scale(4)} for the pill's shadow as well. A shadow
     * is cast <em>downward</em>, onto the paper card the tab is pointing at, so that reserve
     * was buying clear backdrop above a shadow that never goes there — 6 px at 1080p taken
     * off the top of every board in the game, on the one axis the grid is short of.
     */
    public static float marginBandHeight() {
        float width = Theme.scale(TAB_WIDTH) * 1.3f;
        float lane = width + Theme.scale(4);
        return width + lane + Theme.scale(5);
    }

    /**
     * Whether any cursor still has a frame left to draw.
     *
     * <p>{@code Renderer.animating} answers this with {@code !calmMotion} — always true for
     * anyone who has not turned calm motion on — so the whole canvas, four hundred cells and
     * four hundred clue glyphs are repainted sixty times a second, all evening, to drive a
     * halo that measured 1.05:1 and no longer exists. Everything that still moves here runs
     * off a clock that ends: the landing squash and the tab flash
     * ({@link Theme#CURSOR_LAND_MS}), a player settling down ({@link #REST_FADE_MS} after
     * {@link Theme#IDLE_HUSH_START_MS}), and the hearts, which beat only while the two
     * cursors are together.
     *
     * <p>Under-reporting is much the worse mistake: a cue that is drawn but not driven
     * freezes mid-phase, which reads as a fault rather than as calm. So this is deliberately
     * generous in two places. A resting player keeps asking for frames for the whole 25 s
     * before the fade starts, not only for the 1.4 s of the fade itself — nothing can wake
     * the view up at the 25 s mark once it has stopped, so the alternative is a cursor that
     * never dims. And "together" is measured at a cell and a half rather than at one, which
     * covers the heart through the whole of the glide that brings it on.
     *
     * <p>The one thing it does <em>not</em> report is a landing under
     * {@link Comfort#calmMotion}, because there is nothing to draw: the squash is pinned to
     * 1 and the tabs' lean to 0. The glide itself is still covered, by
     * {@link UiState#cursorsSettling}, which {@code Renderer.animating} asks first.
     *
     * <p><b>Not called yet.</b> {@code Renderer.animating} still ends its GAME branch with
     * {@code return !Comfort.get().calmMotion}, so nothing idles until it changes over.
     */
    public static boolean needsFrames(UiState ui, long now) {
        boolean lively = !Comfort.get().calmMotion;
        int players = ui.joined[1] ? 2 : 1;
        for (int player = 0; player < players; player++) {
            if (settlingDown(ui, player, now)
                    || (lively && ui.landing(player, now) < 1f)) {
                return true;
            }
        }
        return players == 2 && lively && together(ui);
    }

    /** Whether this player's cues are still on their way down to the resting state. */
    private static boolean settlingDown(UiState ui, int player, long now) {
        long last = ui.lastActive[player];
        return last > 0 && now - last < Theme.IDLE_HUSH_START_MS + REST_FADE_MS;
    }

    /** Whether the two cursors are close enough for one of the hearts to be beating. */
    private static boolean together(UiState ui) {
        return Math.abs(ui.cursorDrawX[0] - ui.cursorDrawX[1]) <= 1.5f
                && Math.abs(ui.cursorDrawY[0] - ui.cursorDrawY[1]) <= 1.5f;
    }

    private final Draw draw;
    /** Reused so a frame never allocates. Index is the player slot. */
    private final Cursor[] cursors = {new Cursor(), new Cursor()};
    /** Scratch path for the margin tab pointers and the shared border, reused likewise. */
    private final Path scratchPath = new Path();
    /**
     * The last tab fill worked out for each player, and the identity colour it came from.
     * {@link Theme#readableOn} walks a ratio search over real WCAG luminance — a couple of
     * hundred {@code Math.pow} calls for an answer that only changes when a comfort option
     * is toggled. Keying on the source colour is what makes the cache notice that.
     */
    private final int[] tabSource = new int[2];
    private final int[] tabInk = new int[2];

    public CursorRenderer(Draw draw) {
        this.draw = draw;
    }

    public void draw(Canvas canvas, BoardLayout board, GameState game, UiState ui,
                     long now) {
        Cursor rose = cursors[0];
        Cursor sky = cursors[1];
        boolean both = ui.joined[1];

        measure(rose, board, ui, 0, now);
        if (!both) {
            placeBadge(rose, null, board.size);
            drawRails(canvas, board, rose, null, false, false);
            drawMargins(canvas, board, rose, false, false);
            drawCursor(canvas, board, rose);
            return;
        }
        measure(sky, board, ui, 1, now);
        placeBadge(rose, sky, board.size);
        placeBadge(sky, rose, board.size);

        // Measured on the drawn positions, and with the same half-square tolerance
        // BoardRenderer splits its crosshair bands on, so the rail and the band it sits
        // either side of always agree about who is sharing a line with whom.
        boolean sharedRow = BoardRenderer.sameLine(ui.cursorDrawY[0], ui.cursorDrawY[1]);
        boolean sharedCol = BoardRenderer.sameLine(ui.cursorDrawX[0], ui.cursorDrawX[1]);
        drawRails(canvas, board, rose, sky, sharedRow, sharedCol);

        // Sky's tabs step one lane further out whenever the two would otherwise touch,
        // rather than only when the cursors share a line: at 20x20 the pitch is 31.7 px, so
        // two tabs on neighbouring columns fused into a single two-tone lozenge — the exact
        // picture that elsewhere means "we are on the same square".
        float touching = tabLength(board.cell) + Theme.scale(6);
        boolean rowLane = Math.abs(rose.cy - sky.cy) < touching;
        boolean colLane = Math.abs(rose.cx - sky.cx) < touching;
        drawMargins(canvas, board, rose, false, false);
        drawMargins(canvas, board, sky, rowLane, colLane);

        if (rose.row == sky.row && rose.col == sky.col) {
            drawSharedSquare(canvas, board, rose, sky, now);
            return;
        }
        // Whoever played most recently is drawn last, so the player who is actually
        // moving is never buried under a partner who is sitting still.
        boolean roseOnTop = ui.lastActive[0] >= ui.lastActive[1];
        drawCursor(canvas, board, roseOnTop ? sky : rose);
        drawCursor(canvas, board, roseOnTop ? rose : sky);
        boolean sideBySide = Math.abs(rose.col - sky.col) <= 1
                && Math.abs(rose.row - sky.row) <= 1;
        if (sideBySide) {
            drawTetherHeart(canvas, board, rose, sky);
        }
    }

    /**
     * Which corner a badge sits on. The board edges decide first — a badge must never end
     * up in a clue gutter — and after that it leans away from the other player, so two
     * cursors working the same little region never cover each other's name.
     */
    private static void placeBadge(Cursor c, Cursor other, int size) {
        boolean right = false;
        boolean bottom = false;
        if (other != null && Math.abs(other.col - c.col) <= 2
                && Math.abs(other.row - c.row) <= 2) {
            right = other.col < c.col;
            bottom = other.row < c.row;
        }
        if (c.col == 0) {
            right = true;
        } else if (c.col >= size - 1) {
            right = false;
        }
        if (c.row == 0) {
            bottom = true;
        } else if (c.row >= size - 1) {
            bottom = false;
        }
        c.badgeRight = right;
        c.badgeBottom = bottom;
    }

    // ---- Geometry --------------------------------------------------------------------

    /** Everything one cursor needs for a frame, worked out once. */
    private static final class Cursor {
        int player;
        int color;
        /** The same colour lifted until it reads on the backdrop the tabs float on. */
        int tab;
        int col;
        int row;
        float cx;
        float cy;
        /** Outer half-width of the plate this cursor is standing on. */
        float plate;
        /** Half-width of the ring the player-colour border is stroked along. */
        float ring;
        float radius;
        float stroke;
        float badge;
        /** 1 while the player is playing, easing to 0 once they have gone quiet. */
        float strength;
        /** 0 the instant this cursor moved, 1 once it has finished arriving. */
        float land;
        /** Which way it is travelling, -1/0/1 per axis, for the tabs' lean. */
        float headingX;
        float headingY;
        /** True when Sky must be told apart without relying on colour. */
        boolean dashed;
        boolean badgeRight;
        boolean badgeBottom;
    }

    private void measure(Cursor c, BoardLayout board, UiState ui, int player, long now) {
        Comfort comfort = Comfort.get();
        float cell = board.cell;
        boolean bold = comfort.boldCursor;

        c.player = player;
        c.color = Theme.playerColor(player);
        c.tab = tabColor(player, c.color);
        c.dashed = comfort.distinctPlayers && player == 1;
        c.strength = liveliness(ui, player, now);
        c.land = ui.landing(player, now);
        c.headingX = ui.cursorHeadingX[player];
        c.headingY = ui.cursorHeadingY[player];

        c.col = Math.round(ui.cursorDrawX[player]);
        c.row = Math.round(ui.cursorDrawY[player]);
        c.cx = board.left + (ui.cursorDrawX[player] + .5f) * cell;
        c.cy = board.top + (ui.cursorDrawY[player] + .5f) * cell;

        // The landing squash. {@code cursorMovedAt} was added to time this and had no
        // reader anywhere, so a move arrived with no feedback at all: the plate slid in and
        // simply stopped, which is why a 92 ms glide still read as soft rather than as
        // quick. Only the border squashes — the plate is BoardRenderer's, its hard edge is
        // the whole findability argument, and an edge that pulses smears itself.
        float settle = comfort.calmMotion ? 1f
                : 1f + LAND_SQUASH * (1f - Draw.springy(c.land));
        c.plate = BoardRenderer.plateHalf(cell);
        c.ring = BoardRenderer.plateRing(cell) * settle;
        c.stroke = BoardRenderer.plateBorder(cell);
        c.radius = Math.max(2f, cell * .17f) * (c.ring / c.plate);
        // 21.75 px of radius, so the letter inside is 19.0 px of cap height — 13.7
        // arcminutes from ten feet, just over Theme.MIN_READABLE_SP, which is this palette's
        // own floor for a single isolated glyph. It was 16.5 px at 10x10, 15x15 and 20x20
        // alike (the cell term never won), giving 10.4 arcminutes: a badge that broke the
        // rule the rest of the game is held to, at every size anyone actually plays.
        c.badge = Math.max(Theme.scale(bold ? 16f : 14.5f),
                Math.min(Theme.scale(bold ? 24f : 20f), cell * .30f));
    }

    /**
     * How present a cursor should feel. A player who has not touched a control for a while
     * recedes — but only in the soft cues: the plate and its border stay at full strength,
     * because a resting cursor still has to be findable.
     *
     * <p>The wait was six seconds, which in a nonogram is not a pause, it is reading. A
     * 20x20 clue gutter holds two hundred numbers and people work them out for a minute at
     * a time without touching anything, so dimmed was the normal state of a player who was
     * thinking rather than absent. It now runs off {@link Theme#IDLE_HUSH_START_MS}, the
     * same clock the music's fade uses, so the evening quietens as one thing.
     */
    private static float liveliness(UiState ui, int player, long now) {
        long last = ui.lastActive[player];
        long idle = now - last;
        if (last <= 0 || idle <= Theme.IDLE_HUSH_START_MS) {
            return 1f;
        }
        return 1f - Draw.clamp01((idle - Theme.IDLE_HUSH_START_MS) / (float) REST_FADE_MS);
    }

    // ---- The crosshair rail ------------------------------------------------------------

    /**
     * Four lines in the player's colour, on the boundaries of the cursor's own row and
     * column, running the full width of the board and its gutter.
     *
     * <p>{@code BoardRenderer.drawCursorGuides} washes those two lines before the marks are
     * drawn, which is the only order that leaves a placed tile its own colour — and it means
     * a placed tile paints the wash out completely. Measured across Sky's row on a 20x20
     * board mid-game, the pixels under the band read (85,52,75) and (82,50,73): per-square
     * tile grain and nothing else. Blurred to a peripheral proxy, both crosshairs break into
     * disconnected stubs through the picture, and the intersection — the one square the cue
     * exists to point at — sits inside the filled region with no band at all. That is the
     * nearly-solved board, which is exactly when finding your partner across the table
     * matters most.
     *
     * <p>A cell boundary carries no mark ink. A line drawn on one can never mute a tile and
     * can never be muted by one, so the rail is the half of the crosshair that survives the
     * picture and the wash underneath is the half that carries the empty stretches.
     *
     * <p>Both rails step over both plates rather than running under them: this pass draws
     * after the whole board, so a rail through a plate would cut the hard dark edge the
     * cursor's findability rests on. The gap is a cell and a bit wide and reads as the rail
     * arriving at the square rather than as a break in it.
     *
     * <p>On a shared line the two players take one boundary each — Rose the near one, Sky
     * the far one — which is the same half each of them gets from
     * {@link BoardRenderer#stripeFrom} in the band between.
     */
    private void drawRails(Canvas canvas, BoardLayout board, Cursor rose, Cursor sky,
                           boolean sharedRow, boolean sharedCol) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.FILL);
        rails(canvas, paint, board, rose, sky, sharedRow, sharedCol);
        if (sky != null) {
            rails(canvas, paint, board, sky, rose, sharedRow, sharedCol);
        }
    }

    private void rails(Canvas canvas, Paint paint, BoardLayout board, Cursor c, Cursor other,
                       boolean sharedRow, boolean sharedCol) {
        float cell = board.cell;
        float thick = Math.max(1.5f, Theme.scale(Theme.CURSOR_RAIL));
        paint.setColor(Draw.withAlpha(c.color, RAIL_ALPHA));

        // Rose takes the near boundary of a shared line — above on a row, left on a column
        // — because that is the half BoardRenderer.stripeFrom gives her in the band between.
        boolean ownsNear = c.player == 0;
        if (!sharedRow || ownsNear) {
            rail(canvas, paint, c.cy - cell * .5f, thick, board.clueLeft(), board.right,
                    true, c, other);
        }
        if (!sharedRow || !ownsNear) {
            rail(canvas, paint, c.cy + cell * .5f, thick, board.clueLeft(), board.right,
                    true, c, other);
        }
        if (!sharedCol || ownsNear) {
            rail(canvas, paint, c.cx - cell * .5f, thick, board.clueTop(), board.bottom,
                    false, c, other);
        }
        if (!sharedCol || !ownsNear) {
            rail(canvas, paint, c.cx + cell * .5f, thick, board.clueTop(), board.bottom,
                    false, c, other);
        }
    }

    /**
     * One rail, from {@code from} to {@code to}, stepping over each plate that lies across
     * it. The plates are taken in the order they sit along the rail, so the runs between
     * them come out in order without anything being sorted.
     */
    private void rail(Canvas canvas, Paint paint, float fixed, float thick, float from,
                      float to, boolean horizontal, Cursor one, Cursor two) {
        // Snapped to whole pixels for the reason BoardRenderer.rule is: an anti-aliased
        // line on a fractional coordinate spreads its weight over two pixels, and where it
        // lands inside a pixel decides how dark it comes out.
        float near = Math.round(fixed - thick * .5f);
        float wide = Math.max(1f, Math.round(thick));
        Cursor lead = two != null && along(two, horizontal) < along(one, horizontal)
                ? two : one;
        Cursor trail = lead == one ? two : one;

        float at = skipPlate(canvas, paint, near, wide, from, to, horizontal, lead, fixed);
        at = skipPlate(canvas, paint, near, wide, at, to, horizontal, trail, fixed);
        run(canvas, paint, near, wide, at, to, horizontal);
    }

    /** Where a cursor sits along a rail's own axis. */
    private static float along(Cursor c, boolean horizontal) {
        return horizontal ? c.cx : c.cy;
    }

    /**
     * Draws the rail up to {@code c}'s plate and answers where it should pick up again.
     * A plate that is nowhere near this rail, or already behind it, costs nothing.
     */
    private float skipPlate(Canvas canvas, Paint paint, float near, float wide, float at,
                            float limit, boolean horizontal, Cursor c, float fixed) {
        if (c == null || Math.abs(along(c, !horizontal) - fixed) > c.plate) {
            return at;
        }
        float gap = c.plate + Theme.keyline();
        float lo = along(c, horizontal) - gap;
        float hi = along(c, horizontal) + gap;
        if (hi <= at || lo >= limit) {
            return at;
        }
        run(canvas, paint, near, wide, at, Math.min(lo, limit), horizontal);
        return Math.max(at, hi);
    }

    /** One unbroken stretch of rail. The paint must already be filled and coloured. */
    private void run(Canvas canvas, Paint paint, float near, float wide, float from,
                     float to, boolean horizontal) {
        if (to - from < 1f) {
            return;
        }
        if (horizontal) {
            canvas.drawRect(from, near, to, near + wide, paint);
        } else {
            canvas.drawRect(near, from, near + wide, to, paint);
        }
    }

    // ---- One cursor ------------------------------------------------------------------

    private void drawCursor(Canvas canvas, BoardLayout board, Cursor c) {
        drawBorder(canvas, c);
        drawBadge(canvas, board, c, c.player, c.badgeRight, c.badgeBottom);
    }

    /**
     * The solid player-colour border around the plate, with a dark keyline on each side of
     * it.
     *
     * <p>The keylines are what make the two players equal here rather than merely
     * different. Sky's default cyan is only 1.8:1 on cream and Rose's pink 2.4:1 — as
     * <em>identity</em> that is fine, because the hues are opposite and the plate has
     * already answered "where"; as an edge it is not, and a band with no edge reads as a
     * smudge. The keylines belong to neither player, so both borders get exactly the same
     * definition.
     */
    private void drawBorder(Canvas canvas, Cursor c) {
        float l = c.cx - c.ring;
        float t = c.cy - c.ring;
        float r = c.cx + c.ring;
        float b = c.cy + c.ring;
        float keyline = Theme.keyline();
        float inner = c.ring - c.stroke * .5f - keyline * .5f;

        draw.roundRectStroke(canvas, c.cx - inner, c.cy - inner, c.cx + inner,
                c.cy + inner, c.radius * .9f, keyline,
                Draw.withAlpha(Theme.PLATE_INK, 150));

        if (c.dashed) {
            // Both passes share one pitch, so the dark backing lines up under the dashes
            // instead of showing through as a continuous outline.
            float pitch = Math.max(Theme.scale(6), c.stroke * 2.6f);
            dashedRect(canvas, l, t, r, b, c.radius, c.stroke * 1.6f,
                    Draw.withAlpha(Theme.PLATE_INK, 150), pitch);
            dashedRect(canvas, l, t, r, b, c.radius, c.stroke, c.color, pitch);
        } else {
            draw.roundRectStroke(canvas, l, t, r, b, c.radius, c.stroke, c.color);
        }
    }

    /**
     * Sky's border under {@link Comfort#distinctPlayers}: the same rectangle, drawn as
     * dashes. Colour is then never the only thing that tells the two cursors apart, which
     * matters most at exactly the moment the deeper teal is hardest to read.
     */
    private void dashedRect(Canvas canvas, float l, float t, float r, float b,
                            float radius, float width, int color, float pitch) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        dashAlong(canvas, paint, l + radius, t, r - radius, t, pitch);
        dashAlong(canvas, paint, l + radius, b, r - radius, b, pitch);
        dashAlong(canvas, paint, l, t + radius, l, b - radius, pitch);
        dashAlong(canvas, paint, r, t + radius, r, b - radius, pitch);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    /** Walks one edge, laying down evenly spaced dashes with a gap between each. */
    private static void dashAlong(Canvas canvas, Paint paint, float x0, float y0, float x1,
                                  float y1, float pitch) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0) {
            return;
        }
        int count = Math.max(2, Math.round(length / pitch));
        float step = 1f / count;
        for (int i = 0; i < count; i++) {
            float from = (i + .18f) * step;
            float to = (i + .82f) * step;
            canvas.drawLine(x0 + dx * from, y0 + dy * from, x0 + dx * to, y0 + dy * to,
                    paint);
        }
    }

    /**
     * The R / S initial, just outside the corner that faces away from the nearest board
     * edge. It is flipped rather than nudged, so it never lands on the clue gutter, never
     * leaves the card, and never drifts away from the square it belongs to — and it clears
     * the plate entirely, so the border it names stays a complete rectangle.
     */
    private void drawBadge(Canvas canvas, BoardLayout board, Cursor c, int player,
                           boolean right, boolean bottom) {
        float radius = c.badge;
        float reach = c.plate + radius * .72f;
        float cx = c.cx + (right ? reach : -reach);
        float cy = c.cy + (bottom ? reach : -reach);
        // The card is only ever a fallback here: the corner choice above already keeps the
        // badge on the paper, so this can never move a badge far enough to mislead.
        cx = Math.min(Math.max(cx, board.cardLeft() + radius), board.cardRight() - radius);
        cy = Math.min(Math.max(cy, board.cardTop() + radius), board.cardBottom() - radius);

        int alpha = (int) (255 * (.8f + .2f * c.strength));
        if (c.dashed) {
            // Sky's badge is a rounded square to Rose's circle: one more cue that owes
            // nothing to colour.
            float sx = cx + radius * .12f;
            float sy = cy + radius * .18f;
            draw.roundRect(canvas, sx - radius, sy - radius, sx + radius, sy + radius,
                    radius * .42f, Draw.withAlpha(Theme.SHADOW_INK, 95));
            draw.roundRect(canvas, cx - radius, cy - radius, cx + radius, cy + radius,
                    radius * .42f, Draw.withAlpha(c.color, alpha));
            draw.roundRectStroke(canvas, cx - radius, cy - radius, cx + radius,
                    cy + radius, radius * .42f, Math.max(1.5f, radius * .18f),
                    Draw.withAlpha(Theme.CREAM, 220));
        } else {
            draw.circle(canvas, cx + radius * .12f, cy + radius * .18f, radius,
                    Draw.withAlpha(Theme.SHADOW_INK, 95));
            draw.circle(canvas, cx, cy, radius, Draw.withAlpha(c.color, alpha));
            draw.circleStroke(canvas, cx, cy, radius, Math.max(1.5f, radius * .18f),
                    Draw.withAlpha(Theme.CREAM, 220));
        }
        float size = radius * 1.25f;
        draw.text(canvas, player == 0 ? "R" : "S", cx, cy + draw.capCentreOffset(size),
                size, Theme.textOn(c.color), Paint.Align.CENTER, true);
    }

    // ---- Margins ---------------------------------------------------------------------

    /**
     * How long a margin tab is drawn.
     *
     * <p>It was {@code max(cell * .9, 45 px)}, which is wrong at both ends. On a big cell
     * the cell won and there was no limit: a 5x5 tab measured 140 px, a slab rather than a
     * pill. On a small cell the floor won — 45 px against a 39.3 px cell at 15x15 and a
     * 31.7 px one at 20x20, so the tab was 1.42 columns wide while pointing at one column. A
     * cue whose whole job is "this column, not that one" must never be wider than the column
     * it is naming, so the floor became a ceiling: a constant while the squares are large,
     * the square itself once the squares are small, and 51 px at both 5x5 and 10x10 —
     * which is what "the same pill at every board size" was always supposed to mean.
     *
     * <p>The audit that found this proposed paying for the lost length in thickness. That
     * was measured and dropped: the thickness formula it suggested resolves to the width the
     * tab already has at 15x15 and 20x20, because {@code cell * .40} never reaches
     * {@link #TAB_WIDTH} there — so it buys nothing at the two sizes where tabs collide, and
     * {@link #marginBandHeight} would still have had to reserve the worst case, taking 23 px
     * off the top of <em>every</em> board and making the 20x20 cell smaller. What actually
     * stops two tabs fusing is the second lane, which now opens on distance rather than only
     * on a shared line.
     */
    private static float tabLength(float cell) {
        return Math.min(cell * .92f, Theme.scale(TAB_MAX_LENGTH));
    }

    /**
     * Tabs on the backdrop just outside the paper card, level with the cursor's row and
     * its column. They are the one cue whose size owes nothing at all to the board: at
     * 5x5 and at 20x20 they are the same pill in the same place, and because they sit off
     * the card they can be fully saturated without ever competing with a clue or a mark.
     * With the oversized finder frame retired, these are what a player picks up from
     * across the room before their eye has even reached the grid.
     *
     * <p>Which is also why the arrival lands here. A tab leads its cursor {@link #TAB_LEAN}
     * into the direction of travel and eases back over {@link Theme#CURSOR_LAND_MS} —
     * measured at 10x10, that puts it 7 px ahead of where the glide alone would have it one
     * frame after the press. It is the loudest arrival cue the cursor has, because the
     * landing squash on the border can only be worth 6% before it eats the cream margin the
     * plate keeps outside it (at 5x5 the geometric ceiling is 7.6%).
     *
     * <p>Not stretched as well as offset, tempting though that is: a stretched tab is a tab
     * that has stopped naming exactly one line, which is the property {@link #tabLength} was
     * just cut back to protect. And not flashed to full opacity either — that was tried and
     * it is a no-op, because anything that moves a cursor also marks its player active, so
     * {@code strength} is already 1 and the tab is already at full alpha by the time the
     * flash would apply.
     */
    private void drawMargins(Canvas canvas, BoardLayout board, Cursor c, boolean rowLane,
                             boolean colLane) {
        Comfort comfort = Comfort.get();
        float width = Theme.scale(TAB_WIDTH) * (comfort.boldCursor ? 1.3f : 1f);
        float length = tabLength(board.cell);
        float gap = Theme.scale(5);
        float lane = width + Theme.scale(4);
        int alpha = (int) (255 * (.55f + .45f * c.strength));
        // Clamped to under half a square so the lead can never point past the line next
        // door, which only bites at 720p — 12 px is a third of a 20x20 cell at 1080p.
        float lean = comfort.calmMotion ? 0f
                : (1f - c.land) * Math.min(Theme.scale(TAB_LEAN), board.cell * .4f);

        float rowRight = board.cardLeft() - gap - (rowLane ? lane : 0);
        float rowY = c.cy + lean * c.headingY;
        tab(canvas, c, rowRight - width, rowY - length * .5f, rowRight,
                rowY + length * .5f, alpha, true);

        float colBottom = board.cardTop() - gap - (colLane ? lane : 0);
        float colX = c.cx + lean * c.headingX;
        tab(canvas, c, colX - length * .5f, colBottom - width, colX + length * .5f,
                colBottom, alpha, false);
    }

    /**
     * The fill for one player's tabs: their identity colour, lifted until it reads on the
     * dark the backdrop composites to behind the band.
     *
     * <p>Measured against {@link Theme#BACKDROP_REF}, Rose's pink is 5.53:1 and comes back
     * unchanged, and so does Sky's default cyan at 7.27:1. The one that fails is the deep
     * teal {@link Comfort#SKY_ALT} at 1.82:1 — and under that setting Sky's tab is drawn in
     * two pieces as well, losing a further quarter of its ink, so blurred to a peripheral
     * proxy Sky's tabs vanished while Rose's stayed obvious. An equality failure in the one
     * mode that exists to fix an equality failure. Lifted, it reaches 4.15:1.
     *
     * <p>Lifting does spend the lightness gap {@code SKY_ALT} was chosen for: Rose against
     * the lifted teal measures 1.33:1. That is not the loss it looks like, because Rose
     * against Sky's <em>default</em> cyan is 1.31:1 — the tab in colour-blind mode ends up
     * exactly as separable by lightness as the palette the game ships with, and everything
     * it gains over that comes from being segmented rather than solid, which is a cue no
     * amount of colour vision is needed for. Nothing else lifts: the border on the plate
     * stays {@code SKY_ALT}, where it is already 6.91:1 on cream.
     */
    private int tabColor(int player, int color) {
        if (tabSource[player] != color) {
            tabSource[player] = color;
            tabInk[player] = Theme.readableOn(color, Theme.BACKDROP_REF, TAB_RATIO);
        }
        return tabInk[player];
    }

    private void tab(Canvas canvas, Cursor c, float l, float t, float r, float b,
                     int alpha, boolean vertical) {
        float radius = Math.min(r - l, b - t) * .5f;
        int fill = Draw.withAlpha(c.tab, alpha);
        draw.roundRect(canvas, l - 2, t - 1, r + 2, b + 3, radius,
                Draw.withAlpha(Theme.SHADOW_INK, 120));
        // A nub on the inner edge turns the pill into a pointer aimed at the board.
        float nub = radius * 1.15f;
        if (vertical) {
            float mid = (t + b) * .5f;
            triangle(canvas, r + nub, mid, r, mid - nub, r, mid + nub, fill);
        } else {
            float mid = (l + r) * .5f;
            triangle(canvas, mid, b + nub, mid - nub, b, mid + nub, b, fill);
        }
        if (c.dashed) {
            // Segmented for Sky when colour cannot be relied on, matching the dashed ring.
            float length = vertical ? b - t : r - l;
            float piece = length * .38f;
            if (vertical) {
                draw.roundRect(canvas, l, t, r, t + piece, radius, fill);
                draw.roundRect(canvas, l, b - piece, r, b, radius, fill);
            } else {
                draw.roundRect(canvas, l, t, l + piece, b, radius, fill);
                draw.roundRect(canvas, r - piece, t, r, b, radius, fill);
            }
        } else {
            draw.roundRect(canvas, l, t, r, b, radius, fill);
        }
        draw.roundRectStroke(canvas, l, t, r, b, radius, Math.max(1f, Theme.scale(1.6f)),
                Draw.withAlpha(Theme.CREAM, (int) (alpha * .62f)));
    }

    /** A filled triangle, for the pointer on the end of a margin tab. */
    private void triangle(Canvas canvas, float x0, float y0, float x1, float y1, float x2,
                          float y2, int color) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        scratchPath.reset();
        scratchPath.moveTo(x0, y0);
        scratchPath.lineTo(x1, y1);
        scratchPath.lineTo(x2, y2);
        scratchPath.close();
        canvas.drawPath(scratchPath, paint);
    }

    // ---- Playing together ------------------------------------------------------------

    /**
     * Both cursors on one square. There is only one plate, so rather than nesting two
     * borders — illegible the moment a cell is small — the border is split down its
     * diagonal: Rose owns the top and left edges, Sky the bottom and the right. Two badges
     * on opposite corners and a heart finish the reading: we are both here, and here is
     * exactly where.
     */
    private void drawSharedSquare(Canvas canvas, BoardLayout board, Cursor rose, Cursor sky,
                                  long now) {
        drawSplitBorder(canvas, rose, sky);

        // Sky takes the corner across the diagonal from Rose, which is the same diagonal
        // the border is split along. When that corner would fall in a clue gutter the pair
        // mirrors along whichever edge still has room instead.
        boolean skyRight;
        boolean skyBottom;
        if (rose.row == 0) {
            skyBottom = rose.badgeBottom;
            skyRight = !rose.badgeRight;
        } else if (rose.col == 0) {
            skyRight = rose.badgeRight;
            skyBottom = !rose.badgeBottom;
        } else {
            skyRight = !rose.badgeRight;
            skyBottom = !rose.badgeBottom;
        }
        drawBadge(canvas, board, rose, 0, rose.badgeRight, rose.badgeBottom);
        drawBadge(canvas, board, sky, 1, skyRight, skyBottom);
        drawTogetherHeart(canvas, board, rose, now);
    }

    /**
     * The shared border: top and left in Rose's colour, bottom and right in Sky's.
     *
     * <p>It was four straight lines, each begun {@code radius * .55} in from the corner so
     * that the second colour would not cap over the first. That left the two corners nobody
     * was arguing over — Rose's own top-left and Sky's own bottom-right — unpainted, a cream
     * notch 4.2 px wide at 10x10, and the frame read as broken rather than as two players
     * meeting. Each player now owns their diagonal corner outright, and the hand-offs happen
     * half way round the other two, where both tangents point the same way so butt caps meet
     * without either a gap or an overlap.
     */
    private void drawSplitBorder(Canvas canvas, Cursor rose, Cursor sky) {
        // One square, one border, so the more recent landing drives the squash for both.
        Cursor lead = sky.ring > rose.ring ? sky : rose;
        float ring = lead.ring;
        float radius = lead.radius;
        float l = rose.cx - ring;
        float t = rose.cy - ring;
        float r = rose.cx + ring;
        float b = rose.cy + ring;
        float keyline = Theme.keyline();
        float inner = ring - rose.stroke * .5f - keyline * .5f;

        draw.roundRectStroke(canvas, rose.cx - inner, rose.cy - inner, rose.cx + inner,
                rose.cy + inner, radius * .9f, keyline,
                Draw.withAlpha(Theme.PLATE_INK, 150));

        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(rose.stroke);
        paint.setColor(rose.color);
        canvas.drawPath(halfBorder(l, t, r, b, radius), paint);
        paint.setColor(sky.color);
        canvas.drawPath(halfBorder(r, b, l, t, radius), paint);
    }

    /**
     * One player's half of a shared square's border: their own corner whole, the two edges
     * either side of it, and half of each of the corners the other player finishes.
     *
     * <p>Written against a signed corner step, so Sky's half is the same call with the
     * rectangle's opposite corners handed in — the two halves are one shape turned through
     * 180°, and building them from one recipe is what guarantees they meet exactly.
     */
    private Path halfBorder(float l, float t, float r, float b, float radius) {
        float dx = r > l ? radius : -radius;
        float dy = b > t ? radius : -radius;
        scratchPath.reset();
        scratchPath.moveTo(l + HALF_ARC_MID * dx, b - HALF_ARC_MID * dy);
        scratchPath.quadTo(l, b - dy + HALF_ARC_IN * dy, l, b - dy);
        scratchPath.lineTo(l, t + dy);
        scratchPath.cubicTo(l, t + dy - ARC_K * dy, l + dx - ARC_K * dx, t, l + dx, t);
        scratchPath.lineTo(r - dx, t);
        scratchPath.quadTo(r - dx + HALF_ARC_IN * dx, t, r - HALF_ARC_MID * dx,
                t + HALF_ARC_MID * dy);
        return scratchPath;
    }

    /** A shared heart above the square both players chose — a tiny co-op reward. */
    private void drawTogetherHeart(Canvas canvas, BoardLayout board, Cursor c, long now) {
        float beat = Comfort.get().calmMotion ? .5f
                : (float) Math.abs(Math.sin(now / Theme.MOTION_WARM_MS));
        float size = Math.max(board.cell * .26f, Theme.scale(15)) * (.94f + beat * .12f);
        float cy = c.badgeBottom ? c.cy + c.plate + size * 1.05f
                : c.cy - c.plate - size * 1.05f;
        heartOnPlate(canvas, c.cx, cy, size, 235);
    }

    /**
     * A heart on a little plate of its own.
     *
     * <p>It used to be a cream heart with a pink one inside it and nothing underneath, which
     * works on clean paper and fails on the square next door: the heart lands one cell above
     * whichever square the pair are sharing, and after ten minutes most of those squares are
     * crossed out. Composited over an X, all four arms of the dark navy cross came out around
     * the glyph and the whole thing read as a smudge — on the one beat the game calls
     * "Together again ♥".
     *
     * <p>So it gets the same ground every other mark in this file gets: the cursor plate,
     * scaled down. Paper fill, {@link Theme#PLATE_INK} keyline — 17:1 against paper, so the
     * plate's edge is found by value alone whatever it is sitting on — and the heart drawn
     * inside it. The cream outline stays, because the pink still has to separate from the
     * plate it is now on.
     */
    private void heartOnPlate(Canvas canvas, float cx, float cy, float size, int alpha) {
        float radius = size * .96f;
        draw.circle(canvas, cx, cy, radius, Draw.withAlpha(Theme.PAPER, alpha));
        draw.circleStroke(canvas, cx, cy, radius, Theme.keyline(),
                Draw.withAlpha(Theme.PLATE_INK, alpha));
        draw.heart(canvas, cx, cy, size * 1.16f, Draw.withAlpha(Theme.CREAM, alpha));
        draw.heart(canvas, cx, cy, size, Draw.withAlpha(Theme.PINK, alpha));
    }

    /**
     * The heart goes above the pair rather than between them: the two plates meet in the
     * middle, and anything drawn there is lost under them.
     *
     * <p>A soft warm tie used to be drawn between the two centres as well. It was measured
     * and removed: it only fired when the cursors were within one cell, by which point the
     * whole line between them is underneath the two plates — and this pass runs after them,
     * so it painted a 6% grey wash across both, taking two cream plates to (239,228,218).
     * The tie was never once visible as a tie; all it ever did was dirty the thing it was
     * tying.
     */
    private void drawTetherHeart(Canvas canvas, BoardLayout board, Cursor rose,
                                 Cursor sky) {
        float size = Math.max(board.cell * .2f, Theme.scale(11));
        float mx = (rose.cx + sky.cx) * .5f;
        float reach = Math.max(rose.plate, sky.plate) + size * 1.05f;
        float my = Math.min(rose.cy, sky.cy) - reach;
        if (my - size < board.cardTop()) {
            my = Math.max(rose.cy, sky.cy) + reach;
        }
        heartOnPlate(canvas, mx, my, size, 220);
    }
}

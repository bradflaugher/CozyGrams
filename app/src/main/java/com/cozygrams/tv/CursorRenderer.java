package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

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
 *   <li><b>Margin tabs</b> just outside the paper card, on the cursor's row and its column.
 *       Always {@link #TAB_WIDTH} design pixels wide, always against the dark backdrop,
 *       never on top of anything. Two of them cross at your square, and they are the cue
 *       that owes nothing at all to the size of the board.</li>
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
 * <p>None of these cues depends on movement: with {@link Comfort#calmMotion} on, every one
 * of them is exactly as loud standing still as it is in flight.
 */
public final class CursorRenderer {

    /** Period of the idle breathing pulse. */
    private static final float BREATH_MS = 1900f;

    /** A player who has touched nothing for this long lets their cursor settle down. */
    private static final long RESTS_AFTER_MS = 6000;
    /** How long that settling takes, so it is never a blink. */
    private static final long REST_FADE_MS = 1400;

    /** Margin tabs: fixed width, and never shorter than this however small a cell is. */
    private static final float TAB_WIDTH = 12f;
    private static final float TAB_MIN_LENGTH = 30f;

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
     * tab uses, the gap to the card, and the shadow the pill casts.
     */
    public static float marginBandHeight() {
        float width = Theme.scale(TAB_WIDTH) * 1.3f;
        float lane = width + Theme.scale(4);
        return width + lane + Theme.scale(5) + Theme.scale(4);
    }

    private final Draw draw;
    /** Reused so a frame never allocates. Index is the player slot. */
    private final Cursor[] cursors = {new Cursor(), new Cursor()};
    /** Scratch path for the margin tab pointers, reused for the same reason. */
    private final android.graphics.Path nubPath = new android.graphics.Path();

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
            drawMargins(canvas, board, rose, false, false);
            drawCursor(canvas, board, rose);
            return;
        }
        measure(sky, board, ui, 1, now);
        placeBadge(rose, sky, board.size);
        placeBadge(sky, rose, board.size);

        // Sky's tabs step one lane further out when the two share a row or a column, so
        // the pair reads as "we are on the same line" instead of hiding each other.
        boolean sameRow = rose.row == sky.row;
        boolean sameCol = rose.col == sky.col;
        drawMargins(canvas, board, rose, false, false);
        drawMargins(canvas, board, sky, sameRow, sameCol);

        if (sameRow && sameCol) {
            drawSharedSquare(canvas, board, rose, sky, now);
            return;
        }
        boolean sideBySide = Math.abs(rose.col - sky.col) <= 1
                && Math.abs(rose.row - sky.row) <= 1;
        if (sideBySide) {
            drawTetherGlow(canvas, rose, sky);
        }
        // Whoever played most recently is drawn last, so the player who is actually
        // moving is never buried under a partner who is sitting still.
        boolean roseOnTop = ui.lastActive[0] >= ui.lastActive[1];
        drawCursor(canvas, board, roseOnTop ? sky : rose);
        drawCursor(canvas, board, roseOnTop ? rose : sky);
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
        /** -1..1 breathing phase; pinned to 0 under calm motion. */
        float breath;
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
        c.dashed = comfort.distinctPlayers && player == 1;
        c.strength = liveliness(ui, player, now);
        // Only the halo breathes. The border has to stay exactly on the plate that
        // BoardRenderer drew, and a plate that pulsed would smear its own hard edge — the
        // one property the whole cursor now rests on.
        c.breath = comfort.calmMotion ? 0f
                : (float) Math.sin(now / BREATH_MS * Math.PI * 2 + player * 1.7f)
                        * c.strength;

        c.col = Math.round(ui.cursorDrawX[player]);
        c.row = Math.round(ui.cursorDrawY[player]);
        c.cx = board.left + (ui.cursorDrawX[player] + .5f) * cell;
        c.cy = board.top + (ui.cursorDrawY[player] + .5f) * cell;

        c.plate = BoardRenderer.plateHalf(cell);
        c.ring = BoardRenderer.plateRing(cell);
        c.stroke = BoardRenderer.plateBorder(cell);
        c.radius = Math.max(2f, cell * .17f) * (c.ring / c.plate);
        c.badge = Math.max(Theme.scale(bold ? 13 : 11),
                Math.min(Theme.scale(bold ? 20 : 17), cell * .28f));
    }

    /**
     * How present a cursor should feel. A player who has not touched a control for a while
     * recedes — but only in the soft cues: the plate and its border stay at full strength,
     * because a resting cursor still has to be findable.
     */
    private static float liveliness(UiState ui, int player, long now) {
        long last = ui.lastActive[player];
        long idle = now - last;
        if (last <= 0 || idle <= RESTS_AFTER_MS) {
            return 1f;
        }
        return 1f - Draw.clamp01((idle - RESTS_AFTER_MS) / (float) REST_FADE_MS);
    }

    // ---- One cursor ------------------------------------------------------------------

    private void drawCursor(Canvas canvas, BoardLayout board, Cursor c) {
        drawHalo(canvas, c);
        drawBorder(canvas, c);
        drawBadge(canvas, board, c, c.player, c.badgeRight, c.badgeBottom);
    }

    /**
     * A soft outward glow that lifts the plate off whatever it is standing on. It is the
     * only part of a cursor that breathes, and the only part that fades when a player goes
     * quiet — everything load-bearing is drawn at full strength always.
     */
    private void drawHalo(Canvas canvas, Cursor c) {
        float base = c.plate;
        int alpha = (int) (20 * (.4f + .6f * c.strength));
        for (int layer = 3; layer >= 1; layer--) {
            float spread = base * (1.04f + layer * .11f + c.breath * .02f);
            draw.circleStroke(canvas, c.cx, c.cy, spread, base * .24f,
                    Draw.withAlpha(c.color, alpha - layer * 4));
        }
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
        float keyline = Math.max(1f, Theme.scale(1f));
        float inner = c.ring - c.stroke * .5f - keyline * .5f;

        draw.roundRectStroke(canvas, c.cx - inner, c.cy - inner, c.cx + inner,
                c.cy + inner, c.radius * .9f, keyline, Color.argb(150, 30, 20, 46));

        if (c.dashed) {
            // Both passes share one pitch, so the dark backing lines up under the dashes
            // instead of showing through as a continuous outline.
            float pitch = Math.max(Theme.scale(6), c.stroke * 2.6f);
            dashedRect(canvas, l, t, r, b, c.radius, c.stroke * 1.6f,
                    Color.argb(150, 22, 15, 38), pitch);
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
        boolean square = c.dashed;
        if (square) {
            // Sky's badge is a rounded square to Rose's circle: one more cue that owes
            // nothing to colour.
            float sx = cx + radius * .12f;
            float sy = cy + radius * .18f;
            draw.roundRect(canvas, sx - radius, sy - radius, sx + radius, sy + radius,
                    radius * .42f, Color.argb(95, 18, 12, 32));
            draw.roundRect(canvas, cx - radius, cy - radius, cx + radius, cy + radius,
                    radius * .42f, Draw.withAlpha(c.color, alpha));
            draw.roundRectStroke(canvas, cx - radius, cy - radius, cx + radius,
                    cy + radius, radius * .42f, Math.max(1.5f, radius * .18f),
                    Draw.withAlpha(Theme.CREAM, 220));
        } else {
            draw.circle(canvas, cx + radius * .12f, cy + radius * .18f, radius,
                    Color.argb(95, 18, 12, 32));
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
     * Tabs on the backdrop just outside the paper card, level with the cursor's row and
     * its column. They are the one cue whose size owes nothing at all to the board: at
     * 5x5 and at 20x20 they are the same pill in the same place, and because they sit off
     * the card they can be fully saturated without ever competing with a clue or a mark.
     * With the oversized finder frame retired, these are what a player picks up from
     * across the room before their eye has even reached the grid.
     */
    private void drawMargins(Canvas canvas, BoardLayout board, Cursor c, boolean rowLane,
                             boolean colLane) {
        boolean bold = Comfort.get().boldCursor;
        float width = Theme.scale(TAB_WIDTH) * (bold ? 1.3f : 1f);
        float length = Math.max(board.cell * .9f, Theme.scale(TAB_MIN_LENGTH));
        float gap = Theme.scale(5);
        float lane = width + Theme.scale(4);
        int alpha = (int) (255 * (.55f + .45f * c.strength));

        float rowRight = board.cardLeft() - gap - (rowLane ? lane : 0);
        tab(canvas, c, rowRight - width, c.cy - length * .5f, rowRight, c.cy + length * .5f,
                alpha, true);

        float colBottom = board.cardTop() - gap - (colLane ? lane : 0);
        tab(canvas, c, c.cx - length * .5f, colBottom - width, c.cx + length * .5f,
                colBottom, alpha, false);
    }

    private void tab(Canvas canvas, Cursor c, float l, float t, float r, float b,
                     int alpha, boolean vertical) {
        float radius = Math.min(r - l, b - t) * .5f;
        draw.roundRect(canvas, l - 2, t - 1, r + 2, b + 3, radius,
                Color.argb(120, 10, 6, 18));
        // A nub on the inner edge turns the pill into a pointer aimed at the board.
        float nub = radius * 1.15f;
        if (vertical) {
            float mid = (t + b) * .5f;
            triangle(canvas, r + nub, mid, r, mid - nub, r, mid + nub,
                    Draw.withAlpha(c.color, alpha));
        } else {
            float mid = (l + r) * .5f;
            triangle(canvas, mid, b + nub, mid - nub, b, mid + nub, b,
                    Draw.withAlpha(c.color, alpha));
        }
        if (c.dashed) {
            // Segmented for Sky when colour cannot be relied on, matching the dashed ring.
            float length = vertical ? b - t : r - l;
            float piece = length * .38f;
            if (vertical) {
                draw.roundRect(canvas, l, t, r, t + piece, radius,
                        Draw.withAlpha(c.color, alpha));
                draw.roundRect(canvas, l, b - piece, r, b, radius,
                        Draw.withAlpha(c.color, alpha));
            } else {
                draw.roundRect(canvas, l, t, l + piece, b, radius,
                        Draw.withAlpha(c.color, alpha));
                draw.roundRect(canvas, r - piece, t, r, b, radius,
                        Draw.withAlpha(c.color, alpha));
            }
        } else {
            draw.roundRect(canvas, l, t, r, b, radius, Draw.withAlpha(c.color, alpha));
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
        nubPath.reset();
        nubPath.moveTo(x0, y0);
        nubPath.lineTo(x1, y1);
        nubPath.lineTo(x2, y2);
        nubPath.close();
        canvas.drawPath(nubPath, paint);
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
        drawHalo(canvas, rose);
        drawHalo(canvas, sky);
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

    /** The shared border: top and left in Rose's colour, bottom and right in Sky's. */
    private void drawSplitBorder(Canvas canvas, Cursor rose, Cursor sky) {
        float l = rose.cx - rose.ring;
        float t = rose.cy - rose.ring;
        float r = rose.cx + rose.ring;
        float b = rose.cy + rose.ring;
        float core = rose.stroke;
        float corner = rose.radius * .55f;
        float keyline = Math.max(1f, Theme.scale(1f));
        float inner = rose.ring - core * .5f - keyline * .5f;

        draw.roundRectStroke(canvas, rose.cx - inner, rose.cy - inner, rose.cx + inner,
                rose.cy + inner, rose.radius * .9f, keyline, Color.argb(150, 30, 20, 46));

        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(core);
        paint.setColor(rose.color);
        canvas.drawLine(l + corner, t, r, t, paint);
        canvas.drawLine(l, t + corner, l, b, paint);
        paint.setColor(sky.color);
        canvas.drawLine(l, b, r - corner, b, paint);
        canvas.drawLine(r, t, r, b - corner, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    /** A shared heart above the square both players chose — a tiny co-op reward. */
    private void drawTogetherHeart(Canvas canvas, BoardLayout board, Cursor c, long now) {
        float beat = Comfort.get().calmMotion ? .5f
                : (float) Math.abs(Math.sin(now / 620f));
        float size = Math.max(board.cell * .26f, Theme.scale(15)) * (.94f + beat * .12f);
        float cy = c.badgeBottom ? c.cy + c.plate + size * 1.05f
                : c.cy - c.plate - size * 1.05f;
        // Drawn twice: a cream heart a shade larger reads as an outline, so the pink one
        // survives on cream paper as well as on a filled tile.
        draw.heart(canvas, c.cx, cy, size * 1.24f, Draw.withAlpha(Theme.CREAM, 225));
        draw.heart(canvas, c.cx, cy, size, Draw.withAlpha(Theme.PINK, 235));
    }

    /**
     * When the two cursors come to rest side by side, a soft warm tie is drawn between
     * them. It lives on the boundary between the squares, where no mark ever sits, and it
     * never moves on its own — hence no clock: this used to breathe, which under
     * {@code calmMotion} was pinned anyway and otherwise only added a shimmer nobody asked
     * a tether for.
     */
    private void drawTetherGlow(Canvas canvas, Cursor rose, Cursor sky) {
        int color = Draw.blend(rose.color, sky.color, .5f);
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int layer = 3; layer >= 1; layer--) {
            paint.setStrokeWidth(Math.max(Theme.scale(12), rose.plate * layer * .5f));
            paint.setColor(Draw.withAlpha(color, 32 - layer * 6));
            canvas.drawLine(rose.cx, rose.cy, sky.cx, sky.cy, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    /**
     * The heart goes above the pair rather than between them: the two plates meet in the
     * middle, and anything drawn there is lost under them.
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
        draw.heart(canvas, mx, my, size * 1.3f, Draw.withAlpha(Theme.CREAM, 210));
        draw.heart(canvas, mx, my, size, Draw.withAlpha(Theme.PINK, 220));
    }
}

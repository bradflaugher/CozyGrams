package com.cozygrams.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Draws the paper card, the clue gutters, every square, the grid rules and the plate each
 * cursor is standing on.
 *
 * <p>This is the surface players stare at for whole evenings, so it is built around five
 * rules:
 *
 * <ul>
 *   <li><b>A player's colour is never the board's colour.</b> A filled tile is
 *       {@link Theme#TILE}, a deep mulberry plum that belongs to nobody. It used to be a
 *       blend of {@link Theme#PINK_DARK} and {@link Theme#PINK}, which meant Rose spent
 *       every game hunting a pink cursor across a field of pink tiles at 1.30:1 while
 *       Sky's cyan popped off the same field instantly. Reserving the identity colours for
 *       the players is what makes the two cursors equally findable; nothing else can.</li>
 *   <li><b>Clue numbers stay large, readable and never touch.</b> {@link BoardLayout}
 *       reserves one lane per clue group; every number is measured before it is drawn and
 *       its tracking tightened if the live font is wider than the layout assumed. A
 *       <em>solved</em> line's numbers stay at 6.9:1 on paper — players re-read them
 *       constantly to verify a line, so "finished" is carried by the band and the tick,
 *       never by fading the digits toward the paper.</li>
 *   <li><b>The board is countable.</b> The five-square blocking is carried out of the grid
 *       and into the row gutter, the major rules continue through both gutters, and every
 *       other column clue stands on a ground of its own, so "that is the seventh column" is
 *       answerable without touching anything. See {@link #drawClueGrounds}.</li>
 *   <li><b>Filled, crossed and untouched differ in value, not only in hue.</b> A filled
 *       square is a dark tile (9.9:1 on paper), a crossed square is a tinted cell with a
 *       heavy slate X ({@link Theme#CROSS_RATIO} on paper by construction, 8.5:1 against a
 *       tile), an untouched square is clean paper. Greyscale keeps all three apart.</li>
 *   <li><b>Extra contrast changes kind, not amount.</b> See {@link #drawFilled}: the tile
 *       leaves the warm plum family entirely for a near-black slate, the gloss comes off,
 *       the cross deepens to {@link Theme#INK}, the five-square rules thicken and the
 *       solved clues come back up to full ink. The setting used to move a tile by five
 *       percent of luminance, which is nothing from a sofa.</li>
 * </ul>
 *
 * <p>The board keeps no clock of its own: everything here is a function of the state it is
 * handed, so there is no motion for {@code Comfort.calmMotion} to calm that is not already
 * calmed at its source. The one thing that follows a clock is the mark inside a cursor
 * plate, which tracks the cursor's own glide — see {@link #drawCursorPlates}.
 */
public final class BoardRenderer {

    /** Preformatted clue labels, so a full 20x20 frame never allocates a string. */
    private static final String[] NUMBER = numbers(100);
    private static final String[] DIGIT = numbers(10);

    /** Tightest a clue's tracking is ever squeezed. See {@link #drawClueNumber}. */
    private static final float CONDENSE_FLOOR = .86f;

    /**
     * Below this much clear paper between neighbouring column clues, they get a ground to
     * stand on. In design pixels; see {@link #columnCluesCrowd}.
     */
    private static final float CLUE_GAP_MIN = 9f;

    // ---- The crosshair bands ----------------------------------------------------------

    /**
     * How far a band's hue is deepened toward the player's dark shade before it is thinned
     * over the paper. Sky's cyan is very light, so without this the wash needs an alpha
     * near 160 to be seen at all and stops looking like a wash; half-way to
     * {@link Theme#BLUE_DARK} it gets there at 98 instead.
     */
    private static final float BAND_DEEPEN = .5f;
    /** The contrast a band must reach against the paper it lies on. */
    private static final double BAND_RATIO = 1.45;
    /** The same with extra contrast on. */
    private static final double BAND_RATIO_BOLD = 1.80;
    /** However pale an identity colour gets, its band never becomes an opaque stripe. */
    private static final int BAND_ALPHA_CEILING = 190;

    // ---- Squares ------------------------------------------------------------------------

    /**
     * However pale the slate gets, a crossed square never becomes a filled one. The ratio
     * search {@link Theme#washAlpha} runs would happily walk past this if
     * {@link Theme#CROSS_RATIO} were ever set to something a wash cannot reach.
     */
    private static final int CROSS_ALPHA_CEILING = 120;

    /** The crossed-square wash, and the same with extra contrast on. */
    private static final int CROSS_WASH = crossWash(false);
    private static final int CROSS_WASH_BOLD = crossWash(true);

    /**
     * How far one grain step darkens a tile toward {@link Theme#TILE_DEEP}.
     *
     * <p>It was .10, which put the four shades 1.09:1 apart. Grain should be felt rather
     * than counted, and once {@link #grain} stopped laying the four shades out in diagonal
     * stripes the amplitude no longer had to carry the effect on its own.
     */
    private static final float TILE_GRAIN = .07f;

    // ---- The cursor plate --------------------------------------------------------------

    /** How far past its square the plate reaches, in design pixels. */
    private static final float PLATE_OUT = 3.5f;
    /**
     * The plate's hard dark edge, in design pixels.
     *
     * <p>It was 1.2 — 1.8 px at 1080p at every board size, which is 1.3 arcminutes from ten
     * feet, at or below the width at which a line is resolvable at all. The class doc called
     * that "the cue that carries it on clean paper" and on clean paper it was carrying
     * nothing; the coloured border was doing all the work. At 2.2 the edge is 3.3 px, 2.4
     * arcminutes, and it is backed by a soft shadow so the plate reads as a card lying on
     * the paper rather than as a rectangle printed on it.
     */
    private static final float PLATE_EDGE = 2.2f;
    /** How opaque that edge is over whatever the square was. */
    private static final int PLATE_INK_ALPHA = 215;
    /** How far the plate is lifted off the paper, in design pixels of shadow depth. */
    private static final float PLATE_LIFT = 5f;
    /** Thinnest and thickest the player-colour border is ever stroked, in design pixels. */
    private static final float BORDER_FLOOR = 2.6f;
    private static final float BORDER_CEILING = 8f;
    /** Cream left showing outside the border — the part that reads against a dark tile. */
    private static final float PLATE_CREAM_OUT = 2f;
    /** Cream left showing inside it, between the border and the mark. */
    private static final float PLATE_CREAM_IN = .8f;

    private final Draw draw;

    /**
     * The last band worked out for each (player, contrast setting) pair, and the identity
     * colour it was worked out from. {@link Theme#washAlpha} walks a ratio search over real
     * WCAG luminance, which is a few hundred {@code Math.pow} calls — cheap once, silly
     * sixty times a second for an answer that only changes when a comfort option is
     * toggled. Keying on the source colour is what makes the cache notice that.
     */
    private final int[] bandSource = new int[4];
    private final int[] bandWash = new int[4];

    public BoardRenderer(Draw draw) {
        this.draw = draw;
    }

    public void draw(Canvas canvas, BoardLayout board, GameState game, UiState ui,
                     Effects effects, long now) {
        drawCard(canvas, board);
        drawClueGrounds(canvas, board, game, ui);
        // Paper, then guides, then the celebration, then the marks. The guides land between
        // the paper and the marks so they tint the paper without washing over the tiles the
        // players have placed on it; the effects land there too, because a confirmation
        // that buries the thing it is confirming is not a confirmation. A fill's halo
        // contracts *around* its tile, a cross's ticks flick out past its corners, and both
        // stay under every mark and every cursor on the board.
        drawPaper(canvas, board, ui);
        drawCursorGuides(canvas, board, ui);
        effects.drawPulses(canvas, draw, board, now);
        // Sprays land here too, for the same reason the pulses do. Drawn last, in the
        // top-level pass, petals sat on the finished tiles like dirt and spark arms cut
        // across a player's badge — debris on the picture two people are making.
        //
        // The board is named rather than left to the "whichever card I last saw" overload:
        // it is the same board drawPulses was just given, and saying so at the call site is
        // what makes "confined to the paper" readable here instead of two files away.
        effects.drawParticles(canvas, draw, board, now);
        drawMarks(canvas, board, game, ui, effects, now);
        drawGrid(canvas, board, ui);
        drawCursorPlates(canvas, board, game, ui, effects, now);
        drawClues(canvas, board, game, ui);
    }

    /** The warm paper card the whole puzzle sits on. */
    private void drawCard(Canvas canvas, BoardLayout board) {
        float radius = Theme.scale(Theme.RADIUS_CARD);
        draw.shadow(canvas, board.cardLeft(), board.cardTop(), board.cardRight(),
                board.cardBottom(), radius, Theme.scale(9));
        draw.roundRect(canvas, board.cardLeft(), board.cardTop(), board.cardRight(),
                board.cardBottom(), radius, Theme.CREAM);
        draw.roundRectStroke(canvas, board.cardLeft(), board.cardTop(), board.cardRight(),
                board.cardBottom(), radius, Theme.hairline(),
                Color.argb(70, 120, 100, 140));
    }

    /**
     * The ground each clue stands on, so that every number belongs visibly to one line.
     *
     * <p>The two gutters need different answers, because they fail differently.
     *
     * <p><b>Down the side</b>, a row's clues are a whole square's height apart and nothing
     * runs together; what the eye needs is help counting to "the twelfth row", so every
     * second block of five gets a whisper of tint, aligned with the same blocks on the board
     * itself.
     *
     * <p><b>Along the top</b>, that same blocking was drawn and it was answering the wrong
     * question. A column clue has one square's <em>width</em> to live in: at 20x20 on a
     * 1080p panel a two-digit clue drew 26 px inside a 30 px column, leaving 4.2 px of paper
     * between the ink of one number and the ink of the next — 3 arcminutes from ten feet,
     * under what the eye needs to split two digit streams. "3 11 14 18 18 10" read as one
     * number. Half of that is bought back by narrowing the draw box (see
     * {@link BoardLayout#COL_CLUE_FILL}); the rest cannot come out of a board that is
     * already short of room, so it comes out of the ground instead. Every other column's
     * lane is filled with {@link Theme#CLUE_BAND_ALT}, 1.21:1 on paper, and each clue stack
     * then has an edge of its own whatever its neighbour is doing.
     *
     * <p>A column's ground is only as tall as that column's own clue stack, not the whole
     * gutter. Filling the gutter turned the empty half of a 20x20 board's clue band into a
     * field of full-height stripes, which is a lot of ink for a place with no numbers in it;
     * stopping at the top of the stack also says, without any extra marks, how many groups
     * this column is asking for.
     *
     * <p>Drawn before the crosshair bands and long before the solved-line pills, so both of
     * those still win where they land.
     */
    private void drawClueGrounds(Canvas canvas, BoardLayout board, GameState game,
                                 UiState ui) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.FILL);
        boolean bold = ui.highContrastOn;

        if (columnCluesCrowd(board)) {
            paint.setColor(bold ? Draw.blend(Theme.CREAM, Theme.GRID, .13f)
                    : Theme.CLUE_BAND_ALT);
            for (int column = 1; column < board.size; column += 2) {
                float top = board.colClueOuterY(game.puzzle.colClues(column).length - 1);
                canvas.drawRect(board.cellLeft(column), top,
                        board.cellLeft(column) + board.cell, board.top, paint);
            }
        }

        // Half strength down the side: this is the board's own five-square checker carried
        // into the gutter, not a separator, and the row clues are already a whole square
        // apart from each other.
        paint.setColor(bold ? Draw.blend(Theme.CREAM, Theme.GRID, .13f)
                : Draw.blend(Theme.CREAM, Theme.CLUE_BAND_ALT, .45f));
        for (int block = 1; block * 5 < board.size; block += 2) {
            float from = board.cellTop(block * 5);
            float to = Math.min(board.bottom, board.cellTop(block * 5 + 5));
            canvas.drawRect(board.clueLeft(), from, board.left, to, paint);
        }
    }

    /**
     * Whether this board's column clues are close enough together to need a ground.
     *
     * <p>Drawn unconditionally it is ink spent on a problem the board does not have: a 5x5
     * board's single-digit clues sit 87 px apart in 115 px columns, and two tinted lanes in
     * amongst five make those two columns look singled out for a reason nobody can find.
     * The measure is the clear paper between the ink of one clue and the ink of the next,
     * against {@value #CLUE_GAP_MIN} design pixels — about 10 arcminutes from ten feet,
     * roughly twice the gap the eye needs to split two digit streams, so the ground turns up
     * before the merging does rather than after. In practice it appears at 15x15 (8 px of
     * clear paper) and 20x20 (6 px) and stays away at 10x10 (34 px) and 5x5.
     */
    static boolean columnCluesCrowd(BoardLayout board) {
        float widest = Math.min(board.colClueNumberWidth(),
                board.clueTextSize * board.clueDigits * BoardLayout.DIGIT_ADVANCE);
        return board.cell - widest < Theme.scale(CLUE_GAP_MIN);
    }

    /**
     * Soft tinted bands along each cursor's row and column, running the full width of the
     * board and its gutter. This is how a player tracks where their partner is working out
     * of the corner of their eye, and how they read a clue off against its own line.
     *
     * <p>The strength is asked for as a <em>ratio</em>, not set as an alpha. A fixed alpha
     * cannot make the two bands equal: at 40/255 Rose's band measured 1.09:1 against paper
     * and Sky's, being a much lighter hue, measured less still — both below the threshold
     * at which a wash is a thing you can see rather than a thing you can find if told it is
     * there. See {@link #bandColor}.
     *
     * <p>Drawn after the paper and before the marks. Drawn any earlier the opaque paper
     * fill erases the on-board half of every band and only the gutter tint survives; drawn
     * any later the band washes over the filled tiles and mutes them.
     *
     * <p><b>A shared line is split, never composited.</b> Two independent full-width washes
     * on the same row multiply into a mauve that belongs to neither player: measured at
     * 1.95:1 against paper where Rose's band alone is 1.54:1 and Sky's 1.45:1. So at the one
     * moment the colour is most worth having — the two of them working the same line
     * together — it said "somebody is here" instead of "both of us are", and said it 30%
     * louder than either band was designed to. A shared row is now drawn as two half-height
     * stripes, Rose above and Sky below; a shared column as two half-width stripes, Rose
     * left and Sky right. Each keeps its own {@link #bandColor} at its own ratio, nothing
     * composites, and the picture reads as the thing it means. At 20x20 a stripe is 15 px,
     * about 11 arcminutes from ten feet — well clear of resolvable.
     */
    private void drawCursorGuides(Canvas canvas, BoardLayout board, UiState ui) {
        boolean both = ui.joined[1];
        // Tested on the drawn positions rather than the logical ones so the split holds
        // through the ~100 ms glide instead of snapping on halfway across it.
        boolean sameRow = both && sameLine(ui.cursorDrawY[0], ui.cursorDrawY[1]);
        boolean sameCol = both && sameLine(ui.cursorDrawX[0], ui.cursorDrawX[1]);

        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.FILL);
        for (int player = 1; player >= 0; player--) {
            if (player == 1 && !both) {
                continue;
            }
            float rowTop = board.top + ui.cursorDrawY[player] * board.cell;
            float colLeft = board.left + ui.cursorDrawX[player] * board.cell;
            paint.setColor(band(player, ui.highContrastOn));

            canvas.drawRect(board.clueLeft(),
                    rowTop + stripeFrom(sameRow, player) * board.cell, board.right,
                    rowTop + stripeTo(sameRow, player) * board.cell, paint);
            canvas.drawRect(colLeft + stripeFrom(sameCol, player) * board.cell,
                    board.clueTop(),
                    colLeft + stripeTo(sameCol, player) * board.cell, board.bottom, paint);
        }
    }

    /**
     * Where one player's band starts across the width of the line, 0..1: the whole line when
     * they are on it alone, their own half when they are sharing it. Rose takes the near
     * half — above on a row, left on a column — because that is the same diagonal
     * {@code CursorRenderer.drawSplitBorder} gives her on a shared square.
     */
    static float stripeFrom(boolean shared, int player) {
        return shared && player == 1 ? .5f : 0f;
    }

    /** Where it ends. See {@link #stripeFrom}. */
    static float stripeTo(boolean shared, int player) {
        return shared && player == 0 ? .5f : 1f;
    }

    /**
     * Whether two drawn cursor coordinates count as being on the same line.
     *
     * <p>Half a square rather than nothing at all, and measured on the drawn positions, so
     * the split engages as the second cursor comes in and holds as it leaves. Testing the
     * logical squares instead would leave the two full-width washes compositing for the
     * whole of the glide — which is the muddy frame this is here to prevent.
     */
    static boolean sameLine(float first, float second) {
        return Math.abs(first - second) < .5f;
    }

    /** {@link #bandColor} for this player and contrast setting, worked out once. */
    private int band(int player, boolean highContrast) {
        int slot = player * 2 + (highContrast ? 1 : 0);
        int source = Theme.playerColor(player);
        if (bandSource[slot] != source) {
            bandSource[slot] = source;
            bandWash[slot] = bandColor(player,
                    highContrast ? BAND_RATIO_BOLD : BAND_RATIO);
        }
        return bandWash[slot];
    }

    /**
     * One player's crosshair wash: their hue, deepened a little so it has somewhere to go,
     * then thinned to exactly the alpha at which it reaches {@code ratio} against the
     * board's paper. Both players' bands therefore measure the same, and they keep
     * measuring the same if either identity colour ever changes — including when
     * {@link Comfort#distinctPlayers} swaps Sky's cyan for a deep teal, which needs barely
     * a third of the alpha to say the same thing.
     */
    static int bandColor(int player, double ratio) {
        int source = Draw.blend(Theme.playerColor(player), Theme.playerColorDark(player),
                BAND_DEEPEN);
        return Draw.withAlpha(source,
                Theme.washAlpha(source, Theme.PAPER, ratio, BAND_ALPHA_CEILING));
    }

    /** The band a player's row and column are washed with, as it lands on clean paper. */
    static int bandOnPaper(int player, boolean highContrast) {
        int band = bandColor(player, highContrast ? BAND_RATIO_BOLD : BAND_RATIO);
        return Draw.blend(Theme.PAPER, band, (band >>> 24) / 255f);
    }

    /**
     * The paper the picture is drawn on: a faint five-square checker that keeps long rows
     * countable without adding any lines to count past.
     *
     * <p>The checker used to be {@code blend(PAPER, GRID, .055)}. That was the right
     * <em>strength</em> — 1.087:1, and 1.07:1 is what {@link Theme#PAPER_SHADE} measures —
     * but {@link Theme#GRID} is a cool lilac-plum, so the shaded blocks came out greyer and
     * cooler than the sheet they were printed on, in a room the backdrop art paints entirely
     * in candlelight. Warm paper with warmer paper on it is the same idea drawn correctly.
     * Extra contrast keeps the cool grey step, because there value separation is worth more
     * than warmth.
     */
    private void drawPaper(Canvas canvas, BoardLayout board, UiState ui) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.FILL);
        int plain = Theme.PAPER;
        int shaded = ui.highContrastOn ? Draw.blend(Theme.PAPER, Theme.GRID, .095f)
                : Theme.PAPER_SHADE;
        for (int y = 0; y < board.size; y++) {
            for (int x = 0; x < board.size; x++) {
                paint.setColor(((x / 5) + (y / 5)) % 2 == 1 ? shaded : plain);
                canvas.drawRect(board.cellLeft(x), board.cellTop(y),
                        board.cellLeft(x) + board.cell, board.cellTop(y) + board.cell,
                        paint);
            }
        }
    }

    private void drawMarks(Canvas canvas, BoardLayout board, GameState game, UiState ui,
                           Effects effects, long now) {
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x < game.size; x++) {
                byte mark = game.puzzle.marks[y][x];
                if (mark == Puzzle.UNKNOWN) {
                    continue;
                }
                drawMark(canvas, mark, board.centreX(x), board.centreY(y),
                        board.cell * .5f, grain(x, y), effects.squareProgress(x, y, now),
                        ui.highContrastOn, 1f, effects.lineLift(x, y, now));
            }
        }
    }

    /**
     * Which of the four tile shades a square gets.
     *
     * <p>It was {@code x + y}, which is a diagonal ramp: taken modulo four it repeats every
     * four cells along <em>both</em> axes, and a regular diagonal grating is the one spatial
     * pattern the visual system amplifies rather than averages out. Measured across rows
     * 6-10 of a 20x20 board the filled-tile centres cycled 88, 85, 82, 79 and back, in
     * perfect step diagonally — subtle at 1.09:1, but on four hundred tiles it read as a
     * print artefact rather than as hand-placed variation. A hash of the two coordinates
     * gives the same four shades with no period at all.
     *
     * <p>It has to be a real avalanche and not just a multiply: the obvious
     * {@code x * PHI ^ y * PRIME} followed by one shift keeps 95% of horizontal neighbours
     * on the same shade, because both multipliers are odd and the low two bits — which are
     * all this returns — barely move. Measured over a 20x20 board this one lands 108 / 94 /
     * 93 / 105 tiles on the four shades, with neighbours agreeing at chance in both
     * directions.
     */
    static int grain(int x, int y) {
        int hash = x * 0x9E3779B1 + y * 0x85EBCA6B;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        hash *= 0x846CA68B;
        return (hash ^ (hash >>> 16)) & 3;
    }

    /**
     * One mark, centred on a square of half-width {@code half}.
     *
     * <p>{@code fade} scales every colour the mark is built from. It is 1 for the board's
     * own squares and only moves for the pair of marks a gliding cursor plate hands over
     * between; see {@link #drawCursorPlates}.
     */
    private void drawMark(Canvas canvas, byte mark, float cx, float cy, float half,
                          int grain, float pop, boolean bold, float fade, float lift) {
        if (mark == Puzzle.FILLED) {
            drawFilled(canvas, cx, cy, half, grain, pop, bold, fade, lift);
        } else if (mark == Puzzle.CROSSED) {
            drawCross(canvas, cx, cy, half, pop, bold, fade);
        }
    }

    /**
     * A tile with the line-complete light falling across it.
     *
     * <p>How far to carry it is asked of {@link Theme#washAlpha} rather than picked, which is
     * this palette's standing rule: a fixed fraction can only be right for one hue on one
     * background, and the tile is a warm mulberry normally and a cool near-black slate under
     * extra contrast. Asking for {@link Theme#FEEDBACK_MIN_RATIO} against whichever one is in
     * use puts the peak at 1.7:1 over the plum and 2.4:1 over the slate — over the threshold
     * at which a change of light is a thing anybody sees from a sofa, and well under the point
     * where a filled square could be mistaken for anything but filled.
     *
     * <p>The tile is lifted toward {@link Theme#CANDLE} rather than washed with it: the
     * picture keeps its own colour and catches the light, which is the difference between a
     * lamp passing over the board and a highlighter drawn across it. See
     * {@link Effects#lineLift} for why the gutter band alone was not enough.
     */
    private static int lit(int tile, float lift) {
        if (lift <= 0) {
            return tile;
        }
        int reach = Theme.washAlpha(Theme.CANDLE, tile, Theme.FEEDBACK_MIN_RATIO, 120);
        return Draw.blend(tile, Theme.CANDLE, Draw.clamp01(lift) * reach / 255f);
    }

    /** {@code color} with its own alpha scaled by {@code fade}. */
    private static int faded(int color, float fade) {
        return fade >= 1f ? color
                : Draw.withAlpha(color, (int) ((color >>> 24) * Draw.clamp01(fade)));
    }

    /**
     * A filled square: a rounded tile in {@link Theme#TILE}, a deep mulberry plum, with a
     * whisper of gloss along its top so it reads as something physically placed on the
     * paper rather than as a flat swatch.
     *
     * <p>With extra contrast on the tile changes family rather than shade: it desaturates
     * to the near-black slate of {@link Theme#TILE_BOLD}, 13.0:1 on paper against 9.9:1,
     * and the gloss and the per-square variation both come off so every tile is the same
     * solid block. That is a difference a low-vision player can see from a sofa; the old
     * five-percent nudge from {@code rgb(208,102,140)} to {@code rgb(197,93,132)} was not.
     */
    private void drawFilled(Canvas canvas, float cx, float cy, float half, int grain,
                            float pop, boolean bold, float fade, float lift) {
        float scale = pop < 0 ? 1f : .55f + Draw.easeOutBack(pop) * .45f;
        float body = (half - Math.max(1f, half * .11f)) * scale;
        float radius = Math.max(2.5f, half * .32f);

        int base = Theme.tile(bold);
        int tinted = bold ? base : Draw.blend(base, Theme.TILE_DEEP, grain * TILE_GRAIN);
        tinted = lit(tinted, lift);

        draw.roundRect(canvas, cx - body, cy - body, cx + body, cy + body, radius,
                faded(tinted, fade));

        if (!bold) {
            // Gloss: a small rounded highlight in the upper-left quadrant. Kept off in
            // high contrast, where a flat block is worth more than a lit one.
            draw.roundRect(canvas, cx - body * .78f, cy - body * .78f, cx + body * .12f,
                    cy - body * .32f, radius * .7f,
                    faded(Color.argb(30, 255, 236, 240), fade));
        }

        if (pop >= 0) {
            float glow = 1 - pop;
            draw.roundRectStroke(canvas, cx - body, cy - body, cx + body, cy + body, radius,
                    Math.max(1.5f, half * .14f * glow),
                    Draw.withAlpha(Theme.CREAM, (int) (210 * glow * Draw.clamp01(fade))));
        }
    }

    /**
     * A crossed square: a cool wash plus a heavy slate X.
     *
     * <p>At 20x20 the X alone is only a few pixels of stroke, which reads as a grey smudge
     * from a couch. The wash gives the whole square a different <em>value</em> from clean
     * paper, so "ruled out" survives at a glance and in greyscale; the X on top is what
     * confirms it when a player looks straight at the square. With extra contrast on the
     * wash doubles and the X goes all the way to {@link Theme#INK} on a thicker stroke.
     *
     * <p>The wash used to be a fixed alpha of 26, and a fixed alpha cannot know what it is
     * lying on. It measured 1.17:1 against paper while the five-square checker printed
     * underneath it measured 1.087:1 — statistically the same mark. An empty square on a
     * shaded block and a crossed square on a plain one were then 1.07:1 apart, which is to
     * say identical from a sofa, and the promise that filled, crossed and untouched differ
     * in value held for filled and for nothing else. Asked for as a ratio through
     * {@link Theme#washAlpha} it resolves to alpha 44 and cannot drift back under the
     * checker whatever either colour becomes.
     */
    private void drawCross(Canvas canvas, float cx, float cy, float half, float pop,
                           boolean bold, float fade) {
        float scale = pop < 0 ? 1f : Draw.easeOut(pop);
        float pad = half * .91f;
        draw.roundRect(canvas, cx - pad, cy - pad, cx + pad, cy + pad,
                Math.max(2f, half * .28f),
                faded(bold ? CROSS_WASH_BOLD : CROSS_WASH, fade));

        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(2.6f, half * (bold ? .32f : .27f)));
        paint.setColor(faded(bold ? Theme.INK : crossStroke(), fade));
        float reach = half * (.49f - (1 - scale) * .32f);
        canvas.drawLine(cx - reach, cy - reach, cx + reach, cy + reach, paint);
        canvas.drawLine(cx + reach, cy - reach, cx - reach, cy + reach, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    /**
     * The thinnest tint of {@link Theme#CROSS_TINT} that still reaches
     * {@link Theme#CROSS_RATIO} against the paper under it.
     *
     * <p>Worked out once at class load rather than per square: {@link Theme#washAlpha} walks
     * a ratio search over real WCAG luminance, and unlike the crosshair bands there is no
     * identity colour underneath that a comfort option can change.
     */
    static int crossWash(boolean bold) {
        return Draw.withAlpha(Theme.CROSS_TINT, Theme.washAlpha(Theme.CROSS_TINT, Theme.PAPER,
                bold ? Theme.CROSS_RATIO_BOLD : Theme.CROSS_RATIO, CROSS_ALPHA_CEILING));
    }

    /** The X itself: the same slate the wash is made of, taken most of the way to ink. */
    static int crossStroke() {
        return Draw.withAlpha(Draw.blend(Theme.CROSS_TINT, Theme.INK, .30f), 235);
    }

    /**
     * The ruling: a light rule between squares, a heavy one every five, and a heavier one
     * still around the whole sheet.
     *
     * <p><b>Every rule is a snapped rect, not a stroked line.</b> An anti-aliased line
     * centred on a fractional coordinate spreads itself across two pixels at partial
     * coverage, and where it lands inside a pixel decides how dark it comes out. With the
     * old fractional cell — 30.33 px at 20x20 — the twenty-one vertical rules of one board
     * measured anywhere from 1.36:1 to 1.87:1 against the paper: same stroke, same colour,
     * 38% apart in weight purely from arithmetic. From ten feet that is a grid that
     * shimmers, with some cell divisions reading and their neighbours not. Now that
     * {@link BoardLayout} hands over whole-pixel geometry, filling a rect of a whole number
     * of pixels puts every rule at its nominal width and full opacity, and the board is the
     * same drawing at every board size.
     *
     * <p><b>The frame is not just another five-rule.</b> {@code i == 0} and
     * {@code i == size} used to be drawn exactly like the interior fives, so the block at
     * columns 0-4 appeared to continue on into the clue gutter and the eye had to find the
     * board's boundary from where the numbers stopped. The frame asks for 1.37x the interior
     * five-rule, the ratio printed grids have always used, and lands on 3 px against 2 at
     * 20x20 and 9 px against 7 at 5x5 once both are snapped. See {@link #ruleWeight} for why
     * that snapping is now part of the rule rather than something done to it afterwards.
     */
    private void drawGrid(Canvas canvas, BoardLayout board, UiState ui) {
        boolean bold = ui.highContrastOn;
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i <= board.size; i++) {
            boolean edge = i == 0 || i == board.size;
            boolean major = edge || i % 5 == 0;
            float x = board.left + i * board.cell;
            float y = board.top + i * board.cell;

            // Interior block rules carry on through the gutters, so the eye can walk from
            // a clue to its line without losing count of the fives. Only interior ones:
            // extending the outer frame would just draw a box round an empty margin.
            if (major && !edge) {
                float gutterWeight = board.cell * (bold ? .046f : .030f);
                paint.setColor(Draw.withAlpha(Theme.RULE_MAJOR, bold ? 205 : 100));
                rule(canvas, paint, x, board.clueTop(), board.top, gutterWeight, true);
                rule(canvas, paint, y, board.clueLeft(), board.left, gutterWeight, false);
            }

            // The five-square rules thicken with extra contrast, which is the cue that
            // tells a low-vision player where they are in a long line.
            paint.setColor(major ? Draw.withAlpha(Theme.RULE_MAJOR, bold ? 255 : 240)
                    : Draw.withAlpha(Theme.RULE_MINOR, bold ? 190 : 128));
            float weight = ruleWeight(board.cell, edge, major, bold);
            rule(canvas, paint, x, board.top, board.bottom, weight, true);
            rule(canvas, paint, y, board.left, board.right, weight, false);
        }
    }

    /**
     * How thick one rule is drawn, in whole pixels, with the three weights ordered by
     * construction.
     *
     * <p>The ratios alone are not enough, because what reaches the screen is a rounded
     * number of pixels and rounding is not order-preserving. The bold five-rule asks for
     * {@code cell * .090} and the bold frame for {@code cell * .120}, a comfortable 1.33x —
     * but at a 28 px square that is 2.52 and 3.36, and both round to 3. Then the sheet has
     * no edge: the frame weighs exactly what the rule at column 5 weighs, which is the one
     * thing this method exists to prevent. It went unnoticed because the collision band is
     * narrow and no shipping board had landed in it; a 15x15 board at 1280x720 with extra
     * contrast landed in it the moment the board grew.
     *
     * <p>So each weight is snapped here and then held at least one pixel above the one
     * below it. A frame that cannot be told from an interior rule is worth less than a
     * frame one pixel heavier than its ratio asked for, and {@code rule}'s own rounding
     * becomes a no-op rather than the place the ordering is decided.
     */
    static float ruleWeight(float cell, boolean edge, boolean major, boolean bold) {
        float thin = Math.round(Math.max(1f, cell * (bold ? .034f : .026f)));
        if (!major) {
            return thin;
        }
        float five = Math.max(thin + 1,
                Math.round(Math.max(2.2f, cell * (bold ? .090f : .062f))));
        if (!edge) {
            return five;
        }
        return Math.max(five + 1, Math.round(Math.max(3f, cell * (bold ? .120f : .085f))));
    }

    /**
     * One rule, laid down as a whole number of pixels starting on a pixel boundary. The
     * paint must already be {@link Paint.Style#FILL} and coloured.
     */
    private void rule(Canvas canvas, Paint paint, float centre, float from, float to,
                      float weight, boolean vertical) {
        float thick = Math.max(1f, Math.round(weight));
        float near = Math.round(centre - thick * .5f);
        if (vertical) {
            canvas.drawRect(near, from, near + thick, to, paint);
        } else {
            canvas.drawRect(from, near, to, near + thick, paint);
        }
    }

    // ---- The cursor plate --------------------------------------------------------------
    //
    // The plate is drawn here rather than in CursorRenderer because it lives *under* the
    // mark it is holding, and the marks are drawn here. CursorRenderer strokes the
    // player-colour border into the cream gap this leaves and adds the badge, the halo and
    // the margin tabs; the two agree on the geometry through the four helpers below.

    /** Half-width of the plate a cursor stands on, which reaches just past its square. */
    public static float plateHalf(float cell) {
        return cell * .5f + Math.max(Theme.scale(PLATE_OUT), cell * .05f);
    }

    /**
     * Width of the plate's hard dark edge — what makes it read on light paper.
     *
     * @param cell accepted for symmetry with {@link #plateHalf}, {@link #plateBorder} and
     *             {@link #plateRing}, which is how {@code CursorRenderer} reads all four in
     *             one breath. The edge is deliberately fixed in screen pixels and does not
     *             use it: it is the one part of the cursor that has to stay resolvable from
     *             ten feet, so a 20x20 board must not be allowed to shave it.
     */
    public static float plateEdge(float cell) {
        return Math.max(1.4f, Theme.scale(PLATE_EDGE));
    }

    /** Width of the player-colour border stroked around the plate. */
    public static float plateBorder(float cell) {
        float base = Math.min(Theme.scale(BORDER_CEILING),
                Math.max(Theme.scale(BORDER_FLOOR), cell * .10f));
        return Comfort.get().boldCursor ? base * 1.45f : base;
    }

    /** Half-width of the ring that border is stroked along. */
    public static float plateRing(float cell) {
        float border = plateBorder(cell);
        return plateHalf(cell) - plateEdge(cell)
                - Math.max(Theme.scale(PLATE_CREAM_OUT), border * .45f) - border * .5f;
    }

    /** Half-width of the clear space the border encloses, where the mark is redrawn. */
    public static float plateInner(float cell) {
        float border = plateBorder(cell);
        return plateRing(cell) - border * .5f
                - Math.max(Theme.scale(PLATE_CREAM_IN), border * .16f);
    }

    /**
     * The plate under each cursor: a hard-edged cream tile that replaces whatever the
     * square was, with the square's own mark redrawn smaller inside it.
     *
     * <p>This is the cue that makes the two players equally findable, because it is the one
     * cue that owes nothing to whose cursor it is. On a run of plum tiles it is a bright
     * card at 9.5:1; on clean paper its dark outer edge is what carries it. Either way
     * Rose and Sky get exactly the same shape, the same size and the same contrast, and
     * their colours are left to say only <em>whose</em> — which is all an identity colour
     * should ever have to do.
     *
     * <p>Drawn above the grid, so the plate cuts its own square out of the ruling instead
     * of being ruled through: a cursor that the grid draws lines across does not read as
     * sitting on top of the board.
     *
     * <p>Both plates are laid down before either mark, and their dark edges are re-drawn on
     * top afterwards. Two plates on neighbouring squares overlap by {@code 2*plateHalf -
     * cell} — 10.5 px at 20x20 — and whichever was drawn second used to erase the first
     * one's dark edge, so the pair merged into a single cream lozenge with a pink half and a
     * cyan half. That is exactly the picture {@code CursorRenderer.drawSplitBorder} uses to
     * mean "we are both on <em>this</em> square", so "next to each other" and "on the same
     * square" looked the same. (Shrinking a crowded plate to sit strictly inside its own
     * square would be better still, but the player-colour border is stroked by
     * {@code CursorRenderer} from {@link #plateRing} and would no longer land on the plate;
     * that needs both files at once.)
     */
    private void drawCursorPlates(Canvas canvas, BoardLayout board, GameState game,
                                  UiState ui, Effects effects, long now) {
        float cell = board.cell;
        float half = plateHalf(cell);
        float edge = plateEdge(cell);
        float radius = Math.max(2.5f, cell * .17f);
        int players = ui.joined[1] ? 2 : 1;
        boolean crowded = players == 2 && !samePlate(ui) && platesOverlap(ui, half, cell);

        for (int player = 0; player < players; player++) {
            draw.shadow(canvas, plateX(board, ui, player) - half,
                    plateY(board, ui, player) - half, plateX(board, ui, player) + half,
                    plateY(board, ui, player) + half, radius, Theme.scale(PLATE_LIFT));
        }
        for (int player = 0; player < players; player++) {
            drawPlate(canvas, plateX(board, ui, player), plateY(board, ui, player), half,
                    edge, radius);
        }
        for (int player = 0; crowded && player < players; player++) {
            drawPlateEdge(canvas, plateX(board, ui, player), plateY(board, ui, player), half,
                    edge, radius);
        }
        for (int player = 0; player < players; player++) {
            drawPlateMark(canvas, board, game, ui, effects, now, player);
        }
    }

    private static float plateX(BoardLayout board, UiState ui, int player) {
        return board.left + (ui.cursorDrawX[player] + .5f) * board.cell;
    }

    private static float plateY(BoardLayout board, UiState ui, int player) {
        return board.top + (ui.cursorDrawY[player] + .5f) * board.cell;
    }

    /** Both cursors on one square, where there is only one plate to draw. */
    private static boolean samePlate(UiState ui) {
        return sameLine(ui.cursorDrawX[0], ui.cursorDrawX[1])
                && sameLine(ui.cursorDrawY[0], ui.cursorDrawY[1]);
    }

    /** Whether the two plates reach into one another and so need their edges restoring. */
    private static boolean platesOverlap(UiState ui, float half, float cell) {
        return Math.abs(ui.cursorDrawX[0] - ui.cursorDrawX[1]) * cell < half * 2
                && Math.abs(ui.cursorDrawY[0] - ui.cursorDrawY[1]) * cell < half * 2;
    }

    /** The plate itself: a dark edge with a cream face inside it. */
    private void drawPlate(Canvas canvas, float cx, float cy, float half, float edge,
                           float radius) {
        draw.roundRect(canvas, cx - half, cy - half, cx + half, cy + half, radius,
                Draw.withAlpha(Theme.PLATE_INK, PLATE_INK_ALPHA));
        float face = half - edge;
        draw.roundRect(canvas, cx - face, cy - face, cx + face, cy + face, radius * .88f,
                Theme.CREAM);
    }

    /** That edge on its own, put back over a neighbouring plate that overlapped it. */
    private void drawPlateEdge(Canvas canvas, float cx, float cy, float half, float edge,
                               float radius) {
        float mid = half - edge * .5f;
        draw.roundRectStroke(canvas, cx - mid, cy - mid, cx + mid, cy + mid,
                radius - edge * .5f, edge,
                Draw.withAlpha(Theme.PLATE_INK, PLATE_INK_ALPHA));
    }

    /**
     * The square's own mark, redrawn small at the <em>plate's</em> centre.
     *
     * <p>It used to be drawn on the square's centre while the plate was drawn on the
     * smoothed cursor position, on the reasoning that a mark should stay where it belongs
     * rather than be dragged about by a cursor passing over it. What that actually produced
     * was a lens showing half of something: measured at 10x10 with the cursor at x = 5.565,
     * a horizontal scan through the plate reads cream from x=882 to 906 and then tile plum
     * from 910 to 926 — a hard vertical seam inside one plate, and the plum sliver jumps
     * from one side to the other the instant {@code Math.round} crosses the halfway point.
     * Every move flashed a tile that looked half rubbed out for about a tenth of a second.
     *
     * <p>A plate is a lens over one square and a lens must always show one coherent mark. So
     * the two squares the plate is straddling are both drawn at its centre and handed over
     * as it travels. Which two they are is read off the drawn position alone — the squares
     * either side of it on whichever axis is mid-stride — so this owes nothing to the
     * cursor's logical position and stays right however the glide is driven.
     *
     * <p>Each mark reaches full strength by the half-way point rather than fading linearly,
     * so the pair is opaque all the way across: two marks at 50% composited over cream leave
     * a quarter of the plate showing through, which is its own kind of half-erased square.
     */
    private void drawPlateMark(Canvas canvas, BoardLayout board, GameState game, UiState ui,
                               Effects effects, long now, int player) {
        float cx = plateX(board, ui, player);
        float cy = plateY(board, ui, player);
        float drawX = ui.cursorDrawX[player];
        float drawY = ui.cursorDrawY[player];
        float alongX = fraction(drawX);
        float alongY = fraction(drawY);

        // Whichever axis is further from a whole square is the one being crossed; the other
        // is standing still and contributes its settled square to both marks.
        boolean crossingX = lean(alongX) >= lean(alongY);
        float across = crossingX ? alongX : alongY;
        int col = crossingX ? (int) Math.floor(drawX) : Math.round(drawX);
        int row = crossingX ? Math.round(drawY) : (int) Math.floor(drawY);

        drawPlateSquare(canvas, board, game, ui, effects, now, cx, cy, col, row,
                leavingFade(across));
        if (across > 0) {
            drawPlateSquare(canvas, board, game, ui, effects, now, cx, cy,
                    crossingX ? col + 1 : col, crossingX ? row : row + 1,
                    enteringFade(across));
        }
    }

    /**
     * How strongly the square a plate is sliding off is drawn, and how strongly the one it
     * is sliding onto is. Both reach full by the half-way point rather than crossing at 50%,
     * so at least one of the pair is always opaque and the plate never shows cream through
     * the middle of a mark.
     */
    static float leavingFade(float across) {
        return Math.min(1f, 2f - across * 2f);
    }

    static float enteringFade(float across) {
        return Math.min(1f, across * 2f);
    }

    private void drawPlateSquare(Canvas canvas, BoardLayout board, GameState game,
                                 UiState ui, Effects effects, long now, float cx, float cy,
                                 int column, int row, float fade) {
        int col = clamp(column, board.size);
        int line = clamp(row, board.size);
        drawMark(canvas, game.puzzle.marks[line][col], cx, cy, plateInner(board.cell),
                grain(col, line), effects.squareProgress(col, line, now), ui.highContrastOn,
                fade, effects.lineLift(col, line, now));
    }

    /** How far past its square a drawn coordinate has moved, 0 (settled) to just under 1. */
    private static float fraction(float drawn) {
        return drawn - (float) Math.floor(drawn);
    }

    /** How far a fraction is from the nearest whole square: 0 settled, .5 mid-stride. */
    private static float lean(float fraction) {
        return Math.min(fraction, 1 - fraction);
    }

    private static int clamp(int value, int size) {
        return value < 0 ? 0 : Math.min(value, size - 1);
    }

    // ---- Clues ---------------------------------------------------------------------

    private void drawClues(Canvas canvas, BoardLayout board, GameState game, UiState ui) {
        drawRowClues(canvas, board, game, ui);
        drawColumnClues(canvas, board, game, ui);
    }

    private void drawRowClues(Canvas canvas, BoardLayout board, GameState game,
                              UiState ui) {
        float size = board.clueTextSize;
        for (int y = 0; y < game.size; y++) {
            int[] clues = game.puzzle.rowClues(y);
            boolean done = game.puzzle.rowSolved(y);
            float centre = board.centreY(y);

            if (done) {
                float half = Math.min(board.cell * .42f, size * .78f);
                drawSolvedBand(canvas, board.rowClueOuterX(clues.length - 1),
                        centre - half, board.left, centre + half, ui);
            }
            for (int i = clues.length - 1, lane = 0; i >= 0; i--, lane++) {
                float scale = clues[i] == 0 ? .86f : 1f;
                drawClueNumber(canvas, clues[i], board.rowClueRightX(lane),
                        centre + draw.capCentreOffset(size * scale), size * scale,
                        clueColor(clues[i] == 0, done, ui),
                        board.rowClueNumberWidth(lane), Paint.Align.RIGHT);
            }
            if (done) {
                drawSolvedTick(canvas, board.left - board.clueInset * .5f, centre,
                        tickRadius(board), ui);
            }
        }
    }

    private void drawColumnClues(Canvas canvas, BoardLayout board, GameState game,
                                 UiState ui) {
        float size = board.clueTextSize;
        float maxWidth = board.colClueNumberWidth();
        for (int x = 0; x < game.size; x++) {
            int[] clues = game.puzzle.colClues(x);
            boolean done = game.puzzle.colSolved(x);
            float centre = board.centreX(x);

            if (done) {
                float half = Math.min(board.cell * .42f, size * .78f);
                drawSolvedBand(canvas, centre - half,
                        board.colClueOuterY(clues.length - 1), centre + half, board.top,
                        ui);
            }
            for (int i = clues.length - 1, lane = 0; i >= 0; i--, lane++) {
                float scale = clues[i] == 0 ? .86f : 1f;
                drawClueNumber(canvas, clues[i], centre,
                        board.colClueCentreY(lane) + draw.capCentreOffset(size * scale),
                        size * scale, clueColor(clues[i] == 0, done, ui), maxWidth,
                        Paint.Align.CENTER);
            }
            if (done) {
                drawSolvedTick(canvas, centre, board.top - board.clueInset * .5f,
                        tickRadius(board), ui);
            }
        }
    }

    /**
     * The colour of one clue number.
     *
     * <p>Nothing here is allowed below 4.5:1 on paper, ever. A solved line's clues used to
     * be {@code GRID} at 130/255, which composites to {@code rgb(161,149,162)} — 2.5:1, and
     * on a 20x20 board that was most of the row clues at once. Nonogram players re-read
     * solved clues constantly to check a line off, so "this one is finished" has to be said
     * by the band behind the numbers and the tick beside them, and the numbers themselves
     * only step down from {@link Theme#INK} at 15.1:1 to {@link Theme#GRID} at 6.9:1 —
     * quiet enough for the eye to skip, never quiet enough to have to squint at.
     *
     * <p>An empty line still prints its {@code 0} — it is the notation every nonogram uses,
     * and it is the one clue a beginner can act on immediately — but drawn quiet and a
     * little smaller, because a 20x20 board can have eight at once.
     *
     * <p>With extra contrast on, solved clues come all the way back to full ink and the
     * hierarchy is carried entirely by the band and the tick.
     */
    int clueColor(boolean empty, boolean solved, UiState ui) {
        if (empty) {
            return Theme.GRID;
        }
        return solved && !ui.highContrastOn ? Theme.GRID : Theme.INK;
    }

    /**
     * Draws one clue number against {@code anchor} — its centre or its right-hand edge,
     * depending on {@code align} — condensing it if the live font makes it wider than the
     * room it has.
     *
     * <p>Stepping by a constant multiple of the text size is what used to run {@code 5}
     * and {@code 14} together into "514": a two-digit group is nearly twice as wide as a
     * one-digit group, and a fixed step does not know the difference. Everything here is
     * driven by {@link Draw#measure}, and the tracking is tightened rather than the glyphs
     * shrunk, because height is what carries legibility across a room and width is what
     * causes collisions.
     *
     * <p>The floor on that tightening stays at {@value #CONDENSE_FLOOR}. Taking it lower was
     * tried and measured: at .78 the ink of the two digits in a 20x20 column clue merges
     * into one 21 px run, which trades a gap between numbers for a gap inside one. Where the
     * separation between neighbouring column clues comes from instead is
     * {@link BoardLayout#COL_CLUE_FILL} and the ground behind every other lane.
     */
    private void drawClueNumber(Canvas canvas, int value, float anchor, float baseline,
                                float size, int color, float maxWidth, Paint.Align align) {
        String label = value >= 0 && value < NUMBER.length ? NUMBER[value]
                : Integer.toString(value);
        float width = draw.measure(label, size, true);
        if (label.length() < 2 || width <= maxWidth) {
            draw.text(canvas, label, anchor, baseline, size, color, align, true);
            return;
        }
        float squeeze = Math.max(CONDENSE_FLOOR, maxWidth / width);
        float drawn = width * squeeze;
        float x = align == Paint.Align.RIGHT ? anchor - drawn : anchor - drawn * .5f;
        for (int i = 0; i < label.length(); i++) {
            String digit = DIGIT[label.charAt(i) - '0'];
            float advance = draw.measure(digit, size, true) * squeeze;
            draw.text(canvas, digit, x + advance * .5f, baseline, size, color,
                    Paint.Align.CENTER, true);
            x += advance;
        }
    }

    /**
     * The soft band behind a finished line's clues. It runs from the outermost number the
     * line actually uses right up to the grid, which is what makes the tick at its inner
     * end read as belonging to that line rather than floating in the margin. Now that the
     * digits no longer fade, this and the tick are what carry "finished" on their own, so
     * both are drawn to be seen.
     */
    private void drawSolvedBand(Canvas canvas, float left, float top, float right,
                                float bottom, UiState ui) {
        float radius = Math.min((bottom - top), (right - left)) * .5f;
        draw.roundRect(canvas, left, top, right, bottom, radius,
                Color.argb(ui.highContrastOn ? 92 : 58, 112, 96, 146));
    }

    /**
     * How big a solved-line tick is drawn.
     *
     * <p>Floored at 15 design pixels of diameter — 22 px at 1080p, the same size the margin
     * tabs already hold themselves to. Below that a tick is a couple of arcminutes from ten
     * feet, which is under the resolvable limit: a smudge that says something happened
     * without saying what. The quiet strip between the clues and the grid is only about 20
     * px wide at 20x20, so a floored tick overhangs it by a pixel; that is the right trade,
     * because a cue nobody can resolve is worth nothing however neatly it fits.
     */
    static float tickRadius(BoardLayout board) {
        return Math.max(Theme.scale(7.4f),
                Math.min(board.cell * .42f, board.clueInset * .5f));
    }

    /**
     * A check mark that marks a finished line as put to bed.
     *
     * <p>Deliberately not in a player's colour — it used to be {@link Theme#PINK} over
     * {@link Theme#PINK_DARK}, which put twenty small pink discs down the side of a board
     * Rose was already struggling to find a pink cursor on.
     */
    private void drawSolvedTick(Canvas canvas, float cx, float cy, float radius,
                                UiState ui) {
        draw.circle(canvas, cx, cy, radius,
                Draw.blend(Theme.PAPER, Theme.GRID, ui.highContrastOn ? .42f : .28f));
        draw.circleStroke(canvas, cx, cy, radius, Math.max(1f, radius * .12f),
                Draw.withAlpha(Theme.INK, ui.highContrastOn ? 140 : 90));

        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(2f, radius * .30f));
        paint.setColor(Theme.INK);
        canvas.drawLine(cx - radius * .46f, cy + radius * .02f, cx - radius * .12f,
                cy + radius * .40f, paint);
        canvas.drawLine(cx - radius * .12f, cy + radius * .40f, cx + radius * .50f,
                cy - radius * .42f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private static String[] numbers(int count) {
        String[] values = new String[count];
        for (int i = 0; i < count; i++) {
            values[i] = Integer.toString(i);
        }
        return values;
    }
}

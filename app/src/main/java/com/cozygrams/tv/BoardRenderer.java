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
 *       and into both gutters — as shading and as continued major rules — so "that is the
 *       seventh column" is answerable without touching anything.</li>
 *   <li><b>Filled, crossed and untouched differ in value, not only in hue.</b> A filled
 *       square is a dark tile (9.9:1 on paper), a crossed square is a tinted cell with a
 *       heavy slate X (1.2:1 on paper, 8.5:1 against a tile), an untouched square is clean
 *       paper. Greyscale keeps all three apart.</li>
 *   <li><b>Extra contrast changes kind, not amount.</b> See {@link #drawFilled}: the tile
 *       leaves the warm plum family entirely for a near-black slate, the gloss comes off,
 *       the cross deepens to {@link Theme#INK}, the five-square rules thicken and the
 *       solved clues come back up to full ink. The setting used to move a tile by five
 *       percent of luminance, which is nothing from a sofa.</li>
 * </ul>
 *
 * <p>Nothing here moves, so there is no motion for {@code Comfort.calmMotion} to calm.
 */
public final class BoardRenderer {

    /** Preformatted clue labels, so a full 20x20 frame never allocates a string. */
    private static final String[] NUMBER = numbers(100);
    private static final String[] DIGIT = numbers(10);

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

    // ---- The cursor plate --------------------------------------------------------------

    /** How far past its square the plate reaches, in design pixels. */
    private static final float PLATE_OUT = 3.5f;
    /** The plate's hard dark edge, in design pixels. */
    private static final float PLATE_EDGE = 1.2f;
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
        drawGutterBlocks(canvas, board, ui);
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
        effects.drawParticles(canvas, draw, now);
        drawMarks(canvas, board, game, ui, effects, now);
        drawGrid(canvas, board, ui);
        drawCursorPlates(canvas, board, game, ui, effects, now);
        drawClues(canvas, board, game, ui);
    }

    /** The warm paper card the whole puzzle sits on. */
    private void drawCard(Canvas canvas, BoardLayout board) {
        float radius = Theme.scale(22);
        draw.shadow(canvas, board.cardLeft(), board.cardTop(), board.cardRight(),
                board.cardBottom(), radius, Theme.scale(9));
        draw.roundRect(canvas, board.cardLeft(), board.cardTop(), board.cardRight(),
                board.cardBottom(), radius, Theme.CREAM);
        draw.roundRectStroke(canvas, board.cardLeft(), board.cardTop(), board.cardRight(),
                board.cardBottom(), radius, Math.max(1.5f, Theme.scale(2)),
                Color.argb(70, 120, 100, 140));
    }

    /**
     * Carries the board's five-square blocking out into both gutters.
     *
     * <p>Every second block of five gets a whisper of tint, aligned with the same blocks
     * on the board itself. It is the cheapest possible answer to "which column is this
     * number for?" at 20x20, and it costs no ink at all next to a ruled-off gutter.
     */
    private void drawGutterBlocks(Canvas canvas, BoardLayout board, UiState ui) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Draw.blend(Theme.CREAM, Theme.GRID,
                ui.highContrastOn ? .13f : .055f));
        for (int block = 1; block * 5 < board.size; block += 2) {
            float from = board.cellLeft(block * 5);
            float to = Math.min(board.right, board.cellLeft(block * 5 + 5));
            canvas.drawRect(from, board.clueTop(), to, board.top, paint);

            from = board.cellTop(block * 5);
            to = Math.min(board.bottom, board.cellTop(block * 5 + 5));
            canvas.drawRect(board.clueLeft(), from, board.left, to, paint);
        }
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
     */
    private void drawCursorGuides(Canvas canvas, BoardLayout board, UiState ui) {
        for (int player = 1; player >= 0; player--) {
            if (player == 1 && !ui.joined[1]) {
                continue;
            }
            float rowTop = board.top + ui.cursorDrawY[player] * board.cell;
            float colLeft = board.left + ui.cursorDrawX[player] * board.cell;

            draw.paint().setStyle(Paint.Style.FILL);
            draw.paint().setColor(band(player, ui.highContrastOn));
            canvas.drawRect(board.clueLeft(), rowTop, board.right,
                    rowTop + board.cell, draw.paint());
            canvas.drawRect(colLeft, board.clueTop(), colLeft + board.cell,
                    board.bottom, draw.paint());
        }
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
     */
    private void drawPaper(Canvas canvas, BoardLayout board, UiState ui) {
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.FILL);
        int plain = Theme.PAPER;
        int shaded = Draw.blend(Theme.PAPER, Theme.GRID, ui.highContrastOn ? .095f : .055f);
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
                        board.cell * .5f, x + y, effects.squareProgress(x, y, now),
                        ui.highContrastOn);
            }
        }
    }

    /** One mark, centred on a square of half-width {@code half}. */
    private void drawMark(Canvas canvas, byte mark, float cx, float cy, float half,
                          int variant, float pop, boolean bold) {
        if (mark == Puzzle.FILLED) {
            drawFilled(canvas, cx, cy, half, variant, pop, bold);
        } else if (mark == Puzzle.CROSSED) {
            drawCross(canvas, cx, cy, half, pop, bold);
        }
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
    private void drawFilled(Canvas canvas, float cx, float cy, float half, int variant,
                            float pop, boolean bold) {
        float scale = pop < 0 ? 1f : .55f + Draw.easeOutBack(pop) * .45f;
        float body = (half - Math.max(1f, half * .11f)) * scale;
        float radius = Math.max(2.5f, half * .32f);

        int base = Theme.tile(bold);
        int tinted = bold ? base
                : Draw.blend(base, Theme.TILE_DEEP, (variant % 4) * .10f);

        draw.roundRect(canvas, cx - body, cy - body, cx + body, cy + body, radius, tinted);

        if (!bold) {
            // Gloss: a small rounded highlight in the upper-left quadrant. Kept off in
            // high contrast, where a flat block is worth more than a lit one.
            draw.roundRect(canvas, cx - body * .78f, cy - body * .78f, cx + body * .12f,
                    cy - body * .32f, radius * .7f, Color.argb(30, 255, 236, 240));
        }

        if (pop >= 0) {
            float glow = 1 - pop;
            draw.roundRectStroke(canvas, cx - body, cy - body, cx + body, cy + body, radius,
                    Math.max(1.5f, half * .14f * glow),
                    Draw.withAlpha(Theme.CREAM, (int) (210 * glow)));
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
     */
    private void drawCross(Canvas canvas, float cx, float cy, float half, float pop,
                           boolean bold) {
        float scale = pop < 0 ? 1f : Draw.easeOut(pop);
        float pad = half * .91f;
        draw.roundRect(canvas, cx - pad, cy - pad, cx + pad, cy + pad,
                Math.max(2f, half * .28f), Color.argb(bold ? 46 : 26, 88, 80, 122));

        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(2.6f, half * (bold ? .32f : .27f)));
        paint.setColor(bold ? Theme.INK : Color.argb(235, 74, 66, 104));
        float reach = half * (.49f - (1 - scale) * .32f);
        canvas.drawLine(cx - reach, cy - reach, cx + reach, cy + reach, paint);
        canvas.drawLine(cx + reach, cy - reach, cx - reach, cy + reach, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawGrid(Canvas canvas, BoardLayout board, UiState ui) {
        boolean bold = ui.highContrastOn;
        Paint paint = draw.paint();
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i <= board.size; i++) {
            boolean major = i % 5 == 0 || i == board.size;
            float x = board.left + i * board.cell;
            float y = board.top + i * board.cell;

            // Interior block rules carry on through the gutters, so the eye can walk from
            // a clue to its line without losing count of the fives. Only interior ones:
            // extending the outer frame would just draw a box round an empty margin.
            if (major && i > 0 && i < board.size) {
                paint.setStrokeWidth(Math.max(1.1f, board.cell * (bold ? .046f : .030f)));
                paint.setColor(Color.argb(bold ? 205 : 100, 96, 84, 124));
                canvas.drawLine(x, board.clueTop(), x, board.top, paint);
                canvas.drawLine(board.clueLeft(), y, board.left, y, paint);
            }

            // The five-square rules thicken with extra contrast, which is the cue that
            // tells a low-vision player where they are in a long line.
            paint.setStrokeWidth(major
                    ? Math.max(2.2f, board.cell * (bold ? .090f : .062f))
                    : Math.max(1f, board.cell * (bold ? .034f : .026f)));
            paint.setColor(major ? Color.argb(bold ? 255 : 240, 62, 52, 84)
                    : Color.argb(bold ? 190 : 128, 126, 114, 150));
            canvas.drawLine(x, board.top, x, board.bottom, paint);
            canvas.drawLine(board.left, y, board.right, y, paint);
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

    /** Width of the plate's hard dark edge — what makes it read on light paper. */
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
     */
    private void drawCursorPlates(Canvas canvas, BoardLayout board, GameState game,
                                  UiState ui, Effects effects, long now) {
        float cell = board.cell;
        float ph = plateHalf(cell);
        float edge = plateEdge(cell);
        float radius = Math.max(2.5f, cell * .17f);
        for (int player = 0; player < 2; player++) {
            if (player == 1 && !ui.joined[1]) {
                continue;
            }
            float cx = board.left + (ui.cursorDrawX[player] + .5f) * cell;
            float cy = board.top + (ui.cursorDrawY[player] + .5f) * cell;

            draw.roundRect(canvas, cx - ph, cy - ph, cx + ph, cy + ph, radius,
                    Color.argb(215, 30, 20, 46));
            float in = ph - edge;
            draw.roundRect(canvas, cx - in, cy - in, cx + in, cy + in, radius * .88f,
                    Theme.CREAM);

            // The mark is redrawn on its own square rather than on the plate's centre, so
            // that during the ~80 ms a cursor spends gliding between squares the mark stays
            // where it belongs instead of being dragged along by the plate.
            int col = clamp(Math.round(ui.cursorDrawX[player]), board.size);
            int row = clamp(Math.round(ui.cursorDrawY[player]), board.size);
            drawMark(canvas, game.puzzle.marks[row][col], board.centreX(col),
                    board.centreY(row), plateInner(cell), col + row,
                    effects.squareProgress(col, row, now), ui.highContrastOn);
        }
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
                drawClueNumber(canvas, clues[i], board.rowClueCentreX(lane),
                        centre + draw.capCentreOffset(size * scale), size * scale,
                        clueColor(clues[i] == 0, done, ui),
                        board.rowClueNumberWidth(lane));
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
                        size * scale, clueColor(clues[i] == 0, done, ui), maxWidth);
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
     * Draws one clue number centred on {@code centreX}, condensing it if the live font
     * makes it wider than its lane.
     *
     * <p>Stepping by a constant multiple of the text size is what used to run {@code 5}
     * and {@code 14} together into "514": a two-digit group is nearly twice as wide as a
     * one-digit group, and a fixed step does not know the difference. Everything here is
     * driven by {@link Draw#measure}, and the tracking is tightened rather than the glyphs
     * shrunk, because height is what carries legibility across a room and width is what
     * causes collisions.
     */
    private void drawClueNumber(Canvas canvas, int value, float centreX, float baseline,
                                float size, int color, float maxWidth) {
        String label = value >= 0 && value < NUMBER.length ? NUMBER[value]
                : Integer.toString(value);
        float width = draw.measure(label, size, true);
        if (label.length() < 2 || width <= maxWidth) {
            draw.text(canvas, label, centreX, baseline, size, color, Paint.Align.CENTER,
                    true);
            return;
        }
        float squeeze = Math.max(.86f, maxWidth / width);
        float x = centreX - width * squeeze * .5f;
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

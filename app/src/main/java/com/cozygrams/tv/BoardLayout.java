package com.cozygrams.tv;

/**
 * Where the board and its clue gutters sit on screen.
 *
 * <p>The layout is driven by the actual clue content rather than fixed guesses, so a
 * 20x20 board with four-group rows still gets room to breathe, and a 5x5 board is not
 * padded with dead space. Everything respects a TV-safe margin because living-room
 * panels overscan.
 *
 * <h2>How clue text is sized</h2>
 *
 * <p>Clue numbers are the game. They are sized by the two things that can actually crop
 * them and by nothing else:
 *
 * <ul>
 *   <li><b>The row height.</b> A row clue has to sit inside one square's worth of height,
 *       so the text can never exceed {@code cell * ROW_FIT}.</li>
 *   <li><b>The column width.</b> A column clue has to sit inside one square's worth of
 *       width <em>including every digit it needs</em>, so it can never exceed
 *       {@code cell * CLUE_FILL / (digits * DIGIT_ADVANCE)}. This is what the old
 *       {@code cell * .52f} rule was standing in for, and why two-digit clues collided
 *       at 20x20: the old rule did not know how many digits it had to fit.</li>
 * </ul>
 *
 * <p>That ideal is then clamped: never larger than {@code CLUE_MAX} design pixels, because
 * a 5x5 board should not have poster-sized clues; and raised to the ten-foot legibility
 * floor wherever the squares can hold it. Where they cannot — a 20x20 board with big text
 * on — the floor is allowed to overrun the ideal by {@code FLOOR_OVERRUN} and no further,
 * and the renderer pays for that overrun by tightening the tracking between digits rather
 * than by shrinking them. So the numbers degrade to "as large as fits", never to numbers
 * that overflow their own column.
 *
 * <p>The gutters are then sized <em>from that text</em> rather than from a magic constant:
 * one lane per clue group, each lane only as wide as the widest number that can actually
 * land in it plus {@code LANE_AIR} of clear space. Measuring lanes one at a time is what
 * stops a 20x20 board — four groups per row, but only ever two digits in the group nearest
 * the grid — from reserving two-digit width four times over and leaving the outer half of
 * its gutter permanently empty.
 *
 * <p>Because the gutters depend on the text, which depends on the cell, which depends on
 * the gutters, the constructor solves the relation numerically. See the constructor for
 * why it is damped rather than iterated straight.
 *
 * <h2>The rail is furniture, and furniture gets a fixed footprint</h2>
 *
 * <p>The side rail used to be <em>reserved</em> at {@code max(scale(250), width * .19)}
 * and then <em>drawn</em> from {@code board.right + scale(34)}, so it quietly absorbed
 * every pixel the height-capped board failed to use: 375 px reserved, 648 px on screen,
 * and 58 px of horizontal drift between one puzzle and the next as the board's own width
 * changed under it. Turning larger text on made it worse — the grid lost 5% while the rail
 * gained 6%, an accessibility setting taking pixels from the thing being read and handing
 * them to a static legend.
 *
 * <p>{@link #railWidth} is now a flat {@value #RAIL_WIDTH} design pixels measured against
 * the <em>screen</em> rather than the type scale, and the rail is anchored to the safe
 * right edge, so it is the same width and in the same place on every board and in every
 * text size. Everything else is the board's.
 */
public final class BoardLayout {

    /**
     * A safe upper bound on a bold sans-serif digit's advance, as a fraction of the text
     * size. Roboto Bold measures .568 and Liberation Sans Bold — what the preview harness
     * draws with — measures .556, so reserving .58 covers the device and the harness alike
     * without paying for a font neither of them uses. The renderer measures the real
     * glyphs and condenses anything that still runs over, so this constant can only ever
     * cost a little air; it can never cause a collision.
     */
    public static final float DIGIT_ADVANCE = .58f;

    /** How much of a lane a clue number may fill before the renderer condenses it. */
    public static final float CLUE_FILL = .86f;

    /** The side rail's width, in design pixels measured against the screen. */
    public static final float RAIL_WIDTH = 240f;
    /** Air between the paper card and the rail, in design pixels. */
    private static final float RAIL_GAP = 40f;

    /** Largest clue text we ever draw, in design pixels. */
    private static final float CLUE_MAX = 32f;
    /** Tallest a clue may be relative to the row it labels. */
    private static final float ROW_FIT = .74f;
    /** Clear band between the clue block and the grid, as a fraction of the text size. */
    private static final float CLUE_INSET = .76f;

    /**
     * The quiet strip beside the grid is never narrower than this, whatever the clue type
     * size works out to.
     *
     * <p>The solved-line tick lives in that strip and has a floor of its own, because a
     * mark too small to resolve from a sofa is not a mark. Without a matching floor here
     * the two disagreed on a 20x20 board — the tick stayed legible and simply overhung
     * the strip it was supposed to sit in.
     */
    private static final float CLUE_INSET_MIN = 15f;

    /** The inset a given clue type size earns, never below {@link #CLUE_INSET_MIN}. */
    static float clueInsetFor(float text) {
        return Math.max(text * CLUE_INSET, Theme.scale(CLUE_INSET_MIN));
    }
    /** Clear space around a clue number inside its lane, as a fraction of the text size. */
    private static final float LANE_AIR = .40f;
    /** Vertical pitch of stacked column clues, as a fraction of the text size. */
    private static final float COL_PITCH = 1.24f;
    /**
     * How far the comfort floor may push a clue past the size its square can hold. The
     * overrun is paid for in tracking, never in glyph height, so at worst two digits sit a
     * little closer together — which reads far better across a room than smaller numbers.
     */
    private static final float FLOOR_OVERRUN = 1.10f;

    /** Size of one square, in pixels. */
    public final float cell;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;
    /** Width of the row-clue gutter to the left of the board. */
    public final float clueWidth;
    /** Height of the column-clue gutter above the board. */
    public final float clueHeight;
    /** Text size used for clue numbers, already floored for ten-foot legibility. */
    public final float clueTextSize;
    public final int size;

    /** The side rail's edges. Fixed width, anchored to the safe right edge. */
    public final float panelLeft;
    public final float panelRight;

    /** How many clue groups the busiest row / column of this puzzle holds. */
    public final int rowClueLanes;
    public final int colClueLanes;
    /** Distance between the centres of two neighbouring column clues. */
    public final float colCluePitch;
    /** Quiet band between the clues and the grid; a solved-line tick lives in it. */
    public final float clueInset;
    /** Digits the widest column clue needs, which is what can crop a clue horizontally. */
    public final int clueDigits;

    private final float cardPad;
    private final float screenHeight;
    /** Width of each row-clue lane, index 0 nearest the grid. */
    private final float[] rowLanePitch;
    /** Distance from the inner edge of the gutter out to the far side of each lane. */
    private final float[] rowLaneEdge;

    public BoardLayout(float width, float height, Puzzle puzzle, boolean hasSidePanel) {
        this.screenHeight = height;
        this.size = puzzle.size;
        this.rowClueLanes = Math.max(1, puzzle.widestRowClue());
        this.colClueLanes = Math.max(1, puzzle.tallestColClue());
        this.clueDigits = columnDigits(puzzle);

        // Row lanes are measured one at a time. A 20x20 board typically wants four groups
        // per row but only ever puts a two-digit number in the group nearest the grid, so
        // reserving two-digit width for all four lanes is what left the outer half of the
        // gutter permanently empty.
        int[] laneDigits = rowLaneDigits(puzzle, rowClueLanes);

        float safeX = width * Theme.SAFE_AREA;
        float safeY = height * Theme.SAFE_AREA;
        this.cardPad = Theme.scale(12);
        float headerHeight = headerHeight();
        float footerHeight = footerHeight(height);
        float sidePanelWidth = hasSidePanel ? railWidth(height) : 0;
        float panelGap = hasSidePanel ? dp(RAIL_GAP, height) : 0;

        float spentX = safeX * 2 + cardPad * 2 + sidePanelWidth + panelGap;
        float spentY = safeY * 2 + headerHeight + footerHeight;

        // Fixed point: the gutters follow the text, the text follows the cell, and the
        // cell follows the gutters. Bigger text means a smaller cell means smaller text,
        // so the relation oscillates rather than contracting; averaging each new estimate
        // with the last damps it out in a handful of passes.
        float text = Theme.scale(24);
        for (int pass = 0; pass < 10; pass++) {
            float span = Math.max(Theme.scale(60),
                    Math.min(width - spentX - gutterX(text, laneDigits),
                            height - spentY - gutterY(text, colClueLanes)));
            float next = clueSize(span / size, clueDigits);
            text = pass == 0 ? next : (text + next) * .5f;
        }
        // Then settle downward only, so the size we ship is guaranteed to be one the
        // squares it produced can actually hold — even for a pathological puzzle whose
        // every line alternates and needs ten clue lanes each way.
        for (int pass = 0; pass < 4; pass++) {
            float span = Math.max(Theme.scale(60),
                    Math.min(width - spentX - gutterX(text, laneDigits),
                            height - spentY - gutterY(text, colClueLanes)));
            float fitted = clueSize(span / size, clueDigits);
            if (fitted >= text) {
                break;
            }
            text = fitted;
        }

        clueTextSize = text;
        colCluePitch = text * COL_PITCH;
        clueInset = clueInsetFor(text);
        rowLanePitch = new float[rowClueLanes];
        rowLaneEdge = new float[rowClueLanes];
        for (int lane = 0; lane < rowClueLanes; lane++) {
            rowLanePitch[lane] = lanePitch(text, laneDigits[lane]);
            rowLaneEdge[lane] = rowLanePitch[lane]
                    + (lane == 0 ? 0 : rowLaneEdge[lane - 1]);
        }
        clueWidth = clueInset + rowLaneEdge[rowClueLanes - 1];
        clueHeight = gutterY(text, colClueLanes);

        // Centre the board plus its gutter inside the space left of the side panel. The
        // header and footer reserves already contain the card's own padding, so it is not
        // charged twice here.
        float regionLeft = safeX + cardPad + clueWidth;
        float regionRight = width - safeX - sidePanelWidth - panelGap - cardPad;
        float regionTop = safeY + headerHeight + clueHeight;
        float regionBottom = height - safeY - footerHeight;
        float availableWidth = regionRight - regionLeft;
        float availableHeight = regionBottom - regionTop;

        float boardSize = Math.max(Theme.scale(60),
                Math.min(availableWidth, availableHeight));
        cell = boardSize / size;

        left = regionLeft + Math.max(0, (availableWidth - boardSize) / 2);
        top = regionTop + Math.max(0, (availableHeight - boardSize) / 2);
        right = left + boardSize;
        bottom = top + boardSize;

        // Anchored, not derived. Deriving it from the board's right edge is what let the
        // rail swallow the board's leftover width and shift by 58 px between puzzles.
        panelRight = width - safeX;
        panelLeft = panelRight - sidePanelWidth;
    }

    /**
     * The rail's width: flat design pixels against the screen.
     *
     * <p>Deliberately {@link #dp} and not {@link Theme#scale}. {@code Theme.scale} is
     * driven by a screen height {@code Renderer} inflates by 16% when larger text is on,
     * which is right for type and wrong for furniture: it made the accessibility setting
     * widen the legend and narrow the grid. The rail holds the same pixels either way and
     * its copy fits itself to them.
     */
    public static float railWidth(float screenHeight) {
        return dp(RAIL_WIDTH, screenHeight);
    }

    /** Design pixels — 720p reference — against the real screen, ignoring the type scale. */
    public static float dp(float designPixels, float screenHeight) {
        return designPixels * screenHeight / 720f;
    }

    // ---- Clue geometry ---------------------------------------------------------------

    /** Centre of the row-clue lane {@code lane} steps out from the grid (0 = nearest). */
    public float rowClueCentreX(int lane) {
        int index = clampLane(lane);
        return left - clueInset - rowLaneEdge[index] + rowLanePitch[index] * .5f;
    }

    /** Far edge of a row-clue lane, i.e. the side away from the grid. */
    public float rowClueOuterX(int lane) {
        return left - clueInset - rowLaneEdge[clampLane(lane)];
    }

    /** Centre of the column-clue lane {@code lane} rows above the grid (0 = nearest). */
    public float colClueCentreY(int lane) {
        return top - clueInset - (lane + .5f) * colCluePitch;
    }

    /** Far edge of a column-clue lane, i.e. the side away from the grid. */
    public float colClueOuterY(int lane) {
        return top - clueInset - (lane + 1) * colCluePitch;
    }

    /** Outer edge of the row-clue gutter. */
    public float clueLeft() {
        return left - clueWidth;
    }

    /** Outer edge of the column-clue gutter. */
    public float clueTop() {
        return top - clueHeight;
    }

    /** Widest a row clue number may be drawn in this lane before it must be condensed. */
    public float rowClueNumberWidth(int lane) {
        return rowLanePitch[clampLane(lane)] * CLUE_FILL;
    }

    private int clampLane(int lane) {
        return lane < 0 ? 0 : Math.min(lane, rowClueLanes - 1);
    }

    /** Widest a column clue number may be drawn before it must be condensed. */
    public float colClueNumberWidth() {
        return cell * CLUE_FILL;
    }

    // ---- Board geometry --------------------------------------------------------------

    public float centreX(int column) {
        return left + (column + .5f) * cell;
    }

    public float centreY(int row) {
        return top + (row + .5f) * cell;
    }

    public float cellLeft(int column) {
        return left + column * cell;
    }

    public float cellTop(int row) {
        return top + row * cell;
    }

    /** True when there is enough width to show the "playing together" side rail. */
    public boolean hasRoomForPanel() {
        return panelRight - panelLeft >= dp(200, screenHeight);
    }

    /** Top of the rail: level with the paper card, under the title line. */
    public float panelTop() {
        return cardTop();
    }

    /**
     * Bottom of the rail: the safe edge of the screen.
     *
     * <p>The rail may run below the card because the ribbon no longer crosses it — the
     * ribbon is centred on the card and capped to the card's width, so it stays in the
     * column the board is in. That extra height is what lets the rail close with a tail
     * block instead of stopping halfway down the screen.
     */
    public float panelBottom() {
        return screenHeight - screenHeight * Theme.SAFE_AREA;
    }

    /** Outer bounds of the paper card behind the board and its clue gutters. */
    public float cardLeft() {
        return left - clueWidth - cardPad;
    }

    public float cardTop() {
        return top - clueHeight - cardPad;
    }

    public float cardRight() {
        return right + cardPad;
    }

    public float cardBottom() {
        return bottom + cardPad;
    }

    // ---- Sizing rules ----------------------------------------------------------------

    /**
     * The clue text size for a given square size.
     *
     * <p>Two things can crop a clue and nothing else does: the height of the row it labels,
     * and the width of the column it labels once every digit is counted. The smaller of
     * those is what fits. The ten-foot legibility floor is then allowed to push past that
     * — but by at most {@link #FLOOR_OVERRUN}, which the renderer pays for by tightening
     * the tracking between digits rather than by shrinking them. That bound is what keeps
     * a 20x20 board in big-text mode honest instead of printing numbers wider than its own
     * columns or taller than its own rows.
     */
    static float clueSize(float cell, int digits) {
        float fits = Math.min(cell * ROW_FIT, cell * CLUE_FILL / (digits * DIGIT_ADVANCE));
        float size = Math.min(fits, Theme.scale(CLUE_MAX));
        return Math.max(size,
                Math.min(Theme.scale(Theme.MIN_READABLE_SP), fits * FLOOR_OVERRUN));
    }

    /** Width of one row-clue lane: the widest number that lane can hold, plus air. */
    static float lanePitch(float text, int digits) {
        return text * (digits * DIGIT_ADVANCE + LANE_AIR);
    }

    static float gutterX(float text, int[] laneDigits) {
        float total = clueInsetFor(text);
        for (int digits : laneDigits) {
            total += lanePitch(text, digits);
        }
        return total;
    }

    static float gutterY(float text, int lanes) {
        return lanes * text * COL_PITCH + clueInsetFor(text);
    }

    /**
     * Vertical room to leave above the paper card, measured from the safe-area edge.
     *
     * <p>Derived from the single line {@code HudScene} actually draws there rather than
     * from a constant, so it tracks the type scale instead of quietly colliding with it
     * the next time {@code Theme} grows. Includes the card's own padding.
     *
     * <p>It used to reserve a second line as well — {@code CAPTION * 1.72}, about 62 px at
     * 1080p, taken off the top of every board at every size for a mode caption and a
     * progress readout. The mode now shares the wordmark's baseline and the progress moved
     * into the rail, where it is a bar rather than the smallest type on screen.
     */
    static float headerHeight() {
        return Theme.textSize(Theme.SUBHEAD)
                + Theme.scale(HudScene.HEADER_GAP) + Theme.scale(12)
                // The column tabs live in this band, between the title and the card.
                + CursorRenderer.marginBandHeight();
    }

    /**
     * Room to leave below the paper card for the message ribbon.
     *
     * <p>The ribbon's bottom edge is pinned to the safe line and it grows upward, so this
     * is derived from where it lands rather than guessed, and it reserves the ribbon's
     * two-line worst case — a message that wraps must never be able to change the board's
     * size underneath the players. Includes the card's own padding.
     */
    static float footerHeight(float screenHeight) {
        float ribbonTop = HudScene.ribbonInset(screenHeight)
                + HudScene.ribbonHeight(HudScene.RIBBON_MAX_LINES)
                + Theme.scale(HudScene.RIBBON_GAP) + Theme.scale(12);
        return Math.max(Theme.scale(24), ribbonTop - screenHeight * Theme.SAFE_AREA);
    }

    /**
     * Digits in the largest column clue. A column clue is the one that has to fit inside a
     * single square's width, so this is what bounds the clue text size.
     */
    static int columnDigits(Puzzle puzzle) {
        int largest = 1;
        for (int index = 0; index < puzzle.size; index++) {
            largest = Math.max(largest, largest(puzzle.colClues(index)));
        }
        return digitsOf(largest);
    }

    /**
     * Digits each row-clue lane has to hold, indexed from the lane nearest the grid.
     * Rows are laid out right to left, so the last group of every row shares lane 0.
     */
    static int[] rowLaneDigits(Puzzle puzzle, int lanes) {
        int[] largest = new int[lanes];
        for (int y = 0; y < puzzle.size; y++) {
            int[] clues = puzzle.rowClues(y);
            for (int i = clues.length - 1, lane = 0; i >= 0 && lane < lanes; i--, lane++) {
                largest[lane] = Math.max(largest[lane], clues[i]);
            }
        }
        int[] digits = new int[lanes];
        for (int lane = 0; lane < lanes; lane++) {
            digits[lane] = digitsOf(largest[lane]);
        }
        return digits;
    }

    private static int digitsOf(int value) {
        int digits = 1;
        for (int rest = Math.abs(value); rest >= 10; rest /= 10) {
            digits++;
        }
        return digits;
    }

    private static int largest(int[] clues) {
        int best = 1;
        for (int clue : clues) {
            best = Math.max(best, clue);
        }
        return best;
    }
}

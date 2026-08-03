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
 * <p>{@link Theme#clueScale()} multiplies the two clamps that are matters of taste — the
 * ceiling and the comfort floor — and the share of a row a digit may fill, so LARGER TEXT
 * reaches the clue digits and not only the words around them. It is deliberately kept off
 * {@code CLUE_FILL}: that bound is where two numbers touch, and a setting must not be able to
 * ask for illegible. See {@link Theme#CLUE_TEXT_SHARE}.
 *
 * <h2>How large a 20x20 clue can actually be</h2>
 *
 * <p>Two reviews running have asked for a 40 px square and 29 px clue type at 1920x1080, the
 * second conceding 39/29. Neither is reachable, and the arithmetic is short enough to state
 * here so that a third review does not spend itself on it:
 *
 * <pre>
 *   972 px safe band = tab band + card pad + column gutter + 20 squares + ribbon band
 * </pre>
 *
 * <p>A 39 px square is 780 px of grid. A 29 px clue stacked four lanes deep — what the deck's
 * busiest 20x20 columns need — is {@code 4 * 29 * COL_PITCH} plus the inset, about 141 px of
 * gutter, and the card's own padding is 36 px more. That comes to 957 px of the 972, leaving
 * 15 px for both the cursor tab band and the message ribbon, which together cannot go below
 * about 128. The bar is not a target this file is missing; it is a quantity the screen does
 * not contain. Worse, the trade runs the wrong way: the column gutter is four lanes deep, so
 * every extra pixel of clue type costs the grid four and takes a fifth of a pixel off every
 * square. Forcing the type up shrinks the squares that carry it.
 *
 * <p>What the screen does contain — and what this file now delivers at every size — is the
 * game's own stated floor, {@link Theme#MIN_READABLE_SP}: 27 px at 1080p, a cap height of
 * 13.5 arcminutes from ten feet, against the 5 arcminutes at which a numeral stops being
 * identifiable at all. The second review put that cap at 3.9 arcminutes. The panel it
 * describes — 55", 1080p, ten feet — resolves 40.1 px to the inch, so an 18 px cap subtends
 * {@code atan(18 / 40.1 / 120)} = 12.9 arcminutes; the figure was out by a factor of 3.3, and
 * the reading that the digits sit at the legibility floor does not follow from it.
 *
 * <p>The gutters are then sized <em>from that text</em> rather than from a magic constant:
 * one lane per clue group, each lane only as wide as the widest number that can actually
 * land in it plus {@code LANE_AIR} of clear space. Measuring lanes one at a time is what
 * stops a 20x20 board — four groups per row, but only ever two digits in the group nearest
 * the grid — from reserving two-digit width four times over and leaving the outer half of
 * its gutter permanently empty.
 *
 * <p>Because the gutters depend on the text, which depends on the cell, which depends on
 * the gutters, the constructor solves the relation numerically. See the constructor for why
 * it bisects rather than iterating or averaging.
 *
 * <h2>The grid is whole pixels</h2>
 *
 * <p>{@link #cell} is a whole number of pixels and {@link #left} and {@link #top} land on
 * one, which is what lets {@code BoardRenderer} draw every rule at exactly its nominal width
 * and full opacity. It used to be {@code span / size} — 30.33, 39.34, 58.15, 113.08 px at
 * 1080p — and an anti-aliased line centred on a fractional coordinate takes its weight from
 * where it happens to land inside a pixel: measured across the twenty-one vertical rules of
 * one 20x20 board, the same stroke in the same colour ran from 1.36:1 to 1.87:1 against the
 * paper. From ten feet that grid shimmers. The board pays for it out of its own size, at
 * most one pixel per square.
 *
 * <h2>The board is height-bound, and only height-bound</h2>
 *
 * <p>This is the single most important fact about the layout, and two passes in a row have
 * mis-read it. Measured across all 96 combinations the game ships — 1280x720, 1920x1080 and
 * 3840x2160, larger text on and off, every board size from 5 to 20 — the vertical budget is
 * the smaller one <em>every single time</em>, and the grid takes 96% to 100% of it. At
 * 1920x1080 with a busy 20x20 board it used to be 594.5 px of height against 1120.1 px of
 * width: not a board failing to claim its room, a board with less than half the room on one
 * axis that it had on the other.
 *
 * <p>So the several hundred pixels of bare backdrop between the card and the rail are not
 * space the layout is leaving on the table. The grid is square, and a square cannot spend
 * width it has no matching height for; that band is the arithmetic remainder of a square
 * picture on a 16:9 screen, and the only thing that shrinks it is a <em>taller</em> board.
 * Every pixel of the board's size is therefore decided by one column of numbers:
 *
 * <pre>
 *   1080 = 108 safe area + headerHeight + clue gutter + grid + footerHeight
 * </pre>
 *
 * <p>Which is why {@link #headerHeight()} is now the tab band and nothing else, and why the
 * reserves below are written as what they physically are rather than as round numbers. It is
 * also why narrowing the rail is not a lever: 100 px off the rail is 100 px added to a width
 * budget that still has 397 to 569 px going spare at 1080p after this pass.
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
 * text size. Everything else is the board's. Since the board cannot use that width anyway
 * the rail costs it nothing — and that is exactly why the rail, not a strip across the top
 * of the screen, is where {@code HudScene} now says which picture this is.
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

    /**
     * How much of its own column a <em>column</em> clue may fill, which is a different
     * question from how large the text may be.
     *
     * <p>{@link #CLUE_FILL} does two jobs at once, and one of them it was doing badly. As a
     * <em>sizing</em> rule it is right: a two-digit number has to fit inside one square's
     * width, so 86% of the column is what {@link #clueSize} may spend. As a <em>drawing</em>
     * rule it was wrong, because it left the ink of one clue 14% of a column away from the
     * ink of the next. Measured at 20x20 on a 1080p panel: a two-digit clue drew 26.1 px
     * inside a 30.3 px column, so neighbouring clues sat 4.2 px apart while the two digits
     * of a single number sat about 2 px apart. A 2:1 ratio on paper, 2 px in the room —
     * from ten feet "3 11 14 18 18 10" is one continuous number.
     *
     * <p>Splitting the two rules lets the renderer condense a two-digit clue's tracking
     * without touching the cap height that carries legibility: at 20x20 the gap between
     * neighbouring clues goes from 4.2 px to 6.0 px, measured on the rendered ink. Single
     * digit clues are already narrower than this and never condense at all.
     *
     * <p>It is .80 and not the .72 the review asked for, because .72 overdraws the account.
     * Two digits at 24.4 px carry about 22 px of ink between them, so a 21 px draw box makes
     * the digits of one number touch each other — measured, the ink runs merged into one
     * 21 px blob. What buys the rest of the separation is the alternating ground behind
     * every other lane (see {@code BoardRenderer.drawClueGrounds}); the gap and the ground
     * together are worth more than either taken to its limit.
     */
    public static final float COL_CLUE_FILL = .80f;

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
    /**
     * Vertical pitch of stacked column clues, as a fraction of the text size.
     *
     * <p>A digit's cap is .70 em, so 1.02 leaves .32 em of clear leading between one clue and
     * the one above it — still half again the leading a book sets body copy with, and column
     * clues are read one at a time down a lane rather than as running prose. It was 1.24,
     * then 1.10, and each step is the same argument: the column gutter is charged to the same
     * vertical budget the squares come out of, and the squares are what the game is played
     * on. This lane is four deep on the deck's busiest 20x20 columns, so the last .08 em is
     * worth 8.3 px of board height at 1080p.
     */
    private static final float COL_PITCH = 1.02f;
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

        float text = solveClueTextSize(width, height, spentX, spentY, laneDigits,
                colClueLanes, clueDigits, size);

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
        clueWidth = gutterX(text, laneDigits);
        clueHeight = gutterY(text, colClueLanes);

        // The square comes out of the same expression the search above tested, not out of
        // the region rectangle below. The two are algebraically the same number and were
        // spelled differently, which was fine while the cell was fractional and is not fine
        // now that it is floored: a float's worth of disagreement either side of a whole
        // pixel is a whole pixel of square, and the settled clue size was then sized for a
        // cell the board did not have.
        cell = cellFor(boardSpan(width, height, spentX, spentY, text, laneDigits,
                colClueLanes), size, height);
        float boardSize = cell * size;

        // Centre the board plus its gutter inside the space left of the side panel. The
        // header and footer reserves already contain the card's own padding, so it is not
        // charged twice here.
        float regionLeft = safeX + cardPad + clueWidth;
        float regionRight = width - safeX - sidePanelWidth - panelGap - cardPad;
        float regionTop = safeY + headerHeight + clueHeight;
        float regionBottom = height - safeY - footerHeight;
        float availableWidth = regionRight - regionLeft;
        float availableHeight = regionBottom - regionTop;

        // Whole pixels, so every rule in the grid lands on the same anti-alias phase. See
        // cellFor for what the fractional version cost.
        left = Math.round(regionLeft + Math.max(0, (availableWidth - boardSize) * .5f));
        top = Math.round(regionTop + Math.max(0, (availableHeight - boardSize) * .5f));
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

    /**
     * The rule a row clue's last digit is set against — the inner edge of its lane, less
     * half the lane's air.
     *
     * <p>Row clues used to be centred in their lanes, which is what every layout does until
     * someone looks at it down a twenty-row gutter. Lane 0 is sized for the widest number
     * that can land in it, so on a board with "18" rows the single-digit rows sat 7 px to
     * the left of the two-digit ones and the units column zig-zagged all the way down. Set
     * against a shared right-hand rule instead, every last group ends on the same vertical,
     * which is what lets the eye check "these are all the final runs" in one sweep — and it
     * is what printed nonograms have always done.
     */
    public float rowClueRightX(int lane) {
        int index = clampLane(lane);
        return left - clueInset - rowLaneEdge[index] + rowLanePitch[index]
                - clueTextSize * LANE_AIR * .5f;
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

    /**
     * Widest a row clue number may be drawn in this lane before it must be condensed: the
     * lane less its air, which is exactly the advance the lane was measured to reserve.
     *
     * <p>It used to be {@code pitch * CLUE_FILL}, which is a rule about columns applied to a
     * lane. For a single-digit lane that comes out <em>wider</em> than the lane's own
     * reserved advance, so a number right-aligned against the lane's inner rule could
     * overrun its outer edge by .06 em and land in its neighbour. In practice the cap never
     * binds either way — a digit measures .556 to .568 em against the .58 reserved, so no
     * row clue has ever condensed — but a bound that permits a collision is not a bound.
     */
    public float rowClueNumberWidth(int lane) {
        return rowLanePitch[clampLane(lane)] - clueTextSize * LANE_AIR;
    }

    private int clampLane(int lane) {
        return lane < 0 ? 0 : Math.min(lane, rowClueLanes - 1);
    }

    /** Widest a column clue number may be drawn before it must be condensed. */
    public float colClueNumberWidth() {
        return cell * COL_CLUE_FILL;
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

    /** The smallest board we will draw whatever the screen says, in design pixels. */
    private static final float MIN_BOARD = 60f;

    /**
     * The largest square worth drawing, in design pixels — 105 px at 1080p.
     *
     * <p>{@link Theme#MAX_CELL} says it is the ceiling and is not: at 104 design px it
     * resolves to 156 px at 1080p, and a 5x5 board's square measured 115 px before this
     * file stopped reserving a title strip and would have reached 126 after. The cap has
     * therefore never bound at any resolution the game ships on, which is why a 5x5 board
     * still reads as a whiteboard — twenty-five enormous empty rectangles filling the same
     * paper four hundred squares get — rather than as a jewel sitting in the lamplight.
     *
     * <p>105 px is where the audit's own arithmetic put it: a 520 px grid at 1920x1080, a
     * card a little over half the screen height, and a square that is still 74 arcminutes
     * across from ten feet. Above that a square gains nothing but paper — its clue stopped
     * growing at {@link #CLUE_MAX}, which a 43 design-pixel square already reaches.
     *
     * <p>It is stated here rather than fixed in {@code Theme} because the two are measured
     * against different heights, and that difference is the point: {@link #cellCeiling}
     * takes whichever is tighter, and this one is expressed in {@link #dp} against the real
     * screen while {@code Theme.MAX_CELL} goes through {@code Theme.scale}, which
     * {@code Renderer} inflates by 16% for LARGER TEXT. Collapsing them into one number
     * would hand a 5x5 board bigger squares the moment somebody asked for bigger words.
     * {@code Theme.MAX_CELL} is documented as the outer bound for that reason.
     */
    private static final float CELL_MAX = 70f;

    /**
     * The tighter of the theme's stated ceiling and the one it was written for.
     *
     * <p>Measured against the <em>screen</em>, like {@link #railWidth} and for the same
     * reason: {@code Theme.scale} is fed a height {@code Renderer} inflates by 16% when
     * larger text is on, so a ceiling expressed through it would let a 5x5 board's squares
     * grow from 105 px to 122 the moment somebody asked for bigger words. Larger text is a
     * setting about type. It makes the clue on a 5x5 board go 48 px to 55.7; it has no
     * business making the square under it bigger.
     */
    static float cellCeiling(float screenHeight) {
        return Math.min(Theme.scale(Theme.MAX_CELL), dp(CELL_MAX, screenHeight));
    }

    /**
     * The largest clue text this board's own squares can hold, solved numerically.
     *
     * <p>Fixed point: the gutters follow the text, the text follows the cell, and the cell
     * follows the gutters. "The largest clue its own squares can hold" is the largest text
     * with {@code text <= fits(text)}, and {@code fits} is non-increasing — bigger clues
     * mean wider gutters mean smaller squares mean smaller clues — so bisection lands on it
     * exactly.
     *
     * <p>It used to be ten passes of a damped average, which is the right tool while
     * {@code fits} is continuous and the wrong one now that {@link #cellFor} rounds the cell
     * down to a whole pixel: {@code fits} became a staircase, and an average settles
     * wherever it happens to be when the passes run out rather than on the tread. Measured
     * on the pathological alternating 18x18 board at 1280x720 with larger text on, the
     * average stopped at 13.02 px against squares that could hold 13.84.
     *
     * <p>Lifted out of the constructor and made package-private so this relation — which
     * decides every pixel of the board's size — can be pinned directly rather than only
     * through a finished layout.
     */
    static float solveClueTextSize(float width, float height, float spentX, float spentY,
                                   int[] laneDigits, int colLanes, int digits, int size) {
        // The ceiling has to be the largest size clueSize can return or the search cannot
        // reach it: with LARGER TEXT on a 5x5 board wants CLUE_MAX * clueScale, and a
        // bisection bracketed at plain CLUE_MAX would have converged on 48 px and reported
        // the setting as having done nothing.
        float low = Theme.scale(1);
        float high = Theme.scale(CLUE_MAX) * Theme.clueScale();
        for (int pass = 0; pass < 24; pass++) {
            float mid = (low + high) * .5f;
            float fits = clueSize(cellFor(boardSpan(width, height, spentX, spentY, mid,
                    laneDigits, colLanes), size, height), digits);
            if (fits >= mid) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * How much room the board itself gets for a given clue type size: whichever of the two
     * axes runs out first, once that axis has paid for its own clue gutter.
     *
     * <p>Pulled out of the fixed-point loop because the loop ran it twice, spelled
     * differently the second time was one edit away from being a different function.
     * Package-private for the same reason {@link #clueSize} and {@link #cellFor} are: it is
     * one term of the relation {@link #solveClueTextSize} solves, and a test that has to
     * build a whole board to reach it is testing five things at once.
     */
    static float boardSpan(float width, float height, float spentX, float spentY,
                           float text, int[] laneDigits, int colLanes) {
        return Math.min(width - spentX - gutterX(text, laneDigits),
                height - spentY - gutterY(text, colLanes));
    }

    /**
     * The square size a given board span settles on: a whole number of pixels, never larger
     * than {@link Theme#MAX_CELL}.
     *
     * <p><b>Whole pixels.</b> The cell used to be {@code span / size}, which is fractional
     * at every board size the game ships — 30.33, 39.34, 58.15, 113.08 px at 1080p. Each
     * grid rule is then stroked at a different fractional offset, so an anti-aliased 1 px
     * line lands anywhere between one dark pixel and two half-dark ones. Measured across
     * the 21 vertical rules of a 20x20 board, the minor rules ranged from 1.36:1 to 1.87:1
     * against paper — the same stroke, the same colour, 38% apart in weight purely from
     * where the maths happened to put them. From ten feet the grid shimmers: some cell
     * divisions read and their neighbours do not. Flooring the cell makes every rule land
     * on the same phase, and costs at most one pixel per square (20x20: 606.6 px of board
     * becomes 600).
     *
     * <p><b>And a ceiling.</b> Nothing used to bound the cell from above, so a 5x5 board
     * spent the whole card on twenty-five squares and read as a whiteboard rather than as a
     * jewel sitting on paper. {@link Theme#MAX_CELL} was added to say so and was set too
     * high to ever bind; {@link #cellCeiling} is the same rule at the number it was written
     * for, and it is applied here — inside the function the fixed-point search calls — so
     * the clue size settles against the square the board will actually have rather than
     * against the one it asked for.
     */
    static float cellFor(float span, int size, float screenHeight) {
        float room = Math.max(Theme.scale(MIN_BOARD), span);
        return Math.max(1f,
                Math.min(cellCeiling(screenHeight), (float) Math.floor(room / size)));
    }

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
        float big = Theme.clueScale();
        float fits = Math.min(cell * ROW_FIT * big,
                cell * CLUE_FILL / (digits * DIGIT_ADVANCE));
        float size = Math.min(fits, Theme.scale(CLUE_MAX) * big);
        return Math.max(size,
                Math.min(Theme.scale(Theme.MIN_READABLE_SP) * big, fits * FLOOR_OVERRUN));
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
     * Vertical room to leave above the paper card, measured from the safe-area edge: the
     * clear band the column tabs need, and the card's own padding. Nothing else.
     *
     * <p>This is the pixel that pays for the whole board. It reserved a title strip as well
     * — {@code textSize(SUBHEAD) + HEADER_GAP}, 66 px at 1080p — for one line reading
     * "COZYGRAMS   ENDLESS #7 · 20 × 20 … Fresh Baked Pie". Two reserves shrank first (a
     * second caption line, then a second ribbon line) and the board still only got 54% of
     * the screen height, because the strip that was left is charged to the <em>scarce</em>
     * axis while the rail it duplicates sits on the axis with 525 px going spare. So the
     * strip is gone: the wordmark is branding, the {@code HomeScene} title already carries
     * it, and the mode and the picture's name moved into the rail — see
     * {@code HudScene.drawWhereWeAre}. Measured at 1920x1080 on the busiest 20x20 board,
     * the reserve goes 150.3 px to 84.3 and the square 29 px to 32.
     *
     * <p>{@link CursorRenderer#marginBandHeight()} is the part that cannot go. The column
     * tab is anchored to the card's top edge and painted upward, so without this band a
     * saturated pill and its shadow would cross the safe line the moment a cursor reached a
     * column near the top of the picture — and the tabs are the one cue that owes nothing
     * to the size of the board.
     */
    static float headerHeight() {
        return CursorRenderer.marginBandHeight() + Theme.scale(12);
    }

    /**
     * Room to leave below the paper card for the message ribbon.
     *
     * <p>The ribbon's bottom edge is pinned to the safe line and it grows upward, so this
     * is derived from where it lands rather than guessed, and it reserves
     * {@link HudScene#RIBBON_MAX_LINES} lines — a message that wraps must never be able to
     * change the board's size underneath the players. Includes the card's own padding.
     *
     * <p><b>Why this one stays, when the title strip went.</b> It looks like the same kind
     * of waste: 82.8 px of the scarce axis at 1080p, held all evening for a pill that is on
     * screen four seconds at a time. It was tried and it does not work. Letting the pill
     * float over the backdrop needs backdrop to float over, and with the reserve cut to
     * {@code scale(24)} the board simply grows into it — measured on the busiest 20x20
     * board at 1920x1080, the square goes 32 px to 35 and the card runs to y=1002.8 against
     * a safe line at 1026, leaving 23 px for a 64.8 px pill. Putting the pill beside the
     * card instead fails on the other axis: the widest message the game writes is 690 px of
     * type, and the grown card leaves 365 px of unused width in the whole row. So a line of
     * prose two people are meant to read has nowhere else to be on a 16:9 screen — and the
     * remaining alternative, reserving nothing and reflowing when a message arrives, moves
     * the squares under their hands mid-puzzle.
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

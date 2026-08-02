package com.cozygrams.tv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Geometry proofs for the playing surface.
 *
 * <p>The board is the one screen a couple stares at for a whole evening, and it is also
 * the one screen whose layout is fully derived rather than hand-placed. These tests pin
 * the invariants that derivation has to keep, at both shipping resolutions, with and
 * without big text, for every board size and for the worst clue load a nonogram can have.
 *
 * <p>The "worst clue load" is not the busiest picture in the library — it is a line that
 * alternates filled and empty all the way across, which needs {@code size / 2} clue lanes.
 * No picture looks like that, but the layout must not fall apart if one ever does, and it
 * is the only case that exercises the sizing loop's failure modes.
 */
public class BoardLayoutTest {

    private static final float FULL_HD_W = 1920;
    private static final float FULL_HD_H = 1080;
    private static final float HD_W = 1280;
    private static final float HD_H = 720;
    /** How much {@code Renderer} inflates the design scale when big text is on. */
    private static final float BIG_TEXT = 1.16f;

    // ---- The invariants ----------------------------------------------------------------

    /** Everything the player has to see stays inside the overscan-safe rectangle. */
    @Test
    public void theCardAlwaysStaysInsideTheSafeArea() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            float safeX = w * Theme.SAFE_AREA;
            float safeY = h * Theme.SAFE_AREA;
            assertTrue(what + ": card runs off the left (" + board.cardLeft() + ")",
                    board.cardLeft() >= safeX - .5f);
            assertTrue(what + ": card runs off the top (" + board.cardTop() + ")",
                    board.cardTop() >= safeY - .5f);
            assertTrue(what + ": card runs off the right (" + board.cardRight() + ")",
                    board.cardRight() <= w - safeX + .5f);
            assertTrue(what + ": card runs off the bottom (" + board.cardBottom() + ")",
                    board.cardBottom() <= h - safeY + .5f);
        });
    }

    /**
     * The paper card never eats into the room the "playing together" rail needs, and the
     * rail takes a fixed slice rather than a share of the screen.
     *
     * <p>The rail used to be {@code max(scale(250), width * .19f)} wide and anchored to
     * the board's right edge, which meant it silently absorbed every pixel a height-capped
     * board failed to use — at 1080p it ended up exactly as wide as the puzzle. Worse,
     * turning on larger text made the grid narrower and the rail wider, so an
     * accessibility setting took pixels away from the thing being read. It is now a flat
     * {@link BoardLayout#RAIL_WIDTH} design pixels, right-anchored, so it neither grows
     * with the type scale nor moves between one board and the next.
     */
    @Test
    public void theRailTakesAFixedSliceAndNeverMoves() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            assertTrue(what + ": card overlaps the rail",
                    board.cardRight() <= board.panelLeft);
            assertEquals(what + ": rail is not its fixed width",
                    BoardLayout.dp(BoardLayout.RAIL_WIDTH, h),
                    board.panelRight - board.panelLeft, .5f);
            assertEquals(what + ": rail is not anchored to the safe-area edge",
                    w - w * Theme.SAFE_AREA, board.panelRight, .5f);
            assertTrue(what + ": rail reported as unusable",
                    board.hasRoomForPanel());
        });
    }

    /** Larger text must never hand the rail pixels taken from the board. */
    @Test
    public void largerTextDoesNotWidenTheRailAtTheBoardsExpense() {
        Puzzle puzzle = busiestGenerated(20);
        Theme.setScreenHeight(FULL_HD_H);
        BoardLayout normal = new BoardLayout(FULL_HD_W, FULL_HD_H, puzzle, true);
        float normalRail = normal.panelRight - normal.panelLeft;

        Theme.setScreenHeight(FULL_HD_H * 1.16f);
        BoardLayout big = new BoardLayout(FULL_HD_W, FULL_HD_H, puzzle, true);
        float bigRail = big.panelRight - big.panelLeft;
        Theme.setScreenHeight(FULL_HD_H);

        assertEquals("the rail grew when the type did", normalRail, bigRail, .5f);
    }

    /**
     * Every clue lane is wide enough for the widest number that can land in it, with real
     * air left over, and the lanes together fit the gutter they were measured for.
     */
    @Test
    public void everyClueFitsItsLaneWithAirToSpare() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            float outermost = board.rowClueOuterX(board.rowClueLanes - 1);
            assertTrue(what + ": row lanes overflow the gutter",
                    outermost >= board.clueLeft() - .5f);
            assertTrue(what + ": row gutter is padded with dead space",
                    outermost - board.clueLeft() <= board.clueTextSize);

            float previous = board.left;
            for (int lane = 0; lane < board.rowClueLanes; lane++) {
                float centre = board.rowClueCentreX(lane);
                float half = board.rowClueNumberWidth(lane) / 2;
                assertTrue(what + ": lane " + lane + " overlaps its neighbour",
                        centre + half <= previous + .5f);
                assertTrue(what + ": lane " + lane + " leaves no air",
                        board.rowClueNumberWidth(lane)
                                >= board.clueTextSize * BoardLayout.DIGIT_ADVANCE);
                previous = centre - half;
            }

            float topmost = board.colClueOuterY(board.colClueLanes - 1);
            assertTrue(what + ": column lanes overflow the gutter",
                    topmost >= board.clueTop() - .5f);
            assertTrue(what + ": column gutter is padded with dead space",
                    topmost - board.clueTop() <= board.clueTextSize);
        });
    }

    /**
     * The two ways a clue can be cropped. A clue must sit inside the height of the row it
     * labels and inside the width of the column it labels, digits and all — the second of
     * those is what a size rule driven only by the cell height keeps getting wrong.
     */
    @Test
    public void clueTextNeverOutgrowsItsOwnSquare() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            assertTrue(what + ": clue text is taller than its row (" + board.clueTextSize
                            + " in " + board.cell + ")",
                    board.clueTextSize <= board.cell * .82f);
            float widest = board.clueTextSize * board.clueDigits
                    * BoardLayout.DIGIT_ADVANCE;
            assertTrue(what + ": a " + board.clueDigits + "-digit column clue is wider "
                            + "than its column (" + widest + " in " + board.cell + ")",
                    widest <= board.cell * .96f);
        });
    }

    /** Nothing anywhere in the layout may come out zero or negative. */
    @Test
    public void nothingCollapsesOrGoesNegative() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            assertTrue(what + ": cell " + board.cell, board.cell > 1);
            assertTrue(what + ": clue text " + board.clueTextSize,
                    board.clueTextSize > 1);
            assertTrue(what + ": row gutter " + board.clueWidth, board.clueWidth > 0);
            assertTrue(what + ": column gutter " + board.clueHeight,
                    board.clueHeight > 0);
            assertTrue(what + ": clue inset " + board.clueInset, board.clueInset > 0);
            assertTrue(what + ": board is not square",
                    Math.abs((board.right - board.left) - (board.bottom - board.top))
                            < .01f);
            assertTrue(what + ": cells do not span the board",
                    Math.abs(board.cell * board.size - (board.right - board.left)) < .01f);
        });
    }

    /**
     * Clue numbers stay big enough to read from a sofa on any board a player will meet.
     *
     * <p>The floor is 80% of {@link Theme#MIN_READABLE_SP}: a 20x20 board with big text on
     * is the one case where the squares genuinely cannot hold the full comfort size, and
     * numbers that fit at 80% beat numbers at 100% that run into each other.
     */
    @Test
    public void cluesStayReadableFromTenFeet() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            if (board.rowClueLanes > board.size / 3) {
                return; // the pathological alternating board; legibility yields to fit
            }
            float floor = Theme.MIN_READABLE_SP * h / 720f * .80f;
            assertTrue(what + ": clue text " + board.clueTextSize + " below " + floor,
                    board.clueTextSize >= floor);
        });
    }

    /**
     * The clue size really is the fixed point of "gutters follow text, text follows cell".
     *
     * <p>That relation oscillates rather than contracting, so this is the test that would
     * catch a damping change leaving the numbers a size or two smaller than they could be.
     */
    @Test
    public void theSizingLoopSettlesOnTheLargestSizeThatFits() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            float ideal = BoardLayout.clueSize(board.cell, board.clueDigits);
            assertTrue(what + ": clue text " + board.clueTextSize + " exceeds what its "
                            + board.cell + "px squares hold (" + ideal + ")",
                    board.clueTextSize <= ideal + .01f);
            assertTrue(what + ": clue text " + board.clueTextSize + " settled well short "
                            + "of " + ideal, board.clueTextSize >= ideal * .95f);
        });
    }

    /**
     * Smaller boards have room for larger clues and always take it, up to the cap that
     * stops a 5x5 board from turning its clues into posters.
     */
    @Test
    public void smallerBoardsGetLargerClues() {
        Theme.setScreenHeight(FULL_HD_H);
        float previous = 0;
        float[] sizes = new float[5];
        for (int size = 20, i = 0; size >= 5; size -= 5, i++) {
            Puzzle puzzle = PuzzleGenerator.compose(0, 0, size);
            float text = new BoardLayout(FULL_HD_W, FULL_HD_H, puzzle, true).clueTextSize;
            assertTrue(size + "x" + size + " clues (" + text + ") are smaller than the "
                    + "bigger board's (" + previous + ")", text >= previous);
            sizes[i] = text;
            previous = text;
        }
        assertTrue("a 10x10 should get clearly larger clues than a 20x20",
                sizes[2] > sizes[0] * 1.3f);
    }

    /** The row gutter is measured per lane, so single-digit lanes stay narrow. */
    @Test
    public void singleDigitLanesDoNotReserveTwoDigitWidth() {
        boolean[][] art = new boolean[20][20];
        for (int y = 0; y < 20; y++) {
            art[y][0] = true;               // one square, then a gap, then a long run
            for (int x = 2; x < 20; x++) {
                art[y][x] = true;
            }
        }
        Theme.setScreenHeight(FULL_HD_H);
        Puzzle puzzle = new Puzzle(art, "one then eighteen");
        BoardLayout board = new BoardLayout(FULL_HD_W, FULL_HD_H, puzzle, true);

        assertTrue("rows should need two lanes", board.rowClueLanes == 2);
        assertTrue("the single-digit lane should be narrower than the two-digit one",
                board.rowClueNumberWidth(1) < board.rowClueNumberWidth(0) * .8f);
    }

    // ---- Sweep -------------------------------------------------------------------------

    private interface Check {
        void run(float width, float height, BoardLayout board, String what);
    }

    /**
     * Runs a check over both shipping resolutions, both text sizes, every board size, and
     * three clue loads: the endless deck, the authored Story Book, and the alternating
     * worst case.
     */
    private void forEveryScreenAndPuzzle(Check check) {
        float[][] screens = {{FULL_HD_W, FULL_HD_H}, {HD_W, HD_H}};
        for (float[] screen : screens) {
            for (float textScale : new float[]{1f, BIG_TEXT}) {
                float width = screen[0];
                float height = screen[1];
                Theme.setScreenHeight(height * textScale);
                String screenName = (int) width + "x" + (int) height
                        + (textScale > 1 ? " big-text" : "");

                for (int size = 5; size <= 20; size++) {
                    check(check, width, height, busiestGenerated(size),
                            screenName + " endless " + size);
                    check(check, width, height, alternating(size),
                            screenName + " alternating " + size);
                }
                for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
                    Puzzle puzzle = PuzzleLibrary.get(chapter);
                    check(check, width, height, puzzle,
                            screenName + " story '" + puzzle.name + "'");
                }
            }
        }
        Theme.setScreenHeight(FULL_HD_H);
    }

    private void check(Check check, float width, float height, Puzzle puzzle, String what) {
        check.run(width, height, new BoardLayout(width, height, puzzle, true), what);
    }

    /** The endless-deck board of this size with the most clue groups to place. */
    private Puzzle busiestGenerated(int size) {
        Puzzle busiest = null;
        int worst = -1;
        for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
            for (int variant = 0; variant < 3; variant++) {
                Puzzle puzzle = PuzzleGenerator.compose(subject, variant, size);
                int load = puzzle.widestRowClue() + puzzle.tallestColClue();
                if (load > worst) {
                    worst = load;
                    busiest = puzzle;
                }
            }
        }
        return busiest;
    }

    /** A board no artist would draw: every line alternates, so every line is all clues. */
    private Puzzle alternating(int size) {
        boolean[][] art = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                art[y][x] = (x + y) % 2 == 0;
            }
        }
        return new Puzzle(art, "checkerboard");
    }
}

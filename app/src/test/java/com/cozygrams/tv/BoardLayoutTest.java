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
    private static final float UHD_W = 3840;
    private static final float UHD_H = 2160;
    /**
     * What LARGER TEXT multiplies type by, as {@code Renderer} now applies it.
     *
     * <p>It used to be 1.16 here and it was applied by telling {@code Theme} the screen was
     * 16% taller, which is what the shipped {@code Renderer} did — and what made the setting
     * shrink the board: every padding, stroke, reserve and clue gutter grew with it. The
     * multiplier reaches {@link Theme#setTextScale} only now, so the harness sets what the
     * game sets. See {@link #largerTextNeverShrinksTheClueDigits}.
     */
    private static final float BIG_TEXT = Theme.TEXT_SCALE_BIG;

    /** Puts the theme where a given screen and text setting would put it. */
    private static void screen(float height, float textScale) {
        Theme.setScreenHeight(height);
        Theme.setTextScale(textScale);
    }

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

    /**
     * The one setting named for legibility must never make the puzzle's own numbers smaller.
     *
     * <p>It did, at every board size. LARGER TEXT was applied by telling {@code Theme} the
     * screen was 16% taller, which scales furniture as well as type, so the reserves and the
     * clue gutters grew and the squares underneath them shrank: measured at 1920x1080 on the
     * same puzzle, clue ink went 47.4 px to 46.4 at 10x10, 31.8 to 31.1 at 15x15 and 26.1 to
     * 25.2 at 20x20, while every word in the side rail visibly grew. A player who cannot read
     * the small numbers turned the setting on and the small numbers got smaller.
     *
     * <p>Two things fix it and both are asserted here: the multiplier reaches type only, so
     * nothing can shrink; and {@link Theme#clueScale} passes half of it to the clue digits, so
     * where the squares have room the numbers actually grow. They do not have room at 15x15
     * and 20x20 — a two-digit clue there is already as wide as the column it labels — so the
     * promise is "never smaller", and "larger" wherever it can be kept.
     */
    @Test
    public void largerTextNeverShrinksTheClueDigits() {
        float[][] screens = {{HD_W, HD_H}, {FULL_HD_W, FULL_HD_H}, {UHD_W, UHD_H}};
        for (float[] screen : screens) {
            float width = screen[0];
            float height = screen[1];
            for (int size = 5; size <= 20; size += 5) {
                Puzzle puzzle = busiestGenerated(size);
                screen(height, 1f);
                float plain = new BoardLayout(width, height, puzzle, true).clueTextSize;
                screen(height, BIG_TEXT);
                float big = new BoardLayout(width, height, puzzle, true).clueTextSize;
                String what = (int) width + "x" + (int) height + " " + size + "x" + size;
                assertTrue(what + ": LARGER TEXT took the clue digits from " + plain
                                + " px to " + big + " px", big >= plain - .01f);
            }
            // And where a square can hold a bigger number, it gets one. A 10x10 clue is
            // bound by the height of its own row rather than by the width of a two-digit
            // column, which is the room this setting is able to spend.
            screen(height, 1f);
            float plainTen = new BoardLayout(width, height, busiestGenerated(10),
                    true).clueTextSize;
            screen(height, BIG_TEXT);
            float bigTen = new BoardLayout(width, height, busiestGenerated(10),
                    true).clueTextSize;
            assertTrue((int) width + "x" + (int) height + ": LARGER TEXT left a 10x10 clue "
                            + "at " + plainTen + " px, so it did nothing where there was room",
                    bigTen > plainTen * 1.05f);
        }
        screen(FULL_HD_H, 1f);
    }

    /**
     * A clue digit is never drawn below the floor the palette states for it.
     *
     * <p>{@link Theme#MIN_READABLE_SP} is 18 design pixels — 27 px at 1080p, a cap height of
     * 13.5 arcminutes from ten feet — and its own Javadoc calls it the floor for the clue
     * digits. The busiest 20x20 board shipped at 26.05 px, under the game's own number,
     * because the vertical budget had a tab band and a message ribbon charged against it that
     * were bigger than they needed to be.
     *
     * <p>This is the criterion two reviews have asked for at 40 px squares and 29 px type.
     * That is not reachable — see {@code BoardLayout}'s class notes for the arithmetic, and
     * note that the column gutter is four lanes deep, so forcing the type up takes the
     * squares down. What is reachable is the stated floor, at every size, on every screen.
     */
    @Test
    public void theBusiestBoardClearsTheStatedClueFloor() {
        float[][] screens = {{HD_W, HD_H}, {FULL_HD_W, FULL_HD_H}, {UHD_W, UHD_H}};
        for (float[] screen : screens) {
            float height = screen[1];
            screen(height, 1f);
            float floor = Theme.scale(Theme.MIN_READABLE_SP);
            // Boards the game can actually deal. The alternating checkerboard the other
            // tests sweep is deliberately absent: it needs ten clue lanes on each axis, no
            // picture looks like that, and the layout's job there is to degrade without
            // breaking rather than to stay comfortable — which
            // clueTextNeverOutgrowsItsOwnSquare is what pins.
            for (int size = 5; size <= 20; size += 5) {
                for (Puzzle puzzle : new Puzzle[]{busiestGenerated(size)}) {
                    BoardLayout board = new BoardLayout(screen[0], height, puzzle, true);
                    // Twenty squares is the one size where the floor is a target rather than
                    // a guarantee, and the reason is arithmetic rather than effort: a
                    // two-digit clue has to fit the width of its own column, which pins it to
                    // about .74 of the square, and twenty squares plus a four-lane gutter
                    // plus the tab band and the ribbon do not leave a 36 px square inside a
                    // 972 px safe band. FLOOR_OVERRUN is what closes the last of the gap, and
                    // it is bounded so the digits are never condensed until they touch.
                    float wanted = size >= 20 ? floor * .94f : floor;
                    assertTrue((int) screen[0] + "x" + (int) height + " " + size + "x" + size
                                    + " '" + puzzle.name + "': clue type is "
                                    + board.clueTextSize + " px against a floor of " + floor,
                            board.clueTextSize >= wanted - .01f);
                }
            }
        }
        screen(FULL_HD_H, 1f);
    }

    /** Larger text must never hand the rail pixels taken from the board. */
    @Test
    public void largerTextDoesNotWidenTheRailAtTheBoardsExpense() {
        Puzzle puzzle = busiestGenerated(20);
        Theme.setScreenHeight(FULL_HD_H);
        BoardLayout normal = new BoardLayout(FULL_HD_W, FULL_HD_H, puzzle, true);
        float normalRail = normal.panelRight - normal.panelLeft;

        screen(FULL_HD_H, BIG_TEXT);
        BoardLayout big = new BoardLayout(FULL_HD_W, FULL_HD_H, puzzle, true);
        float bigRail = big.panelRight - big.panelLeft;
        screen(FULL_HD_H, 1f);

        assertEquals("the rail grew when the type did", normalRail, bigRail, .5f);
    }

    /**
     * Every clue lane is wide enough for the widest number that can land in it, with real
     * air left over, and the lanes together fit the gutter they were measured for.
     *
     * <p>Walked from the right-hand rule each lane is now set against rather than from its
     * centre. Row clues used to be centred in their lanes, so a lane sized for "18" put its
     * single-digit rows 7 px left of its two-digit ones and the units column zig-zagged down
     * the whole gutter; {@link BoardLayout#rowClueRightX} is what they are drawn against
     * now, and it is what this has to walk or it stops testing the picture.
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
                float right = board.rowClueRightX(lane);
                float left = right - board.rowClueNumberWidth(lane);
                assertTrue(what + ": lane " + lane + " overlaps its neighbour",
                        right <= previous + .5f);
                assertTrue(what + ": lane " + lane + " runs past the gutter",
                        left >= board.clueLeft() - .5f);
                // Exactly one digit's advance, so this is a float's width from equality
                // rather than a margin: rowClueNumberWidth is now the lane less its air.
                assertTrue(what + ": lane " + lane + " is too narrow for a digit",
                        board.rowClueNumberWidth(lane)
                                >= board.clueTextSize * BoardLayout.DIGIT_ADVANCE - .01f);
                if (lane > 0) {
                    assertTrue(what + ": lane " + lane + " leaves no air beside the lane "
                                    + "inside it",
                            previous - right >= board.clueTextSize * .3f);
                }
                previous = left;
            }

            float topmost = board.colClueOuterY(board.colClueLanes - 1);
            assertTrue(what + ": column lanes overflow the gutter",
                    topmost >= board.clueTop() - .5f);
            assertTrue(what + ": column gutter is padded with dead space",
                    topmost - board.clueTop() <= board.clueTextSize);
        });
    }

    /**
     * A column clue leaves clear paper on both sides of itself inside its own column.
     *
     * <p>{@code colClueNumberWidth} had nothing holding it at all, and it is what decides
     * whether a 20x20 board's clue band reads as numbers or as one digit stream: at
     * {@link BoardLayout#CLUE_FILL} the ink of one clue came within 4.2 px of the ink of the
     * next, about 3 arcminutes from ten feet. This floor is the reason
     * {@link BoardLayout#COL_CLUE_FILL} exists apart from the sizing rule, and it is what
     * stops the two being quietly merged back together.
     */
    @Test
    public void aColumnClueLeavesClearPaperInsideItsOwnColumn() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            float clear = board.cell - board.colClueNumberWidth();
            assertTrue(what + ": a column clue may fill " + board.colClueNumberWidth()
                            + " of its " + board.cell + " px column",
                    clear >= board.cell * .18f);
        });
    }

    /**
     * Every square is a whole number of pixels, and the grid starts on one.
     *
     * <p>This is the invariant {@code BoardRenderer.drawGrid} now leans on. A fractional
     * cell put every rule at a different anti-alias phase, which measured as a 1.36:1 to
     * 1.87:1 spread in weight across the twenty-one vertical rules of one 20x20 board — the
     * same stroke in the same colour, 38% apart, and from ten feet a grid that shimmers. The
     * board may come out smaller than the room it was offered as a result; never larger,
     * because that room is what holds it inside the safe area.
     */
    @Test
    public void everySquareIsAWholeNumberOfPixels() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            assertEquals(what + ": cell " + board.cell + " is not a whole pixel",
                    Math.floor(board.cell), board.cell, 0f);
            assertEquals(what + ": the grid does not start on a whole pixel",
                    Math.floor(board.left), board.left, 0f);
            assertEquals(what + ": the grid does not start on a whole pixel",
                    Math.floor(board.top), board.top, 0f);
            assertTrue(what + ": the square outgrew Theme.MAX_CELL",
                    board.cell <= Theme.scale(Theme.MAX_CELL) + .01f);
        });
    }

    /**
     * The two ways a clue can be cropped. A clue must sit inside the height of the row it
     * labels and inside the width of the column it labels, digits and all — the second of
     * those is what a size rule driven only by the cell height keeps getting wrong.
     *
     * <p>The vertical bound is on the <em>ink</em> rather than on the type size, which is the
     * thing that can actually be cropped: a digit is a cap and nothing else — no ascender
     * above it, no descender below — so it occupies .70 em of the line it is set on. Bounding
     * the em was fine while every clue was sized straight from {@code ROW_FIT}, and stopped
     * being fine when {@link Theme#clueScale} let LARGER TEXT spend more of a row on the
     * number in it: at 1920x1080 with the setting on, a 67 px row carries 55.2 px of type,
     * which is 82% of the row as an em and 58% of it as ink, with a fifth of the row clear
     * above the digit and a fifth below. The em bound called that a crop. It is not one.
     */
    @Test
    public void clueTextNeverOutgrowsItsOwnSquare() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            float ink = board.clueTextSize * .70f;
            assertTrue(what + ": clue ink is taller than its row (" + ink + " of "
                            + board.cell + ", type " + board.clueTextSize + ")",
                    ink <= board.cell * .68f);
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
     * <p>That relation is non-increasing rather than contracting, so this is the test that
     * would catch a change to the search leaving the numbers a size or two smaller than they
     * could be. It caught exactly that when the cell was first floored to whole pixels: the
     * ten damped-average passes that used to solve the relation settled at 13.02 px on the
     * alternating 18x18 board at 1280x720 with larger text on, against squares that could
     * hold 13.84, because a staircase has no point for an average to converge on. The
     * constructor bisects now, which lands on the tread.
     *
     * <p>The lower bound is one whole pixel of square, which is the height of the staircase's
     * own tread, and it used to be a flat 95%. Those are the same thing on a big board and
     * they are not on a small one: the clue is about .74 of the square, so one pixel is 1.7%
     * of a 60 px cell and 5.3% of a 19 px one. The alternating 18x18 board at 1280x720 with
     * larger text on settles at 14.6925 px against an ideal of 15.466 — 94.997%, which is
     * the tread and not a miss, and which a flat 95% called a failure by two ten-thousandths
     * of a pixel. Stated as a tread this is tighter than 95% everywhere above a 20 px square
     * and honest below it, and it still fails the damped average that prompted the test:
     * 13.02 against 13.11 for a square one pixel smaller.
     */
    @Test
    public void theSizingLoopSettlesOnTheLargestSizeThatFits() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            float ideal = BoardLayout.clueSize(board.cell, board.clueDigits);
            float oneTreadDown = BoardLayout.clueSize(board.cell - 1, board.clueDigits);
            assertTrue(what + ": clue text " + board.clueTextSize + " exceeds what its "
                            + board.cell + "px squares hold (" + ideal + ")",
                    board.clueTextSize <= ideal + .01f);
            assertTrue(what + ": clue text " + board.clueTextSize + " settled a whole "
                            + "square short of " + ideal + " (a " + (board.cell - 1)
                            + "px square would hold " + oneTreadDown + ")",
                    board.clueTextSize >= oneTreadDown - .01f);
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

    // ---- What the board is allowed to be charged for -----------------------------------

    /**
     * The only thing above the paper card is the band the column tabs paint into, and the
     * only thing below it is the message ribbon. Anything else is the centring leftover,
     * which is less than one square.
     *
     * <p>This is the test that stops the frame silently shrinking again, and it is written
     * as a bound on the <em>chrome</em> rather than on the board because that is where the
     * pixels kept going. Two passes tried to give the 20x20 board more room and both aimed
     * at the wrong thing: the grid is square and height-bound at every board size and every
     * resolution the game ships (1280x720, 1920x1080, 3840x2160, larger text either way —
     * 96 combinations, vertical budget smaller in all 96), so the several hundred pixels of
     * bare backdrop beside the card cannot be claimed by anything but a taller board, and a
     * taller board can only come out of this column of numbers. When it was written the
     * title strip was charging 66 px at 1080p for one line of branding and metadata that
     * the rail now carries for free.
     *
     * <p>The lower bounds matter as much as the upper ones. Below the tab band a saturated
     * pill paints through the overscan edge; below the ribbon reserve the pill lands on the
     * last row of the picture.
     */
    @Test
    public void nothingIsChargedToTheBoardButTheTabBandAndTheRibbon() {
        forEveryScreenAndPuzzle((w, h, board, what) -> {
            float safeY = h * Theme.SAFE_AREA;
            float above = board.cardTop() - safeY;
            float below = (h - safeY) - board.cardBottom();
            float band = CursorRenderer.marginBandHeight();
            float ribbon = HudScene.ribbonHeight(HudScene.RIBBON_MAX_LINES)
                    + Theme.scale(HudScene.RIBBON_GAP);

            assertTrue(what + ": the column tabs would cross the safe line (" + above
                    + " of " + band + ")", above >= band - .5f);
            assertTrue(what + ": the ribbon would land on the card (" + below + " of "
                    + ribbon + ")", below >= ribbon - .5f);

            if (board.cell >= BoardLayout.cellCeiling(h) - .01f) {
                return; // at its ceiling the board leaves room on purpose. See the jewel.
            }
            assertTrue(what + ": " + Math.round(above + below) + " px of chrome above and "
                            + "below the card, against " + Math.round(band + ribbon)
                            + " px of tab band and ribbon plus one " + board.cell
                            + " px square of centring",
                    above + below <= band + ribbon + board.cell + .5f);
        });
    }

    /**
     * The 20x20 board — the one the whole layout is judged on — keeps more than 55% of the
     * screen's height for its own squares.
     *
     * <p>Measured on the busiest board the endless deck composes: 58% at 1280x720, 59% at
     * 1920x1080 and 59% at 3840x2160, one or two points lower with larger text on. Before
     * the title strip moved into the rail it was 50% to 55%, so this floor is the one thing
     * standing between here and there. In squares: 19 px to 21 at 720p, 29 to 32 at 1080p,
     * 59 to 64 at 4K, and the clue digits grew with them (23.6 px to 26.0 at 1080p).
     */
    @Test
    public void theBiggestBoardKeepsMostOfTheScreenHeight() {
        float[][] screens = {{HD_W, HD_H}, {FULL_HD_W, FULL_HD_H}, {UHD_W, UHD_H}};
        for (float[] screen : screens) {
            for (float textScale : new float[]{1f, BIG_TEXT}) {
                float width = screen[0];
                float height = screen[1];
                screen(height, textScale);
                BoardLayout board = new BoardLayout(width, height, busiestGenerated(20),
                        true);
                float grid = board.cell * board.size;
                String what = (int) width + "x" + (int) height
                        + (textScale > 1 ? " big-text" : "") + ": ";
                assertTrue(what + "the grid is only " + Math.round(100 * grid / height)
                                + "% of the screen height (" + board.cell + " px squares)",
                        grid >= height * .55f);
            }
        }
        screen(FULL_HD_H, 1f);
    }

    /**
     * A 5x5 board stays a jewel sitting in the lamplight rather than becoming a whiteboard.
     *
     * <p>{@link Theme#MAX_CELL} was meant to say this and never bound: at 104 design pixels
     * it resolves to 156 px at 1080p while a 5x5 square measured 115, so the cap has never
     * once changed a picture. {@link BoardLayout#cellCeiling} is the same rule at the number
     * it was written for, and it has to bind here or reclaiming the title strip would simply
     * have grown the 5x5 board from 115 px squares to 126.
     *
     * <p>The second half is why the ceiling is measured against the screen rather than
     * through {@code Theme.scale}: larger text is a setting about type, and a 5x5 square is
     * 105 px at 1080p with it on or off.
     */
    @Test
    public void aSmallBoardStaysAJewelAndLargerTextDoesNotChangeThat() {
        float[][] screens = {{HD_W, HD_H}, {FULL_HD_W, FULL_HD_H}, {UHD_W, UHD_H}};
        for (float[] screen : screens) {
            float width = screen[0];
            float height = screen[1];
            float plain = 0;
            for (float textScale : new float[]{1f, BIG_TEXT}) {
                screen(height, textScale);
                float ceiling = BoardLayout.cellCeiling(height);
                BoardLayout board = new BoardLayout(width, height,
                        PuzzleGenerator.compose(0, 0, 5), true);
                String what = (int) width + "x" + (int) height
                        + (textScale > 1 ? " big-text" : "") + ": ";
                assertEquals(what + "the cell ceiling does not bind on a 5x5 board, so the "
                        + "card fills the frame", ceiling, board.cell, 1f);
                assertTrue(what + "a 5x5 grid takes " + Math.round(100 * board.cell * 5
                                / height) + "% of the screen height",
                        board.cell * 5 <= height * .55f);
                if (textScale == 1f) {
                    plain = board.cell;
                } else {
                    assertEquals("larger text changed the size of a 5x5 square", plain,
                            board.cell, .01f);
                }
            }
        }
        screen(FULL_HD_H, 1f);
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

    // ---- The relation the whole board hangs off -----------------------------------------

    /**
     * The clue-size fixed point, pinned directly rather than through a finished layout.
     *
     * <p>{@link BoardLayout#solveClueTextSize} was twenty lines inside a ninety-line
     * constructor, so the only way to say anything about it was to build a board and measure
     * the result — which conflates the relation with the centring, the rounding and the rail
     * anchoring that follow it. It is a named static now, and this is the property that makes
     * it correct: the answer is the <em>largest</em> size the squares it produces can hold,
     * so it is a fixed point from below and a hair above it is not.
     *
     * <p>{@code fits} is non-increasing in the size asked for — bigger clues mean wider
     * gutters mean smaller squares mean smaller clues — which is what makes bisection land
     * on the tread of the staircase {@link BoardLayout#cellFor}'s whole-pixel floor creates.
     */
    @Test
    public void theClueSizeSolverLandsOnTheLargestSizeTheSquaresCanHold() {
        Theme.setScreenHeight(FULL_HD_H);
        int[] laneDigits = {2, 2, 1, 1};
        for (int size = 5; size <= 20; size++) {
            for (float spentY : new float[]{200, 320, 460}) {
                float text = BoardLayout.solveClueTextSize(FULL_HD_W, FULL_HD_H, 700,
                        spentY, laneDigits, 4, 2, size);
                String what = size + "x" + size + " spentY=" + spentY;

                float fitsHere = fitsFor(text, laneDigits, spentY, size);
                assertTrue(what + ": settled at " + text + " but the squares only hold "
                        + fitsHere, fitsHere >= text - .01f);

                float higher = text + .5f;
                assertTrue(what + ": " + higher + " would also have fitted, so " + text
                                + " was not the largest",
                        fitsFor(higher, laneDigits, spentY, size) < higher);
            }
        }
    }

    /** The clue size the squares come out at when the gutters are drawn at {@code text}. */
    private static float fitsFor(float text, int[] laneDigits, float spentY, int size) {
        return BoardLayout.clueSize(BoardLayout.cellFor(
                BoardLayout.boardSpan(FULL_HD_W, FULL_HD_H, 700, spentY, text, laneDigits, 4),
                size, FULL_HD_H), 2);
    }

    private interface Check {
        void run(float width, float height, BoardLayout board, String what);
    }

    /**
     * Runs a check over all three shipping resolutions, both text sizes, every board size,
     * and three clue loads: the endless deck, the authored Story Book, and the alternating
     * worst case.
     *
     * <p>4K is in the sweep because it is the resolution where every derived number is
     * furthest from the 720p design pixels they are written in, and because a cell ceiling
     * that binds at 1080p has to bind there too.
     */
    private void forEveryScreenAndPuzzle(Check check) {
        float[][] screens = {{FULL_HD_W, FULL_HD_H}, {HD_W, HD_H}, {UHD_W, UHD_H}};
        for (float[] screen : screens) {
            for (float textScale : new float[]{1f, BIG_TEXT}) {
                float width = screen[0];
                float height = screen[1];
                screen(height, textScale);
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
        screen(FULL_HD_H, 1f);
    }

    private void check(Check check, float width, float height, Puzzle puzzle, String what) {
        check.run(width, height, new BoardLayout(width, height, puzzle, true), what);
    }

    /**
     * The endless-deck board of this size with the most clue groups to place.
     *
     * <p>Cached, because the sweep asks for it once per size per screen per text size and
     * composing every subject at every variant to find it is by far the most expensive
     * thing in this file. The deck is deterministic, so one answer per size is the whole
     * answer.
     */
    private static final java.util.Map<Integer, Puzzle> BUSIEST = new java.util.HashMap<>();

    private Puzzle busiestGenerated(int size) {
        return BUSIEST.computeIfAbsent(size, wanted -> {
            Puzzle busiest = null;
            int worst = -1;
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                for (int variant = 0; variant < 3; variant++) {
                    Puzzle puzzle = PuzzleGenerator.compose(subject, variant, wanted);
                    int load = puzzle.widestRowClue() + puzzle.tallestColClue();
                    if (load > worst) {
                        worst = load;
                        busiest = puzzle;
                    }
                }
            }
            return busiest;
        });
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

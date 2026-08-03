package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The board's promises about being <em>seen</em>, as opposed to being drawn.
 *
 * <p>Every number here comes out of a design review that failed the build, and each one is
 * the kind of thing that is invisible in a screenshot taken eighteen inches from a monitor
 * and disqualifying on a 55" panel ten feet away:
 *
 * <ul>
 *   <li>The two cursors have to be <b>equally</b> findable. Not both findable — equally.
 *       Rose is the more prominent player and she was the one losing.</li>
 *   <li>A clue a player re-reads to check a line off has to stay readable after the line
 *       is finished.</li>
 *   <li>A cue smaller than the eye can resolve from a sofa is not a cue.</li>
 * </ul>
 *
 * <p>These run with {@code returnDefaultValues}, where every {@code android.graphics.Color}
 * static answers zero, so everything asserted here is computed with the bit arithmetic in
 * {@link Theme} and {@link Draw} rather than through the framework.
 */
public class BoardRendererTest {

    /** A 55" 1080p panel from ten feet: one pixel is this many arcminutes. */
    private static final double ARCMIN_PER_PIXEL =
            Math.toDegrees(Math.atan(1 / (1080 / (55 * 9 / Math.hypot(16, 9))) / 120)) * 60;

    /** Below about this, a shape stops being resolvable and becomes a smudge. */
    private static final double RESOLVABLE_ARCMINUTES = 8;

    /** The floor the review set for the crosshair bands. */
    private static final double BAND_FLOOR = 1.4;

    private final UiState plain = new UiState();
    private final UiState loud = new UiState();

    @Before
    public void setUp() {
        Theme.setScreenHeight(1080);
        Comfort.get().restoreDefaults();
        loud.highContrastOn = true;
    }

    @After
    public void tearDown() {
        Theme.setScreenHeight(720);
        Comfort.get().restoreDefaults();
    }

    // ---- The crosshair bands ------------------------------------------------------------

    /**
     * Both bands clear the perceptual floor, and — the part that actually matters — they
     * clear it by the same amount. A fixed alpha cannot do this: at 40/255 Rose's band
     * measured 1.09:1 on paper and Sky's, being a far lighter hue, measured less still.
     */
    @Test
    public void bothPlayersCrosshairBandsAreEqualAndAboveThreshold() {
        for (boolean distinct : new boolean[]{false, true}) {
            Comfort.get().distinctPlayers = distinct;
            for (boolean contrast : new boolean[]{false, true}) {
                double rose = bandRatio(0, contrast);
                double sky = bandRatio(1, contrast);
                assertTrue("Rose's band is only " + rose + ":1 (distinct=" + distinct
                        + ", contrast=" + contrast + ")", rose >= BAND_FLOOR);
                assertTrue("Sky's band is only " + sky + ":1 (distinct=" + distinct
                        + ", contrast=" + contrast + ")", sky >= BAND_FLOOR);
                assertEquals("the two bands are not the same strength", rose, sky, .06);
            }
        }
    }

    @Test
    public void extraContrastStrengthensTheBands() {
        for (int player = 0; player < 2; player++) {
            assertTrue("extra contrast did not strengthen player " + player + "'s band",
                    bandRatio(player, true) > bandRatio(player, false) + .2);
        }
    }

    /** The band is a wash, never an opaque stripe that erases the paper beneath it. */
    @Test
    public void aBandNeverGoesOpaque() {
        for (boolean distinct : new boolean[]{false, true}) {
            Comfort.get().distinctPlayers = distinct;
            for (int player = 0; player < 2; player++) {
                int alpha = BoardRenderer.bandColor(player, 1.8) >>> 24;
                assertTrue("player " + player + "'s band is " + alpha + "/255", alpha < 220);
            }
        }
    }

    private static double bandRatio(int player, boolean highContrast) {
        return Theme.contrastRatio(BoardRenderer.bandOnPaper(player, highContrast),
                Theme.PAPER);
    }

    /**
     * When the two cursors land on the same line, the band splits along it instead of
     * compositing.
     *
     * <p>Two independent full-width washes on one row multiply into a mauve that belongs to
     * neither player — measured 1.95:1 against paper where Rose alone is 1.54:1 and Sky
     * 1.45:1 — so the one moment the colour is most worth having said "somebody is here",
     * 30% louder than either band was designed to be. Split, the two stripes tile the line
     * exactly once between them and neither is thinner than the other.
     */
    @Test
    public void aSharedLineIsSplitBetweenTheTwoPlayersRatherThanComposited() {
        for (boolean shared : new boolean[]{false, true}) {
            float rose = BoardRenderer.stripeTo(shared, 0) - BoardRenderer.stripeFrom(shared, 0);
            float sky = BoardRenderer.stripeTo(shared, 1) - BoardRenderer.stripeFrom(shared, 1);
            assertEquals("the two stripes are not the same width", rose, sky, 1e-4);
            if (shared) {
                assertEquals("the stripes do not tile the line exactly once", 1f,
                        rose + sky, 1e-4);
                assertEquals("the stripes do not meet", BoardRenderer.stripeTo(true, 0),
                        BoardRenderer.stripeFrom(true, 1), 1e-4);
                // At 20x20 a 30 px line splits into 15 px stripes: about 11 arcminutes from
                // ten feet, well clear of resolvable. Anything below .4 would not be.
                assertTrue("a shared stripe is only " + rose + " of the line", rose >= .4f);
            } else {
                assertEquals("a lone band does not cover its whole line", 1f, rose, 1e-4);
            }
        }
    }

    /** The split holds through the glide, not only once both cursors have landed. */
    @Test
    public void twoCursorsCountAsSharingALineBeforeTheySettleOnIt() {
        assertTrue("settled cursors on one line are not seen as sharing it",
                BoardRenderer.sameLine(6f, 6f));
        assertTrue("the split does not engage until the glide has finished",
                BoardRenderer.sameLine(5.6f, 6f));
        assertTrue("the split lets go the moment a cursor starts to leave",
                BoardRenderer.sameLine(6f, 6.4f));
        assertTrue("two cursors a square apart are treated as sharing a line",
                !BoardRenderer.sameLine(5f, 6f));
    }

    // ---- The cursor plate -----------------------------------------------------------------

    /**
     * The plate is the cue that carries findability, and it is drawn identically for both
     * players — so the only thing left to check is that it is a sane shape at every board
     * size, from a 5x5's fat cells to a 20x20's 33-pixel ones.
     */
    @Test
    public void thePlateIsWellFormedAtEverySize() {
        for (float height : new float[]{720, 1080, 1440, 2160}) {
            Theme.setScreenHeight(height);
            // The band of real square sizes: a 20x20 board's cells at the bottom, a 5x5
            // board's at the top, measured in the design pixels everything else scales in.
            for (float design = 20; design <= 150; design += .5f) {
                assertPlate(Theme.scale(design));
            }
        }
        // And on the real layouts, at both resolutions the game ships for. Screen height
        // is set first because that is the order Renderer draws in: everything BoardLayout
        // hands back is already in the scale Theme is holding.
        for (int size : new int[]{5, 10, 15, 20}) {
            Theme.setScreenHeight(1080);
            assertPlate(new BoardLayout(1920, 1080, new GameState(3, size).puzzle,
                    true).cell);
            Theme.setScreenHeight(720);
            assertPlate(new BoardLayout(1280, 720, new GameState(3, size).puzzle,
                    true).cell);
        }
    }

    private static void assertPlate(float cell) {
        float half = BoardRenderer.plateHalf(cell);
        float ring = BoardRenderer.plateRing(cell);
        float inner = BoardRenderer.plateInner(cell);
        float border = BoardRenderer.plateBorder(cell);

        assertTrue("the plate does not cover its own square at cell " + cell,
                half > cell * .5f);
        // It reaches past the square, but never so far that it swallows a neighbour: the
        // old finder reticle enclosed 2.3 cells and could only ever say roughly-where.
        assertTrue("the plate spans " + (half * 2 / cell) + " cells at cell " + cell,
                half * 2 <= cell * 1.75f);
        assertTrue("the border is outside the plate at cell " + cell,
                ring + border * .5f < half);
        assertTrue("the mark overlaps the border at cell " + cell,
                inner < ring - border * .5f);
        // Whatever else happens, the square's own mark stays recognisably itself.
        assertTrue("the mark is only " + (inner * 2 / cell) + " of its square at cell "
                + cell, inner * 2 >= cell * .55f);
    }

    /**
     * A gliding plate always shows one opaque mark.
     *
     * <p>The mark used to be drawn on its square's centre while the plate was drawn on the
     * smoothed cursor position, so mid-glide the plate showed part of one tile and part of
     * the paper beside it, split by a hard vertical seam that jumped sides the instant
     * {@code Math.round} crossed the halfway point. Both squares are drawn at the plate's
     * centre now and handed over as it travels — and the handover is deliberately not a
     * 50/50 crossfade, because two marks at half strength composited over cream leave a
     * quarter of the plate showing through, which is its own kind of half-erased square.
     */
    @Test
    public void aGlidingPlateNeverShowsCreamThroughItsMark() {
        for (float across = 0; across < 1f; across += .01f) {
            float leaving = BoardRenderer.leavingFade(across);
            float entering = BoardRenderer.enteringFade(across);
            assertTrue("neither mark is opaque " + across + " of the way across",
                    Math.max(leaving, entering) >= 1f - 1e-4);
            assertTrue("a fade is out of range at " + across,
                    leaving >= 0 && leaving <= 1 && entering >= 0 && entering <= 1);
        }
        // The two ends of a stride have to agree with the two ends of the next one, or the
        // mark flickers once per square crossed.
        assertEquals("a settled plate does not show its own square", 1f,
                BoardRenderer.leavingFade(0f), 1e-4);
        assertEquals("a settled plate shows the next square as well", 0f,
                BoardRenderer.enteringFade(0f), 1e-4);
        assertEquals("the arriving square is not fully there by the end of the stride", 1f,
                BoardRenderer.enteringFade(.999f), 1e-3);
        assertTrue("the departing square is still visible at the end of the stride",
                BoardRenderer.leavingFade(.999f) <= .01f);
    }

    /**
     * The ruling reads as a printed grid: a frame heavier than the five-square rules, which
     * are heavier than the rules between squares, at every size and in whole pixels.
     *
     * <p>{@code i == 0} and {@code i == size} used to be drawn exactly like the interior
     * fives, so the block at columns 0-4 appeared to carry on into the clue gutter and the
     * eye had to find the board's edge from where the numbers stopped.
     */
    @Test
    public void theFrameOutweighsTheFivesAndTheFivesOutweighTheRest() {
        for (float height : new float[]{720, 1080, 2160}) {
            Theme.setScreenHeight(height);
            for (int size : new int[]{5, 10, 15, 20}) {
                BoardLayout board = new BoardLayout(height * 16 / 9, height,
                        new GameState(7, size).puzzle, true);
                for (boolean bold : new boolean[]{false, true}) {
                    String at = size + "x" + size + " at " + (int) height + (bold ? " bold" : "");
                    float edge = Math.round(BoardRenderer.ruleWeight(board.cell, true, true, bold));
                    float five = Math.round(BoardRenderer.ruleWeight(board.cell, false, true, bold));
                    float thin = Math.round(BoardRenderer.ruleWeight(board.cell, false, false, bold));
                    assertTrue(at + ": the frame (" + edge + ") does not outweigh a five ("
                            + five + ")", edge > five);
                    assertTrue(at + ": a five (" + five + ") does not outweigh a rule ("
                            + thin + ")", five > thin);
                    assertTrue(at + ": a rule rounds away to nothing", thin >= 1);
                }
            }
        }
    }

    /** Bold cursors thicken the border without moving the plate it is drawn on. */
    @Test
    public void boldCursorsThickenTheBorderInPlace() {
        float cell = 44f;
        float plain = BoardRenderer.plateBorder(cell);
        float plate = BoardRenderer.plateHalf(cell);
        Comfort.get().boldCursor = true;
        assertTrue("bold cursors did not thicken the border",
                BoardRenderer.plateBorder(cell) > plain * 1.2f);
        assertEquals("bold cursors moved the plate", plate,
                BoardRenderer.plateHalf(cell), 1e-4);
        assertTrue("a bold border ate the mark",
                BoardRenderer.plateInner(cell) * 2 >= cell * .55f);
    }

    // ---- Squares ---------------------------------------------------------------------------

    /**
     * A crossed square really does differ in value from the paper under it — including from
     * the five-square checker, which is the comparison that failed.
     *
     * <p>The wash was a fixed alpha of 26 and measured 1.17:1 on paper, while the checker
     * printed underneath measured 1.087:1. An untouched square on a shaded block and a
     * crossed square on a plain one were therefore 1.07:1 apart, which is nothing from a
     * sofa, and the board's promise that filled, crossed and untouched separate by value
     * held for filled and for nothing else. Asked for as {@link Theme#CROSS_RATIO} through
     * {@link Theme#washAlpha} the pair is 1.21:1 apart, measured on the rendered pixels.
     */
    @Test
    public void aCrossedSquareOutrunsTheCheckerItIsPrintedOn() {
        for (boolean bold : new boolean[]{false, true}) {
            int wash = BoardRenderer.crossWash(bold);
            double asked = bold ? Theme.CROSS_RATIO_BOLD : Theme.CROSS_RATIO;
            int onPaper = Draw.blend(Theme.PAPER, wash, (wash >>> 24) / 255f);
            int onShade = Draw.blend(Theme.PAPER_SHADE, wash, (wash >>> 24) / 255f);

            double plain = Theme.contrastRatio(onPaper, Theme.PAPER);
            assertTrue("a crossed square is only " + plain + ":1 on paper (bold=" + bold
                    + ")", plain >= asked - .03);
            // The one that decides whether "ruled out" survives at a glance: a crossed
            // square on plain paper against an untouched square on a shaded block.
            double confusable = Theme.contrastRatio(onPaper, Theme.PAPER_SHADE);
            assertTrue("a crossed square is only " + confusable + ":1 against the checker",
                    confusable >= 1.15);
            assertTrue("the wash went opaque and erased the square it is washing over",
                    (wash >>> 24) < 128);
            assertTrue("a crossed square is lighter on shaded paper than on plain",
                    Theme.contrastRatio(onShade, Theme.PAPER) > plain);
        }
    }

    /**
     * The tile grain is grain, not corduroy.
     *
     * <p>It was {@code x + y}, a diagonal ramp: taken modulo four it repeats every four
     * cells along both axes, and a regular diagonal grating is the one spatial pattern the
     * visual system amplifies instead of averaging. Measured across rows 6-10 of a 20x20
     * board the filled-tile centres cycled 88, 85, 82, 79 in perfect diagonal step.
     */
    @Test
    public void theTileGrainHasNoPeriod() {
        int[] counts = new int[4];
        int acrossMatches = 0;
        int downMatches = 0;
        int diagonalMatches = 0;
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                int shade = BoardRenderer.grain(x, y);
                assertTrue("grain is out of range at " + x + "," + y,
                        shade >= 0 && shade < 4);
                counts[shade]++;
                acrossMatches += shade == BoardRenderer.grain(x + 1, y) ? 1 : 0;
                downMatches += shade == BoardRenderer.grain(x, y + 1) ? 1 : 0;
                diagonalMatches += shade == BoardRenderer.grain(x + 1, y + 1) ? 1 : 0;
            }
        }
        for (int shade = 0; shade < 4; shade++) {
            assertTrue("shade " + shade + " lands on " + counts[shade] + " of 400 tiles",
                    counts[shade] > 400 / 8);
        }
        // Chance is 100 of 400 in each direction. x + y scores 400 down the diagonal;
        // `x * PHI ^ y * PRIME` with one shift scores 360 across, because the low two bits
        // of an odd multiple barely move. A hash that has actually avalanched sits near
        // chance in all three.
        assertTrue("neighbouring tiles share a shade " + acrossMatches + " times across",
                acrossMatches < 160);
        assertTrue("neighbouring tiles share a shade " + downMatches + " times down",
                downMatches < 160);
        assertTrue("the grain still repeats diagonally (" + diagonalMatches + " matches)",
                diagonalMatches < 160);
    }

    // ---- Clues -----------------------------------------------------------------------------

    /**
     * A solved line's clues used to be {@code GRID} at 130/255, which composites to
     * {@code rgb(161,149,162)} — 2.52:1, below even the 3:1 floor WCAG allows for large
     * text, and these are not large text. On a 20x20 board that was most of the row clues
     * at once, on the very numbers a player re-reads to verify a line.
     */
    @Test
    public void noClueNumberIsEverBelowFourAndAHalfToOne() {
        BoardRenderer renderer = new BoardRenderer(new Draw());
        for (UiState ui : new UiState[]{plain, loud}) {
            for (boolean empty : new boolean[]{false, true}) {
                for (boolean solved : new boolean[]{false, true}) {
                    int color = renderer.clueColor(empty, solved, ui);
                    assertEquals("a clue number is drawn translucent, so what it actually "
                            + "measures depends on what is behind it", 0xff, color >>> 24);
                    double onPaper = Theme.contrastRatio(color, Theme.PAPER);
                    double onCard = Theme.contrastRatio(color, Theme.CREAM);
                    assertTrue("a clue number is " + onPaper + ":1 on paper (empty=" + empty
                            + ", solved=" + solved + ")", onPaper >= 4.5);
                    assertTrue("a clue number is " + onCard + ":1 on the card", onCard >= 4.5);
                }
            }
        }
    }

    /** "Finished" still has to be visible — just not by making the digits unreadable. */
    @Test
    public void solvedCluesStepDownWithoutDisappearing() {
        BoardRenderer renderer = new BoardRenderer(new Draw());
        int live = renderer.clueColor(false, false, plain);
        int done = renderer.clueColor(false, true, plain);
        assertTrue("a solved clue is not quieter than a live one",
                Theme.contrastRatio(done, Theme.PAPER)
                        < Theme.contrastRatio(live, Theme.PAPER));

        // With extra contrast on the hierarchy moves off the ink entirely and onto the
        // band and the tick, because someone who asked for more contrast did not ask for
        // a subtler one.
        assertEquals(renderer.clueColor(false, false, loud),
                renderer.clueColor(false, true, loud));
    }

    /**
     * The alternating ground behind the column clues turns up where the clues actually
     * crowd, and stays away where they do not.
     *
     * <p>The two ends are what matter and they are the two ends this pins: a 20x20 board
     * always gets it, because six pixels of paper between two-digit clues is three
     * arcminutes and they merge; a 5x5 board never does, because single digits 87 px apart
     * in 115 px columns have no problem to solve, and two tinted lanes among five make those
     * two columns look singled out for a reason nobody can find. In between it follows the
     * measurement, and it may only ever go one way as the board gets busier.
     */
    @Test
    public void theClueGroundAppearsOnlyWhereTheCluesCrowd() {
        for (float height : new float[]{720, 1080, 2160}) {
            Theme.setScreenHeight(height);
            boolean crowdedAlready = false;
            for (int size = 5; size <= 20; size++) {
                BoardLayout board = new BoardLayout(height * 16 / 9, height,
                        new GameState(7, size).puzzle, true);
                boolean crowds = BoardRenderer.columnCluesCrowd(board);
                String at = size + "x" + size + " at " + (int) height + ", "
                        + (board.cell - board.colClueNumberWidth()) + " px of clear paper";
                if (size == 20) {
                    assertTrue(at + ": no ground behind clues that merge", crowds);
                }
                if (size == 5) {
                    assertTrue(at + ": a ground the clues do not need", !crowds);
                }
                assertTrue(at + ": a busier board stopped needing a ground",
                        crowds || !crowdedAlready);
                crowdedAlready = crowds;
            }
        }
    }

    /**
     * The solved-line tick was a ~16 px pill holding an ~8 px glyph: about three
     * arcminutes from ten feet, under the resolvable limit. It is floored now, at the same
     * size the margin tabs already hold themselves to.
     */
    @Test
    public void theSolvedTickIsBigEnoughToResolveFromASofa() {
        for (int size : new int[]{5, 10, 15, 20}) {
            BoardLayout board = new BoardLayout(1920, 1080, new GameState(7, size).puzzle,
                    true);
            double arcminutes = BoardRenderer.tickRadius(board) * 2 * ARCMIN_PER_PIXEL;
            assertTrue("the tick on a " + size + "x" + size + " board is only "
                            + arcminutes + "' across from ten feet",
                    arcminutes >= RESOLVABLE_ARCMINUTES);
            // And it still fits the quiet strip it lives in, give or take a pixel.
            assertTrue("the tick on a " + size + "x" + size + " board overhangs its strip",
                    BoardRenderer.tickRadius(board) <= board.clueInset * .5f + 2);
        }
    }
}

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

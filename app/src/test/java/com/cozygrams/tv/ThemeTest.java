package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * The promises the palette and the type scale make to every other part of the game.
 *
 * <p>Two of them are worth stating out loud, because both are easy to break by nudging a
 * single number and neither shows up as a crash:
 *
 * <ul>
 *   <li><b>Nothing made of words is ever drawn too small to read from a sofa.</b> The
 *       arcminute arithmetic in {@link Theme}'s notes is repeated here as an assertion,
 *       against a real 55" 1080p panel at ten feet, so the claim in the documentation and
 *       the constant in the code cannot drift apart.</li>
 *   <li><b>Every text colour clears a stated WCAG ratio against the surface it is drawn
 *       on.</b> Not "looks fine on my monitor" — real relative luminance, real ratios.</li>
 * </ul>
 */
public class ThemeTest {

    // ---- The room ------------------------------------------------------------------

    /** Picture height of a 55" 16:9 panel, in inches: {@code 55 * 9 / hypot(16, 9)}. */
    private static final double PANEL_HEIGHT_INCHES = 55 * 9 / Math.hypot(16, 9);
    /** A sofa is about ten feet from the television. */
    private static final double VIEWING_INCHES = 120;
    /** Cap height of a sans-serif face as a fraction of its em size. */
    private static final double CAP_HEIGHT = .70;

    /** ISO 9241-303's practical minimum for reading; 20-22' is the comfortable band. */
    private static final double MIN_ARCMINUTES = 16;
    /** A familiar glyph stays identifiable down to about here. */
    private static final double IDENTIFIABLE_ARCMINUTES = 5;

    private static final float BIG_TEXT_SCALE = 1.16f;

    @After
    public void resetScreen() {
        Theme.setScreenHeight(720);
    }

    /** Cap height of {@code px} of type, in arcminutes, from ten feet on a 55" 1080p set. */
    private static double arcminutes(float px, float screenPixels) {
        double inches = px * CAP_HEIGHT * PANEL_HEIGHT_INCHES / screenPixels;
        return Math.toDegrees(Math.atan(inches / VIEWING_INCHES)) * 60;
    }

    // ---- The legibility floor --------------------------------------------------------

    @Test
    public void proseNeverFallsBelowTheFloor() {
        for (float height : new float[]{720, 1080, 1440, 2160}) {
            Theme.setScreenHeight(height);
            float floor = Theme.scale(Theme.MIN_PROSE_SP);
            for (float requested = 0; requested <= 80; requested += .5f) {
                assertTrue("textSize(" + requested + ") at " + height + "p fell below the "
                                + "prose floor",
                        Theme.textSize(requested) >= floor - 1e-4f);
            }
        }
    }

    @Test
    public void theProseFloorIsComfortableFromTenFeet() {
        Theme.setScreenHeight(1080);
        double floor = arcminutes(Theme.textSize(0), 1080);
        assertTrue("the prose floor is only " + floor + "' from ten feet",
                floor >= MIN_ARCMINUTES);
        // 36 px at 1080p, which is Android TV's own 18sp body minimum.
        assertEquals(36f, Theme.textSize(Theme.CAPTION), .01f);
    }

    /**
     * Why {@link Theme#MIN_READABLE_SP} is lower than the prose floor and has to stay
     * there: it is the clue-digit floor, and at 20x20 two digits have to fit inside one
     * square. Raising it to the prose floor would make them collide with their own
     * column, which is the whole reason the two floors are separate numbers.
     */
    @Test
    public void theGlyphFloorIsWhatTwentyByTwentyCanActuallyHold() {
        Theme.setScreenHeight(1080);
        assertTrue(Theme.MIN_READABLE_SP < Theme.MIN_PROSE_SP);

        // The largest 20x20 board 1080p can offer, measured the way BoardLayout does.
        Puzzle puzzle = new GameState(7, 20).puzzle;
        BoardLayout board = new BoardLayout(1920, 1080, puzzle, true);

        // The biggest two-digit clue that square can hold without overrunning its column.
        float geometryAllows = board.cell * BoardLayout.CLUE_FILL
                / (2 * BoardLayout.DIGIT_ADVANCE);
        assertTrue("a 20x20 square can now hold prose-sized digits, so the two floors "
                        + "could be merged back into one",
                geometryAllows < Theme.scale(Theme.MIN_PROSE_SP));

        // Which is why clue digits get their own, lower floor.
        assertTrue("clue digits are being drawn at prose size after all",
                board.clueTextSize < Theme.scale(Theme.MIN_PROSE_SP));

        // Even so, a clue digit stays well clear of the identification threshold.
        assertTrue("clue digits at 20x20 are only "
                        + arcminutes(board.clueTextSize, 1080) + "' from ten feet",
                arcminutes(board.clueTextSize, 1080) >= IDENTIFIABLE_ARCMINUTES * 2);
    }

    // ---- Scaling ---------------------------------------------------------------------

    @Test
    public void scalingIsMonotonicInBothArguments() {
        Theme.setScreenHeight(1080);
        float previous = -1;
        for (float design = 0; design <= 120; design += .25f) {
            float scaled = Theme.scale(design);
            assertTrue(scaled >= previous);
            previous = scaled;
        }

        previous = -1;
        for (float height = 240; height <= 2160; height += 20) {
            Theme.setScreenHeight(height);
            float scaled = Theme.scale(Theme.BODY);
            assertTrue("a taller screen produced smaller type", scaled >= previous);
            previous = scaled;
        }
    }

    @Test
    public void textSizeIsMonotonicAndBigTextIsGenuinelyBigger() {
        Theme.setScreenHeight(1080);
        float previous = -1;
        for (float design = 0; design <= 120; design += .25f) {
            float size = Theme.textSize(design);
            assertTrue(size >= previous);
            previous = size;
        }

        float[] steps = {Theme.CAPTION, Theme.BODY, Theme.SUBHEAD, Theme.HEADING,
                Theme.TITLE};
        for (int i = 1; i < steps.length; i++) {
            assertTrue("the type scale is not strictly ascending at step " + i,
                    steps[i] > steps[i - 1]);
        }
        assertTrue("the smallest named step is below the prose floor",
                steps[0] >= Theme.MIN_PROSE_SP);

        // Larger text has to actually enlarge every step, not just the ones above a floor.
        Theme.setScreenHeight(1080);
        float[] normal = new float[steps.length];
        for (int i = 0; i < steps.length; i++) {
            normal[i] = Theme.textSize(steps[i]);
        }
        Theme.setScreenHeight(1080 * BIG_TEXT_SCALE);
        for (int i = 0; i < steps.length; i++) {
            assertTrue("larger text did not enlarge step " + i,
                    Theme.textSize(steps[i]) > normal[i]);
        }
    }

    // ---- Contrast ---------------------------------------------------------------------

    @Test
    public void luminanceMathsMatchesWcag() {
        assertEquals(0, Theme.relativeLuminance(android.graphics.Color.BLACK), 1e-9);
        assertEquals(1, Theme.relativeLuminance(android.graphics.Color.WHITE), 1e-9);
        assertEquals(21, Theme.contrastRatio(android.graphics.Color.BLACK,
                android.graphics.Color.WHITE), 1e-6);
        assertEquals(1, Theme.contrastRatio(Theme.PINK, Theme.PINK), 1e-9);
        // Symmetric: the ratio does not care which colour is called the background.
        assertEquals(Theme.contrastRatio(Theme.CREAM, Theme.PANEL),
                Theme.contrastRatio(Theme.PANEL, Theme.CREAM), 1e-9);
    }

    @Test
    public void everyTextColourClearsItsSurface() {
        // Body text on the frosted panels: WCAG AA for normal-size text is 4.5:1, and TV
        // is further away than a desk, so the palette is held to AAA (7:1) where it is
        // the primary voice.
        assertRatio("CREAM on a panel", Theme.CREAM, Theme.PANEL, 7);
        assertRatio("SOFT_TEXT on a panel", Theme.SOFT_TEXT, Theme.PANEL, 7);
        assertRatio("GOLD on a panel", Theme.GOLD, Theme.PANEL, 7);
        assertRatio("BLUE on a panel", Theme.BLUE, Theme.PANEL, 7);
        assertRatio("PINK on a panel", Theme.PINK, Theme.PANEL, 4.5);

        // Clue numbers on the board's paper, which is the most important pair in the game.
        assertRatio("clue numbers on paper", Theme.INK, Theme.PAPER, 7);
        assertRatio("finished clue numbers on paper", Theme.GRID, Theme.PAPER, 4.5);

        // A focused menu row inverts: dark ink on Rose's pink.
        assertRatio("a focused row's label", Theme.INK, Theme.PINK, 4.5);

        // The resting legend label, which used to be GRID and only 2.1:1 here.
        assertRatio("a resting legend label", Draw.blend(Theme.PANEL, Theme.CREAM, .55f),
                Theme.PANEL, 4.5);
    }

    /**
     * The panels are translucent, so the real background is the panel colour composited
     * over whatever the illustration is doing underneath. The brightest thing in either
     * picture is a lamp, so that is the case to hold the palette to.
     */
    @Test
    public void textStillReadsWhereThePanelIsThinnest() {
        int lamplight = android.graphics.Color.rgb(255, 232, 196);
        int worst = Draw.blend(lamplight, Theme.PANEL, 228 / 255f);
        assertRatio("CREAM over the brightest lamplight", Theme.CREAM, worst, 7);
        assertRatio("SOFT_TEXT over the brightest lamplight", Theme.SOFT_TEXT, worst, 7);
        assertRatio("PINK over the brightest lamplight", Theme.PINK, worst, 4.5);
    }

    /**
     * Every identity colour a scene might print as a name has to be liftable to a
     * readable one, including Sky's colour-blind-friendly teal, which is 2:1 raw.
     */
    @Test
    public void identityColoursCanAlwaysBeMadeReadable() {
        assertTrue("Sky's alternate teal is no longer the hard case",
                Theme.contrastRatio(Comfort.SKY_ALT, Theme.PANEL) < 4.5);

        int[] identities = {Theme.PINK, Theme.PINK_DARK, Theme.PINK_LIGHT, Theme.BLUE,
                Theme.BLUE_DARK, Theme.BLUE_LIGHT, Theme.GOLD, Theme.GRID,
                Comfort.SKY_ALT, Comfort.SKY_ALT_DARK, Comfort.SKY_ALT_LIGHT};
        for (int identity : identities) {
            assertRatio("readableOn(...) on a panel",
                    Theme.readableOn(identity, Theme.PANEL, 4.5), Theme.PANEL, 4.5);
            assertRatio("readableOn(...) on paper",
                    Theme.readableOn(identity, Theme.PAPER, 4.5), Theme.PAPER, 4.5);
        }

        // A colour that already reads is handed back untouched, so identity survives.
        assertEquals(Theme.PINK, Theme.readableOn(Theme.PINK, Theme.PANEL, 4.5));
    }

    /**
     * {@link Theme#textOn} has to be right for every fill the game paints a glyph on, not
     * just the light ones the palette used to be made of.
     */
    @Test
    public void textOnAlwaysPicksTheReadableOfInkAndCream() {
        int[] fills = {Theme.PINK, Theme.PINK_DARK, Theme.PINK_LIGHT, Theme.BLUE,
                Theme.BLUE_DARK, Theme.BLUE_LIGHT, Theme.GOLD, Theme.GRID, Theme.INK,
                Theme.CREAM, Theme.PAPER, Theme.PANEL, Theme.SOFT_TEXT, Comfort.SKY_ALT,
                Comfort.SKY_ALT_DARK, Comfort.SKY_ALT_LIGHT};
        for (int fill : fills) {
            int chosen = Theme.textOn(fill);
            int other = chosen == Theme.INK ? Theme.CREAM : Theme.INK;
            assertTrue("textOn picked the worse of the two on " + Integer.toHexString(fill),
                    Theme.contrastRatio(chosen, fill) >= Theme.contrastRatio(other, fill));
        }
    }

    @Test
    public void extraContrastActuallyRaisesContrast() {
        double plain = Theme.contrastRatio(Theme.secondaryText(false), Theme.PANEL);
        double loud = Theme.contrastRatio(Theme.secondaryText(true), Theme.PANEL);
        assertTrue("extra contrast did not raise the secondary text's ratio", loud > plain);
    }

    // ---- The board's ink is nobody's identity ------------------------------------------

    /**
     * The promise that makes the two cursors equally findable, and the one that is easiest
     * to break by "just borrowing" an identity colour for a tile.
     *
     * <p>A filled square used to be {@code blend(PINK_DARK, PINK, .62)}. Rose then had to
     * find a pink cursor on a field of pink at 1.30:1 — same hue, same value — while Sky's
     * cyan was hue-opposite and popped instantly. Nothing drawn on the cursor could fix
     * that; only taking the board off the identity palette could.
     */
    @Test
    public void aFilledTileIsNobodysColour() {
        // Both players' cursors clear the 3:1 WCAG floor for a graphical object against
        // the field they are found on, and neither has an unfair advantage over the other.
        double rose = Theme.contrastRatio(Theme.PINK, Theme.TILE);
        double sky = Theme.contrastRatio(Theme.BLUE, Theme.TILE);
        assertTrue("Rose's cursor is only " + rose + ":1 on a filled tile", rose >= 3);
        assertTrue("Sky's cursor is only " + sky + ":1 on a filled tile", sky >= 3);

        // And the cursor plate — the cue that actually carries findability — is identical
        // for both, so whatever the identity colours do, the two are equal.
        assertRatio("a cursor plate on a filled tile", Theme.CREAM, Theme.TILE, 7);
        assertRatio("a cursor plate on a filled tile, extra contrast", Theme.CREAM,
                Theme.TILE_BOLD, 7);
    }

    /**
     * Filled, crossed and untouched have to separate by <em>value</em>, so that the picture
     * survives greyscale, a colour-blind player and a badly calibrated television alike.
     */
    @Test
    public void theThreeSquareStatesSeparateByValue() {
        // The crossed square's wash, composited the way BoardRenderer draws it.
        int crossed = Draw.blend(Theme.PAPER, android.graphics.Color.rgb(88, 80, 122),
                26 / 255f);
        assertRatio("a filled tile against paper", Theme.TILE, Theme.PAPER, 7);
        assertRatio("a filled tile against a crossed square", Theme.TILE, crossed, 7);
        assertRatio("a filled tile against the cream card", Theme.TILE, Theme.CREAM, 7);
    }

    /**
     * "Extra contrast" has to be a change of kind. Turning it on used to move a tile from
     * {@code rgb(208,102,140)} to {@code rgb(197,93,132)} — about five percent of
     * luminance, which is nothing across a room.
     */
    @Test
    public void extraContrastChangesTheTileByMoreThanANudge() {
        double plain = Theme.contrastRatio(Theme.TILE, Theme.PAPER);
        double loud = Theme.contrastRatio(Theme.TILE_BOLD, Theme.PAPER);
        assertTrue("extra contrast left the tile at " + loud + ":1 against " + plain
                + ":1", loud >= plain * 1.25);
        // And it is a different colour, not the same one turned down: the warm plum
        // desaturates to a cool slate, so the two are told apart by hue as well.
        assertTrue("the tile stayed warm with extra contrast on",
                red(Theme.TILE) - blue(Theme.TILE) > 0
                        && red(Theme.TILE_BOLD) - blue(Theme.TILE_BOLD) < 0);
    }

    // ---- Washes ------------------------------------------------------------------------

    /**
     * {@link Theme#washAlpha} is what makes the cursor bands equal rather than merely
     * present: it answers "how much of this colour do I need" instead of being told a
     * number that can only be right for one hue.
     */
    @Test
    public void washAlphaFindsTheThinnestTintThatClearsTheRatio() {
        int[] colors = {Theme.PINK, Theme.PINK_DARK, Theme.BLUE, Theme.BLUE_DARK,
                Theme.GOLD, Theme.GRID, Theme.INK, Comfort.SKY_ALT};
        for (double ratio : new double[]{1.2, 1.45, 1.8, 2.5}) {
            for (int color : colors) {
                int alpha = Theme.washAlpha(color, Theme.PAPER, ratio, 200);
                int washed = Draw.blend(Theme.PAPER, color, alpha / 255f);
                assertTrue("a wash of " + Integer.toHexString(color) + " at " + ratio
                                + ":1 came out at "
                                + Theme.contrastRatio(washed, Theme.PAPER) + ":1",
                        Theme.contrastRatio(washed, Theme.PAPER) >= ratio
                                || alpha >= 200);
                assertTrue("washAlpha exceeded its ceiling", alpha <= 200);
            }
        }
        // A thinner wash for a louder ratio would mean the search ran the wrong way.
        assertTrue(Theme.washAlpha(Theme.PINK, Theme.PAPER, 1.8, 200)
                > Theme.washAlpha(Theme.PINK, Theme.PAPER, 1.2, 200));
    }

    // ---- Layout -------------------------------------------------------------------------

    /**
     * Leanback asks for five percent of each edge — 27dp vertically and 48dp horizontally
     * on a 1080p set. This sat at .045 for a while, which is inside the margin an
     * overscanning panel is allowed to eat.
     */
    @Test
    public void theSafeAreaMeetsLeanback() {
        assertTrue("SAFE_AREA is only " + Theme.SAFE_AREA, Theme.SAFE_AREA >= .05f);
        assertEquals(54f, 1080 * Theme.SAFE_AREA, .001);
        assertEquals(96f, 1920 * Theme.SAFE_AREA, .001);
    }

    private static int red(int color) {
        return (color >> 16) & 0xff;
    }

    private static int blue(int color) {
        return color & 0xff;
    }

    private static void assertRatio(String what, int color, int background,
                                    double minimum) {
        double ratio = Theme.contrastRatio(color, background);
        assertTrue(what + " is only " + Math.round(ratio * 100) / 100.0 + ":1, wanted "
                + minimum + ":1", ratio >= minimum);
    }
}

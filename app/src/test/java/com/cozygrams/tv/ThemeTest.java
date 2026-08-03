package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * The promises the palette, the type scale and the motion ladder make to every other part
 * of the game.
 *
 * <p>Four of them are worth stating out loud, because each is easy to break by nudging a
 * single number and none of them shows up as a crash:
 *
 * <ul>
 *   <li><b>Nothing made of words is ever drawn too small to read from a sofa.</b> The
 *       arcminute arithmetic in {@link Theme}'s notes is repeated here as an assertion,
 *       against a real 55" 1080p panel at ten feet, so the claim in the documentation and
 *       the constant in the code cannot drift apart.</li>
 *   <li><b>Every text colour clears a stated WCAG ratio against the surface it is drawn
 *       on.</b> Not "looks fine on my monitor" — real relative luminance, real ratios.</li>
 *   <li><b>Every mark on the board clears a stated ratio too.</b> This is the newer half,
 *       and it is here because the old half was not enough: the palette was immaculate
 *       while the board's own feedback was drawn between 1.05:1 and 1.37:1 — present in
 *       the code, absent from the room. Ratios beat alphas, and both are asserted.</li>
 *   <li><b>LARGER TEXT enlarges words and nothing else.</b> It used to work by lying to
 *       {@link Theme#setScreenHeight(float)}, which shrank the board to make room for its
 *       own bigger paddings.</li>
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

    /** WCAG's floor for a graphical object that has to be found against its field. */
    private static final double OBJECT_RATIO = 3;

    @After
    public void resetTheRoom() {
        Theme.setScreenHeight(720);
        Theme.setTextScale(1f);
        Comfort.get().restoreDefaults();
    }

    /** Cap height of {@code px} of type, in arcminutes, from ten feet on a 55" 1080p set. */
    private static double arcminutes(float px, float screenPixels) {
        return markArcminutes(px * (float) CAP_HEIGHT, screenPixels);
    }

    /** The angle a mark of {@code px} subtends, for things that are not letters. */
    private static double markArcminutes(float px, float screenPixels) {
        double inches = px * PANEL_HEIGHT_INCHES / screenPixels;
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

    /**
     * Marks that are neither words nor glyphs — chapter pips, progress dots — have no
     * fallback of familiarity to be recognised by, so they get a floor of their own.
     */
    @Test
    public void theSmallestDecorativeMarkIsStillAMark() {
        Theme.setScreenHeight(1080);
        double angle = markArcminutes(Theme.scale(Theme.MIN_PIP_PX), 1080);
        assertEquals(18f, Theme.scale(Theme.MIN_PIP_PX), .01f);
        assertTrue("a minimum pip is only " + angle + "' across from ten feet",
                angle >= 10);
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
    public void textSizeIsMonotonicAndTheStepsAscend() {
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
    }

    /**
     * The two stroke weights are exactly the idioms they replaced.
     *
     * <p>{@code Math.max(1.5f, Theme.scale(2))} appeared five times and
     * {@code Math.max(1f, Theme.scale(1))} twice, and every one of those sites now calls
     * {@link Theme#hairline()} or {@link Theme#keyline()} instead. That substitution is only
     * safe while the named form is the same number, so it is pinned rather than assumed —
     * at all three shipping heights, because the floors and the scale cross over somewhere
     * below 720p and a helper that quietly diverged there would move a line on a real panel.
     *
     * <p>{@link Theme#MIN_PROSE_SP} is not involved: these are strokes, not type.
     */
    @Test
    public void theNamedStrokeWeightsAreTheIdiomsTheyReplaced() {
        for (float height : new float[]{540, 720, 1080, 2160}) {
            Theme.setScreenHeight(height);
            assertEquals("hairline at " + height, Math.max(1.5f, Theme.scale(2)),
                    Theme.hairline(), .0001f);
            assertEquals("keyline at " + height, Math.max(1f, Theme.scale(1)),
                    Theme.keyline(), .0001f);
            assertTrue("a hairline should be the heavier of the two at " + height,
                    Theme.hairline() >= Theme.keyline());
        }
        Theme.setScreenHeight(1080);
    }

    /**
     * The corner ramp is a ramp, and the two radii the game draws with are the two it
     * declares. {@code BoardRenderer} spelled the card's 22 and {@code HudScene} the seat
     * card's 14 as bare literals, which is how the paper card and the co-op rail once ended
     * up on the same screen with different corners.
     */
    @Test
    public void theCornerRampDescendsAndTheDrawnRadiiComeFromIt() {
        assertTrue("panel should be the largest corner",
                Theme.PANEL_RADIUS > Theme.RADIUS_CARD);
        assertTrue("card should be larger than a chip",
                Theme.RADIUS_CARD > Theme.RADIUS_CHIP);
        assertEquals("the paper card's radius", 22f, Theme.RADIUS_CARD, .0001f);
        assertEquals("a seat card's radius", 14f, Theme.RADIUS_CHIP, .0001f);
    }

    /**
     * Adjacent steps are legibility neighbours, not hierarchy levels — the claim
     * {@link Theme}'s notes make, held to a number here.
     *
     * <p>{@link Theme#CAPTION} is clamped to the prose floor, so on a 1080p screen a
     * label at {@code BODY} and its value at {@code CAPTION} come out at exactly the same
     * size and the hierarchy falls entirely to weight and colour. Two steps apart is a
     * third larger, which reads instantly from a sofa.
     */
    @Test
    public void adjacentTypeStepsAreNotAHierarchy() {
        Theme.setScreenHeight(1080);
        float neighbour = Theme.textSize(Theme.BODY) / Theme.textSize(Theme.CAPTION);
        float skipped = Theme.textSize(Theme.SUBHEAD) / Theme.textSize(Theme.CAPTION);
        assertTrue("adjacent steps are a hierarchy after all: " + neighbour + "x",
                neighbour <= 1.15f);
        assertTrue("two arcminutes apart is not a level: "
                        + (arcminutes(Theme.textSize(Theme.BODY), 1080)
                        - arcminutes(Theme.textSize(Theme.CAPTION), 1080)) + "'",
                arcminutes(Theme.textSize(Theme.BODY), 1080)
                        - arcminutes(Theme.textSize(Theme.CAPTION), 1080) < 3);
        assertTrue("skipping a step is only " + skipped + "x", skipped >= 1.3f);
    }

    /**
     * The point of {@link Theme#setTextScale(float)}: LARGER TEXT used to be applied by
     * telling {@link Theme#setScreenHeight(float)} the screen was 16% taller than it was,
     * which grew every padding, stroke and cell along with the words — the 20x20 paper
     * card measured 797 px normally and 759 px with the setting on. Asking for bigger text
     * made the puzzle smaller.
     */
    @Test
    public void largerTextGrowsWordsAndLeavesEverythingElseAlone() {
        Theme.setScreenHeight(1080);
        float[] steps = {Theme.CAPTION, Theme.BODY, Theme.SUBHEAD, Theme.HEADING,
                Theme.TITLE};
        float[] normal = new float[steps.length];
        for (int i = 0; i < steps.length; i++) {
            normal[i] = Theme.textSize(steps[i]);
        }
        float paddingBefore = Theme.scale(Theme.MENU_INSET);
        float cellBefore = Theme.scale(Theme.MAX_CELL);
        float hairlineBefore = Theme.hairline();

        Theme.setTextScale(Theme.TEXT_SCALE_BIG);
        for (int i = 0; i < steps.length; i++) {
            assertEquals("larger text did not enlarge step " + i,
                    normal[i] * Theme.TEXT_SCALE_BIG, Theme.textSize(steps[i]), .01f);
        }
        // Including the floor, so the smallest thing on screen grows too.
        assertEquals(36f * Theme.TEXT_SCALE_BIG, Theme.textSize(0), .01f);

        assertEquals("larger text moved a padding", paddingBefore,
                Theme.scale(Theme.MENU_INSET), .0001f);
        assertEquals("larger text moved the board's cell size", cellBefore,
                Theme.scale(Theme.MAX_CELL), .0001f);
        assertEquals("larger text moved a stroke weight", hairlineBefore,
                Theme.hairline(), .0001f);
    }

    @Test
    public void theTextScaleIsClampedToSomethingASetCanDraw() {
        Theme.setScreenHeight(1080);
        Theme.setTextScale(.4f);
        assertEquals("shrinking text is never a thing anyone asked for", 1f,
                Theme.textScale(), .0001f);
        Theme.setTextScale(9f);
        assertEquals(1.5f, Theme.textScale(), .0001f);
        Theme.setTextScale(Theme.TEXT_SCALE_BIG);
        assertEquals(Theme.TEXT_SCALE_BIG, Theme.textScale(), .0001f);

        // And LARGER TEXT lands above the comfortable reading band rather than inside it,
        // which is what a setting called "larger" is for.
        assertTrue("larger text is only " + arcminutes(Theme.textSize(0), 1080)
                        + "' from ten feet",
                arcminutes(Theme.textSize(0), 1080) >= 22);
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
        assertRatio("CAUTION on a panel", Theme.CAUTION, Theme.PANEL, 4.5);

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
     * A panel is no longer one colour: {@link Draw#panel} washes lamplight down from its
     * top edge, so the surface under a heading is {@link Theme#PANEL_LIFT} rather than
     * {@link Theme#PANEL}. Anything the palette promises on one has to hold on the other,
     * which is the constraint that fixes {@link Theme#PANEL_LIFT_ALPHA} at 12 — at 15 the
     * lift is bright enough that Sky's {@link Theme#BLUE} drops to 6.94:1 and this test
     * fails, which is exactly what it is for.
     */
    @Test
    public void theLitTopOfAPanelReadsAsWellAsItsBody() {
        assertRatio("CREAM on a lit panel top", Theme.CREAM, Theme.PANEL_LIFT, 7);
        assertRatio("SOFT_TEXT on a lit panel top", Theme.SOFT_TEXT, Theme.PANEL_LIFT, 7);
        assertRatio("GOLD on a lit panel top", Theme.GOLD, Theme.PANEL_LIFT, 7);
        assertRatio("BLUE on a lit panel top", Theme.BLUE, Theme.PANEL_LIFT, 7);
        assertRatio("PINK on a lit panel top", Theme.PINK, Theme.PANEL_LIFT, 4.5);
        assertRatio("CAUTION on a lit panel top", Theme.CAUTION, Theme.PANEL_LIFT, 4.5);

        // It is a lift, not a different colour: light enough to see, dark enough to keep
        // being a panel.
        assertTrue("the lift is the wrong way round",
                Theme.relativeLuminance(Theme.PANEL_LIFT)
                        > Theme.relativeLuminance(Theme.PANEL));
        assertTrue("the lit top is a different surface, not a lit one",
                Theme.contrastRatio(Theme.PANEL_LIFT, Theme.PANEL) < 1.5);
    }

    /**
     * The panels are translucent, so the real background is the panel colour composited
     * over whatever the illustration is doing underneath. The brightest thing in either
     * picture is a lamp, so that is the case to hold the palette to.
     *
     * <p>The lamplight used to be spelled {@code Color.rgb(255, 232, 196)}, which under
     * {@code returnDefaultValues} is zero — so this test was quietly measuring text against
     * a background <em>darker</em> than a panel, and passing for the wrong reason. Colours
     * a test asserts about have to be built the way {@link Theme} builds them.
     *
     * <p>The lit top of a panel is the same case one step brighter, and it is where
     * {@link Theme#PANEL_LIFT_ALPHA} runs out of room: {@link Theme#SOFT_TEXT} lands at
     * 7.04:1 there and would be 6.94:1 if the wash were one level stronger.
     */
    @Test
    public void textStillReadsWhereThePanelIsThinnest() {
        int lamplight = rgb(255, 232, 196);
        int worst = Draw.blend(lamplight, Theme.PANEL, 228 / 255f);
        assertRatio("CREAM over the brightest lamplight", Theme.CREAM, worst, 7);
        assertRatio("SOFT_TEXT over the brightest lamplight", Theme.SOFT_TEXT, worst, 7);
        assertRatio("PINK over the brightest lamplight", Theme.PINK, worst, 4.5);

        int lit = Draw.blend(lamplight, Theme.PANEL_LIFT, 228 / 255f);
        assertRatio("CREAM on a lit panel top over lamplight", Theme.CREAM, lit, 7);
        assertRatio("SOFT_TEXT on a lit panel top over lamplight", Theme.SOFT_TEXT, lit, 7);
        assertRatio("GOLD on a lit panel top over lamplight", Theme.GOLD, lit, 7);
    }

    /**
     * Copy that floats directly on the illustration — the cursor's margin tabs, the header
     * — has no panel to be measured against, so {@link Theme#BACKDROP_REF} stands in for
     * the art. It is only useful if it is dark enough to be the honest worst case.
     */
    @Test
    public void backdropRefIsAnHonestTargetForCopyWithNoPanel() {
        // Sampled from both shipped scenes: (53,62,74) in the garden, (66,49,62) in the
        // room. The reference has to be no brighter than either.
        int garden = rgb(53, 62, 74);
        int room = rgb(66, 49, 62);
        assertTrue("BACKDROP_REF is brighter than the garden it stands in for",
                Theme.relativeLuminance(Theme.BACKDROP_REF)
                        <= Theme.relativeLuminance(garden));
        assertTrue("BACKDROP_REF is brighter than the room it stands in for",
                Theme.relativeLuminance(Theme.BACKDROP_REF)
                        <= Theme.relativeLuminance(room));
        assertRatio("CREAM on the backdrop reference", Theme.CREAM, Theme.BACKDROP_REF, 7);
        for (int player = 0; player < 2; player++) {
            assertRatio("a player's name on the backdrop reference",
                    Theme.readableOn(Theme.playerColor(player), Theme.BACKDROP_REF, 4.5),
                    Theme.BACKDROP_REF, 4.5);
        }
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
                Theme.BLUE_DARK, Theme.BLUE_LIGHT, Theme.GOLD, Theme.GRID, Theme.CANDLE,
                Theme.CANDLE_DEEP, Theme.CAUTION, Comfort.SKY_ALT, Comfort.SKY_ALT_DARK,
                Comfort.SKY_ALT_LIGHT};
        for (int identity : identities) {
            assertRatio("readableOn(...) on a panel",
                    Theme.readableOn(identity, Theme.PANEL, 4.5), Theme.PANEL, 4.5);
            assertRatio("readableOn(...) on a lit panel top",
                    Theme.readableOn(identity, Theme.PANEL_LIFT, 4.5), Theme.PANEL_LIFT,
                    4.5);
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
                Theme.BLUE_DARK, Theme.BLUE_LIGHT, Theme.GOLD, Theme.CANDLE,
                Theme.CANDLE_DEEP, Theme.CAUTION, Theme.GRID, Theme.INK, Theme.CREAM,
                Theme.PAPER, Theme.PANEL, Theme.PANEL_LIFT, Theme.SOFT_TEXT,
                Theme.TOGGLE_OFF, Theme.TRACK, Comfort.SKY_ALT, Comfort.SKY_ALT_DARK,
                Comfort.SKY_ALT_LIGHT};
        for (int fill : fills) {
            int chosen = Theme.textOn(fill);
            int other = chosen == Theme.INK ? Theme.CREAM : Theme.INK;
            assertTrue("textOn picked the worse of the two on " + Integer.toHexString(fill),
                    Theme.contrastRatio(chosen, fill) >= Theme.contrastRatio(other, fill));
        }
        // And a chip's label is never merely the better of two bad options.
        assertRatio("a word printed on CAUTION", Theme.textOn(Theme.CAUTION),
                Theme.CAUTION, 4.5);
        assertRatio("a word printed on GOLD", Theme.textOn(Theme.GOLD), Theme.GOLD, 4.5);
    }

    @Test
    public void extraContrastActuallyRaisesContrast() {
        double plain = Theme.contrastRatio(Theme.secondaryText(false), Theme.PANEL);
        double loud = Theme.contrastRatio(Theme.secondaryText(true), Theme.PANEL);
        assertTrue("extra contrast did not raise the secondary text's ratio", loud > plain);
    }

    // ---- One candle, three depths ------------------------------------------------------

    /**
     * Why the palette carries three golds, which looks like indecision until it is
     * measured: {@link Theme#GOLD} is chosen to glow on a dark panel and is 1.38:1 on
     * paper, which no alpha can rescue — for a long time every gold cue the board tried to
     * draw (the hint, the completed-line sweep, the sparkle) was invisible for exactly
     * this reason.
     */
    @Test
    public void onlyTheBoardsOwnGoldCanBeSeenOnTheBoard() {
        assertTrue("GOLD has become usable on paper, so CANDLE could be retired",
                Theme.contrastRatio(Theme.GOLD, Theme.PAPER) < Theme.FEEDBACK_MIN_RATIO);
        assertRatio("GOLD where it belongs, on a panel", Theme.GOLD, Theme.PANEL, 7);

        // CANDLE is the one accent that clears the floor on both board surfaces at once.
        assertRatio("CANDLE on paper", Theme.CANDLE, Theme.PAPER,
                Theme.FEEDBACK_MIN_RATIO);
        assertRatio("CANDLE on a filled tile", Theme.CANDLE, Theme.TILE,
                Theme.FEEDBACK_MIN_RATIO);
        assertRatio("CANDLE on a filled tile with extra contrast", Theme.CANDLE,
                Theme.TILE_BOLD, Theme.FEEDBACK_MIN_RATIO);
        // A celebration, rather than a mere notification, wants 2.2:1.
        assertRatio("CANDLE as a celebration on paper", Theme.CANDLE, Theme.PAPER, 2.2);

        // CANDLE_DEEP exists because a stroke is thin: it has to be well clear of the
        // floor before a two-pixel sweep is seen at all.
        assertTrue("CANDLE_DEEP is not deeper than CANDLE",
                Theme.contrastRatio(Theme.CANDLE_DEEP, Theme.PAPER)
                        > Theme.contrastRatio(Theme.CANDLE, Theme.PAPER) * 1.4);
        assertRatio("a candle-deep line on paper", Theme.CANDLE_DEEP, Theme.PAPER, 3);

        // Three depths of one light, not three colours: each is warm, and each is warmer
        // in red than in blue by a wide margin.
        for (int gold : new int[]{Theme.GOLD, Theme.CANDLE, Theme.CANDLE_DEEP}) {
            assertTrue("a gold in the candle family went cool",
                    red(gold) - blue(gold) >= 100);
        }
        assertTrue("the candle family is not ordered by depth",
                Theme.relativeLuminance(Theme.GOLD)
                        > Theme.relativeLuminance(Theme.CANDLE)
                        && Theme.relativeLuminance(Theme.CANDLE)
                        > Theme.relativeLuminance(Theme.CANDLE_DEEP));
    }

    /**
     * {@link Theme#FEEDBACK_MIN_RATIO} is only worth stating if the palette can actually
     * reach it on both of the board's surfaces without going opaque — otherwise it is a
     * floor nobody can stand on.
     */
    @Test
    public void theFeedbackFloorIsReachableOnEverySurfaceTheBoardHas() {
        int[] grounds = {Theme.PAPER, Theme.PAPER_SHADE, Theme.CLUE_BAND_ALT, Theme.TILE,
                Theme.TILE_BOLD, Theme.CREAM};
        for (int ground : grounds) {
            int alpha = Theme.washAlpha(Theme.CANDLE, ground, Theme.FEEDBACK_MIN_RATIO,
                    255);
            int washed = Draw.blend(ground, Theme.CANDLE, alpha / 255f);
            assertTrue("a candle wash on " + Integer.toHexString(ground) + " only reaches "
                            + Theme.contrastRatio(washed, ground) + ":1",
                    Theme.contrastRatio(washed, ground) >= Theme.FEEDBACK_MIN_RATIO);
            assertTrue("a candle wash on " + Integer.toHexString(ground)
                            + " needs alpha " + alpha + ", which is not a wash any more",
                    alpha <= 230);
        }
        assertTrue("the feedback floor has drifted below what a sofa can see",
                Theme.FEEDBACK_MIN_RATIO >= 1.5);
    }

    // ---- The board's grounds and rules ---------------------------------------------------

    /**
     * The board has four printed grounds and they have to arrive in this order, each
     * clearly louder than the last: the five-square checker, the alternating clue lane,
     * and the crossed square's wash. When the cross was a fixed alpha of 26 it measured
     * 1.17:1 against the checker's 1.09:1 — the two were the same mark, so "filled,
     * crossed and untouched separate by value" was true of filled and of nothing else.
     */
    @Test
    public void theBoardsPrintedGroundsArriveInOrder() {
        double shade = Theme.contrastRatio(Theme.PAPER_SHADE, Theme.PAPER);
        double lane = Theme.contrastRatio(Theme.CLUE_BAND_ALT, Theme.PAPER);
        double cross = Theme.contrastRatio(crossedSquare(false), Theme.PAPER);

        assertTrue("the five-square checker is loud enough to compete with a mark: "
                + shade, shade <= 1.12);
        assertTrue("the clue lane is not clear of the checker: " + lane + " vs " + shade,
                lane >= shade * 1.1);
        assertTrue("a crossed square is not clear of the clue lane: " + cross + " vs "
                + lane, cross >= lane * 1.05);

        // Both grounds are the paper's own hue warmed, not cooled toward lilac the way
        // blend(PAPER, GRID, .055) was.
        for (int ground : new int[]{Theme.PAPER_SHADE, Theme.CLUE_BAND_ALT}) {
            assertTrue("a paper ground went cool", red(ground) - blue(ground)
                    >= red(Theme.PAPER) - blue(Theme.PAPER));
        }
    }

    /** The two grid inks, against the paper and against each other. */
    @Test
    public void theGridRulesAreFoundOnEveryGroundTheyCross() {
        assertRatio("the heavy five-rule on paper", Theme.RULE_MAJOR, Theme.PAPER, 7);
        assertRatio("the light rule on paper", Theme.RULE_MINOR, Theme.PAPER,
                OBJECT_RATIO);
        assertRatio("the light rule on the checker", Theme.RULE_MINOR, Theme.PAPER_SHADE,
                OBJECT_RATIO);
        assertRatio("the light rule on a clue lane", Theme.RULE_MINOR,
                Theme.CLUE_BAND_ALT, OBJECT_RATIO);
        assertTrue("the two rules are the same weight of ink",
                Theme.contrastRatio(Theme.RULE_MAJOR, Theme.RULE_MINOR) >= 2);
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
        assertTrue("Rose's cursor is only " + rose + ":1 on a filled tile",
                rose >= OBJECT_RATIO);
        assertTrue("Sky's cursor is only " + sky + ":1 on a filled tile",
                sky >= OBJECT_RATIO);

        // And the cursor plate — the cue that actually carries findability — is identical
        // for both, so whatever the identity colours do, the two are equal.
        assertRatio("a cursor plate on a filled tile", Theme.CREAM, Theme.TILE, 7);
        assertRatio("a cursor plate on a filled tile, extra contrast", Theme.CREAM,
                Theme.TILE_BOLD, 7);
    }

    /**
     * {@link Theme#PLATE_INK} is the keyline that makes the plate an object rather than a
     * bright patch, so it has to be found on every surface a cursor can stand on. It was
     * spelled out numerically in three files, which is a strange fate for the one colour
     * that keeps both players equal.
     */
    @Test
    public void thePlateKeylineIsFoundOnEverySquareTheCursorCanReach() {
        int[] under = {Theme.PAPER, Theme.PAPER_SHADE, Theme.CLUE_BAND_ALT, Theme.CREAM,
                crossedSquare(false), crossedSquare(true)};
        for (int surface : under) {
            assertRatio("the plate keyline on " + Integer.toHexString(surface),
                    Theme.PLATE_INK, surface, 7);
        }
        assertTrue("PLATE_INK is no darker than the panel it would sit on",
                Theme.relativeLuminance(Theme.PLATE_INK)
                        < Theme.relativeLuminance(Theme.PANEL));
    }

    /**
     * Three near-blacks, ordered and each with a job. There were four — (8,5,16) behind
     * text, (10,6,18) under panels, (16,11,28) over the whole screen — differing by two
     * levels in places, which nobody can see and everybody has to maintain.
     */
    @Test
    public void theNightFamilyIsOrderedAndNamed() {
        assertTrue("a shadow is not the darkest of the darks",
                Theme.relativeLuminance(Theme.SHADOW_INK)
                        < Theme.relativeLuminance(Theme.NIGHT));
        assertTrue("the scrim is not darker than a cursor keyline",
                Theme.relativeLuminance(Theme.NIGHT)
                        < Theme.relativeLuminance(Theme.PLATE_INK));
        for (int dark : new int[]{Theme.SHADOW_INK, Theme.NIGHT, Theme.PLATE_INK}) {
            assertRatio("cream over one of the darks", Theme.CREAM, dark, 7);
            assertTrue("one of the darks went cold; the night here is plum",
                    blue(dark) > green(dark));
        }
    }

    /**
     * Filled, crossed and untouched have to separate by <em>value</em>, so that the picture
     * survives greyscale, a colour-blind player and a badly calibrated television alike.
     */
    @Test
    public void theThreeSquareStatesSeparateByValue() {
        assertRatio("a filled tile against paper", Theme.TILE, Theme.PAPER, 7);
        assertRatio("a filled tile against a crossed square", Theme.TILE,
                crossedSquare(false), 7);
        assertRatio("a filled tile against a crossed square, extra contrast",
                Theme.TILE_BOLD, crossedSquare(true), 7);
        assertRatio("a filled tile against the cream card", Theme.TILE, Theme.CREAM, 7);

        // The X drawn on the crossed square has to be seen on the wash it sits in.
        assertRatio("the X on its own wash", Theme.CROSS_TINT, crossedSquare(false),
                OBJECT_RATIO);
        // And extra contrast has to make a cross a decision rather than a hint.
        assertTrue("extra contrast did not deepen the cross",
                Theme.CROSS_RATIO_BOLD >= Theme.CROSS_RATIO * 1.25);
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

    // ---- Controls ------------------------------------------------------------------------

    /**
     * A switch carries nine settings and is the smallest element in the game, so its two
     * states cannot be told apart by hue alone. ON and OFF measured 1.34:1 — from ten feet
     * that is one grey pill whichever way it is set, and the knob's position was carrying
     * the whole thing.
     */
    @Test
    public void toggleStatesAreToldApartByValue() {
        double apart = Theme.contrastRatio(Theme.PINK, Theme.TOGGLE_OFF);
        assertTrue("ON and OFF are only " + apart + ":1 apart", apart >= OBJECT_RATIO);
        assertRatio("an OFF track on a panel", Theme.TOGGLE_OFF, Theme.PANEL, 1.2);
        assertRatio("the knob on an OFF track", Theme.SOFT_TEXT, Theme.TOGGLE_OFF,
                OBJECT_RATIO);
    }

    /**
     * A question that means "this cannot be undone" should not be wearing a player's
     * colour. Both destructive confirmations borrowed {@link Theme#PINK_LIGHT}, which is
     * Rose.
     */
    @Test
    public void cautionIsNobodysColourEither() {
        assertTrue("CAUTION is too close to Rose to mean something else",
                Theme.contrastRatio(Theme.CAUTION, Theme.PINK) > 1.2
                        || hueDistance(Theme.CAUTION, Theme.PINK) >= 30);
        assertTrue("CAUTION is too close to Sky to mean something else",
                hueDistance(Theme.CAUTION, Theme.BLUE) >= 60);
        // Warm clay, inside the lamplit palette, rather than an error red.
        assertTrue("CAUTION has become an alarm colour", green(Theme.CAUTION) >= 120);
    }

    /** The two meter tones, and the resting menu row, on the panel they are drawn on. */
    @Test
    public void meterAndRowSurfacesAreVisibleWithoutShouting() {
        assertRatio("an empty progress track on a panel", Theme.TRACK, Theme.PANEL,
                OBJECT_RATIO);

        int resting = Draw.blend(Theme.PANEL, Theme.ROW_REST,
                (Theme.ROW_REST >>> 24) / 255f);
        double separation = Theme.contrastRatio(resting, Theme.PANEL);
        assertTrue("a resting menu row is invisible at " + separation + ":1",
                separation >= 1.35);
        assertTrue("a resting menu row competes with a focused one at " + separation + ":1",
                separation <= 2);
        assertRatio("a resting row's label", Theme.CREAM, resting, 7);
        assertRatio("a resting row's value", Theme.SOFT_TEXT, resting, 7);
        // Warm, not grey: a cream wash over plum comes out the colour of a filing cabinet.
        assertTrue("the resting row went grey", red(resting) - blue(resting) > 0);

        int joined = Draw.blend(Theme.PANEL, Theme.PINK, Theme.SEAT_FILL_JOINED);
        int open = Draw.blend(Theme.PANEL, Theme.PINK, Theme.SEAT_FILL_OPEN);
        assertTrue("a joined seat does not read as fuller than an open one",
                Theme.relativeLuminance(joined) > Theme.relativeLuminance(open));
        assertRatio("a name on a joined seat", Theme.CREAM, joined, 7);
        assertRatio("an invitation on an open seat", Theme.CREAM, open, 7);
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
                Theme.GOLD, Theme.CANDLE, Theme.CANDLE_DEEP, Theme.GRID, Theme.INK,
                Theme.CROSS_TINT, Comfort.SKY_ALT};
        for (double ratio : new double[]{1.2, 1.45, Theme.FEEDBACK_MIN_RATIO, 1.8, 2.5}) {
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

    // ---- Stacked glow ---------------------------------------------------------------------

    /**
     * The rule that stops a halo banding, proved rather than eyeballed.
     *
     * <p>The win card's glow was four concentric rings at alpha 13 — four countable
     * contour lines around the picture the whole game is building toward. A ring that
     * composites at 4/255 cannot make an edge on any surface, and
     * {@link Draw#glowRingAlpha} has to keep every ring under that while still summing to
     * the brightness that was asked for.
     */
    @Test
    public void noRingOfAStackedGlowCanEverBand() {
        for (float reach : new float[]{6, 20, 63, 84, 126, 400}) {
            for (float peak : new float[]{4, 12, 30, 64, 119, 255}) {
                int rings = Draw.glowRings(reach, peak);
                float safe = Draw.glowPeak(reach, peak);
                if (rings == 0) {
                    assertTrue("a glow was dropped although it had room to be drawn",
                            safe < 1);
                    continue;
                }
                int total = 0;
                for (int ring = 0; ring < rings; ring++) {
                    int step = Draw.glowRingAlpha(ring, rings, safe);
                    assertTrue("ring " + ring + " of " + rings + " at reach " + reach
                                    + " carries " + step + ", which is a visible contour",
                            step <= Theme.GLOW_STEP_ALPHA_MAX);
                    assertTrue("a ring carries negative alpha", step >= 0);
                    total += step;
                }
                assertEquals("the ladder did not add up to the brightness asked for",
                        Math.round(safe), total);
                assertTrue("rings are closer together than a pixel can separate",
                        reach / rings >= 2f - 1e-4f);
            }
        }
    }

    /**
     * A halo cannot be brighter than its reach allows: four levels every two pixels is two
     * per pixel, and asking for more than that has to produce a step somewhere. The
     * request is honoured as far as the geometry goes and no further, rather than being
     * met with a bright ring.
     */
    @Test
    public void aGlowIsNeverBrighterThanItsReachCanCarry() {
        assertEquals(0f, Draw.glowPeak(0, 64), .001f);
        assertEquals(0f, Draw.glowPeak(64, 0), .001f);
        assertEquals("a short reach was allowed to carry a full-strength glow",
                20f, Draw.glowPeak(10, 64), .001f);
        assertEquals("a long reach was not given the brightness it asked for",
                64f, Draw.glowPeak(400, 64), .001f);
        assertTrue("the nominal ring count is too coarse to hide a step",
                Theme.GLOW_STEPS >= 12);
        assertEquals("a generous reach should still use the nominal ring count",
                Theme.GLOW_STEPS, Draw.glowRings(126, 12));
    }

    // ---- Motion ---------------------------------------------------------------------------

    /** The duration ladder has to be a ladder: three names, three clearly different beats. */
    @Test
    public void theMotionLadderAscends() {
        assertTrue(Theme.MOTION_QUICK_MS < Theme.MOTION_SETTLE_MS);
        assertTrue(Theme.MOTION_SETTLE_MS < Theme.MOTION_WARM_MS);
        assertTrue("a quick beat is slower than the 120 ms an input has to be confirmed in",
                Theme.MOTION_QUICK_MS <= 250);
        assertTrue("the warmest beat is long enough to be a wait",
                Theme.MOTION_WARM_MS <= 800);
        assertTrue("two beats are close enough to be the same beat",
                Theme.MOTION_WARM_MS >= Theme.MOTION_SETTLE_MS * 1.5f);
    }

    /**
     * The cursor's glide, expressed as a time constant rather than as a fraction per
     * frame. The point is that the same code moves the same way whatever the display is
     * doing, which is the property a per-frame ease cannot have.
     */
    @Test
    public void theGlideIsTheSameMotionAtAnyFrameRate() {
        float atSixty = 0;
        for (int frame = 0; frame < 6; frame++) {
            atSixty += (1 - atSixty) * Draw.approachRate(1000f / 60, Theme.MOTION_TAU_MS);
        }
        float atThirty = 0;
        for (int frame = 0; frame < 3; frame++) {
            atThirty += (1 - atThirty) * Draw.approachRate(1000f / 30, Theme.MOTION_TAU_MS);
        }
        assertEquals("100 ms of glide differs between 30 and 60 Hz", atSixty, atThirty,
                .002f);
        assertTrue("the glide is 90% closed after " + atSixty + " of 100 ms",
                atSixty >= .88f && atSixty <= .95f);

        // A cursor landing has to be over before the next held repeat starts a new one.
        assertTrue("a landing outlives two held repeats",
                Theme.CURSOR_LAND_MS <= Theme.BOARD_REPEAT_MS * 2);
    }

    /**
     * Holding a direction is how you travel on a board, so it has to be a sweep you can
     * stop where you meant to. The board had no gate at all and moved once per frame — a
     * one-second hold crossed a 20-wide grid three times and fired sixty move sounds.
     */
    @Test
    public void aHeldDirectionIsASweepNotASprint() {
        assertTrue(Theme.REPEAT_FAST_MS < Theme.REPEAT_MS);
        assertTrue(Theme.REPEAT_MS < Theme.REPEAT_FIRST_MS);
        assertEquals("the board's cadence has drifted off the shared ladder",
                Theme.REPEAT_FAST_MS, Theme.BOARD_REPEAT_MS);
        assertEquals("a menu's cadence has drifted off the shared ladder",
                Theme.REPEAT_MS, Theme.MENU_REPEAT_MS);

        for (long cadence : new long[]{Theme.BOARD_REPEAT_MS, Theme.MENU_REPEAT_MS}) {
            int steps = stepsWhileHeld(1000, cadence);
            assertTrue("a one-second hold advanced " + steps + " cells", steps <= 12);
            assertTrue("a one-second hold advanced only " + steps + " cells", steps >= 4);
        }
        // The first repeat has to be late enough that a single tap is never two moves.
        assertTrue("a tap could repeat", Theme.REPEAT_FIRST_MS >= 250);
    }

    /** How many moves a held direction produces in {@code windowMs}, first press included. */
    private static int stepsWhileHeld(long windowMs, long cadence) {
        int steps = 1;
        for (long at = Theme.REPEAT_FIRST_MS; at <= windowMs; at += cadence) {
            steps++;
        }
        return steps;
    }

    /** The room dims and quietens on one clock, and only once you have really stopped. */
    @Test
    public void theIdleHushGivesYouTimeToThink() {
        assertTrue(Theme.IDLE_HUSH_START_MS < Theme.IDLE_HUSH_FULL_MS);
        assertTrue("the hush starts before a long think is over",
                Theme.IDLE_HUSH_START_MS >= 20_000);
        assertTrue("the hush takes so long nobody will hear it",
                Theme.IDLE_HUSH_FULL_MS <= 90_000);
        // A shared moment is rarer than an idle one, or it stops being a moment.
        assertTrue(Theme.TOGETHER_COOLDOWN_MS > Theme.IDLE_HUSH_START_MS);
        // And a partner's square is protected for longer than a fumbled double press.
        assertTrue(Theme.PARTNER_GRACE_MS > Theme.REPEAT_FIRST_MS * 2);
    }

    /**
     * The celebration's darkness is a theme decision now rather than two magic numbers.
     * At the old peak of 238 the screen kept 7% of the room: the board, the last square
     * and the pulse that had just landed were all extinguished before the paper had moved
     * a sixth of the way off the table, which reads as a crash rather than as a curtain.
     */
    @Test
    public void theCelebrationNeverBlacksOutTheRoom() {
        assertTrue(Theme.SCRIM_REST < Theme.SCRIM_PEAK);
        assertTrue("the celebration's peak scrim leaves only "
                        + Math.round((255 - Theme.SCRIM_PEAK) * 100 / 255f)
                        + "% of the room", (255 - Theme.SCRIM_PEAK) / 255f >= .33f);
        assertTrue("the scrim is too thin to stop the board ghosting through",
                Theme.SCRIM_PEAK >= 140);
        assertRatio("cream on the settled scrim", Theme.CREAM,
                Draw.blend(Theme.BACKDROP_REF, Theme.NIGHT, Theme.SCRIM_REST / 255f), 7);
    }

    // ---- Identity, seen and heard ---------------------------------------------------------

    /**
     * Pitch is the audible half of a player's identity, so it lives beside
     * {@link Theme#playerColor(int)}. Rose and Sky were byte-identical, which meant that
     * with two people playing there was no way to hear whose square had landed.
     */
    @Test
    public void aPlayersPitchIsAPerfectFifthAboveTheOthers() {
        assertEquals(1f, Theme.playerPitch(0), 1e-6);
        double fifth = Math.pow(2, 7 / 12.0);
        double cents = 1200 * Math.log(Theme.playerPitch(1) / Theme.playerPitch(0))
                / Math.log(2);
        assertEquals("Sky is not a perfect fifth above Rose", fifth,
                Theme.playerPitch(1), .0005);
        assertTrue("Sky is " + cents + " cents above Rose, which is audibly out of tune",
                Math.abs(cents - 700) < 2);
    }

    /** The colour of something both players did, in both comfort settings. */
    @Test
    public void togetherIsBothPlayersMeetingInTheMiddle() {
        for (boolean distinct : new boolean[]{false, true}) {
            Comfort.get().distinctPlayers = distinct;
            int together = Theme.togetherColor();
            double toRose = Theme.contrastRatio(together, Theme.playerColor(0));
            double toSky = Theme.contrastRatio(together, Theme.playerColor(1));
            assertTrue("together sits on Rose rather than between the two: " + toRose
                    + " vs " + toSky, Math.max(toRose, toSky) / Math.min(toRose, toSky)
                    <= 2.6);
            // It is a shared colour, not a legible one — every caller has to lift it.
            assertRatio("together, lifted for a panel",
                    Theme.readableOn(together, Theme.PANEL, 4.5), Theme.PANEL, 4.5);
            assertRatio("together, lifted for paper",
                    Theme.readableOn(together, Theme.PAPER, 4.5), Theme.PAPER, 4.5);
        }
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

    /** Panel 30, card 22, chip 14: a ramp, each about .7 of the one above. */
    @Test
    public void theCornerRampDescends() {
        assertTrue(Theme.PANEL_RADIUS > Theme.RADIUS_CARD);
        assertTrue(Theme.RADIUS_CARD > Theme.RADIUS_CHIP);
        assertEquals(.73f, Theme.RADIUS_CARD / Theme.PANEL_RADIUS, .05f);
        assertEquals(.64f, Theme.RADIUS_CHIP / Theme.RADIUS_CARD, .05f);
    }

    /** Both stroke weights keep their floors on a small screen and grow on a big one. */
    @Test
    public void hairlineAndKeylineHoldTheirFloors() {
        for (float height : new float[]{240, 480, 720, 1080, 1440, 2160}) {
            Theme.setScreenHeight(height);
            assertTrue("a hairline vanished at " + height + "p", Theme.hairline() >= 1.5f);
            assertTrue("a keyline vanished at " + height + "p", Theme.keyline() >= 1f);
            assertTrue("a keyline outweighed a hairline at " + height + "p",
                    Theme.hairline() > Theme.keyline());
        }
        Theme.setScreenHeight(1080);
        assertEquals(3f, Theme.hairline(), .001f);
        assertEquals(1.5f, Theme.keyline(), .001f);
    }

    /**
     * A cap on the cell size is what stops a 5x5 board reading as a spreadsheet: without
     * it the smallest puzzle spends the whole card on twenty-five 125 px squares.
     */
    @Test
    public void aSmallBoardIsAJewelRatherThanASpreadsheet() {
        Theme.setScreenHeight(1080);
        assertEquals(156f, Theme.scale(Theme.MAX_CELL), .01f);
        BoardLayout small = new BoardLayout(1920, 1080, new GameState(1, 5).puzzle, true);
        assertTrue("a 5x5 board would rather be " + small.cell + " px a square than the "
                        + Theme.scale(Theme.MAX_CELL) + " px cap",
                small.cell >= Theme.scale(Theme.MAX_CELL) * .5f);
    }

    /**
     * The bound the generator promises and the layout reserves against. Columns are the
     * under-worked axis, so their cap carries a spare group for the picture detail that has
     * not been drawn yet.
     */
    @Test
    public void theClueGroupBoundsLeaveTheColumnsRoomToGrow() {
        assertTrue(Theme.MAX_ROW_CLUE_GROUPS > 0 && Theme.MAX_COL_CLUE_GROUPS > 0);
        assertTrue("a 20-wide row cannot hold that many groups",
                Theme.MAX_ROW_CLUE_GROUPS <= 10);
        assertTrue("the column bound has no headroom left",
                Theme.MAX_COL_CLUE_GROUPS >= 5);
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /** The crossed square as {@link BoardRenderer} composites it, at the stated ratio. */
    private static int crossedSquare(boolean highContrast) {
        double ratio = highContrast ? Theme.CROSS_RATIO_BOLD : Theme.CROSS_RATIO;
        int alpha = Theme.washAlpha(Theme.CROSS_TINT, Theme.PAPER, ratio, 200);
        return Draw.blend(Theme.PAPER, Theme.CROSS_TINT, alpha / 255f);
    }

    /**
     * Opaque ARGB, built here for the same reason {@code Theme.rgb} builds its own: under
     * {@code returnDefaultValues} every {@code android.graphics.Color} method answers
     * zero, so a sampled colour written as {@code Color.rgb(...)} is transparent black and
     * whatever it was supposed to prove is not being proved.
     */
    private static int rgb(int red, int green, int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    private static int red(int color) {
        return (color >> 16) & 0xff;
    }

    private static int green(int color) {
        return (color >> 8) & 0xff;
    }

    private static int blue(int color) {
        return color & 0xff;
    }

    /** Angular distance between two hues, in degrees, for "these are not the same idea". */
    private static double hueDistance(int first, int second) {
        double delta = Math.abs(hue(first) - hue(second));
        return Math.min(delta, 360 - delta);
    }

    private static double hue(int color) {
        double r = red(color) / 255.0;
        double g = green(color) / 255.0;
        double b = blue(color) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        if (max == min) {
            return 0;
        }
        double h;
        if (max == r) {
            h = (g - b) / (max - min);
        } else if (max == g) {
            h = 2 + (b - r) / (max - min);
        } else {
            h = 4 + (r - g) / (max - min);
        }
        return (h * 60 + 360) % 360;
    }

    private static void assertRatio(String what, int color, int background,
                                    double minimum) {
        double ratio = Theme.contrastRatio(color, background);
        assertTrue(what + " is only " + Math.round(ratio * 100) / 100.0 + ":1, wanted "
                + minimum + ":1", ratio >= minimum);
    }
}

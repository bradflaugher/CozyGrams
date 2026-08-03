package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The contract the rest of the game leans on {@link Effects} for.
 *
 * <p>Four other components call into this class, and three of them are drawing code that a
 * unit test cannot look at. What <em>can</em> be pinned down is the behaviour they depend
 * on: that the frame loop is told to keep running while a staggered wave is still queued,
 * that the board renderer gets a usable pop value, that a celebration cannot be made to
 * throw however much is thrown at it, that calm motion really does calm things down, and
 * that the same inputs always produce the same frame.
 */
public class EffectsTest {

    private static final long T0 = 1_800_000L;

    @Before
    public void setUp() {
        Comfort.get().restoreDefaults();
        Theme.setScreenHeight(1080);
    }

    @After
    public void tearDown() {
        Comfort.get().restoreDefaults();
    }

    // ---- busy() ---------------------------------------------------------------------

    @Test
    public void nothingEmittedMeansNothingToDraw() {
        assertFalse(new Effects().busy(T0));
    }

    @Test
    public void busyGoesQuietOnceEverythingHasExpired() {
        Effects effects = new Effects();
        effects.pulse(Effects.Pulse.FILL, 2, 3, Theme.PINK, T0);
        effects.burst(400, 400, 7, 150, Theme.PINK, Effects.SHAPE_DOT, T0);
        assertTrue(effects.busy(T0));

        // Longest thing in flight is a dot, which lives well under two seconds.
        assertFalse("still busy long after every effect has ended",
                effects.busy(T0 + 10_000));
        assertEquals(0, effects.livePulses(T0 + 10_000));
        assertEquals(0, effects.liveParticles(T0 + 10_000));
    }

    /**
     * The line-clear wave is a queue of pulses whose start times are still in the future.
     * If {@code busy} ignored them the view would stop asking for frames and the wave would
     * simply never be drawn.
     */
    @Test
    public void busyCoversEffectsThatHaveNotStartedYet() {
        Effects effects = new Effects();
        long start = T0 + 900;
        effects.pulse(Effects.Pulse.LINE, 4, 4, Theme.GOLD, start, 420);

        assertTrue("a pending pulse has to keep the frame loop awake", effects.busy(T0));
        assertEquals(1, effects.livePulses(T0));
        assertTrue(effects.busy(start + 100));
        assertFalse(effects.busy(start + 420));
    }

    @Test
    public void aStaggeredLineClearStaysBusyUntilTheLastSquare() {
        Effects effects = new Effects();
        for (int column = 0; column < 20; column++) {
            effects.pulse(Effects.Pulse.LINE, column, 7, Theme.GOLD, T0 + column * 18L, 420);
        }
        assertEquals(20, effects.livePulses(T0));
        assertTrue(effects.busy(T0 + 19 * 18L + 419));
        assertFalse(effects.busy(T0 + 19 * 18L + 420));
    }

    // ---- squareProgress() -----------------------------------------------------------

    @Test
    public void squareProgressIsMinusOneWhenNothingIsHappening() {
        Effects effects = new Effects();
        assertEquals(-1f, effects.squareProgress(3, 3, T0), 0f);

        effects.pulse(Effects.Pulse.FILL, 3, 3, Theme.PINK, T0);
        assertEquals("a neighbour is not animating", -1f,
                effects.squareProgress(4, 3, T0), 0f);
        assertEquals("expired pulses stop reporting", -1f,
                effects.squareProgress(3, 3, T0 + 5000), 0f);
    }

    @Test
    public void squareProgressRunsFromZeroToOneWhileAnimating() {
        Effects effects = new Effects();
        effects.pulse(Effects.Pulse.FILL, 3, 3, Theme.PINK, T0, 400);

        assertEquals(0f, effects.squareProgress(3, 3, T0), 1e-4);
        float previous = -1;
        for (long age = 0; age < 400; age += 25) {
            float progress = effects.squareProgress(3, 3, T0 + age);
            assertTrue("progress left 0..1 at " + age + "ms",
                    progress >= 0f && progress <= 1f);
            assertTrue("progress went backwards at " + age + "ms", progress > previous);
            previous = progress;
        }
    }

    /** A pulse that has not begun yet must not make the square jump to its start pose. */
    @Test
    public void squareProgressIgnoresPulsesStillInTheFuture() {
        Effects effects = new Effects();
        effects.pulse(Effects.Pulse.LINE, 6, 6, Theme.GOLD, T0 + 300, 420);
        assertEquals(-1f, effects.squareProgress(6, 6, T0), 0f);
        assertTrue(effects.squareProgress(6, 6, T0 + 350) >= 0f);
    }

    /**
     * A line crest sweeps over squares that are already placed, so it only ever asks for
     * the tail of the pop curve — a small swell, not a collapse to half size and back.
     */
    @Test
    public void aLineCrestOnlyRipplesAnAlreadyPlacedSquare() {
        Effects effects = new Effects();
        effects.pulse(Effects.Pulse.LINE, 1, 1, Theme.GOLD, T0, 420);
        float progress = effects.squareProgress(1, 1, T0);
        assertTrue("a line crest should start deep into the curve, not at zero",
                progress >= .5f && progress < 1f);
    }

    /**
     * The tile's own pop is now the <em>whole</em> confirmation of a fill — the ring that
     * used to carry it was drawn into the annulus the cursor occupies permanently. So the
     * curve has to have arrived by the time the thumb leaves the button: 120 ms is the
     * span inside which a press still feels connected to the board.
     */
    @Test
    public void aFillIsFullySettledInsideAHundredAndTwentyMilliseconds() {
        Effects effects = new Effects();
        effects.pulse(Effects.Pulse.FILL, 3, 3, Theme.PINK, T0);

        float landed = effects.squareProgress(3, 3, T0 + 120);
        assertTrue("a fill was still animating at 120ms: " + landed, landed > 0f);
        assertTrue("the tile had not reached full size by 120ms",
                Draw.easeOutBack(landed) >= 1f);
        assertEquals("a fill should be over well inside a quarter second", -1f,
                effects.squareProgress(3, 3, T0 + 250), 0f);
    }

    /**
     * The two numbers the board renderer turns {@link Effects#squareProgress} into. Both
     * have to resolve to the square's ordinary appearance, or a tile would be left
     * permanently small or permanently pale once its pulse expired.
     */
    @Test
    public void theSettleMappingResolvesToTheTilesOwnLook() {
        assertTrue(Effects.SETTLE_SCALE > 0f && Effects.SETTLE_SCALE < 1f);
        assertTrue(Effects.SETTLE_LIFT > 0f && Effects.SETTLE_LIFT < 1f);
        // scale(1) == 1 and lift(1) == 0: nothing has to be unwound when a pulse ends.
        float settled = Effects.SETTLE_SCALE + (1 - Effects.SETTLE_SCALE)
                * Draw.easeOutBack(1f);
        assertEquals(1f, settled, 1e-4);
        assertEquals(0f, Effects.SETTLE_LIFT * (1 - 1f), 0f);
    }

    /** A spray is board chrome, so it must not be able to wear either player's colour. */
    @Test
    public void aBoardSprayIsNeitherPlayersColour() {
        int[] identities = {Theme.PINK, Theme.PINK_LIGHT, Theme.PINK_DARK, Theme.BLUE,
                Theme.BLUE_LIGHT, Comfort.SKY_ALT, Comfort.SKY_ALT_LIGHT};
        for (int identity : identities) {
            assertTrue("a spray still reads as " + Integer.toHexString(identity),
                    Theme.contrastRatio(Effects.CANDLE, identity) > 1.15
                            || farApart(Effects.CANDLE, identity));
        }
        // And it has to survive landing on the paper it is thrown onto.
        assertTrue("candlelight vanishes on paper",
                Theme.contrastRatio(Effects.CANDLE, Theme.PAPER) >= 2.0);

        // The colour that actually ships is not CANDLE but the caller's colour pulled .72
        // of the way to it, so that is what has to clear the same two bars. This was the
        // gap the old test left: CANDLE passed on its own while every spray the game
        // emitted was a pale, barely-tinted pink or gold.
        for (int player = 0; player < 2; player++) {
            for (int asked : new int[]{Theme.playerColor(player), Theme.playerColorLight(player),
                    Theme.playerColorDark(player)}) {
                int shipped = Draw.blend(asked, Effects.CANDLE, .72f);
                assertTrue("a spray from " + Integer.toHexString(asked) + " still reads as "
                                + Theme.playerName(player),
                        Theme.contrastRatio(shipped, asked) > 1.15
                                || farApart(shipped, asked));
                floor("a " + Theme.playerName(player) + " spray", shipped, Theme.PAPER, 225);
            }
        }
    }

    /** True when two colours differ by more than a nudge in any channel. */
    private static boolean farApart(int first, int second) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((first >> shift) & 0xff) - ((second >> shift) & 0xff)) >= 48) {
                return true;
            }
        }
        return false;
    }

    // ---- The perceptual floor ---------------------------------------------------------
    //
    // The audit that prompted these measured every action pulse in the game landing between
    // 1.05:1 and 1.37:1 against the surface it was drawn on. None of that is visible from a
    // sofa, and none of it was catchable by a test, because every alpha was a magic number
    // inside a draw method that a unit test cannot look at. The alphas are now named and
    // these walk them.

    /**
     * Every piece of feedback ink, composited exactly as it is drawn, against every surface
     * it lands on.
     *
     * <p>Two surfaces, because the board has two: cream paper and a placed tile. A shape
     * that crosses both — a crown, the line wave — has to clear the floor on both, which is
     * why the crowns are keylined and why the wave is candlelight rather than gold.
     */
    @Test
    public void everyPieceOfFeedbackInkClearsTheFloorItIsTunedTo() {
        for (int player = 0; player < 2; player++) {
            int identity = Theme.playerColor(player);
            String who = Theme.playerName(player);

            // The crowns: keyline first, colour on top of it.
            crownFloor(who + "'s fill crown", identity, Effects.CROWN_FILL_ALPHA);
            crownFloor(who + "'s cross flick", identity, Effects.CROWN_CROSS_ALPHA);
            crownFloor(who + "'s undo crown", identity, Effects.CROWN_UNDO_ALPHA);
            crownFloor(who + "'s sigh crown", Theme.playerColorDark(player),
                    Effects.CROWN_SIGH_ALPHA);

            // The under pass, walked to the dark end of the palette and drawn on paper.
            // These are measured at the moment they first clear the tile rather than at
            // their nominal peak, because that is the first instant anybody can see them:
            // the settle fades linearly and emerges at about a fifth of the way through,
            // and the sigh fades on a square and emerges a little sooner.
            floor(who + "'s settle bloom", Effects.paperInk(identity), Theme.PAPER,
                    (int) (Effects.SETTLE_INK_ALPHA * .8f));
            floor(who + "'s undo outline", Effects.paperInk(identity), Theme.PAPER,
                    Effects.UNDO_INK_ALPHA);
            floor(who + "'s sigh bloom", Effects.paperInk(Theme.playerColorDark(player)),
                    Theme.PAPER, (int) (Effects.SIGH_INK_ALPHA * .7f));
            floor(who + "'s cross flick trace", identity, Theme.PAPER,
                    Effects.SCRATCH_INK_ALPHA);
        }

        // The two pulses that belong to the board rather than to a player. These are the
        // ones that have to work on paper and on a tile with one ink, which is the whole
        // job description of Theme.CANDLE.
        for (int surface : new int[]{Theme.PAPER, Theme.TILE}) {
            floor("the line wave's band", Theme.CANDLE, surface, Effects.CREST_BAND_ALPHA);
            floor("the hint crown", Theme.CANDLE, surface, Effects.CROWN_HINT_ALPHA);
            floor("a hint glint", Theme.CANDLE, surface, Effects.CROWN_GLINT_ALPHA);
        }
        floor("the line wave's bright edge", Draw.blend(Theme.CANDLE, Theme.CREAM, .5f),
                Theme.TILE, Effects.CREST_EDGE_ALPHA);
        floor("the hint's trace glint", Theme.CANDLE_DEEP, Theme.PAPER,
                Effects.CROWN_GLINT_ALPHA);
    }

    /**
     * Why the hint and the line wave ignore the colour their caller asks for.
     *
     * <p>Both were emitted in {@link Theme#GOLD}, which is the right gold on the dark hint
     * chip and on the win card. On the board's own paper it is 1.38:1 <em>at full
     * opacity</em>: there was no alpha, and no amount of retuning, that could ever have
     * made either of them visible. That is not a bug in a number, it is a bug in a colour,
     * so {@code Effects} overrides it rather than trusting the call site.
     */
    @Test
    public void theBoardsGoldIsCandleBecauseGoldCannotBeSeenOnPaper() {
        assertTrue("GOLD has become usable on paper — if that is deliberate, say so here",
                Theme.contrastRatio(Theme.GOLD, Theme.PAPER) < Theme.FEEDBACK_MIN_RATIO);
        assertTrue("candlelight no longer clears the floor on paper",
                Theme.contrastRatio(Theme.CANDLE, Theme.PAPER) >= Theme.FEEDBACK_MIN_RATIO);
        assertTrue("candlelight no longer clears the floor on a placed tile",
                Theme.contrastRatio(Theme.CANDLE, Theme.TILE) >= Theme.FEEDBACK_MIN_RATIO);
    }

    /**
     * A crown has to start outside the cursor plate at every board size, and leave enough
     * ring outside it to be a shape.
     *
     * <p>This is the test the class most needed and did not have. Every action fires on the
     * square the acting player's cursor is standing on, so every action pulse is drawn
     * against an opaque cream plate — and the plate is .55 of a cell at 5x5, .60 at 10x10
     * and .65 at 20x20, while the pulses were sized in cells. A radius that cleared the
     * plate on one board was under it on the next, and in practice all of them were under
     * it on all of them: the assertion below on the <em>old</em> geometry is what that
     * looked like.
     */
    @Test
    public void aCrownCannotDriftBackUnderTheCursorPlate() {
        assertTrue("a crown must start outside the plate", Effects.CROWN_IN > 1f);
        // Every board the game ships, at both resolutions, because the plate's share of a
        // cell is exactly what varies between them: .550 at 5x5 and .702 at 20x20.
        for (int height : new int[]{1080, 720}) {
            Theme.setScreenHeight(height);
            for (int size : new int[]{5, 10, 15, 20}) {
                float cell = new BoardLayout(height * 16 / 9f, height,
                        PuzzleGenerator.compose(0, 0, size), true).cell;
                float plate = BoardRenderer.plateHalf(cell);
                String what = height + "p " + size + "x" + size;

                float ring = plate * 1.45f - plate * Effects.CROWN_IN;
                assertTrue(what + ": the crown's ring is only " + ring / cell + " of a cell",
                        ring >= cell * .18f);
                // The cozy guard rail, which is the other half of the same decision: no
                // shape this layer draws is wider than 1.05 cells, however large the plate
                // it has to climb over gets. At 20x20 the crown reaches 1.018.
                assertTrue(what + ": the crown reaches " + (plate * 1.45f / cell) + " cells",
                        plate * 1.45f <= cell * 1.05f);
                // And the shape the crown replaced: .52 of a cell was the old fill pulse's
                // largest radius, which is inside the plate on every board that ships.
                assertTrue(what + ": cell*.52 has stopped being under the plate — the "
                                + "reason for tying crowns to plateHalf may have gone",
                        cell * .52f < plate);
            }
        }
        Theme.setScreenHeight(1080);
    }

    /**
     * The line wave has to be a band travelling down the line, not the line switching on.
     *
     * <p>The class documentation has claimed "one thing travelling" since the crest was
     * written; the arithmetic said the opposite. A square is lit while its crest is above
     * about a third of its peak, so the number of squares alight at once is that span
     * divided by the emitter's stagger — and at 420 ms with an 18 ms stagger it was 11
     * squares, which on a 10x10 board is the entire row.
     */
    @Test
    public void theLineWaveIsABandNotAWholeRow() {
        Effects effects = new Effects();
        effects.pulse(Effects.Pulse.LINE, 3, 3, Theme.GOLD, T0);
        long lineMs = endOfPulse(effects, T0);

        float from = -1;
        float to = -1;
        for (float p = 0; p <= 1f; p += .001f) {
            if (Effects.crest(p) >= .35f) {
                if (from < 0) {
                    from = p;
                }
                to = p;
            }
        }
        float visibleMs = (to - from) * lineMs;
        assertTrue("one square of the wave stays lit for " + visibleMs + " ms",
                visibleMs > 110 && visibleMs < 180);

        // What the emitter should be doing with it: a board-size-aware stagger between
        // 26 ms and 46 ms, which is 3 to 6 squares alight at once.
        for (long stagger : new long[]{26, 46}) {
            float squares = visibleMs / stagger;
            assertTrue("a " + stagger + " ms stagger lights " + squares + " squares at once",
                    squares >= 2.5f && squares <= 6.5f);
        }
    }

    /**
     * The celebration gets whole gutters, and can be given the whole screen.
     *
     * <p>The gutter runs from the safe edge to the reading column, and the reading column
     * already <em>is</em> the band being kept clear — so subtracting the safe margin from
     * it a second time, which is what the old arithmetic did, left the win screen's own
     * numbers describing two lanes 77 px wide, both hard against the overscan boundary,
     * with every celebration particle inside them.
     */
    @Test
    public void theWinDriftGetsWholeGuttersAndCanBeGivenTheScreen() {
        float width = 1920;
        float margin = width * Theme.SAFE_AREA;
        Effects effects = new Effects();
        effects.setReadingColumn(.14f, .86f);

        assertEquals("the left gutter must start at the safe edge", margin,
                effects.laneColumn(0f, width), .5f);
        float leftGutter = effects.laneColumn(.4999f, width) - effects.laneColumn(0f, width);
        float rightGutter = effects.laneColumn(1f, width) - effects.laneColumn(.5f, width);
        assertTrue("the left gutter is only " + leftGutter + " px of " + width,
                leftGutter >= width * .08f);
        assertEquals("the two gutters should be the same width", leftGutter, rightGutter, 1f);
        assertEquals("the right gutter must end at the safe edge", width - margin,
                effects.laneColumn(1f, width), .5f);

        effects.clearReadingColumn();
        float span = effects.laneColumn(1f, width) - effects.laneColumn(0f, width);
        assertEquals("with no reading column the drift should have the whole safe width",
                width - margin * 2, span, .5f);
        assertEquals(width * .5f, effects.laneColumn(.5f, width), .5f);
    }

    /** {@code ink} at {@code alpha} composited over an opaque surface. */
    private static int over(int ink, int surface, int alpha) {
        return Draw.blend(surface, ink, Math.max(0, Math.min(255, alpha)) / 255f);
    }

    private static void floor(String what, int ink, int surface, int alpha) {
        double ratio = Theme.contrastRatio(over(ink, surface, alpha), surface);
        assertTrue(what + " measures " + String.format("%.2f", ratio) + ":1 at alpha "
                        + alpha + " on " + Integer.toHexString(surface) + ", under the "
                        + Theme.FEEDBACK_MIN_RATIO + ":1 floor",
                ratio >= Theme.FEEDBACK_MIN_RATIO);
    }

    /**
     * A crown is a dark keyline with the colour laid over it, so two things have to read:
     * the rim of keyline that shows either side, and the colour in the middle of it.
     */
    private static void crownFloor(String what, int color, int alpha) {
        int keyed = (int) (alpha * Effects.CROWN_KEYLINE_ALPHA);
        int ink = Effects.crownInk(color);
        for (int surface : new int[]{Theme.PAPER, Theme.TILE}) {
            int rim = over(Effects.CROWN_KEYLINE, surface, keyed);
            double rimRatio = Theme.contrastRatio(rim, surface);
            double faceRatio = Theme.contrastRatio(over(ink, rim, alpha), surface);
            assertTrue(what + " on " + Integer.toHexString(surface) + " reads at "
                            + String.format("%.2f", Math.max(rimRatio, faceRatio))
                            + ":1, under the " + Theme.FEEDBACK_MIN_RATIO + ":1 floor",
                    Math.max(rimRatio, faceRatio) >= Theme.FEEDBACK_MIN_RATIO);
        }
    }

    // ---- clear() ----------------------------------------------------------------------

    @Test
    public void clearReallyClears() {
        Effects effects = new Effects();
        for (int i = 0; i < 20; i++) {
            effects.pulse(Effects.Pulse.LINE, i, 3, Theme.GOLD, T0 + i * 18L, 420);
        }
        effects.pulse(Effects.Pulse.FILL, 5, 5, Theme.PINK, T0);
        effects.burst(500, 500, 12, 200, Theme.GOLD, Effects.SHAPE_SPARK, T0);
        effects.rise(900, 1000, 8, 300, Theme.PINK, Effects.SHAPE_HEART, T0, 1500);
        assertTrue(effects.busy(T0));

        effects.clear();

        assertFalse(effects.busy(T0));
        assertEquals(0, effects.livePulses(T0));
        assertEquals(0, effects.liveParticles(T0));
        assertEquals(-1f, effects.squareProgress(5, 5, T0), 0f);
        assertEquals("a cleared board draws the same frame as a fresh one",
                new Effects().stateDigest(T0), effects.stateDigest(T0));
    }

    // ---- capacity -----------------------------------------------------------------------

    @Test
    public void aBurstFarBiggerThanCapacityIsSurvivable() {
        Effects effects = new Effects();
        effects.burst(600, 400, 5000, 300, Theme.PINK, Effects.SHAPE_DOT, T0);
        int live = effects.liveParticles(T0);
        assertTrue("capacity should be respected, saw " + live, live > 0 && live <= 512);
        assertTrue(effects.busy(T0));
        effects.clear();
        assertEquals(0, effects.liveParticles(T0));
    }

    @Test
    public void anAbsurdNumberOfPulsesIsSurvivable() {
        Effects effects = new Effects();
        for (int i = 0; i < 4000; i++) {
            effects.pulse(Effects.Pulse.LINE, i % 20, (i / 20) % 20, Theme.GOLD, T0, 420);
        }
        assertTrue(effects.livePulses(T0) <= 256);
        assertTrue(effects.busy(T0));
    }

    /**
     * The point of the priority rule: a twenty-square line wave must not be able to evict
     * the fill that a player just made, however full the buffer is.
     */
    @Test
    public void decorationNeverEvictsAPlayersOwnFeedback() {
        Effects effects = new Effects();
        effects.pulse(Effects.Pulse.FILL, 0, 0, Theme.PINK, T0);
        for (int i = 0; i < 3000; i++) {
            effects.pulse(Effects.Pulse.LINE, 1 + i % 19, i % 20, Theme.GOLD, T0, 420);
        }
        assertTrue("the player's own fill was evicted by decoration",
                effects.squareProgress(0, 0, T0 + 50) >= 0f);
    }

    /** The worst real case: both players finish a row and a column on a 20x20 board. */
    @Test
    public void theWorstRealLineClearFitsWithRoomToSpare() {
        Effects effects = new Effects();
        for (int player = 0; player < 2; player++) {
            effects.pulse(Effects.Pulse.FILL, player, player, Theme.playerColor(player), T0);
            for (int i = 0; i < 20; i++) {
                effects.pulse(Effects.Pulse.LINE, i, player, Theme.GOLD, T0 + i * 18L, 420);
                effects.pulse(Effects.Pulse.LINE, player, i, Theme.GOLD, T0 + i * 18L, 420);
            }
        }
        assertEquals("every pulse of the worst real case should survive", 82,
                effects.livePulses(T0));
    }

    /** The whole win celebration, exactly as CozyGameView emits it. */
    @Test
    public void theWholeWinCelebrationFits() {
        assertEquals(68, celebration().liveParticles(T0));
    }

    /**
     * The win screen's own documentation promises everything has landed inside two seconds.
     * It used to be a lie by more than twice over: 34 emissions staggered 42 ms apart, each
     * living 2600 ms, left 68 particles in flight at 2.2 s, 47 at 3.0 s and the last one
     * alive at about 4.4 s — so the "still" screen was still moving for the length of the
     * whole celebration again. A tighter stagger and a shorter life put the last death
     * inside 2.4 s and take the middle of it far below that.
     */
    @Test
    public void theCelebrationIsOverWhenTheCardSaysItIs() {
        Effects effects = celebration();
        assertTrue("the drift should be thinning by 1.5s",
                effects.liveParticles(T0 + 1500) < 40);
        assertTrue("more than a handful were still flying at 2.2s: "
                        + effects.liveParticles(T0 + 2200),
                effects.liveParticles(T0 + 2200) <= 8);
        assertEquals("something was still flying at 2.4s", 0,
                effects.liveParticles(T0 + 2400));
        assertFalse("the frame loop was still being kept awake at 2.4s",
                effects.busy(T0 + 2400));
    }

    /** The celebration exactly as {@code CozyGameView.showWinCelebration} emits it. */
    private static Effects celebration() {
        Effects effects = new Effects();
        for (int i = 0; i < Effects.WIN_DRIFT_EMISSIONS; i++) {
            effects.rise(1920 * (.16f + (i * 37 % 100) / 100f * .68f), 1080 * .96f, 2,
                    1080 * .30f, Effects.confettiColor(i), confettiShape(i),
                    T0 + i * Effects.WIN_DRIFT_STAGGER_MS, Effects.WIN_DRIFT_LIFE_MS);
        }
        return effects;
    }

    // ---- calm motion ---------------------------------------------------------------------

    @Test
    public void calmMotionMeasurablyThinsTheParticles() {
        Effects lively = new Effects();
        lively.burst(600, 400, 7, 150, Theme.PINK, Effects.SHAPE_DOT, T0);
        lively.burst(600, 400, 10, 150, Theme.GOLD, Effects.SHAPE_SPARK, T0);
        int livelyCount = lively.liveParticles(T0);

        Comfort.get().calmMotion = true;
        Effects calm = new Effects();
        calm.burst(600, 400, 7, 150, Theme.PINK, Effects.SHAPE_DOT, T0);
        calm.burst(600, 400, 10, 150, Theme.GOLD, Effects.SHAPE_SPARK, T0);
        int calmCount = calm.liveParticles(T0);

        assertTrue("calm motion should leave at least a token of feedback", calmCount > 0);
        assertTrue("calm motion emitted " + calmCount + " of " + livelyCount,
                calmCount * 2 <= livelyCount);
        // And the other side of it, which had no test and had quietly been lost: calm
        // means gentle, not absent. At one in four a 7-dot fill became two dots and a
        // 10-spark hint three, and with the ink halved on top of that a player who asked
        // for calm motion had no visible feedback at all.
        assertTrue("calm motion left only " + calmCount + " of " + livelyCount
                        + " — that is absent, not gentle",
                calmCount * 4 >= livelyCount);
    }

    @Test
    public void calmMotionThinsTheWinCelebrationToo() {
        assertTrue(celebrationSize(false) > celebrationSize(true) * 2);
    }

    private int celebrationSize(boolean calm) {
        Comfort.get().calmMotion = calm;
        Effects effects = new Effects();
        for (int i = 0; i < 34; i++) {
            effects.rise(960, 1036, 2, 324, Effects.confettiColor(i), confettiShape(i), T0, 1500);
        }
        return effects.liveParticles(T0);
    }

    @Test
    public void calmMotionShortensAndFlattensPulses() {
        Effects lively = new Effects();
        lively.pulse(Effects.Pulse.FILL, 1, 1, Theme.PINK, T0);

        Comfort.get().calmMotion = true;
        Effects calm = new Effects();
        calm.pulse(Effects.Pulse.FILL, 1, 1, Theme.PINK, T0);

        // Shorter: the calm pulse has to finish first.
        long livelyEnd = endOfPulse(lively, T0);
        long calmEnd = endOfPulse(calm, T0);
        assertTrue("calm pulses should be briefer: " + calmEnd + " vs " + livelyEnd,
                calmEnd < livelyEnd);

        // Flatter: the square starts most of the way to its final size instead of at 55%.
        assertTrue("calm motion should not collapse a square before it pops",
                calm.squareProgress(1, 1, T0) > .5f);
    }

    /** Calm motion opts out of the line ripple entirely — placed squares simply stay put. */
    @Test
    public void calmMotionLeavesFinishedLinesAlone() {
        Comfort.get().calmMotion = true;
        Effects effects = new Effects();
        effects.pulse(Effects.Pulse.LINE, 2, 2, Theme.GOLD, T0, 420);
        assertEquals(-1f, effects.squareProgress(2, 2, T0 + 10), 0f);
        assertTrue("the crest itself should still be drawn", effects.busy(T0 + 10));
    }

    @Test
    public void nothingPulsesForever() {
        Comfort.get().calmMotion = true;
        Effects effects = new Effects();
        for (Effects.Pulse kind : Effects.Pulse.values()) {
            effects.pulse(kind, 1, 1, Theme.PINK, T0);
        }
        effects.burst(500, 500, 12, 200, Theme.GOLD, Effects.SHAPE_SPARK, T0);
        effects.rise(500, 900, 6, 300, Theme.PINK, Effects.SHAPE_HEART, T0, 1500);
        assertFalse("every effect must end", effects.busy(T0 + 30_000));
    }

    // ---- determinism -----------------------------------------------------------------------

    @Test
    public void theSameEmissionsAlwaysDrawTheSameFrame() {
        assertEquals(digestOfATypicalSecond(), digestOfATypicalSecond());
    }

    private long digestOfATypicalSecond() {
        Effects effects = new Effects();
        effects.pulse(Effects.Pulse.FILL, 4, 5, Theme.PINK, T0);
        effects.burst(500, 600, 7, 150, Theme.PINK_LIGHT, Effects.SHAPE_DOT, T0);
        effects.pulse(Effects.Pulse.HINT, 2, 7, Theme.GOLD, T0 + 60);
        effects.burst(300, 700, 10, 150, Theme.GOLD, Effects.SHAPE_SPARK, T0 + 60);
        for (int i = 0; i < 20; i++) {
            effects.pulse(Effects.Pulse.LINE, i, 5, Theme.GOLD, T0 + 60 + i * 18L, 420);
        }
        return effects.stateDigest(T0 + 200);
    }

    /**
     * A spray is reproducible from its own arguments, not from how many sprays happened to
     * come before it: the same burst emitted after a different amount of history is still
     * the same burst.
     */
    @Test
    public void aBurstDependsOnItsOwnSeedAndNothingElse() {
        assertEquals(Effects.seedOf(T0, 500, 600, 7, Effects.SHAPE_DOT),
                Effects.seedOf(T0, 500, 600, 7, Effects.SHAPE_DOT));
        assertNotEquals(Effects.seedOf(T0, 500, 600, 7, Effects.SHAPE_DOT),
                Effects.seedOf(T0 + 1, 500, 600, 7, Effects.SHAPE_DOT));

        Effects plain = new Effects();
        plain.burst(500, 600, 7, 150, Theme.PINK, Effects.SHAPE_DOT, T0);

        Effects withHistory = new Effects();
        for (int i = 0; i < 9; i++) {
            withHistory.burst(100 + i, 200 + i, 5, 90, Theme.GOLD, Effects.SHAPE_SPARK,
                    T0 - 4000 - i);
        }
        withHistory.burst(500, 600, 7, 150, Theme.PINK, Effects.SHAPE_DOT, T0);

        assertEquals("the same burst drew differently because of what preceded it",
                plain.stateDigest(T0 + 120), withHistory.stateDigest(T0 + 120));
    }

    /** Different emissions must not collapse onto the same fingerprint. */
    @Test
    public void differentEmissionsLookDifferent() {
        Effects one = new Effects();
        one.burst(500, 600, 7, 150, Theme.PINK, Effects.SHAPE_DOT, T0);
        Effects two = new Effects();
        two.burst(500, 601, 7, 150, Theme.PINK, Effects.SHAPE_DOT, T0);
        assertNotEquals(one.stateDigest(T0 + 120), two.stateDigest(T0 + 120));
    }

    // ---- the confetti rotation ---------------------------------------------------------------

    /**
     * The win drift's shape rule now lives on {@link Effects}, beside the colour rule it is
     * the sibling of. It was written out as the same nested ternary in three places —
     * {@code CozyGameView.showWinCelebration}, the preview harness so the screenshots would
     * match, and the helper at the foot of this file so the particle counts would.
     *
     * <p>The helper stays, deliberately: it is the independent statement of what the game is
     * supposed to throw, and this test is what holds the shipped rotation to it. Delegating
     * it would turn the mirror into a reflection of itself.
     */
    @Test
    public void confettiShapeIsExactlyTheRotationTheWinDriftThrows() {
        for (int i = 0; i < Effects.WIN_DRIFT_EMISSIONS * 3; i++) {
            assertEquals("index " + i, confettiShape(i), Effects.confettiShape(i));
        }
        assertEquals(Effects.SHAPE_HEART, Effects.confettiShape(0));
        assertEquals(Effects.SHAPE_PETAL, Effects.confettiShape(1));
        assertEquals(Effects.SHAPE_DOT, Effects.confettiShape(2));
        // floorMod rather than %, so an index that ever went negative would still land in
        // the rotation instead of falling out of it. Every caller counts up from zero, which
        // is why the emitted sequence is unchanged.
        assertEquals(Effects.SHAPE_PETAL, Effects.confettiShape(-2));
        assertEquals(Effects.SHAPE_DOT, Effects.confettiShape(-1));
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** The mix of shapes {@code CozyGameView.showWinCelebration} sends up. */
    private static int confettiShape(int index) {
        return index % 3 == 0 ? Effects.SHAPE_HEART
                : (index % 3 == 1 ? Effects.SHAPE_PETAL : Effects.SHAPE_DOT);
    }

    /** The first moment at which nothing is left, found by walking forward. */
    private long endOfPulse(Effects effects, long from) {
        for (long age = 0; age <= 4000; age += 5) {
            if (!effects.busy(from + age)) {
                return age;
            }
        }
        return Long.MAX_VALUE;
    }
}

package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The cursor's motion: the part of the interface a screenshot cannot hold to anything.
 *
 * <p>Every glide in this game used to close a fixed fraction of the remaining distance per
 * <em>frame</em>, which made its speed a property of the panel rather than of the game and
 * left nothing to assert about — "0.34 per frame" is true at any speed. Now that
 * {@link UiState#animateCursors} takes milliseconds there is a curve with a stated shape,
 * so the two things that were only ever hoped for can be proved: that the same wall-clock
 * time produces the same motion whatever the frame rate, and that the 60 Hz feel the game
 * shipped with survived the change.
 *
 * <p>These run with {@code returnDefaultValues}, where every {@code android.graphics}
 * static answers zero, so everything here is worked out with the arithmetic in
 * {@link Theme} and {@link Draw} rather than through the framework.
 */
public class CursorMotionTest {

    /** The old per-frame constant, and the 60 Hz frame it was applied on. */
    private static final float LEGACY_EASE = .34f;
    private static final long FRAME_60HZ_MS = 16;

    private GameState game;
    private UiState ui;

    @Before
    public void setUp() {
        Theme.setScreenHeight(1080);
        Comfort.get().restoreDefaults();
        game = new GameState(1, 10);
        ui = new UiState();
        ui.joined[1] = true;
        ui.snapCursors(game);
    }

    @After
    public void tearDown() {
        Theme.setScreenHeight(720);
        Comfort.get().restoreDefaults();
    }

    // ---- The glide ---------------------------------------------------------------------

    /**
     * The whole point of the change: a 30 Hz box and a 60 Hz box show the same motion.
     *
     * <p>Six steps of 16 ms and three of 32 ms are the same 96 ms of an evening, and an
     * exponential in real time cannot tell them apart. The per-frame version could see
     * nothing else: it would have closed 90% in one case and 71% in the other.
     */
    @Test
    public void glideIsTheSameAtAnyFrameRate() {
        float at60 = travel(6, 16);
        float at30 = travel(3, 32);
        assertEquals("60 Hz and 30 Hz must land in the same place", at60, at30, .01f);
    }

    /**
     * And a 24 Hz box, which is where the per-frame version stopped being merely floaty:
     * .34 a frame at 24 Hz takes 245 ms to close 90% against 92 ms at 60 Hz.
     */
    @Test
    public void glideIsTheSameAtTwentyFourHertz() {
        assertEquals(travel(6, 16), travel(2, 48), .02f);
    }

    /**
     * {@link Theme#MOTION_TAU_MS} was matched to the feel the game shipped with rather than
     * picked as a round number, so this pins it to the thing it was matched against: the old
     * {@code .34} a frame first passed 90% of the gap on its sixth 60 Hz frame, 100 ms in,
     * and 42 ms of tau passes it at 96.7 ms. Three milliseconds apart is under a fifth of a
     * frame, which is the strongest statement anyone can make about a feel.
     */
    @Test
    public void glideKeepsTheSixtyHertzFeelItShippedWith() {
        int frames = 0;
        for (float closed = 0; closed < .9f; frames++) {
            closed += (1 - closed) * LEGACY_EASE;
        }
        assertEquals("the old ease reached 90% here", 100.0, frames * 1000.0 / 60, .1);
        assertEquals("and the new one has to reach it in the same instant",
                96.7, Theme.MOTION_TAU_MS * Math.log(10), .5);
    }

    /** A wrap is a jump, not a journey: it must never streak across the whole board. */
    @Test
    public void wrappingSnapsRatherThanGliding() {
        game.cursorX[0] = 9;
        ui.cursorDrawX[0] = 0;
        ui.animateCursors(game, 16);
        assertEquals(9f, ui.cursorDrawX[0], 0f);
    }

    /** And an ordinary step terminates, rather than approaching for ever. */
    @Test
    public void aGlideActuallyFinishes() {
        game.cursorX[0] = 1;
        for (int frame = 0; frame < 20; frame++) {
            ui.animateCursors(game, 16);
        }
        assertFalse("a settled cursor must stop asking for frames",
                ui.cursorsSettling(game));
    }

    /** One player's glide is nobody else's business. */
    @Test
    public void eachPlayerGlidesOnTheirOwn() {
        game.cursorX[0] = 1;
        ui.animateCursors(game, 16);
        assertEquals(game.cursorX[1], ui.cursorDrawX[1], 0f);
        assertTrue(ui.cursorDrawX[0] > 0f && ui.cursorDrawX[0] < 1f);
    }

    // ---- The gap the caller hands over --------------------------------------------------

    /**
     * {@link UiState#animateCursors} eases in real milliseconds, so it believes whatever the
     * clock says; its Javadoc puts the clamp on the caller. That makes
     * {@link CozyGameView#frameGap} the other half of the same contract, and the two ends it
     * has to hold are the two the exponential cannot survive on its own.
     *
     * <p>Above: a view resumed after a minute away reports 60,000 ms, and any tau closes any
     * gap completely long before that — both cursors would simply appear where they were
     * going. Below: {@code SystemClock.uptimeMillis} counts whole milliseconds and the input
     * path invalidates on every key, so two draws inside one tick report 0 ms, and a glide
     * advanced by nothing does not advance at all while a player holds a direction.
     */
    @Test
    public void theFrameGapIsClampedAtBothEnds() {
        assertEquals("a minute asleep is not a frame",
                CozyGameView.MAX_FRAME_MS, CozyGameView.frameGap(1_000, 61_000));
        assertEquals("two draws inside one tick still have to move",
                CozyGameView.MIN_FRAME_MS, CozyGameView.frameGap(1_000, 1_000));
        assertEquals("and an ordinary frame passes through untouched",
                17L, CozyGameView.frameGap(1_000, 1_017));
    }

    /** Before there is a previous frame there is no gap to measure, only a fair guess. */
    @Test
    public void theFirstFrameOfASessionIsChargedOneNominalFrame() {
        assertEquals(CozyGameView.NOMINAL_FRAME_MS, CozyGameView.frameGap(0, 900_000));
    }

    /**
     * The clamped worst case still has to look like a glide rather than a cut. 64 ms is four
     * 60 Hz frames and closes 78% of the gap — a visible stutter, which is honest, but the
     * cursor is demonstrably still travelling rather than already arrived.
     */
    @Test
    public void theWorstClampedFrameStillGlides() {
        float closed = Draw.approachRate(CozyGameView.MAX_FRAME_MS, Theme.MOTION_TAU_MS);
        assertTrue("a clamped frame must not be a teleport", closed < .85f);
        assertTrue("nor a stall", closed > .5f);
    }

    // ---- Heading and landing -----------------------------------------------------------

    /**
     * The heading is read off the gap the glide is closing, so it is right for a stick and
     * for a hint that jumps the cursor as well as for a D-pad — and it is <em>held</em>
     * after the cursor lands, which is what lets the margin tabs ease their lean out
     * instead of dropping it the frame the glide ends.
     */
    @Test
    public void headingSurvivesTheLanding() {
        game.cursorX[0] = 1;
        game.cursorY[0] = 0;
        ui.animateCursors(game, 16);
        assertEquals(1f, ui.cursorHeadingX[0], 0f);
        for (int frame = 0; frame < 20; frame++) {
            ui.animateCursors(game, 16);
        }
        assertEquals("a settled cursor still remembers which way it came",
                1f, ui.cursorHeadingX[0], 0f);

        game.cursorX[0] = 0;
        ui.animateCursors(game, 16);
        assertEquals(-1f, ui.cursorHeadingX[0], 0f);
    }

    /** Nothing has moved yet, so nothing may claim a direction. */
    @Test
    public void aStillCursorHasNoHeading() {
        ui.animateCursors(game, 16);
        assertEquals(0f, ui.cursorHeadingX[0], 0f);
        assertEquals(0f, ui.cursorHeadingY[0], 0f);
    }

    /**
     * The landing clock runs 0 to 1 over {@link Theme#CURSOR_LAND_MS} and then stays there.
     * A cursor that has never moved is <em>landed</em>, not perpetually arriving: the
     * opening frame of a game must not squash a cursor nobody has touched.
     */
    @Test
    public void landingRunsOnceAndStops() {
        assertEquals(1f, ui.landing(0, 10_000), 0f);
        ui.cursorMovedAt[0] = 10_000;
        assertEquals(0f, ui.landing(0, 10_000), 0f);
        assertEquals(.5f, ui.landing(0, 10_000 + (long) (Theme.CURSOR_LAND_MS / 2)), .01f);
        assertEquals(1f, ui.landing(0, 10_000 + (long) Theme.CURSOR_LAND_MS), 0f);
        assertEquals(1f, ui.landing(0, 60_000), 0f);
    }

    // ---- When the view may stop --------------------------------------------------------

    /**
     * {@code Renderer.animating} ends its game branch with {@code !calmMotion}, so the whole
     * canvas is repainted sixty times a second for ever. {@link CursorRenderer#needsFrames}
     * is what lets it stop, and the only way it can be wrong is by under-reporting — a cue
     * that is drawn but not driven freezes mid-phase, which reads as a fault. So every clock
     * the cursors run on is checked here, and the quiet case is checked last.
     */
    @Test
    public void framesAreAskedForWhileACursorIsLanding() {
        long now = 100_000;
        ui.lastActive[0] = now - Theme.IDLE_HUSH_START_MS - 5_000;
        ui.lastActive[1] = now - Theme.IDLE_HUSH_START_MS - 5_000;
        ui.cursorMovedAt[0] = now - 10;
        assertTrue(CursorRenderer.needsFrames(ui, now));
    }

    @Test
    public void framesAreAskedForWhileAPlayerIsStillSettlingDown() {
        long now = 100_000;
        ui.lastActive[0] = now - 1_000;
        ui.lastActive[1] = 1;
        // Far apart, so no heart is beating and only the fade can be asking.
        ui.cursorDrawX[1] = 8;
        ui.cursorDrawY[1] = 8;
        assertTrue(CursorRenderer.needsFrames(ui, now));
    }

    @Test
    public void framesAreAskedForWhileAHeartIsBeating() {
        long now = 500_000;
        quiet(now);
        ui.cursorDrawX[1] = ui.cursorDrawX[0] + 1;
        ui.cursorDrawY[1] = ui.cursorDrawY[0];
        assertTrue(CursorRenderer.needsFrames(ui, now));
    }

    /** Two people who have wandered off, on opposite corners: the television may rest. */
    @Test
    public void aQuietTableAsksForNothing() {
        long now = 500_000;
        quiet(now);
        assertFalse(CursorRenderer.needsFrames(ui, now));
    }

    /**
     * A landing under calm motion has nothing to draw — the squash is pinned to 1 and the
     * tabs' lean to 0 — so it must not keep the television awake. The glide underneath it
     * is still covered, by {@link UiState#cursorsSettling}.
     */
    @Test
    public void calmMotionHasNoLandingToDraw() {
        long now = 500_000;
        quiet(now);
        ui.cursorMovedAt[0] = now - 10;
        assertTrue(CursorRenderer.needsFrames(ui, now));
        Comfort.get().calmMotion = true;
        assertFalse(CursorRenderer.needsFrames(ui, now));
    }

    /** Calm motion stills the hearts too, so even a shared square stops asking. */
    @Test
    public void calmMotionStillsTheHearts() {
        long now = 500_000;
        quiet(now);
        ui.cursorDrawX[1] = ui.cursorDrawX[0];
        ui.cursorDrawY[1] = ui.cursorDrawY[0];
        assertTrue(CursorRenderer.needsFrames(ui, now));
        Comfort.get().calmMotion = true;
        assertFalse(CursorRenderer.needsFrames(ui, now));
    }

    /** Everybody long since finished moving, and the two cursors far apart. */
    private void quiet(long now) {
        ui.lastActive[0] = now - Theme.IDLE_HUSH_START_MS - 10_000;
        ui.lastActive[1] = now - Theme.IDLE_HUSH_START_MS - 10_000;
        ui.cursorMovedAt[0] = now - 10_000;
        ui.cursorMovedAt[1] = now - 10_000;
        ui.cursorDrawX[0] = 0;
        ui.cursorDrawY[0] = 0;
        ui.cursorDrawX[1] = 9;
        ui.cursorDrawY[1] = 9;
    }

    /** How far a cursor gets from 0 toward 1 in {@code steps} steps of {@code dt} ms. */
    private float travel(int steps, long dt) {
        game.cursorX[0] = 1;
        game.cursorY[0] = 0;
        ui.cursorDrawX[0] = 0;
        ui.cursorDrawY[0] = 0;
        for (int step = 0; step < steps; step++) {
            ui.animateCursors(game, dt);
        }
        return ui.cursorDrawX[0];
    }
}

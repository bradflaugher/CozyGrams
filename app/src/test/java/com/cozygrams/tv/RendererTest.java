package com.cozygrams.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * When the view is allowed to stop repainting.
 *
 * <p>{@code Renderer.animating} touches no {@code Canvas}, so it is a plain unit test —
 * which matters, because it is the one method in the renderer whose failures are invisible
 * in a screenshot. Getting it wrong in one direction burns a television's power all
 * evening on a frame that never changes; getting it wrong in the other freezes a live
 * animation part way through, which reads as a crash rather than as calm.
 *
 * <p>The win card is the case that was wrong in <em>both</em> directions. It shared a
 * branch with the menus — {@code !calmMotion || effects.busy(now)} — so with Calmer
 * Animation on it stopped asking for frames as soon as the last board pulse died at about
 * 760 ms, freezing the celebration mid-entrance with no credit line and no "Press A"
 * button; and with calm off it answered true for ever, holding a static 576 px near-white
 * card at 60 fps on a panel that may be OLED. {@code WinScene.STILL_AT_MS} had existed for
 * exactly this and was read by nothing but its own test.
 */
public class RendererTest {

    /** Comfortably past every beat, and past the invitation's three counted breaths. */
    /**
     * Long enough that everything on the win card has finished, drift included. It was
     * 20 s, which was past the last beat but is now inside the ambient drift: the card keeps
     * a few motes turning until the room hushes, so "well after" has to be past
     * {@link Theme#IDLE_HUSH_FULL_MS} rather than past the choreography.
     */
    private static final long WELL_AFTER = Theme.IDLE_HUSH_FULL_MS + 20_000;

    private final Renderer renderer = new Renderer();
    private final Effects effects = new Effects();
    private final GameState game = new GameState(7L, 10);

    @After
    public void restoreComfort() {
        Comfort.get().restoreDefaults();
    }

    private UiState won(long at) {
        UiState ui = new UiState();
        ui.screen = UiState.GAME;
        ui.won = true;
        ui.winAt = at;
        return ui;
    }

    private boolean animatingAt(UiState ui, long now) {
        return renderer.animating(game, ui, effects, now);
    }

    // ---- The win card ------------------------------------------------------------------

    /**
     * Every beat of the celebration gets a frame, in both comfort modes. Sampled every
     * 16 ms rather than at the beat boundaries, because the fault this replaces was a gap
     * in the middle of an entrance rather than a missing beat.
     */
    @Test
    public void theWholeCelebrationKeepsAskingForFrames() {
        for (boolean calm : new boolean[]{false, true}) {
            Comfort.get().calmMotion = calm;
            UiState ui = won(100_000);
            for (long elapsed = 0; elapsed <= WinScene.ARRIVED_MS; elapsed += 16) {
                assertTrue("calm=" + calm + " stopped repainting at " + elapsed
                                + "ms, while the card was still arriving",
                        animatingAt(ui, ui.winAt + elapsed));
            }
        }
    }

    /**
     * And it stops. A win card left on screen while the pair make tea must not keep a panel
     * awake — the whole point of {@code WinScene}'s counted breaths.
     *
     * <p>Where it stops moved, and deliberately. The beats are over at
     * {@link WinScene#STILL_AT_MS} and the card used to go rigid there, which is why the last
     * second of the celebration was three pixel-identical frames; a few motes now drift on
     * until {@link Theme#IDLE_HUSH_FULL_MS}, the same clock the music fades on and the point
     * the whole game agrees somebody has walked away. So the promise this test protects is
     * unchanged — the card sleeps, and nothing repaints for ever — and it is measured against
     * {@link WinScene#ambientUntilMs()}, which is the honest name for "when it is done".
     */
    @Test
    public void theWinCardEventuallySleeps() {
        Comfort.get().calmMotion = false;
        UiState ui = won(100_000);
        assertTrue("the beats were cut short",
                animatingAt(ui, ui.winAt + WinScene.STILL_AT_MS - 1));
        assertTrue("the drift was cut short",
                animatingAt(ui, ui.winAt + WinScene.ambientUntilMs() - 1));
        assertTrue("the drift should outlast the choreography",
                WinScene.ambientUntilMs() > WinScene.STILL_AT_MS);
        assertFalse("the win card repaints for ever with full motion on",
                animatingAt(ui, ui.winAt + WinScene.ambientUntilMs() + 1));
        assertFalse(animatingAt(ui, ui.winAt + WELL_AFTER));
    }

    /**
     * Calmed motion sleeps sooner, because there is no breath left to drive — but not
     * before the last beat has landed. This is the direction that used to freeze.
     */
    @Test
    public void calmMotionSleepsSoonerButNotEarly() {
        Comfort.get().calmMotion = true;
        UiState ui = won(100_000);
        assertTrue("calm froze the card before it finished arriving",
                animatingAt(ui, ui.winAt + (long) WinScene.ARRIVED_MS));
        assertFalse(animatingAt(ui, ui.winAt + WinScene.stillAtMs() + 1));
        assertTrue("calm waits as long as full motion",
                WinScene.stillAtMs() < WinScene.STILL_AT_MS);
    }

    /**
     * A celebration whose particles outlive the beats still gets frames. The drift is
     * emitted by the view rather than by the scene, so the two clocks are independent and
     * whichever runs longer has to win.
     */
    @Test
    public void liveConfettiKeepsTheWinCardAwake() {
        Comfort.get().calmMotion = false;
        UiState ui = won(100_000);
        long late = ui.winAt + WELL_AFTER;
        assertFalse(animatingAt(ui, late));
        effects.rise(960, 1000, 4, 300, Theme.PINK, Effects.SHAPE_HEART, late, 900);
        assertTrue("a live celebration was left to freeze", animatingAt(ui, late + 100));
        assertFalse(animatingAt(ui, late + 2_000));
    }

    // ---- Everything else -----------------------------------------------------------------

    /** The menus keep their idle pulse, and lose it when the player asks for calm. */
    @Test
    public void theMenusKeepTheirOwnRule() {
        UiState ui = new UiState();
        ui.screen = UiState.HOME;
        Comfort.get().calmMotion = false;
        assertTrue(animatingAt(ui, 100_000));
        Comfort.get().calmMotion = true;
        assertFalse(animatingAt(ui, 100_000));
        effects.rise(960, 1000, 4, 300, Theme.PINK, Effects.SHAPE_HEART, 100_000, 900);
        assertTrue("a live effect on a menu was left to freeze",
                animatingAt(ui, 100_100));
    }

    /**
     * A quiet board with calm on is the one state in the game that is genuinely still, and
     * it has to be allowed to be: no cursor is gliding, no message is up and nothing is
     * breathing.
     */
    @Test
    public void aStillBoardWithCalmOnStops() {
        Comfort.get().calmMotion = true;
        UiState ui = new UiState();
        ui.screen = UiState.GAME;
        ui.toast = "";
        ui.snapCursors(game);
        assertFalse(animatingAt(ui, 100_000));
        Comfort.get().calmMotion = false;
        assertTrue("the cursors breathe and nothing is driving them",
                animatingAt(ui, 100_000));
    }
}

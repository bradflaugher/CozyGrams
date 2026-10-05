package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** One-time tips: once each, one at a time, never in the way. */
public class TipsTest {

    private static final long T = 100_000;

    @Test
    public void eachTipShowsOnceAndInTurn() {
        Tips tips = new Tips();
        UiState ui = new UiState();
        ui.screen = UiState.HOME;
        assertEquals(Tips.STORY, tips.next(ui, false));
        tips.show(Tips.STORY, T);
        assertEquals(Tips.CORNER, tips.next(ui, false));
        tips.show(Tips.CORNER, T);
        assertEquals(-1, tips.next(ui, false));
        ui.screen = UiState.GAME;
        assertEquals(Tips.CLUES, tips.next(ui, false));
    }

    @Test
    public void tipsBelongToTheirScreenAndHands() {
        UiState ui = new UiState();
        ui.screen = UiState.GAME;
        assertTrue(Tips.applies(Tips.PEN, ui, true));
        assertFalse(Tips.applies(Tips.PEN, ui, false));
        assertTrue(Tips.applies(Tips.LEGEND, ui, false));
        ui.won = true;
        assertFalse(Tips.applies(Tips.CLUES, ui, false));
        ui.won = false;
        ui.joined[1] = true;
        assertFalse(Tips.applies(Tips.JOIN, ui, false));
    }

    /** A press just after a tip appears was already on its way, so it does not count. */
    @Test
    public void anyPressPutsATipAwayAfterAMoment() {
        Tips tips = new Tips();
        tips.show(Tips.CLUES, T);
        assertFalse(tips.dismiss(T + 100));
        assertEquals(Tips.CLUES, tips.showing);
        assertTrue(tips.dismiss(T + Tips.GRACE_MS));
        assertEquals(-1, tips.showing);
        assertFalse(tips.ready(T + Tips.GRACE_MS + 10, 0));
        assertTrue(tips.ready(T + Tips.GRACE_MS + Tips.GAP_MS, 0));
    }

    @Test
    public void anUnansweredTipFadesOnItsOwn() {
        Tips tips = new Tips();
        UiState ui = new UiState();
        ui.screen = UiState.GAME;
        tips.show(Tips.CLUES, T);
        tips.tick(T + Tips.SHOW_MS - 1, ui, false);
        assertEquals(Tips.CLUES, tips.showing);
        tips.tick(T + Tips.SHOW_MS, ui, false);
        assertEquals(-1, tips.showing);
        assertEquals(Tips.ALL, Tips.ALL & ((1 << Tips.COUNT) - 1));
    }

    @Test
    public void everyTipHasWords() {
        for (int tip = 0; tip < Tips.COUNT; tip++) {
            for (int hands = 0; hands < 5; hands++) {
                assertFalse(Tips.text(tip, hands).isEmpty());
            }
        }
    }

    // ---- Where the bubble goes --------------------------------------------------------

    private static final float[] SAFE = {96, 54, 1824, 1026};
    /** The title screen's Story Book row on the right-hand card, at 1080p. */
    private static final float[] STORY_ROW = {1025, 338, 1728, 502};

    private static boolean overlaps(float[] spot, float w, float h, float[] rect) {
        return spot[0] < rect[2] && spot[0] + w > rect[0]
                && spot[1] < rect[3] && spot[1] + h > rect[1];
    }

    /** A row on the right is pointed at from its left, beside it, when that is clear. */
    @Test
    public void aRailRowIsPointedAtFromItsLeft() {
        float[] spot = Tips.place(420, 120, 20, 32, 10, STORY_ROW, 1920, SAFE,
                new float[0], new int[0], 0, 0);
        assertNotNull(spot);
        assertEquals(Tips.LEFT, (int) spot[2]);
        assertEquals(1025 - 20 - 420, spot[0], .01f);
    }

    /**
     * The welcome line sits where a centred bubble would; the bubble slides down the
     * row, keeping its pointer on it, until it is clear of the words.
     */
    @Test
    public void theBubbleSlidesOffTheWordsBesideIt() {
        float[] welcome = {225, 360, 865, 410};
        float[] spot = Tips.place(420, 120, 20, 32, 10, STORY_ROW, 1920, SAFE,
                welcome, new int[]{0}, 0, 1);
        assertNotNull(spot);
        assertEquals(Tips.LEFT, (int) spot[2]);
        assertFalse(overlaps(spot, 420, 120, welcome));
        // The pointer, the row's middle held inside the bubble's straight edge, still lands
        // on the row.
        float pointer = Math.max(spot[1] + 32, Math.min(spot[1] + 120 - 32, 420));
        assertTrue(pointer >= STORY_ROW[1] && pointer <= STORY_ROW[3]);
    }

    /** Words that may step aside are covered only when asked; the rest never are. */
    @Test
    public void onlyWordsThatMayStepAsideAreEverCovered() {
        // Everything beside the row and around it is text.
        float[] clear = {
                96, 54, 1010, 1026,     // the whole left card's words, which must stay
                1025, 54, 1728, 330,    // the heading above the row, which may yield
                1025, 510, 1728, 1026,  // every row below
        };
        int[] groups = {0, Tips.YIELD_HEADER, 0};
        assertNull(Tips.place(420, 120, 20, 32, 10, STORY_ROW, 1920, SAFE, clear, groups,
                0, 3));
        float[] spot = Tips.place(420, 120, 20, 32, 10, STORY_ROW, 1920, SAFE, clear, groups,
                Tips.YIELD_HEADER, 3);
        assertNotNull(spot);
        assertEquals(Tips.ABOVE, (int) spot[2]);
        assertEquals(Tips.YIELD_HEADER, Tips.overlapped(spot[0], spot[1], spot[0] + 420,
                spot[1] + 120, 10, clear, groups, 3));
    }

    /** Words that stepped aside fade with the bubble and come back with it gone. */
    @Test
    public void yieldedWordsFadeWithTheBubble() {
        Tips tips = new Tips();
        tips.show(Tips.CORNER, T);
        tips.yielding = Tips.YIELD_DETAIL;
        assertEquals(1f, tips.wordsAlpha(Tips.YIELD_HEADER, T + 1000), 0f);
        assertEquals(0f, tips.wordsAlpha(Tips.YIELD_DETAIL, T + 1000), 0f);
        tips.hide(T + 2000);
        assertEquals(0, tips.yielding);
        assertEquals(1f, tips.wordsAlpha(Tips.YIELD_DETAIL, T + 2000), 0f);
    }
}

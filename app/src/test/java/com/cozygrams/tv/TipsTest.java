package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
}

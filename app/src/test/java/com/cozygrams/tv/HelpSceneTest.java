package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * How to play: every page has something to say, says it aloud as
 * well as on screen, and names the buttons that are actually in the room.
 */
public class HelpSceneTest {

    @Before
    @After
    public void tidyUp() {
        HudScene.setTouch(false);
        HudScene.setRemoteOnly(false);
    }

    @Test
    public void everyPageHasATabATitleAndEntries() {
        assertEquals(HelpScene.PAGE_COUNT, HelpScene.TABS.length);
        for (int page = 0; page < HelpScene.PAGE_COUNT; page++) {
            assertFalse(HelpScene.title(page).isEmpty());
            String[][] entries = HelpScene.entries(page);
            assertTrue(entries.length >= 3);
            for (String[] entry : entries) {
                assertEquals(2, entry.length);
                assertFalse(entry[0].isEmpty());
                assertFalse(entry[1].isEmpty());
            }
        }
    }

    @Test
    public void pagesWrapRatherThanRunningOut() {
        assertEquals(HelpScene.title(0), HelpScene.title(HelpScene.PAGE_COUNT));
        assertEquals(HelpScene.title(HelpScene.PAGE_COUNT - 1), HelpScene.title(-1));
    }

    /** A screen reader hears the whole page, not just its heading. */
    @Test
    public void aPageIsSpokenInFull() {
        for (int page = 0; page < HelpScene.PAGE_COUNT; page++) {
            String spoken = HelpScene.spoken(page);
            assertTrue(spoken.contains(HelpScene.title(page)));
            for (String[] entry : HelpScene.entries(page)) {
                assertTrue(spoken.contains(entry[1]));
            }
        }
    }

    /** The controls page names whatever is in the player's hands. */
    @Test
    public void controlsFollowWhateverIsInTheRoom() {
        String pad = HelpScene.entries(HelpScene.PAGE_CONTROLS)[1][1];
        HudScene.setRemoteOnly(true);
        String remote = HelpScene.entries(HelpScene.PAGE_CONTROLS)[1][1];
        HudScene.setRemoteOnly(false);
        HudScene.setTouch(true);
        String touch = HelpScene.entries(HelpScene.PAGE_CONTROLS)[0][1];
        assertNotEquals(pad, remote);
        assertNotEquals(pad, touch);
    }
}

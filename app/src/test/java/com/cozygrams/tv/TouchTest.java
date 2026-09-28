package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * The phone's side of the game: what the screens say once a finger is in charge, and that
 * nothing answers a tap before it has actually been drawn.
 */
public class TouchTest {

    @After
    public void putTheRoomBack() {
        HudScene.forgetTheRoom();
    }

    @Test
    public void aTelevisionNeverSeesTouchWording() {
        assertFalse(HudScene.touch());
        assertEquals("press any button", HudScene.inviteLine());
        assertFalse(HomeScene.confirmName().equals("TAP"));
        assertFalse(HudScene.backName().equals("BACK"));
    }

    @Test
    public void aPhoneNamesWhatAFingerDoes() {
        HudScene.setTouch(true);
        assertEquals("TAP", HomeScene.confirmName());
        assertEquals("BACK", HudScene.backName());
        assertTrue(HudScene.inviteLine().contains("controller"));
        for (int row = 0; row < HomeScene.ITEM_COUNT; row++) {
            String footer = HomeScene.touchFooter(row);
            assertTrue(footer, footer.startsWith("Tap"));
            assertFalse("no gamepad keys on a phone: " + footer,
                    footer.contains("D-pad") || footer.contains(" A "));
        }
    }

    @Test
    public void nothingIsTappableBeforeItIsDrawn() {
        HudScene.setTouch(true);
        assertEquals(-1, HudScene.touchButtonAt(10, 10));
        assertEquals(-1, new SettingsScene(new Draw()).itemAt(10, 10));
        assertEquals(-1, new HomeScene(new Draw()).itemAt(10, 10));
        assertEquals(0, new HomeScene(new Draw()).stepAt(HomeScene.ITEM_SIZE, 10));
    }

    @Test
    public void theTouchButtonsAreFourDistinctActions() {
        int[] buttons = {HudScene.TOUCH_FILL, HudScene.TOUCH_CROSS, HudScene.TOUCH_HINT,
                HudScene.TOUCH_MENU};
        for (int i = 0; i < buttons.length; i++) {
            assertEquals(i, buttons[i]);
        }
        assertTrue(HudScene.touchPadHeight() > 0);
    }
}

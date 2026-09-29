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
        assertEquals("the win card names the HOME button on the screen",
                "HOME", HudScene.backName());
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
        assertFalse(HudScene.backButtonAt(10, 10));
        assertEquals(-1, new SettingsScene(new Draw()).itemAt(10, 10));
        assertEquals(-1, new HomeScene(new Draw()).itemAt(10, 10));
        assertEquals(0, new HomeScene(new Draw()).stepAt(HomeScene.ITEM_SIZE, 10));
    }

    /**
     * People on phones look for a way back on the screen, not a gesture. Every screen a
     * finger can get stuck on draws one; the title screen, where back means leaving the
     * game, is left to the phone.
     */
    @Test
    public void everyScreenButTheTitleHasAWayBackOnScreen() {
        UiState ui = new UiState();
        ui.screen = UiState.HOME;
        assertEquals(null, Renderer.backLabel(ui));
        ui.screen = UiState.GAME;
        assertEquals("HOME", Renderer.backLabel(ui));
        ui.won = true;
        assertEquals("the win card too", "HOME", Renderer.backLabel(ui));
        ui.screen = UiState.SETTINGS;
        assertEquals("BACK", Renderer.backLabel(ui));
    }

    /**
     * The pen's halves share an edge. A tap just inside CROSS used to be answered by FILL,
     * because the slop was applied before anything had been found under the finger.
     */
    @Test
    public void aTapJustInsideCrossIsCross() throws Exception {
        HudScene.setTouch(true);
        java.lang.reflect.Field rects = HudScene.class.getDeclaredField("touchRects");
        rects.setAccessible(true);
        float[][] r = (float[][]) rects.get(null);
        r[HudScene.TOUCH_FILL] = new float[]{0, 0, 100, 50};
        r[HudScene.TOUCH_CROSS] = new float[]{100, 0, 200, 50};
        java.lang.reflect.Field drawn = HudScene.class.getDeclaredField("touchRectsDrawn");
        drawn.setAccessible(true);
        drawn.setBoolean(null, true);
        assertEquals(HudScene.TOUCH_CROSS, HudScene.touchButtonAt(101, 25));
        assertEquals(HudScene.TOUCH_FILL, HudScene.touchButtonAt(99, 25));
    }

    @Test
    public void theTouchButtonsAreDistinctActions() {
        int[] buttons = {HudScene.TOUCH_FILL, HudScene.TOUCH_CROSS, HudScene.TOUCH_HINT,
                HudScene.TOUCH_MENU, HudScene.TOUCH_MARK, HudScene.TOUCH_BACK};
        for (int i = 0; i < buttons.length; i++) {
            assertEquals(i, buttons[i]);
        }
        assertTrue(HudScene.touchPadHeight() > 0);
    }
}

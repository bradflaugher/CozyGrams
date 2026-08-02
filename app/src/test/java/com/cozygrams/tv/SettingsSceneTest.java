package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The cozy corner's contract: the rows line up with what they claim to do, every row can
 * be reached and changed with nothing but a D-pad, and the whole list still fits on a
 * 1080p panel at the largest text size.
 */
public class SettingsSceneTest {

    /**
     * The tightest case we ever draw: the largest text setting, which asks Theme to
     * behave as though the screen were 16% taller than it is.
     */
    private static final float BIG_TEXT_SCALE = 1.16f;

    @Test
    public void everyRowHasALabelAndAnExplanation() {
        assertEquals(SettingsScene.ITEM_COUNT, SettingsScene.labels().length);
        assertEquals(SettingsScene.ITEM_COUNT, SettingsScene.descriptions().length);
        assertEquals(SettingsScene.ITEM_COUNT, SettingsScene.states(new UiState()).length);
        for (String label : SettingsScene.labels()) {
            assertFalse(label.trim().isEmpty());
            assertEquals(label, label.toUpperCase(java.util.Locale.ROOT));
        }
        for (String description : SettingsScene.descriptions()) {
            assertFalse(description.trim().isEmpty());
        }
    }

    /**
     * Long strings are what break a 10-foot layout. The label shares its pill with a
     * switch, and the explanation gets one line across the panel, so both have a budget.
     */
    @Test
    public void nothingIsTooLongToFitItsPlace() {
        for (String label : SettingsScene.labels()) {
            assertTrue(label + " is too long for a row", label.length() <= 22);
        }
        for (String description : SettingsScene.descriptions()) {
            assertTrue(description + " is too long for one line",
                    description.length() <= 46);
        }
    }

    @Test
    public void theRowsFitInsideTheirBandWithoutOverlapping() {
        for (float height : new float[]{720, 1080, 2160}) {
            for (float textScale : new float[]{1f, BIG_TEXT_SCALE}) {
                Theme.setScreenHeight(height * textScale);
                float top = height * SettingsScene.ROWS_TOP;
                float bottom = height * SettingsScene.ROWS_BOTTOM;
                float[] centres = SettingsScene.rowCentres(top, bottom);
                float half = SettingsScene.rowHalfHeight(top, bottom);

                assertEquals(SettingsScene.ITEM_COUNT, centres.length);
                assertTrue("first row escapes the panel", centres[0] - half >= top);
                assertTrue("last row escapes the panel",
                        centres[centres.length - 1] + half <= bottom);
                for (int i = 1; i < centres.length; i++) {
                    assertTrue("rows " + (i - 1) + " and " + i + " overlap",
                            centres[i] - half > centres[i - 1] + half);
                }
                // A row still has to be tall enough to hold its own label.
                assertTrue("a row is shorter than its text at height " + height,
                        half * 2 > Theme.textSize(17) * .8f);
            }
        }
    }

    @Test
    public void groupsAreSeparatedByMoreAirThanRowsWithinAGroup() {
        Theme.setScreenHeight(1080);
        float[] centres = SettingsScene.rowCentres(0, 1000);
        float withinAGroup = centres[SettingsScene.ITEM_BIG_TEXT]
                - centres[SettingsScene.ITEM_CONTRAST];
        float acrossAGroup = centres[SettingsScene.ITEM_GENTLE]
                - centres[SettingsScene.ITEM_SFX];
        assertTrue(acrossAGroup > Math.abs(withinAGroup));
    }

    // ---- Reachable and changeable with a bare remote ---------------------------------

    @Test
    public void everyRowIsHandledByTheCentreButton() {
        UiState ui = new UiState();
        for (int item = 0; item < SettingsScene.ITEM_BACK; item++) {
            assertTrue("row " + item + " does nothing when chosen",
                    SettingsScene.toggle(ui, item));
        }
        assertFalse("the back row must close the corner, not toggle",
                SettingsScene.toggle(ui, SettingsScene.ITEM_BACK));
    }

    @Test
    public void aRowIndexFromNowhereIsTreatedAsLeaving() {
        UiState ui = new UiState();
        assertFalse(SettingsScene.toggle(ui, -1));
        assertFalse(SettingsScene.toggle(ui, SettingsScene.ITEM_COUNT));
        assertFalse(SettingsScene.toggle(ui, 9999));
    }

    @Test
    public void everySwitchRowFlipsTheStateItDraws() {
        UiState ui = new UiState();
        SaveStore.restoreDefaults(ui);
        for (int item = 0; item <= SettingsScene.ITEM_CALM_MOTION; item++) {
            boolean before = SettingsScene.states(ui)[item];
            SettingsScene.toggle(ui, item);
            assertEquals("row " + item + " does not show its own state",
                    !before, SettingsScene.states(ui)[item]);
            SettingsScene.toggle(ui, item);
            assertEquals(before, SettingsScene.states(ui)[item]);
        }
    }

    @Test
    public void switchesAndActionsAreToldApart() {
        for (int item = 0; item <= SettingsScene.ITEM_CALM_MOTION; item++) {
            assertTrue(SettingsScene.hasSwitch(item));
        }
        assertFalse(SettingsScene.hasSwitch(SettingsScene.ITEM_DEFAULTS));
        assertFalse(SettingsScene.hasSwitch(SettingsScene.ITEM_BACK));
        assertFalse(SettingsScene.hasSwitch(-1));
    }

    @Test
    public void puttingEverythingBackReachesBothHalvesOfTheOptions() {
        UiState ui = new UiState();
        ui.bigTextOn = true;
        ui.musicOn = false;
        Comfort.get().calmMotion = true;
        Comfort.get().distinctPlayers = true;

        SettingsScene.toggle(ui, SettingsScene.ITEM_DEFAULTS);

        assertFalse(ui.bigTextOn);
        assertTrue(ui.musicOn);
        assertFalse(Comfort.get().calmMotion);
        assertFalse(Comfort.get().distinctPlayers);
    }

    // ---- Player identity -------------------------------------------------------------

    @Test
    public void skyOnlyChangesColourWhenAskedTo() {
        Comfort.get().restoreDefaults();
        int everyday = Comfort.skyColor();
        Comfort.get().distinctPlayers = true;
        assertEquals(Comfort.SKY_ALT, Comfort.skyColor());
        assertEquals(Comfort.SKY_ALT_DARK, Comfort.skyColorDark());
        assertEquals(Comfort.SKY_ALT_LIGHT, Comfort.skyColorLight());
        assertTrue("the alternate identity has to actually be different",
                everyday != Comfort.skyColor());
        Comfort.get().restoreDefaults();
    }

    /**
     * The point of the alternate identity is not a different hue — Rose's pink and Sky's
     * everyday blue already differ in hue. It is a different <em>lightness</em>, so the
     * two players stay apart when hue perception is reduced, or on a badly set up TV.
     * Rose's pink is Theme.PINK, spelled out here because Theme's colours are stubs on a
     * plain JVM.
     */
    @Test
    public void skysAlternateIdentityIsLighterOrDarkerThanRoseNotJustADifferentHue() {
        double rose = luminance(255, 122, 158);
        double everydaySky = luminance(112, 202, 224);
        double alternateSky = luminance(
                (Comfort.SKY_ALT >> 16) & 0xff,
                (Comfort.SKY_ALT >> 8) & 0xff,
                Comfort.SKY_ALT & 0xff);

        assertTrue("the shipped pair are nearly the same lightness, which is the problem",
                contrast(rose, everydaySky) < 1.5);
        assertTrue("the alternate has to be clearly darker than Rose",
                contrast(rose, alternateSky) >= 3.0);
    }

    private static double contrast(double first, double second) {
        double lighter = Math.max(first, second);
        double darker = Math.min(first, second);
        return (lighter + .05) / (darker + .05);
    }

    /** Relative luminance, as defined by WCAG. */
    private static double luminance(int red, int green, int blue) {
        return .2126 * channel(red) + .7152 * channel(green) + .0722 * channel(blue);
    }

    private static double channel(int value) {
        double part = value / 255.0;
        return part <= .03928 ? part / 12.92 : Math.pow((part + .055) / 1.055, 2.4);
    }
}

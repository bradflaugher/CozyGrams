package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The cozy corner's contract: the rows line up with what they claim to do, every row can
 * be reached and changed with nothing but a D-pad, the one row that cannot be undone asks
 * first, and the whole list still fits on a 1080p panel at the largest text size.
 */
public class SettingsSceneTest {

    /**
     * The tightest case we ever draw: the largest text setting, which asks Theme to
     * behave as though the screen were 16% taller than it is.
     */
    private static final float BIG_TEXT_SCALE = Theme.TEXT_SCALE_BIG;

    /** The screen keeps its armed row and its note in statics, so put them back. */
    @Before
    @After
    public void tidyUp() {
        SettingsScene.forgetTheRoom();
        Comfort.get().restoreDefaults();
        Theme.setTextScale(1f);
    }

    @Test
    public void everyRowHasALabelAndAnExplanation() {
        assertEquals(SettingsScene.ITEM_COUNT, SettingsScene.labels().length);
        assertEquals(SettingsScene.ITEM_COUNT, SettingsScene.descriptions().length);
        assertEquals(SettingsScene.ITEM_COUNT, SettingsScene.states(new UiState()).length);
        for (String label : SettingsScene.labels()) {
            assertFalse(label.trim().isEmpty());
            assertTrue("labels use calm sentence case: " + label,
                    Character.isUpperCase(label.charAt(0)));
            assertFalse("labels should not shout: " + label,
                    label.length() > 1
                            && label.equals(label.toUpperCase(java.util.Locale.ROOT)));
        }
        for (String description : SettingsScene.descriptions()) {
            assertFalse(description.trim().isEmpty());
        }
    }

    /**
     * Long strings are what break a 10-foot layout. The label shares its pill with a
     * switch, the printed state and — on one row — two identity chips, and the explanation
     * gets one line across the panel.
     *
     * <p>Character counts are a proxy, and they are the only one available here:
     * {@code Paint.measureText} returns zero under {@code returnDefaultValues}, so
     * {@code fit()} would report every string as fitting. The real numbers come from the
     * preview harness at 1920x1080 with LARGER TEXT on, where the label lane is 560.6 px
     * for a plain switch row and 468.0 px for the identity row, which also carries two
     * colour chips. The longest of each measured 528.8 px ("GENTLE MISTAKE CHECK", 20
     * characters) and 389.6 px ("TELL THEM APART", 15). A label can never shrink to fit,
     * because at LARGER TEXT {@code textSize(17)} is already sitting on the prose floor —
     * so these caps are the whole guard, and the render is what checks them.
     */
    @Test
    public void nothingIsTooLongToFitItsPlace() {
        for (int item = 0; item < SettingsScene.ITEM_COUNT; item++) {
            String label = SettingsScene.labels()[item];
            int budget = item == SettingsScene.ITEM_DISTINCT_PLAYERS ? 16
                    : (SettingsScene.hasSwitch(item) ? 20 : 22);
            assertTrue(label + " is too long for its lane", label.length() <= budget);
        }
        for (String description : SettingsScene.descriptions()) {
            assertTrue(description + " is too long for one line",
                    description.length() <= 46);
        }
    }

    /** The armed label is drawn in the same lane as the resting one. */
    @Test
    public void theQuestionFitsWhereTheRowDoes() {
        SettingsScene.armDefaults(1000);
        assertTrue(SettingsScene.labels()[SettingsScene.ITEM_DEFAULTS].length() <= 22);
        assertTrue(SettingsScene.labels()[SettingsScene.ITEM_DEFAULTS].endsWith("?"));
    }

    @Test
    public void theRowsFitInsideTheirBandWithoutOverlapping() {
        for (float height : new float[]{720, 1080, 2160}) {
            for (float textScale : new float[]{1f, BIG_TEXT_SCALE}) {
                Theme.setScreenHeight(height);
                Theme.setTextScale(textScale);
                float top = height * SettingsScene.ROWS_TOP;
                float bottom = height * SettingsScene.ROWS_BOTTOM;
                float[] centres = SettingsScene.rowCentres(top, bottom);
                float half = SettingsScene.rowHalfHeight(top, bottom);

                assertEquals(SettingsScene.VISIBLE_ROWS, centres.length);
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
        Theme.setTextScale(1f);
    }

    @Test
    public void theSevenRowWindowFollowsFocusWithoutRunningPastTheList() {
        assertEquals(0, SettingsScene.windowStart(SettingsScene.ITEM_MUSIC));
        assertEquals(0, SettingsScene.windowStart(SettingsScene.ITEM_HINTS));
        assertEquals(2, SettingsScene.windowStart(SettingsScene.ITEM_CONTRAST));
        assertEquals(SettingsScene.ITEM_COUNT - SettingsScene.VISIBLE_ROWS,
                SettingsScene.windowStart(SettingsScene.ITEM_BACK));
    }

    @Test
    public void everySettingBelongsToAClearGroup() {
        assertEquals("SOUND", SettingsScene.sectionName(SettingsScene.ITEM_MUSIC));
        assertEquals("HELPING HANDS", SettingsScene.sectionName(SettingsScene.ITEM_GENTLE));
        assertEquals("COMFORT & ACCESS",
                SettingsScene.sectionName(SettingsScene.ITEM_BIG_TEXT));
        assertEquals("STORY & RESET", SettingsScene.sectionName(SettingsScene.ITEM_DEFAULTS));
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

    /**
     * The reset now takes two presses, not one. That is a deliberate change: the row wipes
     * all nine options with no undo and sits one row above BACK TO THE PUZZLE, which is
     * where every visit ends — a single overshoot downward from CALMER ANIMATION used to
     * be enough to lose the lot.
     */
    @Test
    public void puttingEverythingBackReachesBothHalvesOfTheOptions() {
        UiState ui = new UiState();
        ui.bigTextOn = true;
        ui.musicOn = false;
        Comfort.get().calmMotion = true;
        Comfort.get().distinctPlayers = true;

        SettingsScene.toggle(ui, SettingsScene.ITEM_DEFAULTS);
        assertTrue("the first press only asks", SettingsScene.defaultsArmed());
        assertTrue("nothing has been undone yet", ui.bigTextOn);

        SettingsScene.toggle(ui, SettingsScene.ITEM_DEFAULTS);

        assertFalse(SettingsScene.defaultsArmed());
        assertFalse(ui.bigTextOn);
        assertTrue(ui.musicOn);
        assertFalse(Comfort.get().calmMotion);
        assertFalse(Comfort.get().distinctPlayers);
    }

    @Test
    public void startingTheStoryAgainAsksFirst() {
        assertFalse(SettingsScene.storyRestartArmed());
        assertTrue(SettingsScene.toggle(new UiState(), SettingsScene.ITEM_START_STORY, 1000));
        assertTrue(SettingsScene.storyRestartArmed());
        assertFalse(SettingsScene.consumeStoryRestart());
        assertTrue(SettingsScene.toggle(new UiState(), SettingsScene.ITEM_START_STORY, 1200));
        assertFalse(SettingsScene.storyRestartArmed());
        assertTrue(SettingsScene.consumeStoryRestart());
        assertFalse("consuming it is a one-shot", SettingsScene.consumeStoryRestart());
    }

    @Test
    public void theQuestionLapsesRatherThanWaitingForEver() {
        SettingsScene.armDefaults(10_000);
        SettingsScene.expireDefaults(10_000 + SettingsScene.CONFIRM_WINDOW_MS);
        assertTrue("still armed on the last millisecond", SettingsScene.defaultsArmed());
        SettingsScene.expireDefaults(10_000 + SettingsScene.CONFIRM_WINDOW_MS + 1);
        assertFalse(SettingsScene.defaultsArmed());
    }

    /** Touching any other row is an answer of "no". */
    @Test
    public void changingSomethingElseTakesTheQuestionBack() {
        UiState ui = new UiState();
        SettingsScene.toggle(ui, SettingsScene.ITEM_DEFAULTS);
        assertTrue(SettingsScene.defaultsArmed());
        SettingsScene.toggle(ui, SettingsScene.ITEM_MUSIC);
        assertFalse(SettingsScene.defaultsArmed());
    }

    /** A clock of zero must not arm something for ever, and must still be able to fire. */
    @Test
    public void aClocklessCallerCanStillArmAndConfirm() {
        UiState ui = new UiState();
        ui.hintsOn = false;
        SettingsScene.toggle(ui, SettingsScene.ITEM_DEFAULTS, 0);
        assertTrue(SettingsScene.defaultsArmed());
        SettingsScene.toggle(ui, SettingsScene.ITEM_DEFAULTS, 0);
        assertTrue(ui.hintsOn);
    }

    @Test
    public void aStaleSecondPressAsksAgainInsteadOfResettingAnything() {
        UiState ui = new UiState();
        ui.hintsOn = false;
        SettingsScene.toggle(ui, SettingsScene.ITEM_DEFAULTS, 1000);
        SettingsScene.toggle(ui, SettingsScene.ITEM_DEFAULTS,
                1000 + SettingsScene.CONFIRM_WINDOW_MS + 1);
        assertFalse("a stale confirmation must not restore defaults", ui.hintsOn);
        assertTrue("the stale press becomes a fresh first press", SettingsScene.defaultsArmed());
    }

    @Test
    public void theBackRowNamesWhereItActuallyReturns() {
        UiState ui = new UiState();
        ui.screenBeforeSettings = UiState.HOME;
        assertEquals("Back to the menu", SettingsScene.labels(ui)[SettingsScene.ITEM_BACK]);
        assertEquals("back to choosing a picture",
                SettingsScene.descriptions(ui)[SettingsScene.ITEM_BACK]);

        ui.screenBeforeSettings = UiState.GAME;
        assertEquals("Back to the puzzle",
                SettingsScene.labels(ui)[SettingsScene.ITEM_BACK]);
        assertEquals("we'll keep your place",
                SettingsScene.descriptions(ui)[SettingsScene.ITEM_BACK]);
    }

    // ---- Saying what happened ---------------------------------------------------------

    /**
     * Nine of the eleven rows change something the player cannot see from this screen, and
     * the corner has no message ribbon of its own — the ribbon is drawn with the board. The
     * explanation slot is the one voice it has, so it carries the answer.
     */
    @Test
    public void everySwitchSaysWhatItJustDid() {
        UiState ui = new UiState();
        for (int item = 0; item <= SettingsScene.ITEM_CALM_MOTION; item++) {
            SettingsScene.toggle(ui, item, 1000);
            String said = SettingsScene.bottomLine(item, 1000);
            assertNotEquals("row " + item + " says nothing back",
                    SettingsScene.descriptions()[item], said);
            assertTrue(said + " does not name its row",
                    said.startsWith(SettingsScene.friendlyName(item)));
            assertTrue(said + " does not say which way it went",
                    said.endsWith(SettingsScene.states(ui)[item] ? " is on" : " is off"));
        }
    }

    @Test
    public void theNoteGivesTheRowsExplanationBackAfterwards() {
        UiState ui = new UiState();
        SettingsScene.toggle(ui, SettingsScene.ITEM_MUSIC, 1000);
        assertNotEquals(SettingsScene.descriptions()[SettingsScene.ITEM_MUSIC],
                SettingsScene.bottomLine(SettingsScene.ITEM_MUSIC, 1000));
        assertEquals(SettingsScene.descriptions()[SettingsScene.ITEM_MUSIC],
                SettingsScene.bottomLine(SettingsScene.ITEM_MUSIC,
                        1000 + SettingsScene.NOTE_MS));
    }

    /** While the question is up it is the only thing the bottom line has to say. */
    @Test
    public void theQuestionOwnsTheBottomLineWhileItIsArmed() {
        SettingsScene.armDefaults(500);
        for (int item = 0; item < SettingsScene.ITEM_COUNT; item++) {
            assertTrue(SettingsScene.bottomLine(item, 500).contains("to be sure"));
        }
    }

    /** The state is a word, not just a colour and a knob that moves 27 px. */
    @Test
    public void aSwitchStatesItselfInWords() {
        assertEquals("ON", SettingsScene.stateWord(true));
        assertEquals("OFF", SettingsScene.stateWord(false));
    }

    /** All-caps is a drawing decision; what a screen reader is handed is a sentence. */
    @Test
    public void aRowCanBeSpokenWithoutShouting() {
        assertEquals("Music", SettingsScene.friendlyName(SettingsScene.ITEM_MUSIC));
        assertEquals("Larger text", SettingsScene.friendlyName(SettingsScene.ITEM_BIG_TEXT));
        // A row index from nowhere still names a row rather than throwing.
        assertFalse(SettingsScene.friendlyName(-1).isEmpty());
        assertFalse(SettingsScene.friendlyName(9999).isEmpty());
    }

    // ---- Who is tidying ----------------------------------------------------------------

    @Test
    public void theSubtitleNamesWhoeverOpenedTheCorner() {
        assertEquals("Make the room feel just right", SettingsScene.subtitle());
        SettingsScene.setTidyingPlayer(1);
        assertEquals(Theme.playerName(1) + " is tidying the room", SettingsScene.subtitle());
        SettingsScene.setTidyingPlayer(7);
        assertEquals("a seat nobody is in must not be named",
                "Make the room feel just right", SettingsScene.subtitle());
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

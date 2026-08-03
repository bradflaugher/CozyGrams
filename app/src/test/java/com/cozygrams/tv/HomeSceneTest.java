package com.cozygrams.tv;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The title screen's state, in particular the guard on the one row that can throw away
 * everything a pair has made.
 *
 * <p>Nothing here can check that a row's two strings fit beside each other: the test source
 * set runs with {@code returnDefaultValues}, so {@code Paint.measureText} answers zero and
 * every string is infinitely narrow. That half is checked by rendering — see the worst-case
 * title-screen frames in the preview harness — and the copy is kept short here so the
 * measuring guard in {@code HomeScene.drawRow} stays a backstop rather than the plan.
 */
public class HomeSceneTest {

    @Before
    public void reset() {
        HomeScene.disarmRestart();
        HomeScene.setPendingSize(0);
        HudScene.setRemoteOnly(false);
    }

    @Test
    public void labelsAndValuesLineUpWithTheRowCount() {
        GameState game = new GameState(7, 10);
        assertEquals(HomeScene.ITEM_COUNT, HomeScene.items(game).length);
        assertEquals(HomeScene.ITEM_COUNT, HomeScene.values(game).length);
    }

    @Test
    public void restartStartsUnarmed() {
        assertFalse(HomeScene.restartArmed());
    }

    @Test
    public void armingTheRestartChangesWhatTheRowSays() {
        GameState game = new GameState(7, 10);
        String resting = HomeScene.items(game)[HomeScene.ITEM_RESTART];
        HomeScene.armRestart(1000);
        assertTrue(HomeScene.restartArmed());
        String asking = HomeScene.items(game)[HomeScene.ITEM_RESTART];
        assertFalse("the row must visibly become a question", resting.equals(asking));
        assertTrue(asking.contains("?"));
        assertTrue("and must say it cannot be undone",
                HomeScene.values(game)[HomeScene.ITEM_RESTART].contains("undone"));
    }

    @Test
    public void theQuestionLapsesSoItCannotBeAnsweredMuchLater() {
        HomeScene.armRestart(1000);
        HomeScene.expireRestart(3000);
        assertTrue("still armed a moment later", HomeScene.restartArmed());
        HomeScene.expireRestart(1000 + 6001);
        assertFalse("but not minutes later", HomeScene.restartArmed());
    }

    /**
     * Two people are pressing two controllers at once, so the second press has to come
     * from the hands that asked the question.
     */
    @Test
    public void onlyTheControllerThatAskedMayAnswer() {
        HomeScene.armRestart(1000, 1);
        assertEquals(1, HomeScene.restartArmedBy());
        assertTrue(HomeScene.mayConfirmRestart(1));
        assertFalse("Rose must not be able to answer Sky's question",
                HomeScene.mayConfirmRestart(0));
    }

    @Test
    public void aQuestionNobodyOwnsMayBeAnsweredByAnyone() {
        HomeScene.armRestart(1000);
        assertEquals(-1, HomeScene.restartArmedBy());
        assertTrue(HomeScene.mayConfirmRestart(0));
        assertTrue(HomeScene.mayConfirmRestart(1));
    }

    @Test
    public void lettingTheQuestionGoAlsoForgetsWhoAskedIt() {
        HomeScene.armRestart(1000, 1);
        HomeScene.expireRestart(1000 + 6001);
        assertEquals(-1, HomeScene.restartArmedBy());
        HomeScene.armRestart(1000, 1);
        HomeScene.disarmRestart();
        assertEquals(-1, HomeScene.restartArmedBy());
    }

    @Test
    public void theRestartRowSaysWhatItWouldCost() {
        GameState game = new GameState(7, 5);
        game.startStory(9);
        assertTrue(HomeScene.values(game)[HomeScene.ITEM_RESTART].contains("chapter 10"));
    }

    /**
     * "When" is said by the label, not by a third word trailing the stepper.
     *
     * <p>The value used to read "‹ 20 × 20 ›  next", which runs a label, a control and a
     * hint together in one line so the eye cannot tell which of them "next" belongs to. It
     * is the row's name that changes now, and the value stays a stepper and nothing else.
     */
    @Test
    public void aPendingSizeIsShownAsComingNext() {
        GameState game = new GameState(7, 10);
        HomeScene.setPendingSize(20);
        String value = HomeScene.values(game)[HomeScene.ITEM_SIZE];
        assertTrue(value.contains("20 × 20"));
        assertFalse("the value is a stepper and nothing else", value.contains("next"));
        assertEquals("NEXT BOARD SIZE", HomeScene.items(game)[HomeScene.ITEM_SIZE]);
    }

    @Test
    public void aPendingSizeMatchingTheCurrentBoardIsNotMarkedAsPending() {
        GameState game = new GameState(7, 10);
        HomeScene.setPendingSize(10);
        assertFalse(HomeScene.values(game)[HomeScene.ITEM_SIZE].contains("next"));
        assertEquals("BOARD SIZE", HomeScene.items(game)[HomeScene.ITEM_SIZE]);
    }

    @Test
    public void storyModeShowsTheChapterRatherThanABoardSize() {
        GameState game = new GameState(7, 5);
        game.startStory(3);
        String value = HomeScene.values(game)[HomeScene.ITEM_CONTINUE];
        assertTrue(value.contains("4"));
        assertTrue(value.contains(String.valueOf(PuzzleLibrary.count())));
    }

    /**
     * The story was a one-way door: nothing in the menu could start an endless picture
     * again. The size row is that door, so in story mode it has to say so.
     */
    @Test
    public void theSizeRowIsTheWayOutOfTheStory() {
        GameState story = new GameState(7, 5);
        story.startStory(3);
        assertFalse("the row must not still be called a board size",
                HomeScene.items(story)[HomeScene.ITEM_SIZE].contains("BOARD"));
        assertTrue(HomeScene.items(story)[HomeScene.ITEM_SIZE].contains("ENDLESS"));
        assertEquals("BOARD SIZE", HomeScene.items(new GameState(7, 10))[HomeScene.ITEM_SIZE]);
    }

    /**
     * Chapter fourteen is a 12x12 board and endless play only deals 5, 10, 15 and 20, so
     * the row has to offer 10 — a stepper seeded from the chapter would walk 17 and 22.
     */
    @Test
    public void theSizeRowOnlyOffersSizesEndlessPlayDeals() {
        GameState story = new GameState(7, 5);
        story.startStory(13);
        assertEquals(12, story.size);
        assertEquals(10, HomeScene.endlessSize(story));
        assertTrue(HomeScene.values(story)[HomeScene.ITEM_SIZE].contains("10 × 10"));
        assertEquals(GameState.MAX_SIZE, HomeScene.endlessSize(new GameState(7, 20)));
    }

    @Test
    public void theSizeRowStartsFromWhicheverSizeWasChosenLast() {
        GameState story = new GameState(7, 5);
        story.startStory(3);
        HomeScene.setPendingSize(15);
        String value = HomeScene.values(story)[HomeScene.ITEM_SIZE];
        assertTrue(value.contains("15 × 15"));
        assertEquals("and every size is a fresh picture from inside the story",
                "NEXT PICTURE", HomeScene.items(story)[HomeScene.ITEM_SIZE]);
    }

    /**
     * The chapter count on the title screen is the real length of the book, not a number
     * somebody typed. It has been wrong before.
     */
    @Test
    public void theStoryRowCountsTheChaptersThatExist() {
        GameState fresh = new GameState(7, 10);
        assertTrue(HomeScene.values(fresh)[HomeScene.ITEM_STORY]
                .startsWith(PuzzleLibrary.count() + " "));
    }

    /** And once there is progress, the row shows what the pair have opened. */
    @Test
    public void theStoryRowShowsHowFarTheBookHasBeenOpened() {
        GameState game = new GameState(7, 5);
        game.startStory(4);
        String value = HomeScene.values(game)[HomeScene.ITEM_STORY];
        assertTrue(value.contains("5 of " + PuzzleLibrary.count()));
    }

    @Test
    public void theConfirmButtonIsNamedAfterWhateverIsInThePlayersHands() {
        assertEquals("A", HomeScene.confirmName());
        HudScene.setRemoteOnly(true);
        assertEquals("OK", HomeScene.confirmName());
    }

    @Test
    public void theWelcomeLineIsTidiedAndNeverNull() {
        HomeScene.setWelcome(null);
        assertEquals("", HomeScene.welcome());
        HomeScene.setWelcome("  Back after a while  ");
        assertEquals("Back after a while", HomeScene.welcome());
    }
}

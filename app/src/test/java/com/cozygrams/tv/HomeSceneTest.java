package com.cozygrams.tv;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The title screen's state, in particular the guard on the one row that can throw away
 * everything a pair has made.
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

    @Test
    public void theRestartRowSaysWhatItWouldCost() {
        GameState game = new GameState(7, 5);
        game.startStory(9);
        assertTrue(HomeScene.values(game)[HomeScene.ITEM_RESTART].contains("chapter 10"));
    }

    @Test
    public void aPendingSizeIsShownAsComingNext() {
        GameState game = new GameState(7, 10);
        HomeScene.setPendingSize(20);
        String value = HomeScene.values(game)[HomeScene.ITEM_SIZE];
        assertTrue(value.contains("20 × 20"));
        assertTrue("and marked as not yet in effect", value.contains("next"));
    }

    @Test
    public void aPendingSizeMatchingTheCurrentBoardIsNotMarkedAsPending() {
        GameState game = new GameState(7, 10);
        HomeScene.setPendingSize(10);
        assertFalse(HomeScene.values(game)[HomeScene.ITEM_SIZE].contains("next"));
    }

    @Test
    public void storyModeShowsTheChapterRatherThanABoardSize() {
        GameState game = new GameState(7, 5);
        game.startStory(3);
        String value = HomeScene.values(game)[HomeScene.ITEM_CONTINUE];
        assertTrue(value.contains("4"));
        assertTrue(value.contains(String.valueOf(PuzzleLibrary.count())));
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

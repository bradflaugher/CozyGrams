package com.cozygrams.tv;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The title screen: three rows, two of them steppers, every chapter on the table.
 */
public class HomeSceneTest {

    @Before
    public void reset() {
        HomeScene.disarmRestart();
        HomeScene.setPendingSize(0);
        HomeScene.setPendingChapter(-1);
        HudScene.setRemoteOnly(false);
    }

    @Test
    public void labelsAndValuesLineUpWithTheRowCount() {
        GameState game = new GameState(7, 10);
        assertEquals(3, HomeScene.ITEM_COUNT);
        assertEquals(HomeScene.ITEM_COUNT, HomeScene.items(game).length);
        assertEquals(HomeScene.ITEM_COUNT, HomeScene.values(game).length);
    }

    @Test
    public void theMenuIsStoryEndlessAndSettings() {
        GameState game = new GameState(7, 10);
        String[] items = HomeScene.items(game);
        assertEquals("Story Book", items[HomeScene.ITEM_STORY]);
        assertEquals("Endless", items[HomeScene.ITEM_SIZE]);
        assertEquals("Cozy Corner", items[HomeScene.ITEM_SETTINGS]);
    }

    @Test
    public void aPendingSizeIsShownAsAStepperAndNothingElse() {
        GameState game = new GameState(7, 10);
        HomeScene.setPendingSize(20);
        String value = HomeScene.values(game)[HomeScene.ITEM_SIZE];
        assertTrue(value.contains("20 × 20"));
        assertFalse("the value is a stepper and nothing else", value.contains("next"));
    }

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
        assertTrue(HomeScene.values(story)[HomeScene.ITEM_SIZE].contains("15 × 15"));
    }

    @Test
    public void theStoryRowCountsTheChaptersThatExist() {
        GameState fresh = new GameState(7, 10);
        String value = HomeScene.values(fresh)[HomeScene.ITEM_STORY];
        assertTrue(value.contains("/ " + PuzzleLibrary.count()));
        assertTrue(value.contains("1"));
    }

    @Test
    public void theStoryRowShowsTheChapterOnTheStepper() {
        GameState game = new GameState(7, 5);
        game.startStory(4);
        String value = HomeScene.values(game)[HomeScene.ITEM_STORY];
        assertTrue(value.contains("5 / " + PuzzleLibrary.count()));
    }

    /**
     * Every chapter is on the stepper from the first evening. Left and right wrap the
     * whole book, including evenings the pair have not opened yet.
     */
    @Test
    public void everyChapterIsSelectableFromTheJump() {
        GameState fresh = new GameState(7, 10);
        assertEquals(PuzzleLibrary.count() - 1, HomeScene.furthestPlayable(fresh));
        assertEquals(1, HomeScene.browseChapter(fresh, 1));
        HomeScene.setPendingChapter(-1);
        assertEquals(PuzzleLibrary.count() - 1, HomeScene.browseChapter(fresh, -1));
        HomeScene.setPendingChapter(9);
        assertEquals(9, HomeScene.chapterToShow(fresh));
    }

    @Test
    public void theStoryRowWrapsInsideTheBook() {
        HomeScene.setPendingChapter(-1);
        GameState game = new GameState(7, 5);
        game.startStory(3);
        assertEquals(3, HomeScene.chapterToShow(game));
        assertEquals(2, HomeScene.browseChapter(game, -1));
        assertEquals(3, HomeScene.browseChapter(game, 1));
        assertEquals(4, HomeScene.browseChapter(game, 1));
    }

    @Test
    public void theFooterTellsYouTheSteppersMoveSideways() {
        assertTrue(HomeScene.footerHint(HomeScene.ITEM_SIZE).toLowerCase()
                .contains("start"));
        assertTrue(HomeScene.footerHint(HomeScene.ITEM_STORY).toLowerCase()
                .contains("chapter"));
        assertTrue(HomeScene.compactFooterHint(HomeScene.ITEM_STORY).contains("← →"));
        assertTrue(HomeScene.compactFooterHint(HomeScene.ITEM_SIZE).contains("Start"));
    }

    @Test
    public void landingCopyNamesTheSelectedChapterAndMode() {
        GameState game = new GameState(7, 10);
        HomeScene.setPendingChapter(0);
        assertEquals("First Heart", HomeScene.landingFeature(game, HomeScene.ITEM_STORY));
        assertEquals("First Heart", HomeScene.rowDetail(game, HomeScene.ITEM_STORY));
        assertEquals("10 × 10 picture", HomeScene.landingFeature(game,
                HomeScene.ITEM_SIZE));
        assertEquals("Cozy Corner", HomeScene.landingFeature(game,
                HomeScene.ITEM_SETTINGS));
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

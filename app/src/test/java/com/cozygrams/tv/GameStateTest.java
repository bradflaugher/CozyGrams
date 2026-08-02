package com.cozygrams.tv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** The rules of play: cursors, marks, hints, and moving between boards. */
public class GameStateTest {

    @Test
    public void cursorWrapsAroundTheEdges() {
        GameState game = new GameState(7, 5);
        game.move(0, -1, -1);
        assertEquals(4, game.cursorX[0]);
        assertEquals(4, game.cursorY[0]);
    }

    @Test
    public void markingTheSameSquareTwiceClearsIt() {
        GameState game = new GameState(7, 5);
        game.move(0, -1, -1);
        game.mark(0, Puzzle.FILLED);
        assertEquals(Puzzle.FILLED, game.puzzle.marks[4][4]);
        game.mark(0, Puzzle.FILLED);
        assertEquals(Puzzle.UNKNOWN, game.puzzle.marks[4][4]);
    }

    @Test
    public void eachPlayerMovesTheirOwnCursor() {
        GameState game = new GameState(7, 10);
        game.move(0, 3, 2);
        game.move(1, -1, 0);
        assertEquals(3, game.cursorX[0]);
        assertEquals(2, game.cursorY[0]);
        assertEquals(0, game.cursorX[1]);
        assertEquals(0, game.cursorY[1]);
    }

    @Test
    public void endlessModeKeepsChosenDifficulty() {
        GameState game = new GameState(7, 15);
        game.next();
        game.next();
        game.next();
        assertEquals(15, game.size);
        assertFalse(game.storyMode);
    }

    @Test
    public void endlessNextDealsADifferentBoard() {
        GameState game = new GameState(7, 10);
        long firstSeed = game.seed;
        game.next();
        assertNotEquals(firstSeed, game.seed);
        assertEquals(0, game.moves[0]);
        assertEquals(0, game.moves[1]);
    }

    @Test
    public void hintFindsAndFillsARealSolutionCell() {
        GameState game = new GameState(7, 5);
        assertTrue(game.hint(0));
        int x = game.cursorX[0];
        int y = game.cursorY[0];
        assertEquals(Puzzle.FILLED, game.puzzle.marks[y][x]);
        assertTrue(game.puzzle.solution[y][x]);
    }

    @Test
    public void hintGivesUpOnceThePictureIsComplete() {
        GameState game = new GameState(7, 5);
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x < game.size; x++) {
                if (game.puzzle.solution[y][x]) {
                    game.puzzle.marks[y][x] = Puzzle.FILLED;
                }
            }
        }
        assertFalse(game.hint(0));
    }

    /**
     * Deliberately checks the story against the library rather than against hard-coded
     * names, so the book can be rewritten without this test having to be edited too.
     */
    @Test
    public void storyAdvancesThroughAuthoredPuzzles() {
        GameState game = new GameState(7, 5);
        game.startStory(7);
        assertTrue(game.storyMode);
        assertEquals(PuzzleLibrary.get(7).name, game.puzzle.name);

        game.next();
        assertEquals(8, game.storyIndex);
        assertEquals(PuzzleLibrary.get(8).name, game.puzzle.name);
        assertEquals(PuzzleLibrary.get(8).size, game.size);
    }

    @Test
    public void storyWrapsBackToTheFirstChapter() {
        GameState game = new GameState(7, 5);
        game.startStory(PuzzleLibrary.count() - 1);
        game.next();
        assertEquals(0, game.storyIndex);
        assertEquals(PuzzleLibrary.get(0).name, game.puzzle.name);
    }

    @Test
    public void storyRemembersHowFarThePairHaveTravelled() {
        GameState game = new GameState(7, 5);
        game.startStory(4);
        assertEquals(4, game.storyFurthest);
        game.startStory(2);
        assertEquals("going back a chapter does not lose the journey",
                4, game.storyFurthest);
    }

    @Test
    public void progressReportsHowMuchOfThePictureIsFound() {
        GameState game = new GameState(7, 5);
        assertEquals(0f, game.pictureProgress(), 1e-6);
        for (int y = 0; y < game.size; y++) {
            for (int x = 0; x < game.size; x++) {
                if (game.puzzle.solution[y][x]) {
                    game.puzzle.marks[y][x] = Puzzle.FILLED;
                }
            }
        }
        assertEquals(1f, game.pictureProgress(), 1e-6);
    }

    @Test
    public void startEndlessClampsAnImpossibleSize() {
        GameState game = new GameState(7, 5);
        game.startEndless(11, 999);
        assertEquals(GameState.MAX_SIZE, game.size);
        game.startEndless(11, -4);
        assertEquals(GameState.MIN_SIZE, game.size);
    }
}

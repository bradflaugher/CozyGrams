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

    // ---- Who placed what -------------------------------------------------------------

    /** Puts a player's cursor somewhere without going through the wrapping arithmetic. */
    private static void at(GameState game, int player, int x, int y) {
        game.cursorX[player] = x;
        game.cursorY[player] = y;
    }

    @Test
    public void aMarkRemembersWhoMadeItAndWhen() {
        GameState game = new GameState(7, 5);
        at(game, 0, 2, 3);
        game.mark(0, Puzzle.FILLED, 5000);
        assertEquals("Rose is player 1, so zero can keep meaning nobody",
                1, game.placedBy[3][2]);
        assertEquals(5000, game.placedAt[3][2]);

        at(game, 1, 2, 3);
        game.mark(1, Puzzle.FILLED, 9000);
        assertEquals("clearing hands the square back to nobody",
                GameState.NOBODY, game.placedBy[3][2]);
        assertEquals(0, game.placedAt[3][2]);
    }

    /**
     * The collision the design has by default: {@code resetTable} starts the two cursors on
     * adjacent squares, so one player stepping onto the other's fresh square and pressing
     * the same button is the ordinary opening move, not a corner case.
     */
    @Test
    public void afreshSquareIsTheirPartnersForAWhile() {
        GameState game = new GameState(7, 5);
        at(game, 0, 2, 3);
        game.mark(0, Puzzle.FILLED, 5000);

        at(game, 1, 2, 3);
        assertTrue("Sky is about to rub out something Rose just did",
                game.wouldUndoPartner(1, 5000 + Theme.PARTNER_GRACE_MS - 1));
        assertFalse("after the grace the square is simply part of the picture",
                game.wouldUndoPartner(1, 5000 + Theme.PARTNER_GRACE_MS + 1));
        assertFalse("nobody is ever protected from their own square",
                game.wouldUndoPartner(0, 5000 + 10));
    }

    @Test
    public void theGraceNeverFiresOnASquareWithNoClockBehindIt() {
        GameState game = new GameState(7, 5);
        at(game, 0, 2, 3);
        game.mark(0, Puzzle.FILLED);
        at(game, 1, 2, 3);
        assertFalse("a timeless mark must fail open, not lock the square",
                game.wouldUndoPartner(1, 1));
    }

    @Test
    public void aCrossedSquareIsNeverGuarded() {
        GameState game = new GameState(7, 5);
        at(game, 0, 2, 3);
        game.mark(0, Puzzle.CROSSED, 5000);
        at(game, 1, 2, 3);
        assertFalse("a cross is a guess about where the picture is not",
                game.wouldUndoPartner(1, 5010));
    }

    @Test
    public void aLineCountsAsSharedOnlyWhenBothHandsAreInIt() {
        GameState game = new GameState(7, 5);
        for (int x = 0; x < game.size; x++) {
            at(game, 0, x, 2);
            game.mark(0, Puzzle.FILLED, 100);
        }
        assertFalse("one player filling a row is not a shared row",
                game.lineWasShared(0, 2, true));

        at(game, 1, 4, 2);
        game.mark(1, Puzzle.FILLED, 200);      // clears Rose's last square
        game.mark(1, Puzzle.FILLED, 300);      // and puts Sky's own down
        assertTrue("both of them are in that row now", game.lineWasShared(0, 2, true));
        assertFalse("and the column through it is still Rose's alone",
                game.lineWasShared(4, 0, false));
    }

    @Test
    public void aNewBoardForgetsWhoPlacedWhat() {
        GameState game = new GameState(7, 5);
        at(game, 0, 2, 3);
        game.mark(0, Puzzle.FILLED, 5000);
        game.sharedLines = 4;
        game.next();
        assertEquals(GameState.NOBODY, game.placedBy[3][2]);
        assertEquals(0, game.placedAt[3][2]);
        assertEquals(0, game.sharedLines);
        assertEquals(0, game.lastMarkAt[0]);
    }

    /** The attribution grid has to be the same shape as the marks it describes. */
    @Test
    public void theAttributionGridFollowsTheBoardSize() {
        GameState game = new GameState(7, 5);
        assertEquals(5, game.placedBy.length);
        game.startEndless(11, 20);
        assertEquals(20, game.placedBy.length);
        assertEquals(20, game.placedAt[0].length);
        game.startStory(0);
        assertEquals(game.size, game.placedBy.length);
    }

    @Test
    public void undoingAHintLeavesNothingBehindAndCrossingOutIsAMove() {
        GameState game = new GameState(7, 5);
        at(game, 0, 1, 1);
        game.mark(0, Puzzle.FILLED, 5000);
        int after = game.moves[0];
        game.undoMark(0, 1, 1, Puzzle.UNKNOWN);
        assertEquals(Puzzle.UNKNOWN, game.puzzle.marks[1][1]);
        assertEquals(GameState.NOBODY, game.placedBy[1][1]);
        assertEquals(after - 1, game.moves[0]);

        game.crossOut(1, 4, 4, 6000);
        assertEquals(Puzzle.CROSSED, game.puzzle.marks[4][4]);
        assertEquals(2, game.placedBy[4][4]);
        assertEquals(1, game.moves[1]);
    }

    @Test
    public void aHintIsThatPlayersSquareToo() {
        GameState game = new GameState(7, 5);
        assertTrue(game.hint(1, 4000));
        int x = game.cursorX[1];
        int y = game.cursorY[1];
        assertEquals(2, game.placedBy[y][x]);
        assertEquals(4000, game.placedAt[y][x]);
        assertEquals(Puzzle.FILLED, game.lastMarkKind[1]);
    }

    @Test
    public void theCursorsKnowWhenTheyAreTogether() {
        GameState game = new GameState(7, 10);
        at(game, 0, 4, 4);
        at(game, 1, 5, 4);
        assertFalse(game.sharingASquare());
        assertTrue(game.sideBySide());

        at(game, 1, 4, 4);
        assertTrue(game.sharingASquare());
        assertTrue(game.sideBySide());

        at(game, 1, 7, 4);
        assertFalse(game.sideBySide());
    }
}

package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * What can be held to account about the input surface without a live {@code View}.
 *
 * <p>{@link CozyGameView} needs a {@code Context}, a window and a real controller, none of
 * which exist here — so everything worth asserting is deliberately kept in static, pure
 * helpers, and this is where they are checked. The rest of the class is verified by
 * rendering and by reading the frames.
 */
public class CozyGameViewTest {

    // ---- The animation clock ----------------------------------------------------------

    @Test
    public void theFirstFrameOfASessionIsChargedANominalFrame() {
        assertEquals(CozyGameView.NOMINAL_FRAME_MS, CozyGameView.frameGap(0, 900_000));
        assertEquals(CozyGameView.NOMINAL_FRAME_MS, CozyGameView.frameGap(-5, 900_000));
    }

    @Test
    public void aFrameGapIsClampedAtBothEnds() {
        assertEquals(CozyGameView.MAX_FRAME_MS, CozyGameView.frameGap(1000, 61_000));
        assertEquals(CozyGameView.MIN_FRAME_MS, CozyGameView.frameGap(1000, 1000));
        assertEquals(17, CozyGameView.frameGap(1000, 1017));
    }

    // ---- Held directions ---------------------------------------------------------------

    /**
     * The board and the menus share one ladder, and the board is the fast rung because
     * holding a direction is how a player travels there. The numbers themselves live in
     * {@link Theme}; what matters here is that the two surfaces are on the same ladder and
     * on the right rungs, which is what stopped being true when the board had no gate at
     * all and crossed a 20-wide grid in a third of a second.
     */
    @Test
    public void theBoardRepeatsFasterThanAMenuAndBothWaitTheSameFirstTime() {
        assertTrue(Theme.BOARD_REPEAT_MS < Theme.MENU_REPEAT_MS);
        assertTrue(Theme.REPEAT_FIRST_MS > Theme.MENU_REPEAT_MS);
        // A one-second hold should cross a board, not fly over it: at 300 then 85 that is
        // ten cells, which is half of the widest board.
        long cells = 1 + (1000 - Theme.REPEAT_FIRST_MS) / Theme.BOARD_REPEAT_MS;
        assertTrue("a held direction crosses " + cells + " cells a second",
                cells >= 6 && cells <= 12);
    }

    /**
     * The hold-to-hint gesture has to be longer than Android's own long-press so a slow
     * deliberate press cannot reach it by accident, and short enough to be a hold rather
     * than a wait. It used to be counted in key repeats, which fired at 500 ms on a stock
     * remote and never at all on one that emits no repeats.
     */
    /**
     * A size sitting in the stepper must not steal the next story chapter. That is how
     * finishing the first heart jumped to a 20×20.
     */
    @Test
    public void aBankedSizeNeverHijacksTheStoryBook() {
        assertFalse(CozyGameView.takesTheBankedSize(true, 20, 5));
        assertFalse(CozyGameView.takesTheBankedSize(true, 10, 10));
        assertTrue("endless play still honours a size that is actually next",
                CozyGameView.takesTheBankedSize(false, 20, 5));
        assertFalse("a matching size is not a change",
                CozyGameView.takesTheBankedSize(false, 10, 10));
        assertFalse(CozyGameView.takesTheBankedSize(false, 0, 5));
    }

    @Test
    public void theHintHoldIsLongerThanALongPressAndShorterThanAPause() {
        assertTrue(CozyGameView.HINT_HOLD_MS > 500);
        assertTrue(CozyGameView.HINT_HOLD_MS <= 900);
    }

    // ---- A finished line ---------------------------------------------------------------

    /**
     * The sweep along a finished line takes about the same time whatever the board size.
     * A flat 18 ms lit eight squares of a 20-wide row at once, which reads as a flash.
     */
    @Test
    public void aLineSweepsRatherThanFlashing() {
        for (int size = GameState.MIN_SIZE; size <= GameState.MAX_SIZE; size++) {
            long stagger = CozyGameView.lineStaggerMs(size);
            assertTrue("size " + size, stagger >= 26 && stagger <= 46);
            long sweep = stagger * (size - 1);
            assertTrue("a " + size + "-wide sweep takes " + sweep + " ms",
                    sweep >= 100 && sweep <= 900);
        }
        // Nonsense in, something drawable out.
        assertTrue(CozyGameView.lineStaggerMs(0) > 0);
        assertTrue(CozyGameView.lineStaggerMs(-4) > 0);
    }

    // ---- Speaking ----------------------------------------------------------------------

    /**
     * The whole interface is one canvas with no node tree behind it, so a screen reader
     * has nothing to read unless the game hands it a sentence. Squares are counted from
     * one, because nobody says "row zero" out loud.
     */
    @Test
    public void aCursorDescribesItselfInWordsAPersonWouldUse() {
        GameState game = new GameState(4242, 10);
        game.cursorX[0] = 0;
        game.cursorY[0] = 0;
        assertEquals(Theme.playerName(0) + ", row 1, column 1, empty",
                CozyGameView.describeCursor(game, 0));

        game.move(1, 2, 3);
        game.mark(1, Puzzle.FILLED);
        assertTrue(CozyGameView.describeCursor(game, 1).endsWith(", filled"));
        assertTrue(CozyGameView.describeCursor(game, 1)
                .startsWith(Theme.playerName(1) + ", row 4, column 4"));
    }

    @Test
    public void everySquareStateHasAWordForIt() {
        assertEquals("empty", CozyGameView.markWord(Puzzle.UNKNOWN));
        assertEquals("filled", CozyGameView.markWord(Puzzle.FILLED));
        assertEquals("crossed out", CozyGameView.markWord(Puzzle.CROSSED));
        // A byte from nowhere still says something rather than throwing.
        assertFalse(CozyGameView.markWord((byte) 99).isEmpty());
        assertNotEquals(CozyGameView.markWord(Puzzle.FILLED),
                CozyGameView.markWord((byte) 99));
    }
}

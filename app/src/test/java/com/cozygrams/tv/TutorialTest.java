package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The first-evening tour: a real little puzzle that ends solved, steps that can always be
 * left, buttons that can always be reached with left and right, and words for every hand.
 */
public class TutorialTest {

    private static final long T = 100_000;

    @Test
    public void theLittlePictureIsAHeartWithHonestClues() {
        assertEquals("[1, 1]", java.util.Arrays.toString(Tutorial.rowClues(0)));
        assertEquals("[5]", java.util.Arrays.toString(Tutorial.rowClues(Tutorial.READ_ROW)));
        assertEquals("[5]", java.util.Arrays.toString(Tutorial.rowClues(Tutorial.FILL_ROW)));
        assertEquals("[1]", java.util.Arrays.toString(Tutorial.rowClues(Tutorial.CROSS_ROW)));
        // The square the player crosses out really is empty in the picture.
        assertFalse(Tutorial.PICTURE[Tutorial.CROSS_ROW][Tutorial.CROSS_COL]);
        assertTrue(Tutorial.PICTURE[Tutorial.FILL_ROW][Tutorial.FILL_COL]);
        // And the picture is uniquely solvable from its clues, like every board in the game.
        assertTrue(NonogramSolver.uniquelyLineSolvable(Tutorial.PICTURE));
    }

    @Test
    public void walkingThroughEndsOnTheSolvedPicture() {
        Tutorial tour = new Tutorial();
        tour.start(T);
        long now = T;
        while (true) {
            if (tour.step == Tutorial.STEP_FILL) {
                assertTrue(tour.awaitingAction());
                assertTrue(tour.fill(now));
                assertFalse(tour.fill(now));
            } else if (tour.step == Tutorial.STEP_CROSS) {
                assertTrue(tour.cross(now));
            }
            now += 10_000;
            if (tour.step == Tutorial.STEP_SOLVED) {
                byte[][] marks = tour.board(now, false);
                for (int r = 0; r < Tutorial.SIZE; r++) {
                    assertTrue("row " + r, Tutorial.rowDone(marks, r));
                    assertTrue("col " + r, Tutorial.colDone(marks, r));
                    for (int c = 0; c < Tutorial.SIZE; c++) {
                        assertEquals(Tutorial.PICTURE[r][c] ? Puzzle.FILLED : Puzzle.CROSSED,
                                marks[r][c]);
                    }
                }
            }
            if (!tour.next(now)) {
                break;
            }
        }
        assertTrue(tour.isLast());
        assertEquals("Let's begin", tour.nextLabel());
    }

    /** Squares arrive one by one; with calm motion they are simply there. */
    @Test
    public void squaresArriveInTurnUnlessMotionIsCalm() {
        Tutorial tour = new Tutorial();
        tour.go(Tutorial.STEP_READ, T);
        assertEquals(Puzzle.UNKNOWN, tour.board(T, false)[Tutorial.READ_ROW][4]);
        assertTrue(tour.animating(T + 100, false));
        assertEquals(Puzzle.FILLED, tour.board(T + 5000, false)[Tutorial.READ_ROW][4]);
        assertFalse(tour.animating(T + 5000, false));
        assertEquals(Puzzle.FILLED, tour.board(T, true)[Tutorial.READ_ROW][4]);
        assertFalse(tour.animating(T, true));
    }

    @Test
    public void skipAndBackAreAlwaysWithinReach() {
        Tutorial tour = new Tutorial();
        tour.start(T);
        assertEquals(Tutorial.FOCUS_NEXT, tour.focus);
        // Back is not there on the first step, so left goes straight to Skip.
        tour.moveFocus(-1);
        assertEquals(Tutorial.FOCUS_SKIP, tour.focus);
        tour.moveFocus(-1);
        assertEquals(Tutorial.FOCUS_SKIP, tour.focus);
        tour.next(T);
        assertEquals(Tutorial.FOCUS_NEXT, tour.focus);
        tour.moveFocus(-1);
        assertEquals(Tutorial.FOCUS_BACK, tour.focus);
        tour.back(T);
        assertEquals(Tutorial.STEP_WELCOME, tour.step);
    }

    /** A remote has no cross button, so OK gets there through fill, as it does in play. */
    @Test
    public void aRemoteCrossesThroughFill() {
        Tutorial tour = new Tutorial();
        tour.go(Tutorial.STEP_CROSS, T);
        assertTrue(tour.cycle(T));
        assertFalse(tour.acted);
        assertEquals(Puzzle.FILLED,
                tour.board(T, false)[Tutorial.CROSS_ROW][Tutorial.CROSS_COL]);
        assertTrue(tour.cycle(T + 10));
        assertTrue(tour.acted);
        assertEquals(Puzzle.CROSSED,
                tour.board(T + 10, false)[Tutorial.CROSS_ROW][Tutorial.CROSS_COL]);
        assertNull(tourAt(Tutorial.STEP_SOLVED).target());
    }

    @Test
    public void everyStepHasWordsForEveryHand() {
        int[] hands = {Tutorial.HANDS_PAD, Tutorial.HANDS_REMOTE, Tutorial.HANDS_TOUCH,
                Tutorial.HANDS_UNKNOWN};
        for (int step = 0; step < Tutorial.STEP_COUNT; step++) {
            Tutorial tour = tourAt(step);
            assertFalse(tour.title().isEmpty());
            for (int hand : hands) {
                String spoken = tour.spoken(hand);
                assertTrue(spoken.contains(tour.title()));
                assertTrue(spoken.endsWith("button."));
            }
        }
        Tutorial fill = tourAt(Tutorial.STEP_FILL);
        assertTrue(fill.prompt(Tutorial.HANDS_PAD).contains("A"));
        assertTrue(fill.prompt(Tutorial.HANDS_REMOTE).contains("OK"));
        assertTrue(fill.prompt(Tutorial.HANDS_TOUCH).startsWith("Tap"));
        Tutorial together = tourAt(Tutorial.STEP_TOGETHER);
        assertNotEquals(together.body(Tutorial.HANDS_PAD), together.body(Tutorial.HANDS_TOUCH));
        assertEquals(4, Tutorial.controls(Tutorial.HANDS_REMOTE).length);
    }

    private static Tutorial tourAt(int step) {
        Tutorial tour = new Tutorial();
        tour.go(step, T);
        return tour;
    }
}

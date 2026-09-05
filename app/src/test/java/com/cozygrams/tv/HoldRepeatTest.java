package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Hold-to-repeat without a second event from the hardware.
 *
 * <p>The living-room failure this pins down: a held D-pad or stick that sends one DOWN
 * and then silence used to move a single square. A 20×20 board is twenty taps that way.
 * The view now asks {@link HoldRepeat#due} on its own clock, so the numbers here are
 * the contract that clock has to keep.
 */
public class HoldRepeatTest {

    @Test
    public void aTapIsOneSquare() {
        HoldRepeat hold = downAt(1000);
        assertFalse("a tap must not become two squares",
                hold.due(1000 + Theme.REPEAT_FIRST_MS - 1,
                        Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS));
    }

    @Test
    public void aHoldStartsRepeatingAfterTheSharedFirstWait() {
        HoldRepeat hold = downAt(1000);
        assertTrue(hold.due(1000 + Theme.REPEAT_FIRST_MS,
                Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS));
        assertFalse(hold.due(1000 + Theme.REPEAT_FIRST_MS + Theme.BOARD_REPEAT_MS - 1,
                Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS));
        assertTrue(hold.due(1000 + Theme.REPEAT_FIRST_MS + Theme.BOARD_REPEAT_MS,
                Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS));
    }

    /**
     * The whole point of the change: a pad that never sends another event still
     * crosses a 20-wide board if the view keeps asking.
     */
    @Test
    public void aSilentHoldCrossesATwentyWideBoard() {
        HoldRepeat hold = downAt(0);
        int steps = 1;
        for (long now = 1; now <= 2_000; now++) {
            if (hold.due(now, Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS)) {
                steps++;
            }
        }
        assertEquals(HoldRepeat.stepsDuring(2_000, Theme.REPEAT_FIRST_MS,
                Theme.BOARD_REPEAT_MS), steps);
        assertTrue("a two-second hold only reached " + steps, steps >= 20);
        assertTrue("a two-second hold raced to " + steps, steps <= 22);
    }

    @Test
    public void aMatchingKeyDoesNotResetTheClock() {
        HoldRepeat hold = downAt(1000);
        hold.adopt(0, 0, 1, 11, 20, 1040);
        assertFalse("the hat-then-key pair must not restart the first wait",
                hold.due(1299, Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS));
        assertTrue(hold.due(1300, Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS));
    }

    @Test
    public void lettingTheKeyUpStopsTheWalk() {
        HoldRepeat hold = downAt(1000);
        hold.releaseKey(11, 20, false);
        assertFalse(hold.active);
        assertFalse(hold.due(2000, Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS));
    }

    @Test
    public void aStickStillHeldKeepsWalkingAfterTheMatchingKeyComesUp() {
        HoldRepeat hold = downAt(1000);
        hold.releaseKey(11, 20, true);
        assertTrue(hold.active);
        assertEquals(HoldRepeat.FROM_STICK, hold.keyCode);
        assertTrue(hold.due(1300, Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS));
    }

    @Test
    public void aNewDirectionRestartsTheFirstWait() {
        HoldRepeat hold = downAt(1000);
        hold.adopt(0, 1, 0, 11, 22, 1100);
        assertFalse(hold.due(1399, Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS));
        assertTrue(hold.due(1400, Theme.REPEAT_FIRST_MS, Theme.BOARD_REPEAT_MS));
    }

    private static HoldRepeat downAt(long now) {
        HoldRepeat hold = new HoldRepeat();
        hold.adopt(0, 0, 1, 11, 20, now);
        return hold;
    }
}

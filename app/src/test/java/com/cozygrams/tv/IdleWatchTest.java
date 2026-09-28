package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The evening winding down: full speed while somebody plays, a gentle ten frames a second once
 * they have stopped, and a screen that is allowed to sleep once they have plainly gone — every
 * step undone by the next press.
 */
public class IdleWatchTest {

    private static final long START = 1_000_000;

    @Test
    public void somebodyPlayingGetsEveryFrame() {
        IdleWatch idle = new IdleWatch(START);
        assertEquals(IdleWatch.EVERY_VSYNC, idle.frameDelay(true, START));
        assertEquals(IdleWatch.EVERY_VSYNC,
                idle.frameDelay(true, START + IdleWatch.SETTLE_AFTER_MS - 1));
    }

    @Test
    public void aStillScreenNeverAsksForAFrame() {
        IdleWatch idle = new IdleWatch(START);
        assertEquals(IdleWatch.NO_FRAME, idle.frameDelay(false, START));
        assertEquals(IdleWatch.NO_FRAME, idle.frameDelay(false, START + 3_600_000));
    }

    @Test
    public void aQuietRoomSettlesAndThenDozes() {
        IdleWatch idle = new IdleWatch(START);
        assertEquals(IdleWatch.SETTLED_FRAME_MS,
                idle.frameDelay(true, START + IdleWatch.SETTLE_AFTER_MS));
        assertEquals(IdleWatch.DOZING_FRAME_MS,
                idle.frameDelay(true, START + IdleWatch.DOZE_AFTER_MS));
        assertTrue("settling must be slower than a frame but still alive",
                IdleWatch.SETTLED_FRAME_MS > 16 && IdleWatch.SETTLED_FRAME_MS <= 250);
    }

    @Test
    public void theNextPressBringsEverythingBack() {
        IdleWatch idle = new IdleWatch(START);
        long later = START + IdleWatch.DOZE_AFTER_MS + 5;
        assertTrue(idle.dozing(later));
        assertTrue("the press that ends a doze says so", idle.noticed(later));
        assertEquals(IdleWatch.EVERY_VSYNC, idle.frameDelay(true, later));
        assertFalse("an ordinary press is not a wake-up", idle.noticed(later + 10));
    }

    @Test
    public void theScreenIsLetGoOnceNotOnEveryCheck() {
        IdleWatch idle = new IdleWatch(START);
        assertFalse(idle.dozing(START + IdleWatch.DOZE_AFTER_MS - 1));
        assertTrue(idle.dozing(START + IdleWatch.DOZE_AFTER_MS));
        assertFalse(idle.dozing(START + IdleWatch.DOZE_AFTER_MS * 2));
    }

    @Test
    public void theDozeTimerWaitsForWhatIsLeft() {
        IdleWatch idle = new IdleWatch(START);
        assertEquals(IdleWatch.DOZE_AFTER_MS, idle.untilDoze(START));
        idle.noticed(START + 60_000);
        assertEquals(IdleWatch.DOZE_AFTER_MS - 1_000, idle.untilDoze(START + 61_000));
        assertEquals("never a busy loop", 1_000,
                idle.untilDoze(START + 60_000 + IdleWatch.DOZE_AFTER_MS * 3));
    }

    @Test
    public void aClockThatRunsBackwardsIsNotIdle() {
        IdleWatch idle = new IdleWatch(START);
        assertEquals(0, idle.idleFor(START - 500));
        assertEquals(IdleWatch.EVERY_VSYNC, idle.frameDelay(true, START - 500));
    }
}

package com.cozygrams.tv;

/**
 * Notices when the room has stopped playing, so the game can stop working as hard as it does
 * for somebody who is actually there.
 *
 * <p>{@link Renderer#animating} is honestly true almost all the time — the cursors breathe, the
 * menus pulse, the hearts beat — so without this the view repainted at 60 fps for as long as the
 * app was open. A phone left on the title screen ran its GPU flat out on the coffee table, and
 * because {@code FLAG_KEEP_SCREEN_ON} was never taken back it never slept either; a television
 * never reached its own screensaver, which on an OLED panel is how a title screen gets burned in.
 *
 * <p>So the evening winds down in two steps, both undone by the very next press:
 *
 * <ol>
 *   <li>After {@link #SETTLE_AFTER_MS} with nobody touching anything the ambient motion carries
 *       on at {@link #SETTLED_FRAME_MS} a frame — ten a second, which is still plainly alive to
 *       anyone glancing over but a sixth of the work.</li>
 *   <li>After {@link #DOZE_AFTER_MS} the screen is allowed to sleep, and what little still moves
 *       ticks over once a second until it does.</li>
 * </ol>
 *
 * <p>Plain arithmetic on a clock, with no {@code View} or {@code Context} in sight, so the policy
 * can be tested and {@link Renderer} stays runnable on the desktop preview harness.
 */
final class IdleWatch {

    /** How long without input before the frame rate settles. */
    static final long SETTLE_AFTER_MS = 30_000;
    /** How long without input before the screen is allowed to sleep. */
    static final long DOZE_AFTER_MS = 10 * 60_000;

    /** The frame spacing once settled: 10 fps. */
    static final long SETTLED_FRAME_MS = 100;
    /** The frame spacing once dozing, while the screen decides whether to go dark. */
    static final long DOZING_FRAME_MS = 1_000;

    /** {@link #frameDelay}'s answer for "draw the next frame on the next vsync". */
    static final long EVERY_VSYNC = 0;
    /** {@link #frameDelay}'s answer for "nothing is moving, so do not schedule a frame". */
    static final long NO_FRAME = -1;

    /** When somebody last pressed, touched, scrolled or pushed something. */
    private long lastInputAt;
    /** True once {@link #dozing} has said so, until the next input wakes it. */
    private boolean dozed;

    IdleWatch(long now) {
        lastInputAt = now;
    }

    /**
     * Somebody is here.
     *
     * @return true when this ends a doze, so the caller knows the screen has to be told to stay
     *         awake again. False for every ordinary press, which is nearly all of them.
     */
    boolean noticed(long now) {
        lastInputAt = now;
        boolean woke = dozed;
        dozed = false;
        return woke;
    }

    /** How long it has been since anybody did anything. */
    long idleFor(long now) {
        return Math.max(0, now - lastInputAt);
    }

    /**
     * When to draw again, given whether the renderer still has something moving.
     *
     * @return {@link #NO_FRAME} when nothing moves, {@link #EVERY_VSYNC} while somebody is
     *         playing, and otherwise a delay in milliseconds.
     */
    long frameDelay(boolean animating, long now) {
        if (!animating) {
            return NO_FRAME;
        }
        long idle = idleFor(now);
        if (idle < SETTLE_AFTER_MS) {
            return EVERY_VSYNC;
        }
        return idle < DOZE_AFTER_MS ? SETTLED_FRAME_MS : DOZING_FRAME_MS;
    }

    /**
     * True the first time the room has been quiet long enough for the screen to be allowed to
     * sleep; false before that and on every later ask, so the caller changes the window's
     * flags once rather than on every check.
     */
    boolean dozing(long now) {
        if (dozed || idleFor(now) < DOZE_AFTER_MS) {
            return false;
        }
        dozed = true;
        return true;
    }

    /** How long until {@link #dozing} could first answer true, never less than a second. */
    long untilDoze(long now) {
        return Math.max(1_000, DOZE_AFTER_MS - idleFor(now));
    }
}

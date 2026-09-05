package com.cozygrams.tv;

/**
 * Application-level hold-to-repeat for a single cursor (or the one menu highlight).
 *
 * <p>Android will not do this for us. Plenty of living-room pads — Xbox hat switches,
 * Bluetooth HID remotes, sticks that sit at a constant deflection — send one DOWN and
 * one UP for a held direction, or one motion event when the axis changes and nothing
 * else while the thumb stays put. The platform's own key-repeat never arrives, so a
 * 20-wide board used to cost twenty separate taps.
 *
 * <p>The first step is taken by the caller the moment the direction goes down. This
 * object then answers {@link #due} against the shared cadence in {@link Theme}, so a
 * ticker on the view can keep walking without waiting for another event from the
 * hardware. Releasing the key, or the stick coming back through centre, is what
 * clears it.
 */
final class HoldRepeat {

    /** {@link #keyCode} for a hold that came from a stick or hat, not a key. */
    static final int FROM_STICK = 0;

    boolean active;
    int who;
    int dx;
    int dy;
    int deviceId;
    int keyCode;
    long pressedAt;
    long steppedAt;

    /**
     * Starts a hold whose first step has already been taken at {@code now}.
     *
     * <p>Re-adopting the same direction from the same player is a no-op: the matching
     * D-pad key that follows a hat event must not reset the first-repeat clock, or a
     * pad that reports both would never get past the opening square.
     */
    void adopt(int who, int dx, int dy, int deviceId, int keyCode, long now) {
        if (active && this.who == who && this.dx == dx && this.dy == dy) {
            if (this.keyCode == FROM_STICK) {
                this.keyCode = keyCode;
            }
            this.deviceId = deviceId;
            return;
        }
        this.active = true;
        this.who = who;
        this.dx = dx;
        this.dy = dy;
        this.deviceId = deviceId;
        this.keyCode = keyCode;
        this.pressedAt = now;
        this.steppedAt = now;
    }

    boolean matches(int who, int dx, int dy) {
        return active && this.who == who && this.dx == dx && this.dy == dy;
    }

    /**
     * True when a held direction may take another square. The first wait is
     * {@code firstMs} from the original press; after that it is {@code repeatMs}
     * from the previous step. Mutates {@link #steppedAt} when it returns true so
     * the caller can just move.
     */
    boolean due(long now, long firstMs, long repeatMs) {
        if (!active) {
            return false;
        }
        long wait = now - pressedAt < firstMs ? firstMs : repeatMs;
        if (now - steppedAt < wait) {
            return false;
        }
        steppedAt = now;
        return true;
    }

    /** Lets go when this key comes back up. A stick still held keeps the lane. */
    void releaseKey(int deviceId, int keyCode, boolean stickStillDown) {
        if (!active || this.deviceId != deviceId || this.keyCode != keyCode) {
            return;
        }
        if (stickStillDown) {
            this.keyCode = FROM_STICK;
            return;
        }
        clear();
    }

    /** Lets go when this device's stick or hat has returned to rest. */
    void releaseStick(int deviceId) {
        if (active && this.deviceId == deviceId && this.keyCode == FROM_STICK) {
            clear();
        }
    }

    void clear() {
        active = false;
        who = 0;
        dx = 0;
        dy = 0;
        deviceId = 0;
        keyCode = FROM_STICK;
        pressedAt = 0;
        steppedAt = 0;
    }

    /**
     * How many squares a hold of {@code durationMs} travels, counting the opening
     * tap. Pure arithmetic so the 20-wide board has a number that can be named.
     */
    static int stepsDuring(long durationMs, long firstMs, long repeatMs) {
        if (repeatMs <= 0 || durationMs < firstMs) {
            return 1;
        }
        return 2 + (int) ((durationMs - firstMs) / repeatMs);
    }
}

package android.os;

/**
 * Desktop stand-in for {@code android.os.SystemClock}, with an <em>injected</em> clock.
 *
 * <p>v1.2.0's {@code CozyGameView} reads {@code SystemClock.uptimeMillis()} directly in
 * five places (toast expiry, action feedback, cursor pulse, win entrance, celebration).
 * The baseline has to be byte-reproducible, so nothing here ever consults a real clock:
 * the driver sets the time before each frame and the value stays put until it is set
 * again. This is the only way the original class is influenced by the harness, and it
 * changes no drawing code.
 */
public final class SystemClock {

    private static long uptime;

    private SystemClock() {
    }

    /** Harness hook: pin the clock the view will read on its next frame. */
    public static void setUptimeMillis(long millis) {
        uptime = millis;
    }

    public static long uptimeMillis() {
        return uptime;
    }

    public static long elapsedRealtime() {
        return uptime;
    }

    public static void sleep(long millis) {
        // The harness never advances time by sleeping.
    }
}

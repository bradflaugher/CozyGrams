package android.os;

/**
 * Desktop stand-in for {@code android.os.Handler}.
 *
 * <p>Posted work is dropped rather than run: the harness renders single frames from a
 * frozen clock, so anything scheduled for "later" has no later to happen in. Immediate
 * {@link #post} still runs inline so nothing silently stalls.
 */
public class Handler {

    public Handler() {
    }

    public Handler(Looper looper) {
    }

    public boolean post(Runnable action) {
        if (action != null) {
            action.run();
        }
        return true;
    }

    public boolean postDelayed(Runnable action, long delayMillis) {
        return true;
    }

    public void removeCallbacks(Runnable action) {
    }

    public void removeCallbacksAndMessages(Object token) {
    }
}

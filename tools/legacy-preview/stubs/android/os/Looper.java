package android.os;

/**
 * Desktop stand-in for {@code android.os.Looper}.
 *
 * <p>{@link #getMainLooper()} returns {@code null}, which is the honest answer off-device:
 * there is no Android main thread here. App code that guards on a null looper (as
 * {@code SaveStore} does) then takes its synchronous path instead of posting work that
 * would never run.
 */
public final class Looper {

    private Looper() {
    }

    public static Looper getMainLooper() {
        return null;
    }

    public static Looper myLooper() {
        return null;
    }
}

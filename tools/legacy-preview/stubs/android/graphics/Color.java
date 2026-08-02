package android.graphics;

/**
 * Desktop stand-in for {@code android.graphics.Color}.
 *
 * <p>Colours are packed ARGB ints, bit-for-bit identical to the platform, and the
 * accessors use exactly the platform's shift/mask arithmetic. Nothing here is lossy.
 */
public final class Color {

    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int TRANSPARENT = 0x00000000;

    private Color() {
    }

    public static int rgb(int red, int green, int blue) {
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    /** Harness helper: the AWT colour for a packed ARGB int. */
    static java.awt.Color awt(int color) {
        return new java.awt.Color(color, true);
    }
}

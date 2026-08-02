package com.cozygrams.tv;

/**
 * The comfort options — the ones that change how CozyGrams <em>looks and moves</em>
 * rather than what it is.
 *
 * <p>These live here rather than on {@link UiState} so that any part of the drawing code
 * can honour them without having a {@code UiState} threaded down to it: particles and
 * palette lookups happen a long way from the scene that owns the menu. There is one
 * shared instance for the whole app, loaded by {@link SaveStore} at startup and toggled
 * from the cozy corner.
 */
public final class Comfort {

    // ---- Sky's colour-blind-friendly identity ---------------------------------------

    /**
     * Rose and Sky are a pink/light-blue pair. Their hues are far apart, but their
     * <em>lightness</em> is almost identical (a contrast ratio of roughly 1.3:1), so once
     * hue perception is reduced the two cursors collapse into the same pale smudge.
     *
     * <p>This deeper teal keeps Sky unmistakably cool while making it clearly darker than
     * Rose — about 3:1 against her pink — so the two players differ in lightness as well
     * as hue, and stay apart even in greyscale.
     */
    public static final int SKY_ALT = rgb(18, 92, 116);
    public static final int SKY_ALT_DARK = rgb(8, 56, 74);
    public static final int SKY_ALT_LIGHT = rgb(118, 186, 208);

    private static final Comfort SHARED = new Comfort();

    /** Sky wears a deeper teal and a dashed cursor ring, so colour is never the only cue. */
    public boolean distinctPlayers;
    /** Thicker, larger cursor rings for players who need a bigger target to find. */
    public boolean boldCursor;
    /** Fewer particles and no idle breathing, for anyone who finds movement tiring. */
    public boolean calmMotion;

    private Comfort() {
    }

    /** The one shared set of comfort options. */
    public static Comfort get() {
        return SHARED;
    }

    /** Sky's current identity colour, which is the only place the alternate hue lives. */
    public static int skyColor() {
        return SHARED.distinctPlayers ? SKY_ALT : Theme.BLUE;
    }

    public static int skyColorDark() {
        return SHARED.distinctPlayers ? SKY_ALT_DARK : Theme.BLUE_DARK;
    }

    public static int skyColorLight() {
        return SHARED.distinctPlayers ? SKY_ALT_LIGHT : Theme.BLUE_LIGHT;
    }

    /** Opaque ARGB, worked out here so these colours are real values everywhere. */
    private static int rgb(int red, int green, int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    /** Everything off: the warm, lively look the game ships with. */
    public void restoreDefaults() {
        distinctPlayers = false;
        boldCursor = false;
        calmMotion = false;
    }
}

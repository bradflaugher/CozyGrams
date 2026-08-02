package com.cozygrams.tv;

/**
 * Presentation state: which screen is showing, what the menus have highlighted, the
 * current message, and the player-facing options. Kept apart from {@link GameState} so
 * the renderer can be exercised without a live Android view.
 */
public final class UiState {

    public static final int HOME = 0;
    public static final int GAME = 1;
    public static final int SETTINGS = 2;

    public int screen = HOME;
    public int menu;
    /** Where the cozy corner was opened from, so Back always returns there. */
    public int screenBeforeSettings = HOME;
    /** Menu row that was highlighted before the cozy corner opened. */
    public int menuBeforeSettings;

    public boolean won;
    public long winAt;

    /** Message shown along the bottom of the board. */
    public String toast = "Press a button on each controller to join";
    public long toastAt;
    /** Optional accent colour for the current message. */
    public int toastColor = Theme.CREAM;

    // The look the game ships with. These live here rather than in the save layer so
    // that "put everything back" is a plain state change with no persistence behind it,
    // which keeps the whole rendering set free of Android storage types.
    public static final boolean DEFAULT_MUSIC = true;
    public static final boolean DEFAULT_SFX = true;
    public static final boolean DEFAULT_GENTLE = false;
    public static final boolean DEFAULT_HINTS = true;
    public static final boolean DEFAULT_BIG_TEXT = false;
    public static final boolean DEFAULT_CONTRAST = false;

    public boolean musicOn = DEFAULT_MUSIC;
    public boolean sfxOn = DEFAULT_SFX;
    public boolean gentleCheck = DEFAULT_GENTLE;
    public boolean hintsOn = DEFAULT_HINTS;
    public boolean bigTextOn = DEFAULT_BIG_TEXT;
    public boolean highContrastOn = DEFAULT_CONTRAST;

    /** True once a controller has claimed the matching player slot. */
    public final boolean[] joined = {false, false};
    /** When each player last did anything, for gently dimming an idle cursor. */
    public final long[] lastActive = {0, 0};

    /** Smoothed cursor positions in board coordinates, for gliding movement. */
    public final float[] cursorDrawX = {0, 1};
    public final float[] cursorDrawY = {0, 0};

    /** When each cursor last moved, used to time the landing squash. */
    public final long[] cursorMovedAt = {0, 0};

    /** Puts every option back to the look the game ships with. */
    public void restoreDefaults() {
        musicOn = DEFAULT_MUSIC;
        sfxOn = DEFAULT_SFX;
        gentleCheck = DEFAULT_GENTLE;
        hintsOn = DEFAULT_HINTS;
        bigTextOn = DEFAULT_BIG_TEXT;
        highContrastOn = DEFAULT_CONTRAST;
        Comfort.get().restoreDefaults();
    }

    public void showToast(String message, int color, long now) {
        toast = message;
        toastColor = color;
        toastAt = now;
    }

    public void showToast(String message, long now) {
        showToast(message, Theme.CREAM, now);
    }

    /** Snaps the drawn cursors onto their logical squares, e.g. after a new board. */
    public void snapCursors(GameState game) {
        for (int player = 0; player < 2; player++) {
            cursorDrawX[player] = game.cursorX[player];
            cursorDrawY[player] = game.cursorY[player];
        }
    }

    /**
     * Eases the drawn cursors toward their logical squares. Wrapping is handled by
     * snapping instead of gliding, so a cursor never streaks across the whole board.
     */
    public void animateCursors(GameState game, float amount) {
        for (int player = 0; player < 2; player++) {
            cursorDrawX[player] = approach(cursorDrawX[player], game.cursorX[player], amount);
            cursorDrawY[player] = approach(cursorDrawY[player], game.cursorY[player], amount);
        }
    }

    private static float approach(float from, float to, float amount) {
        if (Math.abs(to - from) > 1.6f) {
            return to;
        }
        if (Math.abs(to - from) < .002f) {
            return to;
        }
        return from + (to - from) * amount;
    }

    /** True while any cursor is still sliding, so the view keeps requesting frames. */
    public boolean cursorsSettling(GameState game) {
        for (int player = 0; player < 2; player++) {
            if (cursorDrawX[player] != game.cursorX[player]
                    || cursorDrawY[player] != game.cursorY[player]) {
                return true;
            }
        }
        return false;
    }
}

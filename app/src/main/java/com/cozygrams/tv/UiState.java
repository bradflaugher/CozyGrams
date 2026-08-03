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

    /**
     * The television's own Display &amp; Sound text size, as {@code Configuration.fontScale}.
     *
     * <p>Kept beside {@link #bigTextOn} because the two are the same request arriving by two
     * routes, and they have to <em>compose</em>: someone who has already told the whole
     * television they need bigger words and then turns LARGER TEXT on is asking twice, not
     * asking for the larger of the two. See {@link #textScale()}.
     */
    public float systemTextScale = 1f;

    /**
     * What type is multiplied by: the set's own preference and the game's own switch,
     * together.
     *
     * <p>{@link Theme#setTextScale} clamps the product, so a manufacturer shipping a
     * {@code fontScale} of 2.0 cannot combine with LARGER TEXT to demand type half again
     * as large as the board can hold.
     */
    public float textScale() {
        return systemTextScale * (bigTextOn ? Theme.TEXT_SCALE_BIG : 1f);
    }

    /** True once a controller has claimed the matching player slot. */
    public final boolean[] joined = {false, false};
    /** When each player last did anything, for gently dimming an idle cursor. */
    public final long[] lastActive = {0, 0};

    /** Smoothed cursor positions in board coordinates, for gliding movement. */
    public final float[] cursorDrawX = {0, 1};
    public final float[] cursorDrawY = {0, 0};

    /** When each cursor last moved, used to time the landing squash. */
    public final long[] cursorMovedAt = {0, 0};

    /**
     * Which way each cursor is travelling, -1, 0 or 1 per axis. Held after it lands so the
     * margin tabs can ease their lean out instead of dropping it the frame the glide ends.
     */
    public final float[] cursorHeadingX = {0, 0};
    public final float[] cursorHeadingY = {0, 0};

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
     * Eases the drawn cursors toward their logical squares over {@code dtMillis} of real
     * time. Wrapping is handled by snapping instead of gliding, so a cursor never streaks
     * across the whole board.
     *
     * <p>This took a fraction to close <em>per frame</em> — .34, which lands in 92 ms at
     * 60 Hz. That makes the glide's speed a property of the panel rather than of the game:
     * the identical code takes 184 ms on a 30 Hz box and 245 ms at 24 Hz, where it reads as
     * floaty and then as broken. Android TV hardware is not all 60 Hz and neither is a view
     * that has just been resumed, so the curve is an exponential in milliseconds now — see
     * {@link Theme#MOTION_TAU_MS}, whose 42 ms reproduces the 60 Hz feel the game shipped
     * with.
     *
     * <p>Clamping {@code dtMillis} to something a frame could plausibly have taken is the
     * caller's job: a view that was paused for a minute otherwise hands over sixty thousand
     * milliseconds and both cursors teleport.
     */
    public void animateCursors(GameState game, long dtMillis) {
        float amount = Draw.approachRate(dtMillis, Theme.MOTION_TAU_MS);
        for (int player = 0; player < 2; player++) {
            cursorHeadingX[player] = heading(cursorDrawX[player], game.cursorX[player],
                    cursorHeadingX[player]);
            cursorHeadingY[player] = heading(cursorDrawY[player], game.cursorY[player],
                    cursorHeadingY[player]);
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

    /**
     * Which way a cursor is travelling on one axis, or the way it last travelled once the
     * gap has closed.
     *
     * <p>Read off the gap the glide is closing rather than recorded at the key press, so it
     * is right for the analog stick and for a hint that jumps the cursor across the board,
     * and so it cannot drift out of step with the motion it is describing.
     */
    private static float heading(float from, float to, float previous) {
        float gap = to - from;
        if (gap > .01f) {
            return 1f;
        }
        return gap < -.01f ? -1f : previous;
    }

    /**
     * How far through its arrival a cursor is: 0 the instant it moves, 1 once it has
     * settled. {@link CursorRenderer} draws the landing squash and the margin tabs' lean
     * from this, and {@code Renderer.animating} reads it to know when it may stop asking
     * for frames.
     */
    public float landing(int player, long now) {
        if (cursorMovedAt[player] <= 0) {
            return 1f;
        }
        return Draw.clamp01((now - cursorMovedAt[player]) / Theme.CURSOR_LAND_MS);
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

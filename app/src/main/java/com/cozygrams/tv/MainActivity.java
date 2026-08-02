package com.cozygrams.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/**
 * Hosts the single game surface.
 *
 * <p>The activity stays deliberately thin: it owns the window flags a leanback game
 * needs and hands everything else to {@link CozyGameView}.
 */
@SuppressWarnings("deprecation")
public final class MainActivity extends Activity {

    private static final int IMMERSIVE_FLAGS =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private CozyGameView game;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Nobody wants the TV to sleep while they are staring at a half-finished puzzle.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new CozyGameView(this);
        setContentView(game);
        applyImmersiveMode();
    }

    /**
     * Re-asserts fullscreen whenever the window comes back. Android TV restores the
     * system bars after a notification or a launcher overlay, and a bar creeping back in
     * over the board is exactly the kind of friction this game should never have.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
            if (game != null) {
                game.requestFocus();
            }
        }
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(IMMERSIVE_FLAGS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (game != null) {
            game.resume();
        }
    }

    @Override
    protected void onPause() {
        if (game != null) {
            game.pause();
        }
        super.onPause();
    }
}

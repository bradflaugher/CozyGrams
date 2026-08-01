package com.cozygrams.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity {
    private CozyGameView game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        game = new CozyGameView(this);
        setContentView(game);
    }
    @Override protected void onResume() { super.onResume(); if (game != null) game.resume(); }
    @Override protected void onPause() { if (game != null) game.pause(); super.onPause(); }
}

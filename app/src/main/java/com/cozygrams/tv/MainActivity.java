package com.cozygrams.tv;

import android.app.Activity;
import android.content.Context;
import android.hardware.input.InputManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/**
 * Hosts the single game surface.
 *
 * <p>The activity stays deliberately thin: it owns the window flags a leanback game
 * needs, it owns the room's attention, and it hands everything else to {@link CozyGameView}.
 *
 * <h3>Audio focus</h3>
 * A TV is a shared room. The Assistant answers a question, a doorbell camera speaks, another app
 * starts playing — and until now CozyGrams would have carried on regardless, because it never
 * asked Android for the room and never listened for being told it had lost it. It asks now, in
 * {@code onResume}, and gives it back in {@code onPause}.
 *
 * <p>Losing focus is handled the way a considerate guest would: something transient stops the
 * music and the effects until it is over, something permanent stops them and does not creep back
 * on its own. A <em>denied</em> request is deliberately treated as permission to play — some set
 * top boxes never grant focus to anything, and a game that went silent on those would be broken
 * for a reason the player could never discover.
 *
 * <h3>Controllers coming and going</h3>
 * A pad's batteries die mid-picture and the platform says so exactly once, here. Without that
 * message {@link PlayerRegistry} keeps the seat filled for ever: Sky's cursor sits on the board
 * with nobody behind it, the legend goes on naming buttons nobody is holding, and a fresh
 * controller has to share Rose rather than taking the empty chair. The listener is registered for
 * the life of the activity because a controller can just as easily disappear while the game is
 * paused behind a launcher overlay.
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
    private AudioManager audio;
    private AudioManager.OnAudioFocusChangeListener focusListener;
    private Object focusRequest;
    private InputManager input;
    private InputManager.InputDeviceListener deviceListener;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        // Nobody wants the TV to sleep while they are staring at a half-finished puzzle.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new CozyGameView(this);
        setContentView(game);
        applyImmersiveMode();
        watchForControllers();
    }

    @Override
    protected void onDestroy() {
        if (input != null && deviceListener != null) {
            input.unregisterInputDeviceListener(deviceListener);
            deviceListener = null;
        }
        super.onDestroy();
    }

    // ---- Controllers -------------------------------------------------------------------

    /**
     * Listens for a controller leaving. Added and removed only — a device that has changed
     * (a pad renegotiating its layout, say) is still the same pad in the same chair, and
     * treating that as an arrival would toast "Sky joined" at somebody already sitting down.
     */
    private void watchForControllers() {
        input = (InputManager) getSystemService(Context.INPUT_SERVICE);
        if (input == null) {
            return;
        }
        deviceListener = new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                // Nothing to do: a controller takes its seat on its first button press, so
                // that the seat belongs to somebody who is actually playing.
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                if (game != null) {
                    game.onControllerLost(deviceId);
                }
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
            }
        };
        try {
            input.registerInputDeviceListener(deviceListener, null);
        } catch (Throwable ignored) {
            // A box that will not talk about its input devices still gets to play.
            deviceListener = null;
        }
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
        requestAudioFocus();
        if (game != null) {
            game.resume();
        }
    }

    @Override
    protected void onPause() {
        if (game != null) {
            game.pause();
        }
        abandonAudioFocus();
        super.onPause();
    }

    // ---- Audio focus -------------------------------------------------------------------

    private void requestAudioFocus() {
        if (audio == null) {
            audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (audio == null) {
            return;
        }
        if (focusListener == null) {
            focusListener = new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int change) {
                    handleFocusChange(change);
                }
            };
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest == null) {
                    focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build())
                            .setOnAudioFocusChangeListener(focusListener)
                            .setWillPauseWhenDucked(false)
                            .build();
                }
                audio.requestAudioFocus((AudioFocusRequest) focusRequest);
            } else {
                audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Throwable ignored) {
            // A box that will not talk about focus still gets to play the game.
        }
    }

    private void abandonAudioFocus() {
        if (audio == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audio.abandonAudioFocusRequest((AudioFocusRequest) focusRequest);
            } else if (focusListener != null) {
                audio.abandonAudioFocus(focusListener);
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    /**
     * Android delivers this on a binder thread. {@link CozyGameView#pause} and
     * {@link CozyGameView#resume} touch the save store and the view's own state, so the work is
     * posted back to the UI thread rather than done where it arrives.
     */
    private void handleFocusChange(int change) {
        final CozyGameView view = game;
        if (view == null) {
            return;
        }
        final boolean giveWay = change == AudioManager.AUDIOFOCUS_LOSS
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
        if (!giveWay && change != AudioManager.AUDIOFOCUS_GAIN) {
            // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: something short is about to speak over us,
            // so the music steps aside without stopping. A cozy game that killed its own
            // music every time the television said something would be worse than one that
            // never had any.
            view.post(new Runnable() {
                @Override
                public void run() {
                    view.duck(true);
                }
            });
            return;
        }
        view.post(new Runnable() {
            @Override
            public void run() {
                if (giveWay) {
                    view.pause();
                } else {
                    // Whatever spoke over us has finished; the score comes back up whether
                    // it ducked or stopped.
                    view.duck(false);
                    view.resume();
                }
            }
        });
    }
}

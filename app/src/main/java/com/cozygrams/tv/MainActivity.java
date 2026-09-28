package com.cozygrams.tv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
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
 * <p>Two more things outrank the game however focus is going. A phone call — or a video call,
 * which is {@code MODE_IN_COMMUNICATION} — means the music does not start at all, even if a
 * focus gain arrives in the middle of it. And headphones coming out mean the sound stops there
 * and then, because the phone's own speaker is the last place anybody on a bus wanted it; the
 * puzzle is left exactly as it was.
 *
 * <p>Focus changes arrive on a binder thread and are posted to the UI thread, so they can land
 * after {@code onPause}. {@link #resumed} is what stops a late "you can play again" from
 * starting the music behind whatever the player went off to do.
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
    private AudioFocusRequest focusRequest;
    private InputManager input;
    private InputManager.InputDeviceListener deviceListener;
    private BroadcastReceiver noisyReceiver;

    /**
     * True between {@code onResume} and {@code onPause}. Only ever read and written on the UI
     * thread; the focus listener's work is posted there before it looks.
     */
    private boolean resumed;

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

    /**
     * The manifest asks to keep the activity through a text-size change, a new colour mode,
     * a keyboard folding away and the rest, rather than being torn down and rebuilt around a
     * half-finished puzzle. Keeping it is only half the job: the view still has to draw the
     * next frame in the new configuration, and it reads the font scale fresh every frame.
     */
    @Override
    public void onConfigurationChanged(Configuration changed) {
        super.onConfigurationChanged(changed);
        if (game != null) {
            game.invalidate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        requestAudioFocus();
        listenForHeadphones();
        if (game != null) {
            game.resume();
            if (inACall()) {
                // The picture is welcome during a call; the music is not.
                game.hush();
            }
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (game != null) {
            game.pause();
        }
        stopListeningForHeadphones();
        abandonAudioFocus();
        super.onPause();
    }

    // ---- Headphones --------------------------------------------------------------------

    /**
     * {@code ACTION_AUDIO_BECOMING_NOISY} is Android saying "whatever you are playing is about
     * to come out of the speaker instead". It cannot be declared in the manifest, and there is
     * no reason to hear it while nothing is playing, so it is listened for only while the game
     * is in front. Delivered on the UI thread.
     */
    private void listenForHeadphones() {
        if (noisyReceiver != null) {
            return;
        }
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (game != null && intent != null
                        && AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    game.headphonesOut();
                }
            }
        };
        try {
            registerReceiver(noisyReceiver,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        } catch (Throwable ignored) {
            // A box that will not say so still plays; it has no headphones to pull out.
            noisyReceiver = null;
        }
    }

    private void stopListeningForHeadphones() {
        if (noisyReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(noisyReceiver);
        } catch (Throwable ignored) {
            // Already gone.
        }
        noisyReceiver = null;
    }

    /** True during a phone call or a voice or video call in another app. */
    private boolean inACall() {
        if (audio == null) {
            return false;
        }
        try {
            int mode = audio.getMode();
            return mode == AudioManager.MODE_IN_CALL
                    || mode == AudioManager.MODE_IN_COMMUNICATION;
        } catch (Throwable ignored) {
            return false;
        }
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
            audio.requestAudioFocus(focusRequest);
        } catch (Throwable ignored) {
            // A box that will not talk about focus still gets to play the game.
        }
    }

    private void abandonAudioFocus() {
        if (audio == null) {
            return;
        }
        try {
            if (focusRequest != null) {
                audio.abandonAudioFocusRequest(focusRequest);
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
                } else if (resumed && !inACall()) {
                    // Whatever spoke over us has finished; the score comes back up whether
                    // it ducked or stopped. Only while we are still in front, though: this
                    // was posted from a binder thread, and if onPause got to the UI thread
                    // first, resuming here would start the music behind the launcher.
                    view.duck(false);
                    view.resume();
                } else {
                    // Undo a duck all the same, so the next real resume starts at full level.
                    view.duck(false);
                }
            }
        });
    }
}

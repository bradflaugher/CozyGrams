package com.cozygrams.tv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Plays {@link CozyScore} — "Rain on the Window" — straight out of the CPU, so the APK carries no
 * audio at all. This class owns only the plumbing: one streaming {@code AudioTrack}, one daemon
 * render thread, and a soft limiter that holds the music bus under
 * {@link CozySynth#MUSIC_CEILING}. Everything musical lives in {@link CozyScore}.
 *
 * <p>If the device cannot give us an {@code AudioTrack} (some emulators, some set-top boxes with
 * no audio route) the failure is swallowed once and music quietly stays off for the session
 * rather than taking the game down with it.
 */
final class CozyMusic {

    /** Block size in stereo <em>frames</em>. */
    private static final int BLOCK = 1024;
    /** How far down a duck goes, and how long it takes to get there. */
    private static final double DUCK_LEVEL = .25;
    private static final double DUCK_SECONDS = .30;

    private final CozyScore score = new CozyScore(CozySynth.SAMPLE_RATE, 0xC0DEBA5EL);

    private AudioTrack track;
    private Thread thread;
    private volatile boolean playing;
    private volatile boolean ducked;
    private boolean enabled = true;
    private boolean audioBroken;

    synchronized void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            start();
        } else {
            stop();
        }
    }

    synchronized void start() {
        if (thread != null && !thread.isAlive()) {
            thread = null;
            track = null;
            playing = false;
        }
        if (playing || !enabled || audioBroken || thread != null) {
            return;
        }
        AudioTrack built = build();
        if (built == null) {
            audioBroken = true;
            return;
        }
        track = built;
        playing = true;
        // Nothing is rendering right now — stop() joined the last thread — so this is the one
        // safe moment to wind the score's fade back and have the music arrive rather than appear.
        score.resumeFade();
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                render(built);
            }
        }, "cozy-music");
        thread.setDaemon(true);
        thread.start();
    }

    private AudioTrack build() {
        try {
            int minimum = AudioTrack.getMinBufferSize(CozySynth.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) {
                minimum = BLOCK * 4 * 4;
            }
            AudioTrack built = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(CozySynth.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build())
                    .setBufferSizeInBytes(Math.max(minimum, BLOCK * 4 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            if (built.getState() != AudioTrack.STATE_INITIALIZED) {
                built.release();
                return null;
            }
            return built;
        } catch (Throwable unavailable) {
            return null;
        }
    }

    /** The render thread owns the track from here on, including releasing it. */
    private void render(AudioTrack owned) {
        float[] mix = new float[BLOCK * 2];
        short[] pcm = new short[BLOCK * 2];
        double duck = 1;
        double duckStep = 1 / (DUCK_SECONDS * CozySynth.SAMPLE_RATE / BLOCK);
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        } catch (Throwable ignored) {
            // Priority is a nicety; a TV that refuses it still gets music.
        }
        try {
            owned.play();
            while (true) {
                boolean last = !playing;
                score.render(mix, 0, BLOCK);
                double target = ducked ? DUCK_LEVEL : 1;
                duck += Math.max(-duckStep, Math.min(duckStep, target - duck));
                for (int i = 0; i < BLOCK * 2; i++) {
                    double value = CozySynth.limit(mix[i] * duck, CozySynth.MUSIC_CEILING);
                    if (last) {
                        // One final block ramped to silence, so stopping never clicks.
                        value *= (BLOCK * 2 - 1 - i) / (double) (BLOCK * 2);
                    }
                    pcm[i] = (short) (value * 32767);
                }
                if (owned.write(pcm, 0, BLOCK * 2) < 0 || last) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            // A track pulled out from under us on stop() is expected; just unwind.
        } finally {
            try {
                owned.pause();
                owned.flush();
                owned.stop();
            } catch (Throwable ignored) {
                // Best effort.
            }
            owned.release();
        }
    }

    /** Lifts the score for a few seconds when a puzzle is finished, then lets it settle back. */
    synchronized void celebrate() {
        score.celebrate();
    }

    /** A finished line: a second of countermelody, with no change in level. */
    void sparkle() {
        score.sparkle();
    }

    /** Somebody pressed something. Keeps the arrangement from receding into its idle hush. */
    void nudge() {
        score.nudge();
    }

    /** One player or two. With two the pad opens up and the bass walks in halves. */
    void setPresence(int players) {
        score.setPresence(players);
    }

    /**
     * How far along the picture is: 0 keeps the arrangement at its sparsest, 1 brings the felt
     * piano, the rain and an octave shimmer on the melody forward.
     */
    void setIntensity(float value) {
        score.setIntensity(value);
    }

    /**
     * Steps out of the way of something more important — a notification, the Assistant — without
     * stopping. Android hands us {@code AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK} for exactly this, and
     * a cozy game that killed its own music every time the TV said something would be worse than
     * one that never had any.
     */
    void duck(boolean value) {
        ducked = value;
    }

    /** Safe to call repeatedly, from any thread, and from {@code onDetachedFromWindow}. */
    synchronized void stop() {
        playing = false;
        Thread old = thread;
        thread = null;
        AudioTrack oldTrack = track;
        track = null;
        if (old == null || old == Thread.currentThread()) {
            return;
        }
        try {
            old.join(350);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (old.isAlive() && oldTrack != null) {
            // A blocked write only unblocks if we pause the track underneath it.
            try {
                oldTrack.pause();
                oldTrack.flush();
            } catch (Throwable ignored) {
                // Best effort; the render thread still releases in its finally block.
            }
            try {
                old.join(250);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

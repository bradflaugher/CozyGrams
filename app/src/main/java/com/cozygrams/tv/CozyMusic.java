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

    private static final int BLOCK = 1024;

    private final CozyScore score = new CozyScore(CozySynth.SAMPLE_RATE, 0xC0DEBA5EL);

    private AudioTrack track;
    private Thread thread;
    private volatile boolean playing;
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
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) {
                minimum = BLOCK * 2 * 4;
            }
            AudioTrack built = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(CozySynth.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(minimum, BLOCK * 2 * 4))
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
        float[] mix = new float[BLOCK];
        short[] pcm = new short[BLOCK];
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
                for (int i = 0; i < BLOCK; i++) {
                    double value = CozySynth.limit(mix[i], CozySynth.MUSIC_CEILING);
                    if (last) {
                        // One final block ramped to silence, so stopping never clicks.
                        value *= (BLOCK - 1 - i) / (double) BLOCK;
                    }
                    pcm[i] = (short) (value * 32767);
                }
                if (owned.write(pcm, 0, BLOCK) < 0 || last) {
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

    /**
     * Optional: 0 keeps the arrangement at its sparsest, 1 brings the felt piano and the rain
     * forward. Nothing calls it yet — the caller can wire it to puzzle progress whenever it likes.
     */
    void setIntensity(float value) {
        score.setIntensity(value);
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

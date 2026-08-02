package com.cozygrams.tv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.LockSupport;

/**
 * The game's sound effects: ten distinct synthesised voices mixed by one small polyphonic engine.
 *
 * <h3>Why a mixer instead of a queue of tracks</h3>
 * CozyGrams is a two-player game, and Rose and Sky press buttons at the same time. Playing each
 * effect on its own {@code AudioTrack} and sleeping for its duration means the second player's
 * sound arrives late or not at all. Instead there is a single streaming track and a pool of
 * {@link #VOICES} voice slots which are summed into every output block, so any twelve effects can
 * overlap and nobody's input is ever swallowed by somebody else's.
 *
 * <p>{@link #play} does no synthesis and never blocks: it drops the request into a bounded queue
 * and wakes the mixer. Rendering, {@code AudioTrack} construction and every allocation happen on
 * the mixer thread. When nothing has sounded for a moment the track is paused and the thread
 * parks, so an idle living room costs nothing and muting stops it outright.
 *
 * <p>Mix budget: each voice peaks at most {@link CozySynth#VOICE_PEAK}, and the summed bus is
 * soft-limited to {@link CozySynth#SFX_CEILING}. See {@link CozySynth} for the full accounting.
 */
final class CozySfx {

    /** The ordinals here are the contract shared with {@link CozySynth}'s sound constants. */
    enum Sound { MOVE, FILL, CROSS, CLEAR, HINT, ERROR, WIN, SELECT, LINE, JOIN }

    private static final int BLOCK = 256;
    private static final int VOICES = 12;
    /** Anything faster than this on the D-pad is not audible as separate taps anyway. */
    private static final long MOVE_MIN_GAP_NS = 40_000_000L;
    private static final long IDLE_PARK_NS = 800_000_000L;

    private final ArrayBlockingQueue<Sound> pending = new ArrayBlockingQueue<Sound>(32);
    private final Voice[] voices = new Voice[VOICES];
    private final float[] accumulator = new float[BLOCK];
    private final short[] pcm = new short[BLOCK];

    private volatile boolean enabled = true;
    private volatile boolean running;
    private volatile boolean audioBroken;
    private volatile Thread mixer;
    private volatile AudioTrack sink;

    private volatile long lastMoveRequest;
    private long lastMoveVoice;
    private long triggerCount;

    CozySfx() {
        for (int i = 0; i < VOICES; i++) {
            voices[i] = new Voice();
        }
    }

    synchronized void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            startMixer();
        } else {
            shutdown();
        }
    }

    /**
     * Called straight from {@code onKeyDown}. Allocation free, lock free enough to be harmless on
     * the UI thread, and it never waits for audio.
     */
    void play(Sound sound) {
        if (sound == null || !enabled || !running) {
            return;
        }
        if (sound == Sound.MOVE) {
            // Two players scrubbing a 20x20 board would otherwise machine-gun the mixer.
            long now = System.nanoTime();
            if (now - lastMoveRequest < MOVE_MIN_GAP_NS) {
                return;
            }
            lastMoveRequest = now;
        }
        pending.offer(sound);
        Thread worker = mixer;
        if (worker != null) {
            LockSupport.unpark(worker);
        }
    }

    /** Idempotent; safe from {@code onDetachedFromWindow} and safe to follow with setEnabled. */
    synchronized void release() {
        shutdown();
    }

    // ---- Mixer ---------------------------------------------------------------------------

    private void startMixer() {
        if (audioBroken || running || mixer != null) {
            return;
        }
        running = true;
        pending.clear();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                mix();
            }
        }, "cozy-sfx");
        worker.setDaemon(true);
        mixer = worker;
        worker.start();
    }

    private void shutdown() {
        running = false;
        Thread worker = mixer;
        mixer = null;
        pending.clear();
        if (worker == null || worker == Thread.currentThread()) {
            return;
        }
        LockSupport.unpark(worker);
        try {
            worker.join(400);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (!worker.isAlive()) {
            return;
        }
        // A device that has stopped draining leaves the mixer stuck in write(); pausing the
        // track underneath it is the only thing that lets go. The thread still owns the
        // release, so we never pull the track out from under a live writer.
        AudioTrack stuck = sink;
        if (stuck != null) {
            try {
                stuck.pause();
                stuck.flush();
            } catch (Throwable ignored) {
                // Best effort.
            }
            try {
                worker.join(250);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void mix() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        } catch (Throwable ignored) {
            // Priority is a nicety; a TV that refuses it still gets sound.
        }
        AudioTrack track = build();
        if (track == null) {
            audioBroken = true;
            running = false;
            mixer = null;
            return;
        }
        sink = track;
        try {
            boolean streaming = false;
            long idleSince = System.nanoTime();
            while (running) {
                drain();
                boolean active = anyVoiceActive();
                if (!active && pending.isEmpty()) {
                    if (streaming && System.nanoTime() - idleSince >= IDLE_PARK_NS) {
                        track.pause();
                        track.flush();
                        streaming = false;
                    }
                    if (!streaming) {
                        LockSupport.park(this);
                        continue;
                    }
                } else {
                    idleSince = System.nanoTime();
                    if (!streaming) {
                        track.play();
                        streaming = true;
                    }
                }
                renderBlock();
                if (track.write(pcm, 0, BLOCK, AudioTrack.WRITE_BLOCKING) < 0) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            // Losing the audio device mid-session should never take the game with it.
        } finally {
            sink = null;
            for (Voice voice : voices) {
                voice.active = false;
            }
            try {
                track.pause();
                track.flush();
                track.stop();
            } catch (Throwable ignored) {
                // Best effort.
            }
            track.release();
        }
    }

    private AudioTrack build() {
        try {
            int minimum = AudioTrack.getMinBufferSize(CozySynth.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) {
                minimum = BLOCK * 2 * 8;
            }
            AudioTrack built = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(CozySynth.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(minimum, BLOCK * 2 * 6))
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

    private boolean anyVoiceActive() {
        for (Voice voice : voices) {
            if (voice.active) {
                return true;
            }
        }
        return false;
    }

    private void drain() {
        Sound sound;
        while ((sound = pending.poll()) != null) {
            trigger(sound);
        }
    }

    private void trigger(Sound sound) {
        int id = sound.ordinal();
        float gain = 1f;
        if (sound == Sound.MOVE) {
            // A held D-pad sags the tap towards a whisper; pausing lets it come back up.
            long now = System.nanoTime();
            double spacing = Math.min(1, (now - lastMoveVoice) / 260_000_000.0);
            lastMoveVoice = now;
            gain = (float) (.42 + .58 * spacing * spacing);
        }
        Voice voice = claim(sound);
        if (voice == null) {
            return;
        }
        long seed = CozySynth.mix(++triggerCount * 0x9E3779B97F4A7C15L + id);
        int length = CozySynth.sampleCount(id);
        if (voice.buffer.length < length) {
            voice.buffer = new float[length];
        }
        CozySynth.render(id, seed, voice.buffer);
        voice.length = length;
        voice.position = 0;
        voice.gain = gain;
        voice.move = sound == Sound.MOVE;
        voice.active = true;
    }

    /** Free slot first; otherwise sacrifice a cursor tap, and only then the nearest to finishing. */
    private Voice claim(Sound sound) {
        Voice fallback = null;
        for (Voice voice : voices) {
            if (!voice.active) {
                return voice;
            }
            if (voice.move && sound != Sound.MOVE) {
                return voice;
            }
            int remaining = voice.length - voice.position;
            if (fallback == null || remaining < fallback.length - fallback.position) {
                fallback = voice;
            }
        }
        return fallback;
    }

    private void renderBlock() {
        Arrays.fill(accumulator, 0f);
        for (Voice voice : voices) {
            if (!voice.active) {
                continue;
            }
            int count = Math.min(BLOCK, voice.length - voice.position);
            float gain = voice.gain;
            float[] buffer = voice.buffer;
            int offset = voice.position;
            for (int i = 0; i < count; i++) {
                accumulator[i] += buffer[offset + i] * gain;
            }
            voice.position += count;
            if (voice.position >= voice.length) {
                voice.active = false;
            }
        }
        for (int i = 0; i < BLOCK; i++) {
            pcm[i] = (short) (CozySynth.limit(accumulator[i], CozySynth.SFX_CEILING) * 32767);
        }
    }

    private static final class Voice {
        float[] buffer = new float[0];
        int length;
        int position;
        float gain = 1f;
        boolean move;
        boolean active;
    }
}

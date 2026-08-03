package com.cozygrams.tv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

/**
 * The game's sound effects: eleven distinct synthesised voices mixed by one small polyphonic
 * engine, in stereo, in a room.
 *
 * <h3>Why a mixer instead of a queue of tracks</h3>
 * CozyGrams is a two-player game, and Rose and Sky press buttons at the same time. Playing each
 * effect on its own {@code AudioTrack} and sleeping for its duration means the second player's
 * sound arrives late or not at all. Instead there is a single streaming track and a pool of
 * {@link #VOICES} voice slots which are summed into every output block, so any twelve effects can
 * overlap and nobody's input is ever swallowed by somebody else's.
 *
 * <h3>Two players, not one</h3>
 * Everything that can be per-player is per-player, because a shared scalar in a couch co-op game
 * is an unfairness with a shrug on top. Rose holding a direction used to silence Sky's deliberate
 * single tap — the rate gate and the held-D-pad gain sag were one field each, so the busier player
 * owned them both. They are arrays now, one entry per {@link #ROSE}, {@link #SKY} and
 * {@link #ROOM}. On top of that each player has a pitch ({@link CozySynth#playerTilt}), a place in
 * the stereo field, a directional walk on the cursor tick and their own run of fills, so two people
 * working on one board sound like two people.
 *
 * <p>{@link #play} does no synthesis and never blocks: it writes one int into a lock-free ring and
 * wakes the mixer. Rendering, {@code AudioTrack} construction and every allocation happen on the
 * mixer thread. When nothing has sounded for a moment the track is paused and the thread parks, so
 * an idle living room costs nothing and muting stops it outright.
 *
 * <p>Mix budget: each voice peaks at most {@link CozySynth#VOICE_PEAK}, and the summed bus — room
 * included — is soft-limited to {@link CozySynth#SFX_CEILING}. See {@link CozySynth} for the full
 * accounting.
 */
final class CozySfx {

    /** The ordinals here are the contract shared with {@link CozySynth}'s sound constants. */
    enum Sound { MOVE, FILL, CROSS, CLEAR, HINT, ERROR, WIN, SELECT, LINE, JOIN, NUDGE }

    /** Rose. Player slots index every piece of per-player state in this class. */
    static final int ROSE = 0;
    /** Sky. */
    static final int SKY = 1;
    /** Nobody in particular: a menu, a new controller, the picture finishing. */
    static final int ROOM = 2;
    private static final int SLOTS = 3;

    /** Block size in stereo <em>frames</em>; the PCM buffer is twice this. */
    private static final int BLOCK = 256;
    private static final int VOICES = 12;
    /** A power of two so the ring can mask instead of divide. */
    private static final int QUEUE = 64;

    /** Anything faster than this on the D-pad is not audible as separate taps anyway. */
    private static final long MOVE_MIN_GAP_NS = 40_000_000L;
    /** Two ticks closer together than this are one gesture, so the pitch walk continues. */
    private static final long WALK_LINK_NS = 400_000_000L;
    /** Fills closer together than this are one run up the pentatonic. */
    private static final long RUN_LINK_NS = 900_000_000L;
    /** Long enough to let the room's measured 0.84 s tail finish before the track is flushed. */
    private static final long IDLE_PARK_NS = 1_600_000_000L;

    /** 64 samples — 1.45 ms — of raised cosine before a live voice is taken from underneath. */
    private static final int STEAL_RELEASE = 64;
    /** How far the cursor tick may drift from D5 in either direction, in semitones. */
    private static final int MAX_WALK = 3;
    /** How far a run of fills may climb the pentatonic. */
    private static final int MAX_RUN = 5;
    /** How far out into the room a player sits. */
    private static final double PLAYER_PAN = .42;
    /** The room, quietly: enough to give the effects a place, not enough to wash them. */
    private static final double SFX_WET = .13;

    // ---- Request words ---------------------------------------------------------------
    //
    // A request is one int: sound ordinal in bits 0-3, player slot in 4-5, variant in 6-9.
    // Packing it means the queue is a plain int[] and play() allocates nothing at all — the
    // old ArrayBlockingQueue<Sound> could not carry the player, which is how the two blockers
    // in this class got in.

    /** No modifier: the sound as written for this player. */
    private static final int PLAIN = 0;
    /** The cursor did not step, it teleported across the board — a fifth down. */
    private static final int WRAP = 1;
    /** A switch turned off rather than on — a major third down. */
    private static final int OFF = 2;
    /** {@code WALK + n}: position n in a directional walk or in a run of fills. */
    private static final int WALK = 4;

    /** F major pentatonic from F4. A run of fills climbs it; a run of clears walks back down. */
    private static final double[] RUN = {349.23, 392.00, 440.00, 523.25, 587.33, 698.46};
    /** 2^(-4/12): the major third down that every switch in the world uses to mean "off". */
    private static final double MAJOR_THIRD_DOWN = .7937005259840998;

    private static final float[] EMPTY = new float[0];

    private final int[] queue = new int[QUEUE];
    private final Voice[] voices = new Voice[VOICES];
    private final float[] accumulator = new float[BLOCK * 2];
    private final short[] pcm = new short[BLOCK * 2];
    private final CozySynth.Room room = new CozySynth.Room(SFX_WET);

    private volatile boolean enabled = true;
    private volatile boolean running;
    private volatile boolean audioBroken;
    private volatile Thread mixer;
    private volatile AudioTrack sink;

    /** Ring cursors. Only callers advance {@code head}; only the mixer advances {@code tail}. */
    private volatile int head;
    private volatile int tail;

    // Caller-thread state: the rate gate, the cursor's pitch walk and the fill run.
    private final long[] lastTickAt = new long[SLOTS];
    private final int[] walkStep = new int[SLOTS];
    private final long[] lastRunAt = new long[SLOTS];
    private final int[] runStep = new int[SLOTS];

    // Mixer-thread state.
    private final long[] lastTickVoiceAt = new long[SLOTS];
    private long triggerCount;
    private float[] winBank;
    private float[] joinBank;

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

    // ---- Playing -----------------------------------------------------------------------

    /** A room-wide event that belongs to nobody: a menu press, a win, a controller arriving. */
    void play(Sound sound) {
        play(sound, ROOM);
    }

    /**
     * Called straight from {@code onKeyDown}. Allocation free, lock free enough to be harmless on
     * the UI thread, and it never waits for audio.
     *
     * <p>{@code who} is {@link #ROSE}, {@link #SKY} or {@link #ROOM}. It decides the pitch, the
     * place in the room, and — for {@code MOVE}, {@code FILL} and {@code CLEAR} — whose run or
     * walk this press belongs to.
     */
    void play(Sound sound, int who) {
        if (sound == null) {
            return;
        }
        int slot = slotOf(who);
        switch (sound) {
            case MOVE:
                if (allowTick(slot)) {
                    send(sound, slot, PLAIN);
                }
                break;
            case FILL:
                send(sound, slot, climbRun(slot));
                break;
            case CLEAR:
                send(sound, slot, unclimbRun(slot));
                break;
            default:
                send(sound, slot, PLAIN);
                break;
        }
    }

    /**
     * The cursor tick, told where the cursor went.
     *
     * <p>A run of ticks in one direction walks up or down a semitone at a time within three
     * semitones of D5, so holding right sounds like going somewhere and holding left sounds like
     * coming back. {@code wrapped} means the cursor did not step at all but reappeared on the far
     * side of the board — {@code GameState.move} wraps — and on a 20x20 board that is otherwise
     * completely invisible, so it gets its own tick a fifth down.
     */
    void move(int who, int dx, int dy, boolean wrapped) {
        int slot = slotOf(who);
        long now = System.nanoTime();
        boolean linked = now - lastTickAt[slot] < WALK_LINK_NS;
        if (!allowTick(slot)) {
            return;
        }
        if (wrapped) {
            walkStep[slot] = 0;
            send(Sound.MOVE, slot, WRAP);
            return;
        }
        int direction = dx + dy > 0 ? 1 : -1;
        walkStep[slot] = linked ? clamp(walkStep[slot] + direction, -MAX_WALK, MAX_WALK) : 0;
        send(Sound.MOVE, slot, WALK + MAX_WALK + walkStep[slot]);
    }

    /** A switch in the cozy corner. Turning something on rises; turning it off falls. */
    void select(int who, boolean on) {
        send(Sound.SELECT, slotOf(who), on ? PLAIN : OFF);
    }

    /** Idempotent; safe from {@code onDetachedFromWindow} and safe to follow with setEnabled. */
    synchronized void release() {
        shutdown();
    }

    private static int slotOf(int who) {
        return who == ROSE || who == SKY ? who : ROOM;
    }

    private static int clamp(int value, int low, int high) {
        return value < low ? low : (value > high ? high : value);
    }

    /** The per-player rate gate: two players scrubbing a board must not machine-gun the mixer. */
    private boolean allowTick(int slot) {
        long now = System.nanoTime();
        if (now - lastTickAt[slot] < MOVE_MIN_GAP_NS) {
            return false;
        }
        lastTickAt[slot] = now;
        return true;
    }

    private int climbRun(int slot) {
        long now = System.nanoTime();
        runStep[slot] = now - lastRunAt[slot] < RUN_LINK_NS
                ? Math.min(MAX_RUN, runStep[slot] + 1) : 0;
        lastRunAt[slot] = now;
        return WALK + runStep[slot];
    }

    /** Undoing a run un-plays it: the clear sounds where the run reached, then backs off. */
    private int unclimbRun(int slot) {
        long now = System.nanoTime();
        if (now - lastRunAt[slot] >= RUN_LINK_NS) {
            runStep[slot] = 0;
        }
        lastRunAt[slot] = now;
        int at = runStep[slot];
        runStep[slot] = Math.max(0, at - 1);
        return WALK + at;
    }

    /**
     * Single producer, single consumer: every caller is the UI thread and the mixer is the only
     * reader, so publishing {@code head} after the slot is written is all the ordering needed.
     */
    private void send(Sound sound, int slot, int variant) {
        if (!enabled || !running) {
            return;
        }
        int at = head;
        if (at - tail >= QUEUE) {
            return;
        }
        queue[at & (QUEUE - 1)] = sound.ordinal() | (slot << 4) | (variant << 6);
        head = at + 1;
        Thread worker = mixer;
        if (worker != null) {
            LockSupport.unpark(worker);
        }
    }

    // ---- Pitch and place ---------------------------------------------------------------

    /**
     * The frequency multiplier for one request: who pressed it, and where in a run it sits.
     *
     * <p>Package-private because it is the whole of the two-player sound identity and
     * {@link CozyAudioTest} measures it directly.
     */
    static double tiltFor(int sound, int who, int variant) {
        return identityTilt(sound, who) * stepTilt(sound, variant);
    }

    private static double identityTilt(int sound, int who) {
        if (roomWide(sound)) {
            return 1;
        }
        double tilt = CozySynth.playerTilt(who);
        if (sound == CozySynth.CROSS || sound == CozySynth.CLEAR) {
            // Noise has no pitch to move, only a colour, and a whole fifth turns the pencil into
            // a different pencil. 45% of the interval is enough to tell two hands apart while
            // both still sound like graphite on paper.
            return 1 + (tilt - 1) * .45;
        }
        return tilt;
    }

    private static double stepTilt(int sound, int variant) {
        if (variant == WRAP) {
            return 2.0 / 3.0;
        }
        if (variant == OFF) {
            return MAJOR_THIRD_DOWN;
        }
        if (variant < WALK) {
            return 1;
        }
        int step = variant - WALK;
        if (sound == CozySynth.MOVE) {
            return Math.pow(2, (step - MAX_WALK) / 12.0);
        }
        return RUN[Math.min(step, RUN.length - 1)] / RUN[0];
    }

    /** Events belonging to the board or the room, not a person: centred, and never tilted. */
    private static boolean roomWide(int sound) {
        return sound == CozySynth.WIN || sound == CozySynth.JOIN
                || sound == CozySynth.HINT || sound == CozySynth.LINE;
    }

    private static double panFor(int sound, int who) {
        if (roomWide(sound) || who == ROOM) {
            return 0;
        }
        return who == SKY ? PLAYER_PAN : -PLAYER_PAN;
    }

    // ---- Mixer ---------------------------------------------------------------------------

    private void startMixer() {
        if (audioBroken || running || mixer != null) {
            return;
        }
        running = true;
        head = 0;
        tail = 0;
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
        head = 0;
        tail = 0;
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
        prepareBanks();
        try {
            boolean streaming = false;
            long idleSince = System.nanoTime();
            while (running) {
                drain();
                if (!anyVoiceActive() && tail == head) {
                    if (streaming && System.nanoTime() - idleSince >= IDLE_PARK_NS) {
                        track.pause();
                        track.flush();
                        room.clear();
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
                if (track.write(pcm, 0, BLOCK * 2, AudioTrack.WRITE_BLOCKING) < 0) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            // Losing the audio device mid-session should never take the game with it.
        } finally {
            sink = null;
            for (Voice voice : voices) {
                voice.active = false;
                voice.release = 0;
                voice.pendingRequest = -1;
                voice.buffer = EMPTY;
                voice.shared = false;
            }
            winBank = null;
            joinBank = null;
            room.clear();
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

    /**
     * Renders the two long sounds once, up front, off the write path.
     *
     * <p>The SFX track holds about 35 ms of audio and each block buys back 5.8 ms of it, so a
     * voice that takes longer than that to synthesise is an underrun. Measured on a desktop JVM
     * WIN takes 13 ms and JOIN 7 ms; an Android TV A53 core is comfortably 8x slower than that,
     * which puts WIN at over 100 ms of synthesis at the exact instant the picture is finished.
     * Both play at most once per puzzle and once per controller, so the loss of per-trigger seed
     * variation is inaudible and 500 KB of resident float — released again the moment sound is
     * switched off — is a cheap way to never stutter on the best moment in the game.
     */
    private void prepareBanks() {
        winBank = renderBank(CozySynth.WIN);
        joinBank = renderBank(CozySynth.JOIN);
    }

    private float[] renderBank(int sound) {
        float[] bank = new float[CozySynth.sampleCount(sound)];
        CozySynth.render(sound, CozySynth.mix(sound * 7919L + 0xC0FFEEL), bank);
        return bank;
    }

    private AudioTrack build() {
        try {
            int minimum = AudioTrack.getMinBufferSize(CozySynth.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) {
                minimum = BLOCK * 4 * 8;
            }
            AudioTrack built = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(CozySynth.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build())
                    .setBufferSizeInBytes(Math.max(minimum, BLOCK * 4 * 6))
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
            if (voice.active || voice.release > 0) {
                return true;
            }
        }
        return false;
    }

    private void drain() {
        while (tail != head) {
            int request = queue[tail & (QUEUE - 1)];
            tail = tail + 1;
            trigger(request);
        }
    }

    private void trigger(int request) {
        int id = request & 15;
        Voice voice = claim(id);
        if (voice == null) {
            return;
        }
        float gain = id == CozySynth.MOVE ? tickGain((request >> 4) & 3) : 1f;
        if (voice.active) {
            // Every slot was busy. Ramping the old voice out over 1.45 ms and starting the new
            // one after it costs a twentieth of a video frame; cutting it dead, which is what
            // used to happen, is a step to silence from wherever the waveform happened to be.
            // Measured on a bus of nothing but nudges, a one-sample cut raises the worst sample
            // step from 0.058 to 0.089 — and the ramp leaves it at 0.058 exactly.
            voice.release = STEAL_RELEASE;
            voice.pendingRequest = request;
            voice.pendingGain = gain;
            return;
        }
        synthesise(voice, request, gain);
    }

    /**
     * A held D-pad sags the tap towards a whisper; pausing lets it come back up. The floor used
     * to be 0.42, which put a held tick 7 dB below a deliberate one and left it inaudible under
     * the music — the point of the sag is to stop a scrub chattering, not to erase it.
     */
    private float tickGain(int slot) {
        long now = System.nanoTime();
        double spacing = Math.min(1, (now - lastTickVoiceAt[slot]) / 260_000_000.0);
        lastTickVoiceAt[slot] = now;
        return (float) (.60 + .40 * spacing * spacing);
    }

    /**
     * Free slot first; otherwise sacrifice a cursor tap, and only then the nearest to finishing.
     *
     * <p>The three passes used to be one, which meant that as soon as any voice held an in-flight
     * cursor tap every other sound took it — even with eleven slots standing empty.
     */
    private Voice claim(int sound) {
        for (Voice voice : voices) {
            if (!voice.active && voice.release == 0) {
                return voice;
            }
        }
        if (sound != CozySynth.MOVE) {
            for (Voice voice : voices) {
                if (voice.move && voice.release == 0) {
                    return voice;
                }
            }
        }
        Voice nearest = null;
        for (Voice voice : voices) {
            if (voice.release > 0) {
                continue;
            }
            if (nearest == null
                    || voice.length - voice.position < nearest.length - nearest.position) {
                nearest = voice;
            }
        }
        return nearest;
    }

    private void synthesise(Voice voice, int request, float gain) {
        int id = request & 15;
        int who = (request >> 4) & 3;
        int variant = (request >> 6) & 15;
        float[] bank = id == CozySynth.WIN ? winBank : (id == CozySynth.JOIN ? joinBank : null);
        if (bank != null) {
            voice.buffer = bank;
            voice.shared = true;
        } else {
            if (voice.shared) {
                // The banks are read-only and shared between slots, so never render over one.
                voice.buffer = EMPTY;
                voice.shared = false;
            }
            int length = CozySynth.sampleCount(id);
            if (voice.buffer.length < length) {
                voice.buffer = new float[length];
            }
            long seed = CozySynth.mix(++triggerCount * 0x9E3779B97F4A7C15L + id);
            CozySynth.render(id, seed, voice.buffer, tiltFor(id, who, variant));
        }
        double pan = panFor(id, who);
        voice.panL = (float) CozySynth.panLeft(pan);
        voice.panR = (float) CozySynth.panRight(pan);
        voice.length = CozySynth.sampleCount(id);
        voice.position = 0;
        voice.gain = gain;
        voice.move = id == CozySynth.MOVE;
        voice.active = true;
    }

    private void renderBlock() {
        Arrays.fill(accumulator, 0f);
        for (Voice voice : voices) {
            int from = 0;
            if (voice.release > 0) {
                from = fadeOutStolenVoice(voice);
                if (voice.release == 0 && voice.pendingRequest >= 0) {
                    synthesise(voice, voice.pendingRequest, voice.pendingGain);
                    voice.pendingRequest = -1;
                }
            }
            if (voice.active) {
                mixVoice(voice, from);
            }
        }
        room.process(accumulator, 0, BLOCK);
        for (int i = 0; i < BLOCK * 2; i++) {
            pcm[i] = (short) (CozySynth.limit(accumulator[i], CozySynth.SFX_CEILING) * 32767);
        }
    }

    /** Rides a stolen voice down a raised cosine to exact silence. Returns the frames used. */
    private int fadeOutStolenVoice(Voice voice) {
        int count = Math.min(BLOCK, voice.release);
        int available = Math.max(0, voice.length - voice.position);
        for (int i = 0; i < count; i++) {
            double remaining = voice.release - i;
            double ramp = .5 - .5 * Math.cos(Math.PI * remaining / STEAL_RELEASE);
            double value = (i < available ? voice.buffer[voice.position + i] : 0f)
                    * voice.gain * ramp;
            accumulator[i * 2] += (float) (value * voice.panL);
            accumulator[i * 2 + 1] += (float) (value * voice.panR);
        }
        voice.position = Math.min(voice.length, voice.position + count);
        voice.release -= count;
        if (voice.release <= 0) {
            voice.release = 0;
            voice.active = false;
        }
        return count;
    }

    private void mixVoice(Voice voice, int from) {
        int count = Math.min(BLOCK - from, voice.length - voice.position);
        if (count <= 0) {
            return;
        }
        float[] buffer = voice.buffer;
        int offset = voice.position;
        float gain = voice.gain;
        float left = voice.panL;
        float right = voice.panR;
        for (int i = 0; i < count; i++) {
            float value = buffer[offset + i] * gain;
            accumulator[(from + i) * 2] += value * left;
            accumulator[(from + i) * 2 + 1] += value * right;
        }
        voice.position += count;
        if (voice.position >= voice.length) {
            voice.active = false;
        }
    }

    /**
     * Drives the bus by hand and hands back interleaved stereo — no {@code AudioTrack}, no thread.
     *
     * <p>Request {@code i} is fired just before block {@code atBlock[i]}, which is the only way to
     * test the steal path honestly: a voice taken at position 0 is silent and cannot click, so a
     * pile-up that arrives all at once proves nothing. Exists so {@link CozyAudioTest} can hold
     * the mixer to the same standard as the voices it mixes.
     */
    float[] renderBusForTest(Sound[] sounds, int[] who, int[] atBlock, int blocks) {
        prepareBanks();
        float[] out = new float[blocks * BLOCK * 2];
        for (int b = 0; b < blocks; b++) {
            for (int i = 0; i < sounds.length; i++) {
                if (atBlock[i] == b) {
                    trigger(sounds[i].ordinal() | (slotOf(who[i]) << 4));
                }
            }
            renderBlock();
            System.arraycopy(accumulator, 0, out, b * BLOCK * 2, BLOCK * 2);
        }
        return out;
    }

    private static final class Voice {
        float[] buffer = EMPTY;
        /** True while {@code buffer} points at a shared, read-only bank. */
        boolean shared;
        int length;
        int position;
        float gain = 1f;
        float panL = .70710678f;
        float panR = .70710678f;
        boolean move;
        boolean active;
        /** Frames left of the de-click ramp before {@link #pendingRequest} takes this slot. */
        int release;
        int pendingRequest = -1;
        float pendingGain = 1f;
    }
}

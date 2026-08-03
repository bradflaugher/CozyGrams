package com.cozygrams.tv;

import java.util.Arrays;

/**
 * "Rain on the Window" — the CozyGrams score, composed here in code and synthesised sample by
 * sample so the APK never has to carry an audio file.
 *
 * <h3>The piece</h3>
 * <ul>
 *   <li><b>Key</b> F major. The melody stays inside the F major pentatonic (F G A C D), which
 *       means any two layers that happen to overlap are consonant by construction — that is what
 *       lets the arrangement improvise ornaments without ever landing on a sour note.</li>
 *   <li><b>Tempo</b> 62 BPM, 4/4, written on an eighth-note grid. One bar is 3.87 s.</li>
 *   <li><b>Form</b> 56 bars, about <b>3 minutes 37 seconds</b> before the written material comes
 *       round again:
 *       <ol>
 *         <li><b>A — "Window Light"</b> (16 bars): music box over a soft pad and a slow bass.</li>
 *         <li><b>B — "Hearth"</b> (16 bars): felt piano joins and doubles the tune, the pad opens
 *             up, the bass walks, a few raindrops appear. The warm middle of the piece.</li>
 *         <li><b>C — "Resting"</b> (8 bars): almost everything drops away. Two chords, a pad, a
 *             single bell every other bar and rain. Deliberate emptiness.</li>
 *         <li><b>A' — "Window Light, later"</b> (16 bars): the opening returns ornamented, with
 *             the felt piano shadowing it an octave down.</li>
 *       </ol>
 *   </li>
 *   <li><b>Variation</b> a seeded stream keyed to the cycle number decides which notes get
 *       ornamented, dropped or ghosted an octave down, and it opens up as the evening goes on:
 *       by the fourth pass a quarter of the notes may be left out and ornaments are four times
 *       as likely as on the first.</li>
 *   <li><b>Key changes</b> each pass of the form moves: F, then Bb, then Eb, then C, and round.
 *       That makes the true repeat <b>14 minutes 28 seconds</b> rather than 3:37, and it is a real
 *       long-form plan — IV, then bVII, then the dominant, which wants to go home to F. Every one
 *       of the four keys contains both F and C, which is what keeps the sound effects (Rose's
 *       fill is F4, Sky's is C5) consonant with the music no matter where the piece has got to.
 *       The pad glides between keys over about a second, so it is a modulation, not a jump.</li>
 *   <li><b>Layers</b> bells / felt piano / pad / bass / rain, each with its own gain that glides
 *       over roughly two seconds, so sections fade into one another rather than switch.</li>
 *   <li><b>The room</b> the whole arrangement is placed in stereo — the four pad voices spread
 *       across the field, bells slightly left, piano slightly right, every raindrop somewhere of
 *       its own — and fed to a small {@link CozySynth.Room}. A piece called "Rain on the Window"
 *       played bone dry down the middle of one speaker was never going to be true.</li>
 * </ul>
 *
 * <h3>Reacting to the room</h3>
 * {@link #setIntensity} follows the picture filling in, {@link #celebrate} answers a finished
 * puzzle, {@link #sparkle} answers a finished line, {@link #setPresence} answers a second player
 * sitting down, and {@link #nudge} keeps the arrangement awake — left alone for a minute the
 * melody recedes to pad and bass and the first keypress brings it back.
 *
 * <p>No Android types are referenced here, so the whole arrangement can be rendered and inspected
 * from a unit test. Rendering is purely sequential and sample-by-sample, which means the output is
 * identical no matter how the caller chops it into blocks.
 */
final class CozyScore {

    // ---- Form ------------------------------------------------------------------------

    private static final double BPM = 62;
    private static final int STEPS_PER_BAR = 8;
    private static final int BARS = 56;

    private static final int SECTION_A = 0;
    private static final int SECTION_B = 1;
    private static final int SECTION_C = 2;
    private static final int SECTION_A2 = 3;

    private static final int LAYER_BELL = 0;
    private static final int LAYER_PIANO = 1;
    private static final int LAYER_PAD = 2;
    private static final int LAYER_BASS = 3;
    private static final int LAYER_DROPS = 4;
    private static final int LAYERS = 5;

    /** Per-section layer balance: bell, piano, pad, bass, raindrops. */
    private static final double[][] BALANCE = {
            {1.00, 0.00, 0.55, 0.62, 0.00},   // A  — clear and simple
            {0.72, 0.62, 0.80, 0.75, 0.34},   // B  — warm and full
            {0.44, 0.00, 0.66, 0.28, 0.52},   // C  — almost nothing
            {1.00, 0.32, 0.60, 0.66, 0.22}    // A' — the opening, remembered
    };

    /** {bass, four chord tones} as MIDI numbers. */
    private static final int[][] CHORD_A = {
            {41, 53, 57, 60, 65},   // F        F2  | F  A  C  F
            {50, 53, 57, 60, 62},   // Dm7      D3  | F  A  C  D
            {46, 53, 57, 60, 62},   // Bb6/9    Bb2 | F  A  C  D
            {48, 52, 55, 60, 64},   // C        C3  | E  G  C  E
            {50, 53, 57, 60, 64},   // Dm9      D3  | F  A  C  E
            {43, 53, 58, 62, 65},   // Gm7      G2  | F  Bb D  F
            {46, 53, 57, 60, 65},   // Bbmaj7   Bb2 | F  A  C  F
            {48, 53, 55, 60, 67}    // Csus4    C3  | F  G  C  G
    };

    private static final int[][] CHORD_B = {
            {46, 53, 57, 62, 65},   // Bbmaj7   Bb2 | F  A  D  F
            {45, 53, 57, 60, 65},   // F/A      A2  | F  A  C  F
            {43, 53, 58, 62, 65},   // Gm7      G2  | F  Bb D  F
            {48, 52, 55, 60, 64},   // C        C3  | E  G  C  E
            {46, 53, 57, 62, 65},   // Bbmaj7   Bb2 | F  A  D  F
            {45, 52, 57, 60, 64},   // Am7      A2  | E  A  C  E
            {50, 53, 57, 62, 65},   // Dm7      D3  | F  A  D  F
            {48, 53, 55, 60, 67}    // Csus4    C3  | F  G  C  G
    };

    /**
     * The reharmonisation. Every section is its eight bars played twice, and the second time
     * round these chords go under the same melody instead — the tune is remembered, the ground
     * it stands on has moved. All diatonic to F, all still holding F or A and C, so the
     * pentatonic melody floats over either set.
     */
    private static final int[][] CHORD_A2 = {
            {50, 53, 57, 60, 65},   // Dm7      D3  | F  A  C  F
            {46, 53, 57, 62, 65},   // Bbmaj7   Bb2 | F  A  D  F
            {43, 53, 58, 62, 65},   // Gm7      G2  | F  Bb D  F
            {45, 52, 57, 60, 64},   // Am7      A2  | E  A  C  E
            {41, 53, 57, 60, 67},   // F6/9     F2  | F  A  C  G
            {46, 53, 57, 62, 65},   // Bbmaj7   Bb2 | F  A  D  F
            {43, 53, 57, 62, 65},   // Gm9      G2  | F  A  D  F
            {48, 53, 55, 62, 65}    // C11      C3  | F  G  D  F
    };

    private static final int[][] CHORD_B2 = {
            {43, 53, 58, 62, 65},   // Gm7      G2  | F  Bb D  F
            {46, 53, 57, 60, 65},   // Bbmaj7   Bb2 | F  A  C  F
            {50, 53, 57, 60, 64},   // Dm9      D3  | F  A  C  E
            {41, 53, 57, 60, 67},   // F6/9     F2  | F  A  C  G
            {45, 52, 57, 60, 64},   // Am7      A2  | E  A  C  E
            {46, 53, 57, 62, 65},   // Bbmaj7   Bb2 | F  A  D  F
            {43, 53, 57, 62, 65},   // Gm9      G2  | F  A  D  F
            {48, 53, 55, 62, 67}    // C11      C3  | F  G  D  G
    };

    private static final int[][] CHORD_C = {
            {41, 53, 55, 60, 64},   // Fadd9    F2  | F  G  C  E
            {50, 53, 57, 60, 64}    // Dm9      D3  | F  A  C  E
    };

    /**
     * Where each pass of the form sits: F, Bb, Eb, C. Chosen so that consecutive passes and
     * passes two apart are always in different keys — that is what the self-similarity measure
     * was picking up — and so that F and C are in all four, which keeps the sound effects in
     * tune with the music. Transposing by +2 (G) would have been the obvious fourth choice and
     * is the one to avoid: it puts an F sharp under Rose's F4 fill.
     */
    private static final int[] TRANSPOSE = {0, 5, -2, -5};

    // Melodies: eight bars of eight eighth-notes, MIDI numbers, -1 is a rest.

    private static final int[] MEL_A = {
            81, -1, -1, 84, -1, 81, -1, -1,
            79, -1, 77, -1, -1, 74, -1, -1,
            77, -1, -1, 81, -1, 84, -1, -1,
            86, -1, -1, -1, 84, -1, -1, -1,
            81, -1, 84, -1, -1, 81, -1, 79,
            77, -1, -1, 74, -1, -1, 77, -1,
            79, -1, -1, 81, -1, -1, 84, -1,
            84, -1, -1, -1, -1, -1, -1, -1
    };

    private static final int[] MEL_B = {
            77, -1, 79, -1, 81, -1, -1, -1,
            84, -1, -1, 81, -1, -1, 79, -1,
            77, -1, -1, -1, 74, -1, 77, -1,
            79, -1, -1, -1, -1, -1, -1, -1,
            81, -1, -1, 84, -1, -1, 86, -1,
            84, -1, -1, 81, -1, 79, -1, -1,
            77, -1, 81, -1, -1, 74, -1, -1,
            79, -1, -1, -1, -1, -1, -1, -1
    };

    private static final int[] MEL_C = {
            77, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, 84, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1,
            81, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, 79, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1
    };

    private static final int[] MEL_A2 = {
            81, -1, 84, -1, -1, 81, -1, -1,
            79, 77, -1, -1, 74, -1, -1, -1,
            77, -1, 81, -1, 84, -1, -1, -1,
            86, -1, 84, -1, -1, -1, -1, -1,
            -1, -1, 81, -1, 84, -1, 81, -1,
            79, -1, -1, 77, -1, 74, -1, -1,
            77, -1, 79, -1, 81, -1, 84, -1,
            86, -1, -1, 84, -1, -1, -1, -1
    };

    /** Rain: F major pentatonic, two octaves above the tune. */
    private static final int[] DROPS = {84, 86, 89, 91, 93};
    /** The celebration countermelody sits above the music box, still inside the pentatonic. */
    private static final int[] COUNTER = {84, 86, 89, 91};

    // ---- Voice model -----------------------------------------------------------------

    /** Control-rate grid, in samples: how often layer gains and the celebration lift move. */
    private static final int CONTROL = 64;

    private static final int NOTES = 24;
    private static final int PAD_SLOTS = 5;
    private static final int PARTIALS = 4;

    /** How wide each pad voice sits. The four of them are the width of the whole piece. */
    private static final double[] PAD_PAN = {-.55, -.20, .20, .55, 0};
    /** Bells just left of centre, felt piano just right, so the two tunes are not stacked. */
    private static final double[] LAYER_PAN = {-.12, .18, 0, 0, 0};
    private static final double MUSIC_WET = .22;
    /**
     * Measured at -55 dBFS with the rain layer half open and -50 dBFS wide open. A one-pole at
     * 0.24 throws away most of what you feed it — the obvious 0.0045 for "about -56 dBFS" turns
     * out to be -65, which is under the noise floor of every television ever made.
     */
    private static final double RAIN_GAIN = .014;
    /** Seconds of stillness before the arrangement starts to recede, and before it is hushed. */
    private static final double HUSH_START_SECONDS = 25;
    private static final double HUSH_FULL_SECONDS = 55;

    private static final class Note {
        boolean on;
        int layer;
        final double[] phase = new double[PARTIALS];
        final double[] step = new double[PARTIALS];
        final double[] amp = new double[PARTIALS];
        final double[] decay = new double[PARTIALS];
        int used;
        boolean fm;
        double fmPhase;
        double fmStep;
        double fmDepth;
        double fmDecay;
        double gain;
        double panL;
        double panR;
        /** Attack ramp position, 0 to 1; shaped by a raised cosine so no note ever ticks. */
        double attack;
        double attackStep;
        int remaining;
        int fade;
    }

    private static final class PadSlot {
        double freq;
        double target;
        double level;
        double levelTarget;
        double p1;
        double p2;
        double p3;
        double lfo;
        double lfoStep;
        /** The breathing rate as written; {@link #hush} slows it down from here. */
        double baseLfoStep;
        double panL;
        double panR;
    }

    // ---- State -----------------------------------------------------------------------

    private final int sampleRate;
    private final long seed;
    private final int stepSamples;
    private final Note[] notes = new Note[NOTES];
    private final PadSlot[] pad = new PadSlot[PAD_SLOTS];

    private final double[] layer = new double[LAYERS];
    private final double[] layerTarget = new double[LAYERS];

    private long samplePos;
    private long nextStepAt;
    private int step;
    private int cycle;
    private int transpose;

    private volatile boolean celebrateRequested;
    private volatile boolean sparkleRequested;
    private volatile boolean nudgeRequested;
    private volatile float intensity = .35f;
    private volatile int presence = 1;
    private int celebrateLeft;
    private int sparkleLeft;
    private long samplesSinceNudge;
    private double hush;
    private double lift;
    private double fadeIn;
    private double master;
    private double rainLeft;
    private double rainRight;
    private double rainLfo;
    private final double rainLfoStep;
    private final double layerGlide;
    private final double liftGlide;
    private final CozySynth.Room room = new CozySynth.Room(MUSIC_WET);

    CozyScore(int sampleRate, long seed) {
        this.sampleRate = sampleRate;
        this.seed = seed;
        this.stepSamples = (int) (sampleRate * 60.0 / BPM / 2);
        this.layerGlide = 1 - Math.exp(-CONTROL / (2.0 * sampleRate));
        this.liftGlide = 1 - Math.exp(-CONTROL / (.45 * sampleRate));
        this.rainLfoStep = .07 / sampleRate;
        for (int i = 0; i < NOTES; i++) {
            notes[i] = new Note();
        }
        for (int i = 0; i < PAD_SLOTS; i++) {
            pad[i] = new PadSlot();
            pad[i].freq = CozySynth.hz(CHORD_A[0][Math.min(i, 3) + 1]);
            pad[i].target = pad[i].freq;
            pad[i].baseLfoStep = (.045 + .022 * i) / sampleRate;
            pad[i].lfoStep = pad[i].baseLfoStep;
            pad[i].panL = CozySynth.panLeft(PAD_PAN[i]);
            pad[i].panR = CozySynth.panRight(PAD_PAN[i]);
        }
        System.arraycopy(BALANCE[SECTION_A], 0, layerTarget, 0, LAYERS);
    }

    /** Lifts the arrangement for a few seconds — brighter register, countermelody, fuller pad. */
    void celebrate() {
        celebrateRequested = true;
    }

    /**
     * A finished line. Adds a second of countermelody without touching the balance, so the music
     * gets <em>prettier</em> rather than louder — forty completed lines on one board is forty of
     * these, and forty little swells would be exhausting.
     */
    void sparkle() {
        sparkleRequested = true;
    }

    /**
     * Someone did something. Resets the idle clock behind {@link #hush}.
     *
     * <p>Deliberately counted in samples on the audio thread rather than in milliseconds from the
     * UI: the view stops posting frames when the room goes still, which is exactly the moment
     * this has to keep counting.
     */
    void nudge() {
        nudgeRequested = true;
    }

    /** One player or two. With two, the pad opens a fifth voice and the bass walks in halves. */
    void setPresence(int players) {
        presence = players < 1 ? 1 : (players > 2 ? 2 : players);
    }

    /**
     * How far along the picture is: 0 keeps the score at its sparsest, 1 brings the felt piano,
     * the rain and an octave shimmer on the melody forward. Safe to call from any thread.
     */
    void setIntensity(float value) {
        intensity = value < 0 ? 0 : (value > 1 ? 1 : value);
    }

    /**
     * Winds the fade-in back part way, so music that is being switched on again arrives rather
     * than appears. A fresh score fades up over 5.2 s, which is right on a cold start and feels
     * broken on a settings toggle; from 0.35 the ramp is about 1.7 s.
     *
     * <p>Only safe to call while no render thread is running, which is the only state
     * {@link CozyMusic#start} ever calls it from.
     */
    void resumeFade() {
        fadeIn = Math.min(fadeIn, .35);
    }

    /** How long the written form runs before it comes round again, in seconds. */
    static double formSeconds(int sampleRate) {
        return BARS * STEPS_PER_BAR * (int) (sampleRate * 60.0 / BPM / 2) / (double) sampleRate;
    }

    // ---- Rendering -------------------------------------------------------------------

    /**
     * Fills {@code count} interleaved stereo frames starting at frame {@code offset} — so
     * {@code out} must hold {@code 2 * (offset + count)} floats.
     */
    void render(float[] out, int offset, int count) {
        Arrays.fill(out, offset * 2, (offset + count) * 2, 0f);
        if (celebrateRequested) {
            celebrateRequested = false;
            celebrateLeft = (int) (4.6 * sampleRate);
        }
        if (sparkleRequested) {
            sparkleRequested = false;
            sparkleLeft = (int) (1.4 * sampleRate);
        }
        int done = 0;
        while (done < count) {
            if (samplePos >= nextStepAt) {
                schedule();
                nextStepAt += stepSamples;
            }
            // Sub-chunks are cut on absolute sample positions — the note grid and a fixed
            // control-rate grid — never on the caller's block size, so the piece sounds the
            // same whatever buffer size the device happens to hand us.
            long inControl = samplePos % CONTROL;
            if (inControl == 0) {
                advanceControl();
            }
            int chunk = (int) Math.min(Math.min(count - done, nextStepAt - samplePos),
                    CONTROL - inControl);
            if (chunk <= 0) {
                chunk = 1;
            }
            renderChunk(out, offset + done, chunk);
            done += chunk;
            samplePos += chunk;
        }
        room.process(out, offset, count);
    }

    /**
     * One control tick. Layer gains glide with a two-second constant so sections dissolve into
     * one another; the celebration lift moves about four times faster.
     */
    private void advanceControl() {
        double liftTarget = celebrateLeft > 0 ? 1 : 0;
        lift += (liftTarget - lift) * liftGlide;
        if (celebrateLeft > 0) {
            celebrateLeft -= CONTROL;
        }
        if (sparkleLeft > 0) {
            sparkleLeft -= CONTROL;
        }
        if (nudgeRequested) {
            nudgeRequested = false;
            samplesSinceNudge = 0;
        }
        samplesSinceNudge += CONTROL;
        double still = samplesSinceNudge / (double) sampleRate;
        hush = clamp((still - HUSH_START_SECONDS) / (HUSH_FULL_SECONDS - HUSH_START_SECONDS));
        for (int l = 0; l < LAYERS; l++) {
            layer[l] += (layerTarget[l] - layer[l]) * layerGlide;
        }
        if (fadeIn < 1) {
            fadeIn = Math.min(1, fadeIn + CONTROL / (2.6 * sampleRate));
        }
        master = fadeIn * fadeIn * (1 + .12 * lift);
    }

    private static double clamp(double value) {
        return value < 0 ? 0 : (value > 1 ? 1 : value);
    }

    private void renderChunk(float[] out, int offset, int count) {
        renderPad(out, offset, count);
        renderRain(out, offset, count);
        for (int n = 0; n < NOTES; n++) {
            Note note = notes[n];
            if (note.on) {
                renderNote(note, out, offset, count);
            }
        }
        for (int i = offset * 2; i < (offset + count) * 2; i++) {
            out[i] *= master;
        }
    }

    private void renderPad(float[] out, int offset, int count) {
        double gain = layer[LAYER_PAD] * .016;
        if (gain <= 1e-6) {
            return;
        }
        for (int s = 0; s < PAD_SLOTS; s++) {
            PadSlot slot = pad[s];
            for (int i = 0; i < count; i++) {
                slot.freq += (slot.target - slot.freq) * .00012;
                slot.level += (slot.levelTarget - slot.level) * .00004;
                double increment = slot.freq / sampleRate;
                slot.p1 += increment;
                slot.p2 += increment * 1.0035;
                slot.p3 += increment * 2;
                if (slot.p1 >= 1) {
                    slot.p1 -= 1;
                }
                if (slot.p2 >= 1) {
                    slot.p2 -= 1;
                }
                if (slot.p3 >= 1) {
                    slot.p3 -= 1;
                }
                slot.lfo += slot.lfoStep;
                if (slot.lfo >= 1) {
                    slot.lfo -= 1;
                }
                double breathe = .82 + .18 * CozySynth.sin(slot.lfo);
                double tone = CozySynth.sin(slot.p1) + .85 * CozySynth.sin(slot.p2)
                        + .14 * CozySynth.sin(slot.p3);
                double value = tone * slot.level * breathe * gain;
                out[(offset + i) * 2] += (float) (value * slot.panL);
                out[(offset + i) * 2 + 1] += (float) (value * slot.panR);
            }
        }
    }

    /**
     * The rain the piece is named after.
     *
     * <p>Until now the rain layer was five bells two octaves above the tune, arriving once every
     * four to nine seconds; soloed against the full mix it measured under 1% of the RMS in three
     * of the four sections, which is another way of saying it was not there. This is the bed
     * underneath them: two independently lowpassed noise streams, one per ear, breathing at
     * 0.07 Hz between 55% and full. It fills the empty upper-mid between the pad (under 700 Hz)
     * and the bells (over 1 kHz), and being decorrelated left to right it is the one thing in the
     * mix that has no location at all — which is exactly how weather behaves.
     *
     * <p>The noise index is the absolute sample position, never a per-block counter, so the rain
     * falls in the same places whatever buffer size the device asks for.
     */
    private void renderRain(float[] out, int offset, int count) {
        double gain = layer[LAYER_DROPS] * RAIN_GAIN;
        if (gain <= 1e-9) {
            return;
        }
        for (int i = 0; i < count; i++) {
            int index = (int) (samplePos + i);
            rainLeft += (CozySynth.noise(seed + 1013, index) - rainLeft) * .24;
            rainRight += (CozySynth.noise(seed + 7919, index) - rainRight) * .24;
            rainLfo += rainLfoStep;
            if (rainLfo >= 1) {
                rainLfo -= 1;
            }
            double breathe = .775 + .225 * CozySynth.sin(rainLfo);
            out[(offset + i) * 2] += (float) (rainLeft * breathe * gain);
            out[(offset + i) * 2 + 1] += (float) (rainRight * breathe * gain);
        }
    }

    private void renderNote(Note note, float[] out, int offset, int count) {
        double gain = note.gain * layer[note.layer];
        int limit = Math.min(count, note.remaining);
        for (int i = 0; i < limit; i++) {
            double sum;
            if (note.fm) {
                note.fmPhase += note.fmStep;
                if (note.fmPhase >= 1) {
                    note.fmPhase -= 1;
                }
                note.fmDepth *= note.fmDecay;
                sum = note.amp[0] * CozySynth.sin(note.phase[0]
                        + note.fmDepth * CozySynth.sin(note.fmPhase));
                note.phase[0] += note.step[0];
                if (note.phase[0] >= 1) {
                    note.phase[0] -= 1;
                }
                note.amp[0] *= note.decay[0];
                for (int p = 1; p < note.used; p++) {
                    sum += note.amp[p] * CozySynth.sin(note.phase[p]);
                    note.phase[p] += note.step[p];
                    if (note.phase[p] >= 1) {
                        note.phase[p] -= 1;
                    }
                    note.amp[p] *= note.decay[p];
                }
            } else {
                sum = 0;
                for (int p = 0; p < note.used; p++) {
                    sum += note.amp[p] * CozySynth.sin(note.phase[p]);
                    note.phase[p] += note.step[p];
                    if (note.phase[p] >= 1) {
                        note.phase[p] -= 1;
                    }
                    note.amp[p] *= note.decay[p];
                }
            }
            if (note.attack < 1) {
                note.attack = Math.min(1, note.attack + note.attackStep);
            }
            double shape = .5 - .5 * Math.cos(Math.PI * note.attack);
            if (note.remaining - i <= note.fade) {
                shape *= .5 - .5 * Math.cos(Math.PI * (note.remaining - i) / (double) note.fade);
            }
            double value = sum * shape * gain;
            out[(offset + i) * 2] += (float) (value * note.panL);
            out[(offset + i) * 2 + 1] += (float) (value * note.panR);
        }
        note.remaining -= limit;
        if (note.remaining <= 0) {
            note.on = false;
        }
    }

    // ---- Arrangement -----------------------------------------------------------------

    private void schedule() {
        int bar = (step / STEPS_PER_BAR) % BARS;
        int beat = step % STEPS_PER_BAR;
        if (bar == 0 && beat == 0 && step > 0) {
            cycle++;
        }
        transpose = TRANSPOSE[cycle % TRANSPOSE.length];
        int section = sectionOfBar(bar);
        int local = bar - sectionStart(section);
        int[] chord = chordFor(section, local);

        applyBalance(section);
        if (beat == 0) {
            setPadChord(chord);
            playBass(chord, bar, beat);
        }
        // After setPadChord, which resets this slot: the fifth pad voice is the celebration's
        // extra colour, and it is also how a second player is heard to arrive.
        pad[4].levelTarget = lift > .15 ? 1 : (presence == 2 ? .55 : 0);
        if (beat == 4 && (section == SECTION_B || lift > .25 || presence == 2)) {
            playBass(chord, bar, beat);
        }
        playMelody(section, local, beat, bar);
        playDrops(bar, beat);
        step++;
    }

    /**
     * Where the arrangement answers the room.
     *
     * <p>The intensity multipliers used to be small enough to be a rounding error: measured over
     * a 52-second window, moving {@code setIntensity} from 0 to 1 changed the mix by 0.45 dB and
     * the spectral centroid by 4 Hz, because it only drove the piano and the raindrops and those
     * two were 5% and 0% of section A. They are much bigger now, and the bass moves too, so
     * filling in a picture is something you can hear happening rather than something the code
     * merely does.
     *
     * <p>{@link #hush} pulls the other way: it takes half of the bell, piano and rain back out
     * after a minute of stillness. It never touches pad or bass, so the room quietens but never
     * empties — a pair thinking hard about a 20x20 board must never wonder whether the app has
     * died.
     */
    private void applyBalance(int section) {
        double warm = intensity;
        for (int l = 0; l < LAYERS; l++) {
            layerTarget[l] = BALANCE[section][l];
        }
        layerTarget[LAYER_PIANO] = Math.min(1.2, layerTarget[LAYER_PIANO] + .55 * warm + .30 * lift);
        layerTarget[LAYER_DROPS] = Math.min(1.2, layerTarget[LAYER_DROPS] + .65 * warm + .45 * lift);
        // The bell and the bass scale rather than add, because they are already most of the mix
        // and the headroom above them is only 0.3 dB. Taking level away from an empty board is
        // the cheaper half of the same gesture, and it is the truer one: a picture nobody has
        // started should be the quietest the room ever gets.
        layerTarget[LAYER_BELL] = Math.min(1.4,
                layerTarget[LAYER_BELL] * (.80 + .30 * warm) + .35 * lift);
        layerTarget[LAYER_BASS] = Math.min(1.2,
                layerTarget[LAYER_BASS] * (.86 + .24 * warm) + .15 * lift
                        + (presence == 2 ? .12 : 0));
        layerTarget[LAYER_PAD] = Math.min(1.2, layerTarget[LAYER_PAD] + .20 * lift);

        double awake = 1 - .5 * hush;
        layerTarget[LAYER_BELL] *= awake;
        layerTarget[LAYER_PIANO] *= awake;
        layerTarget[LAYER_DROPS] *= awake;
        double breathing = 1 - .3 * hush;
        for (PadSlot slot : pad) {
            slot.lfoStep = slot.baseLfoStep * breathing;
        }
    }

    private static int sectionOfBar(int bar) {
        if (bar < 16) {
            return SECTION_A;
        }
        if (bar < 32) {
            return SECTION_B;
        }
        if (bar < 40) {
            return SECTION_C;
        }
        return SECTION_A2;
    }

    private static int sectionStart(int section) {
        switch (section) {
            case SECTION_A:
                return 0;
            case SECTION_B:
                return 16;
            case SECTION_C:
                return 32;
            default:
                return 40;
        }
    }

    /** The second pass through a section keeps the tune and changes the ground under it. */
    private static int[] chordFor(int section, int local) {
        boolean repeat = (local / 8) % 2 == 1;
        switch (section) {
            case SECTION_B:
                return (repeat ? CHORD_B2 : CHORD_B)[local % 8];
            case SECTION_C:
                return CHORD_C[(local / 4) % CHORD_C.length];
            default:
                return (repeat ? CHORD_A2 : CHORD_A)[local % 8];
        }
    }

    private static int[] melodyFor(int section) {
        switch (section) {
            case SECTION_B:
                return MEL_B;
            case SECTION_C:
                return MEL_C;
            case SECTION_A2:
                return MEL_A2;
            default:
                return MEL_A;
        }
    }

    private void setPadChord(int[] chord) {
        for (int s = 0; s < 4; s++) {
            pad[s].target = CozySynth.hz(chord[s + 1] + transpose);
            pad[s].levelTarget = 1;
        }
        // The fifth slot is the celebration's extra colour: the second chord tone, an octave up.
        pad[4].target = CozySynth.hz(chord[2] + 12 + transpose);
        pad[4].levelTarget = 0;
    }

    private void playBass(int[] chord, int bar, int beat) {
        if (layerTarget[LAYER_BASS] < .05) {
            return;
        }
        int note = beat == 0 ? chord[0] : chord[0] + 7;
        double velocity = beat == 0 ? 1 : .62;
        spawnBass(CozySynth.hz(note + transpose) * jitter(bar * 31 + beat, 6), .085 * velocity, 0);
    }

    private void playMelody(int section, int local, int beat, int bar) {
        int[] melody = melodyFor(section);
        int pass = (local / 8) % 2;
        int index = (local % 8) * STEPS_PER_BAR + beat;
        int note = melody[index];
        long vary = CozySynth.mix(seed + cycle * 7919L + bar * 131L + beat);
        double roll = (vary >>> 11) * 0x1.0p-53;
        // The longer the evening goes on the more liberties the arrangement takes: holes where
        // notes were, ornaments in the gaps, and from the second pass onwards it stops waiting
        // for the repeat before taking them.
        int age = Math.min(3, cycle);
        double holeChance = .11 + .05 * age;
        double ornamentAbove = .94 - .04 * age;
        boolean varying = pass == 1 || cycle >= 1;

        if (note >= 0) {
            // Occasionally leave a hole where a note used to be. Space is music.
            if (varying && roll < holeChance && section != SECTION_C) {
                return;
            }
            double velocity = .86 + .28 * CozySynth.rnd(vary, 3);
            spawnBell(CozySynth.hz(note + transpose) * jitter(index + bar * 17, 5),
                    .075 * velocity);
            boolean ghost = layerTarget[LAYER_PIANO] > .05 && roll < .55;
            if (ghost) {
                spawnPiano(CozySynth.hz(note - 12 + transpose) * jitter(index + bar * 23, 4),
                        .055 * velocity);
            }
            // Near the end of a picture the tune picks up an octave shimmer on the downbeats.
            if (intensity > .55 && (beat == 0 || beat == 4)) {
                spawnBell(CozySynth.hz(note + 12 + transpose) * jitter(index + 11, 4),
                        .028 * velocity);
            }
        } else if (varying && section != SECTION_C && roll > ornamentAbove) {
            // A gentle passing tone in a gap, always pentatonic so it cannot clash.
            int neighbour = melody[Math.max(0, index - 1)];
            if (neighbour < 0) {
                neighbour = 81;
            }
            spawnBell(CozySynth.hz(neighbour + 2 + transpose) * jitter(index, 5), .040);
        }

        double shine = Math.max(lift, sparkleLeft > 0 ? .55 : 0);
        if (shine > .2 && (beat == 2 || beat == 6)) {
            int high = COUNTER[(int) (CozySynth.rnd(vary, 9) * COUNTER.length) % COUNTER.length];
            spawnBell(CozySynth.hz(high + transpose) * jitter(index + 5, 4), .048 * shine);
        }
    }

    private void playDrops(int bar, int beat) {
        double amount = layerTarget[LAYER_DROPS];
        if (amount < .05) {
            return;
        }
        long vary = CozySynth.mix(seed + cycle * 104729L + bar * 977L + beat * 13L);
        double roll = (vary >>> 11) * 0x1.0p-53;
        if (roll > .24 * amount * 2.2) {
            return;
        }
        int note = DROPS[(int) (CozySynth.rnd(vary, 2) * DROPS.length) % DROPS.length];
        // Every drop lands somewhere of its own, which is what stops a rising density of rain
        // turning into a single point source getting louder.
        double pan = (CozySynth.rnd(vary, 5) - .5) * 1.4;
        spawnDrop(CozySynth.hz(note + transpose) * jitter(bar * 7 + beat, 8), .052 * amount, pan);
    }

    private double jitter(int index, double range) {
        return CozySynth.cents((CozySynth.rnd(seed + 4242, index) - .5) * range);
    }

    // ---- Voice allocation ------------------------------------------------------------

    private Note claim() {
        Note best = null;
        for (int i = 0; i < NOTES; i++) {
            if (!notes[i].on) {
                return notes[i];
            }
            if (best == null || notes[i].remaining < best.remaining) {
                best = notes[i];
            }
        }
        return best;
    }

    /** Music box: a two-operator bell with a stretched partial and a long, round tail. */
    private void spawnBell(double freq, double amp) {
        Note note = claim();
        prepare(note, LAYER_BELL, amp, 2.0, .0015, LAYER_PAN[LAYER_BELL]);
        note.used = 3;
        setPartial(note, 0, freq, 1, 1.30);
        setPartial(note, 1, freq * 2.76, .14, .55);
        setPartial(note, 2, freq * 5.40, .05, .28);
        note.fm = true;
        note.fmStep = freq * 3.0 / sampleRate;
        note.fmDepth = .22;
        note.fmDecay = Math.exp(-1.0 / (.09 * sampleRate));
    }

    /** Felt piano: soft hammer, stretched partials, no bite at all. */
    private void spawnPiano(double freq, double amp) {
        Note note = claim();
        prepare(note, LAYER_PIANO, amp, 1.8, .012, LAYER_PAN[LAYER_PIANO]);
        note.used = 4;
        setPartial(note, 0, freq, 1, 1.10);
        setPartial(note, 1, freq * 2.003, .30, .60);
        setPartial(note, 2, freq * 3.01, .12, .35);
        setPartial(note, 3, freq * 4.02, .05, .20);
    }

    private void spawnBass(double freq, double amp, double pan) {
        Note note = claim();
        prepare(note, LAYER_BASS, amp, 2.4, .022, pan);
        note.used = 3;
        setPartial(note, 0, freq, 1, 1.40);
        setPartial(note, 1, freq * 2, .22, .70);
        setPartial(note, 2, freq * 3, .07, .35);
    }

    /** A raindrop on the window. */
    private void spawnDrop(double freq, double amp, double pan) {
        Note note = claim();
        prepare(note, LAYER_DROPS, amp, .55, .002, pan);
        note.used = 2;
        setPartial(note, 0, freq, 1, .22);
        setPartial(note, 1, freq * 2.01, .10, .10);
    }

    private void prepare(Note note, int layerId, double amp, double seconds, double attack,
            double pan) {
        note.on = true;
        note.layer = layerId;
        note.gain = amp;
        note.panL = CozySynth.panLeft(pan);
        note.panR = CozySynth.panRight(pan);
        note.fm = false;
        note.fmDepth = 0;
        note.remaining = (int) (seconds * sampleRate);
        note.fade = Math.max(1, (int) (Math.min(.30, seconds * .3) * sampleRate));
        note.attack = 0;
        note.attackStep = 1.0 / Math.max(1, attack * sampleRate);
    }

    private void setPartial(Note note, int index, double freq, double amp, double tau) {
        note.phase[index] = 0;
        note.step[index] = freq / sampleRate;
        note.amp[index] = amp;
        note.decay[index] = Math.exp(-1.0 / (tau * sampleRate));
    }
}

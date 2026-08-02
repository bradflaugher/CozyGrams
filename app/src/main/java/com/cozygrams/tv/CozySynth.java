package com.cozygrams.tv;

import java.util.Arrays;

/**
 * Every sample CozyGrams ever makes starts here.
 *
 * <p>This class is deliberately free of Android types, threads and hidden state: hand it a sound
 * id, a seed and a buffer and it fills the buffer with exactly the same samples every time. That
 * makes the whole sound design testable on a plain JVM, and it keeps the APK free of audio assets.
 *
 * <h3>Mix budget (1.0 == digital full scale)</h3>
 * <ul>
 *   <li>A single rendered SFX voice is normalised so it can never exceed {@link #VOICE_PEAK}
 *       (0.30). Quiet sounds such as {@code MOVE} sit far below that.</li>
 *   <li>The SFX bus sums all twelve voice slots and is soft-limited to {@link #SFX_CEILING}
 *       (0.62), so even a wall of simultaneous two-player input cannot run away.</li>
 *   <li>The music bus is soft-limited to {@link #MUSIC_CEILING} (0.34).</li>
 * </ul>
 *
 * <p>Music and SFX are two separate {@code AudioTrack}s which the platform adds together, so the
 * worst case the speaker can ever see is 0.62 + 0.34 = <b>0.96 of full scale</b> — roughly 0.35 dB
 * of headroom held in reserve. Music plus a fistful of concurrent effects therefore cannot clip.
 */
final class CozySynth {

    private CozySynth() {
    }

    /** 44.1 kHz keeps the bell partials and the pencil-stroke noise free of aliasing. */
    static final int SAMPLE_RATE = 44100;

    static final double VOICE_PEAK = 0.30;
    static final double SFX_CEILING = 0.62;
    static final double MUSIC_CEILING = 0.34;

    // ---- Sound ids -------------------------------------------------------------------
    // These mirror CozySfx.Sound.ordinal(); the enum order there is the contract.

    static final int MOVE = 0;
    static final int FILL = 1;
    static final int CROSS = 2;
    static final int CLEAR = 3;
    static final int HINT = 4;
    static final int ERROR = 5;
    static final int WIN = 6;
    static final int SELECT = 7;
    static final int LINE = 8;
    static final int JOIN = 9;
    static final int SOUND_COUNT = 10;

    private static final int[] DURATION_MS = {
            58,    // MOVE   — barely there
            200,   // FILL
            140,   // CROSS
            170,   // CLEAR
            620,   // HINT
            340,   // ERROR
            1500,  // WIN
            160,   // SELECT
            460,   // LINE
            760    // JOIN
    };

    /** Per-sound peak target, all comfortably under {@link #VOICE_PEAK}. */
    private static final double[] PEAK = {
            0.075, // MOVE   — a fingertip, not a click
            0.220, // FILL
            0.150, // CROSS
            0.135, // CLEAR
            0.200, // HINT
            0.125, // ERROR  — the quietest sound in the game on purpose
            0.300, // WIN
            0.185, // SELECT
            0.215, // LINE
            0.235  // JOIN
    };

    static int durationMs(int sound) {
        return DURATION_MS[sound];
    }

    static int sampleCount(int sound) {
        return DURATION_MS[sound] * SAMPLE_RATE / 1000;
    }

    static double peakOf(int sound) {
        return PEAK[sound];
    }

    // ---- Oscillator and randomness primitives ----------------------------------------

    private static final int TABLE_BITS = 12;
    private static final int TABLE = 1 << TABLE_BITS;
    private static final float[] SINE = new float[TABLE + 1];

    static {
        for (int i = 0; i <= TABLE; i++) {
            SINE[i] = (float) Math.sin(2 * Math.PI * i / TABLE);
        }
    }

    /** Sine of a phase measured in <em>cycles</em> (1.0 is one full turn), table interpolated. */
    static double sin(double cycles) {
        double wrapped = cycles - Math.floor(cycles);
        double scaled = wrapped * TABLE;
        int index = (int) scaled;
        if (index >= TABLE) {
            index = TABLE - 1;
        }
        double fraction = scaled - index;
        float low = SINE[index];
        return low + (SINE[index + 1] - low) * fraction;
    }

    /** SplitMix64 finaliser — the only source of randomness anywhere in the audio engine. */
    static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Deterministic value in [0, 1) for a seed and an index. */
    static double rnd(long seed, int index) {
        return (mix(seed ^ (index * 0x9E3779B97F4A7C15L)) >>> 11) * 0x1.0p-53;
    }

    /** Deterministic white noise in [-1, 1]. */
    static double noise(long seed, int index) {
        return rnd(seed, index) * 2 - 1;
    }

    /** Turns a musical cent offset into a frequency multiplier. */
    static double cents(double amount) {
        return Math.pow(2, amount / 1200.0);
    }

    /** Equal-tempered pitch, MIDI 69 == A440. */
    static double hz(double midi) {
        return 440 * Math.pow(2, (midi - 69) / 12.0);
    }

    /**
     * Click-free amplitude shape: raised-cosine attack, exponential body, raised-cosine release
     * that reaches exactly zero at {@code duration}. Returns 0 outside {@code (0, duration)}.
     */
    static double env(double t, double duration, double attack, double tau) {
        if (t <= 0 || t >= duration) {
            return 0;
        }
        double rise = t < attack ? .5 - .5 * Math.cos(Math.PI * t / attack) : 1;
        double body = Math.exp(-Math.max(0, t - attack) / tau);
        double release = Math.min(duration * .35, .045);
        double fall = t > duration - release
                ? .5 - .5 * Math.cos(Math.PI * (duration - t) / release) : 1;
        return rise * body * fall;
    }

    /**
     * Transparent below 70% of {@code ceiling}, asymptotic to it above. Guarantees
     * {@code |result| < ceiling} for any finite input, which is what keeps the buses honest.
     */
    static double limit(double value, double ceiling) {
        if (value != value) {
            return 0;
        }
        double magnitude = Math.abs(value);
        double knee = ceiling * .70;
        if (magnitude <= knee) {
            return value;
        }
        double over = (magnitude - knee) / (ceiling - knee);
        double shaped = knee + (ceiling - knee) * (1 - 1 / (1 + over));
        return value < 0 ? -shaped : shaped;
    }

    // ---- Reusable voice shapes -------------------------------------------------------

    /** Two-operator FM bell: bright strike, quick loss of partials, long round tail. */
    private static void fmBell(float[] out, int total, int onset, double freq, double amp,
            double ratio, double index, double tau, double duration) {
        int span = (int) (duration * SAMPLE_RATE);
        for (int i = 0; i < span && onset + i < total; i++) {
            double t = i / (double) SAMPLE_RATE;
            double shape = env(t, duration, .0015, tau);
            if (shape <= 0) {
                continue;
            }
            double depth = index * Math.exp(-t / (tau * .30));
            double carrier = sin(freq * t + depth * sin(freq * ratio * t));
            out[onset + i] += (float) (amp * shape * carrier);
        }
    }

    // ---- The ten voices --------------------------------------------------------------

    static int render(int sound, long seed, float[] out) {
        int count = sampleCount(sound);
        Arrays.fill(out, 0, count, 0f);
        switch (sound) {
            case MOVE:
                renderMove(seed, out, count);
                break;
            case FILL:
                renderFill(seed, out, count);
                break;
            case CROSS:
                renderCross(seed, out, count);
                break;
            case CLEAR:
                renderClear(seed, out, count);
                break;
            case HINT:
                renderHint(seed, out, count);
                break;
            case ERROR:
                renderError(seed, out, count);
                break;
            case WIN:
                renderWin(seed, out, count);
                break;
            case SELECT:
                renderSelect(seed, out, count);
                break;
            case LINE:
                renderLine(seed, out, count);
                break;
            default:
                renderJoin(seed, out, count);
                break;
        }
        return polish(out, count, PEAK[sound], seed);
    }

    /** A fingertip landing on felt. Dark, tiny, and never the same twice. */
    private static void renderMove(long seed, float[] out, int count) {
        double freq = 592 * cents((rnd(seed, 1) - .5) * 110);
        double duration = count / (double) SAMPLE_RATE;
        double puff = 0;
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double shape = env(t, duration, .0035, .017);
            puff += (noise(seed, i) - puff) * .05;
            double body = sin(freq * t) + .16 * sin(freq * 1.5 * t);
            out[i] = (float) (shape * (body * .8 + puff * 2.2 * Math.exp(-t * 240)));
        }
    }

    /** A wooden bead pressed into place: a soft thock plus a short, warm modal ring. */
    private static void renderFill(long seed, float[] out, int count) {
        double freq = 349.23 * cents((rnd(seed, 2) - .5) * 44);
        double duration = count / (double) SAMPLE_RATE;
        double[] ratios = {1, 2.01, 3.04, 4.97, 6.91};
        double[] gains = {1, .42, .20, .10, .045};
        double[] taus = {.145, .086, .055, .034, .021};
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double gate = t < .002 ? .5 - .5 * Math.cos(Math.PI * t / .002) : 1;
            double tail = t > duration - .04
                    ? .5 - .5 * Math.cos(Math.PI * (duration - t) / .04) : 1;
            double sum = 0;
            for (int p = 0; p < ratios.length; p++) {
                sum += gains[p] * Math.exp(-t / taus[p]) * sin(freq * ratios[p] * t);
            }
            double knock = noise(seed, i + 7000) * Math.exp(-t * 620) * .55;
            out[i] = (float) (gate * tail * (sum + knock));
        }
    }

    /** Graphite dragged across paper: a swept resonant noise band with a little grain. */
    private static void renderCross(long seed, float[] out, int count) {
        double duration = count / (double) SAMPLE_RATE;
        double top = 3050 * cents((rnd(seed, 3) - .5) * 260);
        double bottom = 1650;
        double resonance = 1 / 1.55;
        double ic1 = 0;
        double ic2 = 0;
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double position = t / duration;
            double cutoff = top + (bottom - top) * position;
            double g = Math.tan(Math.PI * cutoff / SAMPLE_RATE);
            double a1 = 1 / (1 + g * (g + resonance));
            double a2 = g * a1;
            double input = noise(seed, i);
            double v3 = input - ic2;
            double v1 = a1 * ic1 + a2 * v3;
            double v2 = ic2 + a2 * ic1 + g * a2 * v3;
            ic1 = 2 * v1 - ic1;
            ic2 = 2 * v2 - ic2;
            double grain = .78 + .22 * sin(150 * t + rnd(seed, 5));
            double shape = env(t, duration, .0025, .048) * grain;
            double paper = sin(168 * t) * Math.exp(-t * 58) * .10;
            out[i] = (float) (shape * v1 * 1.9 + paper * shape);
        }
    }

    /** Brushing a crumb off the table: dark air sweeping down with a soft descending puff. */
    private static void renderClear(long seed, float[] out, int count) {
        double duration = count / (double) SAMPLE_RATE;
        double lp = 0;
        double lp2 = 0;
        double base = 330 * cents((rnd(seed, 4) - .5) * 50);
        double phase = 0;
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double position = t / duration;
            double coefficient = .16 * (1 - position) + .012;
            lp += (noise(seed, i + 400) - lp) * coefficient;
            lp2 += (lp - lp2) * coefficient;
            double glide = base * (1 - .22 * position);
            phase += glide / SAMPLE_RATE;
            double shape = env(t, duration, .008, .058);
            out[i] = (float) (shape * (lp2 * 3.1 + sin(phase) * .38));
        }
    }

    /** Starlight: a small ascending pentatonic run of bells under a scatter of sparkle. */
    private static void renderHint(long seed, float[] out, int count) {
        double[] steps = {1046.50, 1174.66, 1396.91, 1567.98, 2093.00};
        int[] onsets = {0, 52, 106, 158, 236};
        for (int n = 0; n < steps.length; n++) {
            double detune = cents((rnd(seed, 10 + n) - .5) * 26);
            fmBell(out, count, onsets[n] * SAMPLE_RATE / 1000, steps[n] * detune,
                    .55 - n * .05, 2.0, .26, .30, .40);
        }
        for (int s = 0; s < 16; s++) {
            int onset = (int) (rnd(seed, 40 + s) * count * .72);
            double freq = 2100 + rnd(seed, 60 + s) * 3100;
            int span = SAMPLE_RATE * 34 / 1000;
            for (int i = 0; i < span && onset + i < count; i++) {
                double t = i / (double) SAMPLE_RATE;
                out[onset + i] += (float) (.075 * env(t, span / (double) SAMPLE_RATE, .002, .010)
                        * sin(freq * t));
            }
        }
    }

    /**
     * A soft two-note sigh, not a scold. Two felt-piano notes a minor third apart, the second
     * leaning down under the first — the sound of someone saying "mm, not quite" kindly.
     */
    private static void renderError(long seed, float[] out, int count) {
        double drift = cents((rnd(seed, 6) - .5) * 20);
        double[] ratios = {1, 2.002, 3.01, 4.02};
        double[] gains = {1, .26, .09, .035};
        double[] taus = {.30, .17, .10, .06};
        softNote(out, count, 0, 349.23 * drift, .62, ratios, gains, taus, .030, .30);
        softNote(out, count, SAMPLE_RATE * 130 / 1000, 293.66 * drift, .58,
                ratios, gains, taus, .034, .21);
    }

    /** Sunlight through the window: a rolled Fmaj9 on bells over a warm pad and a low root. */
    private static void renderWin(long seed, float[] out, int count) {
        double[] roll = {349.23, 440.00, 523.25, 659.25, 783.99, 1046.50, 1396.91};
        int[] onsets = {0, 88, 172, 254, 336, 470, 700};
        for (int n = 0; n < roll.length; n++) {
            double detune = cents((rnd(seed, 80 + n) - .5) * 16);
            fmBell(out, count, onsets[n] * SAMPLE_RATE / 1000, roll[n] * detune,
                    .46 - n * .028, 3.0, .22, .70, 1.05);
        }
        double duration = count / (double) SAMPLE_RATE;
        double[] pad = {174.61, 220.00, 261.63, 329.63};
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double rise = t < .13 ? .5 - .5 * Math.cos(Math.PI * t / .13) : 1;
            double fall = t > duration - .55
                    ? .5 - .5 * Math.cos(Math.PI * (duration - t) / .55) : 1;
            double sum = 0;
            for (int p = 0; p < pad.length; p++) {
                sum += sin(pad[p] * t) + .55 * sin(pad[p] * 1.0031 * t);
            }
            double bass = sin(87.31 * t) * Math.exp(-t / 1.1) * 1.5
                    + .22 * sin(174.62 * t) * Math.exp(-t / .7);
            out[i] += (float) (rise * fall * (sum * .085 + bass * .30));
        }
    }

    /** A rounded wooden button: a fast downward glide settling onto C5. */
    private static void renderSelect(long seed, float[] out, int count) {
        double duration = count / (double) SAMPLE_RATE;
        double target = 523.25 * cents((rnd(seed, 7) - .5) * 30);
        double phase = 0;
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double freq = target * (1 + .52 * Math.exp(-t / .020));
            phase += freq / SAMPLE_RATE;
            double shape = env(t, duration, .0022, .072);
            double body = sin(phase) + .24 * sin(phase * 2) + .07 * sin(phase * 3);
            double click = noise(seed, i + 900) * Math.exp(-t * 900) * .30;
            out[i] = (float) (shape * (body + click));
        }
    }

    /** A line settles: three chimes up the F pentatonic with a thin shimmer behind them. */
    private static void renderLine(long seed, float[] out, int count) {
        double[] notes = {783.99, 1046.50, 1174.66};
        int[] onsets = {0, 84, 164};
        for (int n = 0; n < notes.length; n++) {
            double detune = cents((rnd(seed, 20 + n) - .5) * 22);
            fmBell(out, count, onsets[n] * SAMPLE_RATE / 1000, notes[n] * detune,
                    .58 - n * .04, 2.5, .24, .34, .46);
        }
        double duration = count / (double) SAMPLE_RATE;
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double shape = env(t, duration, .09, .34);
            out[i] += (float) (shape * .055
                    * (sin(2093 * t) + .6 * sin(3136.0 * 1.001 * t)));
        }
    }

    /** Someone sitting down beside you: an open fifth that grows into a major chord. */
    private static void renderJoin(long seed, float[] out, int count) {
        double drift = cents((rnd(seed, 8) - .5) * 14);
        double[] ratios = {1, 2.004, 3.02, 4.03, 5.05};
        double[] gains = {1, .34, .14, .06, .028};
        double[] taus = {.85, .48, .30, .19, .12};
        softNote(out, count, 0, 174.61 * drift, .50, ratios, gains, taus, .022, .78);
        softNote(out, count, SAMPLE_RATE * 30 / 1000, 261.63 * drift, .42,
                ratios, gains, taus, .022, .72);
        // The third arrives last, so an open fifth turns into F major as the second player lands.
        softNote(out, count, SAMPLE_RATE * 180 / 1000, 220.00 * drift, .36,
                ratios, gains, taus, .026, .56);
        fmBell(out, count, SAMPLE_RATE * 300 / 1000, 698.46 * drift, .20, 3.0, .18, .34, .44);
        double duration = count / (double) SAMPLE_RATE;
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double rise = t < .26 ? .5 - .5 * Math.cos(Math.PI * t / .26) : 1;
            double fall = t > duration - .30
                    ? .5 - .5 * Math.cos(Math.PI * (duration - t) / .30) : 1;
            out[i] += (float) (rise * fall * .085
                    * (sin(87.31 * t) + .7 * sin(130.81 * t) + .3 * sin(174.61 * 1.002 * t)));
        }
    }

    /** Felt-piano style note used by ERROR and JOIN: soft attack, stretched partials, no bite. */
    private static void softNote(float[] out, int total, int onset, double freq, double amp,
            double[] ratios, double[] gains, double[] taus, double attack, double duration) {
        int span = (int) (duration * SAMPLE_RATE);
        for (int i = 0; i < span && onset + i < total; i++) {
            double t = i / (double) SAMPLE_RATE;
            double gate = t < attack ? .5 - .5 * Math.cos(Math.PI * t / attack) : 1;
            double release = Math.min(duration * .35, .06);
            double fall = t > duration - release
                    ? .5 - .5 * Math.cos(Math.PI * (duration - t) / release) : 1;
            double sum = 0;
            for (int p = 0; p < ratios.length; p++) {
                sum += gains[p] * Math.exp(-t / taus[p]) * sin(freq * ratios[p] * t);
            }
            out[onset + i] += (float) (amp * gate * fall * sum);
        }
    }

    /**
     * Scrubs any stray NaN, normalises the buffer to its peak target (with a whisper of level
     * variation so two identical taps are never identical), and forces the first and last samples
     * to exact silence so a voice can never introduce a click when the mixer starts or drops it.
     */
    private static int polish(float[] out, int count, double peak, long seed) {
        double max = 0;
        for (int i = 0; i < count; i++) {
            float value = out[i];
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                out[i] = 0;
                continue;
            }
            double magnitude = Math.abs(value);
            if (magnitude > max) {
                max = magnitude;
            }
        }
        double target = peak * (.90 + .10 * rnd(seed, 9991));
        double scale = max > 1e-9 ? target / max : 0;
        int fadeIn = Math.max(1, Math.min(32, count / 4));
        int fadeOut = Math.max(1, Math.min(192, count / 3));
        for (int i = 0; i < count; i++) {
            double gain = scale;
            if (i < fadeIn) {
                gain *= .5 - .5 * Math.cos(Math.PI * i / fadeIn);
            }
            int fromEnd = count - 1 - i;
            if (fromEnd < fadeOut) {
                gain *= .5 - .5 * Math.cos(Math.PI * fromEnd / fadeOut);
            }
            out[i] = (float) (out[i] * gain);
        }
        return count;
    }
}

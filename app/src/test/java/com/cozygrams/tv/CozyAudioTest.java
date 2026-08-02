package com.cozygrams.tv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the pure synthesis maths. Nothing here touches {@code AudioTrack}: the point of
 * keeping {@link CozySynth} and {@link CozyScore} free of Android types is that the sound design
 * itself can be asserted on — peaks, headroom, click-free envelopes, determinism.
 */
public class CozyAudioTest {

    private static float[] render(int sound, long seed) {
        float[] buffer = new float[CozySynth.sampleCount(sound)];
        int written = CozySynth.render(sound, seed, buffer);
        assertEquals(buffer.length, written);
        return buffer;
    }

    private static double peak(float[] buffer) {
        double max = 0;
        for (float value : buffer) {
            assertFalse("NaN in buffer", Float.isNaN(value));
            assertFalse("infinity in buffer", Float.isInfinite(value));
            max = Math.max(max, Math.abs(value));
        }
        return max;
    }

    private static double rms(float[] buffer, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += buffer[i] * (double) buffer[i];
        }
        return Math.sqrt(sum / (to - from));
    }

    @Test
    public void everySoundIsAudibleAndStaysInsideTheVoiceBudget() {
        for (int sound = 0; sound < CozySynth.SOUND_COUNT; sound++) {
            for (long seed = 0; seed < 6; seed++) {
                float[] buffer = render(sound, seed * 977 + 13);
                double loudest = peak(buffer);
                assertTrue("sound " + sound + " is silent", loudest > 0.01);
                assertTrue("sound " + sound + " exceeded its own target",
                        loudest <= CozySynth.peakOf(sound) + 1e-6);
                assertTrue("sound " + sound + " exceeded the voice budget",
                        loudest <= CozySynth.VOICE_PEAK + 1e-6);
            }
        }
    }

    @Test
    public void everySoundStartsAndEndsInSilenceSoNothingClicks() {
        for (int sound = 0; sound < CozySynth.SOUND_COUNT; sound++) {
            float[] buffer = render(sound, 4242);
            int last = buffer.length - 1;
            assertEquals("sound " + sound + " starts hot", 0f, buffer[0], 1e-9f);
            assertEquals("sound " + sound + " ends hot", 0f, buffer[last], 1e-9f);
            double head = rms(buffer, 0, 8);
            double tail = rms(buffer, last - 7, last + 1);
            double budget = CozySynth.peakOf(sound) * .10;
            assertTrue("sound " + sound + " has an attack discontinuity", head < budget);
            assertTrue("sound " + sound + " has a release discontinuity", tail < budget);
        }
    }

    @Test
    public void repeatedTapsVaryButASeedAlwaysGivesTheSameSound() {
        for (int sound = 0; sound < CozySynth.SOUND_COUNT; sound++) {
            float[] once = render(sound, 8080);
            float[] again = render(sound, 8080);
            for (int i = 0; i < once.length; i++) {
                assertEquals("sound " + sound + " is not deterministic", once[i], again[i], 0f);
            }
            float[] different = render(sound, 8081);
            boolean varies = false;
            for (int i = 0; i < once.length && !varies; i++) {
                varies = Math.abs(once[i] - different[i]) > 1e-6;
            }
            assertTrue("sound " + sound + " is byte identical on every trigger", varies);
        }
    }

    @Test
    public void theQuietSoundsReallyAreTheQuietOnes() {
        assertTrue(CozySynth.peakOf(CozySynth.MOVE) < CozySynth.peakOf(CozySynth.SELECT));
        assertTrue(CozySynth.peakOf(CozySynth.ERROR) < CozySynth.peakOf(CozySynth.FILL));
        assertTrue(CozySynth.peakOf(CozySynth.WIN) >= CozySynth.peakOf(CozySynth.JOIN));
    }

    @Test
    public void twelveConcurrentVoicesPlusMusicCannotClip() {
        double worstVoiceSum = CozySynth.VOICE_PEAK * 12;
        double limited = CozySynth.limit(worstVoiceSum, CozySynth.SFX_CEILING);
        assertTrue(limited < CozySynth.SFX_CEILING);
        assertTrue(CozySynth.limit(-worstVoiceSum, CozySynth.SFX_CEILING)
                > -CozySynth.SFX_CEILING);
        assertTrue("the two buses together must leave headroom",
                CozySynth.SFX_CEILING + CozySynth.MUSIC_CEILING < 1.0);
        // Below the knee the limiter must be perfectly transparent.
        double quiet = CozySynth.SFX_CEILING * .5;
        assertEquals(quiet, CozySynth.limit(quiet, CozySynth.SFX_CEILING), 1e-12);
        assertEquals(0.0, CozySynth.limit(Double.NaN, CozySynth.SFX_CEILING), 0.0);
    }

    @Test
    public void envelopesOpenAndCloseAtExactlyZero() {
        assertEquals(0.0, CozySynth.env(0, .5, .01, .2), 0.0);
        assertEquals(0.0, CozySynth.env(.5, .5, .01, .2), 0.0);
        assertEquals(0.0, CozySynth.env(-1, .5, .01, .2), 0.0);
        double middle = CozySynth.env(.011, .5, .01, .2);
        assertTrue(middle > .9 && middle <= 1.0);
        // Monotone rise through the attack means no step edge at the start of a note.
        double previous = 0;
        for (int i = 0; i <= 40; i++) {
            double value = CozySynth.env(i * .01 / 40, .5, .01, .2);
            assertTrue(value >= previous - 1e-12);
            previous = value;
        }
    }

    // ---- The score -------------------------------------------------------------------

    private static float[] renderScore(CozyScore score, int samples, int block) {
        float[] out = new float[samples];
        for (int i = 0; i < samples; i += block) {
            score.render(out, i, Math.min(block, samples - i));
        }
        return out;
    }

    @Test
    public void theScoreMakesMusicAndKeepsItsHeadroom() {
        CozyScore score = new CozyScore(CozySynth.SAMPLE_RATE, 99);
        int seconds = 45;
        float[] out = renderScore(score, CozySynth.SAMPLE_RATE * seconds, 1024);
        double loudest = peak(out);
        assertTrue("the score never made a sound", loudest > .05);
        assertTrue("the score leans on the limiter far too hard: " + loudest, loudest < .60);
        double after = 0;
        for (float value : out) {
            after = Math.max(after, Math.abs(CozySynth.limit(value, CozySynth.MUSIC_CEILING)));
        }
        assertTrue(after < CozySynth.MUSIC_CEILING);
        // It fades in from silence rather than snapping on.
        assertTrue(rms(out, 0, 256) < rms(out, CozySynth.SAMPLE_RATE * 20,
                CozySynth.SAMPLE_RATE * 20 + 256) + 1e-9);
        assertEquals(0f, out[0], 1e-6f);
    }

    @Test
    public void theScoreRendersTheSameWhateverBlockSizeTheDeviceAsksFor() {
        int samples = CozySynth.SAMPLE_RATE * 12;
        float[] big = renderScore(new CozyScore(CozySynth.SAMPLE_RATE, 7), samples, 4096);
        float[] small = renderScore(new CozyScore(CozySynth.SAMPLE_RATE, 7), samples, 337);
        for (int i = 0; i < samples; i++) {
            assertEquals("block size changed the music at sample " + i, big[i], small[i], 0f);
        }
    }

    @Test
    public void celebrateLiftsTheArrangementAndThenSettlesBack() {
        int window = CozySynth.SAMPLE_RATE * 4;
        CozyScore plain = new CozyScore(CozySynth.SAMPLE_RATE, 5150);
        CozyScore party = new CozyScore(CozySynth.SAMPLE_RATE, 5150);
        renderScore(plain, CozySynth.SAMPLE_RATE * 30, 1024);
        renderScore(party, CozySynth.SAMPLE_RATE * 30, 1024);

        party.celebrate();
        double quietDuring = rms(renderScore(plain, window, 1024), 0, window);
        double loudDuring = rms(renderScore(party, window, 1024), 0, window);
        assertTrue("celebrate() did not lift the score: " + quietDuring + " -> " + loudDuring,
                loudDuring > quietDuring * 1.08);

        renderScore(plain, CozySynth.SAMPLE_RATE * 20, 1024);
        renderScore(party, CozySynth.SAMPLE_RATE * 20, 1024);
        double quietAfter = rms(renderScore(plain, window, 1024), 0, window);
        double loudAfter = rms(renderScore(party, window, 1024), 0, window);
        assertTrue("celebrate() never settled back: " + quietAfter + " -> " + loudAfter,
                loudAfter < quietAfter * 1.25);
    }

    @Test
    public void theFormIsLongEnoughToLiveWith() {
        double seconds = CozyScore.formSeconds(CozySynth.SAMPLE_RATE);
        assertTrue("the loop is too short to survive a co-op session: " + seconds,
                seconds > 180);
        assertTrue(seconds < 300);
    }
}

package com.cozygrams.tv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the pure synthesis maths. Nothing here touches {@code AudioTrack}: the point of
 * keeping {@link CozySynth}, {@link CozyScore} and the {@link CozySfx} mixer free of Android types
 * is that the sound design itself can be asserted on — peaks, headroom, click-free envelopes,
 * determinism, and now spectrum, gesture and width.
 *
 * <p>There is a small FFT at the bottom of this file. Nobody can hear a unit test, so the only
 * honest way to claim "the crossing sound has moved out of the ear's fatigue band" or "a hint
 * climbs and a finished line settles" is to measure the thing being claimed. Every number in the
 * assertions below was measured against the code as it stands and is written down in the test that
 * guards it, so a change that quietly undoes one of them fails here rather than on a sofa.
 */
public class CozyAudioTest {

    private static float[] render(int sound, long seed) {
        float[] buffer = new float[CozySynth.sampleCount(sound)];
        int written = CozySynth.render(sound, seed, buffer);
        assertEquals(buffer.length, written);
        return buffer;
    }

    private static float[] render(int sound, long seed, double tilt) {
        float[] buffer = new float[CozySynth.sampleCount(sound)];
        CozySynth.render(sound, seed, buffer, tilt);
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

    private static double rms(float[] buffer) {
        return rms(buffer, 0, buffer.length);
    }

    private static double db(double a, double b) {
        return 20 * Math.log10(Math.max(1e-12, a) / Math.max(1e-12, b));
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

    /**
     * The tilted renders are covered too: a player's pitch multiplies every frequency in a voice,
     * which is exactly the kind of change that can push a partial into the last few samples and
     * put a click on the end of a sound that used to be clean.
     */
    @Test
    public void aTiltedSoundIsStillClickFree() {
        for (int sound = 0; sound < CozySynth.SOUND_COUNT; sound++) {
            for (double tilt : new double[] {2.0 / 3.0, .7937005259840998, CozySynth.FIFTH, 2.0}) {
                float[] buffer = render(sound, 4242, tilt);
                int last = buffer.length - 1;
                double budget = CozySynth.peakOf(sound) * .10;
                assertEquals("sound " + sound + " starts hot at tilt " + tilt,
                        0f, buffer[0], 1e-9f);
                assertEquals("sound " + sound + " ends hot at tilt " + tilt,
                        0f, buffer[last], 1e-9f);
                assertTrue("sound " + sound + " clicks off at tilt " + tilt,
                        rms(buffer, last - 7, last + 1) < budget);
                assertTrue("sound " + sound + " left its budget at tilt " + tilt,
                        peak(buffer) <= CozySynth.peakOf(sound) + 1e-6);
            }
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
        // A refusal is softer than an apology, which is softer than an answer.
        assertTrue(CozySynth.peakOf(CozySynth.NUDGE) < CozySynth.peakOf(CozySynth.ERROR));
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

    // ---- Two players -----------------------------------------------------------------

    /**
     * Rose and Sky are a perfect fifth apart, and it is the same fifth every time. The interval is
     * safe because the score is F major pentatonic: Rose's fill lands on F4 and Sky's on C5, so
     * two people pressing at the same instant make a fifth rather than a clash.
     */
    @Test
    public void roseAndSkySoundAFifthApart() {
        assertEquals(CozySynth.cents(700), CozySynth.FIFTH, 1e-12);
        assertEquals(1.0, CozySynth.playerTilt(CozySfx.ROSE), 0.0);
        assertEquals(CozySynth.FIFTH, CozySynth.playerTilt(CozySfx.SKY), 0.0);
        for (int sound : new int[] {CozySynth.FILL, CozySynth.MOVE, CozySynth.SELECT,
                CozySynth.ERROR, CozySynth.NUDGE}) {
            double rose = fundamental(render(sound, 4242, CozySfx.tiltFor(sound, CozySfx.ROSE, 0)));
            double sky = fundamental(render(sound, 4242, CozySfx.tiltFor(sound, CozySfx.SKY, 0)));
            assertEquals("sound " + sound + " does not move a fifth for Sky",
                    CozySynth.FIFTH, sky / rose, .02);
        }
    }

    /**
     * Except for the two noise voices, which take 45% of the interval. Noise has no pitch to move,
     * only a colour, and a whole fifth turns the pencil into a different pencil.
     */
    @Test
    public void theNoiseVoicesOnlyTakePartOfTheInterval() {
        for (int sound : new int[] {CozySynth.CROSS, CozySynth.CLEAR}) {
            double tilt = CozySfx.tiltFor(sound, CozySfx.SKY, 0);
            assertEquals("sound " + sound + " should be damped", 1.2242, tilt, .001);
        }
    }

    /** A win, a hint, a line and a controller arriving belong to the room, not to a person. */
    @Test
    public void theRoomsOwnEventsBelongToNobody() {
        for (int sound : new int[] {CozySynth.WIN, CozySynth.JOIN, CozySynth.HINT,
                CozySynth.LINE}) {
            assertEquals("sound " + sound + " must not carry a player's pitch",
                    1.0, CozySfx.tiltFor(sound, CozySfx.SKY, 0), 0.0);
        }
    }

    /**
     * A run of fills walks up the F major pentatonic and stops at the octave, so a six-square run
     * is a small rising phrase rather than six identical thocks.
     */
    @Test
    public void aRunOfFillsClimbsThePentatonic() {
        double[] expected = {1, 392.00 / 349.23, 440.00 / 349.23, 523.25 / 349.23,
                587.33 / 349.23, 2};
        double previous = 0;
        for (int step = 0; step < expected.length; step++) {
            double tilt = CozySfx.tiltFor(CozySynth.FILL, CozySfx.ROSE, 4 + step);
            assertEquals("run step " + step, expected[step], tilt, .001);
            assertTrue("the run must always climb", tilt > previous);
            previous = tilt;
        }
        // It never runs away: past the top of the table every further fill stays on the octave.
        assertEquals(2.0, CozySfx.tiltFor(CozySynth.FILL, CozySfx.ROSE, 4 + 9), .001);
        // Sky's run is the same shape, a fifth up.
        assertEquals(2 * CozySynth.FIFTH, CozySfx.tiltFor(CozySynth.FILL, CozySfx.SKY, 4 + 5),
                .001);
    }

    /**
     * The cursor tick walks a semitone per step within three semitones of D5, and a cursor that
     * wrapped round the board instead of stepping drops a fifth. On a 20x20 board a wrap is
     * otherwise completely invisible, which is the whole reason it has a sound of its own.
     */
    @Test
    public void theCursorTickWalksAndSaysSoWhenItWraps() {
        for (int step = -3; step <= 3; step++) {
            assertEquals("walk step " + step, Math.pow(2, step / 12.0),
                    CozySfx.tiltFor(CozySynth.MOVE, CozySfx.ROSE, 4 + 3 + step), 1e-9);
        }
        assertEquals(2.0 / 3.0, CozySfx.tiltFor(CozySynth.MOVE, CozySfx.ROSE, 1), 1e-9);
        assertEquals(.7937005259840998,
                CozySfx.tiltFor(CozySynth.SELECT, CozySfx.ROSE, 2), 1e-9);
    }

    // ---- Telling one sound from another -----------------------------------------------

    /**
     * A hint <em>opens something up</em> and a finished line <em>closes</em>. They used to be the
     * same ascending pentatonic bell run under the same gold on screen, and they measured as the
     * closest pair in the whole set: 4.26 dB per band apart, with 0.44 octaves between their
     * gestures. The line falls onto the tonic now, which puts them 23 dB and 2.1 octaves apart.
     */
    @Test
    public void aHintClimbsAndALineSettles() {
        float[] hint = render(CozySynth.HINT, 4242);
        float[] line = render(CozySynth.LINE, 4242);
        assertTrue("a hint must climb: " + slope(hint), slope(hint) > slope(line) + .4);
        assertTrue("a line must settle onto the tonic: " + slope(line), slope(line) < -.5);
        assertTrue("hint and line are still the same gesture",
                trajectoryDistance(hint, line) > 1.2);
        assertTrue("hint and line are still the same timbre",
                profileDistance(bandProfile(hint), bandProfile(line)) > 12);
    }

    /**
     * Crossing is the most repeated deliberate action in a nonogram — most of a 20x20 board ends
     * up with an X on it — and it used to put 77% of its energy in 2–6 kHz, the ear's presence
     * peak and the most fatiguing region there is. That is 36% now, and every other voice in the
     * set is well clear of the band.
     */
    @Test
    public void crossHasLeftTheEarsFatigueBand() {
        float[] cross = render(CozySynth.CROSS, 4242);
        double share = bandShare(cross, 2000, 6000);
        assertTrue("CROSS is back in the fatigue band: " + share, share <= .40);
        double centre = centroid(cross);
        assertTrue("CROSS has gone too bright again: " + centre, centre < 3800);
        assertTrue("CROSS has lost its bite: " + centre, centre > 2600);
        for (int sound = 0; sound < CozySynth.SOUND_COUNT; sound++) {
            if (sound != CozySynth.CROSS) {
                assertTrue("sound " + sound + " has moved into the fatigue band",
                        bandShare(render(sound, 4242), 2000, 6000) < .40);
            }
        }
        // CROSS and CLEAR are both noise gestures; they must stay distinct from each other.
        assertTrue(profileDistance(bandProfile(cross), bandProfile(render(CozySynth.CLEAR, 4242)))
                > 7);
    }

    /**
     * The cursor tick is the most frequent sound in the game and it used to lose to its own
     * background music: measured over 24 points of the real score it lifted the mix by 2.97 dB at
     * full gain and 0.87 dB at the gain a held D-pad sags it to, against 4.6–9.6 dB for
     * everything else. A louder peak target, a higher floor on the sag and a base note that sits
     * in the score's own key now put the held case at 2.8 dB on the ear the player is panned
     * towards, which is more than the old tick managed at full strength.
     */
    @Test
    public void theCursorTickCanBeHeardOverTheMusic() {
        CozyScore score = new CozyScore(CozySynth.SAMPLE_RATE, 99);
        int spot = CozySynth.SAMPLE_RATE;
        float[] bed = leftChannel(renderScore(score, spot * 13, 1024));
        float[] tick = render(CozySynth.MOVE, 4242, CozySfx.tiltFor(CozySynth.MOVE, CozySfx.ROSE,
                0));
        double held = .60 + .40 * Math.pow(50 / 260.0, 2);
        double pan = CozySynth.panLeft(-.42);
        assertTrue("the sag floor has been lowered again: " + held, held > .55);
        double lift = liftOverBed(bed, tick, pan, spot);
        double sagged = liftOverBed(bed, tick, held * pan, spot);
        assertTrue("a deliberate tick is masked by the music: " + lift, lift > 4);
        assertTrue("a held-D-pad tick is masked by the music: " + sagged, sagged > 2);
    }

    // ---- The mixer -------------------------------------------------------------------

    /**
     * Taking a slot from a live voice used to stop its samples dead and start the replacement at
     * zero, which is a step to silence from wherever the waveform happened to be. Measured on a
     * bus of nothing but nudges — the one voice in the set with no noise in it, so every sample
     * step is accounted for — a one-sample cut raises the worst step from 0.058 to 0.089, and the
     * 64-sample raised cosine leaves it at 0.058 exactly, whether one voice is stolen or six.
     */
    @Test
    public void stealingAVoiceDoesNotClick() {
        double clean = worstStepUnderSteals(0);
        for (int steals = 1; steals <= 6; steals++) {
            assertEquals(steals + " steals put a step in the bus", clean,
                    worstStepUnderSteals(steals), 1e-6);
        }
    }

    private static double worstStepUnderSteals(int steals) {
        CozySfx sfx = new CozySfx();
        CozySfx.Sound[] sounds = new CozySfx.Sound[12 + steals];
        int[] who = new int[sounds.length];
        int[] when = new int[sounds.length];
        for (int i = 0; i < sounds.length; i++) {
            sounds[i] = CozySfx.Sound.NUDGE;
            // The thieves arrive 35 ms in, by which point their victims are at full amplitude,
            // which is where a hard cut would be loudest. Arriving together proves nothing: a
            // voice taken at position 0 is silent and cannot click.
            when[i] = i < 12 ? 0 : 6 + (i - 12);
        }
        float[] bus = sfx.renderBusForTest(sounds, who, when, 30);
        double worst = 0;
        for (int i = 2; i < bus.length; i++) {
            worst = Math.max(worst, Math.abs(bus[i] - bus[i - 2]));
        }
        return worst;
    }

    /** Twenty overlapping requests from two players must not clip, whatever they are. */
    @Test
    public void theMixerStaysUnderTheSfxCeiling() {
        CozySfx sfx = new CozySfx();
        CozySfx.Sound[] pile = new CozySfx.Sound[20];
        int[] who = new int[20];
        int[] when = new int[20];
        for (int i = 0; i < pile.length; i++) {
            pile[i] = CozySfx.Sound.values()[i % CozySynth.SOUND_COUNT];
            who[i] = i % 2;
            when[i] = i / 4;
        }
        float[] bus = sfx.renderBusForTest(pile, who, when, 60);
        for (float value : bus) {
            assertFalse("NaN on the bus", Float.isNaN(value));
            assertTrue("the bus left its ceiling",
                    Math.abs(CozySynth.limit(value, CozySynth.SFX_CEILING))
                            < CozySynth.SFX_CEILING);
        }
    }

    /**
     * Rose sits to the left of the room and Sky to the right, by a measured 4.3 dB once the room's
     * own reflections have narrowed the image back down. Two people working on one board should
     * be two places, not one.
     */
    @Test
    public void thePlayersSitInDifferentPlaces() {
        double roseBias = channelBias(CozySfx.ROSE);
        double skyBias = channelBias(CozySfx.SKY);
        assertTrue("Rose is not to the left: " + roseBias, roseBias > 3);
        assertTrue("Sky is not to the right: " + skyBias, skyBias < -3);
        // Not exactly zero: the room's right-hand delay lines are 23 samples longer than its
        // left, which is where the width comes from, and over a short window that shows up as
        // about a decibel. It must stay small enough that a centred sound reads as centred.
        assertEquals("a menu press belongs to the room, not to a side",
                0, channelBias(CozySfx.ROOM), 2);
    }

    /** How far left of centre a player's fill sits, in dB. */
    private static double channelBias(int who) {
        CozySfx sfx = new CozySfx();
        float[] bus = sfx.renderBusForTest(new CozySfx.Sound[] {CozySfx.Sound.FILL},
                new int[] {who}, new int[] {0}, 40);
        return db(rms(leftChannel(bus)), rms(rightChannel(bus)));
    }

    /**
     * The win fanfare has to keep pace with the picture. {@code WinScene}'s beat sheet lands the
     * invitation at 950 ms and the last hint at 1120, and the old seven-bell roll had no new
     * audio event after 700 ms — measured, its energy over 1100–1300 ms was 57% of its energy
     * over 800–1000 ms, so those beats arrived into a decaying tail. Two more bells at 950 and
     * 1180 ms bring that to 84%.
     */
    @Test
    public void theWinFanfareKeepsPaceWithTheWinScene() {
        float[] win = render(CozySynth.WIN, 4242);
        int rate = CozySynth.SAMPLE_RATE / 1000;
        double early = rms(win, 800 * rate, 1000 * rate);
        double late = rms(win, 1100 * rate, 1300 * rate);
        assertTrue("the fanfare is over before the win scene is: " + late / early,
                late > early * .75);
        assertTrue("the fanfare needs room for its last bell to ring",
                CozySynth.durationMs(CozySynth.WIN) >= 2000);
    }

    // ---- The score -------------------------------------------------------------------

    /** Renders interleaved stereo: {@code out} holds two floats per frame. */
    private static float[] renderScore(CozyScore score, int frames, int block) {
        float[] out = new float[frames * 2];
        for (int i = 0; i < frames; i += block) {
            score.render(out, i, Math.min(block, frames - i));
        }
        return out;
    }

    private static float[] leftChannel(float[] stereo) {
        return oneChannel(stereo, 0);
    }

    private static float[] rightChannel(float[] stereo) {
        return oneChannel(stereo, 1);
    }

    private static float[] oneChannel(float[] stereo, int which) {
        float[] out = new float[stereo.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = stereo[i * 2 + which];
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
        assertTrue(rms(out, 0, 512) < rms(out, CozySynth.SAMPLE_RATE * 40,
                CozySynth.SAMPLE_RATE * 40 + 512) + 1e-9);
        assertEquals(0f, out[0], 1e-6f);
        assertEquals(0f, out[1], 1e-6f);
    }

    /**
     * The limiter must stay a safety net rather than part of the sound. Measured at the loudest
     * the arrangement ever gets — a full picture, two players, four minutes in — the music bus
     * peaks at 0.220 against a knee of 0.238, so the limiter is never reached. The reverb and the
     * bigger intensity swing were both paid for out of the headroom that stereo panning freed up.
     */
    @Test
    public void theLoudestTheScoreEverGetsStillNeverTouchesTheLimiter() {
        CozyScore score = new CozyScore(CozySynth.SAMPLE_RATE, 99);
        score.setIntensity(1f);
        score.setPresence(2);
        float[] out = renderScore(score, CozySynth.SAMPLE_RATE * 150, 1024);
        double loudest = peak(out);
        double knee = CozySynth.MUSIC_CEILING * .70;
        assertTrue("the score is now leaning on the limiter: " + loudest, loudest < knee);
        assertTrue("the score has gone quiet: " + loudest, loudest > knee * .5);
    }

    @Test
    public void theScoreRendersTheSameWhateverBlockSizeTheDeviceAsksFor() {
        int frames = CozySynth.SAMPLE_RATE * 12;
        float[] big = renderScore(new CozyScore(CozySynth.SAMPLE_RATE, 7), frames, 4096);
        float[] small = renderScore(new CozyScore(CozySynth.SAMPLE_RATE, 7), frames, 337);
        for (int i = 0; i < frames * 2; i++) {
            assertEquals("block size changed the music at sample " + i, big[i], small[i], 0f);
        }
    }

    /**
     * Stereo has to be an improvement on a soundbar without being a problem on a television that
     * folds it back down to one speaker. The two channels correlate at 0.82 — genuinely different,
     * not a trick — and are within a decibel of each other, so nothing important lives on one side.
     */
    @Test
    public void theScoreIsWideAndStillSafeInMono() {
        CozyScore score = new CozyScore(CozySynth.SAMPLE_RATE, 99);
        score.setIntensity(.6f);
        renderScore(score, CozySynth.SAMPLE_RATE * 8, 1024);
        float[] out = renderScore(score, CozySynth.SAMPLE_RATE * 30, 1024);
        float[] left = leftChannel(out);
        float[] right = rightChannel(out);
        double correlation = 0;
        for (int i = 0; i < left.length; i++) {
            correlation += left[i] * (double) right[i];
        }
        correlation /= left.length * rms(left) * rms(right);
        assertTrue("the score is mono in all but name: " + correlation, correlation < .95);
        assertTrue("the two channels have come apart: " + correlation, correlation > .5);
        assertEquals("one ear is getting a different piece", 0, db(rms(left), rms(right)), 1.5);
    }

    @Test
    public void celebrateLiftsTheArrangementAndThenSettlesBack() {
        int window = CozySynth.SAMPLE_RATE * 4;
        CozyScore plain = new CozyScore(CozySynth.SAMPLE_RATE, 5150);
        CozyScore party = new CozyScore(CozySynth.SAMPLE_RATE, 5150);
        renderScore(plain, CozySynth.SAMPLE_RATE * 30, 1024);
        renderScore(party, CozySynth.SAMPLE_RATE * 30, 1024);

        party.celebrate();
        double quietDuring = rms(renderScore(plain, window, 1024));
        double loudDuring = rms(renderScore(party, window, 1024));
        assertTrue("celebrate() did not lift the score: " + quietDuring + " -> " + loudDuring,
                loudDuring > quietDuring * 1.08);

        renderScore(plain, CozySynth.SAMPLE_RATE * 20, 1024);
        renderScore(party, CozySynth.SAMPLE_RATE * 20, 1024);
        double quietAfter = rms(renderScore(plain, window, 1024));
        double loudAfter = rms(renderScore(party, window, 1024));
        assertTrue("celebrate() never settled back: " + quietAfter + " -> " + loudAfter,
                loudAfter < quietAfter * 1.25);
    }

    /**
     * Filling in a picture used to move the mix by a measured 0.45 dB across the whole range of
     * {@code setIntensity} and the spectral centroid by 4 Hz — the one hook wired to gameplay did
     * nothing anybody could hear. It is 2.6 dB and 660 Hz now: the felt piano and the rain come
     * in, an octave shimmer joins the tune past the halfway mark, and the bell and bass scale
     * rather than add so that an untouched board is the quietest the room ever gets.
     */
    @Test
    public void intensityIsSomethingYouCanHear() {
        double[] level = new double[2];
        double[] brightness = new double[2];
        for (int end = 0; end < 2; end++) {
            CozyScore score = new CozyScore(CozySynth.SAMPLE_RATE, 99);
            score.setIntensity(end);
            renderScore(score, CozySynth.SAMPLE_RATE * 8, 1024);
            float[] out = renderScore(score, CozySynth.SAMPLE_RATE * 40, 1024);
            level[end] = rms(out);
            brightness[end] = centroid(leftChannel(out));
        }
        assertTrue("intensity barely moves the level: " + db(level[1], level[0]),
                db(level[1], level[0]) >= 2.0);
        assertTrue("intensity barely moves the colour: " + (brightness[1] - brightness[0]),
                brightness[1] - brightness[0] >= 200);
    }

    /**
     * The written form is 3:37 and used to be the true one: measured over a 460-second render, the
     * band-energy self-similarity peaked at 0.954 at exactly one form length and 0.969 at two,
     * far above every other lag. Each pass is transposed now — F, Bb, Eb, C — so the same six
     * seconds one pass later is in a different key, and those two peaks fall to 0.645 and 0.768,
     * which is no higher than musically unrelated lags. Twenty minutes on a sofa is 5.5 passes.
     */
    @Test
    public void everyPassOfTheFormIsInADifferentKey() {
        CozyScore score = new CozyScore(CozySynth.SAMPLE_RATE, 0xC0DEBA5EL);
        double form = CozyScore.formSeconds(CozySynth.SAMPLE_RATE);
        float[] first = windowAt(score, 20, 6);
        float[] second = windowAt(score, 20 + form, 6);
        double ratio = centroid(second) / centroid(first);
        assertTrue("one pass later the piece is in the same key: " + ratio,
                Math.abs(ratio - 1) > .12);
        assertTrue("the two passes are the same material: "
                        + profileDistance(bandProfile(first), bandProfile(second)),
                profileDistance(bandProfile(first), bandProfile(second)) > 3);
    }

    /** Renders forward one second at a time and keeps only the window asked for. */
    private static float[] windowAt(CozyScore score, double startSeconds, double seconds) {
        float[] chunk = new float[CozySynth.SAMPLE_RATE * 2];
        int frames = (int) (CozySynth.SAMPLE_RATE * seconds);
        float[] out = new float[frames];
        int position = 0;
        int until = (int) (startSeconds * CozySynth.SAMPLE_RATE);
        while (position < until) {
            int step = Math.min(CozySynth.SAMPLE_RATE, until - position);
            score.render(chunk, 0, step);
            position += step;
        }
        for (int done = 0; done < frames; done += CozySynth.SAMPLE_RATE) {
            int step = Math.min(CozySynth.SAMPLE_RATE, frames - done);
            score.render(chunk, 0, step);
            for (int i = 0; i < step; i++) {
                out[done + i] = (chunk[i * 2] + chunk[i * 2 + 1]) * .5f;
            }
        }
        return out;
    }

    @Test
    public void theFormIsLongEnoughToLiveWith() {
        double seconds = CozyScore.formSeconds(CozySynth.SAMPLE_RATE);
        assertTrue("the loop is too short to survive a co-op session: " + seconds,
                seconds > 180);
        assertTrue(seconds < 300);
    }

    /**
     * Switching music back on used to resume mid-phrase at full level — the last sample before a
     * stop measured 0.0483 and the first sample after the restart 0.0478, a step straight to 14%
     * of the ceiling. A fresh score fades up over 5.2 s, which is right on a cold start and would
     * feel broken on a settings toggle, so a resume winds the fade back to 0.35 and arrives over
     * about 1.7 s instead.
     */
    @Test
    public void theMusicFadesBackInAfterAStop() {
        CozyScore score = new CozyScore(CozySynth.SAMPLE_RATE, 99);
        float[] warm = renderScore(score, CozySynth.SAMPLE_RATE * 40, 1024);
        double steady = rms(warm, warm.length - CozySynth.SAMPLE_RATE, warm.length);
        score.resumeFade();
        float[] back = renderScore(score, CozySynth.SAMPLE_RATE * 4, 1024);
        double opening = rms(back, 0, CozySynth.SAMPLE_RATE / 5 * 2);
        assertTrue("the music snapped back on: " + opening / steady, opening < steady * .5);
        double later = rms(back, CozySynth.SAMPLE_RATE * 3 * 2,
                (CozySynth.SAMPLE_RATE * 3 + CozySynth.SAMPLE_RATE / 5) * 2);
        assertTrue("the music never came back: " + later / steady, later > steady * .7);
    }

    /**
     * Left alone the arrangement recedes; the first keypress brings it back. Two identical scores
     * rendered in lockstep, one nudged every second and one not, so the measurement sees the hush
     * and not whichever section it happens to land in. The drop is a deliberate 2.3 dB and only
     * the bell, piano and rain layers move — a pair thinking hard about a 20x20 board must never
     * wonder whether the app has died.
     */
    @Test
    public void theArrangementRecedesWhenTheRoomGoesStill() {
        CozyScore awake = new CozyScore(CozySynth.SAMPLE_RATE, 99);
        CozyScore alone = new CozyScore(CozySynth.SAMPLE_RATE, 99);
        awake.setIntensity(.5f);
        alone.setIntensity(.5f);
        for (int second = 0; second < 90; second++) {
            awake.nudge();
            renderScore(awake, CozySynth.SAMPLE_RATE, 1024);
            renderScore(alone, CozySynth.SAMPLE_RATE, 1024);
        }
        awake.nudge();
        int window = CozySynth.SAMPLE_RATE * 6;
        double busy = rms(renderScore(awake, window, 1024));
        double still = rms(renderScore(alone, window, 1024));
        assertTrue("the room never quietened: " + db(still, busy), db(still, busy) < -1.5);
        assertTrue("the room went too quiet to trust: " + db(still, busy), db(still, busy) > -6);

        alone.nudge();
        renderScore(awake, CozySynth.SAMPLE_RATE * 5, 1024);
        renderScore(alone, CozySynth.SAMPLE_RATE * 5, 1024);
        awake.nudge();
        double busyAgain = rms(renderScore(awake, window, 1024));
        double back = rms(renderScore(alone, window, 1024));
        assertTrue("a keypress did not bring the music back: " + db(back, busyAgain),
                db(back, busyAgain) > -1);
    }

    /** The room must decay to nothing and must not run away, however hard it is driven. */
    @Test
    public void theRoomDecaysAndNeverRunsAway() {
        CozySynth.Room room = new CozySynth.Room(1.0);
        float[] impulse = new float[CozySynth.SAMPLE_RATE * 8];
        impulse[0] = 1;
        impulse[1] = 1;
        room.process(impulse, 0, impulse.length / 2);
        impulse[0] = 0;
        impulse[1] = 0;
        double top = peak(impulse);
        int last = 0;
        for (int i = 0; i < impulse.length; i++) {
            if (Math.abs(impulse[i]) > top * .001) {
                last = i / 2;
            }
        }
        double rt60 = last / (double) CozySynth.SAMPLE_RATE;
        assertTrue("the room has become a hall: " + rt60, rt60 < 1.6);
        assertTrue("the room has no tail at all: " + rt60, rt60 > .4);

        CozySynth.Room driven = new CozySynth.Room(.22);
        float[] block = new float[2048];
        double worst = 0;
        for (int b = 0; b < 400; b++) {
            for (int i = 0; i < block.length; i++) {
                block[i] = (float) CozySynth.noise(b * 31L + 7, i);
            }
            driven.process(block, 0, 1024);
            worst = Math.max(worst, peak(block));
        }
        assertTrue("the room ran away under full-scale noise: " + worst, worst < 3);

        // Emptying it puts it back exactly where it started, which is what a flushed track needs.
        CozySynth.Room reused = new CozySynth.Room(.22);
        float[] first = new float[512];
        first[0] = 1;
        reused.process(first, 0, 256);
        reused.clear();
        float[] again = new float[512];
        again[0] = 1;
        reused.process(again, 0, 256);
        for (int i = 0; i < first.length; i++) {
            assertEquals("clear() left something behind", first[i], again[i], 0f);
        }
    }

    // ---- Measurement -----------------------------------------------------------------
    //
    // A small radix-2 FFT and the three things built on it. Claims about how something sounds
    // are only worth making if they can be checked, and every spectral assertion above is
    // checked with these.

    private static double[] spectrum(float[] x) {
        int n = Integer.highestOneBit(Math.max(2, x.length - 1)) * 2;
        double[] re = new double[n];
        double[] im = new double[n];
        for (int i = 0; i < x.length; i++) {
            re[i] = x[i] * (.5 - .5 * Math.cos(2 * Math.PI * i / x.length));
        }
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double wr = Math.cos(-2 * Math.PI / len);
            double wi = Math.sin(-2 * Math.PI / len);
            for (int i = 0; i < n; i += len) {
                double cr = 1;
                double ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k;
                    int b = a + len / 2;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    double nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = nr;
                }
            }
        }
        double[] power = new double[n / 2];
        for (int i = 0; i < n / 2; i++) {
            power[i] = re[i] * re[i] + im[i] * im[i];
        }
        return power;
    }

    private static double binHz(int bin, int bins) {
        return bin * (double) CozySynth.SAMPLE_RATE / (bins * 2);
    }

    /** Magnitude-weighted mean frequency: how bright the sound is, in Hz. */
    private static double centroid(float[] x) {
        double[] power = spectrum(x);
        double num = 0;
        double den = 0;
        for (int i = 1; i < power.length && binHz(i, power.length) <= 16000; i++) {
            double magnitude = Math.sqrt(power[i]);
            num += binHz(i, power.length) * magnitude;
            den += magnitude;
        }
        return den <= 0 ? 0 : num / den;
    }

    /** The strongest partial, which for these voices is the note being played. */
    private static double fundamental(float[] x) {
        double[] power = spectrum(x);
        int best = 1;
        for (int i = 2; i < power.length && binHz(i, power.length) <= 8000; i++) {
            if (power[i] > power[best]) {
                best = i;
            }
        }
        return binHz(best, power.length);
    }

    private static double bandShare(float[] x, double low, double high) {
        double[] power = spectrum(x);
        double inside = 0;
        double all = 0;
        for (int i = 1; i < power.length && binHz(i, power.length) <= 16000; i++) {
            all += power[i];
            if (binHz(i, power.length) >= low && binHz(i, power.length) <= high) {
                inside += power[i];
            }
        }
        return all <= 0 ? 0 : inside / all;
    }

    /**
     * Ten log-spaced bands from 80 Hz to 16 kHz, each relative to the loudest and floored 40 dB
     * down so an empty band cannot dominate the distance between two sounds. This is the
     * fingerprint that says whether two effects are the same colour.
     */
    private static double[] bandProfile(float[] x) {
        double[] power = spectrum(x);
        double[] band = new double[10];
        for (int i = 1; i < power.length; i++) {
            double hz = binHz(i, power.length);
            if (hz < 80 || hz > 16000) {
                continue;
            }
            int slot = (int) (10 * Math.log(hz / 80) / Math.log(16000 / 80.0));
            if (slot >= 0 && slot < 10) {
                band[slot] += power[i];
            }
        }
        double loudest = 1e-12;
        for (double value : band) {
            loudest = Math.max(loudest, value);
        }
        double[] out = new double[10];
        for (int i = 0; i < 10; i++) {
            out[i] = Math.max(-40, 10 * Math.log10(Math.max(1e-12, band[i]) / loudest));
        }
        return out;
    }

    private static double profileDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs(a[i] - b[i]);
        }
        return sum / a.length;
    }

    /** The centroid of each sixth of a sound, in octaves — the shape of the gesture. */
    private static double[] trajectory(float[] x) {
        double[] out = new double[6];
        int span = x.length / 6;
        float[] slice = new float[span];
        for (int s = 0; s < 6; s++) {
            System.arraycopy(x, s * span, slice, 0, span);
            out[s] = Math.log(Math.max(20, centroid(slice))) / Math.log(2);
        }
        return out;
    }

    /** How far the sound climbs (positive) or settles (negative), end to end, in octaves. */
    private static double slope(float[] x) {
        double[] shape = trajectory(x);
        return shape[shape.length - 1] - shape[0];
    }

    private static double trajectoryDistance(float[] a, float[] b) {
        double[] p = trajectory(a);
        double[] q = trajectory(b);
        double sum = 0;
        for (int i = 0; i < p.length; i++) {
            sum += Math.abs(p[i] - q[i]);
        }
        return sum / p.length;
    }

    /** Peak lift, in dB, of one voice dropped onto twelve different points of the score. */
    private static double liftOverBed(float[] bed, float[] voice, double gain, int spot) {
        int window = CozySynth.SAMPLE_RATE / 10;
        double sum = 0;
        for (int k = 0; k < 12; k++) {
            int at = spot * (k + 1);
            double before = 0;
            double after = 0;
            for (int i = 0; i < window; i++) {
                before = Math.max(before, Math.abs(bed[at + i]));
                after = Math.max(after, Math.abs(bed[at + i]
                        + (i < voice.length ? (float) (voice[i] * gain) : 0)));
            }
            sum += db(after, before);
        }
        return sum / 12;
    }
}

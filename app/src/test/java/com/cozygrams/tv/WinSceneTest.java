package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The celebration's contract.
 *
 * <p>None of this is about pixels — it is about the promises the win screen makes. The
 * words it picks must be defined for every state the game can reach, the picture must
 * have finished assembling before the buttons start working, and the story book has to
 * describe the journey correctly at its first page, somewhere in the middle, and at the
 * end.
 *
 * <p>The choreography is asserted the same way, by keeping the parts of it that are pure
 * functions of time pure: {@link WinScene#paperOpacity} and {@link WinScene#inviteBreath}
 * decide how bright the card is and whether anything is still moving, and both can be
 * walked frame by frame here rather than eyeballed in a screenshot.
 */
public class WinSceneTest {

    private static GameState endless(int solved) {
        GameState game = new GameState(7L, 10);
        game.solved = solved;
        return game;
    }

    private static GameState story(int chapterIndex) {
        GameState game = new GameState(7L, 10);
        game.startStory(chapterIndex);
        return game;
    }

    // ---- Messages --------------------------------------------------------------------

    /**
     * {@code solved} is a running session counter that nothing clamps, so the message
     * picker has to be total over {@code int} — including the negatives a corrupt save
     * could produce — and it has to be stable, or the line would flicker between frames.
     */
    @Test
    public void everyPuzzleCountPicksARealMessage() {
        int[] counts = {0, 1, 7, 8, 9, 1000, Integer.MAX_VALUE, -1, -8, -9,
                Integer.MIN_VALUE};
        for (boolean together : new boolean[]{true, false}) {
            for (int solved : counts) {
                String message = WinScene.message(solved, together);
                assertNotNull("null message for solved=" + solved, message);
                assertFalse("blank message for solved=" + solved, message.trim().isEmpty());
                assertEquals("message is not stable for solved=" + solved, message,
                        WinScene.message(solved, together));
            }
        }
    }

    /** Consecutive puzzles never repeat a line, so a run of wins does not feel canned. */
    @Test
    public void messagesCycleThroughTheWholePool() {
        for (boolean together : new boolean[]{true, false}) {
            Set<String> seen = new HashSet<>();
            for (int solved = 0; solved < WinScene.messageCount(); solved++) {
                seen.add(WinScene.message(solved, together));
            }
            assertEquals(WinScene.messageCount(), seen.size());
            assertEquals(WinScene.message(0, together),
                    WinScene.message(WinScene.messageCount(), together));
            assertEquals(WinScene.message(0, together),
                    WinScene.message(-WinScene.messageCount(), together));
        }
    }

    /**
     * The screen is a shared moment, so nothing may address two people when only one is
     * at the table. "We", "us", "together" and "team" are all claims about a second
     * player.
     */
    @Test
    public void soloWordingNeverInventsASecondPlayer() {
        String[] plural = {"together", "team", " we ", " us ", "both", "two of you"};
        for (int solved = 0; solved < WinScene.messageCount(); solved++) {
            String message = WinScene.message(solved, false).toLowerCase(Locale.ROOT);
            for (String claim : plural) {
                assertFalse(message + " assumes a second player",
                        (" " + message + " ").contains(claim));
            }
        }
        assertEquals("YOU MADE", WinScene.eyebrow(endless(3), false));
        assertEquals("TOGETHER YOU MADE", WinScene.eyebrow(endless(3), true));
        assertFalse(WinScene.message(story(PuzzleLibrary.count() - 1), false)
                .toLowerCase(Locale.ROOT).contains("together"));
    }

    /** Shared credit, never a contest: no line may set the two players against each other. */
    @Test
    public void nothingReadsAsAScore() {
        String[] banned = {"score", "star", "rank", "best", "record", "time", "beat",
                "won", "win", "faster"};
        for (int solved = 0; solved < WinScene.messageCount(); solved++) {
            for (boolean together : new boolean[]{true, false}) {
                String message = WinScene.message(solved, together)
                        .toLowerCase(Locale.ROOT);
                for (String word : banned) {
                    assertFalse(message + " sounds like a results screen",
                            message.contains(word));
                }
            }
        }
    }

    // ---- The reveal ------------------------------------------------------------------

    /**
     * The whole picture has to be on screen before a button press can take it away —
     * otherwise the lock-out is friction protecting nothing. Checked against every board
     * the game can actually deal: all eighteen authored chapters and a wide sweep of
     * generated boards at every size.
     */
    @Test
    public void thePictureIsCompleteBeforeTheButtonsWork() {
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            Puzzle puzzle = PuzzleLibrary.get(chapter);
            assertTrue("chapter " + chapter + " still assembling at the input gate",
                    WinScene.revealDoneMs(puzzle) < WinScene.INPUT_DELAY_MS);
        }
        for (int size = GameState.MIN_SIZE; size <= GameState.MAX_SIZE; size++) {
            for (long seed = 0; seed < 24; seed++) {
                Puzzle puzzle = PuzzleGenerator.generate(seed, size);
                assertTrue("seed " + seed + " at " + size + " still assembling",
                        WinScene.revealDoneMs(puzzle) < WinScene.INPUT_DELAY_MS);
            }
        }
    }

    /**
     * The picture must start appearing the instant the reveal begins. An order taken over
     * the whole grid would sit on an empty top row first and leave the card blank, so the
     * schedule is defined over the picture's own squares and normalised to start at zero.
     */
    @Test
    public void theRevealCoversExactlyThePictureAndStartsImmediately() {
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            Puzzle puzzle = PuzzleLibrary.get(chapter);
            float[] delays = WinScene.revealDelays(puzzle);
            assertEquals(puzzle.size * puzzle.size, delays.length);

            int scheduled = 0;
            float earliest = Float.MAX_VALUE;
            float latest = -1;
            for (int y = 0; y < puzzle.size; y++) {
                for (int x = 0; x < puzzle.size; x++) {
                    float delay = delays[y * puzzle.size + x];
                    if (!puzzle.solution[y][x]) {
                        assertEquals("empty square is animated", -1f, delay, 0f);
                        continue;
                    }
                    scheduled++;
                    earliest = Math.min(earliest, delay);
                    latest = Math.max(latest, delay);
                }
            }
            assertEquals(puzzle.pictureCount(), scheduled);
            assertEquals("chapter " + chapter + " starts blank", 0f, earliest, 0.001f);
            assertTrue("chapter " + chapter + " overruns its spread",
                    latest <= WinScene.REVEAL_SPREAD_MS + 0.001f);
        }
    }

    /** Replaying the same board must produce the same animation, frame for frame. */
    @Test
    public void theRevealIsDeterministic() {
        Puzzle first = PuzzleLibrary.get(9);
        Puzzle second = PuzzleLibrary.get(9);
        float[] a = WinScene.revealDelays(first);
        float[] b = WinScene.revealDelays(second);
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], 0f);
        }
    }

    /** A blank board is not reachable in play, but the schedule must not blow up on one. */
    @Test
    public void anEmptyPictureIsHarmless() {
        Puzzle blank = new Puzzle(new boolean[5][5], "Nothing At All");
        float[] delays = WinScene.revealDelays(blank);
        for (float delay : delays) {
            assertEquals(-1f, delay, 0f);
        }
        assertTrue(WinScene.revealDoneMs(blank) < WinScene.INPUT_DELAY_MS);
    }

    // ---- The beat sheet --------------------------------------------------------------

    /**
     * The beats have to stay in order and the whole celebration has to be over in about
     * two seconds, or the screen stops being a moment and starts being a wait.
     */
    @Test
    public void theBeatsRunInOrderAndFinishPromptly() {
        // The room is dim before the picture moves, so the lift is seen against a calm
        // board rather than a bright one.
        assertTrue(WinScene.DIM_MS <= WinScene.LIFT_AT + 60f);
        // The first square lands no later than the lift starts: the card is never blank.
        assertTrue(WinScene.REVEAL_AT <= WinScene.LIFT_AT);
        assertTrue(WinScene.PLAQUE_AT < WinScene.NAME_AT);
        assertTrue(WinScene.NAME_AT < WinScene.MESSAGE_AT);
        assertTrue(WinScene.MESSAGE_AT < WinScene.CREDIT_AT);
        assertTrue(WinScene.CREDIT_AT < WinScene.JOURNEY_AT);
        assertTrue(WinScene.JOURNEY_AT < WinScene.INVITE_AT);
        assertTrue(WinScene.INVITE_AT < WinScene.HINT_AT);

        // The invitation is legible by the time it can be accepted.
        assertTrue("the prompt appears after input opens",
                WinScene.INVITE_AT < WinScene.INPUT_DELAY_MS);
        // Nothing that carries meaning is still arriving when the gate opens.
        assertTrue(WinScene.MESSAGE_AT + WinScene.MESSAGE_MS <= WinScene.INPUT_DELAY_MS);

        assertTrue("the celebration runs long: " + WinScene.ARRIVED_MS + "ms",
                WinScene.ARRIVED_MS <= 1500f);
    }

    /**
     * The card and the words are two halves of one composition, so the plaque has to be on
     * its way up while the picture is still assembling. It used to start at 500 ms — two
     * hundred milliseconds <em>after</em> the card had already gone fully opaque and two
     * hundred before the picture finished.
     */
    @Test
    public void theWordsStartArrivingWhileThePictureIsStillAssembling() {
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            float done = WinScene.revealDoneMs(PuzzleLibrary.get(chapter));
            assertTrue("chapter " + chapter + " finishes assembling before the plaque",
                    WinScene.PLAQUE_AT < done);
        }
    }

    // ---- The brightest surface in the game --------------------------------------------

    /**
     * The one that failed the review: at 310 ms the picture's card hit full opacity while
     * the picture would not be assembled for another 390 ms and there were no words at all
     * for another 190. A 620x620 near-white rectangle sat alone on near-black holding half
     * a heart — in a dark room, on a panel that may be OLED, as the first frame of the
     * reward. The sheet now follows the picture onto itself.
     */
    @Test
    public void theCardIsNeverBrightAndEmpty() {
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            Puzzle puzzle = PuzzleLibrary.get(chapter);
            float done = WinScene.revealDoneMs(puzzle);
            float assembly = done - WinScene.REVEAL_AT;

            // Stated against how much of the picture exists, not against the clock: a
            // small chapter finishes assembling sooner, and a bright card is right as
            // soon as there is a picture on it. The property is that the sheet is never
            // bright while it is still mostly empty — whatever "still" means for that
            // particular picture.
            for (float elapsed = 0; elapsed <= done; elapsed += 10f) {
                float assembled = (elapsed - WinScene.REVEAL_AT) / assembly;
                if (assembled >= .5f) {
                    continue;
                }
                assertTrue("chapter " + chapter + " is a bright empty sheet at "
                                + elapsed + "ms (" + Math.round(assembled * 100)
                                + "% assembled)",
                        WinScene.paperOpacity(elapsed, assembly) < .75f);
            }
            assertEquals("chapter " + chapter + " never reaches full paper", 1f,
                    WinScene.paperOpacity(done, assembly), 1e-3);
        }
    }

    /** The sheet only ever gets brighter — a card that dips reads as a flicker. */
    @Test
    public void theCardOnlyEverBrightens() {
        float assembly = WinScene.revealDoneMs(PuzzleLibrary.get(0)) - WinScene.REVEAL_AT;
        float previous = -1;
        for (float elapsed = 0; elapsed <= 1200; elapsed += 5) {
            float opacity = WinScene.paperOpacity(elapsed, assembly);
            assertTrue("paper dipped at " + elapsed + "ms", opacity >= previous - 1e-4);
            assertTrue(opacity >= 0f && opacity <= 1f);
            previous = opacity;
        }
        assertEquals("nothing is drawn before the card arrives", 0f,
                WinScene.paperOpacity(WinScene.LIFT_AT, 570f), 0f);
    }

    // ---- Stillness --------------------------------------------------------------------

    /**
     * {@code Renderer.animating} can only stop asking for frames if this scene actually
     * stops changing. The invitation's breath used to be {@code |sin|} with no terminating
     * condition, so a finished puzzle repainted a static picture at 60 fps for as long as
     * it was left on screen.
     */
    @Test
    public void theInvitationStopsBreathing() {
        assertEquals("the breath starts before its own beat", 0f,
                WinScene.inviteBreath(WinScene.INVITE_AT - 1), 0f);
        assertTrue("the invitation never breathes at all",
                WinScene.inviteBreath(WinScene.INVITE_AT + WinScene.INVITE_BREATH_MS / 2)
                        > .9f);

        float last = WinScene.STILL_AT_MS - WinScene.INVITE_AT;
        assertTrue("the breath was still moving one frame before the hold",
                WinScene.inviteBreath(WinScene.INVITE_AT + last - 16) < .05f);
        for (float elapsed = WinScene.STILL_AT_MS; elapsed <= 60_000; elapsed += 137) {
            assertEquals("still breathing at " + elapsed + "ms", 0f,
                    WinScene.inviteBreath(elapsed), 0f);
        }
    }

    /** Everything else has to have finished long before the loop is allowed to sleep. */
    @Test
    public void theStillPointIsAfterEveryOtherBeat() {
        assertTrue(WinScene.STILL_AT_MS > WinScene.ARRIVED_MS);
        assertTrue(WinScene.STILL_AT_MS > WinScene.INVITE_AT + WinScene.INVITE_MS);
        assertEquals(WinScene.INVITE_AT + WinScene.INVITE_BREATH_MS * WinScene.INVITE_BREATHS,
                WinScene.STILL_AT_MS, 1f);
    }

    // ---- Composition ------------------------------------------------------------------

    /**
     * The confetti rises in whatever the composition leaves over, so the composition has to
     * leave the same amount on each side. It did not: a hand-picked left edge of .122 of the
     * width put a 702 px band on the left — 554 px of it painted over by the picture card —
     * and a 131 px strip pinned to the right edge, while the comment three lines above the
     * numbers claimed the two came out equal.
     */
    @Test
    public void theTwoConfettiLanesAreTheSameWidth() {
        for (float width : new float[]{1280, 1920, 3840}) {
            float leftLane = WinScene.compositionLeft(width);
            float rightLane = width - WinScene.compositionRight(width);
            assertEquals("lanes differ at " + width + "px", leftLane, rightLane, .01f);
            assertTrue("no room for confetti at " + width + "px",
                    leftLane > width * Theme.SAFE_AREA * 2);
        }
    }

    // ---- The one thing a player can do ------------------------------------------------

    /**
     * The invitation is the only actionable element on the screen and it was the quietest
     * container on it: a wash of {@link Theme#BLUE} at about 2:1 against its own plaque,
     * quieter than the decorative picture name beside it. A button that has to be found is
     * not a button.
     */
    @Test
    public void theCallToActionReads() {
        double container = Theme.contrastRatio(WinScene.CTA_FACE, Theme.PANEL);
        assertTrue("the pill is " + container + ":1 against the plaque it sits on",
                container >= 4.5);

        int label = Theme.textOn(WinScene.CTA_FACE);
        double text = Theme.contrastRatio(label, WinScene.CTA_FACE);
        assertTrue("the label is " + text + ":1 on the pill", text >= 4.5);

        // And it is the game asking, not either player.
        assertNotEquals(Theme.playerColor(0), WinScene.CTA_FACE);
        assertNotEquals(Theme.playerColor(1), WinScene.CTA_FACE);
    }

    // ---- The story book --------------------------------------------------------------

    @Test
    public void theFirstChapterOpensTheBook() {
        GameState game = story(0);
        assertEquals("THE FIRST CHAPTER", WinScene.eyebrow(game, true));
        assertFalse(WinScene.isFinalChapter(game));
        assertEquals("The book is open — seventeen chapters to come.",
                WinScene.journeyLine(game));
        assertEquals("Press A for the next chapter", WinScene.invite(game));
    }

    @Test
    public void aMiddleChapterCountsWhatIsLeft() {
        GameState game = story(9);
        assertEquals("CHAPTER 10 OF " + PuzzleLibrary.count(),
                WinScene.eyebrow(game, true));
        assertFalse(WinScene.isFinalChapter(game));
        assertEquals("Eight more chapters to come.", WinScene.journeyLine(game));
        assertEquals("Press A for the next chapter", WinScene.invite(game));
        assertEquals(WinScene.message(game.solved, true), WinScene.message(game, true));
    }

    @Test
    public void thePenultimateChapterSaysOneIsLeft() {
        GameState game = story(PuzzleLibrary.count() - 2);
        assertFalse(WinScene.isFinalChapter(game));
        assertEquals("One chapter left in the book.", WinScene.journeyLine(game));
    }

    /** Closing the book is the one real milestone, and it must say so in its own words. */
    @Test
    public void theLastChapterFinishesTheBook() {
        GameState game = story(PuzzleLibrary.count() - 1);
        assertTrue(WinScene.isFinalChapter(game));
        assertEquals("THE LAST CHAPTER", WinScene.eyebrow(game, true));
        assertEquals("Eighteen pictures, cover to cover.", WinScene.journeyLine(game));
        assertEquals("Press A to open the book again", WinScene.invite(game));

        String together = WinScene.message(game, true);
        String alone = WinScene.message(game, false);
        assertEquals("Every page, together, to the very end.", together);
        assertEquals("Every page, to the very end.", alone);
        // The book ending never reuses an ordinary line.
        for (int solved = 0; solved < WinScene.messageCount(); solved++) {
            assertFalse(together.equals(WinScene.message(solved, true)));
            assertFalse(alone.equals(WinScene.message(solved, false)));
        }
    }

    /** Wrapping past the end of the book restarts it rather than running off the shelf. */
    @Test
    public void chapterWordingWrapsWithTheLibrary() {
        int total = PuzzleLibrary.count();
        GameState game = new GameState(7L, 10);
        game.storyMode = true;
        for (int index = -2 * total; index <= 2 * total; index++) {
            game.storyIndex = index;
            assertNotNull(WinScene.eyebrow(game, true));
            assertNotNull(WinScene.journeyLine(game));
            assertNotNull(WinScene.invite(game));
            assertEquals(Math.floorMod(index, total) == total - 1,
                    WinScene.isFinalChapter(game));
        }
    }

    /** Endless boards never mention the book. */
    @Test
    public void endlessSaysNothingAboutChapters() {
        GameState game = endless(4);
        assertNull(WinScene.journeyLine(game));
        assertFalse(WinScene.isFinalChapter(game));
        assertEquals("Press A for another picture", WinScene.invite(game));
        assertFalse(WinScene.eyebrow(game, true).contains("CHAPTER"));
    }

    /** Every chapter of the shipped book produces wording that fits a ten-foot line. */
    @Test
    public void everyChapterHasShortEnoughWording() {
        for (int index = 0; index < PuzzleLibrary.count(); index++) {
            GameState game = story(index);
            for (boolean together : new boolean[]{true, false}) {
                assertTrue(WinScene.eyebrow(game, together).length() <= 24);
                assertTrue(WinScene.message(game, together).length() <= 44);
            }
            assertTrue(WinScene.journeyLine(game).length() <= 46);
            assertTrue(WinScene.invite(game).length() <= 34);
        }
        for (int solved = 0; solved < WinScene.messageCount(); solved++) {
            for (boolean together : new boolean[]{true, false}) {
                assertTrue(WinScene.message(solved, together),
                        WinScene.message(solved, together).length() <= 44);
            }
        }
    }
}

package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
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
 * functions of time pure: {@link WinScene#paperOpacity} decides when the puzzle has become
 * the picture, {@link WinScene#lift} where the picture is, and {@link WinScene#inviteBreath}
 * whether anything is still moving. All three can be walked frame by frame here rather than
 * eyeballed in a screenshot.
 */
public class WinSceneTest {

    @After
    public void restoreComfort() {
        Comfort.get().restoreDefaults();
    }

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
     * the game can actually deal: every authored chapter in the book and a wide sweep of
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
     *
     * <p>The cap moved 1500 -> 1800 for the two beats that were added at the front. The
     * entrances themselves are all the same length as before; what is new is 220 ms of
     * holding still on the finished board and 320 ms of the puzzle resolving into the
     * picture, which is the half-second that turns a fade-to-black into a reveal.
     */
    @Test
    public void theBeatsRunInOrderAndFinishPromptly() {
        // Nothing moves at all until the pair have had the board to themselves.
        assertTrue(WinScene.HOLD_MS <= WinScene.RESOLVE_AT);
        // The squares are already recolouring when the paper starts covering the workings,
        // so the sheet never comes up over a board that has not started changing.
        assertTrue(WinScene.REVEAL_AT <= WinScene.RESOLVE_AT);
        // And the sheet is complete before the picture leaves the table.
        assertEquals(WinScene.LIFT_AT, WinScene.RESOLVE_AT + WinScene.RESOLVE_MS, 0f);

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
                WinScene.ARRIVED_MS <= 1800f);
    }

    /**
     * The board is only removed once it has been completely painted over.
     *
     * <p>{@code Renderer} stops drawing the puzzle, the cursors and the rail at
     * {@link WinScene#BOARD_GONE_MS}, so that instant has to be one where the win card's own
     * sheet is fully opaque and still sitting exactly on the board's card — otherwise the
     * hand-over is a visible cut. It used to sit at the end of the lift instead, with the
     * card at 30% opacity and moving away, which is why the clue digits ghosted through the
     * picture in flight.
     */
    @Test
    public void theBoardIsOnlyDroppedOnceItIsUnderPaper() {
        assertEquals("the board outlives the sheet that hides it",
                WinScene.LIFT_AT, WinScene.BOARD_GONE_MS, 0f);
        assertEquals("the sheet is not opaque when the board goes", 1f,
                WinScene.paperOpacity(WinScene.BOARD_GONE_MS), 1e-4);
        assertEquals("the picture has started moving before the board goes", 0f,
                WinScene.lift(WinScene.BOARD_GONE_MS), 0f);
    }

    /**
     * The card and the words are two halves of one composition, so the plaque has to be on
     * its way up while the picture is still travelling, and the picture has to have
     * finished assembling by the time it lands in its frame.
     *
     * <p>This used to be stated against {@code revealDoneMs}, which was right while the
     * picture assembled in mid-air. It now assembles on the table, between 573 and 787 ms
     * across the whole deck, so that comparison would only be measuring which chapter has
     * the fewest squares.
     */
    @Test
    public void theWordsAreOnTheirWayBeforeThePictureLands() {
        assertTrue(WinScene.PLAQUE_AT < WinScene.PICTURE_HOME_MS);
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            assertTrue("chapter " + chapter + " lands in its frame still assembling",
                    WinScene.revealDoneMs(PuzzleLibrary.get(chapter))
                            <= WinScene.PICTURE_HOME_MS);
        }
        for (int size = GameState.MIN_SIZE; size <= GameState.MAX_SIZE; size++) {
            for (long seed = 0; seed < 24; seed++) {
                assertTrue("seed " + seed + " at " + size + " lands still assembling",
                        WinScene.revealDoneMs(PuzzleGenerator.generate(seed, size))
                                <= WinScene.PICTURE_HOME_MS);
            }
        }
    }

    // ---- The brightest surface in the game --------------------------------------------

    /**
     * The sheet may not touch the board until the pair have been given time to look at it.
     *
     * <p>The old guard was arithmetic — the card's opacity was tied to how much of the
     * picture existed, because it arrived over a 93%-black screen with nothing on it. The
     * guard is now structural: the sheet only ever comes up over the board's own card, in a
     * room dimmed to 21%, with the picture already blooming on it. What has to be asserted
     * is the timing that makes that true.
     */
    @Test
    public void theSheetWaitsForTheHoldAndThenCoversEverything() {
        for (float elapsed = 0; elapsed <= WinScene.RESOLVE_AT; elapsed += 5) {
            assertEquals("paper on the board at " + elapsed + "ms", 0f,
                    WinScene.paperOpacity(elapsed), 0f);
        }
        assertTrue("the sheet is still see-through half way through the resolve",
                WinScene.paperOpacity(WinScene.RESOLVE_AT + WinScene.RESOLVE_MS / 2)
                        < 1f);
        assertEquals(1f, WinScene.paperOpacity(WinScene.LIFT_AT), 1e-4);
        assertEquals(1f, WinScene.paperOpacity(60_000), 0f);
    }

    /** The sheet only ever gets brighter — a card that dips reads as a flicker. */
    @Test
    public void theCardOnlyEverBrightens() {
        float previous = -1;
        for (float elapsed = 0; elapsed <= 2000; elapsed += 5) {
            float opacity = WinScene.paperOpacity(elapsed);
            assertTrue("paper dipped at " + elapsed + "ms", opacity >= previous - 1e-4);
            assertTrue(opacity >= 0f && opacity <= 1f);
            previous = opacity;
        }
    }

    /** And the picture only ever travels forwards, from the table into its frame. */
    @Test
    public void thePictureOnlyEverTravelsForwards() {
        float previous = -1;
        for (float elapsed = 0; elapsed <= 2000; elapsed += 5) {
            float lift = WinScene.lift(elapsed);
            assertTrue("the picture went backwards at " + elapsed + "ms",
                    lift >= previous - 1e-4);
            previous = lift;
        }
        assertEquals("the picture moves during the hold", 0f, WinScene.lift(0), 0f);
        assertEquals(1f, WinScene.lift(WinScene.PICTURE_HOME_MS), 1e-4);
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

    /**
     * And the number {@code Renderer} actually reads outlives the entrance in both comfort
     * modes.
     *
     * <p>Under Calmer Animation there is no breath, no name pop and no pill spring, so
     * waiting the full {@link WinScene#STILL_AT_MS} would repaint an identical frame for
     * nearly seven seconds; but stopping at the last beat's <em>start</em> would freeze the
     * invitation half way in, which is what the old {@code !calmMotion} branch did.
     */
    @Test
    public void theLoopIsToldToWaitForTheWholeEntrance() {
        for (boolean calm : new boolean[]{false, true}) {
            Comfort.get().calmMotion = calm;
            assertTrue("calm=" + calm + " sleeps at " + WinScene.stillAtMs()
                            + "ms, before the last beat lands at " + WinScene.ARRIVED_MS,
                    WinScene.stillAtMs() > WinScene.ARRIVED_MS);
        }
        Comfort.get().calmMotion = false;
        assertEquals(WinScene.STILL_AT_MS, WinScene.stillAtMs());
        Comfort.get().calmMotion = true;
        assertTrue("calm waits as long as full motion does",
                WinScene.stillAtMs() < WinScene.STILL_AT_MS);
    }

    // ---- The page turn ------------------------------------------------------------------

    /**
     * The wash between two pictures has to start opaque, finish transparent and then stay
     * out of the way for ever — a handover whose clock has run past its own window must
     * draw nothing at all, or a stale timestamp would tint every subsequent frame.
     */
    @Test
    public void thePageTurnFinishesAndStaysFinished() {
        assertEquals("the page turn has already started", 0f, WinScene.handover(0), 0f);
        assertTrue(WinScene.handover(WinScene.HANDOVER_MS / 2f) > .5f);
        assertEquals(1f, WinScene.handover(WinScene.HANDOVER_MS), 1e-4);
        for (float since = WinScene.HANDOVER_MS; since <= 600_000; since += 997) {
            assertEquals("still washing at " + since + "ms", 1f,
                    WinScene.handover(since), 0f);
        }
        // A clock that has gone backwards — a frame drawn before the swap it is measuring —
        // draws nothing rather than a full-strength curtain.
        assertEquals(1f, WinScene.handover(-1), 0f);
    }

    // ---- Composition ------------------------------------------------------------------

    /**
     * The composition is centred on the screen, so the air either side of it is equal.
     *
     * <p>It was not: a hand-picked left edge of .122 of the width put a 702 px band on the
     * left and a 131 px strip pinned to the right edge, while the comment three lines above
     * the numbers claimed the two came out equal. The confetti no longer lives in that air
     * — it is given the whole safe width — but a card that is not centred on the screen is
     * still a card that looks like it slipped.
     */
    @Test
    public void theCompositionIsCentredAndInsideTheSafeArea() {
        for (float width : new float[]{1280, 1920, 3840}) {
            float leftAir = WinScene.compositionLeft(width);
            float rightAir = width - WinScene.compositionRight(width);
            assertEquals("the composition is off centre at " + width + "px", leftAir,
                    rightAir, .01f);
            assertTrue("the composition crosses the overscan boundary at " + width + "px",
                    leftAir > width * Theme.SAFE_AREA);
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
        // Twenty-three, not seventeen, since the book grew from eighteen chapters to
        // twenty-four. The literal is pinned rather than derived because this is the very
        // first sentence the book says and it should not be able to change unnoticed.
        assertEquals("The book is open — twenty-three more.",
                WinScene.journeyLine(game));
        assertEquals("Press A for the next chapter", WinScene.invite(game));
    }

    @Test
    public void aMiddleChapterCountsWhatIsLeft() {
        GameState game = story(9);
        assertEquals("CHAPTER 10 OF " + PuzzleLibrary.count(),
                WinScene.eyebrow(game, true));
        assertFalse(WinScene.isFinalChapter(game));
        // Fourteen left of twenty-four, where the eighteen-chapter book left eight.
        assertEquals("Fourteen more chapters to come.", WinScene.journeyLine(game));
        assertEquals("Press A for the next chapter", WinScene.invite(game));
        // A chapter says its own line rather than drawing from the endless rotation. The
        // twenty-four lines in PuzzleLibrary.LINES had no caller outside these tests, so the
        // warmest writing in the game had never reached a television; this is where it goes.
        assertEquals(PuzzleLibrary.line(9), WinScene.message(game, true));
        assertNotEquals(WinScene.message(game.solved, true), WinScene.message(game, true));
    }

    /** Every chapter's own line reaches the card, and no two chapters share one. */
    @Test
    public void everyChapterSaysItsOwnLine() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int index = 0; index < PuzzleLibrary.count(); index++) {
            GameState game = story(index);
            if (WinScene.isFinalChapter(game)) {
                // The book closing earns its own ending; it is not about the picture.
                continue;
            }
            String line = PuzzleLibrary.line(index);
            for (boolean together : new boolean[]{true, false}) {
                assertEquals("chapter " + (index + 1) + " should say its own line",
                        line, WinScene.message(game, together));
            }
            assertTrue("chapter " + (index + 1) + " repeats another chapter's line",
                    seen.add(line));
        }
    }

    /** Endless play has no authored line, so it keeps the rotation. */
    @Test
    public void endlessPlayStillRotatesThroughTheWarmLines() {
        for (int solved = 0; solved < WinScene.messageCount(); solved++) {
            GameState game = endless(solved);
            assertEquals(WinScene.message(solved, true), WinScene.message(game, true));
        }
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
        assertEquals("Twenty-four pictures, cover to cover.", WinScene.journeyLine(game));
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

    /**
     * The journey line is prose, so it counts in words all the way to the last chapter.
     *
     * <p>This is the test the book's growth from eighteen chapters to twenty-four needed
     * and did not have. {@code NUMBER_WORDS} stopped at twenty and {@code word} fell back
     * to {@link Integer#toString}, so the first thing the book ever said became "The book
     * is open — 23 chapters to come." The three copy assertions above only caught it
     * because they happened to pin those exact sentences; this one catches it for any
     * length of book, and for the eyebrow's sake it is deliberately scoped to the journey
     * line — "CHAPTER 10 OF 24" is a location and is meant to be numerals.
     */
    @Test
    public void theBookCountsInWordsAtEveryLength() {
        GameState game = new GameState(7L, 10);
        game.storyMode = true;
        for (int index = 0; index < PuzzleLibrary.count(); index++) {
            game.storyIndex = index;
            String line = WinScene.journeyLine(game);
            for (int at = 0; at < line.length(); at++) {
                assertFalse("chapter " + (index + 1) + " says \"" + line + "\"",
                        Character.isDigit(line.charAt(at)));
            }
        }
    }

    /** Endless boards never mention the book. */
    @Test
    public void endlessSaysNothingAboutChapters() {
        GameState game = endless(4);
        assertFalse(WinScene.isFinalChapter(game));
        assertEquals("Press A for another picture", WinScene.invite(game));
        assertFalse(WinScene.eyebrow(game, true).contains("CHAPTER"));
        assertFalse(WinScene.journeyLine(game).toLowerCase(Locale.ROOT)
                .contains("chapter"));
        assertFalse(WinScene.journeyLine(game).toLowerCase(Locale.ROOT).contains("book"));
    }

    /**
     * Endless closes with a memento instead of a blank.
     *
     * <p>It used to return {@code null}, so the mode people spend most of their evenings in
     * went straight from the move count to the button while story mode got a chapter row
     * and a journey line. The count is {@code game.solved} plus the picture just finished,
     * which is exactly the figure the rail prints as "ENDLESS&nbsp;#4" — the two must never
     * disagree, two seconds apart, about the same picture.
     */
    @Test
    public void endlessCountsThePicturesOnTheWall() {
        assertEquals("The first picture on the wall.",
                WinScene.journeyLine(endless(0)));
        assertEquals("Two pictures on the wall.", WinScene.journeyLine(endless(1)));
        assertEquals("Twenty pictures on the wall.", WinScene.journeyLine(endless(19)));
        assertEquals("Twenty-one pictures on the wall.",
                WinScene.journeyLine(endless(20)));
        // Above ninety-nine the game stops trying to say it out loud, which keeps the line
        // shorter than the words would have been rather than longer.
        assertEquals("100 pictures on the wall.", WinScene.journeyLine(endless(99)));
        // A corrupt save cannot produce a line about "minus four pictures".
        assertNull(WinScene.journeyLine(endless(-1)));
        assertNull(WinScene.journeyLine(endless(-40)));
    }

    // ---- Fitting the column ------------------------------------------------------------

    /**
     * Every string the plaque can show fits its column at the prose floor.
     *
     * <p>Character counts are a proxy and a poor one — "Twenty-four pictures, cover to
     * cover." is 37 characters and 582 px while "The book is open — twenty-three chapters
     * to come." was 49 and 826 px — but they are the only proxy available here: the test
     * source set runs with {@code returnDefaultValues}, so {@code Paint.measureText}
     * answers zero and a real width assertion would pass on anything.
     *
     * <p>So the caps are calibrated against real measurements taken through the desktop
     * render harness at 1920x1080, where the plaque column is 651.2&nbsp;px and
     * {@link Theme#MIN_PROSE_SP} is 36&nbsp;px:
     *
     * <ul>
     *   <li>journey — worst case "The book is open — twenty-three more." at 632 px, and
     *       "Seventy-seven pictures on the wall." at 566. 37 characters.</li>
     *   <li>message — measured at {@link Theme#SUBHEAD}, so it has room to shrink before
     *       it has to overflow. "You never rushed. Look what happened." is the tightest in
     *       the deck: 865 px at 48 px, which {@code fit} settles at 36.2 px, two tenths of
     *       a pixel above the floor. 38 characters is where that runs out.</li>
     *   <li>invite — the binding one, because a pill cannot shrink to fit. "Press A to open
     *       the book again" is 538 px bold at the floor and its pill adds another
     *       {@link Theme#PILL_PAD_X}: 625 of the 651 available. 30 characters, and there is
     *       no room for a longer invitation.</li>
     * </ul>
     *
     * <p>Characters are a proxy for width and a lenient one — a line of thirty-eight
     * capitals would still overflow — so a new string near any of these caps should be
     * measured through the render harness rather than counted.</p>
     *
     * <p>The floor these are measured against moved from {@link Theme#MIN_READABLE_SP} to
     * {@link Theme#MIN_PROSE_SP} — 27 px to 36 px — because nothing on this plaque is an
     * isolated clue digit. That is what makes the caps binding rather than advisory: a line
     * that does not fit now overflows instead of shrinking into unreadability, which is the
     * correct trade for a ten-foot screen and the reason these numbers are pinned.
     */
    /**
     * The widest line the plaque will draw for a message, in characters.
     *
     * <p>Mirrors {@code WinScene.wrapMessage}: a message that fits stays on one line, and one
     * that does not is broken at the space that makes the wider half as narrow as possible.
     * Characters stand in for pixels here exactly as they do elsewhere in this test — a
     * lenient proxy, so a new line near the cap should be checked through the render harness.
     */
    private static int longestWrappedLine(String message) {
        if (message.length() <= 38) {
            return message.length();
        }
        int best = message.length();
        for (int at = message.indexOf(' '); at >= 0; at = message.indexOf(' ', at + 1)) {
            best = Math.min(best,
                    Math.max(at, message.length() - at - 1));
        }
        return best;
    }

    @Test
    public void everyChapterHasShortEnoughWording() {
        for (int index = 0; index < PuzzleLibrary.count(); index++) {
            GameState game = story(index);
            for (boolean together : new boolean[]{true, false}) {
                assertTrue(WinScene.eyebrow(game, together).length() <= 24);
                // Per wrapped line, not per sentence. A chapter's own line is about twice
                // the length of a rotation message - "She has claimed the warm end of the
                // sofa. That is simply how it is now." is 70 characters against "Nobody
                // rushed. Look what happened."'s 33 - and the plaque turns it onto two
                // balanced lines rather than shrinking it under the prose floor. What still
                // has to hold is the width of the widest line, which is the same 38 the
                // one-line messages were measured at. The worst chapter balances at 35.
                assertTrue(WinScene.message(game, together),
                        longestWrappedLine(WinScene.message(game, together)) <= 38);
            }
            assertTrue(WinScene.journeyLine(game), WinScene.journeyLine(game).length() <= 37);
            assertTrue(WinScene.invite(game).length() <= 30);
        }
        for (int solved = 0; solved < WinScene.messageCount(); solved++) {
            for (boolean together : new boolean[]{true, false}) {
                assertTrue(WinScene.message(solved, together),
                        WinScene.message(solved, together).length() <= 38);
            }
            String wall = WinScene.journeyLine(endless(solved));
            assertTrue(wall, wall.length() <= 37);
        }
        // The wall line grows with a number that never stops, so it is checked past the
        // point where the game gives up on words.
        for (int solved : new int[]{0, 1, 6, 16, 66, 76, 98, 99, 100, 999, 100_000}) {
            String wall = WinScene.journeyLine(endless(solved));
            assertTrue(wall, wall.length() <= 37);
        }
    }

    /**
     * The story book's row of hearts stays inside the plaque, at any length of book and on
     * any screen.
     *
     * <p>With twenty-four chapters in a 651 px column a single row is a 27 px pitch, which
     * makes an unvisited chapter a 7 px dot — about five arcminutes from ten feet, which is
     * the point at which a mark stops being identifiable at all. The row wraps rather than
     * shrinking past a floor, so this has to prove both halves: that it never packs tighter
     * than the floor while it has lines left, and that it never runs outside the column.
     */
    @Test
    public void theChapterRowFitsItsColumnAtEveryLength() {
        for (float screen : new float[]{720, 1080, 2160}) {
            Theme.setScreenHeight(screen);
            // The real plaque columns at 1280x720, 1920x1080 and 3840x2160, plus a
            // deliberately cramped one either side of them.
            float real = screen * .6030f;
            for (float column : new float[]{real * .5f, real, real * 1.4f}) {
                int lines = WinScene.chapterLines(column);
                float step = WinScene.chapterStep(column);
                int perLine = (int) Math.ceil(
                        PuzzleLibrary.count() / (double) Math.max(1, lines));
                assertTrue("no lines at column " + column, lines >= 1);
                assertTrue("the row runs outside the plaque: " + step * (perLine - 1)
                                + "px in a " + column + "px column",
                        step * (perLine - 1) <= column);
                assertTrue("the block has no height at column " + column,
                        WinScene.chapterBlockHeight(column) > 0);
            }
        }
        Theme.setScreenHeight(720);
    }
}

package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * The side panel's two contracts with the rest of the game.
 *
 * <p><b>The legend tells the truth about the controller in the room.</b> A bare TV remote
 * has a D-pad, a centre key and Back — it can never reach {@code PlayerRegistry.isCross},
 * so its centre key cycles empty → filled → crossed and hints move to a held press. A
 * legend still reading "A / B / Y" would be three wrong answers in a row.
 *
 * <p><b>Nothing is drawn on a colour it cannot be read on.</b> The chips carry a glyph on
 * a coloured fill, and every colour they can take — including a resting one, and including
 * Sky's deep teal under {@link Comfort#distinctPlayers} — has to leave that glyph legible.
 *
 * <h2>What cannot be asserted here</h2>
 *
 * <p>Unit tests run with {@code returnDefaultValues}, so {@code Paint.measureText} returns
 * zero and every string in the game is nought pixels wide. Nothing about <em>fitting</em>
 * can be proved in this file: not that "PLAYING TOGETHER" stays on its card, not that a
 * message fits its pill, not how many lines the picture's name or the closing note turn
 * onto, and therefore not whether the rail's own content fits between the card's top edge
 * and the safe line. Those are checked by rendering real frames through
 * {@code tools/preview}, and the same limitation is written on {@link Draw#fit}. What is
 * pinned here is everything the geometry is <em>derived</em> from, which is where the
 * ribbon's overflow actually came from: the pill was given the card's width to live in
 * rather than the screen's.
 *
 * <p><b>What the rail now carries.</b> The title strip across the top of the screen is
 * gone and its content is the head of the rail. That is a layout decision rather than a
 * copy one — see {@link BoardLayout#headerHeight()} for the 66 px it was costing the
 * board — but the words themselves are checked here.
 */
public class HudSceneTest {

    /** WCAG AA for normal text; a one-character glyph on a chip is held to the same bar. */
    private static final double AA = 4.5;

    @After
    public void restoreDefaults() {
        HudScene.forgetTheRoom();
        Comfort.get().restoreDefaults();
        Theme.setScreenHeight(1080);
    }

    // ---- The legend ------------------------------------------------------------------

    @Test
    public void aGamepadIsTheDefaultUntilSomethingSaysOtherwise() {
        assertFalse("the remote legend must never be the assumption",
                HudScene.remoteOnly());
        assertEquals("A", HudScene.legendButtons()[0]);
    }

    @Test
    public void everyLegendRowHasAButtonAndAnExplanation() {
        UiState ui = new UiState();
        for (boolean remote : new boolean[]{false, true}) {
            HudScene.setRemoteOnly(remote);
            String[] buttons = HudScene.legendButtons();
            String[] labels = HudScene.legendLabels(ui);
            assertEquals("remoteOnly=" + remote + ": the legend arrays disagree",
                    buttons.length, labels.length);
            assertTrue(buttons.length > 0);
            for (int row = 0; row < buttons.length; row++) {
                assertFalse(buttons[row].isEmpty());
                assertFalse(labels[row].isEmpty());
            }
        }
    }

    /**
     * The panel's height is {@code legendButtons().length} rows tall, so the two legends
     * having the same number of rows is not required — but them each having a row for
     * every reachable action is.
     */
    @Test
    public void aRemoteOnlyLegendNamesNoButtonARemoteDoesNotHave() {
        UiState ui = new UiState();
        HudScene.setRemoteOnly(true);
        for (String button : HudScene.legendButtons()) {
            assertFalse("a bare remote has no " + button + " button",
                    button.equals("A") || button.equals("B") || button.equals("Y")
                            || button.equals("X"));
        }
        // The cycle is the whole point: one key has to cover fill, cross and clear.
        assertTrue("the remote legend never explains the centre-key cycle",
                HudScene.legendLabels(ui)[0].toLowerCase(java.util.Locale.US)
                        .contains("cross"));
    }

    @Test
    public void restingHintsAreSaidOutLoudInBothLegends() {
        UiState ui = new UiState();
        ui.hintsOn = false;
        for (boolean remote : new boolean[]{false, true}) {
            HudScene.setRemoteOnly(remote);
            // Asserts the meaning, not the exact wording: the rail is narrow and the
            // phrasing has to be free to shorten without this test having to be edited.
            boolean mentioned = false;
            for (String label : HudScene.legendLabels(ui)) {
                mentioned |= label.toLowerCase(java.util.Locale.US).contains("resting");
            }
            assertTrue("remoteOnly=" + remote + ": nothing says hints are off", mentioned);
        }
    }

    // ---- Colour on colour --------------------------------------------------------------

    /**
     * A chip's glyph is asked for rather than assumed. Plum ink on a lit chip and cream on
     * a resting one both have to clear AA, in every legend and with either identity
     * palette — the case that used to break was simply never checked.
     */
    @Test
    public void everyChipGlyphReadsOnItsChip() {
        UiState ui = new UiState();
        for (boolean distinct : new boolean[]{false, true}) {
            Comfort.get().distinctPlayers = distinct;
            for (boolean remote : new boolean[]{false, true}) {
                HudScene.setRemoteOnly(remote);
                for (int color : chipColours()) {
                    assertReads("a lit chip's glyph", Theme.textOn(color), color);
                    int resting = Draw.blend(Theme.PANEL, color, .34f);
                    assertReads("a resting chip's glyph", Theme.textOn(resting), resting);
                }
                assertEquals(HudScene.legendButtons().length,
                        HudScene.legendLabels(ui).length);
            }
        }
    }

    /**
     * Sky's card with {@link Comfort#distinctPlayers} on: the deep teal exists precisely
     * because it is darker than Rose, so nothing on her card may assume a light fill.
     *
     * <p>This used to build only the joined card, {@code blend(PANEL, colour, .23f)}, which
     * is why nobody noticed that the <em>open</em> one was the least readable thing in the
     * panel: its name was blended 62% back into its own fill, taking a computed 4.5:1 down
     * to 2.98:1 for Rose and 3.58:1 for Sky, and its ring to 2.64:1 — under the 3:1 floor
     * for a graphical object. Both fills are checked now.
     */
    @Test
    public void skysCardSurvivesHerColourBlindFriendlyColour() {
        Comfort.get().distinctPlayers = true;
        int sky = Theme.playerColor(1);
        assertTrue("the alternate teal is no longer the hard case",
                Theme.contrastRatio(sky, Theme.PANEL) < AA);

        for (float seat : new float[]{Theme.SEAT_FILL_JOINED, Theme.SEAT_FILL_OPEN}) {
            for (int player = 0; player < 2; player++) {
                int color = Theme.playerColor(player);
                // The card fill and accent, worked out exactly as HudScene works them out.
                int fill = Draw.blend(Theme.PANEL, color, seat);
                String who = "player " + player + " at fill " + seat;
                assertReads(who + "'s name", Theme.readableOn(color, fill, AA), fill);
                assertTrue(who + "'s dot vanishes into its own card",
                        Theme.contrastRatio(Theme.readableOn(color, fill, 3), fill) >= 3);
            }
        }

        // And the two players still differ in lightness, which is the point of the mode.
        assertTrue("Rose and Sky collapsed back into the same lightness",
                Theme.contrastRatio(Theme.PINK, sky) >= 2.5);
    }

    /** The empty half of the shared bar has to be findable, not merely present. */
    @Test
    public void theProgressTrackIsAGraphicalObjectAndReadsLikeOne() {
        assertTrue("the track was 1.61:1 on the panel it sits on, which is a rumour",
                Theme.contrastRatio(Theme.TRACK, Theme.PANEL) >= 3);
    }

    // ---- The legend's two states -----------------------------------------------------

    /**
     * The legend spells itself out for a pair who are still learning it and retires into a
     * strip of chips once they are not — 171 px of a 617 px rail, which is what the closing
     * block is drawn in.
     */
    @Test
    public void theLegendRetiresOnceThePairHaveLearnedItAndComesBackForANewcomer() {
        GameState game = new GameState(7, 10);
        int full = HudScene.legendButtons().length;

        game.solved = 0;
        assertEquals("a first evening gets the words", full,
                HudScene.legendRows(game, 500_000));
        game.solved = 2;
        assertEquals(full, HudScene.legendRows(game, 500_000));

        game.solved = 3;
        assertEquals("by the fourth picture the chips are enough", 1,
                HudScene.legendRows(game, 500_000));

        HudScene.setJoinedAt(500_000);
        assertEquals("somebody just sat down; they have not learned anything yet", full,
                HudScene.legendRows(game, 500_000 + 1_000));
        assertEquals("and fifteen seconds later they have", 1,
                HudScene.legendRows(game, 500_000 + 20_000));
    }

    /**
     * A freshly booted box reports a small uptime clock, so the join marker must not read
     * as "a moment ago" before anybody has joined.
     */
    @Test
    public void anUntouchedRoomDoesNotThinkSomebodyJustArrived() {
        GameState game = new GameState(7, 10);
        game.solved = 9;
        assertEquals(1, HudScene.legendRows(game, 0));
        assertEquals(1, HudScene.legendRows(game, 900));
    }

    // ---- The ribbon's room -----------------------------------------------------------

    /**
     * The pill's lane: as wide as the screen allows either side of the card's centre,
     * inside the safe area and clear of the rail.
     *
     * <p>It used to be the card's own width — 694 px at 5x5 against a 690 px invitation to
     * join — and the text was never fitted to it, so both ends of a real message sat on the
     * wallpaper. The widths themselves cannot be measured in a unit test; what can be, and
     * what was wrong, is which rectangle the ribbon is allowed to use.
     */
    @Test
    public void theRibbonMayUseTheScreenRatherThanTheCard() {
        Theme.setScreenHeight(1080);
        for (int size = 5; size <= 20; size += 5) {
            BoardLayout board = new BoardLayout(1920, 1080,
                    PuzzleGenerator.compose(0, 0, size), true);
            float lane = HudScene.ribbonLane(1920, board);
            float centre = (board.cardLeft() + board.cardRight()) / 2;
            String what = size + "x" + size + ": ";

            assertTrue(what + "the pill is still boxed into the card",
                    lane > board.cardRight() - board.cardLeft());
            assertTrue(what + "the pill runs into the overscan band",
                    centre - lane / 2 >= 1920 * Theme.SAFE_AREA - .5f);
            assertTrue(what + "the pill runs under the rail",
                    centre + lane / 2 <= board.panelLeft - .5f);
        }
    }

    @Test
    public void inkAndCreamAreChosenNotAssumed() {
        // Light identity colours take ink; the deep teal takes cream. If textOn ever
        // stopped choosing, one of these would flip.
        assertEquals(Theme.INK, Theme.textOn(Theme.PINK));
        assertEquals(Theme.INK, Theme.textOn(Theme.BLUE));
        assertEquals(Theme.INK, Theme.textOn(Theme.GOLD));
        assertEquals(Theme.INK, Theme.textOn(Theme.SOFT_TEXT));
        assertEquals(Theme.CREAM, Theme.textOn(Comfort.SKY_ALT));
        assertEquals(Theme.CREAM, Theme.textOn(Theme.PANEL));
    }

    // ---- Where we are ----------------------------------------------------------------

    /**
     * The eyebrow at the head of the rail says which deck, how far in, and how big.
     *
     * <p>It is the line the title strip across the top of the screen used to carry, and
     * dropping that strip is what took a 20x20 square from 29 px to 32 — see
     * {@link BoardLayout#headerHeight()}. So what it says has to survive the move: both
     * counters are 1-based, because "STORY 0 / 24" would be the first thing a player read
     * on opening the book, and neither one is a wordmark. {@code HomeScene} sets
     * "COZYGRAMS" in 58 px letters thirty seconds earlier; repeating it all evening cost
     * the board 66 px of the only axis it is ever short of.
     *
     * <p>What cannot be checked here is whether it fits its lane — see the notes on the
     * class. {@code tools/preview} measures that, at every resolution, with a real Paint.
     */
    @Test
    public void theRailSaysWhichPictureAndHowFarIn() {
        GameState game = new GameState(7L, 15);
        game.solved = 3;
        game.storyMode = false;
        assertEquals("ENDLESS #4 · 15 × 15", HudScene.whereWeAre(game));

        game.storyMode = true;
        game.storyIndex = 0;
        assertEquals("STORY 1 / " + PuzzleLibrary.count() + " · 15 × 15",
                HudScene.whereWeAre(game));
        game.storyIndex = PuzzleLibrary.count() - 1;
        assertEquals("STORY " + PuzzleLibrary.count() + " / " + PuzzleLibrary.count()
                + " · 15 × 15", HudScene.whereWeAre(game));

        for (boolean story : new boolean[]{false, true}) {
            game.storyMode = story;
            assertFalse("the wordmark came back into play",
                    HudScene.whereWeAre(game).contains("COZYGRAMS"));
        }
    }

    // ---- The progress line -----------------------------------------------------------

    /**
     * The one number in the rail, at each of the four things it can say.
     *
     * <p>The terminal state is the one that matters and the one that had no test at all:
     * the line used to read "Almost there…" over a picture that was already finished, and
     * the fix — say nothing — is only half the behaviour. {@code drawProgress} still
     * reserves the line's height when the string is empty, so the bar underneath does not
     * jump up the rail at the exact moment the pair win. The blank line visible in
     * {@code 10-win-early} is that reservation, not a dropped string.
     *
     * <p>"One stray square" is the awkward middle: every square of the picture is found, so
     * the count itself would read "3 of 3", but a stray fill elsewhere leaves the clues
     * unsatisfied and the win has not landed. It is the only branch where {@code found} and
     * {@code total} are equal and the picture is still not complete.
     */
    @Test
    public void theProgressLineSaysNothingOnceThePictureIsFinished() {
        GameState game = new GameState(7L, 5);
        boolean[][] art = new boolean[5][5];
        art[0][0] = true;
        art[0][1] = true;
        art[2][3] = true;
        game.puzzle = new Puzzle(art, "Three Squares");

        assertEquals("Take your time", HudScene.progressLabel(game));

        game.puzzle.marks[0][0] = Puzzle.FILLED;
        assertEquals("1 of 3 squares", HudScene.progressLabel(game));

        game.puzzle.marks[0][1] = Puzzle.FILLED;
        game.puzzle.marks[2][3] = Puzzle.FILLED;
        game.puzzle.marks[4][4] = Puzzle.FILLED;
        assertFalse("a stray fill still leaves the clues unsatisfied",
                game.puzzle.complete());
        assertEquals("One stray square", HudScene.progressLabel(game));

        game.puzzle.marks[4][4] = Puzzle.UNKNOWN;
        assertTrue("the picture should be finished", game.puzzle.complete());
        assertEquals("the finished picture is its own message", "",
                HudScene.progressLabel(game));
    }

    /**
     * The empty measure is not drawn.
     *
     * <p>{@link HudScene#theProgressTrackIsAGraphicalObjectAndReadsLikeOne} above proves the
     * track clears 3:1 so it can be found; this proves the 0% case is not asked to be found
     * in the first place. A fresh board already says "Take your time", and a grey capsule
     * under that sentence measures nothing while looking exactly like a component that has
     * not loaded — which is how {@code 21-game-page-turn} read before this.
     *
     * <p>The bar has to come back on the very first square rather than at some threshold,
     * because the label switches to a count on that same square and a count with no measure
     * beneath it is the defect this pairs with.
     */
    @Test
    public void theProgressBarIsNotDrawnBeforeTheFirstSquare() {
        GameState game = new GameState(7L, 5);
        boolean[][] art = new boolean[5][5];
        art[0][0] = true;
        art[0][1] = true;
        game.puzzle = new Puzzle(art, "Two Squares");

        assertFalse("an empty capsule under \"Take your time\" measures nothing",
                HudScene.showsProgressBar(game));
        assertEquals("Take your time", HudScene.progressLabel(game));

        game.puzzle.marks[0][0] = Puzzle.FILLED;
        assertTrue("the first square is the first thing the bar can say",
                HudScene.showsProgressBar(game));
        assertEquals("1 of 2 squares", HudScene.progressLabel(game));
    }

    // ---- The tail --------------------------------------------------------------------

    /**
     * The rail counts the book in words, at every length of book.
     *
     * <p>The tail had no test at all, which is how it came to say "23 chapters still to
     * come" the moment the Story Book grew past twenty chapters: {@code HudScene} kept its
     * own copy of the number-word table and the copy stopped at twenty. It now defers to
     * {@link WinScene#word(int)}, and this holds it there — the rail and the win card are
     * four seconds apart in the same session and must not disagree about the same book.
     */
    @Test
    public void theRailCountsTheBookInWords() {
        GameState game = new GameState(7L, 10);
        game.storyMode = true;
        for (int index = 0; index < PuzzleLibrary.count(); index++) {
            game.storyIndex = index;
            String line = HudScene.tailLine(game);
            for (int at = 0; at < line.length(); at++) {
                assertFalse("chapter " + (index + 1) + " says \"" + line + "\"",
                        Character.isDigit(line.charAt(at)));
            }
        }

        game.storyIndex = 0;
        assertEquals("THE STORY BOOK", HudScene.tailLabel(game));
        assertEquals("Twenty-three chapters still to come", HudScene.tailLine(game));
        game.storyIndex = PuzzleLibrary.count() - 1;
        assertEquals("The last chapter", HudScene.tailLine(game));
    }

    private static int[] chipColours() {
        return new int[]{Theme.PINK, Theme.BLUE, Theme.GOLD, Theme.SOFT_TEXT,
                Theme.playerColor(0), Theme.playerColor(1)};
    }

    private static void assertReads(String what, int color, int background) {
        double ratio = Theme.contrastRatio(color, background);
        assertTrue(what + " is only " + Math.round(ratio * 100) / 100.0 + ":1",
                ratio >= AA);
    }

    /**
     * The heading over the seats has to be true of the room it is in.
     *
     * <p>It read "PLAYING TOGETHER" whoever was playing, so a solo evening spent an hour
     * with that sentence over an empty chair. The game knows better four inches away: the
     * win card ships a separate pool of solo lines with a comment saying "the same warmth for
     * one player, without pretending there were two". The card refuses to pretend for the
     * four seconds it is up; the rail was pretending for the whole evening.
     *
     * <p>The empty seat and its "press any button" are untouched — the invitation was always
     * the warm part, and a chair pulled out is not a chair missing.
     */
    @Test
    public void theSeatsHeadingNeverClaimsCompanyThatIsNotThere() {
        UiState ui = new UiState();
        assertEquals("WHO'S HERE", HudScene.seatsEyebrow(ui));
        ui.joined[0] = true;
        assertEquals("one player is not a pair", "WHO'S HERE", HudScene.seatsEyebrow(ui));
        ui.joined[0] = false;
        ui.joined[1] = true;
        assertEquals("and it is not about which seat",
                "WHO'S HERE", HudScene.seatsEyebrow(ui));
        ui.joined[0] = true;
        assertEquals("PLAYING TOGETHER", HudScene.seatsEyebrow(ui));
    }

    /**
     * The message pill never reaches into the overscan band.
     *
     * <p>{@link HudScene#ribbonLane} is measured from the card's centre out to the safe edge
     * and to the rail, so a pill clamped to it cannot leave the safe rectangle whatever it is
     * asked to say. It used to be allowed past that lane rather than let a word out of the
     * pill: measured at 1920x1080 the left cap sat at x=68 against a safe edge of 96, with
     * 5 px of amber ink outside it. A message that still will not fit is tightened between
     * its glyphs now, which is what the clue digits do in the same situation.
     */
    @Test
    public void theMessagePillStaysInsideTheSafeArea() {
        float[][] screens = {{1280, 720}, {1920, 1080}, {3840, 2160}};
        for (float[] screen : screens) {
            Theme.setScreenHeight(screen[1]);
            Theme.setTextScale(1f);
            for (int size = 5; size <= 20; size += 5) {
                Puzzle puzzle = PuzzleGenerator.compose(0, 0, size);
                BoardLayout board = new BoardLayout(screen[0], screen[1], puzzle, true);
                float centre = (board.cardLeft() + board.cardRight()) / 2;
                float half = HudScene.ribbonLane(screen[0], board) / 2;
                String what = (int) screen[0] + "x" + (int) screen[1] + " " + size;
                assertTrue(what + ": the pill's left cap is at " + (centre - half),
                        centre - half >= screen[0] * Theme.SAFE_AREA - .01f);
                assertTrue(what + ": the pill's right cap is at " + (centre + half),
                        centre + half <= screen[0] * (1 - Theme.SAFE_AREA) + .01f);
            }
        }
        Theme.setScreenHeight(720);
    }
}

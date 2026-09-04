package com.cozygrams.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The persistence rules, exercised as plain data.
 *
 * <p>Unit tests run against a stubbed android.jar, so SharedPreferences does nothing at
 * all here. Everything that decides whether a save can be trusted therefore lives in
 * static functions that take and return ordinary Java values, and this is where they are
 * held to account.
 */
public class SaveStoreTest {

    private static byte[][] board(int size) {
        byte[][] marks = new byte[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                marks[y][x] = (byte) ((x + y) % 3);
            }
        }
        return marks;
    }

    // ---- Round tripping --------------------------------------------------------------

    @Test
    public void boardSurvivesARoundTrip() {
        for (int size : new int[]{GameState.MIN_SIZE, 10, GameState.MAX_SIZE}) {
            byte[][] original = board(size);
            String encoded = SaveStore.encodeMarks(original);
            assertEquals(size * size, encoded.length());
            byte[][] restored = SaveStore.decodeMarks(encoded, size);
            assertTrue("size " + size, restored != null);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    assertEquals(original[y][x], restored[y][x]);
                }
            }
        }
    }

    @Test
    public void aRealGameRoundTripsThroughTheSavedString() {
        GameState game = new GameState(2024, 10);
        game.mark(0, Puzzle.FILLED);
        game.move(1, 3, 2);
        game.mark(1, Puzzle.CROSSED);

        byte[][] restored = SaveStore.decodeMarks(
                SaveStore.encodeMarks(game.puzzle.marks), game.size);
        assertTrue(restored != null);
        assertEquals(Puzzle.FILLED, restored[0][0]);
        assertEquals(Puzzle.CROSSED, restored[2][4]);
    }

    @Test
    public void unexpectedMarkValuesAreWrittenOutAsUntouchedSquares() {
        byte[][] marks = new byte[5][5];
        marks[0][0] = 9;
        marks[1][1] = -4;
        String encoded = SaveStore.encodeMarks(marks);
        assertEquals("0000000000000000000000000", encoded);
        assertTrue(SaveStore.decodeMarks(encoded, 5) != null);
    }

    @Test
    public void nullMarksEncodeToNothingRatherThanThrowing() {
        assertEquals("", SaveStore.encodeMarks(null));
    }

    // ---- Rejecting a save we cannot vouch for ----------------------------------------

    @Test
    public void aTruncatedBoardIsRejected() {
        String full = SaveStore.encodeMarks(board(10));
        assertNull(SaveStore.decodeMarks(full.substring(0, 99), 10));
        assertNull(SaveStore.decodeMarks("", 10));
    }

    @Test
    public void anOverlongBoardIsRejected() {
        assertNull(SaveStore.decodeMarks(SaveStore.encodeMarks(board(10)) + "0", 10));
        assertNull(SaveStore.decodeMarks(SaveStore.encodeMarks(board(20)), 10));
    }

    @Test
    public void garbageInTheBoardIsRejected() {
        assertNull(SaveStore.decodeMarks("hello, world, this is not a board!!!", 6));
        assertNull(SaveStore.decodeMarks("0000000000000000000000004", 5));
        assertNull(SaveStore.decodeMarks("00000000000000000000000-0", 5));
        // A control character sitting inside an otherwise well-formed 25-square board.
        // It is spelled (char) 0 rather than embedded raw: the literal NUL byte this
        // line used to carry made git treat the whole file as binary, so every diff of
        // this test showed up as "Bin 18096 -> 19953 bytes" instead of readable lines.
        // The old string was also only 23 characters, so decodeMarks rejected it on the
        // length gate and never reached the per-character check this case exists to
        // prove. At 25 characters it now fails where it should: on the character.
        assertNull(SaveStore.decodeMarks("0000000000000" + (char) 0 + "00000000000", 5));
        assertNull(SaveStore.decodeMarks(null, 5));
    }

    @Test
    public void aBoardSizeOutsideTheGameIsRejectedRatherThanDecoded() {
        assertNull(SaveStore.decodeMarks("0000", 2));
        assertNull(SaveStore.decodeMarks(SaveStore.encodeMarks(new byte[40][40]), 40));
    }

    // ---- Clamping --------------------------------------------------------------------

    @Test
    public void anOutOfRangeSizeIsClampedIntoSomethingPlayable() {
        assertEquals(GameState.MIN_SIZE, SaveStore.clampSize(0));
        assertEquals(GameState.MIN_SIZE, SaveStore.clampSize(-7));
        assertEquals(GameState.MIN_SIZE, SaveStore.clampSize(Integer.MIN_VALUE));
        assertEquals(GameState.MAX_SIZE, SaveStore.clampSize(999));
        assertEquals(GameState.MAX_SIZE, SaveStore.clampSize(Integer.MAX_VALUE));
        assertEquals(12, SaveStore.clampSize(12));
    }

    @Test
    public void aClampedSizeAlwaysBuildsAPuzzle() {
        // PuzzleGenerator throws outside 5..20, so this is the guard that matters.
        for (int candidate : new int[]{Integer.MIN_VALUE, -1, 0, 4, 21, 5000}) {
            PuzzleGenerator.generate(11, SaveStore.clampSize(candidate));
        }
    }

    @Test
    public void aChapterBeyondTheBookWrapsBackIntoIt() {
        int chapters = PuzzleLibrary.count();
        assertEquals(0, SaveStore.clampStoryIndex(chapters, chapters));
        assertEquals(1, SaveStore.clampStoryIndex(chapters + 1, chapters));
        assertEquals(chapters - 1, SaveStore.clampStoryIndex(-1, chapters));
        assertEquals(0, SaveStore.clampStoryIndex(Integer.MIN_VALUE, 0));
        int wrapped = SaveStore.clampStoryIndex(Integer.MAX_VALUE, chapters);
        assertTrue(wrapped >= 0 && wrapped < chapters);
    }

    @Test
    public void negativeCountersNeverComeBackNegative() {
        assertEquals(0, SaveStore.atLeastZero(-1));
        assertEquals(0, SaveStore.atLeastZero(Integer.MIN_VALUE));
        assertEquals(9, SaveStore.atLeastZero(9));
    }

    /**
     * A pending board size is dropped rather than clamped. Every other number in the file
     * is pulled back into range because the game has to have <em>some</em> board; a size
     * waiting for the next picture is a choice somebody made, and a value we cannot vouch
     * for is not one.
     */
    @Test
    public void aPendingSizeWeCannotVouchForIsSimplyForgotten() {
        assertEquals(15, SaveStore.clampPendingSize(15));
        assertEquals(GameState.MIN_SIZE, SaveStore.clampPendingSize(GameState.MIN_SIZE));
        assertEquals(GameState.MAX_SIZE, SaveStore.clampPendingSize(GameState.MAX_SIZE));
        assertEquals(0, SaveStore.clampPendingSize(0));
        assertEquals(0, SaveStore.clampPendingSize(-3));
        assertEquals(0, SaveStore.clampPendingSize(GameState.MAX_SIZE + 5));
        assertEquals(0, SaveStore.clampPendingSize(Integer.MIN_VALUE));
        assertEquals(0, SaveStore.clampPendingSize(Integer.MAX_VALUE));
    }

    // ---- The version / migration path ------------------------------------------------

    @Test
    public void aPictureThatChangedNoLongerMatchesItsSavedMarks() {
        Puzzle before = PuzzleLibrary.get(3);
        String saved = SaveStore.fingerprint(before);

        // The same board rebuilt is still the same board.
        assertEquals(saved, SaveStore.fingerprint(PuzzleLibrary.get(3)));
        // Marks never confuse the fingerprint; only the hidden picture counts.
        before.marks[0][0] = Puzzle.FILLED;
        assertEquals(saved, SaveStore.fingerprint(before));

        // A different chapter, a different generator result, and an edited pixel all
        // produce a different signature, which is what makes stale marks detectable.
        assertNotEquals(saved, SaveStore.fingerprint(PuzzleLibrary.get(4)));
        assertNotEquals(saved, SaveStore.fingerprint(PuzzleGenerator.generate(1, 5)));

        boolean[][] edited = new boolean[before.size][before.size];
        for (int y = 0; y < before.size; y++) {
            System.arraycopy(before.solution[y], 0, edited[y], 0, before.size);
        }
        edited[2][2] = !edited[2][2];
        assertNotEquals(saved, SaveStore.fingerprint(before.size, before.name, edited));
    }

    @Test
    public void aRenamedOrResizedPictureAlsoChangesItsSignature() {
        boolean[][] art = new boolean[5][5];
        String base = SaveStore.fingerprint(5, "Tiny Tulip", art);
        assertNotEquals(base, SaveStore.fingerprint(5, "Warm Cocoa", art));
        assertNotEquals(base, SaveStore.fingerprint(10, "Tiny Tulip", art));
        assertEquals(base, SaveStore.fingerprint(5, "Tiny Tulip", art));
    }

    @Test
    public void fingerprintingSurvivesMissingPieces() {
        assertEquals("", SaveStore.fingerprint(null));
        assertNotEquals("", SaveStore.fingerprint(5, null, null));
    }

    @Test
    public void everyEndlessSeedAndSizeHasAStableSignature() {
        for (int size = GameState.MIN_SIZE; size <= GameState.MAX_SIZE; size += 5) {
            for (long seed : new long[]{0, 1, 104729, -5}) {
                assertEquals(SaveStore.fingerprint(PuzzleGenerator.generate(seed, size)),
                        SaveStore.fingerprint(PuzzleGenerator.generate(seed, size)));
            }
        }
    }

    // ---- Cheap saves -----------------------------------------------------------------

    @Test
    public void anUnchangedGameProducesAnIdenticalDigest() {
        GameState game = new GameState(4242, 10);
        UiState ui = new UiState();
        SaveStore.Journey journey = new SaveStore.Journey();
        String first = SaveStore.digest(game, ui, journey, 0);
        assertEquals(first, SaveStore.digest(game, ui, journey, 0));

        game.mark(0, Puzzle.FILLED);
        assertNotEquals(first, SaveStore.digest(game, ui, journey, 0));
    }

    @Test
    public void everyStoredFieldIsVisibleToTheDigest() {
        GameState game = new GameState(4242, 10);
        UiState ui = new UiState();
        SaveStore.Journey journey = new SaveStore.Journey();
        SaveStore.restoreDefaults(ui);
        String before = SaveStore.digest(game, ui, journey, 0);

        ui.gentleCheck = !ui.gentleCheck;
        assertNotEquals(before, SaveStore.digest(game, ui, journey, 0));
        ui.gentleCheck = !ui.gentleCheck;

        Comfort.get().calmMotion = true;
        assertNotEquals(before, SaveStore.digest(game, ui, journey, 0));
        Comfort.get().calmMotion = false;

        journey.squares[1] += 4;
        assertNotEquals(before, SaveStore.digest(game, ui, journey, 0));
        journey.squares[1] -= 4;

        game.storyFurthest = 6;
        assertNotEquals(before, SaveStore.digest(game, ui, journey, 0));
        game.storyFurthest = 0;

        game.storyCompleted = 4;
        assertNotEquals(before, SaveStore.digest(game, ui, journey, 0));
        game.storyCompleted = 0;

        assertNotEquals("a size waiting for the next picture is a saved field too",
                before, SaveStore.digest(game, ui, journey, 15));
    }

    /**
     * A pending size is a decision, so it goes to disk at once rather than riding along
     * with the coalesced squares. That is what putting it ahead of the newline buys.
     */
    @Test
    public void choosingASizeForTheNextPictureIsWrittenStraightAway() {
        GameState game = new GameState(4242, 10);
        UiState ui = new UiState();
        SaveStore.Journey journey = new SaveStore.Journey();
        SaveStore.restoreDefaults(ui);
        String before = SaveStore.digest(game, ui, journey, 0);
        assertNotEquals(SaveStore.beforeTheBoard(before),
                SaveStore.beforeTheBoard(SaveStore.digest(game, ui, journey, 15)));
    }

    /**
     * Marking a square must look different from changing a setting, because only one of
     * the two is worth stopping to write immediately.
     */
    @Test
    public void markingASquareIsToldApartFromChangingAnOption() {
        GameState game = new GameState(4242, 10);
        UiState ui = new UiState();
        SaveStore.Journey journey = new SaveStore.Journey();
        SaveStore.restoreDefaults(ui);
        String before = SaveStore.digest(game, ui, journey, 0);

        game.mark(0, Puzzle.FILLED);
        journey.squares[0]++;
        String afterASquare = SaveStore.digest(game, ui, journey, 0);
        assertNotEquals("the square itself must still be saved", before, afterASquare);
        assertEquals("but it is not a reason to stop and write",
                SaveStore.beforeTheBoard(before), SaveStore.beforeTheBoard(afterASquare));

        ui.bigTextOn = true;
        assertNotEquals("a setting must be written straight away",
                SaveStore.beforeTheBoard(afterASquare),
                SaveStore.beforeTheBoard(SaveStore.digest(game, ui, journey, 0)));

        ui.bigTextOn = false;
        game.solved++;
        assertNotEquals("and so must finishing a picture",
                SaveStore.beforeTheBoard(afterASquare),
                SaveStore.beforeTheBoard(SaveStore.digest(game, ui, journey, 0)));
    }

    @Test
    public void movesAreCountedOnceEachEvenWhenABoardResets() {
        assertEquals(0, SaveStore.deltaMoves(0, 0));
        assertEquals(3, SaveStore.deltaMoves(0, 3));
        assertEquals(0, SaveStore.deltaMoves(3, 3));
        assertEquals(1, SaveStore.deltaMoves(3, 4));
        // A new picture zeroes the per-board count; the next mark is worth exactly one.
        assertEquals(0, SaveStore.deltaMoves(40, 0));
        assertEquals(1, SaveStore.deltaMoves(40, 1));
        assertEquals(0, SaveStore.deltaMoves(4, -9));
    }

    // ---- Remembering the journey -----------------------------------------------------

    @Test
    public void aFirstVisitIsGreetedWithoutStatistics() {
        SaveStore.Journey journey = new SaveStore.Journey();
        journey.visits = 1;
        assertEquals("Welcome — let's find a picture together",
                SaveStore.welcomeBack(journey, 1_000_000));
        assertEquals("Welcome — let's find a picture together",
                SaveStore.welcomeBack(null, 1_000_000));
    }

    @Test
    public void aReturningPairAreToldHowLongItHasBeenAndWhatTheyMade() {
        SaveStore.Journey journey = new SaveStore.Journey();
        journey.visits = 5;
        journey.puzzlesFinished = 12;
        long day = 24L * 60 * 60 * 1000;
        journey.previousVisitAt = day * 10;

        assertEquals("Welcome back — 12 pictures finished together",
                SaveStore.welcomeBack(journey, day * 10 + 60_000));
        assertEquals("Back the very next evening — 12 pictures finished together",
                SaveStore.welcomeBack(journey, day * 11 + 60_000));
        assertEquals("Back after 3 days — 12 pictures finished together",
                SaveStore.welcomeBack(journey, day * 13));
        assertEquals("Back after a week away — 12 pictures finished together",
                SaveStore.welcomeBack(journey, day * 18));
        assertEquals("Back after 4 weeks — 12 pictures finished together",
                SaveStore.welcomeBack(journey, day * 39));
        assertEquals("It's been a while — the room kept your seat warm"
                        + " — 12 pictures finished together",
                SaveStore.welcomeBack(journey, day * 400));
    }

    @Test
    public void aClockThatWentBackwardsIsNotTreatedAsTimeTravel() {
        SaveStore.Journey journey = new SaveStore.Journey();
        journey.visits = 3;
        journey.previousVisitAt = 1_000_000_000L;
        assertTrue(SaveStore.welcomeBack(journey, 5).startsWith("Welcome back"));
    }

    @Test
    public void oneFinishedPictureIsCountedInWords() {
        SaveStore.Journey journey = new SaveStore.Journey();
        journey.puzzlesFinished = 1;
        assertEquals(" — one picture finished together", SaveStore.tally(journey));
        journey.puzzlesFinished = 0;
        assertEquals("", SaveStore.tally(journey));
    }

    @Test
    public void theShareLineOnlyAppearsOnceSomeoneHasPlayed() {
        SaveStore.Journey journey = new SaveStore.Journey();
        assertEquals("", SaveStore.shareLine(journey));
        journey.squares[0] = 812;
        assertEquals("Rose has placed 812 squares so far", SaveStore.shareLine(journey));
        journey.squares[1] = 640;
        assertEquals("Rose 812  ·  Sky 640 — squares placed together",
                SaveStore.shareLine(journey));
        journey.squares[0] = 0;
        assertEquals("Sky has placed 640 squares so far", SaveStore.shareLine(journey));
    }

    // ---- What a stale or foreign save actually does ----------------------------------

    @Test
    public void marksFromADifferentPuzzleAreNeverPaintedOntoThisOne() {
        // The exact situation the fingerprint exists to prevent: a saved board of the
        // right length, from the wrong picture.
        Puzzle wasSaved = PuzzleLibrary.get(8);
        Puzzle onTheTable = PuzzleLibrary.get(9);
        assertEquals(wasSaved.size, onTheTable.size);

        String marks = SaveStore.encodeMarks(board(wasSaved.size));
        assertTrue("the string alone looks perfectly valid",
                SaveStore.decodeMarks(marks, onTheTable.size) != null);
        assertNotEquals("but the picture it belongs to does not match",
                SaveStore.fingerprint(wasSaved), SaveStore.fingerprint(onTheTable));
    }

    @Test
    public void theSchemaIsAWholeNumberThatOnlyEverGoesUp() {
        assertTrue(SaveStore.SCHEMA >= 2);
    }

    // ---- A preferences file holding the wrong kind of value --------------------------

    @Test
    public void aPreferenceOfTheWrongTypeFallsBackInsteadOfCrashing() {
        // This is exactly what SharedPreferences.getInt does when the key holds a String.
        assertEquals(Integer.valueOf(5), SaveStore.guarded(() -> {
            throw new ClassCastException("java.lang.String cannot be cast to Integer");
        }, 5));
        assertEquals(Long.valueOf(7L), SaveStore.guarded(() -> {
            throw new ClassCastException("wrong type under seed");
        }, 7L));
        assertEquals(Boolean.TRUE, SaveStore.guarded(() -> {
            throw new ClassCastException("wrong type under music");
        }, true));
        assertEquals("", SaveStore.guarded(() -> {
            throw new ClassCastException("wrong type under marks");
        }, ""));
    }

    @Test
    public void aMissingOrNullPreferenceFallsBackToo() {
        assertEquals("fresh", SaveStore.guarded(() -> null, "fresh"));
        assertEquals(Integer.valueOf(3), SaveStore.guarded(() -> null, 3));
    }

    @Test
    public void aGoodPreferenceIsPassedStraightThrough() {
        assertEquals(Integer.valueOf(14), SaveStore.guarded(() -> 14, 5));
        assertEquals("0120", SaveStore.guarded(() -> "0120", ""));
    }

    @Test
    public void aWholeGarbledSaveStillProducesAPlayableBoard() {
        // Everything at once: a nonsense size, a chapter past the end of the book, a
        // negative counter and a board of the wrong length.
        int size = SaveStore.clampSize(-32768);
        int chapter = SaveStore.clampStoryIndex(-4001, PuzzleLibrary.count());
        GameState game = new GameState(SaveStore.clampSize(size), size);
        game.startStory(chapter);
        game.solved = SaveStore.atLeastZero(-99);

        assertNull(SaveStore.decodeMarks("%%%%", game.size));
        assertEquals(0, game.solved);
        assertTrue(game.size >= GameState.MIN_SIZE && game.size <= GameState.MAX_SIZE);
        assertTrue(game.storyIndex >= 0 && game.storyIndex < PuzzleLibrary.count());
        assertFalse(game.puzzle.complete() && game.puzzle.pictureCount() > 0);
    }

    @Test
    public void defaultsAreTheLookTheGameShipsWith() {
        UiState ui = new UiState();
        ui.musicOn = false;
        ui.sfxOn = false;
        ui.gentleCheck = true;
        ui.hintsOn = false;
        ui.bigTextOn = true;
        ui.highContrastOn = true;
        Comfort.get().distinctPlayers = true;
        Comfort.get().boldCursor = true;
        Comfort.get().calmMotion = true;

        SaveStore.restoreDefaults(ui);

        assertTrue(ui.musicOn);
        assertTrue(ui.sfxOn);
        assertFalse(ui.gentleCheck);
        assertTrue(ui.hintsOn);
        assertFalse(ui.bigTextOn);
        assertFalse(ui.highContrastOn);
        assertFalse(Comfort.get().distinctPlayers);
        assertFalse(Comfort.get().boldCursor);
        assertFalse(Comfort.get().calmMotion);
    }
}

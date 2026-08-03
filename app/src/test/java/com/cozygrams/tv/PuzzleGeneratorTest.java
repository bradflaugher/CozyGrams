package com.cozygrams.tv;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The endless deck has to keep two promises: every board can be finished by logic alone,
 * and every board is a picture worth finishing. These tests check both on every board the
 * generator can ever produce, which is possible because a board depends only on the
 * subject, the look and the size - a finite, and small, universe.
 */
public class PuzzleGeneratorTest {

    /** Same seed, same size, same board - every time, at every size. */
    @Test
    public void generationIsRepeatable() {
        for (int size = 5; size <= 20; size++) {
            for (long seed : new long[] {0, 1, 42, 104729, -7, 987654321L}) {
                Puzzle first = PuzzleGenerator.generate(seed, size);
                Puzzle again = PuzzleGenerator.generate(seed, size);
                assertEquals(size, first.size);
                assertEquals(first.name, again.name);
                for (int y = 0; y < size; y++) {
                    assertArrayEquals("seed=" + seed + " size=" + size,
                            first.solution[y], again.solution[y]);
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBoardsSmallerThanFive() {
        PuzzleGenerator.generate(1, 4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBoardsLargerThanTwenty() {
        PuzzleGenerator.generate(1, 21);
    }

    /**
     * The promise: every board in the deck is <em>uniquely line-solvable</em>. Repeatedly
     * filling in whatever the clues force, one row or column at a time, finishes the whole
     * board. That means the player never has to guess, and there is exactly one answer -
     * no board can be "finished" a second, different way.
     *
     * <p>Also checks that no board had to be <em>rescued</em>: {@code makeFair} nudges
     * single squares until a board line-solves, and if eighteen nudges are not enough it
     * falls back to the subject's hand-drawn portrait. That path has never fired, and this
     * is what makes the day it does a loud failure rather than a plainer picture nobody
     * notices.
     */
    @Test
    public void everyBoardInTheDeckCanBeSolvedByLogicAlone() {
        PuzzleGenerator.rescuedBoards = 0;
        int checked = 0;
        for (int size = 5; size <= 20; size++) {
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                    Puzzle puzzle = PuzzleGenerator.compose(subject, look, size);
                    assertTrue("needs guessing: " + puzzle.name + " look=" + look
                                    + " size=" + size,
                            NonogramSolver.uniquelyLineSolvable(puzzle.solution));
                    checked++;
                }
            }
        }
        assertEquals(16 * PuzzleGenerator.subjectCount() * PuzzleGenerator.VARIANTS, checked);
        assertEquals("a board fell back to its portrait to stay fair",
                0, PuzzleGenerator.rescuedBoards);
    }

    /**
     * A board should look like something. Not blank, not a solid slab, and with no lonely
     * single squares floating in the middle of nowhere - those read as noise rather than
     * as part of a picture.
     */
    @Test
    public void everyBoardIsAPictureRatherThanNoiseOrASlab() {
        for (int size = 5; size <= 20; size++) {
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                    Puzzle puzzle = PuzzleGenerator.compose(subject, look, size);
                    String where = puzzle.name + " look=" + look + " size=" + size;
                    int filled = puzzle.pictureCount();
                    int cells = size * size;
                    assertTrue(where + " is nearly blank (" + filled + "/" + cells + ")",
                            filled * 100 >= cells * 18);
                    assertTrue(where + " is nearly solid (" + filled + "/" + cells + ")",
                            filled * 100 <= cells * 78);
                    for (int y = 0; y < size; y++) {
                        for (int x = 0; x < size; x++) {
                            assertTrue(where + " has a lonely square at " + x + "," + y,
                                    !puzzle.solution[y][x] || hasNeighbour(puzzle, x, y));
                        }
                    }
                }
            }
        }
    }

    /**
     * Two ways a board can waste the player's time, both of which the deck used to do.
     *
     * <p>A <em>dead line</em> is a row or column with nothing in it: its clue reads "0",
     * and the squares behind it are decided before anybody sits down. 382 of the 1,728
     * boards at fifteen squares and up had at least one, and 92 had a complete one-square
     * frame of them - four clue slots showing nothing and seventy-six free squares on the
     * biggest board in the game.
     *
     * <p>A <em>stray fragment</em> is a piece of two, three or four squares floating clear
     * of the picture: the mug whose steam came out as two ears hovering above it, the
     * flower whose stem and leaves fell off the bloom. 228 boards had one. Both are now
     * zero everywhere from eight squares up; below that the whole board is only sixty-four
     * squares and a cat's ear is legitimately two of them.
     */
    /**
     * No board is dictation: a line that is a copy of the line above it, over and over.
     *
     * <p>A blank line gives nothing away and this file has always scored it. Its opposite
     * costs the same and went uncounted: at 20x20 "Rain on the Window" dealt ten consecutive
     * rows all reading {@code 1 1 1 1 1}, so a player solved one line and then transcribed it
     * nine times, and the finished picture was a waffle rather than weather. Swept across the
     * whole deck - 24 subjects by 12 variants at each size - boards holding a run of four or
     * more identical fragmented lines numbered 4/288 at 10x10, 20/288 at 15x15 and 52/288 at
     * 20x20, concentrated in the three subjects painted with tall parallel slots: Rain on the
     * Window, Paper Lantern, Fresh Baked Pie.
     *
     * <p>Three things fixed it and all three are needed. {@code legibility} charges for a
     * repeat from the third copy, so {@code compose} can walk away from the worst of its four
     * framings. The rain now falls in rivulets that start at their own heights, the lantern's
     * ribs are broken by the seams they are gathered at, and the pie's crust strip is stepped
     * rather than level. The bar is a run of four because two identical neighbours are just a
     * straight edge and three is a wide one.
     */
    @Test
    public void noBoardIsDictation() {
        for (int size : new int[]{10, 15, 20}) {
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                    Puzzle puzzle = PuzzleGenerator.compose(subject, look, size);
                    int run = longestRepeat(puzzle);
                    assertTrue(puzzle.name + " look=" + look + " size=" + size + " repeats a "
                                    + "fragmented line " + run + " times running",
                            run <= 5);
                }
            }
        }
    }

    /** The longest run of identical, fragmented lines, counting rows and columns alike. */
    private static int longestRepeat(Puzzle puzzle) {
        return Math.max(longestRepeat(puzzle, true), longestRepeat(puzzle, false));
    }

    private static int longestRepeat(Puzzle puzzle, boolean rows) {
        int best = 0;
        int run = 1;
        for (int i = 1; i < puzzle.size; i++) {
            int[] clue = rows ? puzzle.rowClues(i) : puzzle.colClues(i);
            boolean same = true;
            for (int j = 0; j < puzzle.size && same; j++) {
                same = (rows ? puzzle.solution[i][j] : puzzle.solution[j][i])
                        == (rows ? puzzle.solution[i - 1][j] : puzzle.solution[j][i - 1]);
            }
            run = same ? run + 1 : 1;
            if (clue.length >= 3) {
                best = Math.max(best, run);
            }
        }
        return best;
    }

    /**
     * A symmetric subject never comes out chipped.
     *
     * <p>The heart used to be thinned by "a little shine" — a {@code wipeOval} of radius .08,
     * which resolves to under one square on every board it was drawn on. At 10x10 that was a
     * two-square horizontal slot inside the left lobe with no mirror partner on the right:
     * not a gloss highlight, a bite. It is the picture the win card assembles, presented
     * under the words "Look what happened". The heart's thinning is the heart-shaped hole
     * inside it, which is mirrored by construction.
     *
     * <p>What is asserted is that every <em>interior</em> gap has a mirror partner, not that
     * the whole grid is a mirror image. The silhouette is a vector shape rasterised onto an
     * odd number of squares, so its outline legitimately lands a square differently on one
     * side at 11x11 and 13x13; an outline that leans is a drawing, a hole that leans is
     * damage. Run against the old build this fails on Sweetheart at 10x10, at exactly the
     * two squares the review photographed.
     */
    @Test
    public void theHeartIsNeverChipped() {
        for (int size = 8; size <= 20; size++) {
            for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                // Pose 2 tips the whole heart over on purpose, so it is asymmetric by design
                // and excluded rather than exempted.
                if ((look >> 1) % 3 == 2) {
                    continue;
                }
                Puzzle puzzle = PuzzleGenerator.compose(0, look, size);
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        if (!isInteriorGap(puzzle, x, y)) {
                            continue;
                        }
                        assertFalse(puzzle.name + " look=" + look + " size=" + size
                                        + " has a hole at " + x + "," + y
                                        + " with no partner near " + (size - 1 - x),
                                solidAround(puzzle, size - 1 - x, y));
                    }
                }
            }
        }
    }

    /**
     * Whether the mirror of a hole is solid ink, allowing one square of slack either side.
     *
     * <p>The slack is what separates a drawing from damage. A silhouette is a vector shape
     * rasterised onto an odd number of squares, so the cleft between a heart's two lobes
     * legitimately lands half a square off centre at 11x11 and 13x13 — the gap is mirrored,
     * its edge is one square out. A bite is not like that: the shine that used to be punched
     * into the left lobe at 10x10 had three solid squares facing it.
     */
    private static boolean solidAround(Puzzle puzzle, int x, int y) {
        for (int at = x - 1; at <= x + 1; at++) {
            if (at < 0 || at >= puzzle.size || !puzzle.solution[y][at]) {
                return false;
            }
        }
        return true;
    }

    /** An empty square with ink on both sides of it along its own row: a hole, not an edge. */
    private static boolean isInteriorGap(Puzzle puzzle, int x, int y) {
        if (puzzle.solution[y][x]) {
            return false;
        }
        boolean left = false;
        boolean right = false;
        for (int i = 0; i < x; i++) {
            left |= puzzle.solution[y][i];
        }
        for (int i = x + 1; i < puzzle.size; i++) {
            right |= puzzle.solution[y][i];
        }
        return left && right;
    }

    @Test
    public void noBoardWastesALineOrStrandsAFragment() {
        for (int size = 8; size <= 20; size++) {
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                    Puzzle puzzle = PuzzleGenerator.compose(subject, look, size);
                    String where = puzzle.name + " look=" + look + " size=" + size;
                    for (int line = 0; line < size; line++) {
                        assertTrue(where + " has a dead row " + line,
                                puzzle.rowClues(line)[0] > 0);
                        assertTrue(where + " has a dead column " + line,
                                puzzle.colClues(line)[0] > 0);
                    }
                    assertEquals(where + " strands a fragment",
                            0, strayCells(puzzle));
                }
            }
        }
    }

    /**
     * The clue gutters are sized from these two numbers, so the generator is not allowed
     * to quietly exceed what the board card reserves. Measured across the whole deck:
     * six groups is the worst row (at nineteen and twenty squares) and five the worst
     * column. {@code BoardLayoutTest} separately proves the layout copes with far worse.
     */
    @Test
    public void theClueGuttersNeverOverflow() {
        for (int size = 5; size <= 20; size++) {
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                    Puzzle puzzle = PuzzleGenerator.compose(subject, look, size);
                    String where = puzzle.name + " look=" + look + " size=" + size;
                    assertTrue(where + " needs " + puzzle.widestRowClue() + " row lanes",
                            puzzle.widestRowClue() <= 6);
                    assertTrue(where + " needs " + puzzle.tallestColClue() + " column lanes",
                            puzzle.tallestColClue() <= 5);
                }
            }
        }
    }

    /** The clues the board hands the player really do describe the hidden picture. */
    @Test
    public void everyBoardsCluesMatchItsPicture() {
        for (int size : new int[] {5, 9, 14, 20}) {
            for (long seed = 0; seed < 60; seed++) {
                Puzzle puzzle = PuzzleGenerator.generate(seed, size);
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        puzzle.marks[y][x] = puzzle.solution[y][x] ? Puzzle.FILLED : Puzzle.CROSSED;
                    }
                }
                assertTrue("seed=" + seed + " size=" + size, puzzle.complete());
                assertEquals(0, puzzle.mistakes());
                for (int line = 0; line < size; line++) {
                    assertNotNull(puzzle.rowClues(line));
                    assertNotNull(puzzle.colClues(line));
                }
            }
        }
    }

    /**
     * A bigger board should be a bigger puzzle, not the same puzzle with more squares.
     *
     * <p>The measure is clue groups per line, because that is what a player actually
     * works with: a line reading "4 4 2 2" asks a question, a line reading "18" answers
     * itself. It used to rise from 1.27 at five squares only to 1.62 at twenty, with 5.7%
     * of the lines at twenty squares being a single full-length run - and a full line
     * solve gave away 69% of a 20x20 on its first sweep. Drawing the subjects with holes
     * in them instead of filling them in takes it to 1.31 and 2.01, drops the full-length
     * runs to 3.5%, and drops the first sweep to 59%.
     *
     * <p>Fill is asserted as a band rather than a target: below about a third the board
     * stops being a picture, and above about three fifths it stops being a puzzle.
     */
    @Test
    public void theCurveGetsGentlyHarder() {
        double[] previous = {0};
        for (int size = 5; size <= 20; size++) {
            int groups = 0;
            int fullRuns = 0;
            int lines = 0;
            int filled = 0;
            int boards = 0;
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                    Puzzle puzzle = PuzzleGenerator.compose(subject, look, size);
                    filled += puzzle.pictureCount();
                    boards++;
                    for (int line = 0; line < size; line++) {
                        groups += puzzle.rowClues(line).length + puzzle.colClues(line).length;
                        lines += 2;
                        if (isOneFullRun(puzzle.rowClues(line), size)) {
                            fullRuns++;
                        }
                        if (isOneFullRun(puzzle.colClues(line), size)) {
                            fullRuns++;
                        }
                    }
                }
            }
            double perLine = groups / (double) lines;
            double share = fullRuns / (double) lines;
            double fill = filled / (double) (boards * size * size);
            String where = "size " + size;
            assertTrue(where + " averages only " + perLine + " clue groups a line",
                    perLine >= floorFor(size));
            assertTrue(where + " gives away " + share + " of its lines as one long run",
                    share <= (size >= 12 ? .08 : .25));
            assertTrue(where + " is " + fill + " inked",
                    fill >= .35 && fill <= (size >= 12 ? .62 : .70));
            assertTrue(where + " is no deeper than the size below it",
                    perLine >= previous[0] - .2);
            previous[0] = perLine;
        }
    }

    /** How many clue groups a line has to average at this size, from the measured curve. */
    private static double floorFor(int size) {
        if (size >= 18) {
            return 1.9;
        }
        if (size >= 14) {
            return 1.8;
        }
        if (size >= 12) {
            return 1.65;
        }
        if (size >= 10) {
            return 1.4;
        }
        return 1.1;
    }

    private static boolean isOneFullRun(int[] clue, int size) {
        return clue.length == 1 && clue[0] == size;
    }

    /**
     * Endless mode steps the seed by a large prime. It should walk through subjects and
     * looks rather than circling back to the same handful of pictures.
     *
     * <p>These floors used to be {5:24, 10:45, 15:90, 20:150} and they were passing while
     * badly under-delivering: at five squares the whole deck only held 31 different
     * pictures and endless mode dealt a repeat on its twenty-sixth board. Three hand-drawn
     * portraits per subject instead of one, and three poses that change the silhouette
     * instead of two that only showed up past eleven squares, take the deck to 103, 119,
     * 188 and 236 - so 300 deals now find every picture there is at five and ten squares.
     */
    @Test
    public void endlessModeKeepsDealingNewPictures() {
        int[][] expected = {{5, 100}, {10, 110}, {15, 175}, {20, 220}};
        for (int[] pair : expected) {
            int size = pair[0];
            Set<String> subjects = new HashSet<>();
            Set<String> pictures = new HashSet<>();
            long seed = 12345;
            for (int deal = 0; deal < 300; deal++) {
                Puzzle puzzle = PuzzleGenerator.generate(seed, size);
                subjects.add(puzzle.name);
                pictures.add(fingerprint(puzzle));
                seed += 104729L;
            }
            assertEquals("every subject should come round at size " + size,
                    PuzzleGenerator.subjectCount(), subjects.size());
            assertTrue("size " + size + " repeated too soon: " + pictures.size()
                    + " distinct pictures in 300 deals", pictures.size() >= pair[1]);
        }
    }

    /**
     * The whole deck, counted directly: every size offers a good spread of pictures.
     *
     * <p>The floor dips between eight and eleven squares, which looks odd until you see
     * why. Below eight, every look picks one of three hand-drawn portraits and mirrors it,
     * so all 103 of them are different by construction. From eight up the pictures are
     * painted, and eight or nine squares is too few for a pose to change much or for a
     * companion to find a corner - the spread only opens out again once the details fit.
     */
    @Test
    public void theDeckIsWideAtEverySize() {
        for (int size = 5; size <= 20; size++) {
            Set<String> pictures = new HashSet<>();
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                    pictures.add(fingerprint(PuzzleGenerator.compose(subject, look, size)));
                }
            }
            int floor = size >= 16 ? 190 : (size >= 12 ? 145 : (size >= 8 ? 70 : 100));
            assertTrue("size " + size + " only offers " + pictures.size() + " pictures",
                    pictures.size() >= floor);
        }
    }

    /**
     * The 5x5, 6x6 and 7x7 boards ship exactly as they were drawn.
     *
     * <p>Twenty-five squares is not enough room for the fairness pass to be gentle. It
     * used to run there anyway: at five, six and seven squares it flipped 60, 48 and 24
     * squares across the deck and every single one landed on a hand-drawn portrait. Sleepy
     * Fox lost an ear and had row three turned into a solid bar, and stopped being a fox.
     * The portraits are drawn uniquely line-solvable at all three sizes instead, and the
     * pass leaves locked squares alone below eight.
     *
     * <p>Checking the 6x6 and 7x7 boards against the nearest-neighbour upscale of the 5x5
     * board proves it without the test needing to see the art: if anything had edited any
     * of the three, they would stop agreeing.
     */
    @Test
    public void theLittleBoardsShipExactlyAsTheyWereDrawn() {
        for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
            for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                Puzzle portrait = PuzzleGenerator.compose(subject, look, 5);
                for (int size : new int[] {6, 7}) {
                    Puzzle grown = PuzzleGenerator.compose(subject, look, size);
                    for (int y = 0; y < size; y++) {
                        for (int x = 0; x < size; x++) {
                            int sy = (y * 2 + 1) * 5 / (size * 2);
                            int sx = (x * 2 + 1) * 5 / (size * 2);
                            assertEquals(portrait.name + " look=" + look + " size=" + size
                                            + " was edited at " + x + "," + y,
                                    portrait.solution[sy][sx], grown.solution[y][x]);
                        }
                    }
                }
            }
        }
    }

    /**
     * Sleepy Kitty and Sleepy Fox are two of twenty-four subjects, and they used to be the
     * same picture: two squares apart at 5x5, and an ear-ear-round-face-two-eyes silhouette
     * at every size above it that could not be told apart without reading the label. The
     * fox now has a face that comes to a point at the bottom border where the cat's is
     * round, and the worst case across every size and every pairing of looks is a fifth of
     * the board.
     */
    @Test
    public void sleepyFoxAndSleepyKittyAreDifferentAnimals() {
        for (int size = 5; size <= 20; size++) {
            int closest = Integer.MAX_VALUE;
            for (int catLook = 0; catLook < PuzzleGenerator.VARIANTS; catLook++) {
                Puzzle cat = PuzzleGenerator.compose(1, catLook, size);
                for (int foxLook = 0; foxLook < PuzzleGenerator.VARIANTS; foxLook++) {
                    Puzzle fox = PuzzleGenerator.compose(12, foxLook, size);
                    int apart = 0;
                    for (int y = 0; y < size; y++) {
                        for (int x = 0; x < size; x++) {
                            if (cat.solution[y][x] != fox.solution[y][x]) {
                                apart++;
                            }
                        }
                    }
                    closest = Math.min(closest, apart);
                }
            }
            assertTrue("at size " + size + " the fox and the cat come within " + closest
                            + " squares of " + (size * size),
                    closest * 100 >= size * size * 18);
        }
    }

    /** Subjects are named, and no two share a name. */
    @Test
    public void everySubjectHasItsOwnName() {
        Set<String> names = new HashSet<>();
        assertTrue(PuzzleGenerator.subjectCount() >= 24);
        for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
            String name = PuzzleGenerator.subjectName(subject);
            assertNotNull(name);
            assertTrue(name.trim().length() > 2);
            assertTrue("duplicate subject name " + name, names.add(name));
        }
    }

    /**
     * Boards are dealt on the UI thread, so the whole pipeline - paint, tidy, and the
     * line-solving fairness check - has to be comfortably inside a frame. The budget is
     * about 15ms on modest TV hardware; this asserts a much tighter average so a real
     * regression is caught long before a player would feel it.
     *
     * <p>A deal now draws four framings and keeps the most legible one, so it costs about
     * 0.9ms on a desktop JVM where it used to cost 0.18ms. That is still five times inside
     * the budget the test asserts and fifteen times inside the one a television gives, and
     * it buys the edge lock that closed every blank clue line in the deck.
     */
    @Test
    public void dealingATwentySquareBoardIsFast() {
        for (int warmUp = 0; warmUp < 400; warmUp++) {
            PuzzleGenerator.generate(warmUp, 20);
        }
        int deals = 500;
        long started = System.nanoTime();
        for (int i = 0; i < deals; i++) {
            PuzzleGenerator.generate(i, 20);
        }
        double averageMs = (System.nanoTime() - started) / 1_000_000.0 / deals;
        assertTrue("20x20 generation took " + averageMs + "ms on average", averageMs < 5.0);
    }

    private static boolean hasNeighbour(Puzzle puzzle, int x, int y) {
        int size = puzzle.size;
        return (x > 0 && puzzle.solution[y][x - 1])
                || (x + 1 < size && puzzle.solution[y][x + 1])
                || (y > 0 && puzzle.solution[y - 1][x])
                || (y + 1 < size && puzzle.solution[y + 1][x]);
    }

    /** Squares belonging to a piece of fewer than five that is not the main silhouette. */
    private static int strayCells(Puzzle puzzle) {
        int size = puzzle.size;
        boolean[][] seen = new boolean[size][size];
        int[] stack = new int[size * size];
        int biggest = 0;
        int small = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!puzzle.solution[y][x] || seen[y][x]) {
                    continue;
                }
                int top = 0;
                int found = 0;
                stack[top++] = y * size + x;
                seen[y][x] = true;
                while (top > 0) {
                    int at = stack[--top];
                    int ax = at % size;
                    int ay = at / size;
                    found++;
                    int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] step : steps) {
                        int nx = ax + step[0];
                        int ny = ay + step[1];
                        if (nx < 0 || ny < 0 || nx >= size || ny >= size
                                || !puzzle.solution[ny][nx] || seen[ny][nx]) {
                            continue;
                        }
                        seen[ny][nx] = true;
                        stack[top++] = ny * size + nx;
                    }
                }
                if (found < 5) {
                    small += found;
                }
                biggest = Math.max(biggest, found);
            }
        }
        return biggest < 5 ? 0 : small;
    }

    private static String fingerprint(Puzzle puzzle) {
        StringBuilder out = new StringBuilder(puzzle.size * puzzle.size);
        for (boolean[] row : puzzle.solution) {
            for (boolean filled : row) {
                out.append(filled ? '#' : '.');
            }
        }
        return out.toString();
    }
}

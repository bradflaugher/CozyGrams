package com.cozygrams.tv;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
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
     */
    @Test
    public void everyBoardInTheDeckCanBeSolvedByLogicAlone() {
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
     * Endless mode steps the seed by a large prime. It should walk through subjects and
     * looks rather than circling back to the same handful of pictures.
     */
    @Test
    public void endlessModeKeepsDealingNewPictures() {
        // Bigger boards have room for more of the deck's variation - a floating star, a
        // second window, an extra petal - so they are expected to repeat later.
        int[][] expected = {{5, 24}, {10, 45}, {15, 90}, {20, 150}};
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

    /** The whole deck, counted directly: every size offers a good spread of pictures. */
    @Test
    public void theDeckIsWideAtEverySize() {
        for (int size = 5; size <= 20; size++) {
            Set<String> pictures = new HashSet<>();
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                    pictures.add(fingerprint(PuzzleGenerator.compose(subject, look, size)));
                }
            }
            int floor = size >= 16 ? 140 : (size >= 12 ? 60 : (size >= 8 ? 40 : 24));
            assertTrue("size " + size + " only offers " + pictures.size() + " pictures",
                    pictures.size() >= floor);
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

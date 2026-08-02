package com.cozygrams.tv;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The Story Book is hand-drawn, so these tests are the proof-reading pass: every chapter
 * is a real picture, it can be finished by logic alone, and the book grows from little
 * 5x5 moments to unhurried 15x15 evenings.
 */
public class PuzzleLibraryTest {

    @Test
    public void theBookIsLongEnoughToBeAJourney() {
        assertTrue("the book should have at least sixteen chapters",
                PuzzleLibrary.count() >= 16);
    }

    @Test
    public void everyChapterIsSquareNamedAndTheRightSize() {
        Set<String> names = new HashSet<>();
        Set<Integer> sizesSeen = new HashSet<>();
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            Puzzle puzzle = PuzzleLibrary.get(chapter);
            assertNotNull(puzzle.name);
            assertTrue("duplicate chapter name " + puzzle.name, names.add(puzzle.name));
            assertTrue("odd chapter size " + puzzle.size,
                    puzzle.size == 5 || puzzle.size == 10 || puzzle.size == 15);
            sizesSeen.add(puzzle.size);
            for (boolean[] row : puzzle.solution) {
                assertEquals(puzzle.size, row.length);
            }
            for (int line = 0; line < puzzle.size; line++) {
                assertTrue(puzzle.rowClues(line).length > 0);
                assertTrue(puzzle.colClues(line).length > 0);
            }
        }
        assertTrue("the book should use every size it advertises",
                sizesSeen.containsAll(java.util.Arrays.asList(5, 10, 15)));
    }

    /**
     * The same promise the endless deck makes: line logic alone finishes every chapter,
     * so there is exactly one answer and nobody ever has to guess.
     */
    @Test
    public void everyChapterCanBeSolvedByLogicAlone() {
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            Puzzle puzzle = PuzzleLibrary.get(chapter);
            assertTrue("chapter " + (chapter + 1) + " (" + puzzle.name + ") needs guessing",
                    NonogramSolver.uniquelyLineSolvable(puzzle.solution));
        }
    }

    @Test
    public void everyChapterIsAPictureRatherThanNoiseOrASlab() {
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            Puzzle puzzle = PuzzleLibrary.get(chapter);
            int cells = puzzle.size * puzzle.size;
            int filled = puzzle.pictureCount();
            String where = "chapter " + (chapter + 1) + " (" + puzzle.name + ")";
            assertTrue(where + " is nearly blank", filled * 100 >= cells * 25);
            assertTrue(where + " is nearly solid", filled * 100 <= cells * 78);
            for (int y = 0; y < puzzle.size; y++) {
                for (int x = 0; x < puzzle.size; x++) {
                    assertTrue(where + " has a lonely square at " + x + "," + y,
                            !puzzle.solution[y][x] || hasNeighbour(puzzle, x, y));
                }
            }
        }
    }

    /** Tiny and warm at the start, richer later: chapters never shrink. */
    @Test
    public void theBookGrowsAsItGoes() {
        int previous = 0;
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            int size = PuzzleLibrary.get(chapter).size;
            assertTrue("chapter " + (chapter + 1) + " got smaller than the one before",
                    size >= previous);
            previous = size;
        }
        assertEquals("the book should end at its largest size", 15, previous);
    }

    /**
     * Chapter eight and nine are the hinge of the book - the first is the last of the
     * little ones, the second opens it out - and the game's own tests lean on that.
     */
    @Test
    public void theHingeOfTheBookStaysPut() {
        assertEquals("Moon Kiss", PuzzleLibrary.get(7).name);
        assertEquals(5, PuzzleLibrary.get(7).size);
        assertEquals("Love Birds", PuzzleLibrary.get(8).name);
        assertEquals(10, PuzzleLibrary.get(8).size);
    }

    @Test
    public void theBookWrapsCleanly() {
        assertEquals(PuzzleLibrary.get(0).name, PuzzleLibrary.get(PuzzleLibrary.count()).name);
        assertEquals(PuzzleLibrary.get(PuzzleLibrary.count() - 1).name,
                PuzzleLibrary.get(-1).name);
    }

    @Test
    public void authoredSolutionsSatisfyEveryClue() {
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            Puzzle puzzle = PuzzleLibrary.get(chapter);
            for (int y = 0; y < puzzle.size; y++) {
                for (int x = 0; x < puzzle.size; x++) {
                    puzzle.marks[y][x] = puzzle.solution[y][x] ? Puzzle.FILLED : Puzzle.CROSSED;
                }
            }
            assertTrue("chapter " + (chapter + 1), puzzle.complete());
            for (int line = 0; line < puzzle.size; line++) {
                assertTrue(puzzle.rowSolved(line));
                assertTrue(puzzle.colSolved(line));
            }
        }
    }

    private static boolean hasNeighbour(Puzzle puzzle, int x, int y) {
        int size = puzzle.size;
        return (x > 0 && puzzle.solution[y][x - 1])
                || (x + 1 < size && puzzle.solution[y][x + 1])
                || (y > 0 && puzzle.solution[y - 1][x])
                || (y + 1 < size && puzzle.solution[y + 1][x]);
    }
}

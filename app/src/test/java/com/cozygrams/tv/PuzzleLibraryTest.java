package com.cozygrams.tv;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The Story Book is hand-drawn, so these tests are the proof-reading pass: every chapter
 * is a real picture, it can be finished by logic alone, it is not a board the endless deck
 * already deals, and the book grows from little 5x5 moments to unhurried 20x20 evenings.
 */
public class PuzzleLibraryTest {

    @Test
    public void theBookIsLongEnoughToBeAJourney() {
        assertTrue("the book should have at least twenty-four chapters",
                PuzzleLibrary.count() >= 24);
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
                    puzzle.size == 5 || puzzle.size == 7 || puzzle.size == 10
                            || puzzle.size == 12 || puzzle.size == 15 || puzzle.size == 20);
            sizesSeen.add(puzzle.size);
            for (boolean[] row : puzzle.solution) {
                assertEquals(puzzle.size, row.length);
            }
            for (int line = 0; line < puzzle.size; line++) {
                assertTrue(puzzle.rowClues(line).length > 0);
                assertTrue(puzzle.colClues(line).length > 0);
            }
        }
        assertTrue("the book should use every rung of its ladder",
                sizesSeen.containsAll(java.util.Arrays.asList(5, 7, 10, 12, 15, 20)));
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
            for (int line = 0; line < puzzle.size; line++) {
                assertTrue(where + " has a dead row " + line, puzzle.rowClues(line)[0] > 0);
                assertTrue(where + " has a dead column " + line, puzzle.colClues(line)[0] > 0);
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
        assertEquals("the book should end at its largest size", 20, previous);
    }

    /**
     * The ladder itself, rung by rung. Eight of the old book's eighteen chapters were
     * 5x5 - nearly half of it spent on the size that is over in two minutes - and it then
     * jumped straight to ten squares and stopped at fifteen. No rung may carry more than a
     * quarter of the book now, and no step up may be more than five squares; the six
     * different sizes {@link #everyChapterIsSquareNamedAndTheRightSize} insists on are
     * what stop those two rules from being satisfied by 5, 10, 15 alone.
     */
    @Test
    public void noRungOfTheLadderTakesOverTheBook() {
        int previous = 0;
        int run = 0;
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            int size = PuzzleLibrary.get(chapter).size;
            run = size == previous ? run + 1 : 1;
            assertTrue("too many chapters in a row at " + size + "x" + size,
                    run * 4 <= PuzzleLibrary.count());
            if (previous > 0) {
                assertTrue("chapter " + (chapter + 1) + " jumps from " + previous
                        + " to " + size, size - previous <= 5);
            }
            previous = size;
        }
    }

    /**
     * A chapter has to be its own picture. Four of the eight chapters that used to open
     * the book were byte-identical to a board the endless deck deals - "First Heart" was
     * Sweetheart 5x5 look 0 square for square - and three more were within six squares of
     * one, so the handcrafted book only became handcrafted at chapter nine.
     *
     * <p>The bar is lower at five squares than above it, and honestly so: twenty-five
     * squares is a small space, the deck now draws 103 different pictures in it, and there
     * are only so many ways to draw a heart. From seven squares up every chapter is at
     * least eight squares away from anything the deck can deal, and most are dozens.
     */
    @Test
    public void chaptersAreNotDeckBoards() {
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            Puzzle puzzle = PuzzleLibrary.get(chapter);
            int closest = Integer.MAX_VALUE;
            String twin = "";
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                for (int look = 0; look < PuzzleGenerator.VARIANTS; look++) {
                    Puzzle dealt = PuzzleGenerator.compose(subject, look, puzzle.size);
                    int apart = 0;
                    for (int y = 0; y < puzzle.size; y++) {
                        for (int x = 0; x < puzzle.size; x++) {
                            if (dealt.solution[y][x] != puzzle.solution[y][x]) {
                                apart++;
                            }
                        }
                    }
                    if (apart < closest) {
                        closest = apart;
                        twin = dealt.name + " look " + look;
                    }
                }
            }
            assertTrue("chapter " + (chapter + 1) + " (" + puzzle.name + ") is only "
                            + closest + " squares from endless " + twin,
                    closest >= (puzzle.size <= 5 ? 2 : 8));
        }
    }

    /**
     * Every chapter has to ask at least one question. Five of the old eighteen - chapters
     * 2, 3, 4, 6 and 14 - were completely determined by one sweep of the clues: fill in
     * everything each line forces on its own, once, and the picture was finished. That is
     * dictation, not a puzzle, and it is the one thing an authored board can get wrong
     * that a generated one cannot.
     */
    @Test
    public void everyChapterAsksAtLeastOneQuestion() {
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            Puzzle puzzle = PuzzleLibrary.get(chapter);
            assertTrue("chapter " + (chapter + 1) + " (" + puzzle.name
                            + ") is finished by a single sweep of the clues",
                    firstSweepLeavesSomething(puzzle));
        }
    }

    /** Every chapter carries its one line of copy, and no two share one. */
    @Test
    public void everyChapterHasSomethingToSay() {
        Set<String> lines = new HashSet<>();
        for (int chapter = 0; chapter < PuzzleLibrary.count(); chapter++) {
            String line = PuzzleLibrary.line(chapter);
            assertNotNull(line);
            assertTrue("chapter " + (chapter + 1) + " has no line", line.trim().length() > 20);
            assertTrue("chapter " + (chapter + 1) + " runs long: " + line.length(),
                    line.length() <= 80);
            assertTrue("duplicate line: " + line, lines.add(line));
        }
        assertEquals(PuzzleLibrary.count(), lines.size());
    }

    /**
     * Chapter eight is the hinge of the book: the last of the little ones is behind it and
     * the boards open out to ten squares. It used to be chapter nine, back when the book
     * spent eight chapters at 5x5.
     */
    @Test
    public void theHingeOfTheBookStaysPut() {
        assertTrue("the chapters before the hinge should be small",
                PuzzleLibrary.get(6).size <= 7);
        assertEquals("the book should open out at chapter eight", 10, PuzzleLibrary.get(7).size);
    }

    @Test
    public void theBookWrapsCleanly() {
        assertEquals(PuzzleLibrary.get(0).name, PuzzleLibrary.get(PuzzleLibrary.count()).name);
        assertEquals(PuzzleLibrary.get(PuzzleLibrary.count() - 1).name,
                PuzzleLibrary.get(-1).name);
        assertEquals(PuzzleLibrary.line(0), PuzzleLibrary.line(PuzzleLibrary.count()));
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

    /**
     * True when one pass over every row and then every column - each line narrowed on its
     * own, with nothing carried back - leaves at least one square undecided.
     *
     * <p>The narrowing is deliberately the slow, obvious version rather than
     * {@link NonogramSolver}: that one runs to convergence, and what is being measured
     * here is exactly the difference between the first sweep and convergence.
     */
    private static boolean firstSweepLeavesSomething(Puzzle puzzle) {
        int size = puzzle.size;
        byte[][] known = new byte[size][size];
        for (int row = 0; row < size; row++) {
            byte[] line = new byte[size];
            System.arraycopy(known[row], 0, line, 0, size);
            narrow(line, puzzle.rowClues(row));
            System.arraycopy(line, 0, known[row], 0, size);
        }
        for (int col = 0; col < size; col++) {
            byte[] line = new byte[size];
            for (int y = 0; y < size; y++) {
                line[y] = known[y][col];
            }
            narrow(line, puzzle.colClues(col));
            for (int y = 0; y < size; y++) {
                known[y][col] = line[y];
            }
        }
        for (byte[] row : known) {
            for (byte square : row) {
                if (square == NonogramSolver.UNKNOWN) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Marks every square this clue forces, by trying every arrangement that fits. */
    private static void narrow(byte[] line, int[] clue) {
        int size = line.length;
        boolean[] everFilled = new boolean[size];
        boolean[] everEmpty = new boolean[size];
        place(clue[0] == 0 ? new int[0] : clue, 0, 0, new boolean[size], line,
                everFilled, everEmpty);
        for (int at = 0; at < size; at++) {
            if (everFilled[at] != everEmpty[at]) {
                line[at] = everFilled[at] ? NonogramSolver.FILL : NonogramSolver.EMPTY;
            }
        }
    }

    private static void place(int[] blocks, int block, int from, boolean[] candidate,
            byte[] line, boolean[] everFilled, boolean[] everEmpty) {
        int size = candidate.length;
        if (block == blocks.length) {
            for (int at = 0; at < size; at++) {
                if (line[at] == NonogramSolver.FILL && !candidate[at]) {
                    return;
                }
                if (line[at] == NonogramSolver.EMPTY && candidate[at]) {
                    return;
                }
            }
            for (int at = 0; at < size; at++) {
                if (candidate[at]) {
                    everFilled[at] = true;
                } else {
                    everEmpty[at] = true;
                }
            }
            return;
        }
        int needed = -1;
        for (int rest = block; rest < blocks.length; rest++) {
            needed += blocks[rest] + 1;
        }
        for (int start = from; start + needed <= size; start++) {
            for (int i = 0; i < blocks[block]; i++) {
                candidate[start + i] = true;
            }
            place(blocks, block + 1, start + blocks[block] + 1, candidate, line,
                    everFilled, everEmpty);
            for (int i = 0; i < blocks[block]; i++) {
                candidate[start + i] = false;
            }
        }
    }

    /**
     * The first picture in the book is a heart, drawn straight.
     *
     * <p>It was not. "First Heart" tapered over columns 2-3 and then column 3, so the point
     * leaned a square left of centre and its column clues read 2,4,4,3,2 where a heart reads
     * 2,4,4,4,2 — a lopsided heart, drawn at the largest squares the game makes, as the very
     * first thing a pair finish together. It had been bent that way to clear
     * {@link #chaptersAreNotDeckBoards}, which is the test moving the hand-drawn board when
     * it should have moved the generated one; the deck's own 5x5 heart carries the deeper
     * cleft now and the two are three squares apart.
     */
    @Test
    public void theFirstChapterIsASymmetricHeart() {
        Puzzle heart = PuzzleLibrary.get(0);
        assertEquals("First Heart", heart.name);
        for (int y = 0; y < heart.size; y++) {
            for (int x = 0; x < heart.size; x++) {
                assertEquals("the first heart leans at " + x + "," + y,
                        heart.solution[y][x], heart.solution[y][heart.size - 1 - x]);
            }
        }
    }
}

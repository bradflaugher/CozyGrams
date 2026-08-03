package com.cozygrams.tv;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The solver is what the fairness promise rests on, so it gets checked against the truth:
 * a brute-force count of every grid that matches the clues. If line solving ever finished
 * a board that actually had two answers, the promise would be worthless.
 */
public class NonogramSolverTest {

    @Test
    public void solvesTheObviousBoards() {
        assertTrue(NonogramSolver.uniquelyLineSolvable(grid("###", "###", "###")));
        assertTrue(NonogramSolver.uniquelyLineSolvable(grid("...", "...", "...")));
        assertTrue(NonogramSolver.uniquelyLineSolvable(grid("#..", "...", "...")));
        assertTrue(NonogramSolver.uniquelyLineSolvable(
                grid(".#.#.", "#####", "#####", ".###.", "..#..")));
    }

    /** The classic ambiguity: two squares that can swap corners without changing a clue. */
    @Test
    public void spotsTheSwappableCorners() {
        assertFalse(NonogramSolver.uniquelyLineSolvable(grid("#.", ".#")));
        assertFalse(NonogramSolver.uniquelyLineSolvable(grid("#..", "...", "..#")));
        assertFalse(NonogramSolver.uniquelyLineSolvable(
                grid("#...", "....", "....", "...#")));
    }

    @Test
    public void reportsHowMuchIsLeftUndecided() {
        NonogramSolver.Result result = new NonogramSolver(2).solve(grid("#.", ".#"));
        assertEquals(4, result.undetermined);
        assertFalse(result.solved());
    }

    /**
     * Over thousands of random boards, "line solving finished it" must always mean "there
     * is exactly one answer". Anything else and a player could fill in a legitimate
     * alternative and be told they were wrong.
     */
    @Test
    public void finishingByLineLogicAlwaysMeansTheAnswerIsUnique() {
        Random random = new Random(20240501L);
        int lineSolved = 0;
        for (int trial = 0; trial < 1500; trial++) {
            int size = 4 + random.nextInt(2);
            boolean[][] board = new boolean[size][size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    board[y][x] = random.nextInt(100) < 45;
                }
            }
            if (!NonogramSolver.uniquelyLineSolvable(board)) {
                continue;
            }
            lineSolved++;
            assertEquals("line solving finished an ambiguous board:\n" + show(board),
                    1, countAnswers(board));
        }
        assertTrue("the sample should contain plenty of solvable boards", lineSolved > 300);
    }

    /** Solving never invents squares: what it deduces always matches the real picture. */
    @Test
    public void deductionsAgreeWithTheHiddenPicture() {
        Random random = new Random(7L);
        for (int trial = 0; trial < 400; trial++) {
            int size = 5 + random.nextInt(16);
            boolean[][] board = new boolean[size][size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    board[y][x] = random.nextInt(100) < 55;
                }
            }
            NonogramSolver.Result result = new NonogramSolver(size).solve(board);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    byte deduced = result.state[y][x];
                    if (deduced == NonogramSolver.FILL) {
                        assertTrue(board[y][x]);
                    } else if (deduced == NonogramSolver.EMPTY) {
                        assertFalse(board[y][x]);
                    }
                }
            }
        }
    }

    // ---- Seeded solving and hints ------------------------------------------------------

    /** Starting from what the players already know never loses ground. */
    @Test
    public void seededSolvingPicksUpWherePlayersLeftOff() {
        boolean[][] board = grid(".#.#.", "#####", "#####", ".###.", "..#..");
        NonogramSolver solver = new NonogramSolver(5);
        NonogramSolver.Result cold = solver.solve(board);
        byte[][] known = new byte[5][5];
        known[0][1] = NonogramSolver.FILL;
        known[4][0] = NonogramSolver.EMPTY;
        NonogramSolver.Result warm = solver.solveFrom(board, known);
        assertTrue(warm.undetermined <= cold.undetermined);
        assertFalse(warm.contradicted);
    }

    /**
     * A board that needs a guess stops being one once the guess has been made: seeding the
     * solve with the two squares that were ambiguous finishes it. This is exactly what a
     * hint is for.
     */
    @Test
    public void aSeededSquareUnlocksWhatLogicCouldNotReach() {
        boolean[][] board = grid("#.", ".#");
        assertFalse(NonogramSolver.uniquelyLineSolvable(board));
        byte[][] known = new byte[2][2];
        known[0][0] = NonogramSolver.FILL;
        assertTrue(new NonogramSolver(2).solveFrom(board, known).solved());
    }

    /** A mark that cannot be right makes its line impossible, and says so. */
    @Test
    public void aWrongMarkIsReportedRatherThanBelieved() {
        boolean[][] board = grid("###", "#.#", "###");
        byte[][] known = new byte[3][3];
        known[1][1] = NonogramSolver.FILL;         // the hole in the middle is not filled
        NonogramSolver.Result result = new NonogramSolver(3).solveFrom(board, known);
        assertTrue(result.contradicted);
        assertFalse(result.solved());
    }

    /**
     * The hint has to be a square the clues force from where the pair are now, and it has
     * to agree with the hidden picture. Checked over the whole endless deck at four sizes
     * with a quarter of the board already marked: every hint offered is a real deduction.
     */
    @Test
    public void everyHintIsADeductionAndNeverAGuess() {
        Random random = new Random(31L);
        for (int size : new int[] {5, 10, 15, 20}) {
            NonogramSolver solver = new NonogramSolver(size);
            for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
                Puzzle puzzle = PuzzleGenerator.compose(subject, 0, size);
                byte[][] marks = new byte[size][size];
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        if (random.nextInt(4) == 0) {
                            marks[y][x] = puzzle.solution[y][x]
                                    ? NonogramSolver.FILL : NonogramSolver.EMPTY;
                        }
                    }
                }
                int[] hint = solver.nextDeduction(puzzle.solution, marks);
                assertNotNull(puzzle.name + " at " + size + " had no hint to give", hint);
                assertEquals(puzzle.name + " hinted a square that is already marked",
                        NonogramSolver.UNKNOWN, marks[hint[1]][hint[0]]);
                assertEquals(puzzle.name + " hinted the wrong answer",
                        puzzle.solution[hint[1]][hint[0]], hint[2] == NonogramSolver.FILL);
            }
        }
    }

    /**
     * Roughly half of every nonogram is empty squares, so a hint that could only ever fill
     * one in had to skip half the deductions there are. Over the deck it now offers plenty
     * of crosses as well as fills.
     */
    @Test
    public void hintsCanSayThisOneIsEmptyToo() {
        NonogramSolver solver = new NonogramSolver(15);
        int crosses = 0;
        for (int subject = 0; subject < PuzzleGenerator.subjectCount(); subject++) {
            Puzzle puzzle = PuzzleGenerator.compose(subject, 0, 15);
            int[] hint = solver.nextDeduction(puzzle.solution, new byte[15][15]);
            assertNotNull(hint);
            if (hint[2] == NonogramSolver.EMPTY) {
                crosses++;
            }
        }
        assertTrue("only " + crosses + " of 24 opening hints were crosses", crosses >= 4);
    }

    /**
     * The pair are allowed to be wrong. When a mark makes the seeded solve impossible, or
     * makes it deduce something the picture disagrees with, the hint falls back to solving
     * from scratch rather than handing over a square that is not there.
     */
    @Test
    public void aMistakeOnTheBoardStillGetsARealHint() {
        Puzzle puzzle = PuzzleGenerator.compose(0, 0, 10);
        byte[][] marks = new byte[10][10];
        int wrong = 0;
        for (int y = 0; y < 10 && wrong < 6; y++) {
            for (int x = 0; x < 10 && wrong < 6; x++) {
                marks[y][x] = puzzle.solution[y][x]
                        ? NonogramSolver.EMPTY : NonogramSolver.FILL;
                wrong++;
            }
        }
        int[] hint = new NonogramSolver(10).nextDeduction(puzzle.solution, marks);
        assertNotNull("a board full of mistakes should still get a hint", hint);
        assertEquals(puzzle.solution[hint[1]][hint[0]], hint[2] == NonogramSolver.FILL);
    }

    /** Nothing left to deduce on a finished board. */
    @Test
    public void aFinishedBoardHasNoHintLeft() {
        Puzzle puzzle = PuzzleGenerator.compose(3, 0, 10);
        byte[][] marks = new byte[10][10];
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                marks[y][x] = puzzle.solution[y][x]
                        ? NonogramSolver.FILL : NonogramSolver.EMPTY;
            }
        }
        assertNull(new NonogramSolver(10).nextDeduction(puzzle.solution, marks));
    }

    // ---- Helpers ----------------------------------------------------------------------

    private static boolean[][] grid(String... rows) {
        boolean[][] out = new boolean[rows.length][rows[0].length()];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                out[y][x] = rows[y].charAt(x) == '#';
            }
        }
        return out;
    }

    private static String show(boolean[][] board) {
        StringBuilder text = new StringBuilder();
        for (boolean[] row : board) {
            for (boolean filled : row) {
                text.append(filled ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** Brute force: how many grids match these clues, counting no further than two. */
    private static int countAnswers(boolean[][] board) {
        int size = board.length;
        int[][] columnClues = new int[size][];
        List<List<boolean[]>> rowOptions = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            boolean[] column = new boolean[size];
            for (int y = 0; y < size; y++) {
                column[y] = board[y][index];
            }
            columnClues[index] = Puzzle.clues(column);
            rowOptions.add(linesMatching(Puzzle.clues(board[index]), size));
        }
        return search(0, new boolean[size][], rowOptions, columnClues, size);
    }

    private static List<boolean[]> linesMatching(int[] clue, int size) {
        List<boolean[]> found = new ArrayList<>();
        for (int bits = 0; bits < (1 << size); bits++) {
            boolean[] line = new boolean[size];
            for (int i = 0; i < size; i++) {
                line[i] = (bits >> i & 1) != 0;
            }
            if (Arrays.equals(Puzzle.clues(line), clue)) {
                found.add(line);
            }
        }
        return found;
    }

    private static int search(int row, boolean[][] partial, List<List<boolean[]>> rowOptions,
            int[][] columnClues, int size) {
        if (row == size) {
            for (int x = 0; x < size; x++) {
                boolean[] column = new boolean[size];
                for (int y = 0; y < size; y++) {
                    column[y] = partial[y][x];
                }
                if (!Arrays.equals(Puzzle.clues(column), columnClues[x])) {
                    return 0;
                }
            }
            return 1;
        }
        int found = 0;
        for (boolean[] candidate : rowOptions.get(row)) {
            partial[row] = candidate;
            found += search(row + 1, partial, rowOptions, columnClues, size);
            if (found > 1) {
                return found;
            }
        }
        return found;
    }
}

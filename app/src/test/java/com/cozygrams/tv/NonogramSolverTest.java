package com.cozygrams.tv;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

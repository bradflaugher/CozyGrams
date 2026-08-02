package com.cozygrams.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One nonogram board: the hidden picture, the clue numbers derived from it, and the
 * marks the players have made so far.
 */
public final class Puzzle {

    /** Nothing has been decided about this square yet. */
    public static final byte UNKNOWN = 0;
    /** The players believe this square is part of the picture. */
    public static final byte FILLED = 1;
    /** The players have ruled this square out. */
    public static final byte CROSSED = 2;

    public final int size;
    public final boolean[][] solution;
    /** Player marks: {@link #UNKNOWN}, {@link #FILLED} or {@link #CROSSED}. */
    public final byte[][] marks;
    public final String name;

    private final int[][] rowClues;
    private final int[][] colClues;

    public Puzzle(boolean[][] solution, String name) {
        this.size = solution.length;
        this.solution = solution;
        this.name = name;
        this.marks = new byte[size][size];
        this.rowClues = new int[size][];
        this.colClues = new int[size][];
        cacheClues();
    }

    private void cacheClues() {
        for (int index = 0; index < size; index++) {
            rowClues[index] = clues(solution[index]);
            colClues[index] = clues(column(index));
        }
    }

    private boolean[] column(int col) {
        boolean[] line = new boolean[size];
        for (int y = 0; y < size; y++) {
            line[y] = solution[y][col];
        }
        return line;
    }

    /**
     * Turns a line of the picture into its clue numbers: the length of each run of
     * filled squares, in order. A completely empty line is described by a single zero.
     */
    public static int[] clues(boolean[] line) {
        List<Integer> runs = new ArrayList<>();
        int run = 0;
        for (boolean filled : line) {
            if (filled) {
                run++;
            } else if (run > 0) {
                runs.add(run);
                run = 0;
            }
        }
        if (run > 0) {
            runs.add(run);
        }
        if (runs.isEmpty()) {
            runs.add(0);
        }
        int[] result = new int[runs.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = runs.get(i);
        }
        return result;
    }

    public int[] rowClues(int row) {
        return rowClues[row];
    }

    public int[] colClues(int col) {
        return colClues[col];
    }

    /** The largest number of clue groups in any row, used for gutter sizing. */
    public int widestRowClue() {
        int widest = 1;
        for (int[] clue : rowClues) {
            widest = Math.max(widest, clue.length);
        }
        return widest;
    }

    /** The largest number of clue groups in any column, used for gutter sizing. */
    public int tallestColClue() {
        int tallest = 1;
        for (int[] clue : colClues) {
            tallest = Math.max(tallest, clue.length);
        }
        return tallest;
    }

    // ---- Line state ----------------------------------------------------------------

    private boolean[] markedRow(int row) {
        boolean[] line = new boolean[size];
        for (int x = 0; x < size; x++) {
            line[x] = marks[row][x] == FILLED;
        }
        return line;
    }

    private boolean[] markedCol(int col) {
        boolean[] line = new boolean[size];
        for (int y = 0; y < size; y++) {
            line[y] = marks[y][col] == FILLED;
        }
        return line;
    }

    /** True when the row's filled squares produce exactly the row's clue numbers. */
    public boolean rowSatisfied(int row) {
        return Arrays.equals(clues(markedRow(row)), rowClues(row));
    }

    public boolean colSatisfied(int col) {
        return Arrays.equals(clues(markedCol(col)), colClues(col));
    }

    /** True when every square in the row matches the hidden picture exactly. */
    public boolean rowSolved(int row) {
        for (int x = 0; x < size; x++) {
            if ((marks[row][x] == FILLED) != solution[row][x]) {
                return false;
            }
        }
        return true;
    }

    public boolean colSolved(int col) {
        for (int y = 0; y < size; y++) {
            if ((marks[y][col] == FILLED) != solution[y][col]) {
                return false;
            }
        }
        return true;
    }

    /**
     * After a square changes, crosses out the leftover unknowns in its row and column
     * when those lines are already finished. Returns how many squares were crossed so
     * the caller can celebrate a completed line.
     */
    public int autoCrossCompletedLines(int x, int y) {
        int crossed = 0;
        if (rowSolved(y)) {
            for (int column = 0; column < size; column++) {
                if (marks[y][column] == UNKNOWN) {
                    marks[y][column] = CROSSED;
                    crossed++;
                }
            }
        }
        if (colSolved(x)) {
            for (int row = 0; row < size; row++) {
                if (marks[row][x] == UNKNOWN) {
                    marks[row][x] = CROSSED;
                    crossed++;
                }
            }
        }
        return crossed;
    }

    /**
     * True when every clue is satisfied.
     *
     * <p>This validates the visible clue solution rather than a hidden bitmap identity,
     * so an alternate arrangement that legitimately matches every clue is accepted.
     */
    public boolean complete() {
        for (int y = 0; y < size; y++) {
            if (!rowSatisfied(y)) {
                return false;
            }
        }
        for (int x = 0; x < size; x++) {
            if (!colSatisfied(x)) {
                return false;
            }
        }
        return true;
    }

    /** How many squares are filled in that do not belong to the picture. */
    public int mistakes() {
        int wrong = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (marks[y][x] == FILLED && !solution[y][x]) {
                    wrong++;
                }
            }
        }
        return wrong;
    }

    /** How many of the picture's squares have been found, for progress readouts. */
    public int foundCount() {
        int found = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (marks[y][x] == FILLED && solution[y][x]) {
                    found++;
                }
            }
        }
        return found;
    }

    /** How many squares make up the hidden picture. */
    public int pictureCount() {
        int total = 0;
        for (boolean[] row : solution) {
            for (boolean filled : row) {
                if (filled) {
                    total++;
                }
            }
        }
        return total;
    }
}
